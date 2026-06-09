package com.bc.credit.engine.impl;

import com.bc.credit.dto.AntiFraudRuleFact;
import com.bc.credit.dto.RuleExecutionResultDTO;
import com.bc.credit.dto.RuleHitDetailDTO;
import com.bc.credit.engine.RuleEngine;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieBase;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("droolsRuleEngine")
public class DroolsRuleEngine implements RuleEngine {

    @Autowired
    private DroolsKieContainerManager kieContainerManager;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final String RULE_HIT_COUNT_KEY = "anti-fraud:rule:hit-count:";
    private static final String RULE_EXEC_COUNT_KEY = "anti-fraud:rule:exec-count:";

    @Override
    public Object execute(String expression, Map<String, Object> context) throws Exception {
        throw new UnsupportedOperationException("Drools engine does not support expression-based execution. Use executeRules() instead.");
    }

    @Override
    public Boolean executeBoolean(String expression, Map<String, Object> context) throws Exception {
        throw new UnsupportedOperationException("Drools engine does not support expression-based execution. Use executeRules() instead.");
    }

    @Override
    public <T> T execute(String expression, Map<String, Object> context, Class<T> resultType) throws Exception {
        throw new UnsupportedOperationException("Drools engine does not support expression-based execution. Use executeRules() instead.");
    }

    @Override
    public boolean validateExpression(String expression) {
        return kieContainerManager.validateDrl(expression);
    }

