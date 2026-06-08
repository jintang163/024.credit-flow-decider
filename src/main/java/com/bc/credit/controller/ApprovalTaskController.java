package com.bc.credit.controller;

import com.bc.credit.common.Result;
import com.bc.credit.service.ApprovalTaskService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Api(tags = "审批任务管理")
@RestController
@RequestMapping("/api/approval")
public class ApprovalTaskController {

    @Autowired
    private ApprovalTaskService approvalTaskService;

    @GetMapping("/todo")
    @ApiOperation("查询待办任务")
    public Result<List<Map<String, Object>>> getTodoTasks(
            @ApiParam("处理人") @RequestParam(required = false) String assignee,
            @ApiParam("候选角色组") @RequestParam(required = false) List<String> candidateGroups,
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页条数") @RequestParam(defaultValue = "10") int size) {
        try {
            List<Map<String, Object>> tasks = approvalTaskService.getTodoTasks(
                    assignee, candidateGroups, page, size);
            return Result.success(tasks);
        } catch (Exception e) {
            log.error("查询待办任务失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/task/{taskId}")
    @ApiOperation("查询任务详情")
    public Result<Map<String, Object>> getTaskDetail(
            @ApiParam("任务ID") @PathVariable String taskId) {
        try {
            Map<String, Object> taskDetail = approvalTaskService.getTaskDetail(taskId);
            return Result.success(taskDetail);
        } catch (Exception e) {
            log.error("查询任务详情失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/manual-review/complete")
    @ApiOperation("完成人工复核任务")
    public Result<Void> completeManualReview(
            @ApiParam("任务ID") @RequestParam String taskId,
            @ApiParam("处理人") @RequestParam String assignee,
            @ApiParam("审批结果: PASS-通过, REJECT-拒绝, RETURN-退回") @RequestParam String result,
            @ApiParam("审批意见") @RequestParam(required = false) String opinion,
            @ApiParam("审批金额") @RequestParam(required = false) BigDecimal approveAmount,
            @ApiParam("审批期限") @RequestParam(required = false) Integer approveTerm,
            @ApiParam("利率") @RequestParam(required = false) BigDecimal interestRate) {
        try {
            approvalTaskService.completeManualReview(taskId, assignee, result, opinion,
                    approveAmount, approveTerm, interestRate);
            return Result.success("审批完成", null);
        } catch (Exception e) {
            log.error("人工复核失败", e);
            return Result.error("审批失败: " + e.getMessage());
        }
    }

    @PostMapping("/final-approval/complete")
    @ApiOperation("完成终审任务")
    public Result<Void> completeFinalApproval(
            @ApiParam("任务ID") @RequestParam String taskId,
            @ApiParam("处理人") @RequestParam String assignee,
            @ApiParam("审批结果: PASS-通过, REJECT-拒绝, RETURN-退回") @RequestParam String result,
            @ApiParam("审批意见") @RequestParam(required = false) String opinion,
            @ApiParam("审批金额") @RequestParam(required = false) BigDecimal approveAmount,
            @ApiParam("审批期限") @RequestParam(required = false) Integer approveTerm,
            @ApiParam("利率") @RequestParam(required = false) BigDecimal interestRate) {
        try {
            approvalTaskService.completeFinalApproval(taskId, assignee, result, opinion,
                    approveAmount, approveTerm, interestRate);
            return Result.success("审批完成", null);
        } catch (Exception e) {
            log.error("终审失败", e);
            return Result.error("审批失败: " + e.getMessage());
        }
    }

    @PostMapping("/task/return")
    @ApiOperation("退回任务到指定节点")
    public Result<Void> returnTask(
            @ApiParam("任务ID") @RequestParam String taskId,
            @ApiParam("处理人") @RequestParam String assignee,
            @ApiParam("目标节点ID") @RequestParam String targetNodeId,
            @ApiParam("退回意见") @RequestParam(required = false) String opinion) {
        try {
            approvalTaskService.returnTask(taskId, assignee, targetNodeId, opinion);
            return Result.success("退回成功", null);
        } catch (Exception e) {
            log.error("退回任务失败", e);
            return Result.error("退回失败: " + e.getMessage());
        }
    }

    @PostMapping("/task/claim")
    @ApiOperation("签收任务")
    public Result<Void> claimTask(
            @ApiParam("任务ID") @RequestParam String taskId,
            @ApiParam("处理人") @RequestParam String assignee) {
        try {
            approvalTaskService.claimTask(taskId, assignee);
            return Result.success("签收成功", null);
        } catch (Exception e) {
            log.error("签收任务失败", e);
            return Result.error("签收失败: " + e.getMessage());
        }
    }

    @PostMapping("/task/unclaim")
    @ApiOperation("取消签收任务")
    public Result<Void> unclaimTask(
            @ApiParam("任务ID") @RequestParam String taskId) {
        try {
            approvalTaskService.unclaimTask(taskId);
            return Result.success("取消签收成功", null);
        } catch (Exception e) {
            log.error("取消签收任务失败", e);
            return Result.error("取消签收失败: " + e.getMessage());
        }
    }

    @PostMapping("/task/delegate")
    @ApiOperation("转办任务")
    public Result<Void> delegateTask(
            @ApiParam("任务ID") @RequestParam String taskId,
            @ApiParam("当前处理人") @RequestParam String assignee,
            @ApiParam("转办目标人") @RequestParam String toUser) {
        try {
            approvalTaskService.delegateTask(taskId, assignee, toUser);
            return Result.success("转办成功", null);
        } catch (Exception e) {
            log.error("转办任务失败", e);
            return Result.error("转办失败: " + e.getMessage());
        }
    }

    @GetMapping("/history/{applicationNo}")
    @ApiOperation("查询审批历史")
    public Result<List<Map<String, Object>>> getApprovalHistory(
            @ApiParam("申请编号") @PathVariable String applicationNo) {
        try {
            List<Map<String, Object>> history = approvalTaskService.getApprovalHistory(applicationNo);
            return Result.success(history);
        } catch (Exception e) {
            log.error("查询审批历史失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
}
