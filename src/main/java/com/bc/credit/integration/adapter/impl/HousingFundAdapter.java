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
public class HousingFundAdapter extends AbstractCreditDataAdapter {

    private static final Random RANDOM = new Random();

    public HousingFundAdapter(RestTemplate restTemplate, String apiUrl, String apiKey,
                              int timeoutMs, boolean mockEnabled) {
        super(restTemplate, apiUrl, apiKey, timeoutMs, mockEnabled);
    }

    @Override
    public CreditDataSourceType getDataSourceType() {
        return CreditDataSourceType.HOUSING_FUND;
    }

    @Override
    public String getDataSourceName() {
        return CreditDataSourceType.HOUSING_FUND.getName();
    }

    @Override
    protected StructuredCreditData doQuery(CreditQueryRequest request) throws Exception {
        log.info("[公积金数据] 调用公积金数据API, url: {}", apiUrl);

        JSONObject requestBody = new JSONObject();
        requestBody.put("idCard", request.getIdCard());
        requestBody.put("name", request.getCustomerName());

        long start = System.currentTimeMillis();
        ResponseEntity<String> response = callApi(apiUrl + "/housingfund/query", requestBody, String.class);
        long cost = System.currentTimeMillis() - start;

        log.info("[公积金数据] API调用完成, cost: {}ms, status: {}", cost, response.getStatusCode());

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("公积金数据API调用失败, status: " + response.getStatusCode());
        }

        JSONObject result = fromJson(response.getBody(), JSONObject.class);
        if (!"SUCCESS".equals(result.getString("status"))) {
            throw new RuntimeException("公积金数据返回错误: " + result.getString("message"));
        }

        JSONObject data = result.getJSONObject("data");
        StructuredCreditData creditData = buildBaseData(request);

        int paymentMonths = data.getInteger("paymentMonths");
        BigDecimal monthlyPayment = data.getBigDecimal("monthlyPayment");
        BigDecimal balance = data.getBigDecimal("balance");
        boolean hasLoan = data.getBooleanValue("hasOutstandingLoan");

        BigDecimal incomeReliability = calculateIncomeReliability(paymentMonths, monthlyPayment);
        creditData.setIncomeReliability(incomeReliability);

        if (hasLoan) {
            creditData.setTotalDebtRatio(BigDecimal.valueOf(0.3 + RANDOM.nextDouble() * 0.3));
        } else {
            creditData.setTotalDebtRatio(BigDecimal.valueOf(RANDOM.nextDouble() * 0.3));
        }

        creditData.setMultiLendingCount(0);
        creditData.setOverdueDays(0);
        creditData.setCourtExecutionCount(0);

        return creditData;
    }

    @Override
    protected StructuredCreditData mockQuery(CreditQueryRequest request) {
        StructuredCreditData data = buildBaseData(request);

        int paymentMonths = 12 + RANDOM.nextInt(240);
        BigDecimal monthlyPayment = BigDecimal.valueOf(200 + RANDOM.nextInt(5000));
        BigDecimal balance = BigDecimal.valueOf(RANDOM.nextInt(200000));
        boolean hasLoan = RANDOM.nextBoolean();

        BigDecimal incomeReliability = calculateIncomeReliability(paymentMonths, monthlyPayment);
        data.setIncomeReliability(incomeReliability);

        if (hasLoan) {
            data.setTotalDebtRatio(BigDecimal.valueOf(0.3 + RANDOM.nextDouble() * 0.3)
                    .setScale(4, RoundingMode.HALF_UP));
        } else {
            data.setTotalDebtRatio(BigDecimal.valueOf(RANDOM.nextDouble() * 0.3)
                    .setScale(4, RoundingMode.HALF_UP));
        }

        data.setMultiLendingCount(0);
        data.setOverdueDays(0);
        data.setCourtExecutionCount(0);

        log.info("[公积金数据-MOCK] 生成模拟数据, paymentMonths: {}, monthlyPayment: {}, balance: {}, hasLoan: {}",
                paymentMonths, monthlyPayment, balance, hasLoan);

        return data;
    }

    private BigDecimal calculateIncomeReliability(int paymentMonths, BigDecimal monthlyPayment) {
        double reliability = 50.0;

        if (paymentMonths >= 120) {
            reliability += 25;
        } else if (paymentMonths >= 60) {
            reliability += 18;
        } else if (paymentMonths >= 24) {
            reliability += 10;
        } else if (paymentMonths >= 12) {
            reliability += 5;
        }

        if (monthlyPayment.compareTo(BigDecimal.valueOf(3000)) >= 0) {
            reliability += 20;
        } else if (monthlyPayment.compareTo(BigDecimal.valueOf(1500)) >= 0) {
            reliability += 12;
        } else if (monthlyPayment.compareTo(BigDecimal.valueOf(500)) >= 0) {
            reliability += 6;
        }

        reliability = Math.min(100, Math.max(0, reliability));
        return BigDecimal.valueOf(reliability).setScale(2, RoundingMode.HALF_UP);
    }
}
