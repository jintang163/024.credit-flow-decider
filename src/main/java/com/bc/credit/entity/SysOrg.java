package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_org")
public class SysOrg {

    @TableId
    private Long id;

    @TableField("org_code")
    private String orgCode;

    @TableField("org_name")
    private String orgName;

    @TableField("parent_id")
    private Long parentId;

    @TableField("org_level")
    private Integer orgLevel;

    @TableField("org_type")
    private String orgType;

    @TableField("manager")
    private String manager;

    @TableField("phone")
    private String phone;

    @TableField("address")
    private String address;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("status")
    private Integer status;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("updated_time")
    private LocalDateTime updatedTime;

    @TableField("deleted")
    private Integer deleted;
}
