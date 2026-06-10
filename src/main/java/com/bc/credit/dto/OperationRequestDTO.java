package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class OperationRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String processInstanceId;

    private String operationType;

    private String targetNodeId;

    private String reason;

    private String operator;

    private String testData;
}
