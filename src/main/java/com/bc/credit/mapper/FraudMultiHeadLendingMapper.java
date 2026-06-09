package com.bc.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.credit.entity.FraudMultiHeadLending;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FraudMultiHeadLendingMapper extends BaseMapper<FraudMultiHeadLending> {

    @Select("SELECT COUNT(DISTINCT institution_code) FROM fraud_multi_head_lending WHERE deleted = 0 " +
            "AND id_card = #{idCard} " +
            "AND query_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int countInstitutionsByIdCard(@Param("idCard") String idCard, @Param("days") int days);

    @Select("SELECT COUNT(DISTINCT institution_code) FROM fraud_multi_head_lending WHERE deleted = 0 " +
            "AND id_card = #{idCard} " +
            "AND query_type = #{queryType} " +
            "AND query_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int countInstitutionsByIdCardAndType(@Param("idCard") String idCard,
                                          @Param("queryType") String queryType,
                                          @Param("days") int days);
}
