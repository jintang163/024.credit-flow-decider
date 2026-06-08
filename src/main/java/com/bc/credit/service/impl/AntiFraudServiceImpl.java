package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.FraudCheckResultEnum;
import com.bc.credit.common.enums.RiskLevelEnum;
import com.bc.credit.dto.AntiFraudCheckResultDTO;
import com.bc.credit.engine.RuleEngine;
import com.bc.credit.entity.AntiFraudResult;
import com.bc.credit.entity.AntiFraudRule;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.AntiFraudResultMapper;
import com.bc.credit.mapper.AntiFraudRuleMapper;
import com.bc.credit.service.AntiFraudService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AntiFraudServiceImpl implements AntiFraudService {

    @Autowired
    private AntiFraudRuleMapper antiFraudRuleMapper;

    @Autowired
    private AntiFraudResultMapper antiFraudResultMapper;

    @Autowired
    @Qualifier("qlExpressRuleEngine")
    private RuleEngine ruleEngine;

    private final Map<String, List<AntiFraudRule>> ruleCache = new ConcurrentHashMap<>();
    private volatile long lastCacheTime = 0;
    private static final long CACHE_EXPIRE_MS = 30 * 60 * 1000;

    @Override
    public AntiFraudCheckResultDTO checkFraud(LoanApplication application, String deviceInfo, String ipAddress) {
        log.info("开始反欺诈校验, customerId: {}, applicationNo: {}",
                application.getCustomerId(), application.getApplicationNo());

        List<AntiFraudRule> rules = loadRules();
        List<String> hitRules = new ArrayList<>();
        int totalScore = 0;
        boolean hasRejectRule = false;
        String riskLevel = RiskLevelEnum.LOW.getCode();

        Map<String, Object> context = buildContext(application, deviceInfo, ipAddress);

        for (AntiFraudRule rule : rules) {
            try {
                boolean hit = evaluateRule(rule, context);
                if (hit) {
                    hitRules.add(rule.getRuleName() + "[" + rule.getRuleCode() + "]");
                    totalScore += rule.getRuleScore();

                    String ruleRiskLevel = rule.getRiskLevel();
                    if (RiskLevelEnum.HIGH.getCode().equals(ruleRiskLevel)) {
                        riskLevel = RiskLevelEnum.HIGH.getCode();
                    } else if (RiskLevelEnum.MEDIUM.getCode().equals(ruleRiskLevel)
                            && RiskLevelEnum.LOW.getCode().equals(riskLevel)) {
                        riskLevel = RiskLevelEnum.MEDIUM.getCode();
                    }

                    if ("REJECT".equals(rule.getAction())) {
                        hasRejectRule = true;
                    }

                    log.debug("命中反欺诈规则: {}, 分数: {}, 风险等级: {}",
                            rule.getRuleName(), rule.getRuleScore(), rule.getRiskLevel());
                }
            } catch (Exception e) {
                log.error("执行反欺诈规则失败, ruleCode: {}, ruleExpression: {}",
                        rule.getRuleCode(), rule.getRuleExpression(), e);
            }
        }

        AntiFraudCheckResultDTO result = new AntiFraudCheckResultDTO();
        result.setCustomerId(application.getCustomerId());
        result.setFraudScore(totalScore);
        result.setRiskLevel(riskLevel);
        result.setHitRules(hitRules);
        result.setRuleCount(hitRules.size());

        if (hasRejectRule || totalScore >= 80 || RiskLevelEnum.HIGH.getCode().equals(riskLevel)) {
            result.setCheckResult(FraudCheckResultEnum.REJECT.getCode());
            result.setRemark("触发拒绝规则: " + String.join(",", hitRules));
        } else if (totalScore >= 40 || RiskLevelEnum.MEDIUM.getCode().equals(riskLevel)) {
            result.setCheckResult(FraudCheckResultEnum.ALERT.getCode());
            result.setRemark("触发告警规则: " + String.join(",", hitRules));
        } else {
            result.setCheckResult(FraudCheckResultEnum.PASS.getCode());
            result.setRemark("反欺诈校验通过");
        }

        log.info("反欺诈校验完成, customerId: {}, result: {}, hitRules: {}, totalScore: {}",
                application.getCustomerId(),
                FraudCheckResultEnum.getByCode(result.getCheckResult()).getDesc(),
                hitRules.size(), totalScore);

        return result;
    }

    private List<AntiFraudRule> loadRules() {
        long now = System.currentTimeMillis();
        if (now - lastCacheTime < CACHE_EXPIRE_MS && !ruleCache.isEmpty()) {
            return ruleCache.values().stream()
                    .flatMap(List::stream)
                    .sorted(Comparator.comparing(AntiFraudRule::getSortOrder))
                    .collect(Collectors.toList());
        }

        synchronized (this) {
            if (now - lastCacheTime < CACHE_EXPIRE_MS && !ruleCache.isEmpty()) {
                return ruleCache.values().stream()
                        .flatMap(List::stream)
                        .sorted(Comparator.comparing(AntiFraudRule::getSortOrder))
                        .collect(Collectors.toList());
            }

            List<AntiFraudRule> rules = antiFraudRuleMapper.getAllEnabledRules();
            ruleCache.clear();
            for (AntiFraudRule rule : rules) {
                ruleCache.computeIfAbsent(rule.getRuleType(), k -> new ArrayList<>()).add(rule);
            }
            lastCacheTime = now;

            log.info("反欺诈规则缓存已刷新, 共加载 {} 条规则", rules.size());

            return rules.stream()
                    .sorted(Comparator.comparing(AntiFraudRule::getSortOrder))
                    .collect(Collectors.toList());
        }
    }

    private Map<String, Object> buildContext(LoanApplication application, String deviceInfo, String ipAddress) {
        Map<String, Object> context = new HashMap<>();
        context.put("customerId", application.getCustomerId());
        context.put("idCard", application.getIdCard());
        context.put("phone", application.getPhone());
        context.put("customerName", application.getCustomerName());
        context.put("loanAmount", application.getLoanAmount() != null
                ? application.getLoanAmount().doubleValue() : 0);
        context.put("loanTerm", application.getLoanTerm());
        context.put("loanPurpose", application.getLoanPurpose());
        context.put("applicationNo", application.getApplicationNo());
        context.put("submitTime", application.getSubmitTime());
        context.put("ipAddress", ipAddress);
        context.put("deviceInfo", deviceInfo);
        context.put("applicationCount", getRecentApplicationCount(application.getCustomerId()));
        context.put("age", calculateAge(application.getIdCard()));
        context.put("idCardLocation", extractLocationFromIdCard(application.getIdCard()));
        context.put("phoneLocation", extractLocationFromPhone(application.getPhone()));
        context.put("ipLocation", getIpLocation(ipAddress));
        context.put("residentLocation", "北京市");
        context.put("blacklist", getBlacklist());
        context.put("riskDevices", getRiskDevices());
        context.put("suspiciousKeywords", getSuspiciousKeywords());
        context.put("contactPhone", "13900139000");
        return context;
    }

    private boolean evaluateRule(AntiFraudRule rule, Map<String, Object> context) throws Exception {
        String expression = rule.getRuleExpression();
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        return ruleEngine.executeBoolean(expression, context);
    }

    private int getRecentApplicationCount(String customerId) {
        return 0;
    }

    private int calculateAge(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return 30;
        }
        try {
            int birthYear = Integer.parseInt(idCard.substring(6, 10));
            int currentYear = LocalDateTime.now().getYear();
            return currentYear - birthYear;
        } catch (Exception e) {
            return 30;
        }
    }

    private String extractLocationFromIdCard(String idCard) {
        if (idCard == null || idCard.length() < 6) {
            return "";
        }
        Map<String, String> provinceCodes = new HashMap<>();
        provinceCodes.put("11", "北京市");
        provinceCodes.put("31", "上海市");
        provinceCodes.put("44", "广东省");
        provinceCodes.put("33", "浙江省");
        provinceCodes.put("32", "江苏省");
        String prefix = idCard.substring(0, 2);
        return provinceCodes.getOrDefault(prefix, "北京市");
    }

    private String extractLocationFromPhone(String phone) {
        return "北京市";
    }

    private String getIpLocation(String ipAddress) {
        return "北京市朝阳区";
    }

    private String[] getBlacklist() {
        return new String[]{"110101199001019999", "13800138999"};
    }

    private String[] getRiskDevices() {
        return new String[]{"DEVICE_RISK_001", "DEVICE_RISK_002"};
    }

    private String[] getSuspiciousKeywords() {
        return new String[]{"投资", "赌博", "炒股", "理财", "还贷"};
    }

    public void refreshRuleCache() {
        lastCacheTime = 0;
        loadRules();
    }

    public boolean validateRuleExpression(String expression) {
        return ruleEngine.validateExpression(expression);
    }

    @Override
    public AntiFraudResult saveFraudResult(LoanApplication application, AntiFraudCheckResultDTO resultDTO,
                                            String deviceInfo, String ipAddress) {
        AntiFraudResult result = new AntiFraudResult();
        result.setId(IdWorker.getId());
        result.setApplicationId(application.getId());
        result.setApplicationNo(application.getApplicationNo());
        result.setCustomerId(application.getCustomerId());
        result.setFraudScore(resultDTO.getFraudScore());
        result.setRiskLevel(resultDTO.getRiskLevel());
        result.setHitRules(JSON.toJSONString(resultDTO.getHitRules()));
        result.setRuleCount(resultDTO.getRuleCount());
        result.setCheckResult(resultDTO.getCheckResult());
        result.setDeviceInfo(deviceInfo);
        result.setIpAddress(ipAddress);
        result.setGeoLocation(ipAddress != null ? getIpLocation(ipAddress) : null);
        result.setCheckTime(LocalDateTime.now());
        result.setRemark(resultDTO.getRemark());
        result.setCreatedTime(LocalDateTime.now());
        result.setDeleted(0);

        antiFraudResultMapper.insert(result);
        return result;
    }
}
