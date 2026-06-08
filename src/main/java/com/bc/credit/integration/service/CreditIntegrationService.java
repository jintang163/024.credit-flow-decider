package com.bc.credit.integration.service;

import com.bc.credit.dto.credit.CreditQueryRequest;
import com.bc.credit.dto.credit.CreditQueryResponse;

public interface CreditIntegrationService {

    CreditQueryResponse queryCreditSync(CreditQueryRequest request);

    CreditQueryResponse queryCreditAsync(CreditQueryRequest request);

    CreditQueryResponse queryCredit(CreditQueryRequest request);
}
