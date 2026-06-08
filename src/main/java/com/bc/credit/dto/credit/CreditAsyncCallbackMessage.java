package com.bc.credit.dto.credit;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CreditAsyncCallbackMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String requestId;

    private String queryId;

    private String applicationId;

    private String applicationNo;

    private String customerId;

    private StructuredCreditData creditData;

    private boolean success;

    private String errorMsg;

    private LocalDateTime queryTime;

    private LocalDateTime callbackTime;

    private String callbackUrl;
}
