package com.bc.credit.service;

import com.bc.credit.dto.LimitCalcDTO;
import com.bc.credit.entity.LimitCalcResult;
import com.bc.credit.entity.LimitStrategyConfig;
import com.bc.credit.entity.LoanApplication;
import java.math.BigDecimal;
import java.util.List;

public interface LimitCalculationService {

    LimitCalcDTO calculateLimit(LoanApplication application, Integer creditScore,
                                 String riskLevel, BigDecimal monthlyIncome,
                                 BigDecimal monthlyDebt, BigDecimal remainingLoanAmount);

    LimitCalcDTO calculateLimit(LoanApplication application, Integer creditScore,
                                 String riskLevel, BigDecimal monthlyIncome,
                                 BigDecimal monthlyDebt, BigDecimal remainingLoanAmount,
                                 Integer fraudScore, String scoreSegment);

    LimitCalcResult saveLimitResult(LoanApplication application, LimitCalcDTO calcDTO);

    LimitStrategyConfig getActiveStrategy();

    LimitStrategyConfig getStrategyByCode(String strategyCode);

    List<LimitStrategyConfig> listStrategies();

    void refreshStrategyCache();
}
