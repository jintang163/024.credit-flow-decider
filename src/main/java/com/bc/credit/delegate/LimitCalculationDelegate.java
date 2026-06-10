package com.bc.credit.delegate;

import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.dto.LimitCalcDTO;
import com.bc.credit.entity.LimitCalcResult;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.LimitCalculationService;
import com.bc.credit.service.ProcessContextService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("limitCalculationDelegate")
public class LimitCalculationDelegate implements JavaDelegate {

    @Autowired
    private LimitCalculationService limitCalculationService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private ProcessContextService processContextService;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        Long applicationId = (Long) execution.getVariable(ProcessVariableConstants.APPLICATION_ID);
        String applicationNo = (String) execution.getVariable(ProcessVariableConstants.APPLICATION_NO);

        log.info("执行额度计算服务任务, processInstanceId: {}, applicationId: {}",
                processInstanceId, applicationId);

        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                throw new RuntimeException("贷款申请不存在: " + applicationId);
            }

            Integer creditScore = (Integer) execution.getVariable(ProcessVariableConstants.CREDIT_SCORE);
            String riskLevel = (String) execution.getVariable(ProcessVariableConstants.RISK_LEVEL);
            BigDecimal monthlyIncome = (BigDecimal) execution.getVariable(ProcessVariableConstants.MONTHLY_INCOME);
            BigDecimal monthlyDebt = (BigDecimal) execution.getVariable(ProcessVariableConstants.MONTHLY_DEBT);
            BigDecimal remainingLoanAmount = (BigDecimal) execution.getVariable(ProcessVariableConstants.REMAINING_LOAN_AMOUNT);
            Integer fraudScore = (Integer) execution.getVariable(ProcessVariableConstants.FRAUD_SCORE);
            String scoreSegment = (String) execution.getVariable(ProcessVariableConstants.SCORE_SEGMENT);

            LimitCalcDTO calcDTO = limitCalculationService.calculateLimit(
                    application, creditScore, riskLevel, monthlyIncome, monthlyDebt,
                    remainingLoanAmount, fraudScore, scoreSegment);

            LimitCalcResult calcResult = limitCalculationService.saveLimitResult(application, calcDTO);

            Map<String, Object> variables = new HashMap<>();
            variables.put(ProcessVariableConstants.CREDIT_LIMIT, calcDTO.getCreditLimit());
            variables.put(ProcessVariableConstants.MAX_AVAILABLE_LIMIT, calcDTO.getMaxAvailableLimit());
            variables.put(ProcessVariableConstants.INTEREST_RATE, calcDTO.getInterestRate());
            variables.put(ProcessVariableConstants.NEED_MANUAL_REVIEW, calcDTO.getNeedManualReview());
            variables.put(ProcessVariableConstants.LIMIT_FACTORS, calcDTO.getLimitFactors());
            variables.put(ProcessVariableConstants.LIMIT_CALC_RESULT_ID, calcResult.getId());
            variables.put(ProcessVariableConstants.LIMIT_VALIDITY_DAYS, calcDTO.getValidityDays());
            variables.put(ProcessVariableConstants.LIMIT_STRATEGY_CODE, calcDTO.getStrategyCode());
            variables.put(ProcessVariableConstants.LIMIT_ENGINE_TYPE, calcDTO.getStrategyType());

            processContextService.updateProcessVariables(execution, variables);

            application.setApprovedAmount(calcDTO.getCreditLimit());
            application.setApprovedTerm(application.getLoanTerm());
            application.setInterestRate(calcDTO.getInterestRate());
            loanApplicationMapper.updateById(application);

            log.info("额度计算服务任务执行完成, applicationNo: {}, creditLimit: {}, needManualReview: {}",
                    applicationNo, calcDTO.getCreditLimit(), calcDTO.getNeedManualReview());

        } catch (Exception e) {
            log.error("额度计算服务任务执行失败, applicationId: {}", applicationId, e);
            Map<String, Object> errorVariables = new HashMap<>();
            errorVariables.put(ProcessVariableConstants.CREDIT_LIMIT, BigDecimal.ZERO);
            errorVariables.put(ProcessVariableConstants.NEED_MANUAL_REVIEW, true);
            errorVariables.put(ProcessVariableConstants.LIMIT_ERROR, e.getMessage());
            processContextService.updateProcessVariables(execution, errorVariables);
            throw new RuntimeException("额度计算失败: " + e.getMessage(), e);
        }
    }
}
