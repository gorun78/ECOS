package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 知识接入数据访问服务 — {@code ecos_knowledge.graph_node} 的补字段更新。
 *
 * <p>供 {@code KnowledgeIngestController} 调用：幂等命中后的 UPDATE 收敛在 Service 层
 * （架构铁律 §3.6：Controller 不得直接访问数据库）。{@code KnowledgeNodeMapper} 无
 * update 方法，故此处以 JdbcTemplate 直接 set，行为与原实现逐字一致。</p>
 *
 * <p>异常语义：写入抛出的 {@code DataAccessException} 原样上浮，由调用方 Controller
 * 捕获并按既有 HTTP 行为返回。</p>
 */
@Service
public class KnowledgeIngestQueryService {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeIngestQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 直接 PG UPDATE 节点字段（label / node_type / description / properties / domain / updated_at），
     * 以 id 为条件。
     *
     * @param node 已完成内存补丁的节点
     * @return 实际 UPDATE 命中行数
     */
    public int updateGraphNode(KnowledgeNode node) {
        return jdbcTemplate.update(
                "UPDATE ecos_knowledge.graph_node SET label = ?, node_type = ?, description = ?, " +
                "properties = ?, domain = ?, updated_at = ? WHERE id = ?",
                node.getLabel(), node.getNodeType(), node.getDescription(),
                node.getPropertiesJson(), node.getDomain(),
                node.getUpdatedAt(), node.getId());
    }
}
