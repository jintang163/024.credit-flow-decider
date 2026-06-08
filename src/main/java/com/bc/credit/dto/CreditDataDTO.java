package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CreditDataDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;

    private Integer creditScore;

    private String creditLevel;

    private Integer overdueCount;

    private BigDecimal overdueAmount;

    private BigDecimal totalLoanAmount;

    private BigDecimal remainingLoanAmount;

    private Integer creditCardCount;

    private BigDecimal creditCardLimit;

    private BigDecimal creditCardUsed;

    private String queryResult;

    private Boolean success;

    private String errorMsg;
}
