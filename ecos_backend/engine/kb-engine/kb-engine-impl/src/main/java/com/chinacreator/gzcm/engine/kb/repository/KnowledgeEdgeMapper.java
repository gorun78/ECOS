package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeEdgeMapper {

    @Select("SELECT id, source_id as sourceNodeId, target_id as targetNodeId, type as relationship, weight, properties as propertiesJson, created_at as createdAt FROM ecos_knowledge.graph_edge WHERE id = #{id}")
    KnowledgeEdge findById(@Param("id") String id);

    @Select("SELECT id, source_id as sourceNodeId, target_id as targetNodeId, type as relationship, weight, properties as propertiesJson, created_at as createdAt FROM ecos_knowledge.graph_edge WHERE source_id = #{nodeId}")
    List<KnowledgeEdge> findBySourceNodeId(@Param("nodeId") String nodeId);

    @Select("SELECT id, source_id as sourceNodeId, target_id as targetNodeId, type as relationship, weight, properties as propertiesJson, created_at as createdAt FROM ecos_knowledge.graph_edge WHERE target_id = #{nodeId}")
    List<KnowledgeEdge> findByTargetNodeId(@Param("nodeId") String nodeId);

    @Select("SELECT id, source_id as sourceNodeId, target_id as targetNodeId, type as relationship, weight, properties as propertiesJson, created_at as createdAt FROM ecos_knowledge.graph_edge")
    List<KnowledgeEdge> findAll();

    @Insert("INSERT INTO ecos_knowledge.graph_edge (id, source_id, target_id, type, weight, properties, created_at) " +
            "VALUES (#{id}, #{sourceNodeId}, #{targetNodeId}, #{relationship}, #{weight}, #{propertiesJson, jdbcType=VARCHAR}, #{createdAt})")
    int insert(KnowledgeEdge edge);

    @Select("SELECT COUNT(*) FROM ecos_knowledge.graph_edge")
    long count();
}