package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.RiskLevelEnum;
import com.bc.credit.common.enums.ScoreLevelEnum;
import com.bc.credit.dto.LimitCalcDTO;
import com.bc.credit.entity.LimitCalcResult;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LimitCalcResultMapper;
import com.bc.credit.service.LimitCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class LimitCalculationServiceImpl implements LimitCalculationService {

    @Value("${credit.limit.min-amount:1000}")
    private BigDecimal minAmount;

    @Value("${credit.limit.max-amount:500000}")
    private BigDecimal maxAmount;

    @Value("${credit.limit.manual-review-threshold:200000}")
    private BigDecimal manualReviewThreshold;

    @Value("${credit.limit.high-risk-threshold:500}")
    private Integer highRiskThreshold;

    @Autowired
    private LimitCalcResultMapper limitCalcResultMapper;

    @Override
    public LimitCalcDTO calculateLimit(LoanApplication application, Integer creditScore,
                                        String riskLevel, BigDecimal monthlyIncome,
                                        BigDecimal monthlyDebt, BigDecimal remainingLoanAmount) {
        log.info("开始额度计算, customerId: {}, applicationNo: {}, loanAmount: {}",
                application.getCustomerId(), application.getApplicationNo(), application.getLoanAmount());

        LimitCalcDTO result = new LimitCalcDTO();
        result.setCustomerId(application.getCustomerId());
        result.setIncomeAmount(monthlyIncome);

        if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            monthlyIncome = BigDecimal.valueOf(10000);
        }
        if (monthlyDebt == null) {
            monthlyDebt = BigDecimal.ZERO;
        }

        BigDecimal debtRatio = monthlyDebt.divide(monthlyIncome, 4, RoundingMode.HALF_UP);
        result.setDebtRatio(debtRatio);

        BigDecimal scoreMultiplier = calculateScoreMultiplier(creditScore);
        BigDecimal riskMultiplier = calculateRiskMultiplier(riskLevel);
        BigDecimal dtiMultiplier = calculateDtiMultiplier(debtRatio);

        BigDecimal baseLimit = monthlyIncome.multiply(BigDecimal.valueOf(12))
                .multiply(scoreMultiplier)
                .multiply(riskMultiplier)
                .multiply(dtiMultiplier);

        BigDecimal maxMonthlyPayment = monthlyIncome.multiply(BigDecimal.valueOf(0.5))
                .subtract(monthlyDebt);
        BigDecimal term = BigDecimal.valueOf(application.getLoanTerm());
        BigDecimal limitByPayment = maxMonthlyPayment.multiply(term);

        BigDecimal creditLimit = baseLimit.min(limitByPayment);
        creditLimit = creditLimit.max(minAmount).min(maxAmount);
        creditLimit = creditLimit.min(application.getLoanAmount());
        creditLimit = creditLimit.setScale(0, RoundingMode.DOWN);

        result.setCreditLimit(creditLimit);
        result.setMaxAvailableLimit(baseLimit.min(maxAmount).setScale(0, RoundingMode.DOWN));

        BigDecimal interestRate = calculateInterestRate(creditScore, riskLevel);
        result.setInterestRate(interestRate);

        Map<String, Object> factors = new HashMap<>();
        factors.put("monthlyIncome", monthlyIncome);
        factors.put("monthlyDebt", monthlyDebt);
        factors.put("debtRatio", debtRatio);
        factors.put("creditScore", creditScore);
        factors.put("riskLevel", riskLevel);
        factors.put("scoreMultiplier", scoreMultiplier);
        factors.put("riskMultiplier", riskMultiplier);
        factors.put("dtiMultiplier", dtiMultiplier);
        factors.put("baseLimit", baseLimit);
        factors.put("limitByPayment", limitByPayment);
        result.setLimitFactors(factors);

        boolean needManualReview = creditLimit.compareTo(manualReviewThreshold) >= 0
                || creditScore < highRiskThreshold
                || RiskLevelEnum.HIGH.getCode().equals(riskLevel);
        result.setNeedManualReview(needManualReview);

        if (needManualReview) {
            result.setRemark("额度超过20万或评分较低，需人工复核");
        } else {
            result.setRemark("额度计算完成，可自动审批");
        }

        log.info("额度计算完成, customerId: {}, creditLimit: {}, needManualReview: {}",
                application.getCustomerId(), creditLimit, needManualReview);

        return result;
    }

    private BigDecimal calculateScoreMultiplier(Integer creditScore) {
        ScoreLevelEnum level = ScoreLevelEnum.getByScore(creditScore);
        switch (level) {
            case A: return BigDecimal.valueOf(1.2);
            case B: return BigDecimal.valueOf(1.0);
            case C: return BigDecimal.valueOf(0.8);
            case D: return BigDecimal.valueOf(0.5);
            default: return BigDecimal.valueOf(0.3);
        }
    }

    private BigDecimal calculateRiskMultiplier(String riskLevel) {
        RiskLevelEnum level = RiskLevelEnum.getByCode(riskLevel);
        if (level == null) return BigDecimal.ONE;
        switch (level) {
            case LOW: return BigDecimal.valueOf(1.0);
            case MEDIUM: return BigDecimal.valueOf(0.7);
            case HIGH: return BigDecimal.valueOf(0.4);
            default: return BigDecimal.ONE;
        }
    }

    private BigDecimal calculateDtiMultiplier(BigDecimal debtRatio) {
        double ratio = debtRatio.doubleValue();
        if (ratio < 0.2) return BigDecimal.valueOf(1.0);
        if (ratio < 0.35) return BigDecimal.valueOf(0.9);
        if (ratio < 0.5) return BigDecimal.valueOf(0.7);
        if (ratio < 0.65) return BigDecimal.valueOf(0.5);
        return BigDecimal.valueOf(0.3);
    }

    private BigDecimal calculateInterestRate(Integer creditScore, String riskLevel) {
        BigDecimal baseRate = BigDecimal.valueOf(0.12);
        BigDecimal scoreAdjustment;

        if (creditScore >= 750) {
            scoreAdjustment = BigDecimal.valueOf(-0.04);
        } else if (creditScore >= 700) {
            scoreAdjustment = BigDecimal.valueOf(-0.02);
        } else if (creditScore >= 650) {
            scoreAdjustment = BigDecimal.ZERO;
        } else if (creditScore >= 600) {
            scoreAdjustment = BigDecimal.valueOf(0.02);
        } else {
            scoreAdjustment = BigDecimal.valueOf(0.04);
        }

        BigDecimal riskAdjustment = BigDecimal.ZERO;
        if (RiskLevelEnum.MEDIUM.getCode().equals(riskLevel)) {
            riskAdjustment = BigDecimal.valueOf(0.01);
        } else if (RiskLevelEnum.HIGH.getCode().equals(riskLevel)) {
            riskAdjustment = BigDecimal.valueOf(0.03);
        }

        BigDecimal finalRate = baseRate.add(scoreAdjustment).add(riskAdjustment);
        finalRate = finalRate.max(BigDecimal.valueOf(0.06)).min(BigDecimal.valueOf(0.24));
        return finalRate.setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public LimitCalcResult saveLimitResult(LoanApplication application, LimitCalcDTO calcDTO) {
        LimitCalcResult result = new LimitCalcResult();
        result.setId(IdWorker.getId());
        result.setApplicationId(application.getId());
        result.setApplicationNo(application.getApplicationNo());
        result.setCustomerId(application.getCustomerId());
        result.setIncomeAmount(calcDTO.getIncomeAmount());
        result.setDebtRatio(calcDTO.getDebtRatio());
        result.setCreditLimit(calcDTO.getCreditLimit());
        result.setMaxAvailableLimit(calcDTO.getMaxAvailableLimit());
        result.setInterestRate(calcDTO.getInterestRate());
        result.setLimitFactors(JSON.toJSONString(calcDTO.getLimitFactors()));
        result.setNeedManualReview(calcDTO.getNeedManualReview() ? 1 : 0);
        result.setCalcTime(LocalDateTime.now());
        result.setRemark(calcDTO.getRemark());
        result.setCreatedTime(LocalDateTime.now());
        result.setDeleted(0);

        limitCalcResultMapper.insert(result);
        return result;
    }
}
