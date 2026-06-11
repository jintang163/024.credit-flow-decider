package com.bc.credit.integration.mq;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.common.enums.AsyncTaskStatus;
import com.bc.credit.common.enums.AsyncTaskType;
import com.bc.credit.dto.CreditScoreDTO;
import com.bc.credit.dto.credit.DeadLetterMessage;
import com.bc.credit.dto.credit.ScoringAsyncMessage;
import com.bc.credit.dto.credit.WorkflowCallbackRequest;
import com.bc.credit.entity.CreditScoreResult;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.AsyncTaskService;
import com.bc.credit.service.CreditScoringService;
import com.bc.credit.service.WorkflowCallbackService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class ScoringAsyncConsumer {

    @Value("${rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${rocketmq.consumer-group.scoring-async:credit-scoring-async-consumer-group}")
    private String consumerGroup;

    @Value("${rocketmq.topic.scoring-async:credit-async-scoring}")
    private String topic;

    @Value("${rocketmq.tag.scoring-async:scoring}")
    private String tag;

    @Value("${credit.async.scoring.local-retry.max-attempts:3}")
    private int localRetryMaxAttempts;

    @Value("${credit.async.scoring.local-retry.wait-duration-ms:1000}")
    private long localRetryWaitMs;

    @Value("${credit.async.scoring.circuit-breaker.failure-rate-threshold:50}")
    private float cbFailureRateThreshold;

    @Value("${credit.async.scoring.circuit-breaker.wait-duration-ms:60000}")
    private long cbWaitDurationMs;

    @Value("${credit.async.scoring.circuit-breaker.sliding-window-size:100}")
    private int cbSlidingWindowSize;

    @Value("${credit.async.scoring.circuit-breaker.minimum-number-of-calls:20}")
    private int cbMinimumCalls;

    @Value("${credit.async.enabled:true}")
    private boolean asyncEnabled;

    @Value("${rocketmq.topic.dead-letter:credit-dead-letter}")
    private String deadLetterTopic;

    @Value("${rocketmq.tag.dead-letter:scoring}")
    private String deadLetterTag;

    @Autowired
    private CreditScoringService creditScoringService;

    @Autowired
    private WorkflowCallbackService workflowCallbackService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private AsyncTaskService asyncTaskService;

    @Autowired
    private AsyncTaskProducer asyncTaskProducer;

    private DefaultMQPushConsumer consumer;

    private Retry scoringRetry;

    private CircuitBreaker scoringCircuitBreaker;

    @PostConstruct
    public void init() throws MQClientException {
        if (!asyncEnabled) {
            log.warn("[评分卡-消费者] 异步模式未启用，跳过消费者初始化");
            return;
        }

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(localRetryMaxAttempts)
                .waitDuration(Duration.ofMillis(localRetryWaitMs))
                .retryExceptions(Exception.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .failAfterMaxAttempts(true)
                .build();
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        scoringRetry = retryRegistry.retry("scoringConsumerRetry");

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(cbFailureRateThreshold)
                .waitDurationInOpenState(Duration.ofMillis(cbWaitDurationMs))
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cbSlidingWindowSize)
                .minimumNumberOfCalls(cbMinimumCalls)
                .permittedNumberOfCallsInHalfOpenState(10)
                .build();
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(cbConfig);
        scoringCircuitBreaker = cbRegistry.circuitBreaker("scoringConsumerCircuitBreaker");

        scoringCircuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.info("[评分卡-断路器] 状态变更: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> log.warn("[评分卡-断路器] 故障率超限: {}%",
                        event.getFailureRate()));

        scoringRetry.getEventPublisher()
                .onRetry(event -> log.info("[评分卡-本地重试] 第{}次重试, message: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable() != null ? event.getLastThrowable().getMessage() : ""))
                .onError(event -> log.warn("[评分卡-本地重试] 最终失败, attempts: {}",
                        event.getNumberOfRetryAttempts(), event.getLastThrowable()));

        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(topic, tag);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setConsumeThreadMin(4);
        consumer.setConsumeThreadMax(16);
        consumer.registerMessageListener(new ScoringMessageListener());
        consumer.start();

        log.info("[评分卡-消费者] 启动成功, topic: {}, tag: {}, group: {}, 本地重试次数: {}, 断路器阈值: {}%",
                topic, tag, consumerGroup, localRetryMaxAttempts, cbFailureRateThreshold);
    }

    @PreDestroy
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
            log.info("[评分卡-消费者] 已停止");
        }
    }

    private class ScoringMessageListener implements MessageListenerConcurrently {

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
                log.info("[评分卡-消费者] 收到消息, taskId: {}, msgId: {}, reconsumeTimes: {}, body: {}",
                        taskId, msg.getMsgId(), reconsumeTimes,
                        body.length() > 500 ? body.substring(0, 500) : body);

                ScoringAsyncMessage message = JSON.parseObject(body, ScoringAsyncMessage.class);

                return processMessage(message, body, taskId, reconsumeTimes);

            } catch (Exception e) {
                log.error("[评分卡-消费者] 处理消息异常, taskId: {}, msgId: {}, reconsumeTimes: {}",
                        taskId, msg.getMsgId(), reconsumeTimes, e);
                return handleConsumeException(taskId, reconsumeTimes, e);
            }
        }

        private ConsumeConcurrentlyStatus processMessage(ScoringAsyncMessage message,
                                                         String rawBody,
                                                         String taskId,
                                                         int reconsumeTimes) {
            String processInstanceId = message.getProcessInstanceId();
            Long applicationId = message.getApplicationId();
            int maxRetry = message.getMaxRetry();
            String alertLevel = "HIGH";

            if (reconsumeTimes > 0) {
                try {
                    asyncTaskService.incrementRetry(taskId, "MQ第" + reconsumeTimes + "次重投");
                } catch (Exception e) {
                    log.warn("[评分卡-消费者] incrementRetry失败, taskId: {}", taskId, e);
                }
            }

            if (reconsumeTimes > maxRetry) {
                log.error("[评分卡-消费者] MQ重试次数超限, taskId: {}, reconsumeTimes: {}, maxRetry: {}",
                        taskId, reconsumeTimes, maxRetry);
                sendToDeadLetter(message, rawBody, taskId,
                        "MQ重试次数超限 reconsumeTimes=" + reconsumeTimes,
                        reconsumeTimes, alertLevel);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }

            CircuitBreaker.State cbState = scoringCircuitBreaker.getState();
            if (CircuitBreaker.State.OPEN.equals(cbState)) {
                log.warn("[评分卡-消费者] 断路器OPEN，快速失败, taskId: {}, state: {}", taskId, cbState);
                return handleCircuitBreakerOpen(message, rawBody, taskId, reconsumeTimes, maxRetry);
            }

            try {
                asyncTaskService.updateTaskProcessing(taskId, null, taskId);
            } catch (Exception e) {
                log.warn("[评分卡-消费者] 更新任务状态PROCESSING失败, taskId: {}", taskId, e);
            }

            long startTime = System.currentTimeMillis();
            int localRetryCount = 0;
            Exception lastError = null;

            try {
                RetryableScoringCallable callable = new RetryableScoringCallable(
                        message, applicationId, scoringRetry);
                CreditScoreDTO scoreDTO = CircuitBreaker.decorateCallable(
                        scoringCircuitBreaker, callable).call();

                localRetryCount = callable.getRetryCount();
                long costMs = System.currentTimeMillis() - startTime;

                CreditScoreResult scoreResult = saveScoreResult(applicationId, scoreDTO);

                try {
                    asyncTaskService.updateTaskSuccess(taskId, costMs,
                            JSON.toJSONString(scoreDTO));
                } catch (Exception e) {
                    log.warn("[评分卡-消费者] updateTaskSuccess失败, taskId: {}", taskId, e);
                }

                WorkflowCallbackRequest callbackRequest = buildCallbackRequest(
                        message, scoreDTO, scoreResult, costMs, localRetryCount);

                boolean callbackSuccess = workflowCallbackService.triggerCallback(callbackRequest);

                if (!callbackSuccess) {
                    log.warn("[评分卡-消费者] 流程回调失败，尝试重投MQ, taskId: {}, processInstanceId: {}",
                            taskId, processInstanceId);
                    try {
                        asyncTaskService.updateTaskFailed(taskId,
                                "流程回调失败",
                                JSON.toJSONString(callbackRequest),
                                reconsumeTimes, maxRetry);
                    } catch (Exception e) {
                        log.warn("[评分卡-消费者] updateTaskFailed失败, taskId: {}", taskId, e);
                    }
                    return reconsumeTimes < maxRetry
                            ? ConsumeConcurrentlyStatus.RECONSUME_LATER
                            : ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }

                updateApplicationScore(applicationId, scoreDTO);

                log.info("[评分卡-消费者] 处理成功, taskId: {}, cost: {}ms, score: {}, " +
                                "segment: {}, defaultProb: {}, localRetry: {}, cbState: {}",
                        taskId, costMs, scoreDTO.getTotalScore(),
                        scoreDTO.getScoreSegment(), scoreDTO.getDefaultProbability(),
                        localRetryCount, cbState);

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;

            } catch (Exception e) {
                lastError = e;
                long costMs = System.currentTimeMillis() - startTime;
                log.error("[评分卡-消费者] 评分处理失败, taskId: {}, cost: {}ms, localRetry: {}, " +
                                "reconsumeTimes: {}, maxRetry: {}, cbState: {}",
                        taskId, costMs, localRetryCount, reconsumeTimes, maxRetry, cbState, e);

                return handleScoringFailure(message, rawBody, taskId,
                        reconsumeTimes, maxRetry, localRetryCount, lastError);
            }
        }

        private ConsumeConcurrentlyStatus handleScoringFailure(ScoringAsyncMessage message,
                                                               String rawBody,
                                                               String taskId,
                                                               int reconsumeTimes,
                                                               int maxRetry,
                                                               int localRetryCount,
                                                               Exception e) {
            if (reconsumeTimes < maxRetry) {
                log.info("[评分卡-消费者] MQ重投, reconsumeTimes: {}, maxRetry: {}, taskId: {}",
                        reconsumeTimes, maxRetry, taskId);
                try {
                    asyncTaskService.incrementRetry(taskId,
                            "第" + (reconsumeTimes + 1) + "次失败: " + e.getMessage());
                } catch (Exception ex) {
                    log.warn("[评分卡-消费者] incrementRetry失败, taskId: {}", taskId, ex);
                }
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }

            String deadReason = "MQ重试超限 totalRetry="
                    + (reconsumeTimes + localRetryCount) + " error: " + e.getMessage();
            sendToDeadLetter(message, rawBody, taskId, deadReason,
                    reconsumeTimes + localRetryCount, "HIGH");
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

        private ConsumeConcurrentlyStatus handleCircuitBreakerOpen(ScoringAsyncMessage message,
                                                                   String rawBody,
                                                                   String taskId,
                                                                   int reconsumeTimes,
                                                                   int maxRetry) {
            if (reconsumeTimes < maxRetry) {
                log.info("[评分卡-消费者] 断路器OPEN，等待后重投MQ, taskId: {}, reconsumeTimes: {}",
                        taskId, reconsumeTimes);
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }

            String deadReason = "断路器OPEN且MQ重试超限, reconsumeTimes=" + reconsumeTimes;
            sendToDeadLetter(message, rawBody, taskId, deadReason,
                    reconsumeTimes, "CRITICAL");
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

        private void sendToDeadLetter(ScoringAsyncMessage message,
                                      String rawBody,
                                      String taskId,
                                      String deadReason,
                                      int totalRetry,
                                      String alertLevel) {
            try {
                String errorStack = extractStack(message);

                asyncTaskService.updateTaskDeadLetter(taskId, deadReason, rawBody, totalRetry);

                DeadLetterMessage deadLetterMessage = new DeadLetterMessage();
                deadLetterMessage.setTaskId(taskId);
                deadLetterMessage.setTaskType(AsyncTaskType.CREDIT_SCORING.getCode());
                deadLetterMessage.setTaskTypeName(AsyncTaskType.CREDIT_SCORING.name());
                deadLetterMessage.setProcessInstanceId(message.getProcessInstanceId());
                deadLetterMessage.setApplicationId(message.getApplicationId());
                deadLetterMessage.setApplicationNo(message.getApplicationNo());
                deadLetterMessage.setCustomerId(message.getCustomerId());
                deadLetterMessage.setOriginalTopic(topic);
                deadLetterMessage.setOriginalTag(tag);
                deadLetterMessage.setRetryCount(totalRetry);
                deadLetterMessage.setMaxRetry(message.getMaxRetry());
                deadLetterMessage.setDeadReason(deadReason);
                deadLetterMessage.setLastError(deadReason);
                deadLetterMessage.setErrorStack(errorStack);
                deadLetterMessage.setOriginalBody(rawBody);
                deadLetterMessage.setNeedManualReview(true);
                deadLetterMessage.setAlertLevel(alertLevel);
                deadLetterMessage.setAlertMessage("评分卡死信告警: " + deadReason
                        + ", taskId=" + taskId
                        + ", applicationNo=" + message.getApplicationNo());
                deadLetterMessage.setDeadTime(LocalDateTime.now());

                asyncTaskProducer.sendDeadLetter(deadLetterMessage);

                triggerManualReview(message, taskId, deadReason);

                log.error("[评分卡-消费者] 发送死信消息, taskId: {}, reason: {}, alertLevel: {}",
                        taskId, deadReason, alertLevel);

            } catch (Exception ex) {
                log.error("[评分卡-消费者] 发送死信失败, taskId: {}", taskId, ex);
            }
        }

        private String extractStack(ScoringAsyncMessage message) {
            try {
                Map<String, Object> extra = message.getExtraInfo();
                if (extra != null && extra.get("__stackTrace") != null) {
                    return String.valueOf(extra.get("__stackTrace"));
                }
            } catch (Exception e) {
                // ignore
            }
            return "";
        }

        private void triggerManualReview(ScoringAsyncMessage message,
                                         String taskId,
                                         String reason) {
            try {
                WorkflowCallbackRequest callbackRequest = new WorkflowCallbackRequest();
                callbackRequest.setProcessInstanceId(message.getProcessInstanceId());
                callbackRequest.setSignalName(message.getSignalName());
                callbackRequest.setReceiveTaskId(message.getReceiveTaskId());
                callbackRequest.setTaskType(AsyncTaskType.CREDIT_SCORING.getCode());
                callbackRequest.setTaskId(taskId);
                callbackRequest.setSuccess(false);
                callbackRequest.setErrorMsg(reason);

                Map<String, Object> variables = new HashMap<>();
                variables.put(ProcessVariableConstants.SCORE_PASS, false);
                variables.put(ProcessVariableConstants.SCORING_ASYNC_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_DEAD_LETTER);
                variables.put(ProcessVariableConstants.SCORING_ERROR, reason);
                variables.put(ProcessVariableConstants.SCORING_CALLBACK_STATUS,
                        ProcessVariableConstants.CALLBACK_STATUS_FAILED);
                variables.put(ProcessVariableConstants.ASYNC_CALLBACK_STATUS,
                        ProcessVariableConstants.CALLBACK_STATUS_FAILED);
                variables.put(ProcessVariableConstants.ASYNC_TASK_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_DEAD_LETTER);
                variables.put(ProcessVariableConstants.ASYNC_LAST_ERROR, reason);
                variables.put(ProcessVariableConstants.NEED_MANUAL_REVIEW, true);
                variables.put(ProcessVariableConstants.MANUAL_REVIEW_REASON,
                        "评分卡死信: " + reason);
                callbackRequest.setVariables(variables);

                workflowCallbackService.transferToManualReview(callbackRequest);

            } catch (Exception ex) {
                log.error("[评分卡-消费者] 转人工失败, taskId: {}", taskId, ex);
            }
        }

        private WorkflowCallbackRequest buildCallbackRequest(ScoringAsyncMessage message,
                                                             CreditScoreDTO scoreDTO,
                                                             CreditScoreResult scoreResult,
                                                             long costMs,
                                                             int localRetryCount) {
            WorkflowCallbackRequest request = new WorkflowCallbackRequest();
            request.setProcessInstanceId(message.getProcessInstanceId());
            request.setSignalName(message.getSignalName());
            request.setReceiveTaskId(message.getReceiveTaskId());
            request.setTaskType(AsyncTaskType.CREDIT_SCORING.getCode());
            request.setTaskId(message.getTaskId());
            request.setSuccess(true);

            Map<String, Object> variables = new HashMap<>();
            variables.put(ProcessVariableConstants.CREDIT_SCORE, scoreDTO.getTotalScore());
            variables.put(ProcessVariableConstants.SCORE_LEVEL, scoreDTO.getScoreLevel());
            variables.put(ProcessVariableConstants.SCORE_PASS, scoreDTO.getPass());
            variables.put(ProcessVariableConstants.DIMENSION_SCORES, scoreDTO.getDimensionScores());
            variables.put(ProcessVariableConstants.DEFAULT_PROBABILITY,
                    scoreDTO.getDefaultProbability());
            variables.put(ProcessVariableConstants.SCORE_SEGMENT, scoreDTO.getScoreSegment());
            variables.put(ProcessVariableConstants.ENGINE_TYPE, scoreDTO.getEngineType());
            variables.put(ProcessVariableConstants.MODEL_VERSION, scoreDTO.getModelVersion());
            variables.put(ProcessVariableConstants.SCORING_ASYNC_STATUS,
                    ProcessVariableConstants.ASYNC_STATUS_SUCCESS);
            variables.put(ProcessVariableConstants.SCORING_CALLBACK_STATUS,
                    ProcessVariableConstants.CALLBACK_STATUS_SUCCESS);
            variables.put(ProcessVariableConstants.SCORING_CALLBACK_TIME,
                    LocalDateTime.now().toString());
            variables.put(ProcessVariableConstants.SCORING_COST_MS, costMs);
            variables.put(ProcessVariableConstants.SCORING_RETRY_COUNT, localRetryCount);

            if (scoreResult != null) {
                variables.put(ProcessVariableConstants.SCORE_RESULT_ID, scoreResult.getId());
            }
            if (scoreDTO.getShapValues() != null) {
                variables.put(ProcessVariableConstants.SHAP_VALUES, scoreDTO.getShapValues());
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

        private CreditScoreResult saveScoreResult(Long applicationId, CreditScoreDTO scoreDTO) {
            try {
                LoanApplication application = loanApplicationMapper.selectById(applicationId);
                if (application != null) {
                    return creditScoringService.saveScoreResult(application, scoreDTO);
                }
                return null;
            } catch (Exception e) {
                log.warn("[评分卡-消费者] 保存评分结果失败，继续流程, applicationId: {}", applicationId, e);
                return null;
            }
        }

        private void updateApplicationScore(Long applicationId, CreditScoreDTO scoreDTO) {
            try {
                LoanApplication application = loanApplicationMapper.selectById(applicationId);
                if (application != null) {
                    application.setCreditScore(scoreDTO.getTotalScore());
                    application.setUpdatedTime(LocalDateTime.now());
                    loanApplicationMapper.updateById(application);
                }
            } catch (Exception e) {
                log.warn("[评分卡-消费者] 更新申请表信用分失败, applicationId: {}", applicationId, e);
            }
        }

        private ConsumeConcurrentlyStatus handleConsumeException(String taskId,
                                                                 int reconsumeTimes,
                                                                 Exception e) {
            if (reconsumeTimes < localRetryMaxAttempts) {
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            log.error("[评分卡-消费者] 消息彻底失败, taskId: {}, reconsumeTimes: {}",
                    taskId, reconsumeTimes, e);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
    }

    private static class RetryableScoringCallable implements java.util.concurrent.Callable<CreditScoreDTO> {

        private final ScoringAsyncMessage message;
        private final Long applicationId;
        private final Retry retry;
        private int retryCount = 0;

        RetryableScoringCallable(ScoringAsyncMessage message,
                                 Long applicationId,
                                 Retry retry) {
            this.message = message;
            this.applicationId = applicationId;
            this.retry = retry;
        }

        public int getRetryCount() {
            return retryCount;
        }

        @Override
        public CreditScoreDTO call() throws Exception {
            retry.getEventPublisher().onRetry(event -> retryCount = event.getNumberOfRetryAttempts());
            return Retry.decorateCallable(retry, this::doCalculate).call();
        }

        private CreditScoreDTO doCalculate() throws Exception {
            LoanApplication application = new LoanApplication();
            application.setId(applicationId);
            application.setApplicationNo(message.getApplicationNo());
            application.setCustomerId(message.getCustomerId());
            application.setCustomerName(message.getCustomerName());

            if (message.getExtraInfo() != null) {
                Map<String, Object> ext = message.getExtraInfo();
                if (ext.get(ProcessVariableConstants.LOAN_AMOUNT) != null) {
                    application.setLoanAmount(new BigDecimal(
                            String.valueOf(ext.get(ProcessVariableConstants.LOAN_AMOUNT))));
                }
                if (ext.get(ProcessVariableConstants.LOAN_TERM) != null) {
                    Object term = ext.get(ProcessVariableConstants.LOAN_TERM);
                    application.setLoanTerm(term instanceof Number
                            ? ((Number) term).intValue() : Integer.parseInt(String.valueOf(term)));
                }
            }

            Map<String, Object> extraInfo = message.getExtraInfo() != null
                    ? new HashMap<>(message.getExtraInfo()) : new HashMap<>();

            return CreditScoringServiceHolder.creditScoringService.calculateScore(
                    application,
                    message.getCreditScore(),
                    message.getOverdueCount(),
                    message.getRemainingLoanAmount(),
                    extraInfo
            );
        }
    }

    @org.springframework.stereotype.Component
    static class CreditScoringServiceHolder {
        static CreditScoringService creditScoringService;

        @Autowired
        public CreditScoringServiceHolder(CreditScoringService service) {
            creditScoringService = service;
        }
    }
}
