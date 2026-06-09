package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.FraudCheckResultEnum;
import com.bc.credit.common.enums.RiskLevelEnum;
import com.bc.credit.dto.*;
import com.bc.credit.engine.RuleEngine;
import com.bc.credit.engine.impl.DroolsRuleEngine;
import com.bc.credit.engine.impl.DroolsKieContainerManager;
import com.bc.credit.entity.AntiFraudResult;
import com.bc.credit.entity.AntiFraudRule;
import com.bc.credit.entity.FraudRuleABTest;
import com.bc.credit.entity.FraudRuleExecutionLog;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.AntiFraudResultMapper;
import com.bc.credit.mapper.AntiFraudRuleMapper;
import com.bc.credit.mapper.FraudRuleABTestMapper;
import com.bc.credit.mapper.FraudRuleExecutionLogMapper;
import com.bc.credit.service.AntiFraudService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private FraudRuleExecutionLogMapper fraudRuleExecutionLogMapper;

    @Autowired
    private FraudRuleABTestMapper fraudRuleABTestMapper;

    @Autowired
    @Qualifier("qlExpressRuleEngine")
    private RuleEngine qlExpressRuleEngine;

    @Autowired
    private DroolsRuleEngine droolsRuleEngine;

    @Autowired
    private DroolsKieContainerManager kieContainerManager;

    @Value("${credit.anti-fraud.rule-engine:QL_EXPRESS}")
    private String activeRuleEngine;

    @Value("${credit.anti-fraud.ab-test.enabled:false}")
    private boolean abTestEnabled;

    private final Map<String, List<AntiFraudRule>> ruleCache = new ConcurrentHashMap<>();
    private volatile long lastCacheTime = 0;
    private static final long CACHE_EXPIRE_MS = 30 * 60 * 1000;

    @Override
    public AntiFraudCheckResultDTO checkFraud(LoanApplication application, String deviceInfo, String ipAddress) {
        log.info("开始反欺诈校验, customerId: {}, applicationNo: {}, engine: {}",
                application.getCustomerId(), application.getApplicationNo(), activeRuleEngine);

        String ruleGroup = resolveRuleGroup(application.getCustomerId());

        if ("DROOLS".equalsIgnoreCase(activeRuleEngine)) {
            return checkFraudWithDrools(application, deviceInfo, ipAddress, ruleGroup);
        } else {
            return checkFraudWithQLExpress(application, deviceInfo, ipAddress, ruleGroup);
        }
    }

    private AntiFraudCheckResultDTO checkFraudWithDrools(LoanApplication application, String deviceInfo,
                                                          String ipAddress, String ruleGroup) {
        AntiFraudRuleFact fact = buildRuleFact(application, deviceInfo, ipAddress);

        RuleExecutionResultDTO droolsResult = droolsRuleEngine.executeRules(fact, ruleGroup);

        AntiFraudCheckResultDTO result = convertDroolsResult(droolsResult);

        saveRuleExecutionLogs(application, droolsResult, ruleGroup, "DROOLS");

        log.info("Drools反欺诈校验完成, customerId: {}, result: {}, hitRules: {}, totalScore: {}, group: {}",
                application.getCustomerId(),
                result.getCheckResult(),
                droolsResult.getHitRuleCount(),
                droolsResult.getRiskScore(),
                ruleGroup);

        return result;
    }

    private AntiFraudCheckResultDTO checkFraudWithQLExpress(LoanApplication application, String deviceInfo,
                                                              String ipAddress, String ruleGroup) {
        List<AntiFraudRule> rules = loadRules();
        List<String> hitRules = new ArrayList<>();
        List<RuleHitDetailDTO> hitDetails = new ArrayList<>();
        int totalScore = 0;
        boolean hasRejectRule = false;
        String riskLevel = RiskLevelEnum.LOW.getCode();

        Map<String, Object> context = buildContext(application, deviceInfo, ipAddress);

        for (AntiFraudRule rule : rules) {
            long ruleStart = System.currentTimeMillis();
            try {
                boolean hit = evaluateRule(rule, context);
                long ruleElapsed = System.currentTimeMillis() - ruleStart;

                if (hit) {
                    hitRules.add(rule.getRuleName() + "[" + rule.getRuleCode() + "]");
                    totalScore += rule.getRuleScore();

                    RuleHitDetailDTO detail = new RuleHitDetailDTO(
                            rule.getRuleCode(), rule.getRuleName(), rule.getRuleType(),
                            rule.getRuleScore(), rule.getRiskLevel(), rule.getAction(),
                            "Rule hit: " + rule.getRuleName());
                    detail.setExecutionTimeMs(ruleElapsed);
                    hitDetails.add(detail);

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

                    log.debug("命中反欺诈规则: {}, 分数: {}, 风险等级: {}", rule.getRuleName(), rule.getRuleScore(), rule.getRiskLevel());
                }

                saveSingleRuleLog(application, rule, hit, ruleElapsed, ruleGroup, "QL_EXPRESS");
            } catch (Exception e) {
                log.error("执行反欺诈规则失败, ruleCode: {}, ruleExpression: {}", rule.getRuleCode(), rule.getRuleExpression(), e);
            }
        }

        AntiFraudCheckResultDTO result = new AntiFraudCheckResultDTO();
        result.setCustomerId(application.getCustomerId());
        result.setFraudScore(totalScore);
        result.setRiskLevel(riskLevel);
        result.setHitRules(hitRules);
        result.setRuleCount(hitRules.size());
        result.setHitDetails(hitDetails);
        result.setRuleGroup(ruleGroup);
        result.setRuleVersion("QL_EXPRESS_V1");
        result.setHitFlag(!hitRules.isEmpty());

        boolean hardReject = hasRejectRule || totalScore >= 80 || RiskLevelEnum.HIGH.getCode().equals(riskLevel);
        result.setHardReject(hardReject);

        if (hardReject) {
            result.setCheckResult(FraudCheckResultEnum.REJECT.getCode());
            result.setRemark("触发拒绝规则: " + String.join(",", hitRules));
        } else if (totalScore >= 40 || RiskLevelEnum.MEDIUM.getCode().equals(riskLevel)) {
            result.setCheckResult(FraudCheckResultEnum.ALERT.getCode());
            result.setNeedManualReview(true);
            result.setAdjustedLimitRatio(new BigDecimal("0.7"));
            result.setRemark("触发告警规则: " + String.join(",", hitRules));
        } else {
            result.setCheckResult(FraudCheckResultEnum.PASS.getCode());
            result.setRemark("反欺诈校验通过");
        }

        log.info("QLExpress反欺诈校验完成, customerId: {}, result: {}, hitRules: {}, totalScore: {}",
                application.getCustomerId(),
                FraudCheckResultEnum.getByCode(result.getCheckResult()).getDesc(),
                hitRules.size(), totalScore);

        return result;
    }

    private String resolveRuleGroup(String customerId) {
        if (!abTestEnabled) {
            return "default";
        }

        List<FraudRuleABTest> activeTests = fraudRuleABTestMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FraudRuleABTest>()
                        .eq("status", "RUNNING")
                        .eq("deleted", 0)
                        .le("start_time", LocalDateTime.now())
                        .ge("end_time", LocalDateTime.now()));

        if (activeTests.isEmpty()) {
            return "default";
        }

        FraudRuleABTest test = activeTests.get(0);
        int hash = Math.abs(customerId.hashCode());
        int ratio = test.getTrafficRatioA() != null ? test.getTrafficRatioA() : 50;

        String group = (hash % 100) < ratio ? "A" : "B";

        log.debug("A/B test routing, customerId: {}, test: {}, group: {}", customerId, test.getTestName(), group);
        return group;
    }

    private AntiFraudRuleFact buildRuleFact(LoanApplication application, String deviceInfo, String ipAddress) {
        AntiFraudRuleFact fact = new AntiFraudRuleFact();
        fact.setCustomerId(application.getCustomerId());
        fact.setIdCard(application.getIdCard());
        fact.setPhone(application.getPhone());
        fact.setCustomerName(application.getCustomerName());
        fact.setLoanAmount(application.getLoanAmount());
        fact.setLoanTerm(application.getLoanTerm());
        fact.setLoanPurpose(application.getLoanPurpose());
        fact.setApplicationNo(application.getApplicationNo());
        fact.setIpAddress(ipAddress);
        fact.setDeviceInfo(deviceInfo);
        fact.setDeviceId(application.getDeviceId());
        fact.setContactPhone(application.getContactPhone());
        fact.setMonthlyIncome(application.getMonthlyIncome());
        fact.setMonthlyDebt(application.getMonthlyDebt());

        fact.setDeviceFingerprintAssocCount(getDeviceFingerprintAssocCount(deviceInfo));
        fact.setIpInRiskProxyPool(checkIpInRiskProxyPool(ipAddress));
        fact.setContactInBlacklist(checkContactInBlacklist(application.getContactPhone()));
        fact.setMultiHeadLendingCount7d(getMultiHeadLendingCount7d(application.getIdCard()));

        if (application.getMonthlyDebt() != null && application.getMonthlyIncome() != null
                && application.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
            fact.setDebtRatio(application.getMonthlyDebt().divide(application.getMonthlyIncome(), 4, RoundingMode.HALF_UP));
        } else {
            fact.setDebtRatio(BigDecimal.ZERO);
        }

        fact.setRecentApplicationCount(getRecentApplicationCount(application.getCustomerId()));
        fact.setAge(calculateAge(application.getIdCard()));
        fact.setIdCardLocation(extractLocationFromIdCard(application.getIdCard()));
        fact.setPhoneLocation(extractLocationFromPhone(application.getPhone()));
        fact.setIpLocation(getIpLocation(ipAddress));
        fact.setResidentLocation("北京市");
        fact.setBlacklist(Arrays.asList(getBlacklist()));
        fact.setRiskDevices(Arrays.asList(getRiskDevices()));
        fact.setRiskIpPool(Arrays.asList(getRiskIpPool()));

        return fact;
    }

    private AntiFraudCheckResultDTO convertDroolsResult(RuleExecutionResultDTO droolsResult) {
        AntiFraudCheckResultDTO result = new AntiFraudCheckResultDTO();
        result.setCustomerId(droolsResult.getCustomerId());
        result.setFraudScore(droolsResult.getRiskScore());
        result.setRiskLevel(droolsResult.getRiskLevel());
        result.setHitFlag(droolsResult.isHitFlag());
        result.setHardReject(droolsResult.isHardReject());
        result.setNeedManualReview(droolsResult.isNeedManualReview());
        result.setAdjustedLimitRatio(droolsResult.getAdjustedLimitRatio());
        result.setHitDetails(droolsResult.getHitDetails());
        result.setRuleGroup(droolsResult.getRuleGroup());
        result.setRuleVersion(droolsResult.getRuleVersion());

        List<String> hitRuleNames = new ArrayList<>();
        if (droolsResult.getHitDetails() != null) {
            for (RuleHitDetailDTO detail : droolsResult.getHitDetails()) {
                hitRuleNames.add(detail.getRuleName() + "[" + detail.getRuleCode() + "]");
            }
        }
        result.setHitRules(hitRuleNames);
        result.setRuleCount(hitRuleNames.size());

        if ("REJECT".equals(droolsResult.getCheckResult())) {
            result.setCheckResult(FraudCheckResultEnum.REJECT.getCode());
        } else if ("ALERT".equals(droolsResult.getCheckResult())) {
            result.setCheckResult(FraudCheckResultEnum.ALERT.getCode());
        } else {
            result.setCheckResult(FraudCheckResultEnum.PASS.getCode());
        }
        result.setRemark(droolsResult.getRemark());

        return result;
    }

    private void saveRuleExecutionLogs(LoanApplication application, RuleExecutionResultDTO result,
                                        String ruleGroup, String engineType) {
        if (result.getHitDetails() == null) {
            return;
        }

        for (RuleHitDetailDTO detail : result.getHitDetails()) {
            FraudRuleExecutionLog log = new FraudRuleExecutionLog();
            log.setId(IdWorker.getId());
            log.setApplicationId(application.getId());
            log.setApplicationNo(application.getApplicationNo());
            log.setCustomerId(application.getCustomerId());
            log.setRuleGroup(ruleGroup);
            log.setRuleVersion(result.getRuleVersion());
            log.setRuleCode(detail.getRuleCode());
            log.setRuleName(detail.getRuleName());
            log.setRuleType(detail.getRuleType());
            log.setHit(true);
            log.setHitScore(detail.getScore());
            log.setRiskLevel(detail.getRiskLevel());
            log.setAction(detail.getAction());
            log.setHitDetail(detail.getDetail());
            log.setExecutionTimeMs(detail.getExecutionTimeMs());
            log.setEngineType(engineType);
            log.setExecutionTime(LocalDateTime.now());
            log.setCreatedTime(LocalDateTime.now());
            log.setDeleted(0);

            fraudRuleExecutionLogMapper.insert(log);
        }
    }

    private void saveSingleRuleLog(LoanApplication application, AntiFraudRule rule, boolean hit,
                                    long executionTimeMs, String ruleGroup, String engineType) {
        FraudRuleExecutionLog log = new FraudRuleExecutionLog();
        log.setId(IdWorker.getId());
        log.setApplicationId(application.getId());
        log.setApplicationNo(application.getApplicationNo());
        log.setCustomerId(application.getCustomerId());
        log.setRuleGroup(ruleGroup);
        log.setRuleCode(rule.getRuleCode());
        log.setRuleName(rule.getRuleName());
        log.setRuleType(rule.getRuleType());
        log.setHit(hit);
        log.setHitScore(hit ? rule.getRuleScore() : 0);
        log.setRiskLevel(rule.getRiskLevel());
        log.setAction(hit ? rule.getAction() : "PASS");
        log.setExecutionTimeMs(executionTimeMs);
        log.setEngineType(engineType);
        log.setExecutionTime(LocalDateTime.now());
        log.setCreatedTime(LocalDateTime.now());
        log.setDeleted(0);

        fraudRuleExecutionLogMapper.insert(log);
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
        context.put("contactPhone", application.getContactPhone());
        return context;
    }

    private boolean evaluateRule(AntiFraudRule rule, Map<String, Object> context) throws Exception {
        String expression = rule.getRuleExpression();
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        return qlExpressRuleEngine.executeBoolean(expression, context);
    }

    private int getDeviceFingerprintAssocCount(String deviceInfo) {
        return 0;
    }

    private boolean checkIpInRiskProxyPool(String ipAddress) {
        String[] riskIps = getRiskIpPool();
        if (ipAddress == null || riskIps == null) {
            return false;
        }
        for (String riskIp : riskIps) {
            if (ipAddress.equals(riskIp)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkContactInBlacklist(String contactPhone) {
        String[] blacklist = getBlacklist();
        if (contactPhone == null || blacklist == null) {
            return false;
        }
        for (String item : blacklist) {
            if (contactPhone.equals(item)) {
                return true;
            }
        }
        return false;
    }

    private int getMultiHeadLendingCount7d(String idCard) {
        return 0;
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

    private String[] getRiskIpPool() {
        return new String[]{"10.0.0.1", "192.168.1.100", "172.16.0.1"};
    }

    private String[] getSuspiciousKeywords() {
        return new String[]{"投资", "赌博", "炒股", "理财", "还贷"};
    }

    public void refreshRuleCache() {
        lastCacheTime = 0;
        loadRules();
        kieContainerManager.reloadAll();
    }

    public boolean validateRuleExpression(String expression) {
        if ("DROOLS".equalsIgnoreCase(activeRuleEngine)) {
            return droolsRuleEngine.validateExpression(expression);
        }
        return qlExpressRuleEngine.validateExpression(expression);
    }

    public void reloadDroolsRules(String group) {
        kieContainerManager.reloadRuleGroup(group);
    }

    public void reloadAllDroolsRules() {
        kieContainerManager.reloadAll();
    }

    public void publishDrlToRedis(String group, String ruleName, String drlContent) {
        kieContainerManager.saveDrlToRedis(group, ruleName, drlContent);
        kieContainerManager.reloadRuleGroup(group);
    }

    public boolean validateDrl(String drlContent) {
        return kieContainerManager.validateDrl(drlContent);
    }

    public Map<String, Object> getRuleGroupStatus() {
        return kieContainerManager.getRuleGroupStatus();
    }

    public List<Map<String, Object>> getRuleHitStats(String startTime, String endTime) {
        return fraudRuleExecutionLogMapper.getRuleHitStats(startTime, endTime);
    }

    public List<Map<String, Object>> getABTestStats(String startTime, String endTime) {
        return fraudRuleExecutionLogMapper.getABTestStats(startTime, endTime);
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
