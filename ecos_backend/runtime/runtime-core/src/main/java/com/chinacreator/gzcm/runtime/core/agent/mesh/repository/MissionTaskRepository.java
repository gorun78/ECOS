package com.chinacreator.gzcm.runtime.core.agent.mesh.repository;

import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.MissionTaskEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * MissionTask Mapper — 操作 ecos_mission_task (PG实际结构)
 */
@Mapper
public interface MissionTaskRepository {

    @Select("SELECT id, mission_id AS missionId, agent_id AS agentId, instruction, " +
            "status, result, depends_on AS dependsOn, started_at AS startedAt, finished_at AS finishedAt " +
            "FROM ecos_mission_task WHERE id = #{id}")
    MissionTaskEntity findById(@Param("id") String id);

    @Select("SELECT id, mission_id AS missionId, agent_id AS agentId, instruction, " +
            "status, result, depends_on AS dependsOn, started_at AS startedAt, finished_at AS finishedAt " +
            "FROM ecos_mission_task WHERE mission_id = #{missionId} ORDER BY id")
    List<MissionTaskEntity> findByMissionId(@Param("missionId") String missionId);

    @Insert("INSERT INTO ecos_mission_task (id, mission_id, agent_id, instruction, status, result) " +
            "VALUES (#{id}, #{missionId}, #{agentId}, #{instruction}, #{status}, #{result}::jsonb)")
    int insert(MissionTaskEntity entity);

    @Update("UPDATE ecos_mission_task SET status=#{status}, result=#{result}::jsonb, " +
            "finished_at=#{finishedAt} WHERE id=#{id}")
    int updateCompleted(@Param("id") String id, @Param("status") String status,
                        @Param("result") String result,
                        @Param("finishedAt") java.time.LocalDateTime finishedAt);

    @Update("UPDATE ecos_mission_task SET status=#{status} WHERE id=#{id}")
    int updateStatus(@Param("id") String id, @Param("status") String status);
}
