package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.FraudCheckResultEnum;
import com.bc.credit.common.enums.RiskLevelEnum;
import com.bc.credit.dto.AntiFraudCheckResultDTO;
import com.bc.credit.entity.AntiFraudResult;
import com.bc.credit.entity.AntiFraudRule;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.AntiFraudResultMapper;
import com.bc.credit.mapper.AntiFraudRuleMapper;
import com.bc.credit.service.AntiFraudService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AntiFraudServiceImpl implements AntiFraudService {

    @Autowired
    private AntiFraudRuleMapper antiFraudRuleMapper;

    @Autowired
    private AntiFraudResultMapper antiFraudResultMapper;

    @Override
    public AntiFraudCheckResultDTO checkFraud(LoanApplication application, String deviceInfo, String ipAddress) {
        log.info("开始反欺诈校验, customerId: {}, applicationNo: {}",
                application.getCustomerId(), application.getApplicationNo());

        List<AntiFraudRule> rules = antiFraudRuleMapper.getAllEnabledRules();
        List<String> hitRules = new ArrayList<>();
        int totalScore = 0;
        boolean hasRejectRule = false;
        String riskLevel = RiskLevelEnum.LOW.getCode();

        Map<String, Object> context = buildContext(application, ipAddress);

        for (AntiFraudRule rule : rules) {
            boolean hit = evaluateRule(rule, context);
            if (hit) {
                hitRules.add(rule.getRuleName() + "[" + rule.getRuleCode() + "]");
                totalScore += rule.getRuleScore();

                if (RiskLevelEnum.HIGH.getCode().equals(rule.getRiskLevel())) {
                    riskLevel = RiskLevelEnum.HIGH.getCode();
                } else if (RiskLevelEnum.MEDIUM.getCode().equals(rule.getRiskLevel())
                        && RiskLevelEnum.LOW.getCode().equals(riskLevel)) {
                    riskLevel = RiskLevelEnum.MEDIUM.getCode();
                }

                if ("REJECT".equals(rule.getAction())) {
                    hasRejectRule = true;
                }
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
            result.setRemark("触发拒绝规则，反欺诈校验不通过");
        } else if (totalScore >= 40 || RiskLevelEnum.MEDIUM.getCode().equals(riskLevel)) {
            result.setCheckResult(FraudCheckResultEnum.ALERT.getCode());
            result.setRemark("触发告警规则，建议人工复核");
        } else {
            result.setCheckResult(FraudCheckResultEnum.PASS.getCode());
            result.setRemark("反欺诈校验通过");
        }

        log.info("反欺诈校验完成, customerId: {}, result: {}, hitRules: {}",
                application.getCustomerId(), result.getCheckResult(), hitRules.size());

        return result;
    }

    private Map<String, Object> buildContext(LoanApplication application, String ipAddress) {
        Map<String, Object> context = new HashMap<>();
        context.put("customerId", application.getCustomerId());
        context.put("idCard", application.getIdCard());
        context.put("phone", application.getPhone());
        context.put("loanAmount", application.getLoanAmount());
        context.put("loanPurpose", application.getLoanPurpose());
        context.put("ipAddress", ipAddress);
        return context;
    }

    private boolean evaluateRule(AntiFraudRule rule, Map<String, Object> context) {
        String expression = rule.getRuleExpression();

        switch (rule.getRuleCode()) {
            case "FRAUD_002":
                return Math.random() < 0.1;
            case "FRAUD_003":
                return Math.random() < 0.15;
            case "FRAUD_005":
                String purpose = (String) context.get("loanPurpose");
                return purpose != null && (purpose.contains("投资") || purpose.contains("赌博"));
            case "FRAUD_007":
                return false;
            default:
                return Math.random() < 0.05;
        }
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
        result.setGeoLocation(ipAddress != null ? getGeoLocation(ipAddress) : null);
        result.setCheckTime(LocalDateTime.now());
        result.setRemark(resultDTO.getRemark());
        result.setCreatedTime(LocalDateTime.now());
        result.setDeleted(0);

        antiFraudResultMapper.insert(result);
        return result;
    }

    private String getGeoLocation(String ipAddress) {
        return "北京市朝阳区";
    }
}
