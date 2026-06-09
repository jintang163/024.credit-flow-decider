package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.ScoreLevelEnum;
import com.bc.credit.dto.CreditScoreDTO;
import com.bc.credit.dto.ScoringFeatureInputDTO;
import com.bc.credit.entity.CreditScoreResult;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.CreditScoreResultMapper;
import com.bc.credit.scoring.PmmlScoringEngine;
import com.bc.credit.scoring.PythonScoringClient;
import com.bc.credit.service.CreditScoringService;
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
public class CreditScoringServiceImpl implements CreditScoringService {

    private static final int PASS_SCORE = 600;
    private static final int MIN_SCORE = 300;
    private static final int MAX_SCORE = 850;

    @Autowired
    private CreditScoreResultMapper scoreResultMapper;

    @Autowired
    private PmmlScoringEngine pmmlScoringEngine;

    @Autowired
    private PythonScoringClient pythonScoringClient;

    @Value("${credit.scoring.engine:PMML}")
    private String scoringEngine;

    @Value("${credit.scoring.pmml.model-version:V1.0}")
    private String pmmlModelVersion;

    @Value("${credit.scoring.scorecard-version:V1.0}")
    private String scorecardVersion;

    @Override
    public CreditScoreDTO calculateScore(LoanApplication application, Integer creditScore,
                                         Integer overdueCount, BigDecimal remainingLoanAmount,
                                         Map<String, Object> extraInfo) {
        log.info("开始信用评分计算, customerId: {}, applicationNo: {}, engine: {}",
                application.getCustomerId(), application.getApplicationNo(), scoringEngine);

        ScoringFeatureInputDTO featureInput = buildFeatureInput(application, creditScore,
                overdueCount, remainingLoanAmount, extraInfo);

        long startTime = System.currentTimeMillis();
        CreditScoreDTO result;

        if ("PYTHON".equalsIgnoreCase(scoringEngine) && pythonScoringClient.isEnabled()) {
            result = calculateWithPython(featureInput);
        } else {
            result = calculateWithPmml(featureInput);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        result.setExecutionTimeMs(elapsed);

        result.setScorecardVersion(scorecardVersion);
        applyScoreSegmentMapping(result);
        result.setPass(result.getTotalScore() >= PASS_SCORE);

        if (!result.getPass()) {
            result.setRemark("信用评分不足" + PASS_SCORE + "分，未通过");
        } else {
            result.setRemark("信用评分通过，等级：" + result.getScoreLevel() + "，分段：" + result.getScoreSegment());
        }

        log.info("信用评分计算完成, customerId: {}, totalScore: {}, level: {}, segment: {}, defaultProb: {}, elapsed: {}ms",
                application.getCustomerId(), result.getTotalScore(), result.getScoreLevel(),
                result.getScoreSegment(), result.getDefaultProbability(), elapsed);

        return result;
    }

    private CreditScoreDTO calculateWithPmml(ScoringFeatureInputDTO featureInput) {
        CreditScoreDTO result = new CreditScoreDTO();
        result.setCustomerId(featureInput.getCustomerId());
        result.setEngineType("PMML");

        try {
            PmmlScoringEngine.PmmlScoringResult pmmlResult = pmmlScoringEngine.evaluate(featureInput);

            int score = mapProbabilityToScore(pmmlResult.getDefaultProbability());
            result.setTotalScore(clampScore(score));
            result.setDefaultProbability(BigDecimal.valueOf(pmmlResult.getDefaultProbability())
                    .setScale(6, RoundingMode.HALF_UP));
            result.setModelVersion(pmmlResult.getModelVersion());
            result.setScoreLevel(ScoreLevelEnum.getByScore(result.getTotalScore()).getCode());

            Map<String, Integer> dimensionScores = buildDimensionScoresFromPmml(featureInput, pmmlResult);
            result.setDimensionScores(dimensionScores);

        } catch (Exception e) {
            log.error("PMML scoring failed, falling back to scorecard, customerId: {}",
                    featureInput.getCustomerId(), e);
            result = fallbackScorecardScoring(featureInput);
            result.setEngineType("SCORECARD_FALLBACK");
        }

        return result;
    }

    private CreditScoreDTO calculateWithPython(ScoringFeatureInputDTO featureInput) {
        CreditScoreDTO result = new CreditScoreDTO();
        result.setCustomerId(featureInput.getCustomerId());
        result.setEngineType("PYTHON");

        try {
            PythonScoringClient.PythonScoringResult pyResult = pythonScoringClient.score(featureInput);

            int score = clampScore(pyResult.getScore());
            result.setTotalScore(score);
            result.setDefaultProbability(BigDecimal.valueOf(pyResult.getDefaultProbability())
                    .setScale(6, RoundingMode.HALF_UP));
            result.setModelVersion(pyResult.getModelVersion());
            result.setScoreLevel(ScoreLevelEnum.getByScore(score).getCode());

            if (pyResult.getShapValues() != null && !pyResult.getShapValues().isEmpty()) {
                result.setShapValues(pyResult.getShapValues());
            }

            Map<String, Integer> dimensionScores = buildDimensionScoresFromShap(pyResult.getShapValues());
            result.setDimensionScores(dimensionScores);

        } catch (Exception e) {
            log.error("Python scoring failed, falling back to PMML, customerId: {}",
                    featureInput.getCustomerId(), e);
            result = calculateWithPmml(featureInput);
            result.setEngineType("PMML_FALLBACK");
        }

        return result;
    }

    private int mapProbabilityToScore(double defaultProbability) {
        double baseScore = 600;
        double pdo = 20;
        double baseOdds = 50.0;

        double odds = (1.0 - defaultProbability) / Math.max(defaultProbability, 0.0001);
        double score = baseScore + pdo * Math.log(odds / baseOdds) / Math.log(2);

        return (int) Math.round(score);
    }

    private void applyScoreSegmentMapping(CreditScoreDTO result) {
        int score = result.getTotalScore();
        if (score >= 700) {
            result.setScoreSegment("PRIME");
        } else if (score >= 600) {
            result.setScoreSegment("STANDARD");
        } else {
            result.setScoreSegment("HIGH_RISK");
        }
    }

    private int clampScore(int score) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
    }

