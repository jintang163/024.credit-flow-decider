package com.bc.credit.listener;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.Duration;

@Slf4j
@Component("taskCompleteListener")
public class TaskCompleteListener implements TaskListener {

    @Override
    public void notify(DelegateTask delegateTask) {
        String taskId = delegateTask.getId();
        String taskName = delegateTask.getName();
        String processInstanceId = delegateTask.getProcessInstanceId();
        String assignee = delegateTask.getAssignee();
        String outcome = (String) delegateTask.getVariable("outcome");
        String comment = (String) delegateTask.getVariable("comment");

        log.info("任务完成 - TaskId: {}, TaskName: {}, ProcessInstanceId: {}, Assignee: {}, Outcome: {}, Comment: {}",
                taskId, taskName, processInstanceId, assignee, outcome, comment);

        String createTimeStr = (String) delegateTask.getVariableLocal("taskCreateTime");
        if (createTimeStr != null) {
            try {
                LocalDateTime createTime = LocalDateTime.parse(createTimeStr);
                Duration duration = Duration.between(createTime, LocalDateTime.now());
                log.info("任务处理时长 - TaskId: {}, Duration: {}秒", taskId, duration.getSeconds());
            } catch (Exception e) {
                log.warn("计算任务处理时长失败", e);
            }
        }

        if ("RETURN".equals(outcome) || "REJECT".equals(outcome)) {
            log.info("任务已退回/拒绝 - TaskId: {}, Outcome: {}", taskId, outcome);
        }
    }
}
