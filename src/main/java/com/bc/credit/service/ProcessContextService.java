package com.bc.credit.service;

import com.bc.credit.dto.LoanApplicationDTO;
import com.bc.credit.entity.LoanApplication;

import java.util.Map;

public interface ProcessContextService {

    Map<String, Object> buildProcessContext(LoanApplication application, LoanApplicationDTO applicationDTO);

    String generateApplicationNo();

    String generateIdempotentKey(LoanApplicationDTO applicationDTO);

    Map<String, Object> enrichContext(Map<String, Object> context, String key, Object value);

    Object getContextValue(Map<String, Object> context, String key);

    void validateContext(Map<String, Object> context);
}
