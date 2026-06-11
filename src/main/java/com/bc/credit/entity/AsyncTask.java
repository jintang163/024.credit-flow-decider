package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("async_task")
public class AsyncTask implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String taskId;

    private String taskType;

    private String taskName;

    private String processInstanceId;

    private String executionId;

    private Long applicationId;

    private String applicationNo;

    private String customerId;

    private String customerName;

    private Integer status;

    private String signalName;

    private String receiveTaskId;

    private String messageName;

    private String mqTopic;

    private String mqTag;

    private String mqMsgId;

    private String mqKey;

    private Integer retryCount;

    private Integer maxRetry;

    private Long timeoutMs;

    private LocalDateTime submitTime;

    private LocalDateTime expireTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long costMs;

    private String callbackStatus;

    private LocalDateTime callbackTime;

    private Integer compensationCount;

    private LocalDateTime lastCompensationTime;

    private String lastError;

    private String errorStack;

    private String requestBody;

    private String responseBody;

    private String resultSnapshot;

    private String remark;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private Integer deleted;
}
