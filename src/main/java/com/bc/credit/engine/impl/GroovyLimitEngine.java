package com.bc.credit.engine.impl;

import com.bc.credit.dto.LimitCalcContext;
import com.bc.credit.engine.RuleEngineStatsHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
public class GroovyLimitEngine {

    private static final String MODULE = "limit";

    private final ScriptEngine groovyEngine;

    @org.springframework.beans.factory.annotation.Autowired
    private RuleEngineStatsHelper statsHelper;

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
        try {
            Bindings bindings = buildBindings(context);

            Object result = groovyEngine.eval(script, bindings);

            if (result instanceof LimitCalcContext) {
                LimitCalcContext calcResult = (LimitCalcContext) result;
                long elapsed = System.currentTimeMillis() - startTime;
                statsHelper.recordExecution(MODULE, "groovy", 1, elapsed);
                log.info("Groovy limit engine executed successfully, customerId: {}, finalLimit: {}, elapsed: {}ms",
                        context.getCustomerId(), calcResult.getFinalLimit(), elapsed);
                return calcResult;
            }

            extractBindingsToContext(bindings, context);

            long elapsed = System.currentTimeMillis() - startTime;
            statsHelper.recordExecution(MODULE, "groovy", 1, elapsed);
            log.info("Groovy limit engine executed (binding mode), customerId: {}, finalLimit: {}, elapsed: {}ms",
                    context.getCustomerId(), context.getFinalLimit(), elapsed);
            return context;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            statsHelper.recordExecution(MODULE, "groovy", 0, elapsed);
            log.error("Groovy limit engine execution failed, customerId: {}",
                    context.getCustomerId(), e);
            throw new RuntimeException("Groovy额度计算脚本执行失败: " + e.getMessage(), e);
        }
    }

    public boolean validateScript(String script) {
        try {
            Bindings bindings = buildBindings(new LimitCalcContext());
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
            bindings.put("fraudScoreThreshold", 50);
            bindings.put("fraudDeductionRatio", new BigDecimal("0.5"));
            bindings.put("debtDeductionRatio", new BigDecimal("0.3"));
            bindings.put("manualReviewThreshold", new BigDecimal("200000"));
            bindings.put("minAmount", new BigDecimal("1000"));
            bindings.put("maxAmount", new BigDecimal("500000"));
            bindings.put("validityDays", 30);

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

    private Bindings buildBindings(LimitCalcContext context) {
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
        bindings.put("fraudScoreThreshold", context.getFraudScoreThreshold());
        bindings.put("fraudDeductionRatio", context.getFraudDeductionRatio());
        bindings.put("debtDeductionRatio", context.getDebtDeductionRatio());
        bindings.put("manualReviewThreshold", context.getManualReviewThreshold());
        bindings.put("minAmount", context.getMinAmount());
        bindings.put("maxAmount", context.getMaxAmount());
        bindings.put("validityDays", context.getValidityDays());
        bindings.put("BigDecimal", BigDecimal.class);
        bindings.put("RoundingMode", RoundingMode.class);
        bindings.put("Math", Math.class);
        return bindings;
    }

    private void extractBindingsToContext(Bindings bindings, LimitCalcContext context) {
        String[] keys = {"baseLimit", "fraudDeductionAmount", "debtDeductionAmount",
                "beforeConstraintLimit", "finalLimit", "interestRate"};
        for (String key : keys) {
            Object val = bindings.get(key);
            if (val != null) {
                switch (key) {
                    case "baseLimit": context.setBaseLimit(toBigDecimal(val)); break;
                    case "fraudDeductionAmount": context.setFraudDeductionAmount(toBigDecimal(val)); break;
                    case "debtDeductionAmount": context.setDebtDeductionAmount(toBigDecimal(val)); break;
                    case "beforeConstraintLimit": context.setBeforeConstraintLimit(toBigDecimal(val)); break;
                    case "finalLimit": context.setFinalLimit(toBigDecimal(val)); break;
                    case "interestRate": context.setInterestRate(toBigDecimal(val)); break;
                }
            }
        }

        Object needManualReviewObj = bindings.get("needManualReview");
        if (needManualReviewObj != null) {
            context.setNeedManualReview(Boolean.TRUE.equals(needManualReviewObj));
        }
        Object remarkObj = bindings.get("remark");
        if (remarkObj != null) {
            context.setRemark(remarkObj.toString());
        }
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
            "// 额度计算 Groovy 脚本 (策略参数从 context 绑定变量读取)\n" +
            "// 输入: annualIncome, creditScore, fraudScore, totalDebt, loanAmount\n" +
            "//       incomeMultiplier, scoreCoefficient, fraudScoreThreshold,\n" +
            "//       fraudDeductionRatio, debtDeductionRatio, manualReviewThreshold,\n" +
            "//       minAmount, maxAmount, validityDays\n\n" +
            "// 1. 基础额度\n" +
            "baseLimit = annualIncome * incomeMultiplier * scoreCoefficient\n\n" +
            "// 2. 欺诈扣减: 使用策略表的欺诈阈值和扣减比率\n" +
            "int threshold = fraudScoreThreshold != null ? fraudScoreThreshold : 50\n" +
            "BigDecimal fRatio = fraudDeductionRatio != null ? fraudDeductionRatio : new BigDecimal(\"0.5\")\n" +
            "if (fraudScore != null && fraudScore > threshold) {\n" +
            "    fraudDeductionAmount = baseLimit * (BigDecimal.ONE - fRatio)\n" +
            "    baseLimit = baseLimit * fRatio\n" +
            "} else {\n" +
            "    fraudDeductionAmount = BigDecimal.ZERO\n" +
            "}\n\n" +
            "// 3. 负债抵扣: 使用策略表的负债抵扣比率\n" +
            "BigDecimal dRatio = debtDeductionRatio != null ? debtDeductionRatio : new BigDecimal(\"0.3\")\n" +
            "debtDeductionAmount = (totalDebt != null ? totalDebt : BigDecimal.ZERO) * dRatio\n" +
            "beforeConstraintLimit = baseLimit - debtDeductionAmount\n\n" +
            "// 4. 上下限约束\n" +
            "if (beforeConstraintLimit < minAmount) {\n" +
            "    finalLimit = minAmount\n" +
            "} else if (beforeConstraintLimit > maxAmount) {\n" +
            "    finalLimit = maxAmount\n" +
            "} else {\n" +
            "    finalLimit = beforeConstraintLimit\n" +
            "}\n" +
            "if (finalLimit > loanAmount) finalLimit = loanAmount\n" +
            "finalLimit = finalLimit.setScale(0, RoundingMode.DOWN)\n\n" +
            "// 5. 利率计算\n" +
            "interestRate = new BigDecimal(\"0.12\")\n" +
            "if (creditScore >= 750) interestRate -= new BigDecimal(\"0.04\")\n" +
            "else if (creditScore >= 700) interestRate -= new BigDecimal(\"0.02\")\n" +
            "else if (creditScore >= 650) interestRate = interestRate\n" +
            "else if (creditScore >= 600) interestRate += new BigDecimal(\"0.02\")\n" +
            "else interestRate += new BigDecimal(\"0.04\")\n" +
            "if (interestRate < new BigDecimal(\"0.06\")) interestRate = new BigDecimal(\"0.06\")\n" +
            "if (interestRate > new BigDecimal(\"0.24\")) interestRate = new BigDecimal(\"0.24\")\n" +
            "interestRate = interestRate.setScale(4, RoundingMode.HALF_UP)\n\n" +
            "// 6. 人工复核: 使用策略表的人工复核阈值\n" +
            "BigDecimal mrt = manualReviewThreshold != null ? manualReviewThreshold : new BigDecimal(\"200000\")\n" +
            "needManualReview = (finalLimit >= mrt) || (creditScore != null && creditScore < 500)\n\n" +
            "// 7. 备注\n" +
            "remark = needManualReview ? '额度超过人工复核阈值或评分较低，需人工复核' : '额度计算完成，可自动审批'\n";
}
