package com.bc.credit.service;

import com.bc.credit.dto.AntiFraudCheckResultDTO;
import com.bc.credit.entity.AntiFraudResult;
import com.bc.credit.entity.LoanApplication;

public interface AntiFraudService {

    AntiFraudCheckResultDTO checkFraud(LoanApplication application, String deviceInfo, String ipAddress);

    AntiFraudResult saveFraudResult(LoanApplication application, AntiFraudCheckResultDTO resultDTO,
                                     String deviceInfo, String ipAddress);
}
