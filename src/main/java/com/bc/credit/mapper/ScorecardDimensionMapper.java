package com.bc.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.credit.entity.ScorecardDimension;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ScorecardDimensionMapper extends BaseMapper<ScorecardDimension> {

    @Select("SELECT * FROM scorecard_dimension WHERE scorecard_version = #{version} AND enabled = 1 AND deleted = 0 ORDER BY sort_order ASC")
    List<ScorecardDimension> getDimensionsByVersion(@Param("version") String version);
}
