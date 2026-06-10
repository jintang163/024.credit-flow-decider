package com.bc.credit.engine.impl;

import com.bc.credit.dto.LimitCalcContext;
import com.bc.credit.engine.RuleEngineStatsHelper;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieBase;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DroolsLimitEngine {

    private static final String MODULE = "limit";
    private static final String DEFAULT_GROUP = "limit";

    @Autowired
    private DroolsKieContainerManager kieContainerManager;

    @Autowired
    private RuleEngineStatsHelper statsHelper;

    public LimitCalcContext execute(LimitCalcContext context, String ruleGroup) {
        long startTime = System.currentTimeMillis();
        String group = ruleGroup != null ? ruleGroup : DEFAULT_GROUP;
        List<String> firedRuleNames = new ArrayList<>();

        KieBase kieBase = kieContainerManager.getKieBase(group);
        if (kieBase == null) {
            log.warn("KieBase not found for limit group: {}, trying to load", group);
            kieContainerManager.loadRuleGroupFromFiles(group,
                    "com.bc.credit.limit");
            kieBase = kieContainerManager.getKieBase(group);
        }

        if (kieBase == null) {
            throw new RuntimeException("KieBase not available for limit group: " + group);
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
                    firedRuleNames.add(ruleName);
                    statsHelper.recordHit(MODULE, ruleCode != null ? ruleCode : ruleName);
                    log.debug("Drools limit rule fired: {}, ruleCode: {}, ruleType: {}",
                            ruleName, ruleCode, ruleType);
                }
            });

            kieSession.insert(context);
            int firedRules = kieSession.fireAllRules();

            long elapsed = System.currentTimeMillis() - startTime;
            statsHelper.recordExecution(MODULE, group, firedRules, elapsed);

            log.info("Drools limit engine executed, group: {}, firedRules: {}, firedRuleNames: {}, " +
                            "finalLimit: {}, elapsed: {}ms",
                    group, firedRules, firedRuleNames, context.getFinalLimit(), elapsed);

            return context;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            statsHelper.recordExecution(MODULE, group, 0, elapsed);
            log.error("Drools limit engine execution failed, group: {}, customerId: {}",
                    group, context.getCustomerId(), e);
            throw new RuntimeException("Drools额度计算规则执行失败: " + e.getMessage(), e);
        } finally {
            if (kieSession != null) {
                kieSession.dispose();
            }
        }
    }

    public boolean validateDrl(String drlContent) {
        return kieContainerManager.validateDrl(drlContent, "com.bc.credit.limit");
    }

    public long getRuleHitCount(String ruleCode) {
        return statsHelper.getHitCount(MODULE, ruleCode);
    }

    public long getRuleExecCount(String group) {
        return statsHelper.getExecCount(MODULE, group);
    }
}