    private Map<String, Integer> buildDimensionScoresFromPmml(ScoringFeatureInputDTO input,
                                                                PmmlScoringEngine.PmmlScoringResult pmmlResult) {
        Map<String, Integer> scores = new HashMap<>();
        int overdueCount = input.getOverdueCount() != null ? input.getOverdueCount() : 0;
        if (overdueCount == 0) scores.put("CREDIT_HISTORY", 200);
        else if (overdueCount <= 2) scores.put("CREDIT_HISTORY", 150);
        else if (overdueCount <= 5) scores.put("CREDIT_HISTORY", 100);
        else scores.put("CREDIT_HISTORY", 50);

        BigDecimal income = input.getMonthlyIncome();
        if (income != null) {
            double inc = income.doubleValue();
            if (inc >= 50000) scores.put("REPAYMENT_CAPACITY", 200);
            else if (inc >= 30000) scores.put("REPAYMENT_CAPACITY", 160);
            else if (inc >= 15000) scores.put("REPAYMENT_CAPACITY", 120);
            else if (inc >= 8000) scores.put("REPAYMENT_CAPACITY", 80);
            else scores.put("REPAYMENT_CAPACITY", 40);
        } else {
            scores.put("REPAYMENT_CAPACITY", 40);
        }

        BigDecimal debtRatio = input.getDebtRatio();
        if (debtRatio != null) {
            double dr = debtRatio.doubleValue();
            if (dr < 0.3) scores.put("DEBT_RATIO", 200);
            else if (dr < 0.5) scores.put("DEBT_RATIO", 150);
            else if (dr < 0.7) scores.put("DEBT_RATIO", 100);
            else scores.put("DEBT_RATIO", 50);
        } else {
            scores.put("DEBT_RATIO", 100);
        }

        Integer age = input.getAge();
        if (age != null && age >= 30 && age <= 45) scores.put("PERSONAL_INFO", 180);
        else scores.put("PERSONAL_INFO", 120);

        return scores;
    }

    private Map<String, Integer> buildDimensionScoresFromShap(Map<String, BigDecimal> shapValues) {
        Map<String, Integer> scores = new HashMap<>();
        if (shapValues == null || shapValues.isEmpty()) {
            return scores;
        }

        Map<String, String> featureToDimension = new HashMap<>();
        featureToDimension.put("overdue_count", "CREDIT_HISTORY");
        featureToDimension.put("overdue_amount", "CREDIT_HISTORY");
        featureToDimension.put("max_overdue_days", "CREDIT_HISTORY");
        featureToDimension.put("credit_card_utilization", "CREDIT_HISTORY");
        featureToDimension.put("monthly_income", "REPAYMENT_CAPACITY");
        featureToDimension.put("work_years", "REPAYMENT_CAPACITY");
        featureToDimension.put("debt_ratio", "DEBT_RATIO");
        featureToDimension.put("monthly_debt", "DEBT_RATIO");
        featureToDimension.put("age", "PERSONAL_INFO");
        featureToDimension.put("education_level", "PERSONAL_INFO");
        featureToDimension.put("has_house", "PERSONAL_INFO");
        featureToDimension.put("has_car", "PERSONAL_INFO");
        featureToDimension.put("fill_duration_seconds", "BEHAVIOR");
        featureToDimension.put("apply_hour", "BEHAVIOR");
        featureToDimension.put("is_weekend_apply", "BEHAVIOR");

        Map<String, Double> dimensionContributions = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : shapValues.entrySet()) {
            String dimension = featureToDimension.getOrDefault(entry.getKey(), "OTHER");
            double contribution = Math.abs(entry.getValue().doubleValue());
            dimensionContributions.merge(dimension, contribution, Double::sum);
        }

