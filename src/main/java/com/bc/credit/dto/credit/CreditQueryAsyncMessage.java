package com.bc.credit.dto.credit;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreditQueryAsyncMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskId;

    private String processInstanceId;

    private Long applicationId;

    private String applicationNo;

    private String customerId;

    private String customerName;

    private String idCard;

    private String phone;

    private List<String> dataSources;

    private String queryMode;

    private Integer retryCount;

    private Integer maxRetry;

    private Long timeoutMs;

    private LocalDateTime submitTime;

    private LocalDateTime expireTime;

    private String signalName;

    private String receiveTaskId;

    private String callbackUrl;
}
