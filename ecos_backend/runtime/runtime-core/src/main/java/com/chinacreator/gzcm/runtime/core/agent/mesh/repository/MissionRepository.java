package com.chinacreator.gzcm.runtime.core.agent.mesh.repository;

import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.MissionEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Mission Mapper — 操作 ecos_mission (PG实际结构)
 */
@Mapper
public interface MissionRepository {

    @Select("SELECT id, title, goal, mode, status, plan, result, created_by AS createdBy, " +
            "created_at AS createdAt, finished_at AS finishedAt " +
            "FROM ecos_mission WHERE id = #{id}")
    MissionEntity findById(@Param("id") String id);

    @Select("SELECT id, title, goal, mode, status, plan, result, created_by AS createdBy, " +
            "created_at AS createdAt, finished_at AS finishedAt " +
            "FROM ecos_mission ORDER BY created_at DESC LIMIT #{limit}")
    List<MissionEntity> findRecent(@Param("limit") int limit);

    @Select("SELECT id, title, goal, mode, status, plan, result, created_by AS createdBy, " +
            "created_at AS createdAt, finished_at AS finishedAt " +
            "FROM ecos_mission ORDER BY created_at DESC")
    List<MissionEntity> findAll();

    @Insert("INSERT INTO ecos_mission (id, title, goal, mode, status, plan) " +
            "VALUES (#{id}, #{title}, #{goal}, #{mode}, #{status}, #{plan}::jsonb)")
    int insert(MissionEntity entity);

    @Update("UPDATE ecos_mission SET status=#{status} WHERE id=#{id}")
    int updateRunning(@Param("id") String id, @Param("status") String status,
                      @Param("startedAt") java.time.LocalDateTime startedAt);

    @Update("UPDATE ecos_mission SET status=#{status}, result=#{result}::jsonb, finished_at=#{finishedAt} " +
            "WHERE id=#{id}")
    int updateCompleted(@Param("id") String id, @Param("status") String status,
                        @Param("result") String result,
                        @Param("finishedAt") java.time.LocalDateTime finishedAt);

    @Update("UPDATE ecos_mission SET status=#{status} WHERE id=#{id}")
    int updateStatus(@Param("id") String id, @Param("status") String status);
}
