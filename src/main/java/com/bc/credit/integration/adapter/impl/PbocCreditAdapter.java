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
import java.util.ArrayList;
import java.util.Random;

@Slf4j
public class PbocCreditAdapter extends AbstractCreditDataAdapter {

    private static final Random RANDOM = new Random();

    public PbocCreditAdapter(RestTemplate restTemplate, String apiUrl, String apiKey,
                             int timeoutMs, boolean mockEnabled) {
        super(restTemplate, apiUrl, apiKey, timeoutMs, mockEnabled);
    }

    @Override
    public CreditDataSourceType getDataSourceType() {
        return CreditDataSourceType.PBOC;
    }

    @Override
    public String getDataSourceName() {
        return CreditDataSourceType.PBOC.getName();
    }

    @Override
    protected StructuredCreditData doQuery(CreditQueryRequest request) throws Exception {
        log.info("[央行征信] 调用央行征信API, url: {}", apiUrl);

        JSONObject requestBody = new JSONObject();
        requestBody.put("name", request.getCustomerName());
        requestBody.put("idCard", request.getIdCard());
        requestBody.put("phone", request.getPhone());
        requestBody.put("queryReason", "LOAN_APPROVAL");

        long start = System.currentTimeMillis();
        ResponseEntity<String> response = callApi(apiUrl + "/pboc/query", requestBody, String.class);
        long cost = System.currentTimeMillis() - start;

        log.info("[央行征信] API调用完成, cost: {}ms, status: {}", cost, response.getStatusCode());

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("央行征信API调用失败, status: " + response.getStatusCode());
        }

        JSONObject result = fromJson(response.getBody(), JSONObject.class);
        if (!"SUCCESS".equals(result.getString("code"))) {
            throw new RuntimeException("央行征信返回错误: " + result.getString("message"));
        }

        JSONObject data = result.getJSONObject("data");
        StructuredCreditData creditData = buildBaseData(request);
        creditData.setMultiLendingCount(data.getInteger("multiLendingCount"));
        creditData.setOverdueDays(data.getInteger("overdueDays"));
        creditData.setTotalDebtRatio(data.getBigDecimal("totalDebtRatio"));
        creditData.setIncomeReliability(data.getBigDecimal("incomeReliability"));
        creditData.setCourtExecutionCount(data.getInteger("courtExecutionCount"));
        creditData.setCourtExecutionDetails(data.getObject("courtExecutionDetails", ArrayList.class));

        return creditData;
    }

    @Override
    protected StructuredCreditData mockQuery(CreditQueryRequest request) {
        StructuredCreditData data = buildBaseData(request);

        data.setMultiLendingCount(RANDOM.nextInt(15));
        data.setOverdueDays(RANDOM.nextInt(90));

        BigDecimal debtRatio = BigDecimal.valueOf(RANDOM.nextDouble() * 0.8)
                .setScale(4, RoundingMode.HALF_UP);
        data.setTotalDebtRatio(debtRatio);

        BigDecimal incomeRel = BigDecimal.valueOf(50 + RANDOM.nextInt(50))
                .setScale(2, RoundingMode.HALF_UP);
        data.setIncomeReliability(incomeRel);

        int courtCount = RANDOM.nextInt(3);
        data.setCourtExecutionCount(courtCount);

        if (courtCount > 0) {
            java.util.List<String> details = new ArrayList<>();
            for (int i = 0; i < courtCount; i++) {
                details.add("执行案件" + (i + 1) + ": 金额" + (RANDOM.nextInt(100000) + 10000) + "元");
            }
            data.setCourtExecutionDetails(details);
        }

        log.info("[央行征信-MOCK] 生成模拟数据, multiLendingCount: {}, overdueDays: {}, debtRatio: {}",
                data.getMultiLendingCount(), data.getOverdueDays(), data.getTotalDebtRatio());

        return data;
    }
}
