package com.chinacreator.gzcm.engine.ai.agent.mesh.repository;

import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.MissionEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Mission Mapper — 操作 ecos_mission (PG实际结构)
 *
 * <p><b>D36（2026-11-13 产品级逐功能验证）</b>：本 Mapper 原三条 SELECT 取
 * {@code created_by} / {@code finished_at}，而 {@code ecos_ai.ecos_mission} 实际列只有
 * {@code id,title,goal,mode,status,plan,result,tenant_id,created_at,updated_at}（information_schema 实测，
 * 全库无第二份同名表）。于是 {@code GET /api/v1/agent-mesh/missions} 恒 500：
 * {@code column "created_by" does not exist}。此处按真实表结构取列：
 * 创建者无对应列 ⇒ 不取（保留 null，不编造）；完成时间以 {@code updated_at} 承载，
 * 与 {@link com.chinacreator.gzcm.engine.ai.agent.mesh.entity.MissionEntity} 里
 * {@code getUpdatedAt() -> finishedAt} 的既有语义一致。</p>
 */
@Mapper
public interface MissionRepository {

    @Select("SELECT id, title, goal, mode, status, plan, result, " +
            "created_at AS createdAt, updated_at AS finishedAt " +
            "FROM ecos_mission WHERE id = #{id}")
    MissionEntity findById(@Param("id") String id);

    @Select("SELECT id, title, goal, mode, status, plan, result, " +
            "created_at AS createdAt, updated_at AS finishedAt " +
            "FROM ecos_mission ORDER BY created_at DESC LIMIT #{limit}")
    List<MissionEntity> findRecent(@Param("limit") int limit);

    @Select("SELECT id, title, goal, mode, status, plan, result, " +
            "created_at AS createdAt, updated_at AS finishedAt " +
            "FROM ecos_mission ORDER BY created_at DESC")
    List<MissionEntity> findAll();

    @Insert("INSERT INTO ecos_mission (id, title, goal, mode, status, plan) " +
            "VALUES (#{id}, #{title}, #{goal}, #{mode}, #{status}, CAST(#{plan} AS jsonb))")
    int insert(MissionEntity entity);

    @Update("UPDATE ecos_mission SET status=#{status} WHERE id=#{id}")
    int updateRunning(@Param("id") String id, @Param("status") String status,
                      @Param("startedAt") java.time.LocalDateTime startedAt);

    @Update("UPDATE ecos_mission SET status=#{status}, result=CAST(#{result} AS jsonb), updated_at=#{finishedAt} " +
            "WHERE id=#{id}")
    int updateCompleted(@Param("id") String id, @Param("status") String status,
                        @Param("result") String result,
                        @Param("finishedAt") java.time.LocalDateTime finishedAt);

    @Update("UPDATE ecos_mission SET status=#{status} WHERE id=#{id}")
    int updateStatus(@Param("id") String id, @Param("status") String status);
}
