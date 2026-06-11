package com.bc.credit.dto.credit;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DeadLetterMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String originalTopic;

    private String originalTag;

    private String originalMsgId;

    private String taskId;

    private String taskType;

    private String processInstanceId;

    private Long applicationId;

    private String applicationNo;

    private Integer retryCount;

    private Integer maxRetry;

    private String lastError;

    private String originalBody;

    private LocalDateTime deadTime;

    private String deadReason;

    private Boolean needManualReview;

    private String alertLevel;

    private String alertMessage;
}
