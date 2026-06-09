package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.FraudCheckResultEnum;
import com.bc.credit.dto.*;
import com.bc.credit.engine.impl.DroolsRuleEngine;
import com.bc.credit.engine.impl.DroolsKieContainerManager;
import com.bc.credit.entity.AntiFraudResult;
import com.bc.credit.entity.FraudRuleABTest;
import com.bc.credit.entity.FraudRuleExecutionLog;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.AntiFraudResultMapper;
import com.bc.credit.mapper.FraudRuleABTestMapper;
import com.bc.credit.mapper.FraudRuleExecutionLogMapper;
import com.bc.credit.service.AntiFraudService;
import com.bc.credit.service.FeatureQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class AntiFraudServiceImpl implements AntiFraudService {

    @Autowired
    private AntiFraudResultMapper antiFraudResultMapper;

    @Autowired
    private FraudRuleExecutionLogMapper fraudRuleExecutionLogMapper;

    @Autowired
    private FraudRuleABTestMapper fraudRuleABTestMapper;

    @Autowired
    private DroolsRuleEngine droolsRuleEngine;

    @Autowired
    private DroolsKieContainerManager kieContainerManager;

    @Autowired
    private FeatureQueryService featureQueryService;

    @Value("${credit.anti-fraud.ab-test.enabled:false}")
    private boolean abTestEnabled;

    @Override
    public AntiFraudCheckResultDTO checkFraud(LoanApplication application, String deviceInfo, String ipAddress) {
        log.info("开始反欺诈校验, customerId: {}, applicationNo: {}",
                application.getCustomerId(), application.getApplicationNo());

        String ruleGroup = resolveRuleGroup(application.getCustomerId());

        AntiFraudRuleFact fact = buildRuleFact(application, deviceInfo, ipAddress);

        RuleExecutionResultDTO droolsResult = droolsRuleEngine.executeRules(fact, ruleGroup);

        AntiFraudCheckResultDTO result = convertDroolsResult(droolsResult);

        saveRuleExecutionLogs(application, droolsResult, ruleGroup);

        log.info("反欺诈校验完成, customerId: {}, result: {}, hitRules: {}, totalScore: {}, group: {}",
                application.getCustomerId(),
                result.getCheckResult(),
                droolsResult.getHitRuleCount(),
                droolsResult.getRiskScore(),
                ruleGroup);

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

        fact.setDeviceFingerprintAssocCount(
                featureQueryService.getDeviceFingerprintAssocCount(application.getDeviceId()));
        fact.setIpInRiskProxyPool(
                featureQueryService.isIpInRiskProxyPool(ipAddress));
        fact.setContactInBlacklist(
                featureQueryService.isContactInBlacklist(application.getContactPhone()));
        fact.setMultiHeadLendingCount7d(
                featureQueryService.getMultiHeadLendingCount7d(application.getIdCard()));

        if (application.getMonthlyDebt() != null && application.getMonthlyIncome() != null
                && application.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
            fact.setDebtRatio(application.getMonthlyDebt().divide(application.getMonthlyIncome(), 4, RoundingMode.HALF_UP));
        } else {
            fact.setDebtRatio(BigDecimal.ZERO);
        }

        fact.setRecentApplicationCount(
                featureQueryService.getRecentApplicationCount(application.getCustomerId(), 30));
        fact.setAge(calculateAge(application.getIdCard()));
        fact.setIdCardLocation(
                featureQueryService.getIdCardProvince(application.getIdCard()));
        fact.setPhoneLocation("");
        fact.setIpLocation(
                featureQueryService.getIpLocation(ipAddress));
        fact.setResidentLocation(application.getResidentialAddress() != null ? application.getResidentialAddress() : "");
        fact.setBlacklist(featureQueryService.getBlacklistByType("IDCARD"));
        fact.setRiskDevices(Collections.emptyList());
        fact.setRiskIpPool(Collections.emptyList());

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
                                        String ruleGroup) {
        if (result.getHitDetails() == null) {
            return;
        }

        for (RuleHitDetailDTO detail : result.getHitDetails()) {
            FraudRuleExecutionLog logEntry = new FraudRuleExecutionLog();
            logEntry.setId(IdWorker.getId());
            logEntry.setApplicationId(application.getId());
            logEntry.setApplicationNo(application.getApplicationNo());
            logEntry.setCustomerId(application.getCustomerId());
            logEntry.setRuleGroup(ruleGroup);
            logEntry.setRuleVersion(result.getRuleVersion());
            logEntry.setRuleCode(detail.getRuleCode());
            logEntry.setRuleName(detail.getRuleName());
            logEntry.setRuleType(detail.getRuleType());
            logEntry.setHit(true);
            logEntry.setHitScore(detail.getScore());
            logEntry.setRiskLevel(detail.getRiskLevel());
            logEntry.setAction(detail.getAction());
            logEntry.setHitDetail(detail.getDetail());
            logEntry.setExecutionTimeMs(detail.getExecutionTimeMs());
            logEntry.setEngineType("DROOLS");
            logEntry.setExecutionTime(LocalDateTime.now());
            logEntry.setCreatedTime(LocalDateTime.now());
            logEntry.setDeleted(0);

            fraudRuleExecutionLogMapper.insert(logEntry);
        }
    }

    private int calculateAge(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return 0;
        }
        try {
            int birthYear = Integer.parseInt(idCard.substring(6, 10));
            int currentYear = LocalDateTime.now().getYear();
            return currentYear - birthYear;
        } catch (Exception e) {
            return 0;
        }
    }

    public void refreshRuleCache() {
        kieContainerManager.reloadAll();
    }

    public boolean validateRuleExpression(String drlContent) {
        return droolsRuleEngine.validateDrl(drlContent);
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
        result.setGeoLocation(ipAddress != null ? featureQueryService.getIpLocation(ipAddress) : null);
        result.setCheckTime(LocalDateTime.now());
        result.setRemark(resultDTO.getRemark());
        result.setCreatedTime(LocalDateTime.now());
        result.setDeleted(0);

        antiFraudResultMapper.insert(result);
        return result;
    }
}
