package com.bc.credit.delegate;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.common.enums.AsyncTaskStatus;
import com.bc.credit.common.enums.AsyncTaskType;
import com.bc.credit.dto.credit.ScoringAsyncMessage;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.integration.mq.AsyncTaskProducer;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.AsyncTaskService;
import com.bc.credit.service.ProcessContextService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("creditScoringDelegate")
public class CreditScoringDelegate implements JavaDelegate {

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private ProcessContextService processContextService;

    @Autowired
    private AsyncTaskProducer asyncTaskProducer;

    @Autowired
    private AsyncTaskService asyncTaskService;

    @Value("${credit.async.enabled:true}")
    private boolean asyncEnabled;

    @Value("${credit.async.scoring.enabled:true}")
    private boolean scoringAsyncEnabled;

    @Value("${credit.async.scoring.timeout-ms:20000}")
    private long timeoutMs;

    @Value("${credit.async.scoring.max-retry:3}")
    private int maxRetry;

    @Value("${credit.async.scoring.receive-task-id:receive_scoring}")
    private String receiveTaskId;

    @Value("${credit.async.scoring.signal-name:signalScoringDone}")
    private String signalName;

    @Value("${rocketmq.topic.scoring-async:credit-async-scoring}")
    private String mqTopic;

    @Value("${rocketmq.tag.scoring-async:scoring}")
    private String mqTag;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        Long applicationId = (Long) execution.getVariable(ProcessVariableConstants.APPLICATION_ID);
        String applicationNo = (String) execution.getVariable(ProcessVariableConstants.APPLICATION_NO);

        log.info("[信用评分] 服务任务执行, processInstanceId: {}, applicationId: {}, asyncEnabled: {}",
                processInstanceId, applicationId, asyncEnabled);

