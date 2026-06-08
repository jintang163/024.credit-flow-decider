package com.bc.credit.delegate;

import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.dto.CreditDataDTO;
import com.bc.credit.entity.CreditQueryRecord;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.CreditQueryService;
import com.bc.credit.service.ProcessContextService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("creditQueryDelegate")
public class CreditQueryDelegate implements JavaDelegate {

    @Autowired
    private CreditQueryService creditQueryService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private ProcessContextService processContextService;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        Long applicationId = (Long) execution.getVariable(ProcessVariableConstants.APPLICATION_ID);
        String applicationNo = (String) execution.getVariable(ProcessVariableConstants.APPLICATION_NO);

        log.info("执行征信查询服务任务, processInstanceId: {}, applicationId: {}, applicationNo: {}",
                processInstanceId, applicationId, applicationNo);

        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                throw new RuntimeException("贷款申请不存在: " + applicationId);
            }

            CreditDataDTO creditData = creditQueryService.queryCredit(application);

            CreditQueryRecord record = creditQueryService.saveCreditRecord(application, creditData);

            Map<String, Object> variables = new HashMap<>();
            variables.put(ProcessVariableConstants.CREDIT_SCORE, creditData.getCreditScore());
            variables.put(ProcessVariableConstants.CREDIT_LEVEL, creditData.getCreditLevel());
            variables.put(ProcessVariableConstants.OVERDUE_COUNT, creditData.getOverdueCount());
            variables.put(ProcessVariableConstants.REMAINING_LOAN_AMOUNT, creditData.getRemainingLoanAmount());
            variables.put(ProcessVariableConstants.CREDIT_QUERY_SUCCESS, creditData.getSuccess());
            variables.put(ProcessVariableConstants.CREDIT_QUERY_RECORD_ID, record.getId());

            processContextService.updateProcessVariables(execution, variables);

            if (application.getCreditScore() == null) {
                application.setCreditScore(creditData.getCreditScore());
                loanApplicationMapper.updateById(application);
            }

            log.info("征信查询服务任务执行完成, applicationNo: {}, creditScore: {}",
                    applicationNo, creditData.getCreditScore());

        } catch (Exception e) {
            log.error("征信查询服务任务执行失败, applicationId: {}", applicationId, e);
            Map<String, Object> errorVariables = new HashMap<>();
            errorVariables.put(ProcessVariableConstants.CREDIT_QUERY_SUCCESS, false);
            errorVariables.put(ProcessVariableConstants.CREDIT_QUERY_ERROR, e.getMessage());
            processContextService.updateProcessVariables(execution, errorVariables);
            throw new RuntimeException("征信查询失败: " + e.getMessage(), e);
        }
    }
}
