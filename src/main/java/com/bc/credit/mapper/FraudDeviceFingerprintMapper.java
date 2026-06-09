package com.bc.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.credit.entity.FraudDeviceFingerprint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FraudDeviceFingerprintMapper extends BaseMapper<FraudDeviceFingerprint> {

    @Select("SELECT COUNT(DISTINCT id_card) FROM fraud_device_fingerprint WHERE deleted = 0 " +
            "AND device_id = #{deviceId} " +
            "AND last_seen_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)")
    int countAssocIdCardsIn1Hour(@Param("deviceId") String deviceId);

    @Select("SELECT COUNT(DISTINCT device_id) FROM fraud_device_fingerprint WHERE deleted = 0 " +
            "AND id_card = #{idCard} " +
            "AND last_seen_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)")
    int countDevicesByIdCard24h(@Param("idCard") String idCard);
}
