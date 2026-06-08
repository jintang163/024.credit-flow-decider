package com.bc.credit.service;

import com.bc.credit.dto.LoanApplicationDTO;
import com.bc.credit.entity.LoanApplication;
import java.io.InputStream;
import java.util.Map;

public interface WorkflowService {

    String deployProcess(String bpmnXml, String processName, String deployBy);

    String deployProcessFromFile(String filePath, String processName, String deployBy);

    Map<String, Object> startProcess(String processKey, LoanApplication application,
                                     LoanApplicationDTO applicationDTO);

    void suspendProcess(String processInstanceId);

    void activateProcess(String processInstanceId);

    void terminateProcess(String processInstanceId, String reason);

    Map<String, Object> getProcessVariables(String processInstanceId);

    void setProcessVariables(String processInstanceId, Map<String, Object> variables);

    String getCurrentActivity(String processInstanceId);

    String exportProcessDefinition(String processDefinitionId);

    InputStream getProcessDiagram(String processInstanceId);
}
