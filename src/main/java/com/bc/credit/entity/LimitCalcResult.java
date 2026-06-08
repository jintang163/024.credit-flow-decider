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
@TableName("limit_calc_result")
public class LimitCalcResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long applicationId;

    private String applicationNo;

    private String customerId;

    private BigDecimal incomeAmount;

    private BigDecimal debtRatio;

    private BigDecimal creditLimit;

    private BigDecimal maxAvailableLimit;

    private BigDecimal interestRate;

    private String limitFactors;

    private Integer needManualReview;

    private LocalDateTime calcTime;

    private String remark;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
