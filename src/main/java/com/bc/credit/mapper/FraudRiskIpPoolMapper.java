package com.bc.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.credit.entity.FraudRiskIpPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FraudRiskIpPoolMapper extends BaseMapper<FraudRiskIpPool> {

    @Select("SELECT COUNT(*) FROM fraud_risk_ip_pool WHERE deleted = 0 " +
            "AND ip_address = #{ipAddress} " +
            "AND (expire_time IS NULL OR expire_time > NOW())")
    int countByIpAddress(@Param("ipAddress") String ipAddress);

    @Select("SELECT COUNT(*) FROM fraud_risk_ip_pool WHERE deleted = 0 " +
            "AND (ip_address = #{ipAddress} OR #{ipAddress} LIKE CONCAT(ip_segment, '%')) " +
            "AND (expire_time IS NULL OR expire_time > NOW())")
    int countByIpOrSegment(@Param("ipAddress") String ipAddress);
}
