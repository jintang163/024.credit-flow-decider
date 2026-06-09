package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("fraud_rule_ab_test")
public class FraudRuleABTest implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String testName;

    private String testDesc;

    private String groupAName;

    private String groupARuleContent;

    private String groupBName;

    private String groupBRuleContent;

    private Integer trafficRatioA;

    private Integer trafficRatioB;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer totalSamples;

    private Integer groupASamples;

    private Integer groupBSamples;

    private Integer groupARejectCount;

    private Integer groupBRejectCount;

    private Integer groupAAlertCount;

    private Integer groupBAlertCount;

    private String createdBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
