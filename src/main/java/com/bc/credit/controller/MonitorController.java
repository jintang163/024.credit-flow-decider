package com.bc.credit.controller;

import com.bc.credit.common.Result;
import com.bc.credit.dto.*;
import com.bc.credit.service.MonitorService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Slf4j
@Api(tags = "流程监控面板")
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    @Autowired
    private MonitorService monitorService;

    @PostMapping("/process-instances")
    @ApiOperation("流程实例查询（按申请单号、身份证、状态）")
    public Result<Map<String, Object>> queryProcessInstances(
            @RequestBody ProcessInstanceQueryDTO queryDTO) {
        try {
            Map<String, Object> result = monitorService.queryProcessInstances(queryDTO);
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询流程实例失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/todo-tasks")
    @ApiOperation("待办任务列表（人工复核）")
    public Result<List<Map<String, Object>>> queryTodoTasks(
            @ApiParam("处理人") @RequestParam(required = false) String assignee,
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页条数") @RequestParam(defaultValue = "10") int size) {
        try {
            List<Map<String, Object>> tasks = monitorService.queryTodoTasks(assignee, page, size);
            return Result.success(tasks);
        } catch (Exception e) {
            log.error("查询待办任务失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/diagram/{processInstanceId}")
    @ApiOperation("流程图实时高亮当前节点")
    public Result<Map<String, Object>> getProcessDiagramWithHighlight(
            @ApiParam("流程实例ID") @PathVariable String processInstanceId) {
        try {
            Map<String, Object> result = monitorService.getProcessDiagramWithHighlight(processInstanceId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取流程图高亮信息失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/rule-hit-stats")
    @ApiOperation("规则命中统计面板（反欺诈规则命中排行）")
    public Result<List<Map<String, Object>>> getRuleHitStats(
            @ApiParam("开始日期(yyyy-MM-dd)") @RequestParam(required = false) String startDate,
            @ApiParam("结束日期(yyyy-MM-dd)") @RequestParam(required = false) String endDate,
            @ApiParam("规则组") @RequestParam(required = false) String ruleGroup) {
        try {
            List<Map<String, Object>> stats = monitorService.getRuleHitStats(startDate, endDate, ruleGroup);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("查询规则命中统计失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/credit-query-percentile")
    @ApiOperation("征信耗时百分位监控")
    public Result<CreditQueryPercentileDTO> getCreditQueryPercentile(
            @ApiParam("开始日期(yyyy-MM-dd)") @RequestParam(required = false) String startDate,
            @ApiParam("结束日期(yyyy-MM-dd)") @RequestParam(required = false) String endDate,
            @ApiParam("数据源类型") @RequestParam(required = false) String dataSource) {
        try {
            CreditQueryPercentileDTO result = monitorService.getCreditQueryPercentile(startDate, endDate, dataSource);
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询征信耗时百分位失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/limit-distribution")
    @ApiOperation("额度分布仪表盘")
    public Result<List<LimitDistributionDTO>> getLimitDistribution() {
        try {
            List<LimitDistributionDTO> result = monitorService.getLimitDistribution();
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询额度分布失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/workflow-metrics")
    @ApiOperation("流程引擎指标")
    public Result<WorkflowMetricsDTO> getWorkflowMetrics() {
        try {
            WorkflowMetricsDTO result = monitorService.getWorkflowMetrics();
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询流程引擎指标失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
}
