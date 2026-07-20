package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeNodeMapper {

    @Select("SELECT id, label, node_type as nodeType, description, properties as propertiesJson, domain, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.graph_node WHERE id = #{id}")
    KnowledgeNode findById(@Param("id") String id);

    @Select("SELECT id, label, node_type as nodeType, description, properties as propertiesJson, domain, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.graph_node WHERE domain = #{domain}")
    List<KnowledgeNode> findByDomain(@Param("domain") String domain);

    @Select("SELECT id, label, node_type as nodeType, description, properties as propertiesJson, domain, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.graph_node WHERE label ILIKE CONCAT('%', #{query}, '%')")
    List<KnowledgeNode> searchByLabel(@Param("query") String query);

    @Select("SELECT id, label, node_type as nodeType, description, properties as propertiesJson, domain, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.graph_node")
    List<KnowledgeNode> findAll();

    @Insert("INSERT INTO ecos_knowledge.graph_node (id, label, node_type, description, properties, domain, created_at, updated_at) " +
            "VALUES (#{id}, #{label}, #{nodeType, jdbcType=VARCHAR}, #{description, jdbcType=VARCHAR}, #{propertiesJson, jdbcType=VARCHAR}, #{domain, jdbcType=VARCHAR}, #{createdAt}, #{updatedAt})")
    int insert(KnowledgeNode node);

    @Select("SELECT COUNT(*) FROM ecos_knowledge.graph_node")
    long count();
}