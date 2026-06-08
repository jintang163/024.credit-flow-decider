package com.bc.credit.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import javax.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@ApiModel("贷款申请数据模型")
public class LoanApplicationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请求ID不能为空，用于幂等控制")
    @Size(max = 128, message = "请求ID长度不能超过128字符")
    @ApiModelProperty(value = "幂等请求ID，用于防止重复提交（必填）", required = true, example = "req_abc123xyz")
    private String requestId;

    @NotBlank(message = "客户ID不能为空")
    @Size(max = 64, message = "客户ID长度不能超过64字符")
    @ApiModelProperty(value = "客户ID", required = true, example = "CUST001")
    private String customerId;

    @NotBlank(message = "客户姓名不能为空")
    @Size(max = 64, message = "客户姓名长度不能超过64字符")
    @ApiModelProperty(value = "客户姓名", required = true, example = "张三")
    private String customerName;

    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$",
            message = "身份证号格式不正确")
    @ApiModelProperty(value = "身份证号", required = true, example = "110101199001011234")
    private String idCard;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @ApiModelProperty(value = "手机号", required = true, example = "13800138000")
    private String phone;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @ApiModelProperty(value = "邮箱", required = true, example = "zhangsan@example.com")
    private String email;

    @NotNull(message = "申请金额不能为空")
    @DecimalMin(value = "1000.00", message = "申请金额不能小于1000元")
    @DecimalMax(value = "500000.00", message = "申请金额不能大于50万元")
    @Digits(integer = 9, fraction = 2, message = "申请金额格式不正确，最多保留2位小数")
    @ApiModelProperty(value = "申请金额(元)", required = true, example = "100000.00")
    private BigDecimal loanAmount;

    @NotNull(message = "贷款期限不能为空")
    @Min(value = 3, message = "贷款期限不能小于3个月")
    @Max(value = 360, message = "贷款期限不能大于360个月")
    @ApiModelProperty(value = "贷款期限(月)", required = true, example = "36")
    private Integer loanTerm;

    @NotBlank(message = "贷款用途不能为空")
    @Size(max = 128, message = "贷款用途长度不能超过128字符")
    @ApiModelProperty(value = "贷款用途", required = true, example = "购买家用轿车")
    private String loanPurpose;

    @NotNull(message = "月收入不能为空")
    @DecimalMin(value = "0.00", message = "月收入不能小于0元")
    @DecimalMax(value = "1000000.00", message = "月收入不能大于100万元")
    @Digits(integer = 9, fraction = 2, message = "月收入格式不正确")
    @ApiModelProperty(value = "月收入(元)", required = true, example = "25000.00")
    private BigDecimal monthlyIncome;

    @NotNull(message = "月负债不能为空")
    @DecimalMin(value = "0.00", message = "月负债不能小于0元")
    @DecimalMax(value = "1000000.00", message = "月负债不能大于100万元")
    @Digits(integer = 9, fraction = 2, message = "月负债格式不正确")
    @ApiModelProperty(value = "月负债(元)", required = true, example = "5000.00")
    private BigDecimal monthlyDebt;

    @NotNull(message = "年龄不能为空")
    @Min(value = 18, message = "申请人年龄不能小于18岁")
    @Max(value = 70, message = "申请人年龄不能大于70岁")
    @ApiModelProperty(value = "年龄", required = true, example = "35")
    private Integer age;

    @NotNull(message = "教育程度不能为空")
    @Min(value = 1, message = "教育程度值范围1-6")
    @Max(value = 6, message = "教育程度值范围1-6")
    @ApiModelProperty(value = "教育程度:1-小学及以下,2-初中,3-高中/中专,4-大专,5-本科,6-硕士及以上", required = true, example = "5")
    private Integer educationLevel;

    @NotNull(message = "工作年限不能为空")
    @Min(value = 0, message = "工作年限不能小于0年")
    @Max(value = 50, message = "工作年限不能大于50年")
    @ApiModelProperty(value = "工作年限(年)", required = true, example = "10")
    private Integer workYears;

    @ApiModelProperty(value = "是否有房产", example = "true")
    private Boolean hasHouse;

    @ApiModelProperty(value = "是否有车辆", example = "false")
    private Boolean hasCar;

    @NotBlank(message = "婚姻状况不能为空")
    @Pattern(regexp = "^(SINGLE|MARRIED|DIVORCED|WIDOWED)$", message = "婚姻状况值不正确")
    @ApiModelProperty(value = "婚姻状况:SINGLE-未婚,MARRIED-已婚,DIVORCED-离异,WIDOWED-丧偶", required = true, example = "MARRIED")
    private String maritalStatus;

    @Size(max = 256, message = "居住地址长度不能超过256字符")
    @ApiModelProperty(value = "居住地址", example = "北京市朝阳区建国路88号")
    private String residentialAddress;

    @Size(max = 128, message = "工作单位长度不能超过128字符")
    @ApiModelProperty(value = "工作单位", example = "北京某科技有限公司")
    private String employer;

    @Size(max = 64, message = "职位长度不能超过64字符")
    @ApiModelProperty(value = "职位", example = "高级工程师")
    private String position;

    @NotBlank(message = "紧急联系人姓名不能为空")
    @Size(max = 64, message = "紧急联系人姓名长度不能超过64字符")
    @ApiModelProperty(value = "紧急联系人姓名", required = true, example = "李四")
    private String contactName;

    @NotBlank(message = "紧急联系人电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "紧急联系人电话格式不正确")
    @ApiModelProperty(value = "紧急联系人电话", required = true, example = "13900139000")
    private String contactPhone;

    @NotBlank(message = "紧急联系人关系不能为空")
    @Pattern(regexp = "^(SPOUSE|PARENT|CHILD|SIBLING|FRIEND|COLLEAGUE|OTHER)$", message = "紧急联系人关系值不正确")
    @ApiModelProperty(value = "紧急联系人关系:SPOUSE-配偶,PARENT-父母,CHILD-子女,SIBLING-兄弟姐妹,FRIEND-朋友,COLLEAGUE-同事,OTHER-其他", required = true, example = "SPOUSE")
    private String contactRelation;

    @Size(max = 128, message = "IP地址长度不能超过128字符")
    @ApiModelProperty(value = "客户端IP地址", example = "192.168.1.100")
    private String ipAddress;

    @Size(max = 512, message = "设备信息长度不能超过512字符")
    @ApiModelProperty(value = "设备指纹信息(JSON格式)", example = "{\"deviceId\":\"xxx\",\"os\":\"Android 12\",\"appVersion\":\"1.0.0\"}")
    private String deviceInfo;

    @Size(max = 256, message = "用户代理长度不能超过256字符")
    @ApiModelProperty(value = "浏览器User-Agent", example = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
    private String userAgent;

    @Size(max = 128, message = "设备ID长度不能超过128字符")
    @ApiModelProperty(value = "设备唯一标识", example = "device_abc123")
    private String deviceId;

    @Size(max = 128, message = "MAC地址长度不能超过128字符")
    @ApiModelProperty(value = "MAC地址", example = "00:1A:2B:3C:4D:5E")
    private String macAddress;

    @Size(max = 64, message = "渠道来源长度不能超过64字符")
    @ApiModelProperty(value = "申请渠道:APP-APP端,H5-移动端网页,PC-PC端网页,OFFLINE-线下", example = "APP")
    private String channel;

    @Size(max = 512, message = "备注长度不能超过512字符")
    @ApiModelProperty(value = "备注信息", example = "客户信用良好")
    private String remark;

    @Size(max = 64, message = "提交人长度不能超过64字符")
    @ApiModelProperty(value = "提交人", example = "customer001")
    private String submitBy;
}
