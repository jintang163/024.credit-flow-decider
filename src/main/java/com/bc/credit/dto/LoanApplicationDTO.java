package com.bc.credit.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class LoanApplicationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "客户ID不能为空")
    private String customerId;

    @NotBlank(message = "客户姓名不能为空")
    private String customerName;

    @NotBlank(message = "身份证号不能为空")
    private String idCard;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotNull(message = "申请金额不能为空")
    private BigDecimal loanAmount;

    @NotNull(message = "贷款期限不能为空")
    private Integer loanTerm;

    private String loanPurpose;

    private BigDecimal monthlyIncome;

    private BigDecimal monthlyDebt;

    private Integer age;

    private Integer educationLevel;

    private Integer workYears;

    private Boolean hasHouse;

    private Boolean hasCar;

    private String ipAddress;

    private String deviceInfo;

    private String remark;

    private String submitBy;
}
