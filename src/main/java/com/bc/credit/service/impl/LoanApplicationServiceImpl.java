package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.credit.common.enums.ApplicationStatusEnum;
import com.bc.credit.common.exception.BusinessException;
import com.bc.credit.dto.LoanApplicationDTO;
import com.bc.credit.entity.ApprovalRecord;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.ApprovalRecordMapper;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.IdempotentService;
import com.bc.credit.service.LoanApplicationService;
import com.bc.credit.service.ProcessContextService;
import com.bc.credit.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LoanApplicationServiceImpl implements LoanApplicationService {

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ProcessContextService processContextService;

    @Autowired(required = false)
    private IdempotentService idempotentService;

    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
            "^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitApplication(LoanApplicationDTO applicationDTO) {
        return submitApplicationWithIp(applicationDTO, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitApplicationWithIp(LoanApplicationDTO applicationDTO,
                                                        String ipAddress, String userAgent) {
        log.info("提交贷款申请, customerId: {}, customerName: {}, loanAmount: {}",
                applicationDTO.getCustomerId(), applicationDTO.getCustomerName(),
                applicationDTO.getLoanAmount());

        String idempotentKey = null;
        try {
            if (ipAddress != null && !ipAddress.isEmpty()) {
                applicationDTO.setIpAddress(ipAddress);
            }
            if (userAgent != null && !userAgent.isEmpty()) {
                applicationDTO.setUserAgent(userAgent);
            }

            validateApplication(applicationDTO);

            idempotentKey = processContextService.generateIdempotentKey(applicationDTO);

            if (idempotentService != null) {
                String existingResponse = idempotentService.getExistingResponse(idempotentKey);
                if (existingResponse != null) {
                    log.info("检测到重复请求，直接返回已有响应, key: {}", idempotentKey);
                    return JSON.parseObject(existingResponse, Map.class);
                }

                boolean acquired = idempotentService.checkAndAcquire(idempotentKey, 300);
                if (!acquired) {
                    if (idempotentService.isProcessing(idempotentKey)) {
                        throw new BusinessException("请求正在处理中，请稍后重试");
                    } else {
                        existingResponse = idempotentService.getExistingResponse(idempotentKey);
                        if (existingResponse != null) {
                            return JSON.parseObject(existingResponse, Map.class);
                        }
                        throw new BusinessException("请勿重复提交申请");
                    }
                }
            }

            LoanApplication application = convertToEntity(applicationDTO);
            String applicationNo = processContextService.generateApplicationNo();
            application.setApplicationNo(applicationNo);
            application.setId(IdWorker.getId());
            application.setApplicationStatus(ApplicationStatusEnum.APPROVING.getCode());
            application.setSubmitTime(LocalDateTime.now());
            application.setCreatedTime(LocalDateTime.now());
            application.setUpdatedTime(LocalDateTime.now());
            application.setDeleted(0);
            application.setReturnCount(0);

            loanApplicationMapper.insert(application);

            Map<String, Object> workflowResult = workflowService.startProcess(
                    null, application, applicationDTO);

            String processInstanceId = (String) workflowResult.get("processInstanceId");
            application.setProcessInstanceId(processInstanceId);
            loanApplicationMapper.updateById(application);

            Map<String, Object> processContext = buildProcessContext(application, applicationDTO);

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
            result.put("submitTime", application.getSubmitTime());
            result.put("processContext", processContext);

            if (idempotentService != null) {
                idempotentService.saveResponse(idempotentKey, JSON.toJSONString(result), 3600);
            }

            log.info("贷款申请提交成功, applicationId: {}, applicationNo: {}, processInstanceId: {}",
                    application.getId(), applicationNo, processInstanceId);

            return result;

        } catch (BusinessException e) {
            log.warn("贷款申请业务异常: {}", e.getMessage());
            if (idempotentKey != null && idempotentService != null) {
                idempotentService.release(idempotentKey);
            }
            throw e;
        } catch (Exception e) {
            log.error("贷款申请提交失败", e);
            if (idempotentKey != null && idempotentService != null) {
                idempotentService.release(idempotentKey);
            }
            throw new RuntimeException("申请提交失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateApplication(LoanApplicationDTO dto) {
        log.debug("开始校验申请数据, customerId: {}", dto.getCustomerId());

        if (dto.getAge() != null) {
            if (dto.getAge() < 18 || dto.getAge() > 70) {
                throw new BusinessException("申请人年龄必须在18-70岁之间");
            }

            int ageFromIdCard = calculateAgeFromIdCard(dto.getIdCard());
            if (Math.abs(ageFromIdCard - dto.getAge()) > 1) {
                throw new BusinessException("年龄与身份证号信息不符");
            }
        }

        if (dto.getLoanAmount() != null) {
            if (dto.getLoanAmount().compareTo(new BigDecimal("1000")) < 0) {
                throw new BusinessException("申请金额不能小于1000元");
            }
            if (dto.getLoanAmount().compareTo(new BigDecimal("500000")) > 0) {
                throw new BusinessException("申请金额不能大于50万元");
            }
        }

        if (dto.getLoanTerm() != null) {
            if (dto.getLoanTerm() < 3 || dto.getLoanTerm() > 360) {
                throw new BusinessException("贷款期限必须在3-360个月之间");
            }
        }

        if (dto.getIdCard() != null && !ID_CARD_PATTERN.matcher(dto.getIdCard()).matches()) {
            throw new BusinessException("身份证号格式不正确");
        }

        if (dto.getPhone() != null && !PHONE_PATTERN.matcher(dto.getPhone()).matches()) {
            throw new BusinessException("手机号格式不正确");
        }

        if (dto.getEmail() != null && !EMAIL_PATTERN.matcher(dto.getEmail()).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }

        if (dto.getMonthlyIncome() != null && dto.getMonthlyDebt() != null
                && dto.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dti = dto.getMonthlyDebt().divide(dto.getMonthlyIncome(), 4, BigDecimal.ROUND_HALF_UP);
            if (dti.compareTo(new BigDecimal("0.75")) > 0) {
                throw new BusinessException("债务收入比过高，不能超过75%");
            }
        }

        if (dto.getEducationLevel() != null) {
            if (dto.getEducationLevel() < 1 || dto.getEducationLevel() > 6) {
                throw new BusinessException("教育程度值必须在1-6之间");
            }
        }

        if (dto.getWorkYears() != null) {
            if (dto.getWorkYears() < 0 || dto.getWorkYears() > 50) {
                throw new BusinessException("工作年限必须在0-50年之间");
            }
        }

        log.debug("申请数据校验通过, customerId: {}", dto.getCustomerId());
    }

    @Override
    public Map<String, Object> buildProcessContext(LoanApplication application, LoanApplicationDTO applicationDTO) {
        Map<String, Object> context = processContextService.buildProcessContext(application, applicationDTO);
        processContextService.validateContext(context);
        return context;
    }

    @Override
    public LoanApplication convertToEntity(LoanApplicationDTO dto) {
        LoanApplication entity = new LoanApplication();
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setIdCard(dto.getIdCard());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        entity.setLoanAmount(dto.getLoanAmount());
        entity.setLoanTerm(dto.getLoanTerm());
        entity.setLoanPurpose(dto.getLoanPurpose());
        entity.setMonthlyIncome(dto.getMonthlyIncome());
        entity.setMonthlyDebt(dto.getMonthlyDebt());
        entity.setAge(dto.getAge());
        entity.setEducationLevel(dto.getEducationLevel());
        entity.setWorkYears(dto.getWorkYears());
        entity.setHasHouse(dto.getHasHouse());
        entity.setHasCar(dto.getHasCar());
        entity.setMaritalStatus(dto.getMaritalStatus());
        entity.setResidentialAddress(dto.getResidentialAddress());
        entity.setEmployer(dto.getEmployer());
        entity.setPosition(dto.getPosition());
        entity.setContactName(dto.getContactName());
        entity.setContactPhone(dto.getContactPhone());
        entity.setContactRelation(dto.getContactRelation());
        entity.setChannel(dto.getChannel());
        entity.setDeviceId(dto.getDeviceId());
        entity.setMacAddress(dto.getMacAddress());
        entity.setUserAgent(dto.getUserAgent());
        entity.setRemark(dto.getRemark());
        entity.setCreatedBy(dto.getSubmitBy());
        return entity;
    }

    private int calculateAgeFromIdCard(String idCard) {
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
            if (params.containsKey("idCard")) {
                wrapper.eq(LoanApplication::getIdCard, params.get("idCard"));
            }
            if (params.containsKey("phone")) {
                wrapper.eq(LoanApplication::getPhone, params.get("phone"));
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
    public Map<String, Object> getApplicationContext(String applicationNo) {
        LoanApplication application = getApplicationByNo(applicationNo);
        if (application == null) {
            throw new BusinessException("申请不存在: " + applicationNo);
        }

        Map<String, Object> context = new HashMap<>();
        context.put("applicationId", application.getId());
        context.put("applicationNo", application.getApplicationNo());
        context.put("processInstanceId", application.getProcessInstanceId());
        context.put("applicationStatus", application.getApplicationStatus());
        context.put("applicationStatusDesc", ApplicationStatusEnum.getByCode(application.getApplicationStatus()));
        context.put("customerId", application.getCustomerId());
        context.put("customerName", application.getCustomerName());
        context.put("loanAmount", application.getLoanAmount());
        context.put("loanTerm", application.getLoanTerm());
        context.put("approvedAmount", application.getApprovedAmount());
        context.put("riskLevel", application.getRiskLevel());
        context.put("creditScore", application.getCreditScore());
        context.put("returnCount", application.getReturnCount());
        context.put("returnReason", application.getReturnReason());
        context.put("rejectReason", application.getRejectReason());
        context.put("submitTime", application.getSubmitTime());
        context.put("approveTime", application.getApproveTime());

        return context;
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
}
