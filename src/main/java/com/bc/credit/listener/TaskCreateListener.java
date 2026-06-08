package com.bc.credit.listener;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component("taskCreateListener")
public class TaskCreateListener implements TaskListener {

    @Override
    public void notify(DelegateTask delegateTask) {
        String taskId = delegateTask.getId();
        String taskName = delegateTask.getName();
        String processInstanceId = delegateTask.getProcessInstanceId();
        String assignee = delegateTask.getAssignee();

        log.info("任务创建 - TaskId: {}, TaskName: {}, ProcessInstanceId: {}, Assignee: {}, CreateTime: {}",
                taskId, taskName, processInstanceId, assignee, LocalDateTime.now());

        delegateTask.setVariableLocal("taskCreateTime", LocalDateTime.now().toString());

        if (assignee != null) {
            log.info("已分配任务给用户: {}, 任务: {}", assignee, taskName);
        } else {
            log.warn("任务 {} 未指定处理人", taskName);
        }
    }
}
