package com.bc.credit.service;

import com.bc.credit.dto.LoanApplicationDTO;
import com.bc.credit.entity.LoanApplication;
import java.math.BigDecimal;
import java.util.Map;

public interface LoanApplicationService {

    Map<String, Object> submitApplication(LoanApplicationDTO applicationDTO);

    Map<String, Object> submitApplicationWithIp(LoanApplicationDTO applicationDTO, String ipAddress, String userAgent);

    void validateApplication(LoanApplicationDTO applicationDTO);

    Map<String, Object> buildProcessContext(LoanApplication application, LoanApplicationDTO applicationDTO);

    LoanApplication convertToEntity(LoanApplicationDTO applicationDTO);

    LoanApplication getApplicationById(Long id);

    LoanApplication getApplicationByNo(String applicationNo);

    Map<String, Object> queryApplicationPage(int page, int size, Map<String, Object> params);

    void updateApplicationStatus(Long id, Integer status, String remark);

    Map<String, Object> getApplicationContext(String applicationNo);

    void saveApprovalRecord(Long applicationId, String processInstanceId, String taskId,
                            String taskKey, String taskName, String approveNode, String approver,
                            Integer approveResult, String approveOpinion,
                            BigDecimal approveAmount, Integer approveTerm, BigDecimal interestRate);
}
