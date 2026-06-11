package com.bc.credit.delegate;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.common.enums.ApplicationStatusEnum;
import com.bc.credit.common.enums.AsyncTaskStatus;
import com.bc.credit.common.enums.AsyncTaskType;
import com.bc.credit.dto.credit.CreditQueryAsyncMessage;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("creditQueryDelegate")
public class CreditQueryDelegate implements JavaDelegate {

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

    @Value("${credit.async.credit-query.enabled:true}")
    private boolean creditQueryAsyncEnabled;

    @Value("${credit.async.credit-query.timeout-ms:30000}")
    private long timeoutMs;

    @Value("${credit.async.credit-query.max-retry:3}")
    private int maxRetry;

    @Value("${credit.async.credit-query.receive-task-id:receive_credit_query}")
    private String receiveTaskId;

    @Value("${credit.async.credit-query.signal-name:signalCreditQueryDone}")
    private String signalName;

    @Value("${rocketmq.topic.credit-query-async:credit-async-query}")
    private String mqTopic;

    @Value("${rocketmq.tag.credit-query-async:query}")
    private String mqTag;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        String executionId = execution.getId();
        Long applicationId = (Long) execution.getVariable(ProcessVariableConstants.APPLICATION_ID);
        String applicationNo = (String) execution.getVariable(ProcessVariableConstants.APPLICATION_NO);

        log.info("[征信查询] 服务任务执行, processInstanceId: {}, executionId: {}, " +
                        "applicationId: {}, applicationNo: {}, asyncEnabled: {}",
                processInstanceId, executionId, applicationId, applicationNo, asyncEnabled);

        if (!asyncEnabled || !creditQueryAsyncEnabled) {
            log.warn("[征信查询] 异步模式未启用，跳过异步发送");
            return;
        }

        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                throw new RuntimeException("贷款申请不存在: " + applicationId);
            }

            String taskId = asyncTaskProducer.generateTaskId();

            CreditQueryAsyncMessage message = new CreditQueryAsyncMessage();
            message.setTaskId(taskId);
            message.setProcessInstanceId(processInstanceId);
            message.setApplicationId(applicationId);
            message.setApplicationNo(applicationNo);
            message.setCustomerId(application.getCustomerId());
            message.setCustomerName(application.getCustomerName());
            message.setIdCard(application.getIdCard());
            message.setPhone(application.getPhone());
            message.setDataSources(Arrays.asList("PBOC", "BAIHANG", "SOCIAL_SECURITY", "HOUSING_FUND"));
            message.setRetryCount(0);
            message.setMaxRetry(maxRetry);
            message.setTimeoutMs(timeoutMs);
            message.setSubmitTime(LocalDateTime.now());
            message.setExpireTime(LocalDateTime.now().plusNanos(timeoutMs * 1_000_000L));
            message.setSignalName(signalName);
            message.setReceiveTaskId(receiveTaskId);

            String taskName = "征信查询-" + applicationNo;
            asyncTaskService.createTask(taskId, AsyncTaskType.CREDIT_QUERY, taskName,
                    processInstanceId, applicationId, applicationNo, application.getCustomerId(),
                    signalName, receiveTaskId, mqTopic, mqTag,
                    maxRetry, timeoutMs, JSON.toJSONString(message));

            if (application.getApplicationStatus() == null
                    || !ApplicationStatusEnum.MANUAL_REVIEW.getCode().equals(application.getApplicationStatus())) {
                application.setApplicationStatus(ApplicationStatusEnum.ASYNC_PROCESSING.getCode());
                application.setUpdatedTime(LocalDateTime.now());
                loanApplicationMapper.updateById(application);
            }

            SendResult sendResult = asyncTaskProducer.sendCreditQueryAsync(message);

            if (SendStatus.SEND_OK.equals(sendResult.getSendStatus())) {
                asyncTaskService.updateTaskProcessing(taskId, sendResult.getMsgId(),
                        applicationId + "_" + processInstanceId);

                Map<String, Object> variables = new HashMap<>();
                variables.put(ProcessVariableConstants.CREDIT_QUERY_TASK_ID, taskId);
                variables.put(ProcessVariableConstants.CREDIT_QUERY_ASYNC_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_PENDING);
                variables.put(ProcessVariableConstants.CREDIT_QUERY_SUBMIT_TIME,
                        LocalDateTime.now().toString());
                variables.put(ProcessVariableConstants.CREDIT_QUERY_EXPIRE_TIME,
                        message.getExpireTime().toString());
                variables.put(ProcessVariableConstants.CREDIT_QUERY_RETRY_COUNT, 0);
                variables.put(ProcessVariableConstants.ASYNC_TASK_ID, taskId);
                variables.put(ProcessVariableConstants.ASYNC_TASK_TYPE,
                        AsyncTaskType.CREDIT_QUERY.getCode());
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
                variables.put(ProcessVariableConstants.CREDIT_QUERY_SUCCESS, false);

                processContextService.updateProcessVariables(execution, variables);

                log.info("[征信查询] 异步任务发送成功, taskId: {}, msgId: {}, processInstanceId: {}",
                        taskId, sendResult.getMsgId(), processInstanceId);

            } else {
                log.error("[征信查询] 异步任务发送失败, taskId: {}, status: {}",
                        taskId, sendResult.getSendStatus());
                asyncTaskService.updateTaskFailed(taskId,
                        "MQ发送失败: " + sendResult.getSendStatus(), null, 0, maxRetry);
                throw new RuntimeException("征信查询异步任务发送失败: " + sendResult.getSendStatus());
            }

        } catch (Exception e) {
            log.error("[征信查询] 异步任务异常, applicationId: {}, processInstanceId: {}",
                    applicationId, processInstanceId, e);
            Map<String, Object> errorVariables = new HashMap<>();
            errorVariables.put(ProcessVariableConstants.CREDIT_QUERY_ASYNC_STATUS,
                    ProcessVariableConstants.ASYNC_STATUS_FAILED);
            errorVariables.put(ProcessVariableConstants.CREDIT_QUERY_SUCCESS, false);
            errorVariables.put(ProcessVariableConstants.CREDIT_QUERY_ERROR, e.getMessage());
            errorVariables.put(ProcessVariableConstants.ASYNC_LAST_ERROR, e.getMessage());
            processContextService.updateProcessVariables(execution, errorVariables);
            throw new RuntimeException("征信查询异步发送失败: " + e.getMessage(), e);
        }
    }
}
