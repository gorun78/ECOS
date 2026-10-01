package com.chinacreator.gzcm.engine.data.quality.dao;

import java.util.List;

import com.chinacreator.gzcm.engine.data.quality.model.DqKnowledgeEntryVO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * DqKnowledgeSinkDao — DQ 知识沉淀数据访问层（PMO-48-D T16）。
 * <p>
 * 架构铁律 §2.5 #1 收口：原内联在 {@code DqKnowledgeSinkServiceImpl} 的 JdbcTemplate 调用迁移到本 DAO，
 * SQL 文本一律离开 Service。表前缀统一为 {@code ecos_data}。
 *
 * @author PMO-48-D T16
 */
@Repository
public class DqKnowledgeSinkDao extends JdbcTemplateDaoSupport {

    /** 写知识条目（embedding 存 TEXT，entity_json 为 JSONB）。 */
    public void insertEntry(String id, String sourceType, String sourceId, String category,
                            String title, String summary, String contentMd,
                            String entityJson, String embedding) {
        jdbcTemplate.update(
                "INSERT INTO ecos_data.dq_knowledge_entry"
                + " (id, source_type, source_id, category, title, summary, content_md, entity_json, embedding, created_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, NOW())",
                id, sourceType, sourceId, category, title, summary, contentMd, entityJson, embedding);
    }

    /**
     * 全表扫描（LIMIT 兜底，本地知识条目有限）— RAG 相似检索池，
     * 排序/相似度计算留在 Service（Jaccard 纯内存算法）。
     */
    public List<DqKnowledgeEntryVO> scanKnowledgeEntries(RowMapper<DqKnowledgeEntryVO> mapper, int limit) {
        return jdbcTemplate.query(
                "SELECT id, source_type AS sourceType, source_id AS sourceId, category, title, summary,"
                + " content_md AS contentMd, entity_json::text AS entityJson, embedding AS embeddingJson,"
                + " score_hint AS scoreHint, created_at AS createdAt"
                + " FROM ecos_data.dq_knowledge_entry ORDER BY created_at DESC LIMIT ?",
                mapper, limit);
    }
}
