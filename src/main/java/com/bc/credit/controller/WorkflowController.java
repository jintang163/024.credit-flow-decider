package com.bc.credit.controller;

import com.bc.credit.common.Result;
import com.bc.credit.service.WorkflowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Api(tags = "流程定义管理")
@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private RepositoryService repositoryService;

    @PostMapping("/deploy")
    @ApiOperation("部署流程定义（XML字符串）")
    public Result<Map<String, Object>> deployProcess(
            @ApiParam("BPMN XML内容") @RequestParam String bpmnXml,
            @ApiParam("流程名称") @RequestParam String processName,
            @ApiParam("部署人") @RequestParam String deployBy) {
        try {
            String deploymentId = workflowService.deployProcess(bpmnXml, processName, deployBy);

            Map<String, Object> result = new HashMap<>();
            result.put("deploymentId", deploymentId);
            result.put("processName", processName);

            return Result.success("流程部署成功", result);
        } catch (Exception e) {
            log.error("流程部署失败", e);
            return Result.error("流程部署失败: " + e.getMessage());
        }
    }

    @PostMapping("/deploy/file")
    @ApiOperation("部署流程定义（文件上传）")
    public Result<Map<String, Object>> deployProcessByFile(
            @ApiParam("BPMN文件") @RequestParam("file") MultipartFile file,
            @ApiParam("流程名称") @RequestParam String processName,
            @ApiParam("部署人") @RequestParam String deployBy) {
        try {
            String bpmnXml = new String(file.getBytes(), StandardCharsets.UTF_8);
            String deploymentId = workflowService.deployProcess(bpmnXml, processName, deployBy);

            Map<String, Object> result = new HashMap<>();
            result.put("deploymentId", deploymentId);
            result.put("processName", processName);
            result.put("fileName", file.getOriginalFilename());

            return Result.success("流程部署成功", result);
        } catch (Exception e) {
            log.error("流程部署失败", e);
            return Result.error("流程部署失败: " + e.getMessage());
        }
    }

    @GetMapping("/definitions")
    @ApiOperation("查询流程定义列表")
    public Result<List<Map<String, Object>>> getProcessDefinitions(
            @ApiParam("流程Key") @RequestParam(required = false) String processKey,
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页条数") @RequestParam(defaultValue = "10") int size) {
        try {
            List<ProcessDefinition> definitions;
            if (processKey != null && !processKey.isEmpty()) {
                definitions = repositoryService.createProcessDefinitionQuery()
                        .processDefinitionKey(processKey)
                        .orderByProcessDefinitionVersion().desc()
                        .listPage((page - 1) * size, size);
            } else {
                definitions = repositoryService.createProcessDefinitionQuery()
                        .orderByProcessDefinitionKey().asc()
                        .orderByProcessDefinitionVersion().desc()
                        .listPage((page - 1) * size, size);
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (ProcessDefinition definition : definitions) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", definition.getId());
                map.put("key", definition.getKey());
                map.put("name", definition.getName());
                map.put("version", definition.getVersion());
                map.put("deploymentId", definition.getDeploymentId());
                map.put("suspended", definition.isSuspended());
                map.put("description", definition.getDescription());
                result.add(map);
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("查询流程定义失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/deployments")
    @ApiOperation("查询部署列表")
    public Result<List<Map<String, Object>>> getDeployments(
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页条数") @RequestParam(defaultValue = "10") int size) {
        try {
            List<Deployment> deployments = repositoryService.createDeploymentQuery()
                    .orderByDeploymentTime().desc()
                    .listPage((page - 1) * size, size);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Deployment deployment : deployments) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", deployment.getId());
                map.put("name", deployment.getName());
                map.put("deploymentTime", deployment.getDeploymentTime());
                map.put("category", deployment.getCategory());
                result.add(map);
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("查询部署列表失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/suspend/{processInstanceId}")
    @ApiOperation("挂起流程实例")
    public Result<Void> suspendProcess(
            @ApiParam("流程实例ID") @PathVariable String processInstanceId) {
        try {
            workflowService.suspendProcess(processInstanceId);
            return Result.success("流程已挂起", null);
        } catch (Exception e) {
            log.error("挂起流程失败", e);
            return Result.error("挂起失败: " + e.getMessage());
        }
    }

    @PostMapping("/activate/{processInstanceId}")
    @ApiOperation("恢复流程实例")
    public Result<Void> activateProcess(
            @ApiParam("流程实例ID") @PathVariable String processInstanceId) {
        try {
            workflowService.activateProcess(processInstanceId);
            return Result.success("流程已恢复", null);
        } catch (Exception e) {
            log.error("恢复流程失败", e);
            return Result.error("恢复失败: " + e.getMessage());
        }
    }

    @PostMapping("/terminate/{processInstanceId}")
    @ApiOperation("终止流程实例")
    public Result<Void> terminateProcess(
            @ApiParam("流程实例ID") @PathVariable String processInstanceId,
            @ApiParam("终止原因") @RequestParam(required = false, defaultValue = "手动终止") String reason) {
        try {
            workflowService.terminateProcess(processInstanceId, reason);
            return Result.success("流程已终止", null);
        } catch (Exception e) {
            log.error("终止流程失败", e);
            return Result.error("终止失败: " + e.getMessage());
        }
    }

    @GetMapping("/variables/{processInstanceId}")
    @ApiOperation("查询流程变量")
    public Result<Map<String, Object>> getProcessVariables(
            @ApiParam("流程实例ID") @PathVariable String processInstanceId) {
        try {
            Map<String, Object> variables = workflowService.getProcessVariables(processInstanceId);
            return Result.success(variables);
        } catch (Exception e) {
            log.error("查询流程变量失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/variables/{processInstanceId}")
    @ApiOperation("设置流程变量")
    public Result<Void> setProcessVariables(
            @ApiParam("流程实例ID") @PathVariable String processInstanceId,
            @RequestBody Map<String, Object> variables) {
        try {
            workflowService.setProcessVariables(processInstanceId, variables);
            return Result.success("变量已设置", null);
        } catch (Exception e) {
            log.error("设置流程变量失败", e);
            return Result.error("设置失败: " + e.getMessage());
        }
    }

    @GetMapping("/activity/{processInstanceId}")
    @ApiOperation("查询当前节点")
    public Result<Map<String, Object>> getCurrentActivity(
            @ApiParam("流程实例ID") @PathVariable String processInstanceId) {
        try {
            String activityId = workflowService.getCurrentActivity(processInstanceId);
            Map<String, Object> result = new HashMap<>();
            result.put("activityId", activityId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询当前节点失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/export/{processDefinitionId}")
    @ApiOperation("导出流程定义XML")
    public void exportProcessDefinition(
            @ApiParam("流程定义ID") @PathVariable String processDefinitionId,
            HttpServletResponse response) {
        try {
            String bpmnXml = workflowService.exportProcessDefinition(processDefinitionId);
            ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId).singleResult();

            String fileName = (definition.getName() != null ? definition.getName() : "process")
                    + ".bpmn20.xml";

            response.setContentType("application/xml;charset=UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()));

            OutputStream out = response.getOutputStream();
            out.write(bpmnXml.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        } catch (Exception e) {
            log.error("导出流程定义失败", e);
        }
    }

    @GetMapping("/diagram/{processInstanceId}")
    @ApiOperation("查看流程图")
    public void getProcessDiagram(
            @ApiParam("流程实例ID") @PathVariable String processInstanceId,
            HttpServletResponse response) {
        try (InputStream in = workflowService.getProcessDiagram(processInstanceId);
             OutputStream out = response.getOutputStream()) {

            response.setContentType("image/png");

            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
        } catch (Exception e) {
            log.error("获取流程图失败", e);
        }
    }

    @DeleteMapping("/deployment/{deploymentId}")
    @ApiOperation("删除部署")
    public Result<Void> deleteDeployment(
            @ApiParam("部署ID") @PathVariable String deploymentId,
            @ApiParam("是否级联删除") @RequestParam(defaultValue = "true") boolean cascade) {
        try {
            repositoryService.deleteDeployment(deploymentId, cascade);
            return Result.success("部署已删除", null);
        } catch (Exception e) {
            log.error("删除部署失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/suspend/definition/{processDefinitionId}")
    @ApiOperation("挂起流程定义")
    public Result<Void> suspendProcessDefinition(
            @ApiParam("流程定义ID") @PathVariable String processDefinitionId) {
        try {
            repositoryService.suspendProcessDefinitionById(processDefinitionId, true, null);
            return Result.success("流程定义已挂起", null);
        } catch (Exception e) {
            log.error("挂起流程定义失败", e);
            return Result.error("挂起失败: " + e.getMessage());
        }
    }

    @PostMapping("/activate/definition/{processDefinitionId}")
    @ApiOperation("激活流程定义")
    public Result<Void> activateProcessDefinition(
            @ApiParam("流程定义ID") @PathVariable String processDefinitionId) {
        try {
            repositoryService.activateProcessDefinitionById(processDefinitionId, true, null);
            return Result.success("流程定义已激活", null);
        } catch (Exception e) {
            log.error("激活流程定义失败", e);
            return Result.error("激活失败: " + e.getMessage());
        }
    }
}
