package com.bc.credit.integration.config;

import com.bc.credit.integration.adapter.CreditDataAdapter;
import com.bc.credit.integration.adapter.impl.BaihangCreditAdapter;
import com.bc.credit.integration.adapter.impl.HousingFundAdapter;
import com.bc.credit.integration.adapter.impl.PbocCreditAdapter;
import com.bc.credit.integration.adapter.impl.SocialSecurityAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Slf4j
@Configuration
public class CreditAdapterConfig {

    @Value("${credit.integration.timeout-ms:5000}")
    private int timeoutMs;

    @Value("${credit.integration.mock-enabled:true}")
    private boolean mockEnabled;

    @Value("${credit.integration.pboc.api-url:http://localhost:8081}")
    private String pbocApiUrl;

    @Value("${credit.integration.pboc.api-key:pboc-key}")
    private String pbocApiKey;

    @Value("${credit.integration.baihang.api-url:http://localhost:8082}")
    private String baihangApiUrl;

    @Value("${credit.integration.baihang.api-key:baihang-key}")
    private String baihangApiKey;

    @Value("${credit.integration.social-security.api-url:http://localhost:8083}")
    private String socialSecurityApiUrl;

    @Value("${credit.integration.social-security.api-key:ss-key}")
    private String socialSecurityApiKey;

    @Value("${credit.integration.housing-fund.api-url:http://localhost:8084}")
    private String housingFundApiUrl;

    @Value("${credit.integration.housing-fund.api-key:hf-key}")
    private String housingFundApiKey;

    @Bean
    public RestTemplate creditRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Bean
    public CreditDataAdapter pbocCreditAdapter(RestTemplate creditRestTemplate) {
        log.info("初始化央行征信适配器, mockEnabled: {}, timeoutMs: {}", mockEnabled, timeoutMs);
        return new PbocCreditAdapter(creditRestTemplate, pbocApiUrl, pbocApiKey, timeoutMs, mockEnabled);
    }

    @Bean
    public CreditDataAdapter baihangCreditAdapter(RestTemplate creditRestTemplate) {
        log.info("初始化百行征信适配器, mockEnabled: {}, timeoutMs: {}", mockEnabled, timeoutMs);
        return new BaihangCreditAdapter(creditRestTemplate, baihangApiUrl, baihangApiKey, timeoutMs, mockEnabled);
    }

    @Bean
    public CreditDataAdapter socialSecurityAdapter(RestTemplate creditRestTemplate) {
        log.info("初始化社保数据适配器, mockEnabled: {}, timeoutMs: {}", mockEnabled, timeoutMs);
        return new SocialSecurityAdapter(creditRestTemplate, socialSecurityApiUrl, socialSecurityApiKey, timeoutMs, mockEnabled);
    }

    @Bean
    public CreditDataAdapter housingFundAdapter(RestTemplate creditRestTemplate) {
        log.info("初始化公积金数据适配器, mockEnabled: {}, timeoutMs: {}", mockEnabled, timeoutMs);
        return new HousingFundAdapter(creditRestTemplate, housingFundApiUrl, housingFundApiKey, timeoutMs, mockEnabled);
    }
}
