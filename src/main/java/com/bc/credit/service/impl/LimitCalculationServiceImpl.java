package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.RiskLevelEnum;
import com.bc.credit.common.enums.ScoreLevelEnum;
import com.bc.credit.dto.LimitCalcContext;
import com.bc.credit.dto.LimitCalcDTO;
import com.bc.credit.engine.impl.GroovyLimitEngine;
import com.bc.credit.entity.LimitCalcLog;
import com.bc.credit.entity.LimitCalcResult;
import com.bc.credit.entity.LimitStrategyConfig;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LimitCalcLogMapper;
import com.bc.credit.mapper.LimitCalcResultMapper;
import com.bc.credit.mapper.LimitStrategyConfigMapper;
import com.bc.credit.service.LimitCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LimitCalculationServiceImpl implements LimitCalculationService {

    private static final String STRATEGY_CACHE_PREFIX = "limit:strategy:";
    private static final String STRATEGY_CACHE_VERSION_KEY = "limit:strategy:version";

    @Value("${credit.limit.min-amount:1000}")
    private BigDecimal minAmount;

    @Value("${credit.limit.max-amount:500000}")
    private BigDecimal maxAmount;

    @Value("${credit.limit.manual-review-threshold:200000}")
    private BigDecimal manualReviewThreshold;

    @Value("${credit.limit.high-risk-threshold:500}")
    private Integer highRiskThreshold;

    @Value("${credit.limit.default-engine:GROOVY}")
    private String defaultEngine;

    @Value("${credit.limit.default-income-multiplier:5}")
    private Integer defaultIncomeMultiplier;

    @Value("${credit.limit.default-fraud-score-threshold:50}")
    private Integer defaultFraudScoreThreshold;

    @Value("${credit.limit.default-fraud-deduction-ratio:0.5}")
    private BigDecimal defaultFraudDeductionRatio;

    @Value("${credit.limit.default-debt-deduction-ratio:0.3}")
    private BigDecimal defaultDebtDeductionRatio;

    @Value("${credit.limit.default-validity-days:30}")
    private Integer defaultValidityDays;

    @Autowired
    private LimitCalcResultMapper limitCalcResultMapper;

    @Autowired
    private LimitCalcLogMapper limitCalcLogMapper;

    @Autowired
    private LimitStrategyConfigMapper limitStrategyConfigMapper;

    @Autowired
    private GroovyLimitEngine groovyLimitEngine;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private final Map<String, LimitStrategyConfig> localStrategyCache = new ConcurrentHashMap<>();

    @Override
    public LimitCalcDTO calculateLimit(LoanApplication application, Integer creditScore,
                                        String riskLevel, BigDecimal monthlyIncome,
                                        BigDecimal monthlyDebt, BigDecimal remainingLoanAmount) {
        return calculateLimit(application, creditScore, riskLevel, monthlyIncome,
                monthlyDebt, remainingLoanAmount, null, null);
    }

    @Override
    public LimitCalcDTO calculateLimit(LoanApplication application, Integer creditScore,
                                        String riskLevel, BigDecimal monthlyIncome,
                                        BigDecimal monthlyDebt, BigDecimal remainingLoanAmount,
                                        Integer fraudScore, String scoreSegment) {
        long startTime = System.currentTimeMillis();
        String customerId = application.getCustomerId();
        String applicationNo = application.getApplicationNo();

        log.info("开始额度计算, customerId: {}, applicationNo: {}, loanAmount: {}, engine: {}",
                customerId, applicationNo, application.getLoanAmount(), defaultEngine);

        if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            monthlyIncome = BigDecimal.valueOf(10000);
        }
        if (monthlyDebt == null) {
            monthlyDebt = BigDecimal.ZERO;
        }
        if (remainingLoanAmount == null) {
            remainingLoanAmount = BigDecimal.ZERO;
        }
        if (fraudScore == null) {
            fraudScore = 0;
        }
        if (scoreSegment == null) {
            scoreSegment = resolveScoreSegment(creditScore);
        }

        BigDecimal annualIncome = monthlyIncome.multiply(BigDecimal.valueOf(12));
        BigDecimal debtRatio = monthlyDebt.divide(monthlyIncome, 4, RoundingMode.HALF_UP);

        LimitStrategyConfig strategy = getActiveStrategy();
        LimitCalcContext context = buildCalcContext(application, creditScore, riskLevel,
                monthlyIncome, monthlyDebt, remainingLoanAmount, fraudScore, scoreSegment,
                annualIncome, debtRatio, strategy);

        String engineType = strategy != null ? strategy.getStrategyType() : defaultEngine;
        LimitCalcContext resultContext;

        try {
            if ("GROOVY".equalsIgnoreCase(engineType)) {
                resultContext = executeGroovyEngine(context, strategy);
            } else if ("DROOLS".equalsIgnoreCase(engineType)) {
                resultContext = executeDroolsEngine(context, strategy);
            } else {
                resultContext = executeDefaultEngine(context);
            }
        } catch (Exception e) {
            log.error("引擎执行失败，回退到默认计算逻辑, customerId: {}, engine: {}", customerId, engineType, e);
            resultContext = executeDefaultEngine(context);
            engineType = "FALLBACK";
        }

        LimitCalcDTO result = convertToDTO(resultContext, strategy, engineType);
        result.setLimitFactors(buildLimitFactors(context, resultContext, strategy, engineType));

        long elapsed = System.currentTimeMillis() - startTime;
        saveCalcLog(application, context, resultContext, strategy, engineType, elapsed);

        log.info("额度计算完成, customerId: {}, creditLimit: {}, validityDays: {}, engine: {}, elapsed: {}ms",
                customerId, result.getCreditLimit(), result.getValidityDays(), engineType, elapsed);

        return result;
    }

    private LimitCalcContext buildCalcContext(LoanApplication application, Integer creditScore,
                                              String riskLevel, BigDecimal monthlyIncome,
                                              BigDecimal monthlyDebt, BigDecimal remainingLoanAmount,
                                              Integer fraudScore, String scoreSegment,
                                              BigDecimal annualIncome, BigDecimal debtRatio,
                                              LimitStrategyConfig strategy) {
        LimitCalcContext context = new LimitCalcContext();
        context.setCustomerId(application.getCustomerId());
        context.setApplicationNo(application.getApplicationNo());
        context.setAnnualIncome(annualIncome);
        context.setMonthlyIncome(monthlyIncome);
        context.setMonthlyDebt(monthlyDebt);
        context.setTotalDebt(remainingLoanAmount);
        context.setLoanAmount(application.getLoanAmount());
        context.setLoanTerm(application.getLoanTerm());
        context.setCreditScore(creditScore);
        context.setScoreSegment(scoreSegment);
        context.setFraudScore(fraudScore);
        context.setFraudRiskLevel(resolveFraudRiskLevel(fraudScore));
        context.setRiskLevel(riskLevel);
        context.setDebtRatio(debtRatio);

        if (strategy != null) {
            context.setIncomeMultiplier(calculateIncomeMultiplier(creditScore, strategy));
            context.setScoreCoefficient(resolveScoreCoefficient(scoreSegment, strategy));
            context.setFraudDeductionRatio(strategy.getFraudDeductionRatio());
            context.setDebtDeductionRatio(strategy.getDebtDeductionRatio());
            context.setMinAmount(strategy.getMinAmount());
            context.setMaxAmount(strategy.getMaxAmount());
            context.setValidityDays(strategy.getValidityDays());
        } else {
            context.setIncomeMultiplier(BigDecimal.valueOf(defaultIncomeMultiplier));
            context.setScoreCoefficient(resolveScoreCoefficient(scoreSegment, null));
            context.setFraudDeductionRatio(defaultFraudDeductionRatio);
            context.setDebtDeductionRatio(defaultDebtDeductionRatio);
            context.setMinAmount(minAmount);
            context.setMaxAmount(maxAmount);
            context.setValidityDays(defaultValidityDays);
        }

        context.setStrategyCode(strategy != null ? strategy.getStrategyCode() : "DEFAULT");
        context.setStrategyType(strategy != null ? strategy.getStrategyType() : defaultEngine);

        return context;
    }

    private LimitCalcContext executeGroovyEngine(LimitCalcContext context, LimitStrategyConfig strategy) {
        String script;
        if (strategy != null && strategy.getGroovyScript() != null && !strategy.getGroovyScript().isEmpty()) {
            script = strategy.getGroovyScript();
        } else {
            script = groovyLimitEngine.getDefaultScript();
        }

        log.info("使用Groovy引擎执行额度计算, customerId: {}, strategyCode: {}",
                context.getCustomerId(), context.getStrategyCode());
        return groovyLimitEngine.execute(context, script);
    }

    private LimitCalcContext executeDroolsEngine(LimitCalcContext context, LimitStrategyConfig strategy) {
        log.info("使用Drools引擎执行额度计算, customerId: {}, strategyCode: {}",
                context.getCustomerId(), context.getStrategyCode());
        return executeDefaultEngine(context);
    }

    private LimitCalcContext executeDefaultEngine(LimitCalcContext context) {
        BigDecimal annualIncome = context.getAnnualIncome();
        BigDecimal incomeMultiplier = context.getIncomeMultiplier();
        BigDecimal scoreCoefficient = context.getScoreCoefficient();

        BigDecimal baseLimit = annualIncome.multiply(incomeMultiplier).multiply(scoreCoefficient);
        context.setBaseLimit(baseLimit);

        BigDecimal afterFraudLimit = baseLimit;
        if (context.getFraudScore() != null && context.getFraudScore() > defaultFraudScoreThreshold) {
            BigDecimal fraudDeduction = baseLimit.multiply(BigDecimal.ONE.subtract(context.getFraudDeductionRatio()));
            context.setFraudDeductionAmount(fraudDeduction);
            afterFraudLimit = baseLimit.multiply(context.getFraudDeductionRatio());
        } else {
            context.setFraudDeductionAmount(BigDecimal.ZERO);
        }

        BigDecimal totalDebt = context.getTotalDebt() != null ? context.getTotalDebt() : BigDecimal.ZERO;
        BigDecimal debtDeduction = totalDebt.multiply(context.getDebtDeductionRatio());
        context.setDebtDeductionAmount(debtDeduction);

        BigDecimal beforeConstraint = afterFraudLimit.subtract(debtDeduction);
        if (beforeConstraint.compareTo(BigDecimal.ZERO) < 0) {
            beforeConstraint = BigDecimal.ZERO;
        }
        context.setBeforeConstraintLimit(beforeConstraint);

        BigDecimal finalLimit = beforeConstraint;
        finalLimit = finalLimit.max(context.getMinAmount()).min(context.getMaxAmount());
        if (finalLimit.compareTo(context.getLoanAmount()) > 0) {
            finalLimit = context.getLoanAmount();
        }
        finalLimit = finalLimit.setScale(0, RoundingMode.DOWN);
        context.setFinalLimit(finalLimit);

        BigDecimal interestRate = calculateInterestRate(context.getCreditScore(), context.getRiskLevel());
        context.setInterestRate(interestRate);

        boolean needManualReview = finalLimit.compareTo(manualReviewThreshold) >= 0
                || (context.getCreditScore() != null && context.getCreditScore() < highRiskThreshold)
                || RiskLevelEnum.HIGH.getCode().equals(context.getRiskLevel());
        context.setNeedManualReview(needManualReview);

        context.setValidityDays(context.getValidityDays() != null ? context.getValidityDays() : defaultValidityDays);

        if (needManualReview) {
            context.setRemark("额度超过20万或评分较低，需人工复核");
        } else {
            context.setRemark("额度计算完成，可自动审批");
        }

        return context;
    }

    private BigDecimal calculateIncomeMultiplier(Integer creditScore, LimitStrategyConfig strategy) {
        int multiplierMin = strategy != null && strategy.getIncomeMultiplierMin() != null
                ? strategy.getIncomeMultiplierMin() : 3;
        int multiplierMax = strategy != null && strategy.getIncomeMultiplierMax() != null
                ? strategy.getIncomeMultiplierMax() : 8;

        if (creditScore == null) {
            return BigDecimal.valueOf((multiplierMin + multiplierMax) / 2);
        }

        ScoreLevelEnum level = ScoreLevelEnum.getByScore(creditScore);
        double ratio;
        switch (level) {
            case A: ratio = 1.0; break;
            case B: ratio = 0.75; break;
            case C: ratio = 0.5; break;
            case D: ratio = 0.25; break;
            default: ratio = 0.1; break;
        }

        int multiplier = (int) Math.round(multiplierMin + (multiplierMax - multiplierMin) * ratio);
        return BigDecimal.valueOf(multiplier);
    }

    private BigDecimal resolveScoreCoefficient(String scoreSegment, LimitStrategyConfig strategy) {
        BigDecimal prime = strategy != null && strategy.getScoreCoefficientPrime() != null
                ? strategy.getScoreCoefficientPrime() : new BigDecimal("1.0");
        BigDecimal standard = strategy != null && strategy.getScoreCoefficientStandard() != null
                ? strategy.getScoreCoefficientStandard() : new BigDecimal("0.6");
        BigDecimal highRisk = strategy != null && strategy.getScoreCoefficientHighRisk() != null
                ? strategy.getScoreCoefficientHighRisk() : new BigDecimal("0.2");

        if ("PRIME".equals(scoreSegment)) {
            return prime;
        } else if ("HIGH_RISK".equals(scoreSegment)) {
            return highRisk;
        }
        return standard;
    }

    private String resolveScoreSegment(Integer creditScore) {
        if (creditScore == null) return "STANDARD";
        ScoreLevelEnum level = ScoreLevelEnum.getByScore(creditScore);
        switch (level) {
            case A:
            case B: return "PRIME";
            case C: return "STANDARD";
            default: return "HIGH_RISK";
        }
    }

    private String resolveFraudRiskLevel(Integer fraudScore) {
        if (fraudScore == null) return "LOW";
        if (fraudScore > 70) return "HIGH";
        if (fraudScore > 40) return "MEDIUM";
        return "LOW";
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

    private LimitCalcDTO convertToDTO(LimitCalcContext context, LimitStrategyConfig strategy, String engineType) {
        LimitCalcDTO dto = new LimitCalcDTO();
        dto.setCustomerId(context.getCustomerId());
        dto.setIncomeAmount(context.getMonthlyIncome());
        dto.setAnnualIncome(context.getAnnualIncome());
        dto.setTotalDebt(context.getTotalDebt());
        dto.setDebtRatio(context.getDebtRatio());
        dto.setCreditScore(context.getCreditScore());
        dto.setScoreSegment(context.getScoreSegment());
        dto.setFraudScore(context.getFraudScore());
        dto.setRiskLevel(context.getRiskLevel());
        dto.setCreditLimit(context.getFinalLimit());
        dto.setMaxAvailableLimit(context.getBaseLimit() != null
                ? context.getBaseLimit().min(context.getMaxAmount()).setScale(0, RoundingMode.DOWN)
                : context.getFinalLimit());
        dto.setInterestRate(context.getInterestRate());
        dto.setNeedManualReview(context.getNeedManualReview());
        dto.setValidityDays(context.getValidityDays() != null ? context.getValidityDays() : defaultValidityDays);
        dto.setStrategyCode(context.getStrategyCode());
        dto.setStrategyType(engineType);
        dto.setRemark(context.getRemark());
        return dto;
    }

    private Map<String, Object> buildLimitFactors(LimitCalcContext input, LimitCalcContext output,
                                                   LimitStrategyConfig strategy, String engineType) {
        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("annualIncome", input.getAnnualIncome());
        factors.put("monthlyIncome", input.getMonthlyIncome());
        factors.put("monthlyDebt", input.getMonthlyDebt());
        factors.put("totalDebt", input.getTotalDebt());
        factors.put("debtRatio", input.getDebtRatio());
        factors.put("creditScore", input.getCreditScore());
        factors.put("scoreSegment", input.getScoreSegment());
        factors.put("fraudScore", input.getFraudScore());
        factors.put("riskLevel", input.getRiskLevel());
        factors.put("incomeMultiplier", output.getIncomeMultiplier());
        factors.put("scoreCoefficient", output.getScoreCoefficient());
        factors.put("baseLimit", output.getBaseLimit());
        factors.put("fraudDeductionAmount", output.getFraudDeductionAmount());
        factors.put("debtDeductionAmount", output.getDebtDeductionAmount());
        factors.put("beforeConstraintLimit", output.getBeforeConstraintLimit());
        factors.put("finalLimit", output.getFinalLimit());
        factors.put("minAmount", output.getMinAmount());
        factors.put("maxAmount", output.getMaxAmount());
        factors.put("engineType", engineType);
        factors.put("strategyCode", output.getStrategyCode());
        return factors;
    }

    private void saveCalcLog(LoanApplication application, LimitCalcContext input,
                             LimitCalcContext output, LimitStrategyConfig strategy,
                             String engineType, long elapsed) {
        try {
            LimitCalcLog calcLog = new LimitCalcLog();
            calcLog.setId(IdWorker.getId());
            calcLog.setApplicationId(application.getId());
            calcLog.setApplicationNo(application.getApplicationNo());
            calcLog.setCustomerId(application.getCustomerId());
            calcLog.setStrategyCode(output.getStrategyCode());
            calcLog.setStrategyType(engineType);
            calcLog.setStrategyVersion(strategy != null ? strategy.getVersion() : "DEFAULT");
            calcLog.setAnnualIncome(input.getAnnualIncome());
            calcLog.setTotalDebt(input.getTotalDebt());
            calcLog.setCreditScore(input.getCreditScore());
            calcLog.setScoreSegment(input.getScoreSegment());
            calcLog.setFraudScore(input.getFraudScore());
            calcLog.setLoanAmount(input.getLoanAmount());
            calcLog.setIncomeMultiplier(output.getIncomeMultiplier() != null
                    ? output.getIncomeMultiplier().intValue() : defaultIncomeMultiplier);
            calcLog.setScoreCoefficient(output.getScoreCoefficient());
            calcLog.setBaseLimit(output.getBaseLimit());
            calcLog.setFraudDeductionAmount(output.getFraudDeductionAmount());
            calcLog.setDebtDeductionAmount(output.getDebtDeductionAmount());
            calcLog.setBeforeConstraintLimit(output.getBeforeConstraintLimit());
            calcLog.setFinalLimit(output.getFinalLimit());
            calcLog.setValidityDays(output.getValidityDays() != null ? output.getValidityDays() : defaultValidityDays);
            calcLog.setInterestRate(output.getInterestRate());
            calcLog.setEngineType(engineType);
            calcLog.setExecutionTimeMs(elapsed);
            calcLog.setCalcTime(LocalDateTime.now());
            calcLog.setCreatedTime(LocalDateTime.now());
            calcLog.setDeleted(0);

            Map<String, Object> steps = new LinkedHashMap<>();
            steps.put("step1_baseLimit", output.getBaseLimit());
            steps.put("step2_fraudDeduction", output.getFraudDeductionAmount());
            steps.put("step3_debtDeduction", output.getDebtDeductionAmount());
            steps.put("step4_beforeConstraint", output.getBeforeConstraintLimit());
            steps.put("step5_finalLimit", output.getFinalLimit());
            steps.put("step6_validityDays", output.getValidityDays());
            steps.put("engineType", engineType);
            calcLog.setCalcSteps(JSON.toJSONString(steps));

            limitCalcLogMapper.insert(calcLog);
            log.debug("额度计算日志已保存, applicationNo: {}, logId: {}", application.getApplicationNo(), calcLog.getId());
        } catch (Exception e) {
            log.error("保存额度计算日志失败, applicationNo: {}", application.getApplicationNo(), e);
        }
    }

    @Override
    public LimitCalcResult saveLimitResult(LoanApplication application, LimitCalcDTO calcDTO) {
        LimitCalcResult result = new LimitCalcResult();
        result.setId(IdWorker.getId());
        result.setApplicationId(application.getId());
        result.setApplicationNo(application.getApplicationNo());
        result.setCustomerId(application.getCustomerId());
        result.setIncomeAmount(calcDTO.getIncomeAmount());
        result.setAnnualIncome(calcDTO.getAnnualIncome());
        result.setTotalDebt(calcDTO.getTotalDebt());
        result.setDebtRatio(calcDTO.getDebtRatio());
        result.setCreditScore(calcDTO.getCreditScore());
        result.setScoreSegment(calcDTO.getScoreSegment());
        result.setFraudScore(calcDTO.getFraudScore());
        result.setRiskLevel(calcDTO.getRiskLevel());
        result.setCreditLimit(calcDTO.getCreditLimit());
        result.setMaxAvailableLimit(calcDTO.getMaxAvailableLimit());
        result.setInterestRate(calcDTO.getInterestRate());
        result.setLimitFactors(JSON.toJSONString(calcDTO.getLimitFactors()));
        result.setNeedManualReview(calcDTO.getNeedManualReview() ? 1 : 0);
        result.setStrategyCode(calcDTO.getStrategyCode());
        result.setStrategyType(calcDTO.getStrategyType());
        result.setValidityDays(calcDTO.getValidityDays());
        result.setCalcTime(LocalDateTime.now());
        result.setRemark(calcDTO.getRemark());
        result.setCreatedTime(LocalDateTime.now());
        result.setDeleted(0);

        limitCalcResultMapper.insert(result);
        return result;
    }

    @Override
    public LimitStrategyConfig getActiveStrategy() {
        LimitStrategyConfig cached = localStrategyCache.get("active");
        if (cached != null) {
            return cached;
        }

        if (stringRedisTemplate != null) {
            try {
                String cachedJson = stringRedisTemplate.opsForValue().get(STRATEGY_CACHE_PREFIX + "active");
                if (cachedJson != null) {
                    cached = JSON.parseObject(cachedJson, LimitStrategyConfig.class);
                    if (cached != null) {
                        localStrategyCache.put("active", cached);
                        return cached;
                    }
                }
            } catch (Exception e) {
                log.warn("从Redis读取策略缓存失败", e);
            }
        }

        LimitStrategyConfig dbStrategy = limitStrategyConfigMapper.selectOne(
                new LambdaQueryWrapper<LimitStrategyConfig>()
                        .eq(LimitStrategyConfig::getDefaultStrategy, 1)
                        .eq(LimitStrategyConfig::getEnabled, 1)
                        .last("LIMIT 1"));

        if (dbStrategy != null) {
            localStrategyCache.put("active", dbStrategy);
            cacheStrategyToRedis(dbStrategy);
        }

        return dbStrategy;
    }

    @Override
    public LimitStrategyConfig getStrategyByCode(String strategyCode) {
        String cacheKey = "code_" + strategyCode;
        LimitStrategyConfig cached = localStrategyCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (stringRedisTemplate != null) {
            try {
                String cachedJson = stringRedisTemplate.opsForValue().get(STRATEGY_CACHE_PREFIX + strategyCode);
                if (cachedJson != null) {
                    cached = JSON.parseObject(cachedJson, LimitStrategyConfig.class);
                    if (cached != null) {
                        localStrategyCache.put(cacheKey, cached);
                        return cached;
                    }
                }
            } catch (Exception e) {
                log.warn("从Redis读取策略缓存失败, strategyCode: {}", strategyCode, e);
            }
        }

        LimitStrategyConfig dbStrategy = limitStrategyConfigMapper.selectOne(
                new LambdaQueryWrapper<LimitStrategyConfig>()
                        .eq(LimitStrategyConfig::getStrategyCode, strategyCode)
                        .eq(LimitStrategyConfig::getEnabled, 1)
                        .last("LIMIT 1"));

        if (dbStrategy != null) {
            localStrategyCache.put(cacheKey, dbStrategy);
            cacheStrategyToRedis(dbStrategy);
        }

        return dbStrategy;
    }

    @Override
    public List<LimitStrategyConfig> listStrategies() {
        return limitStrategyConfigMapper.selectList(
                new LambdaQueryWrapper<LimitStrategyConfig>()
                        .eq(LimitStrategyConfig::getEnabled, 1)
                        .orderByDesc(LimitStrategyConfig::getDefaultStrategy)
                        .orderByDesc(LimitStrategyConfig::getCreatedTime));
    }

    @Override
    public void refreshStrategyCache() {
        log.info("刷新额度策略缓存");
        localStrategyCache.clear();

        if (stringRedisTemplate != null) {
            try {
                Set<String> keys = stringRedisTemplate.keys(STRATEGY_CACHE_PREFIX + "*");
                if (keys != null && !keys.isEmpty()) {
                    stringRedisTemplate.delete(keys);
                }
            } catch (Exception e) {
                log.warn("清除Redis策略缓存失败", e);
            }
        }

        LimitStrategyConfig active = getActiveStrategy();
        if (active != null) {
            log.info("策略缓存刷新完成, 活跃策略: {}, 类型: {}", active.getStrategyCode(), active.getStrategyType());
        } else {
            log.info("策略缓存刷新完成, 无活跃策略, 将使用默认配置");
        }
    }

    private void cacheStrategyToRedis(LimitStrategyConfig strategy) {
        if (stringRedisTemplate != null) {
            try {
                String json = JSON.toJSONString(strategy);
                stringRedisTemplate.opsForValue().set(STRATEGY_CACHE_PREFIX + "active", json);
                stringRedisTemplate.opsForValue().set(STRATEGY_CACHE_PREFIX + strategy.getStrategyCode(), json);
                log.debug("策略已缓存到Redis, strategyCode: {}", strategy.getStrategyCode());
            } catch (Exception e) {
                log.warn("缓存策略到Redis失败, strategyCode: {}", strategy.getStrategyCode(), e);
            }
        }
    }
}
