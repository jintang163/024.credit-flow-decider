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
@TableName("loan_application")
public class LoanApplication implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String applicationNo;

    private String processInstanceId;

    private String customerId;

    private String customerName;

    private String idCard;

    private String phone;

    private String email;

    private BigDecimal loanAmount;

    private Integer loanTerm;

    private String loanPurpose;

    private Integer applicationStatus;

    private BigDecimal monthlyIncome;

    private BigDecimal monthlyDebt;

    private Integer age;

    private Integer educationLevel;

    private Integer workYears;

    private Boolean hasHouse;

    private Boolean hasCar;

    private String maritalStatus;

    private String residentialAddress;

    private String employer;

    private String position;

    private String contactName;

    private String contactPhone;

    private String contactRelation;

    private String channel;

    private String deviceId;

    private String macAddress;

    private String userAgent;

    private BigDecimal approvedAmount;

    private Integer approvedTerm;

    private BigDecimal interestRate;

    private String riskLevel;

    private Integer creditScore;

    private Integer fraudResult;

    private LocalDateTime submitTime;

    private LocalDateTime approveTime;

    private String rejectReason;

    private Integer returnCount;

    private String returnReason;

    private String remark;

    private String createdBy;

    private LocalDateTime createdTime;

    private String updatedBy;

    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
