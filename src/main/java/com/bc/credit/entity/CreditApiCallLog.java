package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("credit_api_call_log")
public class CreditApiCallLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String queryId;

    private String requestId;

    private Long applicationId;

    private String applicationNo;

    private String customerId;

    private String dataSource;

    private String dataSourceName;

    private String queryMode;

    private String requestBody;

    private String responseBody;

    private Long costMs;

    private Integer retryCount;

    private Integer success;

    private String errorCode;

    private String errorMsg;

    private String qualityTag;

    private String circuitBreakerStatus;

    private LocalDateTime callTime;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
