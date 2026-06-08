package com.bc.credit.service;

import com.bc.credit.dto.CreditScoreDTO;
import com.bc.credit.entity.CreditScoreResult;
import com.bc.credit.entity.LoanApplication;
import java.math.BigDecimal;
import java.util.Map;

public interface CreditScoringService {

    CreditScoreDTO calculateScore(LoanApplication application, Integer creditScore,
                                   Integer overdueCount, BigDecimal remainingLoanAmount,
                                   Map<String, Object> extraInfo);

    CreditScoreResult saveScoreResult(LoanApplication application, CreditScoreDTO scoreDTO);
}
