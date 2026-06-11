package com.bc.credit.integration.mq;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.common.enums.AsyncTaskStatus;
import com.bc.credit.common.enums.AsyncTaskType;
import com.bc.credit.dto.CreditDataDTO;
import com.bc.credit.dto.credit.CreditQueryAsyncMessage;
import com.bc.credit.dto.credit.WorkflowCallbackRequest;
import com.bc.credit.entity.CreditQueryRecord;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.AsyncTaskService;
import com.bc.credit.service.CreditQueryService;
import com.bc.credit.service.WorkflowCallbackService;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CreditQueryAsyncConsumer {

    @Value("${rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${rocketmq.consumer-group.credit-query-async:credit-query-async-consumer-group}")
    private String consumerGroup;

    @Value("${rocketmq.topic.credit-query-async:credit-async-query}")
    private String topic;

    @Value("${rocketmq.tag.credit-query-async:query}")
    private String tag;

    @Value("${credit.async.credit-query.local-retry.max-attempts:3}")
    private int localRetryMaxAttempts;

    @Value("${credit.async.credit-query.local-retry.wait-duration-ms:1000}")
    private long localRetryWaitMs;

    @Value("${credit.async.enabled:true}")
    private boolean asyncEnabled;

    @Autowired
    private CreditQueryService creditQueryService;

    @Autowired
    private WorkflowCallbackService workflowCallbackService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private AsyncTaskService asyncTaskService;

    private DefaultMQPushConsumer consumer;

    private Retry creditQueryRetry;

    @PostConstruct
    public void init() throws MQClientException {
        if (!asyncEnabled) {
            log.warn("[征信查询-消费者] 异步模式未启用，跳过消费者初始化");
            return;
        }

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(localRetryMaxAttempts)
                .waitDuration(Duration.ofMillis(localRetryWaitMs))
                .retryExceptions(Exception.class)
                .failAfterMaxAttempts(true)
                .build();
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        creditQueryRetry = retryRegistry.retry("creditQueryConsumerRetry");

        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(topic, tag);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.registerMessageListener(new CreditQueryMessageListener());
        consumer.start();

        log.info("[征信查询-消费者] 启动成功, topic: {}, tag: {}, group: {}",
                topic, tag, consumerGroup);
    }

    @PreDestroy
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
            log.info("[征信查询-消费者] 已停止");
        }
    }

    private class CreditQueryMessageListener implements MessageListenerConcurrently {

        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs,
                                                        ConsumeConcurrentlyContext context) {
            if (msgs == null || msgs.isEmpty()) {
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }

            MessageExt msg = msgs.get(0);
            String taskId = msg.getKeys();
            int reconsumeTimes = msg.getReconsumeTimes();

            try {
                String body = new String(msg.getBody(), "UTF-8");
                log.info("[征信查询-消费者] 收到消息, taskId: {}, msgId: {}, reconsumeTimes: {}, body: {}",
                        taskId, msg.getMsgId(), reconsumeTimes, body);

                CreditQueryAsyncMessage message = JSON.parseObject(body, CreditQueryAsyncMessage.class);

                return processMessage(message, body, taskId, reconsumeTimes);

            } catch (Exception e) {
                log.error("[征信查询-消费者] 处理消息异常, taskId: {}, msgId: {}, reconsumeTimes: {}",
                        taskId, msg.getMsgId(), reconsumeTimes, e);
                return handleConsumeException(taskId, reconsumeTimes, e);
            }
        }

        private ConsumeConcurrentlyStatus processMessage(CreditQueryAsyncMessage message,
                                                         String rawBody,
                                                         String taskId,
                                                         int reconsumeTimes) {
            String processInstanceId = message.getProcessInstanceId();
            Long applicationId = message.getApplicationId();
            int maxRetry = message.getMaxRetry();

            if (reconsumeTimes > 0) {
                asyncTaskService.incrementRetry(taskId, "MQ第" + reconsumeTimes + "次重投");
            }

            if (reconsumeTimes > maxRetry) {
                log.error("[征信查询-消费者] MQ重试次数超限, taskId: {}, reconsumeTimes: {}, maxRetry: {}",
                        taskId, reconsumeTimes, maxRetry);
                handleDeadLetter(message, rawBody, taskId, "MQ重试次数超限", reconsumeTimes);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }

            try {
                asyncTaskService.updateTaskProcessing(taskId, null, taskId);
            } catch (Exception e) {
                log.warn("[征信查询-消费者] 更新任务状态为PROCESSING失败, taskId: {}", taskId, e);
            }

            long startTime = System.currentTimeMillis();
            try {
                CreditDataDTO creditData = Retry.decorateCallable(creditQueryRetry,
                        () -> doQueryCredit(message, applicationId)).call();

                long costMs = System.currentTimeMillis() - startTime;

                CreditQueryRecord record = saveQueryRecord(applicationId, message.getCustomerId(), creditData);

                asyncTaskService.updateTaskSuccess(taskId, costMs,
                        JSON.toJSONString(creditData));

                WorkflowCallbackRequest callbackRequest = buildCallbackRequest(
                        message, creditData, record, costMs);

                boolean callbackSuccess = workflowCallbackService.triggerCallback(callbackRequest);

                if (!callbackSuccess) {
                    log.warn("[征信查询-消费者] 流程回调失败，记录为需要补偿, taskId: {}, processInstanceId: {}",
                            taskId, processInstanceId);
                    asyncTaskService.updateTaskFailed(taskId,
                            "流程回调失败",
                            JSON.toJSONString(callbackRequest),
                            reconsumeTimes, maxRetry);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }

                updateApplicationScore(applicationId, creditData);

                log.info("[征信查询-消费者] 处理成功, taskId: {}, cost: {}ms, creditScore: {}",
                        taskId, costMs, creditData.getCreditScore());

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;

            } catch (Exception e) {
                log.error("[征信查询-消费者] 征信查询调用失败, taskId: {}, applicationId: {}",
                        taskId, applicationId, e);
                return handleQueryFailure(message, rawBody, taskId, reconsumeTimes, maxRetry,
                        startTime, e);
            }
        }

        private CreditDataDTO doQueryCredit(CreditQueryAsyncMessage message,
                                            Long applicationId) {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                log.warn("[征信查询-消费者] LoanApplication已查询不到，尝试用消息中的customerId, applicationId: {}",
                        applicationId);
                application = buildTempApplicationFromMessage(message, applicationId);
            }
            return creditQueryService.queryCredit(application);
        }

        private LoanApplication buildTempApplicationFromMessage(CreditQueryAsyncMessage message,
                                                                Long applicationId) {
            LoanApplication app = new LoanApplication();
            app.setId(applicationId);
            app.setApplicationNo(message.getApplicationNo());
            app.setCustomerId(message.getCustomerId());
            app.setCustomerName(message.getCustomerName());
            app.setIdCard(message.getIdCard());
            app.setPhone(message.getPhone());
            return app;
        }

        private CreditQueryRecord saveQueryRecord(Long applicationId,
                                                  Long customerId,
                                                  CreditDataDTO creditData) {
            try {
                LoanApplication application = loanApplicationMapper.selectById(applicationId);
                if (application != null) {
                    return creditQueryService.saveCreditRecord(application, creditData);
                }
                return null;
            } catch (Exception e) {
                log.warn("[征信查询-消费者] 保存征信查询记录失败，继续流程, applicationId: {}", applicationId, e);
                return null;
            }
        }

        private WorkflowCallbackRequest buildCallbackRequest(CreditQueryAsyncMessage message,
                                                             CreditDataDTO creditData,
                                                             CreditQueryRecord record,
                                                             long costMs) {
            WorkflowCallbackRequest request = new WorkflowCallbackRequest();
            request.setProcessInstanceId(message.getProcessInstanceId());
            request.setSignalName(message.getSignalName());
            request.setReceiveTaskId(message.getReceiveTaskId());
            request.setTaskType(AsyncTaskType.CREDIT_QUERY.getCode());
            request.setTaskId(message.getTaskId());
            request.setSuccess(true);

            Map<String, Object> variables = new HashMap<>();
            variables.put(ProcessVariableConstants.CREDIT_SCORE, creditData.getCreditScore());
            variables.put(ProcessVariableConstants.CREDIT_LEVEL, creditData.getCreditLevel());
            variables.put(ProcessVariableConstants.OVERDUE_COUNT, creditData.getOverdueCount());
            variables.put(ProcessVariableConstants.REMAINING_LOAN_AMOUNT,
                    creditData.getRemainingLoanAmount());
            variables.put(ProcessVariableConstants.CREDIT_QUERY_SUCCESS, creditData.getSuccess());
            variables.put(ProcessVariableConstants.CREDIT_QUERY_ASYNC_STATUS,
                    ProcessVariableConstants.ASYNC_STATUS_SUCCESS);
            variables.put(ProcessVariableConstants.CREDIT_QUERY_CALLBACK_STATUS,
                    ProcessVariableConstants.CALLBACK_STATUS_SUCCESS);
            variables.put(ProcessVariableConstants.CREDIT_QUERY_CALLBACK_TIME,
                    LocalDateTime.now().toString());
            variables.put(ProcessVariableConstants.CREDIT_QUERY_COST_MS, costMs);
            variables.put(ProcessVariableConstants.CREDIT_QUERY_DATA_SOURCE_NAMES,
                    creditData.getDataSourceNames());
            variables.put(ProcessVariableConstants.MULTI_LENDING_COUNT,
                    creditData.getMultiLendingCount());
            variables.put(ProcessVariableConstants.TOTAL_DEBT_RATIO,
                    creditData.getTotalDebtRatio());
            variables.put(ProcessVariableConstants.OVERDUE_DAYS, creditData.getOverdueDays());
            variables.put(ProcessVariableConstants.INCOME_RELIABILITY,
                    creditData.getIncomeReliability());
            variables.put(ProcessVariableConstants.COURT_EXECUTION_COUNT,
                    creditData.getCourtExecutionCount());

            if (record != null) {
                variables.put(ProcessVariableConstants.CREDIT_QUERY_RECORD_ID, record.getId());
            }

            variables.put(ProcessVariableConstants.ASYNC_CALLBACK_STATUS,
                    ProcessVariableConstants.CALLBACK_STATUS_SUCCESS);
            variables.put(ProcessVariableConstants.ASYNC_CALLBACK_TIME,
                    LocalDateTime.now().toString());
            variables.put(ProcessVariableConstants.ASYNC_TASK_STATUS,
                    ProcessVariableConstants.ASYNC_STATUS_SUCCESS);
            variables.put(ProcessVariableConstants.ASYNC_LAST_ERROR, "");
            variables.put(ProcessVariableConstants.ASYNC_ERROR_STACK, "");

            request.setVariables(variables);
            return request;
        }

        private void updateApplicationScore(Long applicationId, CreditDataDTO creditData) {
            try {
                LoanApplication application = loanApplicationMapper.selectById(applicationId);
                if (application != null && application.getCreditScore() == null) {
                    application.setCreditScore(creditData.getCreditScore());
                    application.setUpdatedTime(LocalDateTime.now());
                    loanApplicationMapper.updateById(application);
                }
            } catch (Exception e) {
                log.warn("[征信查询-消费者] 更新申请表信用分失败, applicationId: {}", applicationId, e);
            }
        }

        private ConsumeConcurrentlyStatus handleQueryFailure(CreditQueryAsyncMessage message,
                                                             String rawBody,
                                                             String taskId,
                                                             int reconsumeTimes,
                                                             int maxRetry,
                                                             long startTime,
                                                             Exception e) {
            long costMs = System.currentTimeMillis() - startTime;

            if (reconsumeTimes < maxRetry) {
                log.info("[征信查询-消费者] 将重投MQ，reconsumeTimes: {}, maxRetry: {}, taskId: {}",
                        reconsumeTimes, maxRetry, taskId);
                asyncTaskService.incrementRetry(taskId,
                        "第" + (reconsumeTimes + 1) + "次失败: " + e.getMessage());
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }

            handleDeadLetter(message, rawBody, taskId,
                    "MQ重试超限: " + e.getMessage(), maxRetry);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

        private void handleDeadLetter(CreditQueryAsyncMessage message,
                                      String rawBody,
                                      String taskId,
                                      String reason,
                                      int reconsumeTimes) {
            asyncTaskService.updateTaskDeadLetter(taskId,
                    reason, rawBody, reconsumeTimes);

            try {
                WorkflowCallbackRequest callbackRequest = new WorkflowCallbackRequest();
                callbackRequest.setProcessInstanceId(message.getProcessInstanceId());
                callbackRequest.setSignalName(message.getSignalName());
                callbackRequest.setReceiveTaskId(message.getReceiveTaskId());
                callbackRequest.setTaskType(AsyncTaskType.CREDIT_QUERY.getCode());
                callbackRequest.setTaskId(taskId);
                callbackRequest.setSuccess(false);
                callbackRequest.setErrorMsg(reason);

                Map<String, Object> variables = new HashMap<>();
                variables.put(ProcessVariableConstants.CREDIT_QUERY_SUCCESS, false);
                variables.put(ProcessVariableConstants.CREDIT_QUERY_ASYNC_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_DEAD_LETTER);
                variables.put(ProcessVariableConstants.CREDIT_QUERY_ERROR, reason);
                variables.put(ProcessVariableConstants.CREDIT_QUERY_CALLBACK_STATUS,
                        ProcessVariableConstants.CALLBACK_STATUS_FAILED);
                variables.put(ProcessVariableConstants.ASYNC_CALLBACK_STATUS,
                        ProcessVariableConstants.CALLBACK_STATUS_FAILED);
                variables.put(ProcessVariableConstants.ASYNC_TASK_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_DEAD_LETTER);
                variables.put(ProcessVariableConstants.ASYNC_LAST_ERROR, reason);
                variables.put(ProcessVariableConstants.NEED_MANUAL_REVIEW, true);
                variables.put(ProcessVariableConstants.MANUAL_REVIEW_REASON,
                        "征信查询死信: " + reason);
                callbackRequest.setVariables(variables);

                workflowCallbackService.transferToManualReview(callbackRequest);

            } catch (Exception ex) {
                log.error("[征信查询-消费者] 转人工处理失败, taskId: {}", taskId, ex);
            }
        }

        private ConsumeConcurrentlyStatus handleConsumeException(String taskId,
                                                                 int reconsumeTimes,
                                                                 Exception e) {
            if (reconsumeTimes < localRetryMaxAttempts) {
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
    }
}
