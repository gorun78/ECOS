package com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.repository;

import com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.entity.KnowledgeNode;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 知识节点 Mapper — 操作 ecos_knowledge_graph_node
 */
@Mapper
public interface KnowledgeNodeRepository {

    @Select("SELECT id, label, node_type AS nodeType, description, " +
            "properties_json AS propertiesJson, created_at AS createdAt " +
            "FROM ecos_knowledge_graph_node ORDER BY created_at DESC")
    List<KnowledgeNode> findAll();

    @Select("SELECT id, label, node_type AS nodeType, description, " +
            "properties_json AS propertiesJson, created_at AS createdAt " +
            "FROM ecos_knowledge_graph_node WHERE id = #{id}")
    KnowledgeNode findById(@Param("id") String id);

    @Select("SELECT id, label, node_type AS nodeType, description, " +
            "properties_json AS propertiesJson, created_at AS createdAt " +
            "FROM ecos_knowledge_graph_node " +
            "WHERE properties_json->>'domain' = #{domain} " +
            "ORDER BY created_at DESC")
    List<KnowledgeNode> findByDomain(@Param("domain") String domain);

    @Select("SELECT id, label, node_type AS nodeType, description, " +
            "properties_json AS propertiesJson, created_at AS createdAt " +
            "FROM ecos_knowledge_graph_node " +
            "WHERE label ILIKE '%' || #{q} || '%' OR description ILIKE '%' || #{q} || '%' " +
            "ORDER BY created_at DESC")
    List<KnowledgeNode> search(@Param("q") String q);

    @Insert("INSERT INTO ecos_knowledge_graph_node (id, label, node_type, description, properties_json) " +
            "VALUES (#{id}, #{label}, #{nodeType}, #{description}, #{propertiesJson}::jsonb)")
    int insert(KnowledgeNode node);

    @Update("UPDATE ecos_knowledge_graph_node SET label=#{label}, description=#{description}, " +
            "properties_json=#{propertiesJson}::jsonb WHERE id=#{id}")
    int update(KnowledgeNode node);

    @Delete("DELETE FROM ecos_knowledge_graph_node WHERE id=#{id}")
    int delete(@Param("id") String id);
}