        if (!asyncEnabled || !scoringAsyncEnabled) {
            log.warn("[信用评分] 异步模式未启用，跳过异步发送");
            return;
        }

        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                throw new RuntimeException("贷款申请不存在: " + applicationId);
            }

            Integer creditScore = (Integer) execution.getVariable(ProcessVariableConstants.CREDIT_SCORE);
            Integer overdueCount = (Integer) execution.getVariable(ProcessVariableConstants.OVERDUE_COUNT);
            BigDecimal remainingLoanAmount = (BigDecimal) execution.getVariable(
                    ProcessVariableConstants.REMAINING_LOAN_AMOUNT);

            String taskId = asyncTaskProducer.generateTaskId();

            Map<String, Object> extraInfo = new HashMap<>();
            extraInfo.put(ProcessVariableConstants.MONTHLY_INCOME, execution.getVariable(
                    ProcessVariableConstants.MONTHLY_INCOME));
            extraInfo.put(ProcessVariableConstants.MONTHLY_DEBT, execution.getVariable(
                    ProcessVariableConstants.MONTHLY_DEBT));
            extraInfo.put(ProcessVariableConstants.AGE, execution.getVariable(
                    ProcessVariableConstants.AGE));
            extraInfo.put(ProcessVariableConstants.EDUCATION_LEVEL, execution.getVariable(
                    ProcessVariableConstants.EDUCATION_LEVEL));
            extraInfo.put(ProcessVariableConstants.WORK_YEARS, execution.getVariable(
                    ProcessVariableConstants.WORK_YEARS));
            extraInfo.put(ProcessVariableConstants.HAS_HOUSE, execution.getVariable(
                    ProcessVariableConstants.HAS_HOUSE));
            extraInfo.put(ProcessVariableConstants.HAS_CAR, execution.getVariable(
                    ProcessVariableConstants.HAS_CAR));
            extraInfo.put(ProcessVariableConstants.CUSTOMER_ID, application.getCustomerId());
            extraInfo.put(ProcessVariableConstants.CUSTOMER_NAME, application.getCustomerName());
            extraInfo.put(ProcessVariableConstants.LOAN_AMOUNT, application.getLoanAmount());
            extraInfo.put(ProcessVariableConstants.LOAN_TERM, application.getLoanTerm());

            ScoringAsyncMessage message = new ScoringAsyncMessage();
            message.setTaskId(taskId);
            message.setProcessInstanceId(processInstanceId);
            message.setApplicationId(applicationId);
            message.setApplicationNo(applicationNo);
            message.setCustomerId(application.getCustomerId());
            message.setCustomerName(application.getCustomerName());
            message.setCreditScore(creditScore);
            message.setOverdueCount(overdueCount);
            message.setRemainingLoanAmount(remainingLoanAmount);
            message.setExtraInfo(extraInfo);
            message.setRetryCount(0);
            message.setMaxRetry(maxRetry);
            message.setTimeoutMs(timeoutMs);
            message.setSubmitTime(LocalDateTime.now());
            message.setExpireTime(LocalDateTime.now().plusNanos(timeoutMs * 1_000_000L));
            message.setSignalName(signalName);
            message.setReceiveTaskId(receiveTaskId);
            message.setEngineType("PMML");
            message.setModelVersion("V1.0");

            String taskName = "信用评分-" + applicationNo;
            asyncTaskService.createTask(taskId, AsyncTaskType.CREDIT_SCORING, taskName,
                    processInstanceId, applicationId, applicationNo, application.getCustomerId(),
                    signalName, receiveTaskId, mqTopic, mqTag,
                    maxRetry, timeoutMs, JSON.toJSONString(message));

            SendResult sendResult = asyncTaskProducer.sendScoringAsync(message);

            if (SendStatus.SEND_OK.equals(sendResult.getSendStatus())) {
                asyncTaskService.updateTaskProcessing(taskId, sendResult.getMsgId(),
                        applicationId + "_" + processInstanceId);

                Map<String, Object> variables = new HashMap<>();
                variables.put(ProcessVariableConstants.SCORING_TASK_ID, taskId);
                variables.put(ProcessVariableConstants.SCORING_ASYNC_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_PENDING);
                variables.put(ProcessVariableConstants.SCORING_SUBMIT_TIME,
                        LocalDateTime.now().toString());
                variables.put(ProcessVariableConstants.SCORING_EXPIRE_TIME,
                        message.getExpireTime().toString());
                variables.put(ProcessVariableConstants.SCORING_RETRY_COUNT, 0);
                variables.put(ProcessVariableConstants.ASYNC_TASK_ID, taskId);
                variables.put(ProcessVariableConstants.ASYNC_TASK_TYPE,
                        AsyncTaskType.CREDIT_SCORING.getCode());
                variables.put(ProcessVariableConstants.ASYNC_TASK_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_PENDING);
                variables.put(ProcessVariableConstants.ASYNC_SUBMIT_TIME,
                        LocalDateTime.now().toString());
                variables.put(ProcessVariableConstants.ASYNC_EXPIRE_TIME,
                        message.getExpireTime().toString());
                variables.put(ProcessVariableConstants.ASYNC_MAX_RETRY, maxRetry);
                variables.put(ProcessVariableConstants.ASYNC_TIMEOUT_MS, timeoutMs);
                variables.put(ProcessVariableConstants.ASYNC_RETRY_COUNT, 0);
                variables.put(ProcessVariableConstants.ASYNC_CALLBACK_STATUS,
                        ProcessVariableConstants.CALLBACK_STATUS_PENDING);
                variables.put(ProcessVariableConstants.SCORE_PASS, false);

                processContextService.updateProcessVariables(execution, variables);

                log.info("[信用评分] 异步任务发送成功, taskId: {}, msgId: {}, processInstanceId: {}",
                        taskId, sendResult.getMsgId(), processInstanceId);

            } else {
                log.error("[信用评分] 异步任务发送失败, taskId: {}, status: {}",
                        taskId, sendResult.getSendStatus());
                asyncTaskService.updateTaskFailed(taskId,
                        "MQ发送失败: " + sendResult.getSendStatus(), null, 0, maxRetry);
                throw new RuntimeException("信用评分异步任务发送失败: " + sendResult.getSendStatus());
            }

        } catch (Exception e) {
            log.error("[信用评分] 异步任务异常, applicationId: {}, processInstanceId: {}",
                    applicationId, processInstanceId, e);
            Map<String, Object> errorVariables = new HashMap<>();
            errorVariables.put(ProcessVariableConstants.SCORING_ASYNC_STATUS,
                    ProcessVariableConstants.ASYNC_STATUS_FAILED);
            errorVariables.put(ProcessVariableConstants.SCORE_PASS, false);
            errorVariables.put(ProcessVariableConstants.SCORING_ERROR, e.getMessage());
            errorVariables.put(ProcessVariableConstants.ASYNC_LAST_ERROR, e.getMessage());
            processContextService.updateProcessVariables(execution, errorVariables);
            throw new RuntimeException("信用评分异步发送失败: " + e.getMessage(), e);
        }
    }
}
