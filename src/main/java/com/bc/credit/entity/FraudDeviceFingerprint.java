package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("fraud_device_fingerprint")
public class FraudDeviceFingerprint implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String deviceId;

    private String idCard;

    private String phone;

    private String customerId;

    private String ipAddress;

    private String appVersion;

    private String osType;

    private LocalDateTime firstSeenTime;

    private LocalDateTime lastSeenTime;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
