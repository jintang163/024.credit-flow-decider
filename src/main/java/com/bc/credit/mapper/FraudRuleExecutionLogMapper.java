package com.bc.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.credit.entity.FraudRuleExecutionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface FraudRuleExecutionLogMapper extends BaseMapper<FraudRuleExecutionLog> {

    @Select("SELECT rule_code, rule_name, rule_group, " +
            "COUNT(*) as execute_count, " +
            "SUM(CASE WHEN hit = 1 THEN 1 ELSE 0 END) as hit_count, " +
            "ROUND(SUM(CASE WHEN hit = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) as hit_rate, " +
            "AVG(CASE WHEN hit = 1 THEN hit_score ELSE NULL END) as avg_score, " +
            "SUM(CASE WHEN hit = 1 AND action = 'REJECT' THEN 1 ELSE 0 END) as reject_count, " +
            "SUM(CASE WHEN hit = 1 AND action = 'ALERT' THEN 1 ELSE 0 END) as alert_count " +
            "FROM fraud_rule_execution_log WHERE deleted = 0 " +
            "AND execution_time >= #{startTime} AND execution_time <= #{endTime} " +
            "GROUP BY rule_code, rule_name, rule_group " +
            "ORDER BY hit_count DESC")
    List<Map<String, Object>> getRuleHitStats(@Param("startTime") String startTime,
                                               @Param("endTime") String endTime);

    @Select("SELECT rule_group, " +
            "COUNT(*) as total_count, " +
            "SUM(CASE WHEN hit = 1 THEN 1 ELSE 0 END) as hit_count, " +
            "SUM(CASE WHEN hit = 1 AND action = 'REJECT' THEN 1 ELSE 0 END) as reject_count " +
            "FROM fraud_rule_execution_log WHERE deleted = 0 " +
            "AND rule_group IN ('A', 'B') " +
            "AND execution_time >= #{startTime} AND execution_time <= #{endTime} " +
            "GROUP BY rule_group")
    List<Map<String, Object>> getABTestStats(@Param("startTime") String startTime,
                                              @Param("endTime") String endTime);
}
