package com.bc.credit.delegate;

import com.bc.credit.dto.CreditDataDTO;
import com.bc.credit.entity.CreditQueryRecord;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.CreditQueryService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component("creditQueryDelegate")
public class CreditQueryDelegate implements JavaDelegate {

    @Autowired
    private CreditQueryService creditQueryService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        Long applicationId = (Long) execution.getVariable("applicationId");
        String applicationNo = (String) execution.getVariable("applicationNo");

        log.info("执行征信查询服务任务, processInstanceId: {}, applicationId: {}, applicationNo: {}",
                processInstanceId, applicationId, applicationNo);

        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                throw new RuntimeException("贷款申请不存在: " + applicationId);
            }

            CreditDataDTO creditData = creditQueryService.queryCredit(application);

            CreditQueryRecord record = creditQueryService.saveCreditRecord(application, creditData);

            execution.setVariable("creditScore", creditData.getCreditScore());
            execution.setVariable("creditLevel", creditData.getCreditLevel());
            execution.setVariable("overdueCount", creditData.getOverdueCount());
            execution.setVariable("remainingLoanAmount", creditData.getRemainingLoanAmount());
            execution.setVariable("creditQuerySuccess", creditData.getSuccess());
            execution.setVariable("creditQueryRecordId", record.getId());

            if (application.getCreditScore() == null) {
                application.setCreditScore(creditData.getCreditScore());
                loanApplicationMapper.updateById(application);
            }

            log.info("征信查询服务任务执行完成, applicationNo: {}, creditScore: {}",
                    applicationNo, creditData.getCreditScore());

        } catch (Exception e) {
            log.error("征信查询服务任务执行失败, applicationId: {}", applicationId, e);
            execution.setVariable("creditQuerySuccess", false);
            execution.setVariable("creditQueryError", e.getMessage());
            throw new RuntimeException("征信查询失败: " + e.getMessage(), e);
        }
    }
}
