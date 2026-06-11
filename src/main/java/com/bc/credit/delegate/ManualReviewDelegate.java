package com.bc.credit.delegate;

import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.common.enums.ApplicationStatusEnum;
import com.bc.credit.dto.credit.WorkflowCallbackRequest;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.WorkflowCallbackService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("manualReviewDelegate")
public class ManualReviewDelegate implements JavaDelegate {

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private WorkflowCallbackService workflowCallbackService;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        Long applicationId = (Long) execution.getVariable(ProcessVariableConstants.APPLICATION_ID);
        String applicationNo = (String) execution.getVariable(ProcessVariableConstants.APPLICATION_NO);
        String currentActivityId = execution.getCurrentActivityId() != null
                ? execution.getCurrentActivityId() : "";

        log.info("[转人工Delegate] 执行, processInstanceId: {}, applicationId: {}, activityId: {}",
                processInstanceId, applicationId, currentActivityId);

        try {
            String defaultReason = buildDefaultReason(currentActivityId, execution);
            String manualReviewReason = execution.getVariable(
                    ProcessVariableConstants.MANUAL_REVIEW_REASON) != null
                    ? String.valueOf(execution.getVariable(
                            ProcessVariableConstants.MANUAL_REVIEW_REASON))
                    : defaultReason;

            String taskId = execution.getVariable(ProcessVariableConstants.CREDIT_QUERY_TASK_ID) != null
                    ? String.valueOf(execution.getVariable(
                            ProcessVariableConstants.CREDIT_QUERY_TASK_ID))
                    : (execution.getVariable(ProcessVariableConstants.SCORING_TASK_ID) != null
                    ? String.valueOf(execution.getVariable(
                            ProcessVariableConstants.SCORING_TASK_ID))
                    : null);

            String taskType = currentActivityId.contains("credit") ? "CREDIT_QUERY" : "CREDIT_SCORING";

            if (applicationId != null) {
                LoanApplication application = loanApplicationMapper.selectById(applicationId);
                if (application != null) {
                    application.setApplicationStatus(
                            ApplicationStatusEnum.MANUAL_REVIEW.getCode());
                    application.setUpdatedTime(LocalDateTime.now());
                    loanApplicationMapper.updateById(application);
                    log.info("[转人工Delegate] 更新申请表状态为MANUAL_REVIEW, applicationId: {}",
                            applicationId);
                }
            }

            Map<String, Object> variables = new HashMap<>();
            variables.put(ProcessVariableConstants.NEED_MANUAL_REVIEW, true);
            variables.put(ProcessVariableConstants.MANUAL_REVIEW_REASON, manualReviewReason);
            variables.put(ProcessVariableConstants.APPLICATION_STATUS,
                    ApplicationStatusEnum.MANUAL_REVIEW.getCode());
            variables.put(ProcessVariableConstants.APPLICATION_STATUS_DESC,
                    ApplicationStatusEnum.MANUAL_REVIEW.getDesc());
            variables.put(ProcessVariableConstants.ASYNC_TASK_STATUS,
                    ProcessVariableConstants.ASYNC_STATUS_MANUAL_REVIEW);
            variables.put(ProcessVariableConstants.ASYNC_CALLBACK_STATUS,
                    ProcessVariableConstants.CALLBACK_STATUS_FAILED);
            variables.put(ProcessVariableConstants.ASYNC_LAST_ERROR, manualReviewReason);

            if (currentActivityId.contains("credit")) {
                variables.put(ProcessVariableConstants.CREDIT_QUERY_ASYNC_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_MANUAL_REVIEW);
                variables.put(ProcessVariableConstants.CREDIT_QUERY_CALLBACK_STATUS,
                        ProcessVariableConstants.CALLBACK_STATUS_FAILED);
                variables.put(ProcessVariableConstants.CREDIT_QUERY_ERROR, manualReviewReason);
                variables.put(ProcessVariableConstants.CREDIT_QUERY_SUCCESS, false);
            } else if (currentActivityId.contains("scoring")
                    || taskType.equals("CREDIT_SCORING")) {
                variables.put(ProcessVariableConstants.SCORING_ASYNC_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_MANUAL_REVIEW);
                variables.put(ProcessVariableConstants.SCORING_CALLBACK_STATUS,
                        ProcessVariableConstants.CALLBACK_STATUS_FAILED);
                variables.put(ProcessVariableConstants.SCORING_ERROR, manualReviewReason);
                variables.put(ProcessVariableConstants.SCORE_PASS, false);
            }

            execution.setVariables(variables);

            WorkflowCallbackRequest callbackRequest = new WorkflowCallbackRequest();
            callbackRequest.setProcessInstanceId(processInstanceId);
            callbackRequest.setTaskId(taskId);
            callbackRequest.setTaskType(taskType);
            callbackRequest.setSuccess(false);
            callbackRequest.setErrorMsg(manualReviewReason);
            callbackRequest.setVariables(variables);

            try {
                workflowCallbackService.saveApprovalRecord(applicationId, applicationNo,
                        "SYSTEM_AUTO", "超时补偿超限自动转人工",
                        ApplicationStatusEnum.MANUAL_REVIEW.getCode(),
                        manualReviewReason);
            } catch (Exception e) {
                log.warn("[转人工Delegate] 创建审批记录失败，继续流程, applicationId: {}",
                        applicationId, e);
            }

            log.info("[转人工Delegate] 执行成功, applicationId: {}, reason: {}",
                    applicationId, manualReviewReason);

        } catch (Exception e) {
            log.error("[转人工Delegate] 执行失败, processInstanceId: {}, applicationId: {}",
                    processInstanceId, applicationId, e);
            execution.setVariable(ProcessVariableConstants.NEED_MANUAL_REVIEW, true);
            execution.setVariable(ProcessVariableConstants.MANUAL_REVIEW_REASON,
                    "系统异常转人工: " + e.getMessage());
            execution.setVariable(ProcessVariableConstants.APPLICATION_STATUS,
                    ApplicationStatusEnum.MANUAL_REVIEW.getCode());
        }
    }

    private String buildDefaultReason(String activityId, DelegateExecution execution) {
        Integer compCount = (Integer) execution.getVariable(
                ProcessVariableConstants.COMPENSATION_COUNT);
        Integer scoringCompCount = (Integer) execution.getVariable("scoringCompensationCount");

        StringBuilder sb = new StringBuilder();
        if (activityId.contains("credit")) {
            sb.append("征信查询超时补偿超限");
            if (compCount != null) {
                sb.append("(补偿").append(compCount).append("次)");
            }
        } else if (activityId.contains("scoring")) {
            sb.append("信用评分超时补偿超限");
            if (scoringCompCount != null) {
                sb.append("(补偿").append(scoringCompCount).append("次)");
            }
        } else {
            sb.append("异步处理超时自动转人工");
        }

        String lastError = execution.getVariable(ProcessVariableConstants.ASYNC_LAST_ERROR) != null
                ? String.valueOf(execution.getVariable(
                        ProcessVariableConstants.ASYNC_LAST_ERROR))
                : null;
        if (lastError != null && !lastError.isEmpty()) {
            sb.append(" | 最后错误: ").append(lastError.length() > 200
                    ? lastError.substring(0, 200) : lastError);
        }
        return sb.toString();
    }
}
