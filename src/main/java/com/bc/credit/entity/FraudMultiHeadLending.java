package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("fraud_multi_head_lending")
public class FraudMultiHeadLending implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String idCard;

    private String institutionCode;

    private String institutionName;

    private String queryType;

    private LocalDateTime queryTime;

    private String source;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
