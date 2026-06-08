package com.bc.credit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.ApplicationStatusEnum;
import com.bc.credit.entity.ApprovalRecord;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.ApprovalRecordMapper;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.ApprovalTaskService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
public class ApprovalTaskServiceImpl implements ApprovalTaskService {

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

    @Override
    public List<Map<String, Object>> getTodoTasks(String assignee, List<String> candidateGroups,
                                                   int page, int size) {
        log.info("查询待办任务, assignee: {}, candidateGroups: {}, page: {}, size: {}",
                assignee, candidateGroups, page, size);

        TaskQuery taskQuery = taskService.createTaskQuery();

        if (assignee != null && !assignee.isEmpty()) {
            taskQuery.taskAssignee(assignee);
        }

        if (candidateGroups != null && !candidateGroups.isEmpty()) {
            taskQuery.taskCandidateGroupIn(candidateGroups);
        }

        List<Task> tasks = taskQuery.orderByTaskCreateTime().desc()
                .listPage((page - 1) * size, size);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Task task : tasks) {
            Map<String, Object> taskMap = convertTaskToMap(task);
            result.add(taskMap);
        }

        return result;
    }

    @Override
    public Map<String, Object> getTaskDetail(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        Map<String, Object> result = convertTaskToMap(task);
        Map<String, Object> variables = taskService.getVariables(taskId);
        result.put("variables", variables);

        Long applicationId = (Long) variables.get("applicationId");
        if (applicationId != null) {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            result.put("application", application);
        }

        List<Map<String, Object>> returnableNodes = getReturnableNodes(task);
        result.put("returnableNodes", returnableNodes);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeManualReview(String taskId, String assignee, String result,
                                     String opinion, BigDecimal approveAmount,
                                     Integer approveTerm, BigDecimal interestRate) {
        log.info("完成人工复核任务, taskId: {}, assignee: {}, result: {}", taskId, assignee, result);

        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        claimTask(taskId, assignee);

        Map<String, Object> variables = new HashMap<>();
        variables.put("outcome", result);
        variables.put("comment", opinion);
        variables.put("manualReviewResult", result);
        variables.put("manualReviewOpinion", opinion);
        variables.put("manualReviewer", assignee);
        variables.put("manualReviewTime", LocalDateTime.now());

        Long applicationId = (Long) taskService.getVariable(taskId, "applicationId");

        String approveNode = "PASS".equals(result) ? "人工复核通过" :
                            "REJECT".equals(result) ? "人工复核拒绝" :
                            "RETURN".equals(result) ? "人工复核退回补件" : "人工复核";

        saveApprovalRecord(applicationId, task.getProcessInstanceId(), taskId,
                task.getTaskDefinitionKey(), task.getName(), approveNode,
                assignee, "PASS".equals(result) ? 0 : 1, opinion,
                approveAmount, approveTerm, interestRate);

        if ("REJECT".equals(result)) {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            application.setApplicationStatus(ApplicationStatusEnum.REJECTED.getCode());
            application.setRejectReason(opinion);
            application.setApproveTime(LocalDateTime.now());
            application.setUpdatedTime(LocalDateTime.now());
            loanApplicationMapper.updateById(application);
        }

        if ("RETURN".equals(result)) {
            handleReturnTask(task, applicationId, assignee, opinion, "rework_task");
        }

        taskService.complete(taskId, variables);

        log.info("人工复核任务完成, taskId: {}, result: {}", taskId, result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeFinalApproval(String taskId, String assignee, String result,
                                      String opinion, BigDecimal approveAmount,
                                      Integer approveTerm, BigDecimal interestRate) {
        log.info("完成终审任务, taskId: {}, assignee: {}, result: {}", taskId, assignee, result);

        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        claimTask(taskId, assignee);

        Map<String, Object> variables = new HashMap<>();
        variables.put("outcome", result);
        variables.put("comment", opinion);
        variables.put("finalApprovalResult", result);
        variables.put("finalApprovalOpinion", opinion);
        variables.put("finalApprover", assignee);
        variables.put("finalApprovalTime", LocalDateTime.now());

        Long applicationId = (Long) taskService.getVariable(taskId, "applicationId");

        String approveNode = "PASS".equals(result) ? "终审通过" :
                            "REJECT".equals(result) ? "终审拒绝" :
                            "RETURN".equals(result) ? "终审退回复核" : "终审";

        saveApprovalRecord(applicationId, task.getProcessInstanceId(), taskId,
                task.getTaskDefinitionKey(), task.getName(), approveNode,
                assignee, "PASS".equals(result) ? 0 : 1, opinion,
                approveAmount, approveTerm, interestRate);

        LoanApplication application = loanApplicationMapper.selectById(applicationId);
        if ("PASS".equals(result)) {
            application.setApplicationStatus(ApplicationStatusEnum.APPROVED.getCode());
            application.setApprovedAmount(approveAmount);
            application.setApprovedTerm(approveTerm);
            application.setInterestRate(interestRate);
        } else if ("REJECT".equals(result)) {
            application.setApplicationStatus(ApplicationStatusEnum.REJECTED.getCode());
            application.setRejectReason(opinion);
        }
        application.setApproveTime(LocalDateTime.now());
        application.setUpdatedTime(LocalDateTime.now());
        loanApplicationMapper.updateById(application);

        if ("RETURN".equals(result)) {
            handleReturnTask(task, applicationId, assignee, opinion, "manual_review_task");
        }

        taskService.complete(taskId, variables);

        log.info("终审任务完成, taskId: {}, result: {}", taskId, result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnTask(String taskId, String assignee, String targetNodeId, String opinion) {
        log.info("退回任务, taskId: {}, assignee: {}, targetNodeId: {}", taskId, assignee, targetNodeId);

        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        claimTask(taskId, assignee);

        Long applicationId = (Long) taskService.getVariable(taskId, "applicationId");

        String returnNodeName = getReturnNodeName(targetNodeId);
        saveApprovalRecord(applicationId, task.getProcessInstanceId(), taskId,
                task.getTaskDefinitionKey(), task.getName(), "退回至" + returnNodeName,
                assignee, 2, opinion, null, null, null);

        handleReturnTask(task, applicationId, assignee, opinion, targetNodeId);

        Map<String, Object> variables = new HashMap<>();
        variables.put("outcome", "RETURN");
        variables.put("comment", opinion);
        variables.put("returnTarget", targetNodeId);
        taskService.complete(taskId, variables);

        log.info("任务退回完成, taskId: {}, targetNodeId: {}", taskId, targetNodeId);
    }

    private void handleReturnTask(Task task, Long applicationId, String assignee,
                                   String opinion, String targetNodeId) {
        try {
            String processInstanceId = task.getProcessInstanceId();

            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application != null) {
                if ("rework_task".equals(targetNodeId)) {
                    application.setApplicationStatus(ApplicationStatusEnum.SUPPLEMENTING.getCode());
                } else if ("manual_review_task".equals(targetNodeId)) {
                    application.setApplicationStatus(ApplicationStatusEnum.REVIEWING.getCode());
                } else {
                    application.setApplicationStatus(ApplicationStatusEnum.RETURNED.getCode());
                }
                application.setReturnReason(opinion);
                application.setReturnCount(application.getReturnCount() != null
                        ? application.getReturnCount() + 1 : 1);
                application.setUpdatedTime(LocalDateTime.now());
                loanApplicationMapper.updateById(application);
            }

            log.info("流程退回处理完成, processInstanceId: {}, targetNode: {}, returnReason: {}",
                    processInstanceId, targetNodeId, opinion);

        } catch (Exception e) {
            log.error("处理流程退回失败", e);
            throw new RuntimeException("处理流程退回失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> getReturnableNodes(Task task) {
        List<Map<String, Object>> nodes = new ArrayList<>();

        String taskKey = task.getTaskDefinitionKey();

        if ("final_approval_task".equals(taskKey)) {
            Map<String, Object> node1 = new HashMap<>();
            node1.put("nodeId", "manual_review_task");
            node1.put("nodeName", "人工复核");
            nodes.add(node1);

            Map<String, Object> node2 = new HashMap<>();
            node2.put("nodeId", "rework_task");
            node2.put("nodeName", "补充资料");
            nodes.add(node2);
        }

        if ("manual_review_task".equals(taskKey)) {
            Map<String, Object> node = new HashMap<>();
            node.put("nodeId", "rework_task");
            node.put("nodeName", "补充资料");
            nodes.add(node);
        }

        return nodes;
    }

    private String getReturnNodeName(String nodeId) {
        switch (nodeId) {
            case "rework_task":
                return "补充资料";
            case "manual_review_task":
                return "人工复核";
            case "final_approval_task":
                return "终审";
            case "credit_scoring_task":
                return "信用评分";
            default:
                return nodeId;
        }
    }

    @Override
    public void completeTask(String taskId, String assignee, Map<String, Object> variables) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        claimTask(taskId, assignee);
        taskService.complete(taskId, variables);
    }

    @Override
    public void claimTask(String taskId, String assignee) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        if (task.getAssignee() == null || !task.getAssignee().equals(assignee)) {
            taskService.claim(taskId, assignee);
            log.info("任务签收成功, taskId: {}, assignee: {}", taskId, assignee);
        }
    }

    @Override
    public void unclaimTask(String taskId) {
        taskService.unclaim(taskId);
        log.info("任务取消签收, taskId: {}", taskId);
    }

    @Override
    public void delegateTask(String taskId, String assignee, String toUser) {
        claimTask(taskId, assignee);
        taskService.delegateTask(taskId, toUser);
        log.info("任务转办, taskId: {}, from: {}, to: {}", taskId, assignee, toUser);
    }

    @Override
    public List<Map<String, Object>> getApprovalHistory(String applicationNo) {
        LoanApplication application = loanApplicationMapper.selectOne(
                new LambdaQueryWrapper<LoanApplication>().eq(LoanApplication::getApplicationNo, applicationNo));

        if (application == null || application.getProcessInstanceId() == null) {
            return Collections.emptyList();
        }

        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(application.getProcessInstanceId())
                .orderByHistoricActivityInstanceEndTime().asc()
                .list();

        List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(application.getProcessInstanceId())
                .orderByHistoricTaskInstanceEndTime().asc()
                .list();

        Map<String, HistoricTaskInstance> taskMap = new HashMap<>();
        for (HistoricTaskInstance task : historicTasks) {
            taskMap.put(task.getTaskDefinitionKey(), task);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (HistoricActivityInstance activity : activities) {
            Map<String, Object> activityMap = new HashMap<>();
            activityMap.put("activityId", activity.getActivityId());
            activityMap.put("activityName", activity.getActivityName());
            activityMap.put("activityType", activity.getActivityType());
            activityMap.put("startTime", activity.getStartTime());
            activityMap.put("endTime", activity.getEndTime());
            activityMap.put("durationInMillis", activity.getDurationInMillis());

            HistoricTaskInstance task = taskMap.get(activity.getActivityId());
            if (task != null) {
                activityMap.put("assignee", task.getAssignee());
                activityMap.put("deleteReason", task.getDeleteReason());
            }

            result.add(activityMap);
        }

        return result;
    }

    private Map<String, Object> convertTaskToMap(Task task) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", task.getId());
        map.put("taskName", task.getName());
        map.put("taskKey", task.getTaskDefinitionKey());
        map.put("processInstanceId", task.getProcessInstanceId());
        map.put("processDefinitionId", task.getProcessDefinitionId());
        map.put("businessKey", task.getBusinessKey());
        map.put("createTime", task.getCreateTime());
        map.put("assignee", task.getAssignee());
        map.put("owner", task.getOwner());
        map.put("dueDate", task.getDueDate());
        map.put("priority", task.getPriority());
        map.put("category", task.getCategory());
        map.put("description", task.getDescription());

        Map<String, Object> variables = taskService.getVariables(task.getId());
        Long applicationId = (Long) variables.get("applicationId");
        if (applicationId != null) {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application != null) {
                map.put("applicationNo", application.getApplicationNo());
                map.put("customerName", application.getCustomerName());
                map.put("loanAmount", application.getLoanAmount());
                map.put("loanTerm", application.getLoanTerm());
                map.put("returnCount", application.getReturnCount());
                map.put("returnReason", application.getReturnReason());
            }
        }

        return map;
    }

    private void saveApprovalRecord(Long applicationId, String processInstanceId, String taskId,
                                     String taskKey, String taskName, String approveNode,
                                     String approver, Integer approveResult, String approveOpinion,
                                     BigDecimal approveAmount, Integer approveTerm, BigDecimal interestRate) {
        LoanApplication application = loanApplicationMapper.selectById(applicationId);
        if (application == null) {
            return;
        }

        HistoricTaskInstance historicTask = historyService.createHistoricTaskInstanceQuery()
                .taskId(taskId).singleResult();

        LocalDateTime startTime = null;
        LocalDateTime endTime = LocalDateTime.now();
        Long duration = null;

        if (historicTask != null && historicTask.getCreateTime() != null) {
            startTime = historicTask.getCreateTime().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
            duration = Duration.between(startTime, endTime).toMillis();
        }

        ApprovalRecord record = new ApprovalRecord();
        record.setId(IdWorker.getId());
        record.setApplicationId(applicationId);
        record.setApplicationNo(application.getApplicationNo());
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
        record.setStartTime(startTime);
        record.setEndTime(endTime);
        record.setDuration(duration);
        record.setCreatedTime(LocalDateTime.now());
        record.setDeleted(0);

        approvalRecordMapper.insert(record);
    }
}
