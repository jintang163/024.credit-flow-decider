package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.CreditDataSourceType;
import com.bc.credit.dto.CreditDataDTO;
import com.bc.credit.dto.credit.CreditQueryRequest;
import com.bc.credit.dto.credit.CreditQueryResponse;
import com.bc.credit.dto.credit.StructuredCreditData;
import com.bc.credit.entity.CreditQueryRecord;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.integration.service.CreditIntegrationService;
import com.bc.credit.mapper.CreditQueryRecordMapper;
import com.bc.credit.service.CreditQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
public class CreditQueryServiceImpl implements CreditQueryService {

    @Autowired
    private CreditQueryRecordMapper creditQueryRecordMapper;

    @Autowired(required = false)
    private CreditIntegrationService creditIntegrationService;

    @Value("${credit.integration.use-new-service:true}")
    private boolean useNewIntegrationService;

    private static final Random RANDOM = new Random();

    @Override
    public CreditDataDTO queryCredit(LoanApplication application) {
        log.info("开始查询客户征信信息, customerId: {}, applicationNo: {}, useNewService: {}",
                application.getCustomerId(), application.getApplicationNo(), useNewIntegrationService);

        if (useNewIntegrationService && creditIntegrationService != null) {
            return queryCreditWithNewService(application);
        }

        return queryCreditWithMock(application);
    }

    private CreditDataDTO queryCreditWithNewService(LoanApplication application) {
        log.info("[新征信服务] 调用集成征信服务, customerId: {}", application.getCustomerId());

        try {
            CreditQueryRequest request = new CreditQueryRequest();
            request.setCustomerId(application.getCustomerId());
            request.setCustomerName(application.getCustomerName());
            request.setIdCard(application.getIdCard());
            request.setPhone(application.getPhone());
            request.setApplicationId(application.getId());
            request.setApplicationNo(application.getApplicationNo());
            request.setDataSources(Arrays.asList(
                    CreditDataSourceType.PBOC,
                    CreditDataSourceType.BAIHANG,
                    CreditDataSourceType.SOCIAL_SECURITY,
                    CreditDataSourceType.HOUSING_FUND
            ));

            CreditQueryResponse response = creditIntegrationService.queryCreditSync(request);

            log.info("[新征信服务] 查询完成, queryId: {}, success: {}, allSuccess: {}, qualityTag: {}",
                    response.getQueryId(), response.isSuccess(),
                    response.isAllSuccess(), response.getQualityTag());

            return convertToCreditDataDTO(response, application);

        } catch (Exception e) {
            log.error("[新征信服务] 查询失败，降级使用模拟数据, customerId: {}, error: {}",
                    application.getCustomerId(), e.getMessage(), e);
            return queryCreditWithMock(application);
        }
    }

