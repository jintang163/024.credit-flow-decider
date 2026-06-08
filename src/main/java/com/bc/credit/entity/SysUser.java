package com.bc.credit.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId
    private Long id;

    @TableField("username")
    private String username;

    @TableField("password")
    private String password;

    @TableField("real_name")
    private String realName;

    @TableField("email")
    private String email;

    @TableField("phone")
    private String phone;

    @TableField("org_id")
    private Long orgId;

    @TableField("status")
    private Integer status;

    @TableField("user_type")
    private String userType;

    @TableField("avatar")
    private String avatar;

    @TableField("remark")
    private String remark;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("updated_time")
    private LocalDateTime updatedTime;

    @TableField("deleted")
    private Integer deleted;

    @TableField(exist = false)
    private String orgName;

    @TableField(exist = false)
    private String token;
}
