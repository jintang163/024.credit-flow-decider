package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.Map;

@Data
public class CreditScoreDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;

    private String scorecardVersion;

    private Integer totalScore;

    private String scoreLevel;

    private Map<String, Integer> dimensionScores;

    private Boolean pass;

    private String remark;
}
