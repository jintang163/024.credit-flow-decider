package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class LimitCalcDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;

    private BigDecimal incomeAmount;

    private BigDecimal debtRatio;

    private BigDecimal creditLimit;

    private BigDecimal maxAvailableLimit;

    private BigDecimal interestRate;

    private Map<String, Object> limitFactors;

    private Boolean needManualReview;

    private String remark;
}
