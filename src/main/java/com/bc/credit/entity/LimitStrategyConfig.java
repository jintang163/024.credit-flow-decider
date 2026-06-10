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
@TableName("limit_strategy_config")
public class LimitStrategyConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String strategyCode;

    private String strategyName;

    private String strategyType;

    private Integer incomeMultiplierMin;

    private Integer incomeMultiplierMax;

    private BigDecimal scoreCoefficientPrime;

    private BigDecimal scoreCoefficientStandard;

    private BigDecimal scoreCoefficientHighRisk;

    private Integer fraudScoreThreshold;

    private BigDecimal fraudDeductionRatio;

    private BigDecimal debtDeductionRatio;

    private BigDecimal minAmount;

    private BigDecimal maxAmount;

    private Integer validityDays;

    private BigDecimal manualReviewThreshold;

    private String groovyScript;

    private String droolsRuleGroup;

    private Integer enabled;

    private Integer defaultStrategy;

    private String version;

    private String remark;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
