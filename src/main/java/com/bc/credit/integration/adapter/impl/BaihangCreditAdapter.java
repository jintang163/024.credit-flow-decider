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
public class BaihangCreditAdapter extends AbstractCreditDataAdapter {

    private static final Random RANDOM = new Random();

    public BaihangCreditAdapter(RestTemplate restTemplate, String apiUrl, String apiKey,
                                int timeoutMs, boolean mockEnabled) {
        super(restTemplate, apiUrl, apiKey, timeoutMs, mockEnabled);
    }

    @Override
    public CreditDataSourceType getDataSourceType() {
        return CreditDataSourceType.BAIHANG;
    }

    @Override
    public String getDataSourceName() {
        return CreditDataSourceType.BAIHANG.getName();
    }

    @Override
    protected StructuredCreditData doQuery(CreditQueryRequest request) throws Exception {
        log.info("[百行征信] 调用百行征信API, url: {}", apiUrl);

        JSONObject requestBody = new JSONObject();
        requestBody.put("name", request.getCustomerName());
        requestBody.put("idCard", request.getIdCard());
        requestBody.put("phone", request.getPhone());
        requestBody.put("queryType", "CREDIT_REPORT");

        long start = System.currentTimeMillis();
        ResponseEntity<String> response = callApi(apiUrl + "/baihang/query", requestBody, String.class);
        long cost = System.currentTimeMillis() - start;

        log.info("[百行征信] API调用完成, cost: {}ms, status: {}", cost, response.getStatusCode());

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("百行征信API调用失败, status: " + response.getStatusCode());
        }

        JSONObject result = fromJson(response.getBody(), JSONObject.class);
        if (!"0000".equals(result.getString("respCode"))) {
            throw new RuntimeException("百行征信返回错误: " + result.getString("respMsg"));
        }

        JSONObject data = result.getJSONObject("data");
        StructuredCreditData creditData = buildBaseData(request);
        creditData.setMultiLendingCount(data.getInteger("multipleLendingCount"));
        creditData.setOverdueDays(data.getInteger("maxOverdueDays"));
        creditData.setTotalDebtRatio(data.getBigDecimal("debtLevel"));
        creditData.setIncomeReliability(data.getBigDecimal("incomeStability"));
        creditData.setCourtExecutionCount(data.getInteger("enforcementCount"));
        creditData.setCourtExecutionDetails(data.getObject("enforcementList", ArrayList.class));

        return creditData;
    }

    @Override
    protected StructuredCreditData mockQuery(CreditQueryRequest request) {
        StructuredCreditData data = buildBaseData(request);

        data.setMultiLendingCount(RANDOM.nextInt(12));
        data.setOverdueDays(RANDOM.nextInt(60));

        BigDecimal debtRatio = BigDecimal.valueOf(0.3 + RANDOM.nextDouble() * 0.5)
                .setScale(4, RoundingMode.HALF_UP);
        data.setTotalDebtRatio(debtRatio);

        BigDecimal incomeRel = BigDecimal.valueOf(40 + RANDOM.nextInt(60))
                .setScale(2, RoundingMode.HALF_UP);
        data.setIncomeReliability(incomeRel);

        int courtCount = RANDOM.nextInt(2);
        data.setCourtExecutionCount(courtCount);

        if (courtCount > 0) {
            java.util.List<String> details = new ArrayList<>();
            for (int i = 0; i < courtCount; i++) {
                details.add("百行-执行记录" + (i + 1) + ": 涉诉金额" + (RANDOM.nextInt(50000) + 5000) + "元");
            }
            data.setCourtExecutionDetails(details);
        }

        log.info("[百行征信-MOCK] 生成模拟数据, multiLendingCount: {}, overdueDays: {}, debtRatio: {}",
                data.getMultiLendingCount(), data.getOverdueDays(), data.getTotalDebtRatio());

        return data;
    }
}
