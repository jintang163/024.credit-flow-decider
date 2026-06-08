package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.ScoreLevelEnum;
import com.bc.credit.dto.CreditScoreDTO;
import com.bc.credit.entity.CreditScoreResult;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.entity.ScorecardDimension;
import com.bc.credit.entity.ScorecardRule;
import com.bc.credit.mapper.CreditScoreResultMapper;
import com.bc.credit.mapper.ScorecardDimensionMapper;
import com.bc.credit.mapper.ScorecardRuleMapper;
import com.bc.credit.service.CreditScoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CreditScoringServiceImpl implements CreditScoringService {

    private static final String DEFAULT_VERSION = "V1.0";
    private static final int PASS_SCORE = 600;

    @Autowired
    private ScorecardDimensionMapper dimensionMapper;

    @Autowired
    private ScorecardRuleMapper ruleMapper;

    @Autowired
    private CreditScoreResultMapper scoreResultMapper;

    @Override
    public CreditScoreDTO calculateScore(LoanApplication application, Integer creditScore,
                                         Integer overdueCount, BigDecimal remainingLoanAmount,
                                         Map<String, Object> extraInfo) {
        log.info("开始信用评分计算, customerId: {}, applicationNo: {}",
                application.getCustomerId(), application.getApplicationNo());

        CreditScoreDTO result = new CreditScoreDTO();
        result.setCustomerId(application.getCustomerId());
        result.setScorecardVersion(DEFAULT_VERSION);

        Map<String, Integer> dimensionScores = new HashMap<>();
        int totalScore = 0;

        List<ScorecardDimension> dimensions = dimensionMapper.getDimensionsByVersion(DEFAULT_VERSION);

        for (ScorecardDimension dimension : dimensions) {
            int dimScore = calculateDimensionScore(dimension, creditScore, overdueCount,
                    remainingLoanAmount, extraInfo);
            dimensionScores.put(dimension.getDimensionCode(), dimScore);
            totalScore += dimScore;
        }

        totalScore = Math.max(300, Math.min(900, totalScore));

        result.setTotalScore(totalScore);
        result.setScoreLevel(ScoreLevelEnum.getByScore(totalScore).getCode());
        result.setDimensionScores(dimensionScores);
        result.setPass(totalScore >= PASS_SCORE);

        if (!result.getPass()) {
            result.setRemark("信用评分不足" + PASS_SCORE + "分，未通过");
        } else {
            result.setRemark("信用评分通过，等级：" + result.getScoreLevel());
        }

        log.info("信用评分计算完成, customerId: {}, totalScore: {}, level: {}, pass: {}",
                application.getCustomerId(), totalScore, result.getScoreLevel(), result.getPass());

        return result;
    }

    private int calculateDimensionScore(ScorecardDimension dimension, Integer creditScore,
                                         Integer overdueCount, BigDecimal remainingLoanAmount,
                                         Map<String, Object> extraInfo) {
        List<ScorecardRule> rules = ruleMapper.getRulesByDimensionAndVersion(
                dimension.getDimensionCode(), DEFAULT_VERSION);

        for (ScorecardRule rule : rules) {
            if (matchRule(rule, dimension.getDimensionCode(), creditScore, overdueCount,
                    remainingLoanAmount, extraInfo)) {
                return rule.getScore();
            }
        }

        return 0;
    }

    private boolean matchRule(ScorecardRule rule, String dimensionCode, Integer creditScore,
                               Integer overdueCount, BigDecimal remainingLoanAmount,
                               Map<String, Object> extraInfo) {
        switch (dimensionCode) {
            case "CREDIT_HISTORY":
                return evaluateCreditHistoryRule(rule, overdueCount);
            case "REPAYMENT_CAPACITY":
                return evaluateRepaymentCapacityRule(rule, extraInfo);
            case "DEBT_RATIO":
                return evaluateDebtRatioRule(rule, remainingLoanAmount, extraInfo);
            case "PERSONAL_INFO":
                return evaluatePersonalInfoRule(rule, extraInfo);
            default:
                return false;
        }
    }

    private boolean evaluateCreditHistoryRule(ScorecardRule rule, Integer overdueCount) {
        int count = overdueCount != null ? overdueCount : 0;
        switch (rule.getRuleCode()) {
            case "CR_001": return count == 0;
            case "CR_002": return count >= 1 && count <= 2;
            case "CR_003": return count >= 3 && count <= 5;
            case "CR_004": return count > 5;
            default: return false;
        }
    }

    private boolean evaluateRepaymentCapacityRule(ScorecardRule rule, Map<String, Object> extraInfo) {
        if (extraInfo == null || !extraInfo.containsKey("monthlyIncome")) {
            return rule.getRuleCode().equals("RC_005");
        }
        BigDecimal income = (BigDecimal) extraInfo.get("monthlyIncome");
        if (income == null) return rule.getRuleCode().equals("RC_005");

        double incomeVal = income.doubleValue();
        switch (rule.getRuleCode()) {
            case "RC_001": return incomeVal >= 50000;
            case "RC_002": return incomeVal >= 30000 && incomeVal < 50000;
            case "RC_003": return incomeVal >= 15000 && incomeVal < 30000;
            case "RC_004": return incomeVal >= 8000 && incomeVal < 15000;
            case "RC_005": return incomeVal < 8000;
            default: return false;
        }
    }

    private boolean evaluateDebtRatioRule(ScorecardRule rule, BigDecimal remainingLoanAmount,
                                           Map<String, Object> extraInfo) {
        if (extraInfo == null || !extraInfo.containsKey("monthlyIncome")
                || !extraInfo.containsKey("monthlyDebt")) {
            return rule.getRuleCode().equals("DR_003");
        }
        BigDecimal monthlyDebt = (BigDecimal) extraInfo.get("monthlyDebt");
        BigDecimal monthlyIncome = (BigDecimal) extraInfo.get("monthlyIncome");

        if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return rule.getRuleCode().equals("DR_004");
        }

        double debtRatio = monthlyDebt != null
                ? monthlyDebt.divide(monthlyIncome, 4, BigDecimal.ROUND_HALF_UP).doubleValue()
                : 0.5;

        switch (rule.getRuleCode()) {
            case "DR_001": return debtRatio < 0.3;
            case "DR_002": return debtRatio >= 0.3 && debtRatio < 0.5;
            case "DR_003": return debtRatio >= 0.5 && debtRatio < 0.7;
            case "DR_004": return debtRatio >= 0.7;
            default: return false;
        }
    }

    private boolean evaluatePersonalInfoRule(ScorecardRule rule, Map<String, Object> extraInfo) {
        if (extraInfo == null) return false;

        switch (rule.getRuleCode()) {
            case "PI_001":
                Integer age = (Integer) extraInfo.get("age");
                return age != null && age >= 30 && age <= 45;
            case "PI_002":
                age = (Integer) extraInfo.get("age");
                return age != null && ((age >= 25 && age < 30) || (age > 45 && age <= 55));
            case "PI_003":
                age = (Integer) extraInfo.get("age");
                return age != null && ((age >= 18 && age < 25) || (age > 55 && age <= 60));
            case "PI_004":
                Boolean hasHouse = (Boolean) extraInfo.get("hasHouse");
                return Boolean.TRUE.equals(hasHouse);
            case "PI_005":
                Boolean hasCar = (Boolean) extraInfo.get("hasCar");
                return Boolean.TRUE.equals(hasCar);
            case "PI_006":
                Integer educationLevel = (Integer) extraInfo.get("educationLevel");
                return educationLevel != null && educationLevel >= 4;
            case "PI_007":
                Integer workYears = (Integer) extraInfo.get("workYears");
                return workYears != null && workYears >= 5;
            default:
                return false;
        }
    }

    @Override
    public CreditScoreResult saveScoreResult(LoanApplication application, CreditScoreDTO scoreDTO) {
        CreditScoreResult result = new CreditScoreResult();
        result.setId(IdWorker.getId());
        result.setApplicationId(application.getId());
        result.setApplicationNo(application.getApplicationNo());
        result.setCustomerId(application.getCustomerId());
        result.setScorecardVersion(scoreDTO.getScorecardVersion());
        result.setTotalScore(scoreDTO.getTotalScore());
        result.setScoreLevel(scoreDTO.getScoreLevel());
        result.setDimensionScores(JSON.toJSONString(scoreDTO.getDimensionScores()));
        result.setPass(scoreDTO.getPass() ? 1 : 0);
        result.setScoreTime(LocalDateTime.now());
        result.setRemark(scoreDTO.getRemark());
        result.setCreatedTime(LocalDateTime.now());
        result.setDeleted(0);

        scoreResultMapper.insert(result);
        return result;
    }
}
