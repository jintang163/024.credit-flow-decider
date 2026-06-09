package com.bc.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.credit.entity.FraudBlacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FraudBlacklistMapper extends BaseMapper<FraudBlacklist> {

    @Select("SELECT COUNT(*) FROM fraud_blacklist WHERE deleted = 0 " +
            "AND target_type = #{targetType} AND target_value = #{targetValue} " +
            "AND (expire_time IS NULL OR expire_time > NOW())")
    int countByTarget(@Param("targetType") String targetType, @Param("targetValue") String targetValue);

    @Select("SELECT COUNT(*) FROM fraud_blacklist WHERE deleted = 0 " +
            "AND target_type = #{targetType} AND target_value = #{targetValue} " +
            "AND risk_level = 'HIGH' AND (expire_time IS NULL OR expire_time > NOW())")
    int countHighRiskByTarget(@Param("targetType") String targetType, @Param("targetValue") String targetValue);
}
