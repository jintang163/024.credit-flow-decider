package com.bc.credit.service;

import com.bc.credit.dto.credit.WorkflowCallbackRequest;
import com.bc.credit.entity.AsyncTask;
import com.bc.credit.common.enums.AsyncTaskType;
import com.bc.credit.common.enums.AsyncTaskStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AsyncTaskService {

    AsyncTask createTask(String taskId, AsyncTaskType taskType, String taskName,
                         String processInstanceId, Long applicationId,
                         String applicationNo, String customerId,
                         String signalName, String receiveTaskId,
                         String mqTopic, String mqTag,
                         Integer maxRetry, Long timeoutMs,
                         String requestBody);

    void updateTaskStatus(String taskId, AsyncTaskStatus status);

    void updateTaskProcessing(String taskId, String mqMsgId, String mqKey);

    void updateTaskSuccess(String taskId, String responseBody, String resultSnapshot,
                           Long costMs, String callbackStatus, Map<String, Object> variables);

    void updateTaskSuccess(String taskId, Long costMs, String resultSnapshot);

    void updateTaskFailed(String taskId, String errorMsg, String errorStack,
                          Integer retryCount, Integer maxRetry);

    void updateTaskDeadLetter(String taskId, String reason, String rawBody, Integer totalRetry);

    void incrementRetry(String taskId, AsyncTaskStatus status, String lastError);

    void incrementRetry(String taskId, String lastError);

    void incrementCompensation(String taskId, AsyncTaskStatus status, String lastError);

    AsyncTask getByTaskId(String taskId);

    AsyncTask getLatestByProcessAndType(String processInstanceId, String taskType);

    List<AsyncTask> getExpiredTasks(LocalDateTime now, int limit);

    List<AsyncTask> getRetryableFailedTasks(LocalDateTime now, int limit);

    long countByStatusAndType(Integer status, String taskType);
}
