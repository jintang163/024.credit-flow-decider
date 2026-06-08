package com.bc.credit.init;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.entity.ProcessDefinitionVersion;
import com.bc.credit.mapper.ProcessDefinitionVersionMapper;
import com.bc.credit.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Component
public class ProcessDeploymentInitializer implements CommandLineRunner {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ProcessDefinitionVersionMapper versionMapper;

    @Value("${credit.workflow.auto-deploy:true}")
    private boolean autoDeploy;

    @Value("${credit.workflow.check-before-deploy:true}")
    private boolean checkBeforeDeploy;

    @Override
    public void run(String... args) {
        if (!autoDeploy) {
            log.info("流程自动部署已禁用，跳过初始部署");
            return;
        }

        try {
            log.info("开始检查并部署贷款审批流程...");

            ClassPathResource resource = new ClassPathResource("processes/credit-approval-process.bpmn20.xml");
            String bpmnXml = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);

            String processKey = "credit-approval-process";
            String processName = "贷款审批流程";

            if (checkBeforeDeploy) {
                String bpmnHash = DigestUtils.md5Hex(bpmnXml);
                log.debug("BPMN文件MD5哈希值: {}", bpmnHash);

                ProcessDefinitionVersion latestVersion = versionMapper.getLatestActiveVersion(processKey);

                if (latestVersion != null) {
                    String existingHash = latestVersion.getBpmnXml() != null
                            ? DigestUtils.md5Hex(latestVersion.getBpmnXml())
                            : null;

                    if (bpmnHash.equals(existingHash)) {
                        log.info("检测到流程定义未发生变化，跳过部署。当前版本: v{}, Flowable定义ID: {}",
                                latestVersion.getVersion(), latestVersion.getFlowableDefinitionId());
                        return;
                    }

                    log.info("检测到流程定义已变化，旧版本: v{}, 开始部署新版本...", latestVersion.getVersion());
                } else {
                    log.info("未检测到已部署版本，开始首次部署...");
                }
            }

            String deploymentId = workflowService.deployProcess(bpmnXml, processName, "SYSTEM");
            log.info("贷款审批流程部署成功, deploymentId: {}", deploymentId);

            if (checkBeforeDeploy) {
                saveVersionRecord(processKey, processName, deploymentId, bpmnXml);
            }

        } catch (Exception e) {
            log.error("贷款审批流程部署失败", e);
        }
    }

    private void saveVersionRecord(String processKey, String processName, String deploymentId, String bpmnXml) {
        try {
            String flowableDefinitionId = workflowService.getProcessDefinitionId(processKey);

            ProcessDefinitionVersion latestVersion = versionMapper.getLatestActiveVersion(processKey);
            int nextVersion = latestVersion != null ? latestVersion.getVersion() + 1 : 1;

            ProcessDefinitionVersion version = new ProcessDefinitionVersion();
            version.setId(IdWorker.getId());
            version.setProcessKey(processKey);
            version.setProcessName(processName);
            version.setFlowableDeploymentId(deploymentId);
            version.setFlowableDefinitionId(flowableDefinitionId);
            version.setVersion(nextVersion);
            version.setBpmnXml(bpmnXml);
            version.setStatus(1);
            version.setDeployTime(LocalDateTime.now());
            version.setDeployBy("SYSTEM");
            version.setRemark("系统启动自动部署 v" + nextVersion);
            version.setCreatedTime(LocalDateTime.now());
            version.setUpdatedTime(LocalDateTime.now());
            version.setDeleted(0);

            versionMapper.insert(version);

            if (latestVersion != null) {
                versionMapper.disableVersion(latestVersion.getId());
                log.info("已禁用旧版本 v{}", latestVersion.getVersion());
            }

            log.info("流程版本记录已保存: v{}, Flowable定义ID: {}", nextVersion, flowableDefinitionId);

        } catch (Exception e) {
            log.error("保存流程版本记录失败", e);
        }
    }
}
