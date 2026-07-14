package com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.repository;

import com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.entity.KnowledgeEdge;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 知识边 Mapper — 操作 ecos_knowledge_graph_edge
 */
@Mapper
public interface KnowledgeEdgeRepository {

    @Select("SELECT id, source_node_id AS sourceNodeId, target_node_id AS targetNodeId, " +
            "relationship, weight, created_at AS createdAt " +
            "FROM ecos_knowledge_graph_edge ORDER BY created_at DESC")
    List<KnowledgeEdge> findAll();

    @Select("SELECT id, source_node_id AS sourceNodeId, target_node_id AS targetNodeId, " +
            "relationship, weight, created_at AS createdAt " +
            "FROM ecos_knowledge_graph_edge WHERE source_node_id = #{nodeId} OR target_node_id = #{nodeId}")
    List<KnowledgeEdge> findByNodeId(@Param("nodeId") String nodeId);

    @Select("SELECT id, source_node_id AS sourceNodeId, target_node_id AS targetNodeId, " +
            "relationship, weight, created_at AS createdAt " +
            "FROM ecos_knowledge_graph_edge WHERE id = #{id}")
    KnowledgeEdge findById(@Param("id") String id);

    @Insert("INSERT INTO ecos_knowledge_graph_edge (id, source_node_id, target_node_id, relationship, weight) " +
            "VALUES (#{id}, #{sourceNodeId}, #{targetNodeId}, #{relationship}, #{weight})")
    int insert(KnowledgeEdge edge);

    @Delete("DELETE FROM ecos_knowledge_graph_edge WHERE id=#{id}")
    int delete(@Param("id") String id);

    @Delete("DELETE FROM ecos_knowledge_graph_edge WHERE source_node_id = #{nodeId} OR target_node_id = #{nodeId}")
    int deleteByNodeId(@Param("nodeId") String nodeId);
}
