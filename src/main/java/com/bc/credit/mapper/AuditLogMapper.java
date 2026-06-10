package com.bc.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.credit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
