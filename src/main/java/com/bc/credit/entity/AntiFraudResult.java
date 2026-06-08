package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("anti_fraud_result")
public class AntiFraudResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long applicationId;

    private String applicationNo;

    private String customerId;

    private Integer fraudScore;

    private String riskLevel;

    private String hitRules;

    private Integer ruleCount;

    private Integer checkResult;

    private String deviceInfo;

    private String ipAddress;

    private String geoLocation;

    private LocalDateTime checkTime;

    private String remark;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
