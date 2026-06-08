package com.bc.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.credit.entity.ProcessDefinitionVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProcessDefinitionVersionMapper extends BaseMapper<ProcessDefinitionVersion> {

    @Select("SELECT IFNULL(MAX(version), 0) + 1 FROM process_definition_version WHERE process_key = #{processKey} AND deleted = 0")
    Integer getNextVersion(@Param("processKey") String processKey);

    @Select("SELECT * FROM process_definition_version WHERE process_key = #{processKey} AND status = 1 AND deleted = 0 ORDER BY version DESC LIMIT 1")
    ProcessDefinitionVersion getLatestActiveVersion(@Param("processKey") String processKey);

    @Update("UPDATE process_definition_version SET status = 0, updated_time = NOW() WHERE id = #{id}")
    Integer disableVersion(@Param("id") Long id);
}
