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
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
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
        variables.put("manualReviewResult", result);
        variables.put("manualReviewOpinion", opinion);
        variables.put("manualReviewer", assignee);
        variables.put("manualReviewTime", LocalDateTime.now());

        Long applicationId = (Long) taskService.getVariable(taskId, "applicationId");

        saveApprovalRecord(applicationId, task.getProcessInstanceId(), taskId,
                task.getTaskDefinitionKey(), task.getName(), "人工复核",
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
        variables.put("finalApprovalResult", result);
        variables.put("finalApprovalOpinion", opinion);
        variables.put("finalApprover", assignee);
        variables.put("finalApprovalTime", LocalDateTime.now());

        Long applicationId = (Long) taskService.getVariable(taskId, "applicationId");

        saveApprovalRecord(applicationId, task.getProcessInstanceId(), taskId,
                task.getTaskDefinitionKey(), task.getName(), "终审",
                assignee, "PASS".equals(result) ? 0 : 1, opinion,
                approveAmount, approveTerm, interestRate);

        LoanApplication application = loanApplicationMapper.selectById(applicationId);
        if ("PASS".equals(result)) {
            application.setApplicationStatus(ApplicationStatusEnum.APPROVED.getCode());
            application.setApprovedAmount(approveAmount);
            application.setApprovedTerm(approveTerm);
            application.setInterestRate(interestRate);
        } else {
            application.setApplicationStatus(ApplicationStatusEnum.REJECTED.getCode());
            application.setRejectReason(opinion);
        }
        application.setApproveTime(LocalDateTime.now());
        application.setUpdatedTime(LocalDateTime.now());
        loanApplicationMapper.updateById(application);

        taskService.complete(taskId, variables);

        log.info("终审任务完成, taskId: {}, result: {}", taskId, result);
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
