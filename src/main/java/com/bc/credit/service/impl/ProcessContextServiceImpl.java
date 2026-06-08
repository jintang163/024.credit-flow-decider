package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.dto.LoanApplicationDTO;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.service.ProcessContextService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private RuntimeService runtimeService;

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

        context.put(ProcessVariableConstants.APPLICATION_ID, application.getId());
        context.put(ProcessVariableConstants.APPLICATION_NO, application.getApplicationNo());
        context.put(ProcessVariableConstants.PROCESS_INSTANCE_ID, application.getProcessInstanceId());
        context.put(ProcessVariableConstants.SUBMIT_TIME, application.getSubmitTime());

        context.put(ProcessVariableConstants.CUSTOMER_ID, application.getCustomerId());
        context.put(ProcessVariableConstants.CUSTOMER_NAME, application.getCustomerName());
        context.put(ProcessVariableConstants.ID_CARD, application.getIdCard());
        context.put(ProcessVariableConstants.PHONE, application.getPhone());
        context.put(ProcessVariableConstants.EMAIL, application.getEmail());
        context.put(ProcessVariableConstants.AGE, application.getAge());
        context.put(ProcessVariableConstants.EDUCATION_LEVEL, application.getEducationLevel());
        context.put(ProcessVariableConstants.WORK_YEARS, application.getWorkYears());
        context.put(ProcessVariableConstants.MARITAL_STATUS, application.getMaritalStatus());
        context.put(ProcessVariableConstants.HAS_HOUSE, application.getHasHouse() != null ? application.getHasHouse() : false);
        context.put(ProcessVariableConstants.HAS_CAR, application.getHasCar() != null ? application.getHasCar() : false);

        context.put(ProcessVariableConstants.LOAN_AMOUNT, application.getLoanAmount() != null
                ? application.getLoanAmount().doubleValue() : 0);
        context.put(ProcessVariableConstants.LOAN_TERM, application.getLoanTerm());
        context.put(ProcessVariableConstants.LOAN_PURPOSE, application.getLoanPurpose());

        context.put(ProcessVariableConstants.MONTHLY_INCOME, application.getMonthlyIncome() != null
                ? application.getMonthlyIncome().doubleValue() : 0);
        context.put(ProcessVariableConstants.MONTHLY_DEBT, application.getMonthlyDebt() != null
                ? application.getMonthlyDebt().doubleValue() : 0);

        if (application.getMonthlyIncome() != null && application.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0
                && application.getMonthlyDebt() != null) {
            double dti = application.getMonthlyDebt().doubleValue() / application.getMonthlyIncome().doubleValue();
            context.put(ProcessVariableConstants.DTI, dti);
            context.put(ProcessVariableConstants.DEBT_RATIO, dti);
        } else {
            context.put(ProcessVariableConstants.DTI, 0.0);
            context.put(ProcessVariableConstants.DEBT_RATIO, 0.0);
        }

        context.put(ProcessVariableConstants.CONTACT_NAME, application.getContactName());
        context.put(ProcessVariableConstants.CONTACT_PHONE, application.getContactPhone());
        context.put(ProcessVariableConstants.CONTACT_RELATION, application.getContactRelation());

        context.put(ProcessVariableConstants.IP_ADDRESS, applicationDTO.getIpAddress());
        context.put(ProcessVariableConstants.DEVICE_INFO, applicationDTO.getDeviceInfo());
        context.put(ProcessVariableConstants.DEVICE_ID, application.getDeviceId());
        context.put(ProcessVariableConstants.MAC_ADDRESS, application.getMacAddress());
        context.put(ProcessVariableConstants.USER_AGENT, application.getUserAgent());
        context.put(ProcessVariableConstants.CHANNEL, application.getChannel());

        context.put(ProcessVariableConstants.RESIDENTIAL_ADDRESS, application.getResidentialAddress());
        context.put(ProcessVariableConstants.EMPLOYER, application.getEmployer());
        context.put(ProcessVariableConstants.POSITION, application.getPosition());

        context.put(ProcessVariableConstants.APPLY_USER, applicationDTO.getSubmitBy() != null
                ? applicationDTO.getSubmitBy() : application.getCustomerId());

        context.put(ProcessVariableConstants.APPLICATION_STATUS, application.getApplicationStatus());
        context.put(ProcessVariableConstants.RETURN_COUNT, application.getReturnCount() != null ? application.getReturnCount() : 0);

        context.put(ProcessVariableConstants.CREDIT_SCORE, 0);
        context.put(ProcessVariableConstants.CREDIT_LEVEL, "");
        context.put(ProcessVariableConstants.OVERDUE_COUNT, 0);
        context.put(ProcessVariableConstants.OVERDUE_AMOUNT, 0.0);
        context.put(ProcessVariableConstants.TOTAL_LOAN_AMOUNT, 0.0);
        context.put(ProcessVariableConstants.CREDIT_CARD_COUNT, 0);
        context.put(ProcessVariableConstants.CREDIT_CARD_LIMIT, 0.0);
        context.put(ProcessVariableConstants.CREDIT_QUERY_SUCCESS, false);

        context.put(ProcessVariableConstants.FRAUD_SCORE, 0);
        context.put(ProcessVariableConstants.FRAUD_RISK_LEVEL, ProcessVariableConstants.RISK_LOW);
        context.put(ProcessVariableConstants.HIT_RULES, "[]");
        context.put(ProcessVariableConstants.FRAUD_CHECK_RESULT, ProcessVariableConstants.RESULT_PASS);
        context.put(ProcessVariableConstants.FRAUD_RULE_COUNT, 0);
        context.put(ProcessVariableConstants.FRAUD_CHECK_SUCCESS, false);

        context.put(ProcessVariableConstants.CREDIT_SCORING_RESULT, 0);
        context.put(ProcessVariableConstants.RISK_LEVEL, ProcessVariableConstants.RISK_LOW);
        context.put(ProcessVariableConstants.SCORING_SUCCESS, false);

        context.put(ProcessVariableConstants.APPROVED_AMOUNT, 0.0);
        context.put(ProcessVariableConstants.APPROVED_TERM, 0);
        context.put(ProcessVariableConstants.INTEREST_RATE, 0.0);
        context.put(ProcessVariableConstants.LIMIT_AMOUNT, 0.0);
        context.put(ProcessVariableConstants.LIMIT_CALC_SUCCESS, false);

        context.put(ProcessVariableConstants.MANUAL_REVIEW_RESULT, "");
        context.put(ProcessVariableConstants.MANUAL_REVIEW_OPINION, "");
        context.put(ProcessVariableConstants.MANUAL_REVIEWER, "");

        context.put(ProcessVariableConstants.FINAL_APPROVAL_RESULT, "");
        context.put(ProcessVariableConstants.FINAL_APPROVAL_OPINION, "");
        context.put(ProcessVariableConstants.FINAL_APPROVER, "");

        context.put(ProcessVariableConstants.CONTEXT_VERSION, "1.0");
        context.put(ProcessVariableConstants.CONTEXT_BUILD_TIME, LocalDateTime.now().toString());

        log.debug("流程上下文构建完成, applicationNo: {}, contextSize: {}",
                application.getApplicationNo(), context.size());

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
        if (applicationDTO.getRequestId() == null || applicationDTO.getRequestId().trim().isEmpty()) {
            throw new IllegalArgumentException("请求ID不能为空");
        }

        String idempotentKey = "idempotent:loan:apply:" + applicationDTO.getRequestId().trim();
        log.debug("生成幂等键: {}, requestId: {}", idempotentKey, applicationDTO.getRequestId());
        return idempotentKey;
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
                ProcessVariableConstants.APPLICATION_ID,
                ProcessVariableConstants.APPLICATION_NO,
                ProcessVariableConstants.CUSTOMER_ID,
                ProcessVariableConstants.LOAN_AMOUNT,
                ProcessVariableConstants.LOAN_TERM,
                ProcessVariableConstants.SUBMIT_TIME
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

    @Override
    public void updateProcessVariables(DelegateExecution execution, String key, Object value) {
        if (execution != null && key != null && !key.isEmpty()) {
            execution.setVariable(key, value);
            log.debug("更新流程变量(DelegateExecution), key: {}, value: {}, executionId: {}",
                    key, value, execution.getId());
        }
    }

    @Override
    public void updateProcessVariables(DelegateExecution execution, Map<String, Object> variables) {
        if (execution != null && variables != null && !variables.isEmpty()) {
            execution.setVariables(variables);
            log.debug("批量更新流程变量(DelegateExecution), variablesSize: {}, executionId: {}",
                    variables.size(), execution.getId());
        }
    }

    @Override
    public Map<String, Object> mergeWithFlowableVariables(Map<String, Object> localContext, String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isEmpty()) {
            return localContext != null ? localContext : new HashMap<>();
        }

        Map<String, Object> mergedContext = new HashMap<>();
        if (localContext != null) {
            mergedContext.putAll(localContext);
        }

        try {
            Map<String, Object> flowableVariables = runtimeService.getVariables(processInstanceId);
            if (flowableVariables != null && !flowableVariables.isEmpty()) {
                mergedContext.putAll(flowableVariables);
                log.debug("合并Flowable流程变量, processInstanceId: {}, flowableVarSize: {}, mergedSize: {}",
                        processInstanceId, flowableVariables.size(), mergedContext.size());
            }
        } catch (Exception e) {
            log.warn("从Flowable获取流程变量失败, processInstanceId: {}", processInstanceId, e);
        }

        return mergedContext;
    }

    @Override
    public void syncContextToFlowable(String processInstanceId, Map<String, Object> context) {
        if (processInstanceId == null || processInstanceId.isEmpty()
                || context == null || context.isEmpty()) {
            return;
        }

        try {
            runtimeService.setVariables(processInstanceId, context);
            log.debug("同步上下文到Flowable成功, processInstanceId: {}, varSize: {}",
                    processInstanceId, context.size());
        } catch (Exception e) {
            log.error("同步上下文到Flowable失败, processInstanceId: {}", processInstanceId, e);
            throw new RuntimeException("同步流程上下文失败: " + e.getMessage(), e);
        }
    }

    public String getContextSnapshot(Map<String, Object> context) {
        if (context == null) {
            return "{}";
        }
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put(ProcessVariableConstants.APPLICATION_NO, context.get(ProcessVariableConstants.APPLICATION_NO));
        snapshot.put(ProcessVariableConstants.APPLICATION_STATUS, context.get(ProcessVariableConstants.APPLICATION_STATUS));
        snapshot.put(ProcessVariableConstants.CREDIT_SCORE, context.get(ProcessVariableConstants.CREDIT_SCORE));
        snapshot.put(ProcessVariableConstants.FRAUD_SCORE, context.get(ProcessVariableConstants.FRAUD_SCORE));
        snapshot.put(ProcessVariableConstants.RISK_LEVEL, context.get(ProcessVariableConstants.RISK_LEVEL));
        snapshot.put(ProcessVariableConstants.APPROVED_AMOUNT, context.get(ProcessVariableConstants.APPROVED_AMOUNT));
        return JSON.toJSONString(snapshot);
    }
}
