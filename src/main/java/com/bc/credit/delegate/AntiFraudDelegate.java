package com.bc.credit.delegate;

import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.common.enums.FraudCheckResultEnum;
import com.bc.credit.dto.AntiFraudCheckResultDTO;
import com.bc.credit.entity.AntiFraudResult;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.AntiFraudService;
import com.bc.credit.service.ProcessContextService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("antiFraudDelegate")
public class AntiFraudDelegate implements JavaDelegate {

    @Autowired
    private AntiFraudService antiFraudService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private ProcessContextService processContextService;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        Long applicationId = (Long) execution.getVariable(ProcessVariableConstants.APPLICATION_ID);
        String applicationNo = (String) execution.getVariable(ProcessVariableConstants.APPLICATION_NO);

        log.info("执行反欺诈校验服务任务, processInstanceId: {}, applicationId: {}",
                processInstanceId, applicationId);

        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                throw new RuntimeException("贷款申请不存在: " + applicationId);
            }

            String deviceInfo = (String) execution.getVariable(ProcessVariableConstants.DEVICE_INFO);
            String ipAddress = (String) execution.getVariable(ProcessVariableConstants.IP_ADDRESS);

            AntiFraudCheckResultDTO fraudResult = antiFraudService.checkFraud(
                    application, deviceInfo, ipAddress);

            AntiFraudResult result = antiFraudService.saveFraudResult(
                    application, fraudResult, deviceInfo, ipAddress);

            Map<String, Object> variables = new HashMap<>();
            variables.put(ProcessVariableConstants.FRAUD_RESULT, fraudResult.getCheckResult());
            variables.put(ProcessVariableConstants.FRAUD_SCORE, fraudResult.getFraudScore());
            variables.put(ProcessVariableConstants.RISK_LEVEL, fraudResult.getRiskLevel());
            variables.put(ProcessVariableConstants.HIT_RULES, fraudResult.getHitRules());
            variables.put(ProcessVariableConstants.ANTI_FRAUD_RESULT_ID, result.getId());

            processContextService.updateProcessVariables(execution, variables);

            application.setFraudResult(fraudResult.getCheckResult());
            application.setRiskLevel(fraudResult.getRiskLevel());
            loanApplicationMapper.updateById(application);

            log.info("反欺诈校验服务任务执行完成, applicationNo: {}, checkResult: {}",
                    applicationNo, FraudCheckResultEnum.getByCode(fraudResult.getCheckResult()).getDesc());

        } catch (Exception e) {
            log.error("反欺诈校验服务任务执行失败, applicationId: {}", applicationId, e);
            Map<String, Object> errorVariables = new HashMap<>();
            errorVariables.put(ProcessVariableConstants.FRAUD_RESULT, FraudCheckResultEnum.ALERT.getCode());
            errorVariables.put(ProcessVariableConstants.FRAUD_ERROR, e.getMessage());
            processContextService.updateProcessVariables(execution, errorVariables);
            throw new RuntimeException("反欺诈校验失败: " + e.getMessage(), e);
        }
    }
}
