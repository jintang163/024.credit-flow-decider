package com.bc.credit.service;

import com.bc.credit.dto.LimitCalcDTO;
import com.bc.credit.entity.LimitCalcResult;
import com.bc.credit.entity.LoanApplication;
import java.math.BigDecimal;

public interface LimitCalculationService {

    LimitCalcDTO calculateLimit(LoanApplication application, Integer creditScore,
                                 String riskLevel, BigDecimal monthlyIncome,
                                 BigDecimal monthlyDebt, BigDecimal remainingLoanAmount);

    LimitCalcResult saveLimitResult(LoanApplication application, LimitCalcDTO calcDTO);
}
