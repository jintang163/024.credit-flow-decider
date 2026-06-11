package com.bc.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.credit.entity.AsyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AsyncTaskMapper extends BaseMapper<AsyncTask> {

    @Select("SELECT * FROM async_task WHERE task_id = #{taskId} AND deleted = 0 LIMIT 1")
    AsyncTask selectByTaskId(@Param("taskId") String taskId);

    @Select("SELECT * FROM async_task WHERE process_instance_id = #{processInstanceId} " +
            "AND task_type = #{taskType} AND deleted = 0 ORDER BY created_time DESC LIMIT 1")
    AsyncTask selectLatestByProcessAndType(@Param("processInstanceId") String processInstanceId,
                                            @Param("taskType") String taskType);

    @Select("SELECT * FROM async_task WHERE status IN (0, 1, 8) AND deleted = 0 " +
            "AND expire_time < #{now} ORDER BY expire_time ASC LIMIT #{limit}")
    List<AsyncTask> selectExpiredTasks(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("SELECT * FROM async_task WHERE status = 3 AND deleted = 0 " +
            "AND retry_count < max_retry AND expire_time < #{now} " +
            "ORDER BY updated_time ASC LIMIT #{limit}")
    List<AsyncTask> selectRetryableFailedTasks(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM async_task WHERE status = #{status} AND deleted = 0 " +
            "AND task_type = #{taskType}")
    long countByStatusAndType(@Param("status") Integer status, @Param("taskType") String taskType);

    @Update("UPDATE async_task SET status = #{status}, updated_time = NOW() WHERE task_id = #{taskId}")
    int updateStatusByTaskId(@Param("taskId") String taskId, @Param("status") Integer status);

    @Update("UPDATE async_task SET status = #{status}, retry_count = retry_count + 1, " +
            "last_error = #{lastError}, updated_time = NOW() WHERE task_id = #{taskId}")
    int incrementRetryAndUpdateStatus(@Param("taskId") String taskId,
                                       @Param("status") Integer status,
                                       @Param("lastError") String lastError);

    @Update("UPDATE async_task SET status = #{status}, compensation_count = compensation_count + 1, " +
            "last_compensation_time = NOW(), last_error = #{lastError}, updated_time = NOW() " +
            "WHERE task_id = #{taskId}")
    int incrementCompensationAndUpdateStatus(@Param("taskId") String taskId,
                                              @Param("status") Integer status,
                                              @Param("lastError") String lastError);
}
