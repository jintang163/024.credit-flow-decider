package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("fraud_rule_hit_stats")
public class FraudRuleHitStats implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String ruleCode;

    private String ruleName;

    private String ruleGroup;

    private String statsDate;

    private Long executeCount;

    private Long hitCount;

    private java.math.BigDecimal hitRate;

    private Long avgScore;

    private Long rejectCount;

    private Long alertCount;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
