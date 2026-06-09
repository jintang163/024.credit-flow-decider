package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("fraud_rule_execution_log")
public class FraudRuleExecutionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long applicationId;

    private String applicationNo;

    private String customerId;

    private String ruleGroup;

    private String ruleVersion;

    private String ruleCode;

    private String ruleName;

    private String ruleType;

    private Boolean hit;

    private Integer hitScore;

    private String riskLevel;

    private String action;

    private String hitDetail;

    private Long executionTimeMs;

    private String engineType;

    private LocalDateTime executionTime;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
