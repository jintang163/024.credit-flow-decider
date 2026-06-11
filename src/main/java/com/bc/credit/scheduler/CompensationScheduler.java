package com.bc.credit.scheduler;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.common.enums.AsyncTaskStatus;
import com.bc.credit.common.enums.AsyncTaskType;
import com.bc.credit.dto.credit.CreditQueryAsyncMessage;
import com.bc.credit.dto.credit.ScoringAsyncMessage;
import com.bc.credit.dto.credit.WorkflowCallbackRequest;
import com.bc.credit.entity.AsyncTask;
import com.bc.credit.integration.mq.AsyncTaskProducer;
import com.bc.credit.service.AsyncTaskService;
import com.bc.credit.service.WorkflowCallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class CompensationScheduler {

    @Value("${credit.async.enabled:true}")
    private boolean asyncEnabled;

    @Value("${credit.async.compensation.enabled:true}")
    private boolean compensationEnabled;

    @Value("${credit.async.compensation.batch-size:50}")
    private int batchSize;

    @Value("${credit.async.compensation.max-transfer-count:3}")
    private int maxCompensationCount;

    @Value("${credit.async.compensation.manual-transfer-threshold:3}")
    private int manualTransferThreshold;

    @Autowired
    private AsyncTaskService asyncTaskService;

    @Autowired
    private AsyncTaskProducer asyncTaskProducer;

    @Autowired
    private WorkflowCallbackService workflowCallbackService;

    private final Map<String, AtomicInteger> inProgressTaskLock = new ConcurrentHashMap<>();

    @Scheduled(cron = "${credit.async.compensation.cron-expression:0 */1 * * * ?}")
    public void scanExpiredTasks() {
        if (!asyncEnabled || !compensationEnabled) {
            return;
        }
        long start = System.currentTimeMillis();
        log.info("[补偿扫描] 开始扫描超时任务, batchSize: {}, maxCompensationCount: {}",
                batchSize, maxCompensationCount);

        int totalHandled = 0;
        int totalRetried = 0;
        int totalManualTransferred = 0;
        int totalProcessActive = 0;

        try {
            List<AsyncTask> expiredTasks = asyncTaskService.getExpiredTasks(
                    LocalDateTime.now(), batchSize);
            if (expiredTasks == null || expiredTasks.isEmpty()) {
                log.debug("[补偿扫描] 本次扫描无超时任务, cost: {}ms",
                        System.currentTimeMillis() - start);
                return;
            }

            log.info("[补偿扫描] 扫描到超时任务数: {}", expiredTasks.size());

            for (AsyncTask task : expiredTasks) {
                String taskId = task.getTaskId();

                AtomicInteger lock = inProgressTaskLock.computeIfAbsent(taskId, k -> new AtomicInteger(0));
                if (!lock.compareAndSet(0, 1)) {
                    log.debug("[补偿扫描] 跳过处理中任务, taskId: {}", taskId);
                    continue;
                }

                try {
                    totalHandled++;
                    String processInstanceId = task.getProcessInstanceId();

                    if (!workflowCallbackService.isProcessActive(processInstanceId)) {
                        log.warn("[补偿扫描] 流程已结束，任务标记CANCELLED, taskId: {}, " +
                                "processInstanceId: {}", taskId, processInstanceId);
                        try {
                            asyncTaskService.updateTaskStatus(taskId, AsyncTaskStatus.CANCELLED);
                        } catch (Exception e) {
                            log.warn("[补偿扫描] 更新CANCELLED失败, taskId: {}", taskId, e);
                        }
                        continue;
                    }

                    totalProcessActive++;

                    int compensationCount = task.getCompensationCount() != null
                            ? task.getCompensationCount() : 0;

                    if (compensationCount >= maxCompensationCount) {
                        log.warn("[补偿扫描] 补偿次数超限，转人工, taskId: {}, " +
                                        "compensationCount: {}, threshold: {}",
                                taskId, compensationCount, maxCompensationCount);
                        boolean ok = handleManualTransfer(task);
                        if (ok) {
                            totalManualTransferred++;
                        }
                    } else {
                        boolean ok = handleRetry(task, compensationCount);
                        if (ok) {
                            totalRetried++;
                        }
                    }

                } catch (Exception e) {
                    log.error("[补偿扫描] 处理单个任务异常, taskId: {}", task.getTaskId(), e);
                } finally {
                    inProgressTaskLock.remove(taskId);
                }
            }

        } catch (Exception e) {
            log.error("[补偿扫描] 扫描异常", e);
        } finally {
            long cost = System.currentTimeMillis() - start;
            log.info("[补偿扫描] 本次扫描完成, cost: {}ms, handled: {}, retried: {}, " +
                            "manualTransferred: {}, processActive: {}, lockSize: {}",
                    cost, totalHandled, totalRetried, totalManualTransferred,
                    totalProcessActive, inProgressTaskLock.size());
        }
    }

    @Scheduled(cron = "${credit.async.compensation.retry-failed-cron:0 */3 * * * ?}")
    public void retryFailedTasks() {
        if (!asyncEnabled || !compensationEnabled) {
            return;
        }
        long start = System.currentTimeMillis();
        log.debug("[补偿扫描-失败重试] 开始扫描可重试失败任务");

        int totalHandled = 0;
        try {
            List<AsyncTask> failedTasks = asyncTaskService.getRetryableFailedTasks(
                    LocalDateTime.now(), batchSize);
            if (failedTasks == null || failedTasks.isEmpty()) {
                return;
            }
            log.info("[补偿扫描-失败重试] 扫描到可重试失败任务: {}", failedTasks.size());

            for (AsyncTask task : failedTasks) {
                String taskId = task.getTaskId();

                AtomicInteger lock = inProgressTaskLock.computeIfAbsent(
                        taskId, k -> new AtomicInteger(0));
                if (!lock.compareAndSet(0, 1)) {
                    continue;
                }

                try {
                    String processInstanceId = task.getProcessInstanceId();
                    if (!workflowCallbackService.isProcessActive(processInstanceId)) {
                        asyncTaskService.updateTaskStatus(taskId, AsyncTaskStatus.CANCELLED);
                        continue;
                    }
                    totalHandled++;
                    int newCount = (task.getCompensationCount() != null
                            ? task.getCompensationCount() : 0) + 1;
                    handleRetry(task, newCount - 1);
                } catch (Exception e) {
                    log.error("[补偿扫描-失败重试] 处理异常, taskId: {}", taskId, e);
                } finally {
                    inProgressTaskLock.remove(taskId);
                }
            }

        } catch (Exception e) {
            log.error("[补偿扫描-失败重试] 扫描异常", e);
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (totalHandled > 0) {
                log.info("[补偿扫描-失败重试] 处理完成, cost: {}ms, handled: {}",
                        cost, totalHandled);
            }
        }
    }

    private boolean handleRetry(AsyncTask task, int currentCompensationCount) {
        String taskId = task.getTaskId();
        int newCount = currentCompensationCount + 1;
        String taskType = task.getTaskType();

        log.info("[补偿扫描-重试] 执行第{}次补偿, taskId: {}, taskType: {}, processInstanceId: {}",
                newCount, taskId, taskType, task.getProcessInstanceId());

        try {
            asyncTaskService.incrementCompensation(taskId,
                    AsyncTaskStatus.COMPENSATED,
                    "定时扫描补偿第" + newCount + "次");
        } catch (Exception e) {
            log.warn("[补偿扫描-重试] 更新补偿计数失败, taskId: {}", taskId, e);
        }

        boolean resentOk = resendOriginalMessage(task, taskType, newCount);

        if (!resentOk) {
            asyncTaskProducer.sendCompensation(taskId, taskType,
                    task.getProcessInstanceId(), task.getApplicationId(),
                    "定时扫描补偿第" + newCount + "次");
        }

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put(ProcessVariableConstants.COMPENSATION_TRIGGERED, true);
            variables.put(ProcessVariableConstants.COMPENSATION_COUNT, newCount);
            variables.put(ProcessVariableConstants.LAST_COMPENSATION_TIME,
                    LocalDateTime.now().toString());
            variables.put(ProcessVariableConstants.ASYNC_TASK_STATUS,
                    ProcessVariableConstants.ASYNC_STATUS_COMPENSATED);
            variables.put(ProcessVariableConstants.ASYNC_CALLBACK_STATUS,
                    ProcessVariableConstants.CALLBACK_STATUS_PENDING);
            variables.put(ProcessVariableConstants.ASYNC_LAST_ERROR,
                    "定时扫描超时，第" + newCount + "次补偿中");

            if (AsyncTaskType.CREDIT_QUERY.getCode().equals(taskType)) {
                variables.put(ProcessVariableConstants.CREDIT_QUERY_ASYNC_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_COMPENSATED);
                variables.put(ProcessVariableConstants.CREDIT_QUERY_CALLBACK_STATUS,
                        ProcessVariableConstants.CALLBACK_STATUS_PENDING);
            } else if (AsyncTaskType.CREDIT_SCORING.getCode().equals(taskType)) {
                variables.put("scoringCompensationCount", newCount);
                variables.put(ProcessVariableConstants.SCORING_ASYNC_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_COMPENSATED);
                variables.put(ProcessVariableConstants.SCORING_CALLBACK_STATUS,
                        ProcessVariableConstants.CALLBACK_STATUS_PENDING);
            }

            workflowCallbackService.setProcessVariables(
                    task.getProcessInstanceId(), variables);
        } catch (Exception e) {
            log.warn("[补偿扫描-重试] 设置流程变量失败, taskId: {}", taskId, e);
        }

        return true;
    }

    private boolean resendOriginalMessage(AsyncTask task, String taskType, int newCount) {
        if (task.getRequestBody() == null || task.getRequestBody().isEmpty()) {
            return false;
        }
        try {
            if (AsyncTaskType.CREDIT_QUERY.getCode().equals(taskType)) {
                CreditQueryAsyncMessage cqMsg = JSON.parseObject(
                        task.getRequestBody(), CreditQueryAsyncMessage.class);
                cqMsg.setRetryCount(cqMsg.getRetryCount() + 1);
                asyncTaskProducer.sendCreditQueryAsync(cqMsg);
                log.info("[补偿扫描-重试] 重发征信MQ成功, taskId: {}, 补偿次数: {}", taskId, newCount);
                return true;
            } else if (AsyncTaskType.CREDIT_SCORING.getCode().equals(taskType)) {
                ScoringAsyncMessage scMsg = JSON.parseObject(
                        task.getRequestBody(), ScoringAsyncMessage.class);
                scMsg.setRetryCount(scMsg.getRetryCount() + 1);
                asyncTaskProducer.sendScoringAsync(scMsg);
                log.info("[补偿扫描-重试] 重发评分卡MQ成功, taskId: {}, 补偿次数: {}", taskId, newCount);
                return true;
            }
        } catch (Exception e) {
            log.error("[补偿扫描-重试] 重发原始MQ失败, taskId: {}, taskType: {}",
                    taskId, taskType, e);
        }
        return false;
    }

    private boolean handleManualTransfer(AsyncTask task) {
        String taskId = task.getTaskId();
        String taskType = task.getTaskType();
        String processInstanceId = task.getProcessInstanceId();

        log.warn("[补偿扫描-转人工] 任务补偿超限，转人工复核, taskId: {}, taskType: {}, " +
                        "applicationId: {}, processInstanceId: {}",
                taskId, taskType, task.getApplicationId(), processInstanceId);

        try {
            asyncTaskService.updateTaskStatus(taskId, AsyncTaskStatus.MANUAL_REVIEW);
        } catch (Exception e) {
            log.warn("[补偿扫描-转人工] 更新async_task状态失败, taskId: {}", taskId, e);
        }

        try {
            String reason = "定时扫描补偿超限(第" + task.getCompensationCount() + "次)转人工";
            if (task.getLastError() != null && !task.getLastError().isEmpty()) {
                reason += " | 最后错误: " + (task.getLastError().length() > 200
                        ? task.getLastError().substring(0, 200) : task.getLastError());
            }

            WorkflowCallbackRequest request = new WorkflowCallbackRequest();
            request.setProcessInstanceId(processInstanceId);
            request.setTaskId(taskId);
            request.setTaskType(taskType);
            request.setSignalName(task.getSignalName());
            request.setReceiveTaskId(task.getReceiveTaskId());
            request.setSuccess(false);
            request.setErrorMsg(reason);

            Map<String, Object> variables = new HashMap<>();
            variables.put(ProcessVariableConstants.NEED_MANUAL_REVIEW, true);
            variables.put(ProcessVariableConstants.MANUAL_REVIEW_REASON, reason);
            variables.put(ProcessVariableConstants.ASYNC_TASK_STATUS,
                    ProcessVariableConstants.ASYNC_STATUS_MANUAL_REVIEW);
            variables.put(ProcessVariableConstants.ASYNC_LAST_ERROR, reason);

            if (AsyncTaskType.CREDIT_QUERY.getCode().equals(taskType)) {
                variables.put(ProcessVariableConstants.CREDIT_QUERY_ASYNC_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_MANUAL_REVIEW);
                variables.put(ProcessVariableConstants.CREDIT_QUERY_SUCCESS, false);
                variables.put(ProcessVariableConstants.CREDIT_QUERY_ERROR, reason);
            } else if (AsyncTaskType.CREDIT_SCORING.getCode().equals(taskType)) {
                variables.put(ProcessVariableConstants.SCORING_ASYNC_STATUS,
                        ProcessVariableConstants.ASYNC_STATUS_MANUAL_REVIEW);
                variables.put(ProcessVariableConstants.SCORE_PASS, false);
                variables.put(ProcessVariableConstants.SCORING_ERROR, reason);
            }
            request.setVariables(variables);

            workflowCallbackService.transferToManualReview(request);
            return true;

        } catch (Exception e) {
            log.error("[补偿扫描-转人工] 处理失败, taskId: {}", taskId, e);
            return false;
        }
    }

    public Map<String, Object> getCompensationStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("asyncEnabled", asyncEnabled);
        result.put("compensationEnabled", compensationEnabled);
        result.put("batchSize", batchSize);
        result.put("manualTransferThreshold", manualTransferThreshold);
        result.put("inProgressTaskCount", inProgressTaskLock.size());

        Map<String, Object> counts = new HashMap<>();
        for (AsyncTaskType type : AsyncTaskType.values()) {
            for (AsyncTaskStatus status : AsyncTaskStatus.values()) {
                try {
                    long c = asyncTaskService.countByStatusAndType(
                            status.getCode(), type.getCode());
                    if (c > 0) {
                        counts.put(type.name() + "_" + status.name(), c);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        result.put("statusCounts", counts);
        return result;
    }
}
