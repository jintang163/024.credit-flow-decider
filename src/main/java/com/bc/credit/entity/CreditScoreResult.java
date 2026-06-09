package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("credit_score_result")
public class CreditScoreResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long applicationId;

    private String applicationNo;

    private String customerId;

    private String scorecardVersion;

    private Integer totalScore;

    private String scoreLevel;

    private String dimensionScores;

    private BigDecimal defaultProbability;

    private String scoreSegment;

    private String shapValues;

    private String engineType;

    private String modelVersion;

    private Integer pass;

    private LocalDateTime scoreTime;

    private String remark;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
