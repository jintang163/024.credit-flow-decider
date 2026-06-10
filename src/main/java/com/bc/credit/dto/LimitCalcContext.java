package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class LimitCalcContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;

    private String applicationNo;

    private BigDecimal annualIncome;

    private BigDecimal monthlyIncome;

    private BigDecimal monthlyDebt;

    private BigDecimal totalDebt;

    private BigDecimal loanAmount;

    private Integer loanTerm;

    private Integer creditScore;

    private String scoreSegment;

    private Integer fraudScore;

    private String fraudRiskLevel;

    private String riskLevel;

    private BigDecimal debtRatio;

    private String strategyCode;

    private String strategyType;

    private BigDecimal incomeMultiplier;

    private BigDecimal scoreCoefficient;

    private BigDecimal baseLimit;

    private BigDecimal fraudDeductionRatio;

    private BigDecimal fraudDeductionAmount;

    private BigDecimal debtDeductionRatio;

    private Integer fraudScoreThreshold;

    private BigDecimal manualReviewThreshold;

    private BigDecimal debtDeductionAmount;

    private BigDecimal beforeConstraintLimit;

    private BigDecimal finalLimit;

    private BigDecimal minAmount;

    private BigDecimal maxAmount;

    private Integer validityDays;

    private BigDecimal interestRate;

    private Boolean needManualReview;

    private String remark;
}
