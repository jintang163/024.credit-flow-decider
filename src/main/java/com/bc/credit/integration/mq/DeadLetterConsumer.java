package com.bc.credit.integration.mq;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.common.enums.ApplicationStatusEnum;
import com.bc.credit.common.enums.AsyncTaskStatus;
import com.bc.credit.common.enums.AsyncTaskType;
import com.bc.credit.dto.credit.DeadLetterMessage;
import com.bc.credit.dto.credit.WorkflowCallbackRequest;
import com.bc.credit.entity.AsyncTask;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.AsyncTaskService;
import com.bc.credit.service.WorkflowCallbackService;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class DeadLetterConsumer {

    @Value("${rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${rocketmq.consumer-group.dead-letter:credit-dead-letter-consumer-group}")
    private String consumerGroup;

    @Value("${rocketmq.topic.dead-letter:credit-dead-letter}")
    private String topic;

    @Value("${rocketmq.tag.dead-letter:*}")
    private String tag;

    @Value("${credit.async.enabled:true}")
    private boolean asyncEnabled;

    @Value("${credit.async.dead-letter.auto-transfer-manual:true}")
    private boolean autoTransferManual;

    @Autowired
    private WorkflowCallbackService workflowCallbackService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private AsyncTaskService asyncTaskService;

    private DefaultMQPushConsumer consumer;

    private final AtomicLong deadLetterCounter = new AtomicLong(0);

    @PostConstruct
    public void init() throws MQClientException {
        if (!asyncEnabled) {
            log.warn("[死信队列-消费者] 异步模式未启用，跳过死信消费者初始化");
            return;
        }

        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(topic, tag);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.registerMessageListener(new DeadLetterMessageListener());
        consumer.start();

        log.info("[死信队列-消费者] 启动成功, topic: {}, tag: {}, group: {}, autoTransferManual: {}",
                topic, tag, consumerGroup, autoTransferManual);
    }

    @PreDestroy
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
            log.info("[死信队列-消费者] 已停止, 累计处理死信: {}", deadLetterCounter.get());
        }
    }

    private class DeadLetterMessageListener implements MessageListenerConcurrently {

        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs,
                                                        ConsumeConcurrentlyContext context) {
            if (msgs == null || msgs.isEmpty()) {
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }

            MessageExt msg = msgs.get(0);
            String taskId = msg.getKeys();
            long totalDead = deadLetterCounter.incrementAndGet();

            try {
                String body = new String(msg.getBody(), "UTF-8");
                log.error("[死信队列-消费者] 收到死信消息({}), taskId: {}, msgId: {}, bornTimestamp: {}, body: {}",
                        totalDead, taskId, msg.getMsgId(),
                        msg.getBornTimestamp(),
                        body.length() > 500 ? body.substring(0, 500) : body);

                DeadLetterMessage dlqMessage;
                try {
                    dlqMessage = JSON.parseObject(body, DeadLetterMessage.class);
                } catch (Exception parseEx) {
                    log.warn("[死信队列-消费者] 消息非标准DeadLetterMessage格式, " +
                            "按原始字符串记录告警, taskId: {}", taskId, parseEx);
                    triggerSimpleAlert(taskId, body, totalDead);
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }

                processDeadLetterMessage(dlqMessage, taskId, totalDead);

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;

            } catch (Exception e) {
                log.error("[死信队列-消费者] 处理死信异常, taskId: {}, msgId: {}, 第{}个死信",
                        taskId, msg.getMsgId(), totalDead, e);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        }

        private void processDeadLetterMessage(DeadLetterMessage dlq,
                                               String taskId,
                                               long totalDead) {
            String processInstanceId = dlq.getProcessInstanceId();
            Long applicationId = dlq.getApplicationId();
            String taskType = dlq.getTaskType();
            String alertLevel = dlq.getAlertLevel() != null
                    ? dlq.getAlertLevel() : "HIGH";

            log.error("[死信队列-消费者] 告警 level: {}, totalDead: {}, taskId: {}, " +
                            "taskType: {}, applicationId: {}, applicationNo: {}, reason: {}",
                    alertLevel, totalDead, taskId, taskType, applicationId,
                    dlq.getApplicationNo(), dlq.getDeadReason());

            try {
                AsyncTask asyncTask = taskId != null ? asyncTaskService.getByTaskId(taskId) : null;
                if (asyncTask != null
                        && !AsyncTaskStatus.DEAD_LETTER.getCode().equals(asyncTask.getStatus())) {
                    asyncTaskService.updateTaskDeadLetter(taskId,
                            dlq.getDeadReason(), dlq.getOriginalBody(),
                            dlq.getRetryCount() != null ? dlq.getRetryCount() :
                                    (dlq.getMaxRetry() != null ? dlq.getMaxRetry() : 99));
                }
            } catch (Exception e) {
                log.warn("[死信队列-消费者] 更新async_task死信状态失败, taskId: {}", taskId, e);
            }

            updateApplicationToManualIfNeeded(applicationId, dlq, taskType);

            if (processInstanceId != null && !processInstanceId.isEmpty()
                    && autoTransferManual) {
                transferToManualReview(processInstanceId, dlq, taskType);
            }

            triggerAlert(dlq, taskType, alertLevel, totalDead);
        }

        private void updateApplicationToManualIfNeeded(Long applicationId,
                                                        DeadLetterMessage dlq,
                                                        String taskType) {
            if (applicationId == null) {
                return;
            }
            try {
                LoanApplication application = loanApplicationMapper.selectById(applicationId);
                if (application != null
                        && !ApplicationStatusEnum.MANUAL_REVIEW.getCode().equals(
                        application.getApplicationStatus())
                        && !ApplicationStatusEnum.REJECTED.getCode().equals(
                        application.getApplicationStatus())
                        && !ApplicationStatusEnum.APPROVED.getCode().equals(
                        application.getApplicationStatus())) {

                    application.setApplicationStatus(
                            ApplicationStatusEnum.MANUAL_REVIEW.getCode());
                    application.setUpdatedTime(LocalDateTime.now());
                    String remark = buildRemarkForApplication(dlq, taskType);
                    if (application.getRemark() != null
                            && !application.getRemark().isEmpty()) {
                        remark = application.getRemark() + " | " + remark;
                    }
                    application.setRemark(remark);
                    loanApplicationMapper.updateById(application);

                    log.info("[死信队列-消费者] 更新申请为人工复核, applicationId: {}, oldStatus: {}",
                            applicationId, application.getApplicationStatus());
                }
            } catch (Exception e) {
                log.warn("[死信队列-消费者] 更新申请表失败, applicationId: {}", applicationId, e);
            }
        }

        private String buildRemarkForApplication(DeadLetterMessage dlq, String taskType) {
            StringBuilder sb = new StringBuilder();
            sb.append("死信: ");
            if (taskType != null) {
                sb.append(taskType).append(" ");
            }
            sb.append(dlq.getAlertLevel() != null ? "[" + dlq.getAlertLevel() + "]" : "[HIGH] ");
            sb.append(dlq.getDeadReason() != null ? dlq.getDeadReason() : "");
            if (dlq.getAlertMessage() != null && !dlq.getAlertMessage().isEmpty()) {
                sb.append(" | 告警: ").append(dlq.getAlertMessage().length() > 200
                        ? dlq.getAlertMessage().substring(0, 200) : dlq.getAlertMessage());
            }
            return sb.length() > 500 ? sb.substring(0, 500) : sb.toString();
        }

        private void transferToManualReview(String processInstanceId,
                                             DeadLetterMessage dlq,
                                             String taskType) {
            try {
                if (!workflowCallbackService.isProcessActive(processInstanceId)) {
                    log.warn("[死信队列-消费者] 流程已结束，跳过转人工, processInstanceId: {}",
                            processInstanceId);
                    return;
                }

                WorkflowCallbackRequest request = new WorkflowCallbackRequest();
                request.setProcessInstanceId(processInstanceId);
                request.setTaskId(dlq.getTaskId());
                request.setTaskType(taskType);
                request.setSignalName(findSignalByTaskType(taskType));
                request.setSuccess(false);
                request.setErrorMsg(dlq.getDeadReason());

                Map<String, Object> variables = new HashMap<>();
                variables.put(ProcessVariableConstants.NEED_MANUAL_REVIEW, true);
                variables.put(ProcessVariableConstants.MANUAL_REVIEW_REASON,
                        dlq.getAlertMessage() != null ? dlq.getAlertMessage()
                                : dlq.getDeadReason());
                variables.put(ProcessVariableConstants.ASYNC_TASK_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_DEAD_LETTER);
                variables.put(ProcessVariableConstants.ASYNC_LAST_ERROR, dlq.getDeadReason());
                variables.put(ProcessVariableConstants.DEAD_LETTER_ALERT_LEVEL,
                        dlq.getAlertLevel());
                variables.put(ProcessVariableConstants.DEAD_LETTER_REASON,
                        dlq.getDeadReason());
                variables.put(ProcessVariableConstants.IS_DEAD_LETTER, true);

                if (AsyncTaskType.CREDIT_QUERY.getCode().equals(taskType)) {
                    variables.put(ProcessVariableConstants.CREDIT_QUERY_ASYNC_STATUS,
                            ProcessVariableConstants.ASYNC_STATUS_DEAD_LETTER);
                    variables.put(ProcessVariableConstants.CREDIT_QUERY_SUCCESS, false);
                    variables.put(ProcessVariableConstants.CREDIT_QUERY_ERROR, dlq.getDeadReason());
                } else if (AsyncTaskType.CREDIT_SCORING.getCode().equals(taskType)) {
                    variables.put(ProcessVariableConstants.SCORING_ASYNC_STATUS,
                            ProcessVariableConstants.ASYNC_STATUS_DEAD_LETTER);
                    variables.put(ProcessVariableConstants.SCORE_PASS, false);
                    variables.put(ProcessVariableConstants.SCORING_ERROR, dlq.getDeadReason());
                }
                request.setVariables(variables);

                workflowCallbackService.transferToManualReview(request);
                log.info("[死信队列-消费者] 流程转人工完成, processInstanceId: {}, taskType: {}",
                        processInstanceId, taskType);

            } catch (Exception e) {
                log.error("[死信队列-消费者] 流程转人工失败, processInstanceId: {}, taskType: {}",
                        processInstanceId, taskType, e);
            }
        }

        private String findSignalByTaskType(String taskType) {
            if (AsyncTaskType.CREDIT_QUERY.getCode().equals(taskType)) {
                return ProcessVariableConstants.SIGNAL_CREDIT_QUERY_DONE;
            } else if (AsyncTaskType.CREDIT_SCORING.getCode().equals(taskType)) {
                return ProcessVariableConstants.SIGNAL_SCORING_DONE;
            }
            return null;
        }

        private void triggerSimpleAlert(String taskId, String body, long totalDead) {
            log.error("\n" +
                            "==============================\n" +
                            "⚠️  死信队列告警 (非标准格式)\n" +
                            "序号: {}\n" +
                            "TaskId: {}\n" +
                            "消息体摘要: {}\n" +
                            "==============================",
                    totalDead, taskId,
                    body != null && body.length() > 300 ? body.substring(0, 300) : body);
        }

        private void triggerAlert(DeadLetterMessage dlq,
                                   String taskType,
                                   String alertLevel,
                                   long totalDead) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n========================================\n")
                    .append("⚠️  死信队列告警 [").append(alertLevel).append("] #").append(totalDead).append("\n")
                    .append("----------------------------------------\n")
                    .append("任务类型: ").append(dlq.getTaskTypeName() != null ? dlq.getTaskTypeName() : taskType).append("\n")
                    .append("TaskId: ").append(dlq.getTaskId()).append("\n")
                    .append("申请编号: ").append(dlq.getApplicationNo() != null ? dlq.getApplicationNo() : "-").append("\n")
                    .append("申请ID: ").append(dlq.getApplicationId() != null ? dlq.getApplicationId() : "-").append("\n")
                    .append("客户ID: ").append(dlq.getCustomerId() != null ? dlq.getCustomerId() : "-").append("\n")
                    .append("流程Instance: ").append(dlq.getProcessInstanceId() != null ? dlq.getProcessInstanceId() : "-").append("\n")
                    .append("重试次数: ").append(dlq.getRetryCount() != null ? dlq.getRetryCount() : "-")
                    .append(" / ").append(dlq.getMaxRetry() != null ? dlq.getMaxRetry() : "-").append("\n")
                    .append("死信时间: ").append(dlq.getDeadTime() != null ? dlq.getDeadTime() : LocalDateTime.now()).append("\n")
                    .append("----------------------------------------\n")
                    .append("死信原因: ").append(dlq.getDeadReason() != null ? dlq.getDeadReason() : "").append("\n");

            if (dlq.getAlertMessage() != null && !dlq.getAlertMessage().isEmpty()) {
                sb.append("告警信息: ").append(dlq.getAlertMessage()).append("\n");
            }
            if (dlq.getLastError() != null && !dlq.getLastError().isEmpty()) {
                sb.append("最后错误: ").append(dlq.getLastError().length() > 300
                        ? dlq.getLastError().substring(0, 300) : dlq.getLastError()).append("\n");
            }

            sb.append("========================================");

            if ("CRITICAL".equalsIgnoreCase(alertLevel)) {
                log.error(sb.toString());
            } else {
                log.warn(sb.toString());
            }
        }
    }

    public long getDeadLetterCount() {
        return deadLetterCounter.get();
    }
}
