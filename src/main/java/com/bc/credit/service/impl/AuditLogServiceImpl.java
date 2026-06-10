package com.bc.credit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.credit.entity.AuditLog;
import com.bc.credit.mapper.AuditLogMapper;
import com.bc.credit.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AuditLogServiceImpl implements AuditLogService {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Override
    @Async
    public void log(AuditLog auditLog) {
        try {
            if (auditLog.getId() == null) {
                auditLog.setId(IdWorker.getId());
            }
            if (auditLog.getOperationTime() == null) {
                auditLog.setOperationTime(LocalDateTime.now());
            }
            if (auditLog.getCreatedTime() == null) {
                auditLog.setCreatedTime(LocalDateTime.now());
            }
            if (auditLog.getDeleted() == null) {
                auditLog.setDeleted(0);
            }
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("审计日志记录失败", e);
        }
    }

    @Override
    @Async
    public void log(String operationType, String operationModule, String operationDesc,
                    String operator, String targetId, String targetType,
                    String requestParams, String responseResult,
                    String clientIp, boolean success, String errorMsg, Long costMs) {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(IdWorker.getId());
        auditLog.setOperationType(operationType);
        auditLog.setOperationModule(operationModule);
        auditLog.setOperationDesc(operationDesc);
        auditLog.setOperator(operator);
        auditLog.setTargetId(targetId);
        auditLog.setTargetType(targetType);
        auditLog.setRequestParams(requestParams);
        auditLog.setResponseResult(responseResult);
        auditLog.setClientIp(clientIp);
        auditLog.setSuccess(success ? 1 : 0);
        auditLog.setErrorMsg(errorMsg);
        auditLog.setCostMs(costMs);
        auditLog.setOperationTime(LocalDateTime.now());
        auditLog.setCreatedTime(LocalDateTime.now());
        auditLog.setDeleted(0);
        auditLogMapper.insert(auditLog);
    }

    @Override
    public List<AuditLog> queryAuditLogs(Map<String, Object> params, int page, int size) {
        LambdaQueryWrapper<AuditLog> wrapper = buildQueryWrapper(params);
        wrapper.orderByDesc(AuditLog::getOperationTime);

        Page<AuditLog> pageResult = auditLogMapper.selectPage(
                new Page<>(page, size), wrapper);
        return pageResult.getRecords();
    }

    @Override
    public Long countAuditLogs(Map<String, Object> params) {
        LambdaQueryWrapper<AuditLog> wrapper = buildQueryWrapper(params);
        return auditLogMapper.selectCount(wrapper);
    }

    private LambdaQueryWrapper<AuditLog> buildQueryWrapper(Map<String, Object> params) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (params != null) {
            if (params.containsKey("operationType")) {
                wrapper.eq(AuditLog::getOperationType, params.get("operationType"));
            }
            if (params.containsKey("operationModule")) {
                wrapper.eq(AuditLog::getOperationModule, params.get("operationModule"));
            }
            if (params.containsKey("operator")) {
                wrapper.like(AuditLog::getOperator, params.get("operator"));
            }
            if (params.containsKey("targetId")) {
                wrapper.eq(AuditLog::getTargetId, params.get("targetId"));
            }
            if (params.containsKey("success")) {
                wrapper.eq(AuditLog::getSuccess, params.get("success"));
            }
            if (params.containsKey("startTime")) {
                wrapper.ge(AuditLog::getOperationTime, params.get("startTime"));
            }
            if (params.containsKey("endTime")) {
                wrapper.le(AuditLog::getOperationTime, params.get("endTime"));
            }
        }
        return wrapper;
    }
}
