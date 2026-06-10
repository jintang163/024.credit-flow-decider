package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_audit_log")
public class AuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String operationType;

    private String operationModule;

    private String operationDesc;

    private String operator;

    private String targetId;

    private String targetType;

    private String requestParams;

    private String responseResult;

    private String clientIp;

    private Integer success;

    private String errorMsg;

    private Long costMs;

    private LocalDateTime operationTime;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
