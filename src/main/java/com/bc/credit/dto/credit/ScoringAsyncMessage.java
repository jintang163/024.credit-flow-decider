package com.bc.credit.dto.credit;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ScoringAsyncMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskId;

    private String processInstanceId;

    private Long applicationId;

    private String applicationNo;

    private String customerId;

    private String customerName;

    private Integer creditScore;

    private Integer overdueCount;

    private BigDecimal remainingLoanAmount;

    private Map<String, Object> extraInfo;

    private Integer retryCount;

    private Integer maxRetry;

    private Long timeoutMs;

    private LocalDateTime submitTime;

    private LocalDateTime expireTime;

    private String signalName;

    private String receiveTaskId;

    private String engineType;

    private String modelVersion;
}
