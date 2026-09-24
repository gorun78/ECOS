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

    @Select("SELECT id, label, node_type as nodeType, description, properties as propertiesJson, domain, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.graph_node WHERE label = #{label}")
    KnowledgeNode findByLabel(@Param("label") String label);

    @Select("SELECT id, label, node_type as nodeType, description, properties as propertiesJson, domain, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.graph_node WHERE label ILIKE #{labelPattern}")
    List<KnowledgeNode> searchByLabelPattern(@Param("labelPattern") String labelPattern);

    @Select("SELECT id, label, node_type as nodeType, description, properties as propertiesJson, domain, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.graph_node")
    List<KnowledgeNode> findAll();

    @Insert("INSERT INTO ecos_knowledge.graph_node (id, label, node_type, description, properties, domain, created_at, updated_at, " +
            "ontology_id, ontology_version, source_resource_id, source_pk) " +
            "VALUES (#{id}, #{label}, #{nodeType, jdbcType=VARCHAR}, #{description, jdbcType=VARCHAR}, #{propertiesJson, jdbcType=VARCHAR}, #{domain, jdbcType=VARCHAR}, #{createdAt}, #{updatedAt}, " +
            "#{ontologyId, jdbcType=VARCHAR}, #{ontologyVersion, jdbcType=VARCHAR}, #{sourceResourceId, jdbcType=VARCHAR}, #{sourcePk, jdbcType=VARCHAR})")
    int insert(KnowledgeNode node);

    @Select("SELECT COUNT(*) FROM ecos_knowledge.graph_node")
    long count();

    /**
     * F10: 批量查 graph_node 存在性（参数化 IN，返回命中的 id 列表）。
     *
     * @param ids 知识资产 ID 集合（≤ 100）
     * @return 命中的 graph_node.id 列表
     */
    @Select("<script>SELECT id FROM ecos_knowledge.graph_node WHERE id IN " +
            "<foreach item='item' index='index' collection='ids' open='(' separator=',' close=')'>#{item}</foreach></script>")
    List<String> batchExists(@Param("ids") List<String> ids);
}