package com.bc.credit.delegate;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.ApplicationStatusEnum;
import com.bc.credit.entity.ApprovalRecord;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.ApprovalRecordMapper;
import com.bc.credit.mapper.LoanApplicationMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component("approvalNotificationDelegate")
public class ApprovalNotificationDelegate implements JavaDelegate {

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        Long applicationId = (Long) execution.getVariable("applicationId");
        String applicationNo = (String) execution.getVariable("applicationNo");

        log.info("执行审批通过通知服务任务, processInstanceId: {}, applicationId: {}",
                processInstanceId, applicationId);

        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                throw new RuntimeException("贷款申请不存在: " + applicationId);
            }

            BigDecimal creditLimit = (BigDecimal) execution.getVariable("creditLimit");
            Integer approvedTerm = application.getLoanTerm();
            BigDecimal interestRate = (BigDecimal) execution.getVariable("interestRate");

            application.setApplicationStatus(ApplicationStatusEnum.APPROVED.getCode());
            application.setApprovedAmount(creditLimit);
            application.setApprovedTerm(approvedTerm);
            application.setInterestRate(interestRate);
            application.setApproveTime(LocalDateTime.now());
            application.setUpdatedTime(LocalDateTime.now());
            loanApplicationMapper.updateById(application);

            ApprovalRecord record = new ApprovalRecord();
            record.setId(IdWorker.getId());
            record.setApplicationId(applicationId);
            record.setApplicationNo(applicationNo);
            record.setProcessInstanceId(processInstanceId);
            record.setTaskKey("approval_notification_task");
            record.setTaskName("审批通过通知");
            record.setApproveNode("终审通过");
            record.setApprover("SYSTEM");
            record.setApproveResult(0);
            record.setApproveOpinion("系统自动审批通过");
            record.setApproveAmount(creditLimit);
            record.setApproveTerm(approvedTerm);
            record.setInterestRate(interestRate);
            record.setStartTime(LocalDateTime.now());
            record.setEndTime(LocalDateTime.now());
            record.setDuration(0L);
            record.setCreatedTime(LocalDateTime.now());
            record.setDeleted(0);
            approvalRecordMapper.insert(record);

            sendApprovalNotification(application, creditLimit, approvedTerm, interestRate);

            log.info("审批通过通知服务任务执行完成, applicationNo: {}, approvedAmount: {}",
                    applicationNo, creditLimit);

        } catch (Exception e) {
            log.error("审批通过通知服务任务执行失败, applicationId: {}", applicationId, e);
            throw new RuntimeException("审批通知失败: " + e.getMessage(), e);
        }
    }

    private void sendApprovalNotification(LoanApplication application, BigDecimal amount,
                                           Integer term, BigDecimal rate) {
        log.info("发送审批通过通知, customerId: {}, phone: {}, amount: {}, term: {}, rate: {}",
                application.getCustomerId(), application.getPhone(), amount, term, rate);
    }
}
