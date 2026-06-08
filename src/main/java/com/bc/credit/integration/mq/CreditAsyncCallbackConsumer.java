package com.bc.credit.integration.mq;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.dto.credit.CreditAsyncCallbackMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class CreditAsyncCallbackConsumer {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.consumer.group:credit-consumer-group}")
    private String consumerGroup;

    @Value("${rocketmq.topic.credit-callback:credit-async-callback}")
    private String callbackTopic;

    @Value("${rocketmq.tag.credit-callback:credit}")
    private String callbackTag;

    @Value("${credit.integration.async.callback-timeout-ms:10000}")
    private int callbackTimeoutMs;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void init() {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeMessageBatchMaxSize(1);

        try {
            consumer.subscribe(callbackTopic, callbackTag);

            consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                for (MessageExt msg : msgs) {
                    try {
                        return handleMessage(msg);
                    } catch (Exception e) {
                        log.error("[MQ-消费者] 处理消息异常, msgId: {}", msg.getMsgId(), e);
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });

            consumer.start();
            log.info("[MQ-消费者] 启动成功, topic: {}, tag: {}, group: {}",
                    callbackTopic, callbackTag, consumerGroup);

        } catch (Exception e) {
            log.error("[MQ-消费者] 启动失败", e);
            throw new RuntimeException("RocketMQ消费者启动失败", e);
        }
    }

    private ConsumeConcurrentlyStatus handleMessage(MessageExt msg) {
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
        log.info("[MQ-消费者] 收到回调消息, msgId: {}, reconsumeTimes: {}, body: {}",
                msg.getMsgId(), msg.getReconsumeTimes(), body);

        try {
            CreditAsyncCallbackMessage callbackMessage = JSON.parseObject(body, CreditAsyncCallbackMessage.class);

            boolean success = executeCallback(callbackMessage);

            if (success) {
                log.info("[MQ-消费者] 消息处理成功, queryId: {}", callbackMessage.getQueryId());
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } else {
                log.warn("[MQ-消费者] 消息处理失败, queryId: {}, 稍后重试", callbackMessage.getQueryId());
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }

        } catch (Exception e) {
            log.error("[MQ-消费者] 消息处理异常, msgId: {}", msg.getMsgId(), e);
            if (msg.getReconsumeTimes() >= 3) {
                log.error("[MQ-消费者] 消息重试超过3次, 跳过处理, msgId: {}", msg.getMsgId());
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }

    private boolean executeCallback(CreditAsyncCallbackMessage message) {
        if (message.getCallbackUrl() == null || message.getCallbackUrl().isEmpty()) {
            log.info("[MQ-回调] 无回调URL, 跳过HTTP回调, queryId: {}", message.getQueryId());
            return true;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Query-ID", message.getQueryId());
            headers.set("X-Application-ID", String.valueOf(message.getApplicationId()));

            HttpEntity<CreditAsyncCallbackMessage> request = new HttpEntity<>(message, headers);

            long start = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(
                    message.getCallbackUrl(),
                    HttpMethod.POST,
                    request,
                    String.class
            );
            long cost = System.currentTimeMillis() - start;

            log.info("[MQ-回调] HTTP回调完成, queryId: {}, url: {}, status: {}, cost: {}ms",
                    message.getQueryId(), message.getCallbackUrl(), response.getStatusCode(), cost);

            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            log.error("[MQ-回调] HTTP回调失败, queryId: {}, url: {}, error: {}",
                    message.getQueryId(), message.getCallbackUrl(), e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
            log.info("[MQ-消费者] 已关闭");
        }
    }
}
