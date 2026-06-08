package com.bc.credit.integration.adapter;

import com.bc.credit.common.enums.CreditDataSourceType;
import com.bc.credit.dto.credit.CreditQueryRequest;
import com.bc.credit.dto.credit.StructuredCreditData;

public interface CreditDataAdapter {

    CreditDataSourceType getDataSourceType();

    StructuredCreditData query(CreditQueryRequest request) throws Exception;

    boolean isAvailable();

    String getDataSourceName();
}
