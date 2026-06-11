package com.bc.credit.delegate;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.common.enums.ApplicationStatusEnum;
import com.bc.credit.common.enums.AsyncTaskStatus;
import com.bc.credit.common.enums.AsyncTaskType;
import com.bc.credit.dto.credit.ScoringAsyncMessage;
import com.bc.credit.dto.credit.WorkflowCallbackRequest;
import com.bc.credit.entity.AsyncTask;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.integration.mq.AsyncTaskProducer;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.AsyncTaskService;
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
@Component("compensationDelegate")
public class CompensationDelegate implements JavaDelegate {

    @Autowired
    private AsyncTaskService asyncTaskService;

    @Autowired
    private AsyncTaskProducer asyncTaskProducer;

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

        log.info("[超时补偿] 执行补偿, processInstanceId: {}, applicationId: {}, activityId: {}",
                processInstanceId, applicationId, currentActivityId);

        try {
            Integer compensationCount = (Integer) execution.getVariable(
                    ProcessVariableConstants.COMPENSATION_COUNT);
            int newCount = (compensationCount == null ? 0 : compensationCount) + 1;

            String creditTaskId = (String) execution.getVariable(
                    ProcessVariableConstants.CREDIT_QUERY_TASK_ID);
            String scoringTaskId = (String) execution.getVariable(
                    ProcessVariableConstants.SCORING_TASK_ID);

            AsyncTaskType taskType = currentActivityId.contains("credit") || creditTaskId != null
                    ? AsyncTaskType.CREDIT_QUERY : AsyncTaskType.CREDIT_SCORING;
            String taskTypeCode = taskType.getCode();
            String taskId = AsyncTaskType.CREDIT_QUERY.equals(taskType) ? creditTaskId : scoringTaskId;
            String countVarName = AsyncTaskType.CREDIT_QUERY.equals(taskType)
                    ? ProcessVariableConstants.COMPENSATION_COUNT
                    : "scoringCompensationCount";

            AsyncTask asyncTask = null;
            if (taskId != null) {
                asyncTask = asyncTaskService.getByTaskId(taskId);
                if (asyncTask != null) {
                    asyncTaskService.incrementCompensation(taskId,
                            AsyncTaskStatus.COMPENSATED,
                            "BPMN定时边界补偿第" + newCount + "次");
                }
            }

            if (asyncTask != null && asyncTask.getRequestBody() != null) {
                resendMessage(asyncTask, taskType, newCount);
            } else {
                log.warn("[超时补偿] async_task记录或RequestBody丢失，只能通过补偿消息重发, " +
                        "taskId: {}", taskId);
                asyncTaskProducer.sendCompensation(taskId, taskTypeCode, processInstanceId,
                        applicationId, "BPMN边界定时器补偿第" + newCount + "次");
            }

            Map<String, Object> variables = new HashMap<>();
            variables.put(countVarName, newCount);
            variables.put(ProcessVariableConstants.COMPENSATION_TRIGGERED, true);
            variables.put(ProcessVariableConstants.COMPENSATION_COUNT, newCount);
            variables.put(ProcessVariableConstants.LAST_COMPENSATION_TIME,
                    LocalDateTime.now().toString());
            variables.put(ProcessVariableConstants.ASYNC_TASK_STATUS,
                    ProcessVariableConstants.ASYNC_STATUS_COMPENSATED);
            variables.put(ProcessVariableConstants.ASYNC_CALLBACK_STATUS,
                    ProcessVariableConstants.CALLBACK_STATUS_PENDING);
            variables.put(ProcessVariableConstants.ASYNC_LAST_ERROR,
                    "BPMN边界超时，第" + newCount + "次补偿中");

            execution.setVariables(variables);

            updateApplicationStatusIfNeeded(applicationId, taskType, newCount);

            log.info("[超时补偿] 执行完毕, processInstanceId: {}, taskType: {}, " +
                            "newCount: {}, taskId: {}",
                    processInstanceId, taskType, newCount, taskId);

        } catch (Exception e) {
            log.error("[超时补偿] 补偿失败, processInstanceId: {}, applicationId: {}",
                    processInstanceId, applicationId, e);
            execution.setVariable(ProcessVariableConstants.ASYNC_LAST_ERROR,
                    "补偿失败: " + e.getMessage());
            throw new RuntimeException("BPMN补偿处理失败: " + e.getMessage(), e);
        }
    }

    private void resendMessage(AsyncTask asyncTask, AsyncTaskType taskType, int newCount) {
        try {
            String requestBody = asyncTask.getRequestBody();
            switch (taskType) {
                case CREDIT_QUERY:
                    com.bc.credit.dto.credit.CreditQueryAsyncMessage cqMsg =
                            JSON.parseObject(requestBody,
                                    com.bc.credit.dto.credit.CreditQueryAsyncMessage.class);
                    cqMsg.setRetryCount(cqMsg.getRetryCount() + 1);
                    asyncTaskProducer.sendCreditQueryAsync(cqMsg);
                    log.info("[超时补偿] 重发征信查询MQ成功, taskId: {}, 补偿次数: {}",
                            asyncTask.getTaskId(), newCount);
                    break;
                case CREDIT_SCORING:
                    ScoringAsyncMessage scMsg = JSON.parseObject(requestBody,
                            ScoringAsyncMessage.class);
                    scMsg.setRetryCount(scMsg.getRetryCount() + 1);
                    asyncTaskProducer.sendScoringAsync(scMsg);
                    log.info("[超时补偿] 重发评分卡MQ成功, taskId: {}, 补偿次数: {}",
                            asyncTask.getTaskId(), newCount);
                    break;
                default:
                    asyncTaskProducer.sendCompensation(asyncTask.getTaskId(),
                            taskType.getCode(), asyncTask.getProcessInstanceId(),
                            asyncTask.getApplicationId(),
                            "第" + newCount + "次补偿, 未知任务类型: " + taskType);
            }
        } catch (Exception e) {
            log.error("[超时补偿] 重发MQ失败，改用补偿消息通用topic, taskId: {}",
                    asyncTask.getTaskId(), e);
            asyncTaskProducer.sendCompensation(asyncTask.getTaskId(),
                    taskType.getCode(), asyncTask.getProcessInstanceId(),
                    asyncTask.getApplicationId(),
                    "重发原始MQ失败，走通用补偿: " + e.getMessage());
        }
    }

    private void updateApplicationStatusIfNeeded(Long applicationId,
                                                  AsyncTaskType taskType,
                                                  int newCount) {
        if (applicationId == null) {
            return;
        }
        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application != null && newCount == 1) {
                if (!ApplicationStatusEnum.MANUAL_REVIEW.getCode().equals(
                        application.getApplicationStatus())) {
                    application.setApplicationStatus(
                            ApplicationStatusEnum.ASYNC_PROCESSING.getCode());
                    application.setUpdatedTime(LocalDateTime.now());
                    loanApplicationMapper.updateById(application);
                }
            }
        } catch (Exception e) {
            log.warn("[超时补偿] 更新申请表状态失败, applicationId: {}", applicationId, e);
        }
    }
}