        double totalContribution = dimensionContributions.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalContribution <= 0) {
            totalContribution = 1.0;
        }

        for (Map.Entry<String, Double> entry : dimensionContributions.entrySet()) {
            int proportion = (int) (entry.getValue() / totalContribution * 700);
            scores.put(entry.getKey(), Math.max(50, Math.min(250, proportion)));
        }

        return scores;
    }

    private CreditScoreDTO fallbackScorecardScoring(ScoringFeatureInputDTO input) {
        CreditScoreDTO result = new CreditScoreDTO();
        result.setCustomerId(input.getCustomerId());
        result.setModelVersion("SCORECARD_V1.0");

        Map<String, Integer> dimensionScores = new HashMap<>();
        int totalScore = 0;

        int overdueCount = input.getOverdueCount() != null ? input.getOverdueCount() : 0;
        int creditScore;
        if (overdueCount == 0) creditScore = 200;
        else if (overdueCount <= 2) creditScore = 150;
        else if (overdueCount <= 5) creditScore = 100;
        else creditScore = 50;
        dimensionScores.put("CREDIT_HISTORY", creditScore);
        totalScore += creditScore;

        BigDecimal income = input.getMonthlyIncome();
        int incomeScore;
        if (income != null) {
            double inc = income.doubleValue();
            if (inc >= 50000) incomeScore = 200;
            else if (inc >= 30000) incomeScore = 160;
            else if (inc >= 15000) incomeScore = 120;
            else if (inc >= 8000) incomeScore = 80;
            else incomeScore = 40;
        } else {
            incomeScore = 40;
        }
        dimensionScores.put("REPAYMENT_CAPACITY", incomeScore);
        totalScore += incomeScore;

        BigDecimal debtRatio = input.getDebtRatio();
        int debtScore;
        if (debtRatio != null) {
            double dr = debtRatio.doubleValue();
            if (dr < 0.3) debtScore = 200;
            else if (dr < 0.5) debtScore = 150;
            else if (dr < 0.7) debtScore = 100;
            else debtScore = 50;
        } else {
            debtScore = 100;
        }
        dimensionScores.put("DEBT_RATIO", debtScore);
        totalScore += debtScore;

        Integer age = input.getAge();
        int personalScore;
        if (age != null && age >= 30 && age <= 45) personalScore = 180;
        else if (age != null && ((age >= 25 && age < 30) || (age > 45 && age <= 55))) personalScore = 130;
        else personalScore = 80;
        dimensionScores.put("PERSONAL_INFO", personalScore);
        totalScore += personalScore;

        totalScore = clampScore(totalScore);
        result.setTotalScore(totalScore);
        result.setScoreLevel(ScoreLevelEnum.getByScore(totalScore).getCode());
        result.setDimensionScores(dimensionScores);
        result.setDefaultProbability(BigDecimal.ZERO);

        return result;
    }

    private ScoringFeatureInputDTO buildFeatureInput(LoanApplication application, Integer creditScore,
                                                      Integer overdueCount, BigDecimal remainingLoanAmount,
                                                      Map<String, Object> extraInfo) {
        ScoringFeatureInputDTO input = new ScoringFeatureInputDTO();
        input.setCustomerId(application.getCustomerId());
        input.setApplicationNo(application.getApplicationNo());
        input.setOverdueCount(overdueCount != null ? overdueCount : 0);
        input.setAge(application.getAge());
        input.setMonthlyIncome(application.getMonthlyIncome());
        input.setWorkYears(application.getWorkYears());
        input.setEducationLevel(application.getEducationLevel());
        input.setHasHouse(application.getHasHouse());
        input.setHasCar(application.getHasCar());
        input.setMaritalStatus(application.getMaritalStatus());
        input.setLoanAmount(application.getLoanAmount());
        input.setLoanTerm(application.getLoanTerm());
        input.setMonthlyDebt(application.getMonthlyDebt());
        input.setChannel(application.getChannel());

        if (application.getMonthlyDebt() != null && application.getMonthlyIncome() != null
                && application.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
            input.setDebtRatio(application.getMonthlyDebt()
                    .divide(application.getMonthlyIncome(), 4, RoundingMode.HALF_UP));
        }

        if (extraInfo != null) {
            if (extraInfo.containsKey("accountCount")) {
                input.setAccountCount((Integer) extraInfo.get("accountCount"));
            }
            if (extraInfo.containsKey("creditCardCount")) {
                input.setCreditCardCount((Integer) extraInfo.get("creditCardCount"));
            }
            if (extraInfo.containsKey("creditCardUtilization")) {
                Object util = extraInfo.get("creditCardUtilization");
                if (util instanceof BigDecimal) {
                    input.setCreditCardUtilization((BigDecimal) util);
                }
            }
            if (extraInfo.containsKey("creditQueryCount3m")) {
                input.setCreditQueryCount3m((Integer) extraInfo.get("creditQueryCount3m"));
            }
            if (extraInfo.containsKey("maxOverdueDays")) {
                input.setMaxOverdueDays((Integer) extraInfo.get("maxOverdueDays"));
            }
            if (extraInfo.containsKey("overdueAmount")) {
                Object amt = extraInfo.get("overdueAmount");
                if (amt instanceof BigDecimal) {
                    input.setOverdueAmount(((BigDecimal) amt).intValue());
                } else if (amt instanceof Integer) {
                    input.setOverdueAmount((Integer) amt);
                }
            }
            if (extraInfo.containsKey("fillDurationSeconds")) {
                input.setFillDurationSeconds((Integer) extraInfo.get("fillDurationSeconds"));
            }
            if (extraInfo.containsKey("applyHour")) {
                input.setApplyHour((Integer) extraInfo.get("applyHour"));
            }
            if (extraInfo.containsKey("isWeekendApply")) {
                input.setIsWeekendApply((Boolean) extraInfo.get("isWeekendApply"));
            }
        }

        if (application.getSubmitTime() != null) {
            input.setApplyHour(application.getSubmitTime().getHour());
            String dayOfWeek = application.getSubmitTime().getDayOfWeek().toString();
            input.setIsWeekendApply("SATURDAY".equals(dayOfWeek) || "SUNDAY".equals(dayOfWeek));
        }

        return input;
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
        result.setDefaultProbability(scoreDTO.getDefaultProbability());
        result.setScoreSegment(scoreDTO.getScoreSegment());
        result.setShapValues(scoreDTO.getShapValues() != null ? JSON.toJSONString(scoreDTO.getShapValues()) : null);
        result.setEngineType(scoreDTO.getEngineType());
        result.setModelVersion(scoreDTO.getModelVersion());
        result.setPass(scoreDTO.getPass() ? 1 : 0);
        result.setScoreTime(LocalDateTime.now());
        result.setRemark(scoreDTO.getRemark());
        result.setCreatedTime(LocalDateTime.now());
        result.setDeleted(0);

        scoreResultMapper.insert(result);
        return result;
    }

    public Map<String, Object> getScoringEngineStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeEngine", scoringEngine);
        status.put("pmml", pmmlScoringEngine.getModelStatus());
        status.put("pythonServiceEnabled", pythonScoringClient.isEnabled());
        if (pythonScoringClient.isEnabled()) {
            status.put("pythonServiceHealthy", pythonScoringClient.isHealthy());
        }
        return status;
    }

    public void reloadPmmlModel() {
        pmmlScoringEngine.reloadModel();
    }

    public void switchPmmlModel(String version) {
        pmmlScoringEngine.switchModel(version);
    }

    public void uploadPmmlModel(String version, byte[] content) {
        pmmlScoringEngine.uploadModelToRedis(version, content);
        pmmlScoringEngine.switchModel(version);
    }

    public boolean validatePmmlModel(byte[] content) {
        try {
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(content);
            org.dmg.pmml.PMML pmml = org.jpmml.model.PMMLUtil.unmarshal(bis);
            org.jpmml.evaluator.Evaluator evaluator = new org.jpmml.evaluator.LoadingModelEvaluatorBuilder()
                    .pmml(pmml)
                    .build();
            evaluator.verify();
            return true;
        } catch (Exception e) {
            log.warn("PMML model validation failed: {}", e.getMessage());
            return false;
        }
    }
}
