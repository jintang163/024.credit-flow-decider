package com.bc.credit.delegate;

import com.bc.credit.common.enums.FraudCheckResultEnum;
import com.bc.credit.dto.AntiFraudCheckResultDTO;
import com.bc.credit.entity.AntiFraudResult;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.AntiFraudService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component("antiFraudDelegate")
public class AntiFraudDelegate implements JavaDelegate {

    @Autowired
    private AntiFraudService antiFraudService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        Long applicationId = (Long) execution.getVariable("applicationId");
        String applicationNo = (String) execution.getVariable("applicationNo");

        log.info("执行反欺诈校验服务任务, processInstanceId: {}, applicationId: {}",
                processInstanceId, applicationId);

        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                throw new RuntimeException("贷款申请不存在: " + applicationId);
            }

            String deviceInfo = (String) execution.getVariable("deviceInfo");
            String ipAddress = (String) execution.getVariable("ipAddress");

            AntiFraudCheckResultDTO fraudResult = antiFraudService.checkFraud(
                    application, deviceInfo, ipAddress);

            AntiFraudResult result = antiFraudService.saveFraudResult(
                    application, fraudResult, deviceInfo, ipAddress);

            execution.setVariable("fraudResult", fraudResult.getCheckResult());
            execution.setVariable("fraudScore", fraudResult.getFraudScore());
            execution.setVariable("riskLevel", fraudResult.getRiskLevel());
            execution.setVariable("hitRules", fraudResult.getHitRules());
            execution.setVariable("antiFraudResultId", result.getId());

            application.setFraudResult(fraudResult.getCheckResult());
            application.setRiskLevel(fraudResult.getRiskLevel());
            loanApplicationMapper.updateById(application);

            log.info("反欺诈校验服务任务执行完成, applicationNo: {}, checkResult: {}",
                    applicationNo, FraudCheckResultEnum.getByCode(fraudResult.getCheckResult()).getDesc());

        } catch (Exception e) {
            log.error("反欺诈校验服务任务执行失败, applicationId: {}", applicationId, e);
            execution.setVariable("fraudResult", FraudCheckResultEnum.ALERT.getCode());
            execution.setVariable("fraudError", e.getMessage());
            throw new RuntimeException("反欺诈校验失败: " + e.getMessage(), e);
        }
    }
}
