package com.bc.credit.integration.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RocketMQConfig {

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.producer.group:credit-producer-group}")
    private String producerGroup;

    @Value("${rocketmq.producer.send-message-timeout:3000}")
    private int sendMessageTimeout;

    @Value("${rocketmq.producer.retry-times-when-send-failed:2}")
    private int retryTimesWhenSendFailed;

    @Bean
    public DefaultMQProducer creditMqProducer() {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(sendMessageTimeout);
        producer.setRetryTimesWhenSendFailed(retryTimesWhenSendFailed);
        producer.setRetryAnotherBrokerWhenNotStoreOK(true);

        try {
            producer.start();
            log.info("[RocketMQ] 生产者启动成功, nameServer: {}, group: {}", nameServer, producerGroup);
        } catch (Exception e) {
            log.error("[RocketMQ] 生产者启动失败", e);
            throw new RuntimeException("RocketMQ生产者启动失败", e);
        }

        return producer;
    }
}
