package com.bc.credit.integration.adapter.impl;

import com.alibaba.fastjson2.JSONObject;
import com.bc.credit.common.enums.CreditDataSourceType;
import com.bc.credit.dto.credit.CreditQueryRequest;
import com.bc.credit.dto.credit.StructuredCreditData;
import com.bc.credit.integration.adapter.AbstractCreditDataAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

@Slf4j
public class SocialSecurityAdapter extends AbstractCreditDataAdapter {

    private static final Random RANDOM = new Random();

    public SocialSecurityAdapter(RestTemplate restTemplate, String apiUrl, String apiKey,
                                 int timeoutMs, boolean mockEnabled) {
        super(restTemplate, apiUrl, apiKey, timeoutMs, mockEnabled);
    }

    @Override
    public CreditDataSourceType getDataSourceType() {
        return CreditDataSourceType.SOCIAL_SECURITY;
    }

    @Override
    public String getDataSourceName() {
        return CreditDataSourceType.SOCIAL_SECURITY.getName();
    }

    @Override
    protected StructuredCreditData doQuery(CreditQueryRequest request) throws Exception {
        log.info("[社保数据] 调用社保数据API, url: {}", apiUrl);

        JSONObject requestBody = new JSONObject();
        requestBody.put("idCard", request.getIdCard());
        requestBody.put("name", request.getCustomerName());

        long start = System.currentTimeMillis();
        ResponseEntity<String> response = callApi(apiUrl + "/social/query", requestBody, String.class);
        long cost = System.currentTimeMillis() - start;

        log.info("[社保数据] API调用完成, cost: {}ms, status: {}", cost, response.getStatusCode());

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("社保数据API调用失败, status: " + response.getStatusCode());
        }

        JSONObject result = fromJson(response.getBody(), JSONObject.class);
        if (!"200".equals(result.getString("code"))) {
            throw new RuntimeException("社保数据返回错误: " + result.getString("msg"));
        }

        JSONObject data = result.getJSONObject("data");
        StructuredCreditData creditData = buildBaseData(request);

        int paymentMonths = data.getInteger("paymentMonths");
        BigDecimal monthlyBase = data.getBigDecimal("monthlyPaymentBase");
        boolean continuousPayment = data.getBooleanValue("continuousPayment");

        BigDecimal incomeReliability = calculateIncomeReliability(paymentMonths, monthlyBase, continuousPayment);
        creditData.setIncomeReliability(incomeReliability);

        creditData.setMultiLendingCount(0);
        creditData.setOverdueDays(0);
        creditData.setTotalDebtRatio(BigDecimal.ZERO);
        creditData.setCourtExecutionCount(0);

        return creditData;
    }

    @Override
    protected StructuredCreditData mockQuery(CreditQueryRequest request) {
        StructuredCreditData data = buildBaseData(request);

        int paymentMonths = 12 + RANDOM.nextInt(240);
        BigDecimal monthlyBase = BigDecimal.valueOf(3000 + RANDOM.nextInt(20000));
        boolean continuousPayment = RANDOM.nextBoolean();

        BigDecimal incomeReliability = calculateIncomeReliability(paymentMonths, monthlyBase, continuousPayment);
        data.setIncomeReliability(incomeReliability);

        data.setMultiLendingCount(0);
        data.setOverdueDays(0);
        data.setTotalDebtRatio(BigDecimal.ZERO);
        data.setCourtExecutionCount(0);

        log.info("[社保数据-MOCK] 生成模拟数据, paymentMonths: {}, monthlyBase: {}, incomeReliability: {}",
                paymentMonths, monthlyBase, incomeReliability);

        return data;
    }

    private BigDecimal calculateIncomeReliability(int paymentMonths, BigDecimal monthlyBase, boolean continuous) {
        double reliability = 50.0;

        if (paymentMonths >= 120) {
            reliability += 30;
        } else if (paymentMonths >= 60) {
            reliability += 20;
        } else if (paymentMonths >= 24) {
            reliability += 10;
        } else if (paymentMonths >= 12) {
            reliability += 5;
        }

        if (monthlyBase.compareTo(BigDecimal.valueOf(10000)) >= 0) {
            reliability += 15;
        } else if (monthlyBase.compareTo(BigDecimal.valueOf(5000)) >= 0) {
            reliability += 10;
        } else if (monthlyBase.compareTo(BigDecimal.valueOf(3000)) >= 0) {
            reliability += 5;
        }

        if (continuous) {
            reliability += 10;
        }

        reliability = Math.min(100, Math.max(0, reliability));
        return BigDecimal.valueOf(reliability).setScale(2, RoundingMode.HALF_UP);
    }
}