    public RuleExecutionResultDTO executeRules(AntiFraudRuleFact fact, String ruleGroup) {
        long startTime = System.currentTimeMillis();
        List<RuleHitDetailDTO> hitDetails = new ArrayList<>();
        String group = ruleGroup != null ? ruleGroup : "default";

        KieBase kieBase = kieContainerManager.getKieBase(group);
        if (kieBase == null) {
            log.warn("KieBase not found for group: {}, using default", group);
            kieBase = kieContainerManager.getKieBase("default");
        }

        KieSession kieSession = null;
        try {
            kieSession = kieBase.newKieSession();

            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    String ruleName = event.getMatch().getRule().getName();
                    String ruleCode = (String) event.getMatch().getRule().getMetaData().get("ruleCode");
                    String ruleType = (String) event.getMatch().getRule().getMetaData().get("ruleType");
                    int score = event.getMatch().getRule().getMetaData().get("score") != null
                            ? Integer.parseInt(event.getMatch().getRule().getMetaData().get("score").toString())
                            : 0;
                    String riskLevel = (String) event.getMatch().getRule().getMetaData().get("riskLevel");
                    String action = (String) event.getMatch().getRule().getMetaData().get("action");

                    RuleHitDetailDTO detail = new RuleHitDetailDTO(
                            ruleCode != null ? ruleCode : ruleName,
                            ruleName,
                            ruleType != null ? ruleType : "UNKNOWN",
                            score,
                            riskLevel != null ? riskLevel : "MEDIUM",
                            action != null ? action : "ALERT",
                            "Rule fired: " + ruleName
                    );
                    hitDetails.add(detail);

                    incrementRuleHitCount(ruleCode != null ? ruleCode : ruleName);
                    log.debug("Drools rule fired: {}, ruleCode: {}, score: {}", ruleName, ruleCode, score);
                }
            });

            kieSession.insert(fact);
            int firedRules = kieSession.fireAllRules();

            incrementRuleExecCount(group, firedRules);

            long elapsed = System.currentTimeMillis() - startTime;

            RuleExecutionResultDTO result = buildResult(fact, hitDetails, group, elapsed);
            log.info("Drools rule execution completed, group: {}, firedRules: {}, hitRules: {}, totalScore: {}, elapsed: {}ms",
                    group, firedRules, hitDetails.size(), result.getRiskScore(), elapsed);

            return result;
        } catch (Exception e) {
            log.error("Drools rule execution failed, group: {}, customerId: {}", group, fact.getCustomerId(), e);
            throw new RuntimeException("Drools rule execution failed: " + e.getMessage(), e);
        } finally {
            if (kieSession != null) {
                kieSession.dispose();
            }
        }
    }

    private RuleExecutionResultDTO buildResult(AntiFraudRuleFact fact, List<RuleHitDetailDTO> hitDetails,
                                                String ruleGroup, long elapsed) {
        RuleExecutionResultDTO result = new RuleExecutionResultDTO();
        result.setCustomerId(fact.getCustomerId());
        result.setApplicationNo(fact.getApplicationNo());
        result.setHitFlag(!hitDetails.isEmpty());
        result.setHitDetails(hitDetails);
        result.setHitRuleCount(hitDetails.size());
        result.setRuleGroup(ruleGroup);
        result.setTotalExecutionTimeMs(elapsed);

        int totalScore = 0;
        boolean hardReject = false;
        boolean needManualReview = false;
        String highestRiskLevel = "LOW";
        BigDecimal adjustedLimitRatio = BigDecimal.ONE;

        for (RuleHitDetailDTO detail : hitDetails) {
            totalScore += detail.getScore();

            if ("HIGH".equals(detail.getRiskLevel()) && !"HIGH".equals(highestRiskLevel)) {
                highestRiskLevel = "HIGH";
            } else if ("MEDIUM".equals(detail.getRiskLevel()) && "LOW".equals(highestRiskLevel)) {
                highestRiskLevel = "MEDIUM";
            }

            if ("REJECT".equals(detail.getAction())) {
                hardReject = true;
            }

            if ("ALERT".equals(detail.getAction())) {
                needManualReview = true;
                if (adjustedLimitRatio.compareTo(BigDecimal.ONE) > 0) {
                    adjustedLimitRatio = new BigDecimal("0.7");
                }
            }

            if ("REDUCE_LIMIT".equals(detail.getAction())) {
                adjustedLimitRatio = new BigDecimal("0.5");
            }
        }

        result.setRiskScore(Math.min(totalScore, 100));
        result.setRiskLevel(highestRiskLevel);
        result.setHardReject(hardReject);
        result.setNeedManualReview(needManualReview);
        result.setAdjustedLimitRatio(adjustedLimitRatio);

        if (hardReject) {
            result.setCheckResult("REJECT");
            result.setRemark("触发硬拒绝规则，流程终止");
        } else if (needManualReview) {
            result.setCheckResult("ALERT");
            result.setRemark("触发软规则，标记人工复核");
        } else {
            result.setCheckResult("PASS");
            result.setRemark("反欺诈校验通过");
        }

        return result;
    }

    private void incrementRuleHitCount(String ruleCode) {
        try {
            if (stringRedisTemplate != null) {
                stringRedisTemplate.opsForValue().increment(RULE_HIT_COUNT_KEY + ruleCode);
            }
        } catch (Exception e) {
            log.warn("Failed to increment rule hit count for: {}", ruleCode, e);
        }
    }

    private void incrementRuleExecCount(String group, int firedCount) {
        try {
            if (stringRedisTemplate != null) {
                stringRedisTemplate.opsForValue().increment(RULE_EXEC_COUNT_KEY + group, firedCount);
            }
        } catch (Exception e) {
            log.warn("Failed to increment rule exec count for group: {}", group, e);
        }
    }

    public long getRuleHitCount(String ruleCode) {
        try {
            if (stringRedisTemplate != null) {
                String count = stringRedisTemplate.opsForValue().get(RULE_HIT_COUNT_KEY + ruleCode);
                return count != null ? Long.parseLong(count) : 0;
            }
        } catch (Exception e) {
            log.warn("Failed to get rule hit count for: {}", ruleCode, e);
        }
        return 0;
    }

    public long getRuleExecCount(String group) {
        try {
            if (stringRedisTemplate != null) {
                String count = stringRedisTemplate.opsForValue().get(RULE_EXEC_COUNT_KEY + group);
                return count != null ? Long.parseLong(count) : 0;
            }
        } catch (Exception e) {
            log.warn("Failed to get rule exec count for group: {}", group, e);
        }
        return 0;
    }
}
