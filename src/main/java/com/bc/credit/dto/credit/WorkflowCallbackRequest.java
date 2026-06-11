package com.bc.credit.dto.credit;

import lombok.Data;
import java.io.Serializable;
import java.util.Map;

@Data
public class WorkflowCallbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String processInstanceId;

    private String executionId;

    private String signalName;

    private String messageName;

    private String receiveTaskId;

    private String taskType;

    private String taskId;

    private Boolean success;

    private String errorMsg;

    private Map<String, Object> variables;

    private String callbackSource;

    private Long callbackTime;
}
