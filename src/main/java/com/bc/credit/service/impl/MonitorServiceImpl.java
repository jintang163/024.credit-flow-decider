package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bc.credit.dto.*;
import com.bc.credit.entity.AuditLog;
import com.bc.credit.entity.FraudRuleHitStats;
import com.bc.credit.entity.LimitCalcResult;
import com.bc.credit.mapper.CreditApiCallLogMapper;
import com.bc.credit.mapper.FraudRuleHitStatsMapper;
import com.bc.credit.mapper.LimitCalcResultMapper;
import com.bc.credit.service.AuditLogService;
import com.bc.credit.service.MonitorService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.*;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MonitorServiceImpl implements MonitorService {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private FraudRuleHitStatsMapper fraudRuleHitStatsMapper;

    @Autowired
    private CreditApiCallLogMapper creditApiCallLogMapper;

    @Autowired
    private LimitCalcResultMapper limitCalcResultMapper;

    @Autowired
    private AuditLogService auditLogService;

    @Override
    public Map<String, Object> queryProcessInstances(ProcessInstanceQueryDTO queryDTO) {
        List<Map<String, Object>> records = new ArrayList<>();
        long total;

        if ("completed".equalsIgnoreCase(queryDTO.getStatus())
                || "terminated".equalsIgnoreCase(queryDTO.getStatus())) {
            org.flowable.engine.history.HistoricProcessInstanceQuery histQuery = historyService
                    .createHistoricProcessInstanceQuery()
                    .finished();

            if (queryDTO.getApplicationNo() != null && !queryDTO.getApplicationNo().isEmpty()) {
                histQuery.processInstanceBusinessKey(queryDTO.getApplicationNo());
            }
            if (queryDTO.getProcessKey() != null && !queryDTO.getProcessKey().isEmpty()) {
                histQuery.processDefinitionKey(queryDTO.getProcessKey());
            }

            total = histQuery.count();
            List<HistoricProcessInstance> instances = histQuery
                    .orderByProcessInstanceEndTime().desc()
                    .listPage((queryDTO.getPage() - 1) * queryDTO.getSize(), queryDTO.getSize());

            for (HistoricProcessInstance instance : instances) {
                Map<String, Object> map = buildHistoricInstanceMap(instance);
                records.add(map);
            }
        } else {
            org.flowable.engine.runtime.ProcessInstanceQuery runtimeQuery = runtimeService
                    .createProcessInstanceQuery();

            if (queryDTO.getApplicationNo() != null && !queryDTO.getApplicationNo().isEmpty()) {
                runtimeQuery.processInstanceBusinessKey(queryDTO.getApplicationNo());
            }
            if (queryDTO.getIdCard() != null && !queryDTO.getIdCard().isEmpty()) {
                runtimeQuery.variableValueEquals("idCard", queryDTO.getIdCard());
            }
            if (queryDTO.getProcessKey() != null && !queryDTO.getProcessKey().isEmpty()) {
                runtimeQuery.processDefinitionKey(queryDTO.getProcessKey());
            }
            if ("running".equalsIgnoreCase(queryDTO.getStatus())) {
                runtimeQuery.active();
            } else if ("suspended".equalsIgnoreCase(queryDTO.getStatus())) {
                runtimeQuery.suspended();
            }

            total = runtimeQuery.count();
            List<ProcessInstance> instances = runtimeQuery
                    .orderByStartTime().desc()
                    .listPage((queryDTO.getPage() - 1) * queryDTO.getSize(), queryDTO.getSize());

            for (ProcessInstance instance : instances) {
                Map<String, Object> map = buildRuntimeInstanceMap(instance);
                records.add(map);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", queryDTO.getPage());
        result.put("size", queryDTO.getSize());
        return result;
    }

    @Override
    public List<Map<String, Object>> queryTodoTasks(String assignee, int page, int size) {
        org.flowable.task.api.TaskQuery taskQuery = taskService.createTaskQuery();

        if (assignee != null && !assignee.isEmpty()) {
            taskQuery.taskAssignee(assignee);
        }
        taskQuery.taskUnassigned();

        List<Task> tasks = taskQuery.orderByTaskCreateTime().desc()
                .listPage((page - 1) * size, size);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Task task : tasks) {
            Map<String, Object> map = new HashMap<>();
            map.put("taskId", task.getId());
            map.put("taskName", task.getName());
            map.put("taskKey", task.getTaskDefinitionKey());
            map.put("processInstanceId", task.getProcessInstanceId());
            map.put("assignee", task.getAssignee());
            map.put("owner", task.getOwner());
            map.put("createTime", task.getCreateTime());
            map.put("dueDate", task.getDueDate());
            map.put("priority", task.getPriority());

            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();
            if (pi != null) {
                map.put("businessKey", pi.getBusinessKey());
                map.put("processDefinitionName", getProcessDefinitionName(pi.getProcessDefinitionId()));
            }
            result.add(map);
        }
        return result;
    }

    @Override
    public Map<String, Object> getProcessDiagramWithHighlight(String processInstanceId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        Map<String, Object> result = new HashMap<>();

        if (processInstance != null) {
            List<String> activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);
            result.put("processInstanceId", processInstanceId);
            result.put("processDefinitionId", processInstance.getProcessDefinitionId());
            result.put("activeActivityIds", activeActivityIds);
            result.put("businessKey", processInstance.getBusinessKey());
            result.put("status", "running");

            org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService
                    .getBpmnModel(processInstance.getProcessDefinitionId());
            result.put("bpmnModel", bpmnModel);
        } else {
            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (historicInstance != null) {
                result.put("processInstanceId", processInstanceId);
                result.put("processDefinitionId", historicInstance.getProcessDefinitionId());
                result.put("activeActivityIds", Collections.emptyList());
                result.put("businessKey", historicInstance.getBusinessKey());
                result.put("status", "completed");
                result.put("endTime", historicInstance.getEndTime());

                org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService
                        .getBpmnModel(historicInstance.getProcessDefinitionId());
                result.put("bpmnModel", bpmnModel);
            }
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getRuleHitStats(String startDate, String endDate, String ruleGroup) {
        LambdaQueryWrapper<FraudRuleHitStats> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(FraudRuleHitStats::getStatsDate, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(FraudRuleHitStats::getStatsDate, endDate);
        }
        if (ruleGroup != null && !ruleGroup.isEmpty()) {
            wrapper.eq(FraudRuleHitStats::getRuleGroup, ruleGroup);
        }
        wrapper.orderByDesc(FraudRuleHitStats::getHitCount);

        List<FraudRuleHitStats> stats = fraudRuleHitStatsMapper.selectList(wrapper);

        return stats.stream().map(stat -> {
            Map<String, Object> map = new HashMap<>();
            map.put("ruleCode", stat.getRuleCode());
            map.put("ruleName", stat.getRuleName());
            map.put("ruleGroup", stat.getRuleGroup());
            map.put("statsDate", stat.getStatsDate());
            map.put("executeCount", stat.getExecuteCount());
            map.put("hitCount", stat.getHitCount());
            map.put("hitRate", stat.getHitRate());
            map.put("avgScore", stat.getAvgScore());
            map.put("rejectCount", stat.getRejectCount());
            map.put("alertCount", stat.getAlertCount());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public CreditQueryPercentileDTO getCreditQueryPercentile(String startDate, String endDate, String dataSource) {
        CreditQueryPercentileDTO dto = new CreditQueryPercentileDTO();
        dto.setDataSource(dataSource);
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);

        try {
            QueryWrapper<Map<String, Object>> wrapper = new QueryWrapper<>();
            wrapper.select("cost_ms", "call_time");
            if (startDate != null && !startDate.isEmpty()) {
                wrapper.ge("call_time", startDate + " 00:00:00");
            }
            if (endDate != null && !endDate.isEmpty()) {
                wrapper.le("call_time", endDate + " 23:59:59");
            }
            if (dataSource != null && !dataSource.isEmpty()) {
                wrapper.eq("data_source", dataSource);
            }
            wrapper.eq("success", 1);
            wrapper.isNotNull("cost_ms");
            wrapper.orderByAsc("cost_ms");

            List<Map<String, Object>> logs = creditApiCallLogMapper.selectMaps(wrapper);

            if (logs != null && !logs.isEmpty()) {
                List<Long> costs = logs.stream()
                        .map(m -> ((Number) m.get("cost_ms")).longValue())
                        .sorted()
                        .collect(Collectors.toList());

                dto.setTotalCount((long) costs.size());
                dto.setAvgCostMs(BigDecimal.valueOf(costs.stream().mapToLong(Long::longValue).average().orElse(0))
                        .setScale(2, RoundingMode.HALF_UP));
                dto.setP50(calcPercentile(costs, 50));
                dto.setP90(calcPercentile(costs, 90));
                dto.setP95(calcPercentile(costs, 95));
                dto.setP99(calcPercentile(costs, 99));
            } else {
                dto.setTotalCount(0L);
                dto.setAvgCostMs(BigDecimal.ZERO);
                dto.setP50(BigDecimal.ZERO);
                dto.setP90(BigDecimal.ZERO);
                dto.setP95(BigDecimal.ZERO);
                dto.setP99(BigDecimal.ZERO);
            }
        } catch (Exception e) {
            log.error("计算征信耗时百分位失败", e);
            dto.setTotalCount(0L);
        }

        return dto;
    }

    @Override
    public List<LimitDistributionDTO> getLimitDistribution() {
        String[][] ranges = {
                {"0-1万", "0", "10000"},
                {"1万-5万", "10000", "50000"},
                {"5万-10万", "50000", "100000"},
                {"10万-20万", "100000", "200000"},
                {"20万-50万", "200000", "500000"},
                {"50万以上", "500000", null}
        };

        List<LimitDistributionDTO> result = new ArrayList<>();
        Long totalCount = limitCalcResultMapper.selectCount(
                new LambdaQueryWrapper<LimitCalcResult>());

        for (String[] range : ranges) {
            LambdaQueryWrapper<LimitCalcResult> wrapper = new LambdaQueryWrapper<>();
            wrapper.ge(LimitCalcResult::getCreditLimit, new BigDecimal(range[1]));
            if (range[2] != null) {
                wrapper.lt(LimitCalcResult::getCreditLimit, new BigDecimal(range[2]));
            }

            Long count = limitCalcResultMapper.selectCount(wrapper);

            LimitDistributionDTO dto = new LimitDistributionDTO();
            dto.setRange(range[0]);
            dto.setCount(count);
            dto.setPercentage(totalCount > 0
                    ? BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);

            QueryWrapper<LimitCalcResult> avgWrapper = new QueryWrapper<>();
            avgWrapper.select("AVG(credit_limit) as credit_limit");
            avgWrapper.ge("credit_limit", range[1]);
            if (range[2] != null) {
                avgWrapper.lt("credit_limit", range[2]);
            }
            List<Map<String, Object>> avgResult = limitCalcResultMapper.selectMaps(avgWrapper);
            if (avgResult != null && !avgResult.isEmpty() && avgResult.get(0).get("credit_limit") != null) {
                dto.setAvgAmount(new BigDecimal(avgResult.get(0).get("credit_limit").toString())
                        .setScale(2, RoundingMode.HALF_UP));
            } else {
                dto.setAvgAmount(BigDecimal.ZERO);
            }

            result.add(dto);
        }

        return result;
    }

    @Override
    public WorkflowMetricsDTO getWorkflowMetrics() {
        WorkflowMetricsDTO dto = new WorkflowMetricsDTO();

        Long totalInstances = historyService.createHistoricProcessInstanceQuery().count();
        Long runningInstances = runtimeService.createProcessInstanceQuery().active().count();
        Long completedInstances = historyService.createHistoricProcessInstanceQuery().finished().count();
        Long suspendedInstances = runtimeService.createProcessInstanceQuery().suspended().count();

        dto.setTotalInstances(totalInstances);
        dto.setRunningInstances(runningInstances);
        dto.setCompletedInstances(completedInstances);
        dto.setSuspendedInstances(suspendedInstances);

        if (totalInstances > 0) {
            dto.setCompletionRate(BigDecimal.valueOf(completedInstances)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalInstances), 2, RoundingMode.HALF_UP));
        } else {
            dto.setCompletionRate(BigDecimal.ZERO);
        }

        List<HistoricProcessInstance> finishedInstances = historyService
                .createHistoricProcessInstanceQuery()
                .finished()
                .list();

        if (!finishedInstances.isEmpty()) {
            long totalDuration = 0;
            for (HistoricProcessInstance instance : finishedInstances) {
                if (instance.getStartTime() != null && instance.getEndTime() != null) {
                    totalDuration += instance.getEndTime().getTime() - instance.getStartTime().getTime();
                }
            }
            dto.setAvgDurationMs(BigDecimal.valueOf(totalDuration)
                    .divide(BigDecimal.valueOf(finishedInstances.size()), 0, RoundingMode.HALF_UP));
        } else {
            dto.setAvgDurationMs(BigDecimal.ZERO);
        }

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        dto.setTodayStarted(historyService.createHistoricProcessInstanceQuery()
                .startedAfter(todayStart).count());
        dto.setTodayCompleted(historyService.createHistoricProcessInstanceQuery()
                .finishedAfter(todayStart).count());

        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryCreditQuery(String processInstanceId, String operator, String clientIp) {
        log.info("手动重试征信查询, processInstanceId: {}, operator: {}", processInstanceId, operator);
        long start = System.currentTimeMillis();

        try {
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            variables.put("creditQueryRetry", true);
            runtimeService.setVariables(processInstanceId, variables);

            auditLogService.log("RETRY_CREDIT", "MONITOR", "手动重试征信查询",
                    operator, processInstanceId, "PROCESS_INSTANCE",
                    JSON.toJSONString(Map.of("processInstanceId", processInstanceId)),
                    "重试征信查询已触发", clientIp, true, null,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("手动重试征信查询失败", e);
            auditLogService.log("RETRY_CREDIT", "MONITOR", "手动重试征信查询失败",
                    operator, processInstanceId, "PROCESS_INSTANCE",
                    JSON.toJSONString(Map.of("processInstanceId", processInstanceId)),
                    null, clientIp, false, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw new RuntimeException("重试征信查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void skipNode(String processInstanceId, String targetNodeId, String reason,
                         String operator, String clientIp) {
        log.info("手动跳过节点, processInstanceId: {}, targetNodeId: {}, operator: {}",
                processInstanceId, targetNodeId, operator);
        long start = System.currentTimeMillis();

        try {
            List<Task> tasks = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .list();

            if (tasks.isEmpty()) {
                throw new RuntimeException("当前没有待办任务可跳过");
            }

            Task currentTask = tasks.get(0);
            Map<String, Object> variables = new HashMap<>();
            variables.put("skipNode", true);
            variables.put("skipReason", reason);
            variables.put("skippedBy", operator);
            variables.put("skipTargetNode", targetNodeId);

            taskService.complete(currentTask.getId(), variables);

            auditLogService.log("SKIP_NODE", "MONITOR", "手动跳过节点",
                    operator, processInstanceId, "PROCESS_INSTANCE",
                    JSON.toJSONString(Map.of(
                            "processInstanceId", processInstanceId,
                            "currentTaskId", currentTask.getId(),
                            "currentTaskName", currentTask.getName(),
                            "targetNodeId", targetNodeId,
                            "reason", reason)),
                    "节点跳过成功", clientIp, true, null,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("手动跳过节点失败", e);
            auditLogService.log("SKIP_NODE", "MONITOR", "手动跳过节点失败",
                    operator, processInstanceId, "PROCESS_INSTANCE",
                    JSON.toJSONString(Map.of(
                            "processInstanceId", processInstanceId,
                            "targetNodeId", targetNodeId,
                            "reason", reason)),
                    null, clientIp, false, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw new RuntimeException("跳过节点失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modifyRuleTestResult(String processInstanceId, String ruleCode, String testData,
                                     String operator, String clientIp) {
        log.info("修改规则测试结果, processInstanceId: {}, ruleCode: {}, operator: {}",
                processInstanceId, ruleCode, operator);
        long start = System.currentTimeMillis();

        try {
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            variables.put("ruleTestOverride", true);
            variables.put("ruleTestOverrideCode", ruleCode);
            variables.put("ruleTestOverrideData", testData);
            variables.put("ruleTestOverrideBy", operator);
            runtimeService.setVariables(processInstanceId, variables);

            auditLogService.log("MODIFY_RULE_TEST", "MONITOR", "修改规则测试结果",
                    operator, processInstanceId, "PROCESS_INSTANCE",
                    JSON.toJSONString(Map.of(
                            "processInstanceId", processInstanceId,
                            "ruleCode", ruleCode,
                            "testData", testData)),
                    "规则测试结果已修改", clientIp, true, null,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("修改规则测试结果失败", e);
            auditLogService.log("MODIFY_RULE_TEST", "MONITOR", "修改规则测试结果失败",
                    operator, processInstanceId, "PROCESS_INSTANCE",
                    JSON.toJSONString(Map.of(
                            "processInstanceId", processInstanceId,
                            "ruleCode", ruleCode,
                            "testData", testData)),
                    null, clientIp, false, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw new RuntimeException("修改规则测试结果失败: " + e.getMessage(), e);
        }
    }

    private BigDecimal calcPercentile(List<Long> sortedData, int percentile) {
        if (sortedData.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double index = (percentile / 100.0) * (sortedData.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return BigDecimal.valueOf(sortedData.get(lower)).setScale(2, RoundingMode.HALF_UP);
        }
        double value = sortedData.get(lower) + (index - lower) * (sortedData.get(upper) - sortedData.get(lower));
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> buildRuntimeInstanceMap(ProcessInstance instance) {
        Map<String, Object> map = new HashMap<>();
        map.put("processInstanceId", instance.getId());
        map.put("processDefinitionId", instance.getProcessDefinitionId());
        map.put("processDefinitionName", getProcessDefinitionName(instance.getProcessDefinitionId()));
        map.put("businessKey", instance.getBusinessKey());
        map.put("startTime", instance.getStartTime());
        map.put("currentActivityId", instance.getActivityId());
        map.put("status", instance.isSuspended() ? "suspended" : "running");

        List<String> activeActivityIds = runtimeService.getActiveActivityIds(instance.getId());
        map.put("activeActivityIds", activeActivityIds);

        Map<String, Object> variables = runtimeService.getVariables(instance.getId());
        map.put("variables", variables);
        return map;
    }

    private Map<String, Object> buildHistoricInstanceMap(HistoricProcessInstance instance) {
        Map<String, Object> map = new HashMap<>();
        map.put("processInstanceId", instance.getId());
        map.put("processDefinitionId", instance.getProcessDefinitionId());
        map.put("processDefinitionName", getProcessDefinitionName(instance.getProcessDefinitionId()));
        map.put("businessKey", instance.getBusinessKey());
        map.put("startTime", instance.getStartTime());
        map.put("endTime", instance.getEndTime());
        map.put("durationInMillis", instance.getDurationInMillis());
        map.put("deleteReason", instance.getDeleteReason());
        map.put("status", instance.getEndTime() != null ? "completed" : "running");
        return map;
    }

    private String getProcessDefinitionName(String processDefinitionId) {
        try {
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId).singleResult();
            return pd != null ? pd.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
