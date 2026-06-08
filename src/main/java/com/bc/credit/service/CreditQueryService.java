package com.bc.credit.service;

import com.bc.credit.dto.CreditDataDTO;
import com.bc.credit.entity.CreditQueryRecord;
import com.bc.credit.entity.LoanApplication;

public interface CreditQueryService {

    CreditDataDTO queryCredit(LoanApplication application);

    CreditQueryRecord saveCreditRecord(LoanApplication application, CreditDataDTO creditData);
}
