package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class RuleHitDetailDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ruleCode;
    private String ruleName;
    private String ruleType;
    private int score;
    private String riskLevel;
    private String action;
    private String detail;
    private long executionTimeMs;

    public RuleHitDetailDTO() {}

    public RuleHitDetailDTO(String ruleCode, String ruleName, String ruleType,
                            int score, String riskLevel, String action, String detail) {
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.ruleType = ruleType;
        this.score = score;
        this.riskLevel = riskLevel;
        this.action = action;
        this.detail = detail;
    }
}
