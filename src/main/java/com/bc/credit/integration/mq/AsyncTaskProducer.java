package com.bc.credit.integration.mq;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.dto.credit.CreditQueryAsyncMessage;
import com.bc.credit.dto.credit.DeadLetterMessage;
import com.bc.credit.dto.credit.ScoringAsyncMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageConst;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class AsyncTaskProducer {

    @Autowired
    @Qualifier("asyncTaskProducer")
    private DefaultMQProducer asyncTaskProducer;

    @Value("${rocketmq.topic.credit-query-async:credit-async-query}")
    private String creditQueryTopic;

    @Value("${rocketmq.tag.credit-query-async:query}")
    private String creditQueryTag;

    @Value("${rocketmq.topic.scoring-async:credit-async-scoring}")
    private String scoringTopic;

    @Value("${rocketmq.tag.scoring-async:scoring}")
    private String scoringTag;

    @Value("${rocketmq.topic.dead-letter:credit-dead-letter}")
    private String deadLetterTopic;

    @Value("${rocketmq.tag.dead-letter:dead}")
    private String deadLetterTag;

    @Value("${rocketmq.topic.compensation:credit-compensation}")
    private String compensationTopic;

    @Value("${rocketmq.tag.compensation:compensation}")
    private String compensationTag;

    public String generateTaskId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public SendResult sendCreditQueryAsync(CreditQueryAsyncMessage message) {
        String taskId = message.getTaskId() != null ? message.getTaskId() : generateTaskId();
        message.setTaskId(taskId);
        if (message.getSubmitTime() == null) {
            message.setSubmitTime(LocalDateTime.now());
        }

        String body = JSON.toJSONString(message);
        String keys = message.getApplicationId() + "_" + message.getProcessInstanceId();

        return sendMessage(creditQueryTopic, creditQueryTag, taskId, keys, body,
                "征信查询异步任务", message.getApplicationNo() != null ? message.getApplicationNo() : "");
    }

    public SendResult sendScoringAsync(ScoringAsyncMessage message) {
        String taskId = message.getTaskId() != null ? message.getTaskId() : generateTaskId();
        message.setTaskId(taskId);
        if (message.getSubmitTime() == null) {
            message.setSubmitTime(LocalDateTime.now());
        }

        String body = JSON.toJSONString(message);
        String keys = message.getApplicationId() + "_" + message.getProcessInstanceId();

        return sendMessage(scoringTopic, scoringTag, taskId, keys, body,
                "评分卡异步任务", message.getApplicationNo() != null ? message.getApplicationNo() : "");
    }

    public SendResult sendDeadLetter(DeadLetterMessage message) {
        if (message.getDeadTime() == null) {
            message.setDeadTime(LocalDateTime.now());
        }

        String body = JSON.toJSONString(message);
        String keys = (message.getApplicationId() != null ? message.getApplicationId() : "UNK")
                + "_" + (message.getTaskId() != null ? message.getTaskId() : generateTaskId());

        return sendMessage(deadLetterTopic, deadLetterTag, message.getTaskId(), keys, body,
                "死信队列消息", message.getApplicationNo() != null ? message.getApplicationNo() : "");
    }

    public SendResult sendCompensation(String taskId, String taskType, String processInstanceId,
                                         Long applicationId, String reason) {
        String body = String.format("{\"taskId\":\"%s\",\"taskType\":\"%s\",\"processInstanceId\":\"%s\"," +
                        "\"applicationId\":%d,\"reason\":\"%s\",\"compensationTime\":\"%s\"}",
                taskId, taskType, processInstanceId, applicationId, reason, LocalDateTime.now());
        String keys = applicationId + "_" + processInstanceId;

        return sendMessage(compensationTopic, compensationTag, taskId, keys, body,
                "补偿任务", String.valueOf(applicationId));
    }

    private SendResult sendMessage(String topic, String tag, String taskId,
                                    String keys, String body, String bizName, String bizKey) {
        try {
            Message mqMessage = new Message(
                    topic,
                    tag,
                    taskId,
                    body.getBytes(StandardCharsets.UTF_8)
            );

            if (keys != null && !keys.isEmpty()) {
                mqMessage.setKeys(keys);
            }

            mqMessage.putUserProperty("taskId", taskId);
            mqMessage.putUserProperty("produceTime", String.valueOf(System.currentTimeMillis()));

            long start = System.currentTimeMillis();
            SendResult sendResult = asyncTaskProducer.send(mqMessage);
            long cost = System.currentTimeMillis() - start;

            if (SendStatus.SEND_OK.equals(sendResult.getSendStatus())) {
                log.info("[MQ-生产者-{}] 发送成功, taskId: {}, msgId: {}, keys: {}, bizKey: {}, cost: {}ms",
                        bizName, taskId, sendResult.getMsgId(), keys, bizKey, cost);
            } else {
                log.warn("[MQ-生产者-{}] 发送状态异常, taskId: {}, status: {}, keys: {}",
                        bizName, taskId, sendResult.getSendStatus(), keys);
            }

            return sendResult;

        } catch (Exception e) {
            log.error("[MQ-生产者-{}] 发送失败, taskId: {}, keys: {}, bizKey: {}, error: {}",
                    bizName, taskId, keys, bizKey, e.getMessage(), e);
            SendResult failResult = new SendResult();
            failResult.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
            return failResult;
        }
    }

    public SendResult sendDelayMessage(String topic, String tag, String taskId,
                                        String keys, String body, int delayLevel,
                                        String bizName, String bizKey) {
        try {
            Message mqMessage = new Message(
                    topic,
                    tag,
                    taskId,
                    body.getBytes(StandardCharsets.UTF_8)
            );

            if (keys != null && !keys.isEmpty()) {
                mqMessage.setKeys(keys);
            }

            mqMessage.setDelayTimeLevel(delayLevel);

            long start = System.currentTimeMillis();
            SendResult sendResult = asyncTaskProducer.send(mqMessage);
            long cost = System.currentTimeMillis() - start;

            if (SendStatus.SEND_OK.equals(sendResult.getSendStatus())) {
                log.info("[MQ-生产者-{}-延迟] 发送成功, taskId: {}, delayLevel: {}, msgId: {}, cost: {}ms",
                        bizName, taskId, delayLevel, sendResult.getMsgId(), cost);
            }

            return sendResult;

        } catch (Exception e) {
            log.error("[MQ-生产者-{}-延迟] 发送失败, taskId: {}, error: {}", bizName, taskId, e.getMessage(), e);
            SendResult failResult = new SendResult();
            failResult.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
            return failResult;
        }
    }
}
