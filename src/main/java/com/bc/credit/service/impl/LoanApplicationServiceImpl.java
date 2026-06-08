package com.bc.credit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.credit.common.enums.ApplicationStatusEnum;
import com.bc.credit.dto.LoanApplicationDTO;
import com.bc.credit.entity.ApprovalRecord;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.ApprovalRecordMapper;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.LoanApplicationService;
import com.bc.credit.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class LoanApplicationServiceImpl implements LoanApplicationService {

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

    @Autowired
    private WorkflowService workflowService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitApplication(LoanApplicationDTO applicationDTO) {
        log.info("提交贷款申请, customerId: {}, customerName: {}, loanAmount: {}",
                applicationDTO.getCustomerId(), applicationDTO.getCustomerName(),
                applicationDTO.getLoanAmount());

        String applicationNo = generateApplicationNo();

        LoanApplication application = new LoanApplication();
        application.setId(IdWorker.getId());
        application.setApplicationNo(applicationNo);
        application.setCustomerId(applicationDTO.getCustomerId());
        application.setCustomerName(applicationDTO.getCustomerName());
        application.setIdCard(applicationDTO.getIdCard());
        application.setPhone(applicationDTO.getPhone());
        application.setLoanAmount(applicationDTO.getLoanAmount());
        application.setLoanTerm(applicationDTO.getLoanTerm());
        application.setLoanPurpose(applicationDTO.getLoanPurpose());
        application.setApplicationStatus(ApplicationStatusEnum.APPROVING.getCode());
        application.setSubmitTime(LocalDateTime.now());
        application.setRemark(applicationDTO.getRemark());
        application.setCreatedBy(applicationDTO.getSubmitBy());
        application.setCreatedTime(LocalDateTime.now());
        application.setUpdatedTime(LocalDateTime.now());
        application.setDeleted(0);

        loanApplicationMapper.insert(application);

        Map<String, Object> workflowResult = workflowService.startProcess(
                null, application, applicationDTO);

        String processInstanceId = (String) workflowResult.get("processInstanceId");
        application.setProcessInstanceId(processInstanceId);
        loanApplicationMapper.updateById(application);

        saveApprovalRecord(application.getId(), processInstanceId, null,
                "start_application", "提交申请", "申请提交",
                applicationDTO.getSubmitBy(), 0, "提交贷款申请",
                applicationDTO.getLoanAmount(), applicationDTO.getLoanTerm(), null);

        Map<String, Object> result = new HashMap<>();
        result.put("applicationId", application.getId());
        result.put("applicationNo", applicationNo);
        result.put("processInstanceId", processInstanceId);
        result.put("status", ApplicationStatusEnum.APPROVING.getCode());
        result.put("statusDesc", ApplicationStatusEnum.APPROVING.getDesc());

        log.info("贷款申请提交成功, applicationId: {}, applicationNo: {}, processInstanceId: {}",
                application.getId(), applicationNo, processInstanceId);

        return result;
    }

    @Override
    public LoanApplication getApplicationById(Long id) {
        return loanApplicationMapper.selectById(id);
    }

    @Override
    public LoanApplication getApplicationByNo(String applicationNo) {
        LambdaQueryWrapper<LoanApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LoanApplication::getApplicationNo, applicationNo);
        return loanApplicationMapper.selectOne(wrapper);
    }

    @Override
    public Map<String, Object> queryApplicationPage(int page, int size, Map<String, Object> params) {
        Page<LoanApplication> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<LoanApplication> wrapper = new LambdaQueryWrapper<>();

        if (params != null) {
            if (params.containsKey("customerId")) {
                wrapper.eq(LoanApplication::getCustomerId, params.get("customerId"));
            }
            if (params.containsKey("applicationStatus")) {
                wrapper.eq(LoanApplication::getApplicationStatus, params.get("applicationStatus"));
            }
            if (params.containsKey("applicationNo")) {
                wrapper.like(LoanApplication::getApplicationNo, params.get("applicationNo"));
            }
            if (params.containsKey("customerName")) {
                wrapper.like(LoanApplication::getCustomerName, params.get("customerName"));
            }
        }

        wrapper.orderByDesc(LoanApplication::getCreatedTime);
        IPage<LoanApplication> pageResult = loanApplicationMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("records", pageResult.getRecords());

        return result;
    }

    @Override
    public void updateApplicationStatus(Long id, Integer status, String remark) {
        LoanApplication application = loanApplicationMapper.selectById(id);
        if (application != null) {
            application.setApplicationStatus(status);
            if (ApplicationStatusEnum.REJECTED.getCode().equals(status)) {
                application.setRejectReason(remark);
            }
            application.setUpdatedTime(LocalDateTime.now());
            loanApplicationMapper.updateById(application);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveApprovalRecord(Long applicationId, String processInstanceId, String taskId,
                                    String taskKey, String taskName, String approveNode,
                                    String approver, Integer approveResult, String approveOpinion,
                                    BigDecimal approveAmount, Integer approveTerm, BigDecimal interestRate) {
        ApprovalRecord record = new ApprovalRecord();
        record.setId(IdWorker.getId());
        record.setApplicationId(applicationId);
        record.setApplicationNo(getApplicationById(applicationId).getApplicationNo());
        record.setProcessInstanceId(processInstanceId);
        record.setTaskId(taskId);
        record.setTaskKey(taskKey);
        record.setTaskName(taskName);
        record.setApproveNode(approveNode);
        record.setApprover(approver);
        record.setApproveResult(approveResult);
        record.setApproveOpinion(approveOpinion);
        record.setApproveAmount(approveAmount);
        record.setApproveTerm(approveTerm);
        record.setInterestRate(interestRate);
        record.setStartTime(LocalDateTime.now());
        record.setEndTime(LocalDateTime.now());
        record.setDuration(0L);
        record.setCreatedTime(LocalDateTime.now());
        record.setDeleted(0);

        approvalRecordMapper.insert(record);
    }

    private String generateApplicationNo() {
        String prefix = "LN";
        String date = java.time.LocalDate.now().toString().replace("-", "");
        String random = String.format("%06d", new java.util.Random().nextInt(1000000));
        return prefix + date + random;
    }
}
