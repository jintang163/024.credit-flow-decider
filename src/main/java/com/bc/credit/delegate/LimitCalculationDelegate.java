package com.bc.credit.delegate;

import com.bc.credit.dto.LimitCalcDTO;
import com.bc.credit.entity.LimitCalcResult;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.LimitCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Slf4j
@Component("limitCalculationDelegate")
public class LimitCalculationDelegate implements JavaDelegate {

    @Autowired
    private LimitCalculationService limitCalculationService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        Long applicationId = (Long) execution.getVariable("applicationId");
        String applicationNo = (String) execution.getVariable("applicationNo");

        log.info("执行额度计算服务任务, processInstanceId: {}, applicationId: {}",
                processInstanceId, applicationId);

        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                throw new RuntimeException("贷款申请不存在: " + applicationId);
            }

            Integer creditScore = (Integer) execution.getVariable("creditScore");
            String riskLevel = (String) execution.getVariable("riskLevel");
            BigDecimal monthlyIncome = (BigDecimal) execution.getVariable("monthlyIncome");
            BigDecimal monthlyDebt = (BigDecimal) execution.getVariable("monthlyDebt");
            BigDecimal remainingLoanAmount = (BigDecimal) execution.getVariable("remainingLoanAmount");

            LimitCalcDTO calcDTO = limitCalculationService.calculateLimit(
                    application, creditScore, riskLevel, monthlyIncome, monthlyDebt, remainingLoanAmount);

            LimitCalcResult calcResult = limitCalculationService.saveLimitResult(application, calcDTO);

            execution.setVariable("creditLimit", calcDTO.getCreditLimit());
            execution.setVariable("maxAvailableLimit", calcDTO.getMaxAvailableLimit());
            execution.setVariable("interestRate", calcDTO.getInterestRate());
            execution.setVariable("needManualReview", calcDTO.getNeedManualReview());
            execution.setVariable("limitFactors", calcDTO.getLimitFactors());
            execution.setVariable("limitCalcResultId", calcResult.getId());

            application.setApprovedAmount(calcDTO.getCreditLimit());
            application.setApprovedTerm(application.getLoanTerm());
            application.setInterestRate(calcDTO.getInterestRate());
            loanApplicationMapper.updateById(application);

            log.info("额度计算服务任务执行完成, applicationNo: {}, creditLimit: {}, needManualReview: {}",
                    applicationNo, calcDTO.getCreditLimit(), calcDTO.getNeedManualReview());

        } catch (Exception e) {
            log.error("额度计算服务任务执行失败, applicationId: {}", applicationId, e);
            execution.setVariable("creditLimit", BigDecimal.ZERO);
            execution.setVariable("needManualReview", true);
            execution.setVariable("limitError", e.getMessage());
            throw new RuntimeException("额度计算失败: " + e.getMessage(), e);
        }
    }
}
