package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("limit_calc_log")
public class LimitCalcLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long applicationId;

    private String applicationNo;

    private String customerId;

    private String strategyCode;

    private String strategyType;

    private String strategyVersion;

    private BigDecimal annualIncome;

    private BigDecimal totalDebt;

    private Integer creditScore;

    private String scoreSegment;

    private Integer fraudScore;

    private BigDecimal loanAmount;

    private Integer incomeMultiplier;

    private BigDecimal scoreCoefficient;

    private BigDecimal baseLimit;

    private BigDecimal fraudDeductionAmount;

    private BigDecimal debtDeductionAmount;

    private BigDecimal beforeConstraintLimit;

    private BigDecimal finalLimit;

    private Integer validityDays;

    private BigDecimal interestRate;

    private String calcSteps;

    private String engineType;

    private Long executionTimeMs;

    private LocalDateTime calcTime;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
