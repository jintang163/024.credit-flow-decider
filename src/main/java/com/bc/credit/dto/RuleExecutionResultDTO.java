package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class RuleExecutionResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;
    private String applicationNo;
    private boolean hitFlag;
    private int riskScore;
    private String riskLevel;
    private String checkResult;
    private List<RuleHitDetailDTO> hitDetails;
    private int hitRuleCount;
    private boolean hardReject;
    private boolean needManualReview;
    private BigDecimal adjustedLimitRatio;
    private String ruleGroup;
    private String ruleVersion;
    private long totalExecutionTimeMs;
    private String remark;
}
