package com.bc.credit.service.impl;

import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.common.enums.ApplicationStatusEnum;
import com.bc.credit.common.enums.AsyncTaskStatus;
import com.bc.credit.dto.credit.WorkflowCallbackRequest;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.AsyncTaskService;
import com.bc.credit.service.LoanApplicationService;
import com.bc.credit.service.WorkflowCallbackService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WorkflowCallbackServiceImpl implements WorkflowCallbackService {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private AsyncTaskService asyncTaskService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private LoanApplicationService loanApplicationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean triggerCallback(WorkflowCallbackRequest request) {
        log.info("[流程回调] 触发回调, processInstanceId: {}, signalName: {}, messageName: {}, taskType: {}",
                request.getProcessInstanceId(), request.getSignalName(),
                request.getMessageName(), request.getTaskType());

        String processInstanceId = request.getProcessInstanceId();
        if (!isProcessActive(processInstanceId)) {
            log.warn("[流程回调] 流程实例不存在或已结束, processInstanceId: {}", processInstanceId);
            return false;
        }

        Map<String, Object> variables = request.getVariables() != null ?
                new HashMap<>(request.getVariables()) : new HashMap<>();

        variables.put(ProcessVariableConstants.ASYNC_CALLBACK_STATUS,
                ProcessVariableConstants.CALLBACK_STATUS_TRIGGERED);
        variables.put(ProcessVariableConstants.ASYNC_LAST_ERROR, request.getErrorMsg());

        boolean triggered = false;

        try {
            if (request.getReceiveTaskId() != null && !request.getReceiveTaskId().isEmpty()) {
                triggered = triggerReceiveTask(processInstanceId, request.getReceiveTaskId(), variables);
                if (!triggered && request.getSignalName() != null && !request.getSignalName().isEmpty()) {
                    triggered = signalProcessInstance(processInstanceId, request.getSignalName(), variables);
                }
                if (!triggered && request.getMessageName() != null && !request.getMessageName().isEmpty()) {
                    triggered = messageEventReceived(processInstanceId, request.getMessageName(), variables);
                }
            } else if (request.getSignalName() != null && !request.getSignalName().isEmpty()) {
                triggered = signalProcessInstance(processInstanceId, request.getSignalName(), variables);
                if (!triggered && request.getMessageName() != null && !request.getMessageName().isEmpty()) {
                    triggered = messageEventReceived(processInstanceId, request.getMessageName(), variables);
                }
            } else if (request.getMessageName() != null && !request.getMessageName().isEmpty()) {
                triggered = messageEventReceived(processInstanceId, request.getMessageName(), variables);
            }

            if (!triggered) {
                log.warn("[流程回调] 三种触发方式均未生效，仅设置变量, processInstanceId: {}", processInstanceId);
                setProcessVariables(processInstanceId, variables);
            }

            if (request.isSuccess()) {
                log.info("[流程回调] 回调成功, processInstanceId: {}, taskType: {}, triggered: {}",
                        processInstanceId, request.getTaskType(), triggered);

                if (request.getTaskId() != null) {
                    Map<String, Object> cbVars = new HashMap<>(variables);
                    cbVars.put(ProcessVariableConstants.ASYNC_CALLBACK_STATUS,
                            ProcessVariableConstants.CALLBACK_STATUS_COMPLETED);
                    asyncTaskService.updateTaskSuccess(request.getTaskId(), null, null,
                            System.currentTimeMillis() - (request.getCallbackTime() != null ?
                                    request.getCallbackTime() : System.currentTimeMillis()),
                            ProcessVariableConstants.CALLBACK_STATUS_COMPLETED, cbVars);
                }
            }

            return true;

        } catch (Exception e) {
            log.error("[流程回调] 回调异常, processInstanceId: {}", processInstanceId, e);
            if (request.getTaskId() != null) {
                asyncTaskService.incrementRetry(request.getTaskId(),
                        AsyncTaskStatus.FAILED, "回调流程失败: " + e.getMessage());
            }
            throw new RuntimeException("流程回调失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean signalProcessInstance(String processInstanceId, String signalName,
                                          Map<String, Object> variables) {
        log.info("[流程回调] Signal触发, processInstanceId: {}, signal: {}", processInstanceId, signalName);

        List<Execution> executions = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .signalEventSubscriptionName(signalName)
                .list();

        if (executions == null || executions.isEmpty()) {
            log.warn("[流程回调] 未找到signal订阅, processInstanceId: {}, signal: {}",
                    processInstanceId, signalName);
            return false;
        }

        for (Execution execution : executions) {
            log.info("[流程回调] 触发signal, executionId: {}, signal: {}", execution.getId(), signalName);
            if (variables != null && !variables.isEmpty()) {
                runtimeService.signalEventReceived(signalName, execution.getId(), variables);
            } else {
                runtimeService.signalEventReceived(signalName, execution.getId());
            }
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean messageEventReceived(String processInstanceId, String messageName,
                                         Map<String, Object> variables) {
        log.info("[流程回调] Message触发, processInstanceId: {}, message: {}",
                processInstanceId, messageName);

        List<Execution> executions = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .messageEventSubscriptionName(messageName)
                .list();

        if (executions == null || executions.isEmpty()) {
            log.warn("[流程回调] 未找到message订阅, processInstanceId: {}, message: {}",
                    processInstanceId, messageName);
            return false;
        }

        for (Execution execution : executions) {
            log.info("[流程回调] 触发message, executionId: {}, message: {}", execution.getId(), messageName);
            if (variables != null && !variables.isEmpty()) {
                runtimeService.messageEventReceived(messageName, execution.getId(), variables);
            } else {
                runtimeService.messageEventReceived(messageName, execution.getId());
            }
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean triggerReceiveTask(String processInstanceId, String receiveTaskId,
                                       Map<String, Object> variables) {
        log.info("[流程回调] ReceiveTask触发, processInstanceId: {}, receiveTaskId: {}",
                processInstanceId, receiveTaskId);

        List<Execution> executions = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .activityId(receiveTaskId)
                .list();

        if (executions == null || executions.isEmpty()) {
            log.warn("[流程回调] 未找到ReceiveTask, processInstanceId: {}, activityId: {}",
                    processInstanceId, receiveTaskId);
            return false;
        }

        for (Execution execution : executions) {
            log.info("[流程回调] 触发ReceiveTask, executionId: {}", execution.getId());
            if (variables != null && !variables.isEmpty()) {
                runtimeService.trigger(execution.getId(), variables);
            } else {
                runtimeService.trigger(execution.getId());
            }
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setProcessVariables(String processInstanceId, Map<String, Object> variables) {
        log.debug("[流程回调] 设置流程变量, processInstanceId: {}, varSize: {}",
                processInstanceId, variables != null ? variables.size() : 0);
        if (variables != null && !variables.isEmpty()) {
            runtimeService.setVariables(processInstanceId, variables);
        }
    }

    @Override
    public boolean isProcessActive(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isEmpty()) {
            return false;
        }
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return pi != null;
    }

    @Override
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    @Override
    public String getCurrentActivityId(String processInstanceId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return pi != null ? pi.getActivityId() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean triggerCompensationRetry(String processInstanceId, String taskType,
                                             String signalName, Map<String, Object> variables) {
        log.info("[流程补偿] 触发补偿重试, processInstanceId: {}, taskType: {}, signal: {}",
                processInstanceId, taskType, signalName);

        if (!isProcessActive(processInstanceId)) {
            log.warn("[流程补偿] 流程已结束，跳过补偿, processInstanceId: {}", processInstanceId);
            return false;
        }

        Map<String, Object> compensationVars = variables != null ?
                new HashMap<>(variables) : new HashMap<>();
        compensationVars.put(ProcessVariableConstants.COMPENSATION_TRIGGERED, true);

        Integer compensationCount = (Integer) runtimeService.getVariable(
                processInstanceId, ProcessVariableConstants.COMPENSATION_COUNT);
        compensationCount = compensationCount == null ? 1 : compensationCount + 1;
        compensationVars.put(ProcessVariableConstants.COMPENSATION_COUNT, compensationCount);
        compensationVars.put(ProcessVariableConstants.LAST_COMPENSATION_TIME, LocalDateTime.now().toString());

        if (signalName != null && !signalName.isEmpty()) {
            return signalProcessInstance(processInstanceId, signalName, compensationVars);
        }
        setProcessVariables(processInstanceId, compensationVars);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean transferToManualReview(String processInstanceId, String taskType,
                                           String reason, String operator) {
        log.info("[流程补偿] 转人工复核, processInstanceId: {}, taskType: {}, reason: {}",
                processInstanceId, taskType, reason);

        if (!isProcessActive(processInstanceId)) {
            log.warn("[流程补偿] 流程已结束，跳过转人工, processInstanceId: {}", processInstanceId);
            return false;
        }

        Map<String, Object> processVars = runtimeService.getVariables(processInstanceId);
        Long applicationId = (Long) processVars.get(ProcessVariableConstants.APPLICATION_ID);

        if (applicationId != null) {
            LoanApplication app = loanApplicationMapper.selectById(applicationId);
            if (app != null) {
                app.setApplicationStatus(ApplicationStatusEnum.MANUAL_REVIEW.getCode());
                app.setUpdatedTime(LocalDateTime.now());
                app.setRemark("异步任务[" + taskType + "]超时/失败转人工: " + reason);
                loanApplicationMapper.updateById(app);
                log.info("[流程补偿] 申请状态已更新为人工复核, applicationId: {}", applicationId);

                try {
                    loanApplicationService.saveApprovalRecord(applicationId, processInstanceId,
                            null, "manual_transfer", "转人工复核",
                            "系统补偿", operator != null ? operator : "SYSTEM",
                            0, reason, app.getApprovedAmount(), app.getApprovedTerm(),
                            app.getInterestRate());
                } catch (Exception e) {
                    log.warn("[流程补偿] saveApprovalRecord失败，继续流程, applicationId: {}",
                            applicationId, e);
                }
            }
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put(ProcessVariableConstants.ASYNC_TASK_STATUS,
                ProcessVariableConstants.ASYNC_STATUS_MANUAL_REVIEW);
        vars.put(ProcessVariableConstants.ASYNC_LAST_ERROR, reason);
        vars.put(ProcessVariableConstants.NEED_MANUAL_REVIEW, true);
        vars.put(ProcessVariableConstants.MANUAL_REVIEW_REASON, reason);
        vars.put(ProcessVariableConstants.APPLICATION_STATUS,
                ApplicationStatusEnum.MANUAL_REVIEW.getCode());
        vars.put(ProcessVariableConstants.APPLICATION_STATUS_DESC,
                ApplicationStatusEnum.MANUAL_REVIEW.getDesc());

        if (ProcessVariableConstants.TASK_TYPE_CREDIT_QUERY.equals(taskType)) {
            vars.put(ProcessVariableConstants.CREDIT_QUERY_ASYNC_STATUS,
                    ProcessVariableConstants.ASYNC_STATUS_MANUAL_REVIEW);
            vars.put(ProcessVariableConstants.CREDIT_QUERY_SUCCESS, false);
            vars.put(ProcessVariableConstants.CREDIT_QUERY_CALLBACK_STATUS,
                    ProcessVariableConstants.CALLBACK_STATUS_FAILED);
            vars.put(ProcessVariableConstants.CREDIT_QUERY_ERROR, reason);
        } else if (ProcessVariableConstants.TASK_TYPE_SCORING.equals(taskType)) {
            vars.put(ProcessVariableConstants.SCORING_ASYNC_STATUS,
                    ProcessVariableConstants.ASYNC_STATUS_MANUAL_REVIEW);
            vars.put(ProcessVariableConstants.SCORE_PASS, false);
            vars.put(ProcessVariableConstants.SCORING_CALLBACK_STATUS,
                    ProcessVariableConstants.CALLBACK_STATUS_FAILED);
            vars.put(ProcessVariableConstants.SCORING_ERROR, reason);
        }

        boolean triggerEscaped = escapeFromReceiveTask(processInstanceId, vars);
        log.info("[流程补偿] 从ReceiveTask脱离结果: {}, processInstanceId: {}", triggerEscaped, processInstanceId);

        if (!triggerEscaped) {
            setProcessVariables(processInstanceId, vars);
        }

        List<Task> userTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey("manual_review_task")
                .list();
        log.info("[流程补偿] 当前人工复核任务数: {}, processInstanceId: {}", userTasks.size(), processInstanceId);

        return true;
    }

    private boolean escapeFromReceiveTask(String processInstanceId, Map<String, Object> variables) {
        String[] receiveTaskIds = {
                ProcessVariableConstants.RECEIVE_TASK_ID_CREDIT_QUERY,
                ProcessVariableConstants.RECEIVE_TASK_ID_SCORING
        };

        boolean anyTriggered = false;
        for (String receiveTaskId : receiveTaskIds) {
            List<Execution> executions = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId)
                    .activityId(receiveTaskId)
                    .list();

            if (executions != null && !executions.isEmpty()) {
                for (Execution execution : executions) {
                    try {
                        log.info("[流程补偿-脱离] 从ReceiveTask脱离, activityId: {}, executionId: {}",
                                receiveTaskId, execution.getId());
                        runtimeService.trigger(execution.getId(), variables);
                        anyTriggered = true;
                    } catch (Exception e) {
                        log.warn("[流程补偿-脱离] trigger失败, activityId: {}, executionId: {}",
                                receiveTaskId, execution.getId(), e);
                    }
                }
            }
        }

        return anyTriggered;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean transferToManualReview(WorkflowCallbackRequest request) {
        log.info("[流程回调-转人工] 基于WorkflowCallbackRequest转人工, " +
                        "processInstanceId: {}, taskType: {}, success: {}, receiveTaskId: {}",
                request.getProcessInstanceId(), request.getTaskType(), request.isSuccess(),
                request.getReceiveTaskId());

        String processInstanceId = request.getProcessInstanceId();
        if (!isProcessActive(processInstanceId)) {
            log.warn("[流程回调-转人工] 流程已结束, processInstanceId: {}", processInstanceId);
            return false;
        }

        Map<String, Object> mergedVars = new HashMap<>();
        if (request.getVariables() != null && !request.getVariables().isEmpty()) {
            mergedVars.putAll(request.getVariables());
        }

        mergedVars.putIfAbsent(ProcessVariableConstants.NEED_MANUAL_REVIEW, true);
        if (request.getErrorMsg() != null) {
            mergedVars.putIfAbsent(ProcessVariableConstants.MANUAL_REVIEW_REASON, request.getErrorMsg());
        }

        if (request.getReceiveTaskId() != null && !request.getReceiveTaskId().isEmpty()) {
            try {
                List<Execution> executions = runtimeService.createExecutionQuery()
                        .processInstanceId(processInstanceId)
                        .activityId(request.getReceiveTaskId())
                        .list();
                if (executions != null && !executions.isEmpty()) {
                    for (Execution execution : executions) {
                        log.info("[流程回调-转人工] 直接脱离receiveTask: {}, executionId: {}",
                                request.getReceiveTaskId(), execution.getId());
                        runtimeService.trigger(execution.getId(), mergedVars);
                    }
                }
            } catch (Exception e) {
                log.warn("[流程回调-转人工] 直接脱离receiveTask失败, receiveTaskId: {}",
                        request.getReceiveTaskId(), e);
            }
        }

        String reason = request.getErrorMsg() != null
                ? request.getErrorMsg() : "异步任务失败转人工";
        String operator = "SYSTEM_AUTO";
        return transferToManualReview(processInstanceId, request.getTaskType(), reason, operator);
    }

    @Override
    public void saveApprovalRecord(Long applicationId, String applicationNo,
                                    String operator, String action,
                                    Integer targetStatus, String remark) {
        log.info("[审批记录] 保存, applicationId: {}, applicationNo: {}, action: {}, " +
                        "targetStatus: {}, operator: {}",
                applicationId, applicationNo, action, targetStatus, operator);
        if (applicationId == null) {
            return;
        }
        try {
            LoanApplication app = loanApplicationMapper.selectById(applicationId);
            loanApplicationService.saveApprovalRecord(
                    applicationId,
                    app != null ? app.getProcessInstanceId() : null,
                    null,
                    action != null ? action : "STATUS_UPDATE",
                    targetStatus != null ? String.valueOf(targetStatus) : "",
                    remark != null ? remark : "",
                    operator != null ? operator : "SYSTEM",
                    0,
                    remark,
                    app != null ? app.getApprovedAmount() : null,
                    app != null ? app.getApprovedTerm() : null,
                    app != null ? app.getInterestRate() : null
            );
        } catch (Exception e) {
            log.warn("[审批记录] 保存失败, applicationId: {}, action: {}",
                    applicationId, action, e);
        }
    }
}
