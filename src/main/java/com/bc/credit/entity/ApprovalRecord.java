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
@TableName("approval_record")
public class ApprovalRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long applicationId;

    private String applicationNo;

    private String processInstanceId;

    private String taskId;

    private String taskKey;

    private String taskName;

    private String approveNode;

    private String approver;

    private Integer approveResult;

    private String approveOpinion;

    private BigDecimal approveAmount;

    private Integer approveTerm;

    private BigDecimal interestRate;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long duration;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
