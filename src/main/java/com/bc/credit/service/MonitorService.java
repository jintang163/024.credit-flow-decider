package com.bc.credit.service;

import com.bc.credit.dto.*;
import java.util.List;
import java.util.Map;

public interface MonitorService {

    Map<String, Object> queryProcessInstances(ProcessInstanceQueryDTO queryDTO);

    List<Map<String, Object>> queryTodoTasks(String assignee, int page, int size);

    Map<String, Object> getProcessDiagramWithHighlight(String processInstanceId);

    List<Map<String, Object>> getRuleHitStats(String startDate, String endDate, String ruleGroup);

    CreditQueryPercentileDTO getCreditQueryPercentile(String startDate, String endDate, String dataSource);

    List<LimitDistributionDTO> getLimitDistribution();

    WorkflowMetricsDTO getWorkflowMetrics();

    void retryCreditQuery(String processInstanceId, String operator, String clientIp);

    void skipNode(String processInstanceId, String targetNodeId, String reason, String operator, String clientIp);

    void modifyRuleTestResult(String processInstanceId, String ruleCode, String testData, String operator, String clientIp);
}
