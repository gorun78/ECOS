package com.chinacreator.gzcm.runtime.core.agent.mesh.repository;

import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.AgentRegistryEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Agent注册表 Mapper — 操作 ecos_agent_registry (PG实际结构)
 */
@Mapper
public interface AgentRegistryRepository {

    @Select("SELECT id, name, role, capability, status, endpoint, metadata, " +
            "created_at AS createdAt, updated_at AS updatedAt " +
            "FROM ecos_agent_registry WHERE id = #{id}")
    AgentRegistryEntity findById(@Param("id") String id);

    @Select("SELECT id, name, role, capability, status, endpoint, metadata, " +
            "created_at AS createdAt, updated_at AS updatedAt " +
            "FROM ecos_agent_registry ORDER BY created_at DESC")
    List<AgentRegistryEntity> findAll();

    @Select("SELECT id, name, role, capability, status, endpoint, metadata, " +
            "created_at AS createdAt, updated_at AS updatedAt " +
            "FROM ecos_agent_registry WHERE status = 'ACTIVE' ORDER BY created_at DESC")
    List<AgentRegistryEntity> findActive();

    @Insert("INSERT INTO ecos_agent_registry (id, name, role, capability, status, endpoint, metadata) " +
            "VALUES (#{id}, #{name}, #{role}, #{capability}::jsonb, #{status}, #{endpoint}, #{metadata}::jsonb)")
    int insert(AgentRegistryEntity agent);

    @Update("UPDATE ecos_agent_registry SET name=#{name}, role=#{role}, capability=#{capability}::jsonb, " +
            "status=#{status}, endpoint=#{endpoint}, metadata=#{metadata}::jsonb, updated_at=NOW() " +
            "WHERE id=#{id}")
    int update(AgentRegistryEntity agent);

    @Delete("DELETE FROM ecos_agent_registry WHERE id=#{id}")
    int delete(@Param("id") String id);
}
