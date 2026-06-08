package com.bc.credit.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.ApplicationStatusEnum;
import com.bc.credit.dto.LoanApplicationDTO;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.entity.ProcessDefinitionVersion;
import com.bc.credit.mapper.ProcessDefinitionVersionMapper;
import com.bc.credit.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.*;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WorkflowServiceImpl implements WorkflowService {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private ProcessDefinitionVersionMapper versionMapper;

    @Value("${credit.workflow.default-process-key:credit_approval_process}")
    private String defaultProcessKey;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deployProcess(String bpmnXml, String processName, String deployBy) {
        log.info("开始部署流程定义, processName: {}, deployBy: {}", processName, deployBy);

        String resourceName = processName + ".bpmn20.xml";

        Deployment deployment = repositoryService.createDeployment()
                .name(processName)
                .addBytes(resourceName, bpmnXml.getBytes(StandardCharsets.UTF_8))
                .deploy();

        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        Integer nextVersion = versionMapper.getNextVersion(processDefinition.getKey());

        ProcessDefinitionVersion version = new ProcessDefinitionVersion();
        version.setId(IdWorker.getId());
        version.setProcessKey(processDefinition.getKey());
        version.setProcessName(processName);
        version.setFlowableDeploymentId(deployment.getId());
        version.setFlowableDefinitionId(processDefinition.getId());
        version.setVersion(nextVersion);
        version.setBpmnXml(bpmnXml);
        version.setStatus(1);
        version.setDeployTime(LocalDateTime.now());
        version.setDeployBy(deployBy);
        version.setRemark("版本 " + nextVersion + " 部署");
        version.setCreatedTime(LocalDateTime.now());
        version.setUpdatedTime(LocalDateTime.now());
        version.setDeleted(0);

        versionMapper.insert(version);

        log.info("流程定义部署成功, processKey: {}, version: {}, deploymentId: {}",
                processDefinition.getKey(), nextVersion, deployment.getId());

        return deployment.getId();
    }

    @Override
    public String deployProcessFromFile(String filePath, String processName, String deployBy) {
        try {
            java.io.File file = new java.io.File(filePath);
            String bpmnXml = org.apache.commons.io.FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            return deployProcess(bpmnXml, processName, deployBy);
        } catch (Exception e) {
            log.error("从文件部署流程失败", e);
            throw new RuntimeException("部署流程失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startProcess(String processKey, LoanApplication application,
                                            LoanApplicationDTO applicationDTO) {
        log.info("启动流程实例, processKey: {}, applicationNo: {}", processKey, application.getApplicationNo());

        String actualProcessKey = (processKey != null && !processKey.isEmpty()) ? processKey : defaultProcessKey;

        Map<String, Object> variables = new HashMap<>();
        variables.put("applicationId", application.getId());
        variables.put("applicationNo", application.getApplicationNo());
        variables.put("customerId", application.getCustomerId());
        variables.put("customerName", application.getCustomerName());
        variables.put("loanAmount", application.getLoanAmount());
        variables.put("loanTerm", application.getLoanTerm());
        variables.put("monthlyIncome", applicationDTO.getMonthlyIncome());
        variables.put("monthlyDebt", applicationDTO.getMonthlyDebt());
        variables.put("age", applicationDTO.getAge());
        variables.put("educationLevel", applicationDTO.getEducationLevel());
        variables.put("workYears", applicationDTO.getWorkYears());
        variables.put("hasHouse", applicationDTO.getHasHouse());
        variables.put("hasCar", applicationDTO.getHasCar());
        variables.put("ipAddress", applicationDTO.getIpAddress());
        variables.put("deviceInfo", applicationDTO.getDeviceInfo());
        variables.put("submitBy", applicationDTO.getSubmitBy());

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                actualProcessKey, application.getApplicationNo(), variables);

        Map<String, Object> result = new HashMap<>();
        result.put("processInstanceId", processInstance.getId());
        result.put("processDefinitionId", processInstance.getProcessDefinitionId());
        result.put("businessKey", processInstance.getBusinessKey());
        result.put("activityId", processInstance.getActivityId());

        log.info("流程实例启动成功, processInstanceId: {}, applicationNo: {}",
                processInstance.getId(), application.getApplicationNo());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startProcessWithContext(String processKey, LoanApplication application,
                                                        LoanApplicationDTO applicationDTO,
                                                        Map<String, Object> processContext) {
        log.info("使用完整上下文启动流程实例, processKey: {}, applicationNo: {}, contextSize: {}",
                processKey, application.getApplicationNo(),
                processContext != null ? processContext.size() : 0);

        String actualProcessKey = (processKey != null && !processKey.isEmpty()) ? processKey : defaultProcessKey;

        Map<String, Object> variables = new HashMap<>();
        if (processContext != null) {
            variables.putAll(processContext);
        }

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                actualProcessKey, application.getApplicationNo(), variables);

        String processInstanceId = processInstance.getId();

        if (processContext != null && !processContext.isEmpty()) {
            processContext.put("processInstanceId", processInstanceId);
            runtimeService.setVariables(processInstanceId, processContext);
            log.debug("上下文已同步到Flowable, processInstanceId: {}, varSize: {}",
                    processInstanceId, processContext.size());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("processInstanceId", processInstanceId);
        result.put("processDefinitionId", processInstance.getProcessDefinitionId());
        result.put("businessKey", processInstance.getBusinessKey());
        result.put("activityId", processInstance.getActivityId());
        result.put("processContext", processContext);

        log.info("流程实例启动成功(带上下文), processInstanceId: {}, applicationNo: {}",
                processInstanceId, application.getApplicationNo());

        return result;
    }

    @Override
    public void suspendProcess(String processInstanceId) {
        log.info("挂起流程实例: {}", processInstanceId);
        runtimeService.suspendProcessInstanceById(processInstanceId);
        log.info("流程实例已挂起: {}", processInstanceId);
    }

    @Override
    public void activateProcess(String processInstanceId) {
        log.info("恢复流程实例: {}", processInstanceId);
        runtimeService.activateProcessInstanceById(processInstanceId);
        log.info("流程实例已恢复: {}", processInstanceId);
    }

    @Override
    public void terminateProcess(String processInstanceId, String reason) {
        log.info("终止流程实例: {}, reason: {}", processInstanceId, reason);
        runtimeService.deleteProcessInstance(processInstanceId, reason);
        log.info("流程实例已终止: {}", processInstanceId);
    }

    @Override
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    @Override
    public void setProcessVariables(String processInstanceId, Map<String, Object> variables) {
        runtimeService.setVariables(processInstanceId, variables);
    }

    @Override
    public String getCurrentActivity(String processInstanceId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return processInstance != null ? processInstance.getActivityId() : null;
    }

    @Override
    public String exportProcessDefinition(String processDefinitionId) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();

        if (processDefinition == null) {
            throw new RuntimeException("流程定义不存在: " + processDefinitionId);
        }

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        BpmnXMLConverter converter = new BpmnXMLConverter();
        byte[] bpmnBytes = converter.convertToXML(bpmnModel);
        return new String(bpmnBytes, StandardCharsets.UTF_8);
    }

    @Override
    public InputStream getProcessDiagram(String processInstanceId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            throw new RuntimeException("流程实例不存在: " + processInstanceId);
        }

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
        List<String> activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);

        ProcessDiagramGenerator diagramGenerator = new DefaultProcessDiagramGenerator();
        return diagramGenerator.generateDiagram(bpmnModel, "png",
                activeActivityIds, activeActivityIds,
                processEngine.getProcessEngineConfiguration().getActivityFontName(),
                processEngine.getProcessEngineConfiguration().getLabelFontName(),
                processEngine.getProcessEngineConfiguration().getAnnotationFontName(),
                processEngine.getProcessEngineConfiguration().getClassLoader(),
                1.0, false);
    }
}
