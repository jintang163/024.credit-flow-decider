package com.bc.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.credit.entity.ScorecardRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ScorecardRuleMapper extends BaseMapper<ScorecardRule> {

    @Select("SELECT * FROM scorecard_rule WHERE dimension_code = #{dimensionCode} AND scorecard_version = #{version} AND enabled = 1 AND deleted = 0 ORDER BY sort_order ASC")
    List<ScorecardRule> getRulesByDimensionAndVersion(@Param("dimensionCode") String dimensionCode, @Param("version") String version);
}
