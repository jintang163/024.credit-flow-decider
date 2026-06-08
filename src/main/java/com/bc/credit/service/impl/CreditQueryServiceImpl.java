package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.dto.CreditDataDTO;
import com.bc.credit.entity.CreditQueryRecord;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.CreditQueryRecordMapper;
import com.bc.credit.service.CreditQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
public class CreditQueryServiceImpl implements CreditQueryService {

    @Autowired
    private CreditQueryRecordMapper creditQueryRecordMapper;

    private static final Random RANDOM = new Random();

    @Override
    public CreditDataDTO queryCredit(LoanApplication application) {
        log.info("开始查询客户征信信息, customerId: {}, applicationNo: {}",
                application.getCustomerId(), application.getApplicationNo());

        CreditDataDTO creditData = new CreditDataDTO();
        creditData.setCustomerId(application.getCustomerId());
        creditData.setSuccess(true);

        int baseScore = 650;
        int randomDelta = RANDOM.nextInt(200) - 50;
        int creditScore = Math.max(300, Math.min(850, baseScore + randomDelta));
        creditData.setCreditScore(creditScore);

        if (creditScore >= 750) {
            creditData.setCreditLevel("A");
        } else if (creditScore >= 650) {
            creditData.setCreditLevel("B");
        } else if (creditScore >= 550) {
            creditData.setCreditLevel("C");
        } else if (creditScore >= 450) {
            creditData.setCreditLevel("D");
        } else {
            creditData.setCreditLevel("E");
        }

        int overdueCount = RANDOM.nextInt(10);
        creditData.setOverdueCount(overdueCount);
        creditData.setOverdueAmount(BigDecimal.valueOf(RANDOM.nextDouble() * 50000).setScale(2, BigDecimal.ROUND_HALF_UP));
        creditData.setTotalLoanAmount(BigDecimal.valueOf(RANDOM.nextDouble() * 500000).setScale(2, BigDecimal.ROUND_HALF_UP));
        creditData.setRemainingLoanAmount(BigDecimal.valueOf(RANDOM.nextDouble() * 200000).setScale(2, BigDecimal.ROUND_HALF_UP));
        creditData.setCreditCardCount(RANDOM.nextInt(8));
        creditData.setCreditCardLimit(BigDecimal.valueOf(RANDOM.nextDouble() * 200000).setScale(2, BigDecimal.ROUND_HALF_UP));
        creditData.setCreditCardUsed(BigDecimal.valueOf(RANDOM.nextDouble() * 100000).setScale(2, BigDecimal.ROUND_HALF_UP));

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("score", creditScore);
        resultMap.put("level", creditData.getCreditLevel());
        resultMap.put("overdueCount", overdueCount);
        resultMap.put("queryTime", LocalDateTime.now().toString());
        resultMap.put("source", "MOCK_PBOC");
        creditData.setQueryResult(JSON.toJSONString(resultMap));

        log.info("征信查询完成, customerId: {}, score: {}, level: {}",
                application.getCustomerId(), creditScore, creditData.getCreditLevel());

        return creditData;
    }

    @Override
    public CreditQueryRecord saveCreditRecord(LoanApplication application, CreditDataDTO creditData) {
        CreditQueryRecord record = new CreditQueryRecord();
        record.setId(IdWorker.getId());
        record.setApplicationId(application.getId());
        record.setApplicationNo(application.getApplicationNo());
        record.setCustomerId(application.getCustomerId());
        record.setQueryType("PERSONAL");
        record.setQueryChannel("BIGDATA");
        record.setCreditScore(creditData.getCreditScore());
        record.setCreditLevel(creditData.getCreditLevel());
        record.setOverdueCount(creditData.getOverdueCount());
        record.setOverdueAmount(creditData.getOverdueAmount());
        record.setTotalLoanAmount(creditData.getTotalLoanAmount());
        record.setRemainingLoanAmount(creditData.getRemainingLoanAmount());
        record.setCreditCardCount(creditData.getCreditCardCount());
        record.setCreditCardLimit(creditData.getCreditCardLimit());
        record.setCreditCardUsed(creditData.getCreditCardUsed());
        record.setQueryResult(creditData.getQueryResult());
        record.setQueryTime(LocalDateTime.now());
        record.setSuccess(creditData.getSuccess() ? 1 : 0);
        record.setErrorMsg(creditData.getErrorMsg());
        record.setCreatedTime(LocalDateTime.now());
        record.setDeleted(0);

        creditQueryRecordMapper.insert(record);
        return record;
    }
}
