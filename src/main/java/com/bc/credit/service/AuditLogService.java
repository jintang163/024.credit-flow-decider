package com.bc.credit.service;

import com.bc.credit.entity.AuditLog;
import java.util.List;
import java.util.Map;

public interface AuditLogService {

    void log(AuditLog auditLog);

    void log(String operationType, String operationModule, String operationDesc,
             String operator, String targetId, String targetType,
             String requestParams, String responseResult,
             String clientIp, boolean success, String errorMsg, Long costMs);

    List<AuditLog> queryAuditLogs(Map<String, Object> params, int page, int size);

    Long countAuditLogs(Map<String, Object> params);
}
