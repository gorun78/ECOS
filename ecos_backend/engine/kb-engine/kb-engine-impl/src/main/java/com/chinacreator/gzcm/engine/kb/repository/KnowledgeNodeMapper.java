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
     * 知识接入幂等 upsert 的 UPDATE 分支（PMO-74 H9-T4：从 {@code KnowledgeIngestController}
     * 内联的数据访问模板下沉至此）。
     *
     * <p>列集合与 WHERE 条件与下沉前逐字段一致：仅更新 {@code label / node_type /
     * description / properties / domain / updated_at}，不动 {@code created_at} 与
     * B3-2 实例抽取专用的 {@code ontology_id / ontology_version / source_resource_id /
     * source_pk}。可空列标 {@code jdbcType=VARCHAR}（与 {@link #insert} 同风格，
     * 避免驱动无法推断 NULL 类型）。
     *
     * @param node 已在内存中完成"仅补缺失字段"patch 的节点（id 必填）
     * @return 受影响行数（0 表示 id 不存在）
     */
    @Update("UPDATE ecos_knowledge.graph_node SET label = #{label}, node_type = #{nodeType, jdbcType=VARCHAR}, "
            + "description = #{description, jdbcType=VARCHAR}, properties = #{propertiesJson, jdbcType=VARCHAR}, "
            + "domain = #{domain, jdbcType=VARCHAR}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateIngestFields(KnowledgeNode node);

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