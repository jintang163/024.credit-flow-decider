package com.bc.credit.integration.adapter;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.dto.credit.CreditQueryRequest;
import com.bc.credit.dto.credit.StructuredCreditData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class AbstractCreditDataAdapter implements CreditDataAdapter {

    protected RestTemplate restTemplate;

    protected String apiUrl;

    protected String apiKey;

    protected int timeoutMs;

    protected boolean mockEnabled;

    protected AbstractCreditDataAdapter(RestTemplate restTemplate, String apiUrl,
                                        String apiKey, int timeoutMs, boolean mockEnabled) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutMs;
        this.mockEnabled = mockEnabled;
    }

    @Override
    public StructuredCreditData query(CreditQueryRequest request) throws Exception {
        log.info("[{}] 开始查询征信数据, customerId: {}, idCard: {}",
                getDataSourceName(), request.getCustomerId(), maskIdCard(request.getIdCard()));

        if (mockEnabled) {
            log.info("[{}] Mock模式已启用，返回模拟数据", getDataSourceName());
            return mockQuery(request);
        }

        return doQuery(request);
    }

    protected abstract StructuredCreditData doQuery(CreditQueryRequest request) throws Exception;

    protected abstract StructuredCreditData mockQuery(CreditQueryRequest request);

    protected ResponseEntity<String> callApi(String url, Object requestBody, Class<String> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("X-Request-ID", java.util.UUID.randomUUID().toString());

        HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, responseType);
    }

    protected StructuredCreditData buildBaseData(CreditQueryRequest request) {
        StructuredCreditData data = new StructuredCreditData();
        data.setCustomerId(request.getCustomerId());
        data.setIdCard(request.getIdCard());
        data.setQueryTime(LocalDateTime.now());
        data.setQueryId(java.util.UUID.randomUUID().toString().replace("-", ""));

        Map<String, Boolean> status = new HashMap<>();
        status.put(getDataSourceType().getCode(), true);
        data.setDataSourceStatus(status);

        return data;
    }

    protected String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    protected String toJson(Object obj) {
        return JSON.toJSONString(obj);
    }

    protected <T> T fromJson(String json, Class<T> clazz) {
        return JSON.parseObject(json, clazz);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
