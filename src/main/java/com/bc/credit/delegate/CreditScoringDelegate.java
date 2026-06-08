package com.bc.credit.delegate;

import com.bc.credit.dto.CreditScoreDTO;
import com.bc.credit.entity.CreditScoreResult;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.CreditScoringService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("creditScoringDelegate")
public class CreditScoringDelegate implements JavaDelegate {

    @Autowired
    private CreditScoringService creditScoringService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        Long applicationId = (Long) execution.getVariable("applicationId");
        String applicationNo = (String) execution.getVariable("applicationNo");

        log.info("执行信用评分服务任务, processInstanceId: {}, applicationId: {}",
                processInstanceId, applicationId);

        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                throw new RuntimeException("贷款申请不存在: " + applicationId);
            }

            Integer creditScore = (Integer) execution.getVariable("creditScore");
            Integer overdueCount = (Integer) execution.getVariable("overdueCount");
            BigDecimal remainingLoanAmount = (BigDecimal) execution.getVariable("remainingLoanAmount");

            Map<String, Object> extraInfo = new HashMap<>();
            extraInfo.put("monthlyIncome", execution.getVariable("monthlyIncome"));
            extraInfo.put("monthlyDebt", execution.getVariable("monthlyDebt"));
            extraInfo.put("age", execution.getVariable("age"));
            extraInfo.put("educationLevel", execution.getVariable("educationLevel"));
            extraInfo.put("workYears", execution.getVariable("workYears"));
            extraInfo.put("hasHouse", execution.getVariable("hasHouse"));
            extraInfo.put("hasCar", execution.getVariable("hasCar"));

            CreditScoreDTO scoreDTO = creditScoringService.calculateScore(
                    application, creditScore, overdueCount, remainingLoanAmount, extraInfo);

            CreditScoreResult scoreResult = creditScoringService.saveScoreResult(application, scoreDTO);

            execution.setVariable("creditScore", scoreDTO.getTotalScore());
            execution.setVariable("scoreLevel", scoreDTO.getScoreLevel());
            execution.setVariable("scorePass", scoreDTO.getPass());
            execution.setVariable("dimensionScores", scoreDTO.getDimensionScores());
            execution.setVariable("scoreResultId", scoreResult.getId());

            application.setCreditScore(scoreDTO.getTotalScore());
            loanApplicationMapper.updateById(application);

            log.info("信用评分服务任务执行完成, applicationNo: {}, totalScore: {}, pass: {}",
                    applicationNo, scoreDTO.getTotalScore(), scoreDTO.getPass());

        } catch (Exception e) {
            log.error("信用评分服务任务执行失败, applicationId: {}", applicationId, e);
            execution.setVariable("creditScore", 0);
            execution.setVariable("scorePass", false);
            execution.setVariable("scoreError", e.getMessage());
            throw new RuntimeException("信用评分失败: " + e.getMessage(), e);
        }
    }
}
