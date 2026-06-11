package com.bc.credit.service;

import com.bc.credit.dto.credit.WorkflowCallbackRequest;

import java.util.Map;

public interface WorkflowCallbackService {

    boolean triggerCallback(WorkflowCallbackRequest request);

    boolean signalProcessInstance(String processInstanceId, String signalName,
                                   Map<String, Object> variables);

    boolean messageEventReceived(String processInstanceId, String messageName,
                                  Map<String, Object> variables);

    boolean triggerReceiveTask(String processInstanceId, String receiveTaskId,
                                Map<String, Object> variables);

    void setProcessVariables(String processInstanceId, Map<String, Object> variables);

    boolean isProcessActive(String processInstanceId);

    Map<String, Object> getProcessVariables(String processInstanceId);

    String getCurrentActivityId(String processInstanceId);

    boolean triggerCompensationRetry(String processInstanceId, String taskType,
                                      String signalName, Map<String, Object> variables);

    boolean transferToManualReview(String processInstanceId, String taskType,
                                    String reason, String operator);

    boolean transferToManualReview(WorkflowCallbackRequest request);

    void saveApprovalRecord(Long applicationId, String applicationNo,
                             String operator, String action,
                             Integer targetStatus, String remark);
}
