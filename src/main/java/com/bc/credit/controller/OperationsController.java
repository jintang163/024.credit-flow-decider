package com.bc.credit.controller;

import com.bc.credit.common.Result;
import com.bc.credit.dto.OperationRequestDTO;
import com.bc.credit.entity.AuditLog;
import com.bc.credit.service.AuditLogService;
import com.bc.credit.service.MonitorService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Api(tags = "运维操作管理")
@RestController
@RequestMapping("/api/ops")
public class OperationsController {

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private AuditLogService auditLogService;

    @PostMapping("/retry-credit")
    @ApiOperation("重试征信查询")
    public Result<Void> retryCreditQuery(
            @RequestBody OperationRequestDTO request,
            HttpServletRequest httpRequest) {
        try {
            String clientIp = getClientIp(httpRequest);
            monitorService.retryCreditQuery(request.getProcessInstanceId(),
                    request.getOperator(), clientIp);
            return Result.success("征信查询重试已触发", null);
        } catch (Exception e) {
            log.error("重试征信查询失败", e);
            return Result.error("操作失败: " + e.getMessage());
        }
    }

    @PostMapping("/skip-node")
    @ApiOperation("跳过节点")
    public Result<Void> skipNode(
            @RequestBody OperationRequestDTO request,
            HttpServletRequest httpRequest) {
        try {
            String clientIp = getClientIp(httpRequest);
            monitorService.skipNode(request.getProcessInstanceId(),
                    request.getTargetNodeId(), request.getReason(),
                    request.getOperator(), clientIp);
            return Result.success("节点跳过成功", null);
        } catch (Exception e) {
            log.error("跳过节点失败", e);
            return Result.error("操作失败: " + e.getMessage());
        }
    }

    @PostMapping("/modify-rule-test")
    @ApiOperation("修改规则测试结果")
    public Result<Void> modifyRuleTestResult(
            @RequestBody OperationRequestDTO request,
            HttpServletRequest httpRequest) {
        try {
            String clientIp = getClientIp(httpRequest);
            monitorService.modifyRuleTestResult(request.getProcessInstanceId(),
                    request.getTargetNodeId(), request.getTestData(),
                    request.getOperator(), clientIp);
            return Result.success("规则测试结果已修改", null);
        } catch (Exception e) {
            log.error("修改规则测试结果失败", e);
            return Result.error("操作失败: " + e.getMessage());
        }
    }

    @GetMapping("/audit-logs")
    @ApiOperation("查询审计日志")
    public Result<Map<String, Object>> queryAuditLogs(
            @ApiParam("操作类型") @RequestParam(required = false) String operationType,
            @ApiParam("操作模块") @RequestParam(required = false) String operationModule,
            @ApiParam("操作人") @RequestParam(required = false) String operator,
            @ApiParam("目标ID") @RequestParam(required = false) String targetId,
            @ApiParam("是否成功") @RequestParam(required = false) Integer success,
            @ApiParam("开始时间") @RequestParam(required = false) String startTime,
            @ApiParam("结束时间") @RequestParam(required = false) String endTime,
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页条数") @RequestParam(defaultValue = "10") int size) {
        try {
            Map<String, Object> params = new HashMap<>();
            if (operationType != null) params.put("operationType", operationType);
            if (operationModule != null) params.put("operationModule", operationModule);
            if (operator != null) params.put("operator", operator);
            if (targetId != null) params.put("targetId", targetId);
            if (success != null) params.put("success", success);
            if (startTime != null) params.put("startTime", startTime);
            if (endTime != null) params.put("endTime", endTime);

            List<AuditLog> records = auditLogService.queryAuditLogs(params, page, size);
            Long total = auditLogService.countAuditLogs(params);

            Map<String, Object> result = new HashMap<>();
            result.put("records", records);
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询审计日志失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
