package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ScoringFeatureInputDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;

    private String applicationNo;

    private Integer overdueCount;

    private Integer overdueAmount;

    private Integer accountCount;

    private Integer creditCardCount;

    private BigDecimal creditCardUtilization;

    private Integer creditQueryCount3m;

    private Integer maxOverdueDays;

    private Integer age;

    private BigDecimal monthlyIncome;

    private Integer workYears;

    private Integer educationLevel;

    private Boolean hasHouse;

    private Boolean hasCar;

    private String maritalStatus;

    private BigDecimal loanAmount;

    private Integer loanTerm;

    private BigDecimal monthlyDebt;

    private BigDecimal debtRatio;

    private Integer fillDurationSeconds;

    private Integer applyHour;

    private Boolean isWeekendApply;

    private String channel;

    private String deviceId;

    private String ipAddress;
}
