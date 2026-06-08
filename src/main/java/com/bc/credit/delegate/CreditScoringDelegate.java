package com.bc.credit.delegate;

import com.bc.credit.common.ProcessVariableConstants;
import com.bc.credit.dto.CreditScoreDTO;
import com.bc.credit.entity.CreditScoreResult;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.CreditScoringService;
import com.bc.credit.service.ProcessContextService;
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

    @Autowired
    private ProcessContextService processContextService;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        Long applicationId = (Long) execution.getVariable(ProcessVariableConstants.APPLICATION_ID);
        String applicationNo = (String) execution.getVariable(ProcessVariableConstants.APPLICATION_NO);

        log.info("执行信用评分服务任务, processInstanceId: {}, applicationId: {}",
                processInstanceId, applicationId);

        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                throw new RuntimeException("贷款申请不存在: " + applicationId);
            }

            Integer creditScore = (Integer) execution.getVariable(ProcessVariableConstants.CREDIT_SCORE);
            Integer overdueCount = (Integer) execution.getVariable(ProcessVariableConstants.OVERDUE_COUNT);
            BigDecimal remainingLoanAmount = (BigDecimal) execution.getVariable(ProcessVariableConstants.REMAINING_LOAN_AMOUNT);

            Map<String, Object> extraInfo = new HashMap<>();
            extraInfo.put(ProcessVariableConstants.MONTHLY_INCOME, execution.getVariable(ProcessVariableConstants.MONTHLY_INCOME));
            extraInfo.put(ProcessVariableConstants.MONTHLY_DEBT, execution.getVariable(ProcessVariableConstants.MONTHLY_DEBT));
            extraInfo.put(ProcessVariableConstants.AGE, execution.getVariable(ProcessVariableConstants.AGE));
            extraInfo.put(ProcessVariableConstants.EDUCATION_LEVEL, execution.getVariable(ProcessVariableConstants.EDUCATION_LEVEL));
            extraInfo.put(ProcessVariableConstants.WORK_YEARS, execution.getVariable(ProcessVariableConstants.WORK_YEARS));
            extraInfo.put(ProcessVariableConstants.HAS_HOUSE, execution.getVariable(ProcessVariableConstants.HAS_HOUSE));
            extraInfo.put(ProcessVariableConstants.HAS_CAR, execution.getVariable(ProcessVariableConstants.HAS_CAR));

            CreditScoreDTO scoreDTO = creditScoringService.calculateScore(
                    application, creditScore, overdueCount, remainingLoanAmount, extraInfo);

            CreditScoreResult scoreResult = creditScoringService.saveScoreResult(application, scoreDTO);

            Map<String, Object> variables = new HashMap<>();
            variables.put(ProcessVariableConstants.CREDIT_SCORE, scoreDTO.getTotalScore());
            variables.put(ProcessVariableConstants.SCORE_LEVEL, scoreDTO.getScoreLevel());
            variables.put(ProcessVariableConstants.SCORE_PASS, scoreDTO.getPass());
            variables.put(ProcessVariableConstants.DIMENSION_SCORES, scoreDTO.getDimensionScores());
            variables.put(ProcessVariableConstants.SCORE_RESULT_ID, scoreResult.getId());

            processContextService.updateProcessVariables(execution, variables);

            application.setCreditScore(scoreDTO.getTotalScore());
            loanApplicationMapper.updateById(application);

            log.info("信用评分服务任务执行完成, applicationNo: {}, totalScore: {}, pass: {}",
                    applicationNo, scoreDTO.getTotalScore(), scoreDTO.getPass());

        } catch (Exception e) {
            log.error("信用评分服务任务执行失败, applicationId: {}", applicationId, e);
            Map<String, Object> errorVariables = new HashMap<>();
            errorVariables.put(ProcessVariableConstants.CREDIT_SCORE, 0);
            errorVariables.put(ProcessVariableConstants.SCORE_PASS, false);
            errorVariables.put(ProcessVariableConstants.SCORE_ERROR, e.getMessage());
            processContextService.updateProcessVariables(execution, errorVariables);
            throw new RuntimeException("信用评分失败: " + e.getMessage(), e);
        }
    }
}
