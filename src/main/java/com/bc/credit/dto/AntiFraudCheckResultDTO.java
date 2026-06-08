package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class AntiFraudCheckResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;

    private Integer fraudScore;

    private String riskLevel;

    private List<String> hitRules;

    private Integer ruleCount;

    private Integer checkResult;

    private String remark;
}
