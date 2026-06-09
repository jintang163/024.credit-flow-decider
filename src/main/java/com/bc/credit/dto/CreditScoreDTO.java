package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class CreditScoreDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;

    private String scorecardVersion;

    private Integer totalScore;

    private String scoreLevel;

    private String scoreSegment;

    private Map<String, Integer> dimensionScores;

    private Boolean pass;

    private String remark;

    private BigDecimal defaultProbability;

    private Map<String, BigDecimal> shapValues;

    private String engineType;

    private String modelVersion;

    private long executionTimeMs;
}
