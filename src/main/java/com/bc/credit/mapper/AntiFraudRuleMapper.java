package com.bc.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.credit.entity.AntiFraudRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AntiFraudRuleMapper extends BaseMapper<AntiFraudRule> {

    @Select("SELECT * FROM anti_fraud_rule WHERE enabled = 1 AND deleted = 0 ORDER BY sort_order ASC")
    List<AntiFraudRule> getAllEnabledRules();
}
