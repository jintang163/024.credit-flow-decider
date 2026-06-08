package com.bc.credit.init;

import com.bc.credit.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class ProcessDeploymentInitializer implements CommandLineRunner {

    @Autowired
    private WorkflowService workflowService;

    @Value("${credit.workflow.auto-deploy:true}")
    private boolean autoDeploy;

    @Override
    public void run(String... args) {
        if (!autoDeploy) {
            log.info("流程自动部署已禁用，跳过初始部署");
            return;
        }

        try {
            log.info("开始自动部署贷款审批流程...");

            ClassPathResource resource = new ClassPathResource("processes/credit-approval-process.bpmn20.xml");
            String bpmnXml = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);

            String deploymentId = workflowService.deployProcess(
                    bpmnXml, "贷款审批流程", "SYSTEM");

            log.info("贷款审批流程部署成功, deploymentId: {}", deploymentId);

        } catch (Exception e) {
            log.error("贷款审批流程部署失败", e);
        }
    }
}