    private CreditDataDTO convertToCreditDataDTO(CreditQueryResponse response, LoanApplication application) {
        CreditDataDTO creditData = new CreditDataDTO();
        creditData.setCustomerId(application.getCustomerId());
        creditData.setSuccess(response.isSuccess());

        StructuredCreditData structuredData = response.getData();

        int creditScore = calculateCreditScore(structuredData);
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

        int overdueCount = structuredData.getOverdueDays() != null && structuredData.getOverdueDays() < Integer.MAX_VALUE
                ? Math.min(structuredData.getOverdueDays() / 30 + 1, 10)
                : RANDOM.nextInt(10);
        creditData.setOverdueCount(overdueCount);
        creditData.setOverdueAmount(BigDecimal.valueOf(RANDOM.nextDouble() * 50000).setScale(2, RoundingMode.HALF_UP));
        creditData.setTotalLoanAmount(BigDecimal.valueOf(RANDOM.nextDouble() * 500000).setScale(2, RoundingMode.HALF_UP));

        BigDecimal remainingAmount = structuredData.getTotalDebtRatio() != null
                ? application.getLoanAmount().multiply(structuredData.getTotalDebtRatio()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(RANDOM.nextDouble() * 200000).setScale(2, RoundingMode.HALF_UP);
        creditData.setRemainingLoanAmount(remainingAmount);

        creditData.setCreditCardCount(RANDOM.nextInt(8));
        creditData.setCreditCardLimit(BigDecimal.valueOf(RANDOM.nextDouble() * 200000).setScale(2, RoundingMode.HALF_UP));
        creditData.setCreditCardUsed(BigDecimal.valueOf(RANDOM.nextDouble() * 100000).setScale(2, RoundingMode.HALF_UP));

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("score", creditScore);
        resultMap.put("level", creditData.getCreditLevel());
        resultMap.put("overdueCount", overdueCount);
        resultMap.put("queryTime", LocalDateTime.now().toString());
        resultMap.put("source", "INTEGRATED_CREDIT");
        resultMap.put("queryId", response.getQueryId());
        resultMap.put("qualityTag", response.getQualityTag() != null ? response.getQualityTag().getCode() : "NORMAL");
        resultMap.put("allSuccess", response.isAllSuccess());
        resultMap.put("failedDataSources", response.getFailedDataSources());
        resultMap.put("multiLendingCount", structuredData.getMultiLendingCount());
        resultMap.put("overdueDays", structuredData.getOverdueDays());
        resultMap.put("totalDebtRatio", structuredData.getTotalDebtRatio());
        resultMap.put("incomeReliability", structuredData.getIncomeReliability());
        resultMap.put("courtExecutionCount", structuredData.getCourtExecutionCount());
        resultMap.put("pendingReview", structuredData.isPendingReview());

        creditData.setQueryResult(JSON.toJSONString(resultMap));

        if (!response.isAllSuccess()) {
            creditData.setErrorMsg("部分数据源查询失败: " + String.join(",", response.getFailedDataSources()));
        }

        log.info("[新征信服务] 转换完成, customerId: {}, score: {}, level: {}, pendingReview: {}",
                application.getCustomerId(), creditScore, creditData.getCreditLevel(),
                structuredData.isPendingReview());

        return creditData;
    }

    private int calculateCreditScore(StructuredCreditData data) {
        int score = 650;

        if (data.getMultiLendingCount() != null && data.getMultiLendingCount() < Integer.MAX_VALUE) {
            if (data.getMultiLendingCount() == 0) {
                score += 50;
            } else if (data.getMultiLendingCount() <= 3) {
                score += 30;
            } else if (data.getMultiLendingCount() <= 5) {
                score += 10;
            } else if (data.getMultiLendingCount() <= 10) {
                score -= 20;
            } else {
                score -= 50;
            }
        }

        if (data.getOverdueDays() != null && data.getOverdueDays() < Integer.MAX_VALUE) {
            if (data.getOverdueDays() == 0) {
                score += 80;
            } else if (data.getOverdueDays() <= 30) {
                score += 30;
            } else if (data.getOverdueDays() <= 90) {
                score -= 30;
            } else {
                score -= 80;
            }
        }

        if (data.getTotalDebtRatio() != null) {
            BigDecimal ratio = data.getTotalDebtRatio();
            if (ratio.compareTo(new BigDecimal("0.3")) < 0) {
                score += 40;
            } else if (ratio.compareTo(new BigDecimal("0.5")) < 0) {
                score += 20;
            } else if (ratio.compareTo(new BigDecimal("0.7")) < 0) {
                score -= 20;
            } else {
                score -= 50;
            }
        }

        if (data.getIncomeReliability() != null) {
            BigDecimal reliability = data.getIncomeReliability();
            if (reliability.compareTo(new BigDecimal("80")) >= 0) {
                score += 30;
            } else if (reliability.compareTo(new BigDecimal("60")) >= 0) {
                score += 15;
            } else if (reliability.compareTo(new BigDecimal("40")) >= 0) {
                score -= 10;
            } else {
                score -= 30;
            }
        }

        if (data.getCourtExecutionCount() != null && data.getCourtExecutionCount() < Integer.MAX_VALUE) {
            if (data.getCourtExecutionCount() == 0) {
                score += 20;
            } else if (data.getCourtExecutionCount() <= 2) {
                score -= 50;
            } else {
                score -= 100;
            }
        }

        if (data.isPendingReview()) {
            score -= 50;
        }

        return Math.max(300, Math.min(850, score));
    }

    private CreditDataDTO queryCreditWithMock(LoanApplication application) {
        log.info("[模拟数据] 使用模拟征信数据, customerId: {}", application.getCustomerId());

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
        creditData.setOverdueAmount(BigDecimal.valueOf(RANDOM.nextDouble() * 50000).setScale(2, RoundingMode.HALF_UP));
        creditData.setTotalLoanAmount(BigDecimal.valueOf(RANDOM.nextDouble() * 500000).setScale(2, RoundingMode.HALF_UP));
        creditData.setRemainingLoanAmount(BigDecimal.valueOf(RANDOM.nextDouble() * 200000).setScale(2, RoundingMode.HALF_UP));
        creditData.setCreditCardCount(RANDOM.nextInt(8));
        creditData.setCreditCardLimit(BigDecimal.valueOf(RANDOM.nextDouble() * 200000).setScale(2, RoundingMode.HALF_UP));
        creditData.setCreditCardUsed(BigDecimal.valueOf(RANDOM.nextDouble() * 100000).setScale(2, RoundingMode.HALF_UP));

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("score", creditScore);
        resultMap.put("level", creditData.getCreditLevel());
        resultMap.put("overdueCount", overdueCount);
        resultMap.put("queryTime", LocalDateTime.now().toString());
        resultMap.put("source", "MOCK_PBOC");
        creditData.setQueryResult(JSON.toJSONString(resultMap));

        log.info("[模拟数据] 征信查询完成, customerId: {}, score: {}, level: {}",
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
        record.setQueryChannel("INTEGRATED");
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

        log.info("征信查询记录已保存, applicationNo: {}, creditScore: {}, success: {}",
                application.getApplicationNo(), creditData.getCreditScore(), creditData.getSuccess());

        return record;
    }
}
