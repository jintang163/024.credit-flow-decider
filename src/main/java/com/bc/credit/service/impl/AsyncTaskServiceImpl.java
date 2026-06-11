package com.bc.credit.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.AsyncTaskStatus;
import com.bc.credit.common.enums.AsyncTaskType;
import com.bc.credit.entity.AsyncTask;
import com.bc.credit.mapper.AsyncTaskMapper;
import com.bc.credit.service.AsyncTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AsyncTaskServiceImpl implements AsyncTaskService {

    @Autowired
    private AsyncTaskMapper asyncTaskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AsyncTask createTask(String taskId, AsyncTaskType taskType, String taskName,
                                 String processInstanceId, Long applicationId,
                                 String applicationNo, String customerId,
                                 String signalName, String receiveTaskId,
                                 String mqTopic, String mqTag,
                                 Integer maxRetry, Long timeoutMs,
                                 String requestBody) {
        log.info("[异步任务] 创建任务, taskId: {}, taskType: {}, processInstanceId: {}",
                taskId, taskType.getCode(), processInstanceId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = timeoutMs != null ?
                now.plusNanos(timeoutMs * 1_000_000L) : now.plusMinutes(30);

        AsyncTask task = new AsyncTask();
        task.setId(IdWorker.getId());
        task.setTaskId(taskId);
        task.setTaskType(taskType.getCode());
        task.setTaskName(taskName);
        task.setProcessInstanceId(processInstanceId);
        task.setApplicationId(applicationId);
        task.setApplicationNo(applicationNo);
        task.setCustomerId(customerId);
        task.setStatus(AsyncTaskStatus.PENDING.getCode());
        task.setSignalName(signalName);
        task.setReceiveTaskId(receiveTaskId);
        task.setMqTopic(mqTopic);
        task.setMqTag(mqTag);
        task.setRetryCount(0);
        task.setMaxRetry(maxRetry != null ? maxRetry : 3);
        task.setTimeoutMs(timeoutMs != null ? timeoutMs : 1800000L);
        task.setSubmitTime(now);
        task.setExpireTime(expireTime);
        task.setRequestBody(requestBody);
        task.setCompensationCount(0);
        task.setCreatedTime(now);
        task.setUpdatedTime(now);
        task.setDeleted(0);

        asyncTaskMapper.insert(task);
        log.info("[异步任务] 任务创建成功, id: {}, taskId: {}, expireTime: {}",
                task.getId(), taskId, expireTime);
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskStatus(String taskId, AsyncTaskStatus status) {
        log.debug("[异步任务] 更新状态, taskId: {}, status: {}", taskId, status);
        asyncTaskMapper.updateStatusByTaskId(taskId, status.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskProcessing(String taskId, String mqMsgId, String mqKey) {
        log.debug("[异步任务] 标记处理中, taskId: {}, mqMsgId: {}", taskId, mqMsgId);
        AsyncTask task = asyncTaskMapper.selectByTaskId(taskId);
        if (task != null) {
            task.setStatus(AsyncTaskStatus.PROCESSING.getCode());
            task.setMqMsgId(mqMsgId);
            task.setMqKey(mqKey);
            task.setStartTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());
            asyncTaskMapper.updateById(task);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskSuccess(String taskId, String responseBody, String resultSnapshot,
                                   Long costMs, String callbackStatus,
                                   Map<String, Object> variables) {
        log.info("[异步任务] 标记成功, taskId: {}, costMs: {}", taskId, costMs);
        AsyncTask task = asyncTaskMapper.selectByTaskId(taskId);
        if (task != null) {
            LocalDateTime now = LocalDateTime.now();
            task.setStatus(AsyncTaskStatus.SUCCESS.getCode());
            task.setResponseBody(responseBody);
            task.setResultSnapshot(resultSnapshot != null ? resultSnapshot :
                    (variables != null ? JSON.toJSONString(variables) : null));
            task.setEndTime(now);
            task.setCostMs(costMs);
            task.setCallbackStatus(callbackStatus);
            task.setCallbackTime(now);
            task.setUpdatedTime(now);
            asyncTaskMapper.updateById(task);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskSuccess(String taskId, Long costMs, String resultSnapshot) {
        updateTaskSuccess(taskId, resultSnapshot, resultSnapshot, costMs,
                AsyncTaskStatus.SUCCESS.name(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskFailed(String taskId, String errorMsg, String errorStack,
                                  Integer retryCount, Integer maxRetry) {
        log.warn("[异步任务] 标记失败, taskId: {}, error: {}", taskId, errorMsg);
        AsyncTask task = asyncTaskMapper.selectByTaskId(taskId);
        if (task != null) {
            AsyncTaskStatus status = AsyncTaskStatus.FAILED;
            if (retryCount != null && maxRetry != null && retryCount >= maxRetry) {
                status = AsyncTaskStatus.DEAD_LETTER;
            }
            task.setStatus(status.getCode());
            task.setLastError(errorMsg);
            task.setErrorStack(errorStack);
            task.setEndTime(LocalDateTime.now());
            task.setRetryCount(retryCount != null ? retryCount : task.getRetryCount());
            task.setUpdatedTime(LocalDateTime.now());
            asyncTaskMapper.updateById(task);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskDeadLetter(String taskId, String reason, String rawBody,
                                      Integer totalRetry) {
        log.error("[异步任务] 标记死信, taskId: {}, reason: {}, totalRetry: {}",
                taskId, reason, totalRetry);
        AsyncTask task = asyncTaskMapper.selectByTaskId(taskId);
        if (task != null) {
            LocalDateTime now = LocalDateTime.now();
            task.setStatus(AsyncTaskStatus.DEAD_LETTER.getCode());
            task.setLastError(reason);
            task.setResponseBody(rawBody);
            task.setRetryCount(totalRetry != null ? totalRetry : task.getMaxRetry());
            task.setEndTime(now);
            task.setUpdatedTime(now);
            asyncTaskMapper.updateById(task);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementRetry(String taskId, AsyncTaskStatus status, String lastError) {
        log.info("[异步任务] 重试计数+, taskId: {}, status: {}", taskId, status);
        asyncTaskMapper.incrementRetryAndUpdateStatus(taskId, status.getCode(), lastError);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementRetry(String taskId, String lastError) {
        log.info("[异步任务] 重试计数+, taskId: {}", taskId);
        asyncTaskMapper.incrementRetryAndUpdateStatus(taskId,
                AsyncTaskStatus.RETRYING.getCode(), lastError);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementCompensation(String taskId, AsyncTaskStatus status, String lastError) {
        log.info("[异步任务] 补偿计数+, taskId: {}, status: {}", taskId, status);
        asyncTaskMapper.incrementCompensationAndUpdateStatus(taskId, status.getCode(), lastError);
    }

    @Override
    public AsyncTask getByTaskId(String taskId) {
        return asyncTaskMapper.selectByTaskId(taskId);
    }

    @Override
    public AsyncTask getLatestByProcessAndType(String processInstanceId, String taskType) {
        return asyncTaskMapper.selectLatestByProcessAndType(processInstanceId, taskType);
    }

    @Override
    public List<AsyncTask> getExpiredTasks(LocalDateTime now, int limit) {
        return asyncTaskMapper.selectExpiredTasks(now, limit);
    }

    @Override
    public List<AsyncTask> getRetryableFailedTasks(LocalDateTime now, int limit) {
        return asyncTaskMapper.selectRetryableFailedTasks(now, limit);
    }

    @Override
    public long countByStatusAndType(Integer status, String taskType) {
        return asyncTaskMapper.countByStatusAndType(status, taskType);
    }
}
