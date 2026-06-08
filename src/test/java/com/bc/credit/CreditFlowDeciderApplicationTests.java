package com.bc.credit;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.dto.LoanApplicationDTO;
import com.bc.credit.service.LoanApplicationService;
import com.bc.credit.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@SpringBootTest
class CreditFlowDeciderApplicationTests {

    @Autowired
    private LoanApplicationService loanApplicationService;

    @Autowired
    private WorkflowService workflowService;

    @Test
    void contextLoads() {
        log.info("Spring Boot 上下文加载成功");
    }

    @Test
    void testSubmitApplication() {
        LoanApplicationDTO dto = new LoanApplicationDTO();
        dto.setCustomerId("CUST" + System.currentTimeMillis());
        dto.setCustomerName("张三");
        dto.setIdCard("110101199001011234");
        dto.setPhone("13800138000");
        dto.setLoanAmount(new BigDecimal("150000"));
        dto.setLoanTerm(36);
        dto.setLoanPurpose("装修贷款");
        dto.setMonthlyIncome(new BigDecimal("25000"));
        dto.setMonthlyDebt(new BigDecimal("5000"));
        dto.setAge(35);
        dto.setEducationLevel(4);
        dto.setWorkYears(10);
        dto.setHasHouse(true);
        dto.setHasCar(true);
        dto.setIpAddress("192.168.1.100");
        dto.setDeviceInfo("{\"deviceId\":\"DEV001\",\"deviceType\":\"MOBILE\"}");
        dto.setSubmitBy("test_user");

        log.info("测试提交贷款申请, 请求参数: {}", JSON.toJSONString(dto));

        Map<String, Object> result = loanApplicationService.submitApplication(dto);

        log.info("测试提交贷款申请成功, 返回结果: {}", JSON.toJSONString(result));

        String processInstanceId = (String) result.get("processInstanceId");

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, Object> variables = workflowService.getProcessVariables(processInstanceId);
        log.info("测试查询流程变量, 变量信息: {}", JSON.toJSONString(variables));

        String currentActivity = workflowService.getCurrentActivity(processInstanceId);
        log.info("测试查询当前节点, 当前节点: {}", currentActivity);
    }
}
