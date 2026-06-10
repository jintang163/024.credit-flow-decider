package com.bc.credit.engine.impl;

import com.bc.credit.dto.LimitCalcContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class GroovyLimitEngine {

    private final ScriptEngine groovyEngine;
    private final ConcurrentHashMap<String, String> scriptCache = new ConcurrentHashMap<>();

    public GroovyLimitEngine() {
        ScriptEngineManager manager = new ScriptEngineManager();
        this.groovyEngine = manager.getEngineByName("groovy");
        if (this.groovyEngine == null) {
            log.error("Groovy script engine not available, please check groovy-jsr223 dependency");
            throw new IllegalStateException("Groovy script engine not available");
        }
        log.info("GroovyLimitEngine initialized successfully");
    }

    public LimitCalcContext execute(LimitCalcContext context, String script) {
        long startTime = System.currentTimeMillis();
        String cacheKey = "script_" + script.hashCode();
        try {
            Bindings bindings = groovyEngine.createBindings();
            bindings.put("ctx", context);
            bindings.put("annualIncome", context.getAnnualIncome());
            bindings.put("monthlyIncome", context.getMonthlyIncome());
            bindings.put("monthlyDebt", context.getMonthlyDebt());
            bindings.put("totalDebt", context.getTotalDebt());
            bindings.put("loanAmount", context.getLoanAmount());
            bindings.put("loanTerm", context.getLoanTerm());
            bindings.put("creditScore", context.getCreditScore());
            bindings.put("scoreSegment", context.getScoreSegment());
            bindings.put("fraudScore", context.getFraudScore());
            bindings.put("fraudRiskLevel", context.getFraudRiskLevel());
            bindings.put("riskLevel", context.getRiskLevel());
            bindings.put("debtRatio", context.getDebtRatio());
            bindings.put("incomeMultiplier", context.getIncomeMultiplier());
            bindings.put("scoreCoefficient", context.getScoreCoefficient());
            bindings.put("minAmount", context.getMinAmount());
            bindings.put("maxAmount", context.getMaxAmount());
            bindings.put("validityDays", context.getValidityDays());
            bindings.put("BigDecimal", BigDecimal.class);
            bindings.put("RoundingMode", RoundingMode.class);
            bindings.put("Math", Math.class);

            Object result = groovyEngine.eval(script, bindings);

            if (result instanceof LimitCalcContext) {
                LimitCalcContext calcResult = (LimitCalcContext) result;
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("Groovy limit engine executed successfully, customerId: {}, finalLimit: {}, elapsed: {}ms",
                        context.getCustomerId(), calcResult.getFinalLimit(), elapsed);
                return calcResult;
            }

            if (result != null) {
                log.warn("Groovy script returned non-Context result type: {}, falling back to context binding",
                        result.getClass().getSimpleName());
            }

            Object baseLimitObj = bindings.get("baseLimit");
            Object fraudDeductionAmountObj = bindings.get("fraudDeductionAmount");
            Object debtDeductionAmountObj = bindings.get("debtDeductionAmount");
            Object beforeConstraintLimitObj = bindings.get("beforeConstraintLimit");
            Object finalLimitObj = bindings.get("finalLimit");
            Object interestRateObj = bindings.get("interestRate");
            Object needManualReviewObj = bindings.get("needManualReview");
            Object remarkObj = bindings.get("remark");

            if (baseLimitObj != null) {
                context.setBaseLimit(toBigDecimal(baseLimitObj));
            }
            if (fraudDeductionAmountObj != null) {
                context.setFraudDeductionAmount(toBigDecimal(fraudDeductionAmountObj));
            }
            if (debtDeductionAmountObj != null) {
                context.setDebtDeductionAmount(toBigDecimal(debtDeductionAmountObj));
            }
            if (beforeConstraintLimitObj != null) {
                context.setBeforeConstraintLimit(toBigDecimal(beforeConstraintLimitObj));
            }
            if (finalLimitObj != null) {
                context.setFinalLimit(toBigDecimal(finalLimitObj));
            }
            if (interestRateObj != null) {
                context.setInterestRate(toBigDecimal(interestRateObj));
            }
            if (needManualReviewObj != null) {
                context.setNeedManualReview(Boolean.TRUE.equals(needManualReviewObj));
            }
            if (remarkObj != null) {
                context.setRemark(remarkObj.toString());
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Groovy limit engine executed (binding mode), customerId: {}, finalLimit: {}, elapsed: {}ms",
                    context.getCustomerId(), context.getFinalLimit(), elapsed);
            return context;

        } catch (Exception e) {
            log.error("Groovy limit engine execution failed, customerId: {}, script cacheKey: {}",
                    context.getCustomerId(), cacheKey, e);
            throw new RuntimeException("Groovy额度计算脚本执行失败: " + e.getMessage(), e);
        }
    }

    public boolean validateScript(String script) {
        try {
            Bindings bindings = groovyEngine.createBindings();
            bindings.put("ctx", new LimitCalcContext());
            bindings.put("annualIncome", BigDecimal.ZERO);
            bindings.put("monthlyIncome", BigDecimal.ZERO);
            bindings.put("monthlyDebt", BigDecimal.ZERO);
            bindings.put("totalDebt", BigDecimal.ZERO);
            bindings.put("loanAmount", BigDecimal.ZERO);
            bindings.put("loanTerm", 12);
            bindings.put("creditScore", 700);
            bindings.put("scoreSegment", "STANDARD");
            bindings.put("fraudScore", 0);
            bindings.put("fraudRiskLevel", "LOW");
            bindings.put("riskLevel", "LOW");
            bindings.put("debtRatio", BigDecimal.ZERO);
            bindings.put("incomeMultiplier", new BigDecimal("5"));
            bindings.put("scoreCoefficient", BigDecimal.ONE);
            bindings.put("minAmount", new BigDecimal("1000"));
            bindings.put("maxAmount", new BigDecimal("500000"));
            bindings.put("validityDays", 30);
            bindings.put("BigDecimal", BigDecimal.class);
            bindings.put("RoundingMode", RoundingMode.class);
            bindings.put("Math", Math.class);

            groovyEngine.eval(script, bindings);
            return true;
        } catch (Exception e) {
            log.warn("Groovy script validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String getDefaultScript() {
        return DEFAULT_GROOVY_SCRIPT;
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj instanceof BigDecimal) {
            return (BigDecimal) obj;
        }
        if (obj instanceof Number) {
            return BigDecimal.valueOf(((Number) obj).doubleValue());
        }
        return new BigDecimal(obj.toString());
    }

    private static final String DEFAULT_GROOVY_SCRIPT =
            "// 额度计算 Groovy 脚本\n" +
            "// 输入变量: annualIncome, creditScore, fraudScore, totalDebt, loanAmount, minAmount, maxAmount\n" +
            "//            incomeMultiplier, scoreCoefficient, debtDeductionRatio, fraudDeductionRatio\n" +
            "// 输出变量: baseLimit, fraudDeductionAmount, debtDeductionAmount, beforeConstraintLimit, finalLimit\n" +
            "//            interestRate, needManualReview, remark\n\n" +
            "// 1. 基础额度 = 年收入 × 倍数 × 评分系数\n" +
            "baseLimit = annualIncome * incomeMultiplier * scoreCoefficient\n\n" +
            "// 2. 欺诈扣减: 欺诈风险分 > 阈值则额度 × 扣减比率\n" +
            "if (fraudScore != null && fraudScore > 50) {\n" +
            "    fraudDeductionAmount = baseLimit * (1 - new BigDecimal(\"0.5\"))\n" +
            "    baseLimit = baseLimit * new BigDecimal(\"0.5\")\n" +
            "} else {\n" +
            "    fraudDeductionAmount = BigDecimal.ZERO\n" +
            "}\n\n" +
            "// 3. 负债抵扣: 现有总负债 × 0.3\n" +
            "debtDeductionAmount = (totalDebt != null ? totalDebt : BigDecimal.ZERO) * new BigDecimal(\"0.3\")\n" +
            "beforeConstraintLimit = baseLimit - debtDeductionAmount\n\n" +
            "// 4. 上下限约束\n" +
            "if (beforeConstraintLimit < minAmount) {\n" +
            "    finalLimit = minAmount\n" +
            "} else if (beforeConstraintLimit > maxAmount) {\n" +
            "    finalLimit = maxAmount\n" +
            "} else {\n" +
            "    finalLimit = beforeConstraintLimit\n" +
            "}\n" +
            "if (finalLimit > loanAmount) {\n" +
            "    finalLimit = loanAmount\n" +
            "}\n" +
            "finalLimit = finalLimit.setScale(0, RoundingMode.DOWN)\n\n" +
            "// 5. 利率计算\n" +
            "interestRate = new BigDecimal(\"0.12\")\n" +
            "if (creditScore >= 750) {\n" +
            "    interestRate = interestRate - new BigDecimal(\"0.04\")\n" +
            "} else if (creditScore >= 700) {\n" +
            "    interestRate = interestRate - new BigDecimal(\"0.02\")\n" +
            "} else if (creditScore >= 650) {\n" +
            "    interestRate = interestRate\n" +
            "} else if (creditScore >= 600) {\n" +
            "    interestRate = interestRate + new BigDecimal(\"0.02\")\n" +
            "} else {\n" +
            "    interestRate = interestRate + new BigDecimal(\"0.04\")\n" +
            "}\n" +
            "if (interestRate < new BigDecimal(\"0.06\")) interestRate = new BigDecimal(\"0.06\")\n" +
            "if (interestRate > new BigDecimal(\"0.24\")) interestRate = new BigDecimal(\"0.24\")\n" +
            "interestRate = interestRate.setScale(4, RoundingMode.HALF_UP)\n\n" +
            "// 6. 是否需要人工复核\n" +
            "needManualReview = (finalLimit >= new BigDecimal(\"200000\")) || (creditScore < 500)\n\n" +
            "// 7. 备注\n" +
            "remark = needManualReview ? '额度超过20万或评分较低，需人工复核' : '额度计算完成，可自动审批'\n";
}
