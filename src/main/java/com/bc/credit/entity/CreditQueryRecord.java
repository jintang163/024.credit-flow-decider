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
@TableName("credit_query_record")
public class CreditQueryRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long applicationId;

    private String applicationNo;

    private String customerId;

    private String queryType;

    private String queryChannel;

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

    private LocalDateTime queryTime;

    private Integer success;

    private String errorMsg;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
