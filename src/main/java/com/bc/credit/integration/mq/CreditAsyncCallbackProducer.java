package com.bc.credit.integration.mq;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.dto.credit.CreditAsyncCallbackMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class CreditAsyncCallbackProducer {

    @Autowired
    private DefaultMQProducer creditMqProducer;

    @Value("${rocketmq.topic.credit-callback:credit-async-callback}")
    private String callbackTopic;

    @Value("${rocketmq.tag.credit-callback:credit}")
    private String callbackTag;

    public boolean sendCallbackMessage(CreditAsyncCallbackMessage message) {
        try {
            String messageBody = JSON.toJSONString(message);
            Message mqMessage = new Message(
                    callbackTopic,
                    callbackTag,
                    message.getQueryId(),
                    messageBody.getBytes(StandardCharsets.UTF_8)
            );

            mqMessage.setKeys(message.getApplicationId() + "_" + message.getCustomerId());

            log.info("[MQ-生产者] 发送征信异步回调消息, queryId: {}, applicationId: {}, customerId: {}",
                    message.getQueryId(), message.getApplicationId(), message.getCustomerId());

            long start = System.currentTimeMillis();
            SendResult sendResult = creditMqProducer.send(mqMessage);
            long cost = System.currentTimeMillis() - start;

            if (SendStatus.SEND_OK.equals(sendResult.getSendStatus())) {
                log.info("[MQ-生产者] 消息发送成功, queryId: {}, msgId: {}, cost: {}ms",
                        message.getQueryId(), sendResult.getMsgId(), cost);
                return true;
            } else {
                log.warn("[MQ-生产者] 消息发送状态异常, queryId: {}, status: {}",
                        message.getQueryId(), sendResult.getSendStatus());
                return false;
            }

        } catch (Exception e) {
            log.error("[MQ-生产者] 消息发送失败, queryId: {}, error: {}",
                    message.getQueryId(), e.getMessage(), e);
            return false;
        }
    }

    public boolean sendAsyncQueryRequest(String queryId, Object requestData) {
        try {
            String messageBody = JSON.toJSONString(requestData);
            Message mqMessage = new Message(
                    "credit-async-query",
                    "query",
                    queryId,
                    messageBody.getBytes(StandardCharsets.UTF_8)
            );

            log.info("[MQ-生产者] 发送异步查询请求, queryId: {}", queryId);

            SendResult sendResult = creditMqProducer.send(mqMessage);

            if (SendStatus.SEND_OK.equals(sendResult.getSendStatus())) {
                log.info("[MQ-生产者] 异步查询请求发送成功, queryId: {}, msgId: {}",
                        queryId, sendResult.getMsgId());
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("[MQ-生产者] 异步查询请求发送失败, queryId: {}", queryId, e);
            return false;
        }
    }
}
