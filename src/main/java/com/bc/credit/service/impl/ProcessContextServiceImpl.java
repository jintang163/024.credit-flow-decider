package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.dto.LoanApplicationDTO;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.service.ProcessContextService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ProcessContextServiceImpl implements ProcessContextService {

    @Value("${credit.application.no-prefix:LN}")
    private String applicationNoPrefix;

    @Value("${credit.application.worker-id:0}")
    private int workerId;

    private final AtomicInteger sequence = new AtomicInteger(0);
    private static final int MAX_SEQUENCE = 999999;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public Map<String, Object> buildProcessContext(LoanApplication application, LoanApplicationDTO applicationDTO) {
        Map<String, Object> context = new HashMap<>();

        context.put("applicationId", application.getId());
        context.put("applicationNo", application.getApplicationNo());
        context.put("processInstanceId", application.getProcessInstanceId());
        context.put("submitTime", application.getSubmitTime());

        context.put("customerId", application.getCustomerId());
        context.put("customerName", application.getCustomerName());
        context.put("idCard", application.getIdCard());
        context.put("phone", application.getPhone());
        context.put("email", application.getEmail());
        context.put("age", application.getAge());
        context.put("educationLevel", application.getEducationLevel());
        context.put("workYears", application.getWorkYears());
        context.put("maritalStatus", application.getMaritalStatus());
        context.put("hasHouse", application.getHasHouse() != null ? application.getHasHouse() : false);
        context.put("hasCar", application.getHasCar() != null ? application.getHasCar() : false);

        context.put("loanAmount", application.getLoanAmount() != null
                ? application.getLoanAmount().doubleValue() : 0);
        context.put("loanTerm", application.getLoanTerm());
        context.put("loanPurpose", application.getLoanPurpose());

        context.put("monthlyIncome", application.getMonthlyIncome() != null
                ? application.getMonthlyIncome().doubleValue() : 0);
        context.put("monthlyDebt", application.getMonthlyDebt() != null
                ? application.getMonthlyDebt().doubleValue() : 0);

        if (application.getMonthlyIncome() != null && application.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0
                && application.getMonthlyDebt() != null) {
            double dti = application.getMonthlyDebt().doubleValue() / application.getMonthlyIncome().doubleValue();
            context.put("dti", dti);
            context.put("debtRatio", dti);
        } else {
            context.put("dti", 0.0);
            context.put("debtRatio", 0.0);
        }

        context.put("contactName", application.getContactName());
        context.put("contactPhone", application.getContactPhone());
        context.put("contactRelation", application.getContactRelation());

        context.put("ipAddress", applicationDTO.getIpAddress());
        context.put("deviceInfo", applicationDTO.getDeviceInfo());
        context.put("deviceId", application.getDeviceId());
        context.put("macAddress", application.getMacAddress());
        context.put("userAgent", application.getUserAgent());
        context.put("channel", application.getChannel());

        context.put("residentialAddress", application.getResidentialAddress());
        context.put("employer", application.getEmployer());
        context.put("position", application.getPosition());

        context.put("applyUser", applicationDTO.getSubmitBy() != null
                ? applicationDTO.getSubmitBy() : application.getCustomerId());

        context.put("applicationStatus", application.getApplicationStatus());
        context.put("returnCount", application.getReturnCount() != null ? application.getReturnCount() : 0);

        context.put("creditScore", 0);
        context.put("creditLevel", "");
        context.put("overdueCount", 0);
        context.put("overdueAmount", 0.0);
        context.put("totalLoanAmount", 0.0);
        context.put("creditCardCount", 0);
        context.put("creditCardLimit", 0.0);

        context.put("fraudScore", 0);
        context.put("fraudRiskLevel", "LOW");
        context.put("hitRules", "[]");
        context.put("fraudCheckResult", "PASS");

        context.put("creditScoringResult", 0);
        context.put("riskLevel", "LOW");

        context.put("approvedAmount", 0.0);
        context.put("approvedTerm", 0);
        context.put("interestRate", 0.0);
        context.put("limitAmount", 0.0);

        context.put("manualReviewResult", "");
        context.put("manualReviewOpinion", "");
        context.put("manualReviewer", "");

        context.put("finalApprovalResult", "");
        context.put("finalApprovalOpinion", "");
        context.put("finalApprover", "");

        context.put("contextVersion", "1.0");
        context.put("contextBuildTime", LocalDateTime.now().toString());

        log.debug("流程上下文构建完成, applicationNo: {}, contextKeys: {}",
                application.getApplicationNo(), context.keySet());

        return context;
    }

    @Override
    public String generateApplicationNo() {
        String date = LocalDate.now().format(DATE_FORMATTER);
        int seq = sequence.getAndIncrement();
        if (seq > MAX_SEQUENCE) {
            synchronized (this) {
                if (sequence.get() > MAX_SEQUENCE) {
                    sequence.set(0);
                }
                seq = sequence.getAndIncrement();
            }
        }

        String timestamp = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        String workerStr = String.format("%02d", workerId);
        String seqStr = String.format("%06d", seq);

        String applicationNo = String.format("%s%s%s%s%s",
                applicationNoPrefix, date, workerStr, timestamp, seqStr);

        log.debug("生成申请单号: {}", applicationNo);
        return applicationNo;
    }

    @Override
    public String generateIdempotentKey(LoanApplicationDTO applicationDTO) {
        StringBuilder sb = new StringBuilder();

        if (applicationDTO.getRequestId() != null && !applicationDTO.getRequestId().isEmpty()) {
            sb.append("req:").append(applicationDTO.getRequestId());
        } else {
            sb.append("cust:").append(applicationDTO.getCustomerId());
            sb.append(":idcard:").append(applicationDTO.getIdCard());
            sb.append(":phone:").append(applicationDTO.getPhone());
            sb.append(":amount:").append(applicationDTO.getLoanAmount());
            sb.append(":term:").append(applicationDTO.getLoanTerm());
            sb.append(":time:").append(LocalDateTime.now().format(TIME_FORMATTER));
        }

        String idempotentKey = DigestUtils.md5Hex(sb.toString());
        log.debug("生成幂等键: {}, original: {}", idempotentKey, sb);
        return "idempotent:loan:apply:" + idempotentKey;
    }

    @Override
    public Map<String, Object> enrichContext(Map<String, Object> context, String key, Object value) {
        if (context != null && key != null && !key.isEmpty()) {
            context.put(key, value);
            log.debug("上下文 enriched, key: {}, value: {}", key, value);
        }
        return context;
    }

    @Override
    public Object getContextValue(Map<String, Object> context, String key) {
        if (context == null || key == null || key.isEmpty()) {
            return null;
        }
        return context.get(key);
    }

    @Override
    public void validateContext(Map<String, Object> context) {
        if (context == null) {
            throw new IllegalArgumentException("流程上下文不能为空");
        }

        String[] requiredKeys = {
                "applicationId", "applicationNo", "customerId",
                "loanAmount", "loanTerm", "submitTime"
        };

        for (String key : requiredKeys) {
            if (!context.containsKey(key)) {
                throw new IllegalArgumentException("流程上下文缺少必要字段: " + key);
            }
            if (context.get(key) == null) {
                throw new IllegalArgumentException("流程上下文字段值为空: " + key);
            }
        }

        log.debug("流程上下文校验通过");
    }

    public String getContextSnapshot(Map<String, Object> context) {
        if (context == null) {
            return "{}";
        }
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("applicationNo", context.get("applicationNo"));
        snapshot.put("applicationStatus", context.get("applicationStatus"));
        snapshot.put("creditScore", context.get("creditScore"));
        snapshot.put("fraudScore", context.get("fraudScore"));
        snapshot.put("riskLevel", context.get("riskLevel"));
        snapshot.put("approvedAmount", context.get("approvedAmount"));
        return JSON.toJSONString(snapshot);
    }
}
