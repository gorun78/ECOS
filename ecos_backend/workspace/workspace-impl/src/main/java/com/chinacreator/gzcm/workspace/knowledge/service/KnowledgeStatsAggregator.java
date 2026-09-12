package com.chinacreator.gzcm.workspace.knowledge.service;

import com.chinacreator.gzcm.workspace.knowledge.dto.KnowledgeStatsVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * 知识工作台统计聚合器 — E-A#2 产物。
 *
 * <p>13 项 COUNT + 3 ts 全部 JdbcTemplate 只读。
 * 单表 COUNT 失败（如 kb_ontology_snapshot 未建、kg_sync_log 未建）按 0 占位 + 打 WARN，
 * 不抛异常不 500（前端展示 ~ 提示表未建）。
 *
 * @author ecos-factory
 * @since PMO-55 批次 E-A
 */
@Service("ecosKnowledgeStatsAggregator")
public class KnowledgeStatsAggregator {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeStatsAggregator.class);

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * 最近一次数据变更时间 — GREATEST 包裹每张表最大时间戳。
     * 注意：sys_rule_version.changed_at / updated_at / created_at 是 BIGINT (epoch ms)，
     * 需显式 CAST 成 TIMESTAMP 以和 TIMESTAMPTZ 域统一；显式 COALESCE 避免 NULL 传播。
     */
    private static final String GREATEST_TS_SQL =
            "SELECT GREATEST(" +
                    "COALESCE((SELECT MAX(created_at) FROM ecos_knowledge.graph_node), TIMESTAMP '1970-01-01 00:00:00+08')," +
                    "COALESCE((SELECT MAX(updated_at) FROM ecos_knowledge.graph_edge), TIMESTAMP '1970-01-01 00:00:00+08')," +
                    "COALESCE((SELECT MAX(updated_at) FROM ecos_knowledge.knowledge_article), TIMESTAMP '1970-01-01 00:00:00+08')," +
                    "COALESCE((SELECT MAX(created_at) FROM kb_ontology_snapshot), TIMESTAMP '1970-01-01 00:00:00+08')," +
                    "COALESCE((SELECT MAX(updated_at) FROM kb_cognitive_pipeline), TIMESTAMP '1970-01-01 00:00:00+08')," +
                    "COALESCE((SELECT MAX(parse_at) FROM kb_lineage_event), TIMESTAMP '1970-01-01 00:00:00+08')," +
                    "COALESCE((SELECT MAX(TO_TIMESTAMP(changed_at / 1000.0)) FROM sys_rule_version), TIMESTAMP '1970-01-01 00:00:00+08')," +
                    "COALESCE((SELECT MAX(TO_TIMESTAMP(updated_at / 1000.0)) FROM sys_compliance_rule), TIMESTAMP '1970-01-01 00:00:00+08')" +
                    ")";

    private final JdbcTemplate jdbc;

    public KnowledgeStatsAggregator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 聚合 13 数字段 + lastKgSyncAt / lastOntologySnapshotAt / lastUpdatedAt。
     */
    public KnowledgeStatsVO aggregate() {
        KnowledgeStatsVO vo = new KnowledgeStatsVO();

        vo.setGraphNodeCount(count("SELECT COUNT(*) FROM ecos_knowledge.graph_node"));
        vo.setGraphEdgeCount(count("SELECT COUNT(*) FROM ecos_knowledge.graph_edge"));
        vo.setArticleCount(count("SELECT COUNT(*) FROM ecos_knowledge.knowledge_article"));
        vo.setEmbeddingCount(count("SELECT COUNT(*) FROM ecos_knowledge.knowledge_embedding"));
        vo.setComplianceRuleCount(count("SELECT COUNT(*) FROM sys_compliance_rule"));
        vo.setActiveComplianceRuleCount(count("SELECT COUNT(*) FROM sys_compliance_rule WHERE status = 'ACTIVE'"));
        vo.setRuleVersionCount(count("SELECT COUNT(*) FROM sys_rule_version"));
        vo.setOntologySnapshotCount(count("SELECT COUNT(*) FROM kb_ontology_snapshot WHERE is_deleted = 0"));
        vo.setOntologyVersionCount(count(
                "SELECT COUNT(DISTINCT ontology_id) FROM kb_ontology_snapshot WHERE is_deleted = 0"));
        vo.setKgSyncLogCount(count("SELECT COUNT(*) FROM ecos_knowledge.kg_sync_log"));
        vo.setCognitivePipelineCount(count("SELECT COUNT(*) FROM kb_cognitive_pipeline WHERE is_deleted = 0"));
        vo.setLineageEventCount(count("SELECT COUNT(*) FROM kb_lineage_event WHERE is_deleted = 0"));
        vo.setPublishedOntologyVersionCount(count(
                "SELECT COUNT(DISTINCT ontology_id) FROM kb_ontology_snapshot WHERE is_deleted = 0"));

        vo.setLastKgSyncAt(maxTimestamp("SELECT MAX(created_at) FROM ecos_knowledge.kg_sync_log"));
        vo.setLastOntologySnapshotAt(maxTimestamp("SELECT MAX(created_at) FROM kb_ontology_snapshot WHERE is_deleted = 0"));
        vo.setLastUpdatedAt(maxTimestamp(GREATEST_TS_SQL));

        return vo;
    }

    // ── 内部辅助（失败兜底 0/null, 不抛）────────────────

    private Long count(String sql) {
        try {
            Long val = jdbc.queryForObject(sql, Long.class);
            return val == null ? 0L : val;
        } catch (Exception ex) {
            log.warn("knowledge stats count fallback 0: {} ({})", sql, shortMsg(ex));
            return 0L;
        }
    }

    /**
     * 取 MAX(created_at)：兼容 returned value 类型（TIMESTAMP → OffsetDateTime 或 Instant，
     * BIGINT（epoch ms）→ Long），统一输出 ISO-8601 字符串；查不到返回 null（JSON 省略）。
     *
     * <p>注意：sys_rule_version.changed_at 列在 V100 定义为 BIGINT（epoch ms），
     * GREATEST 混合 TIMESTAMP/BIGINT 需显式类型转换，此处用最外层 COALESCE 统一。
     */
    private String maxTimestamp(String sql) {
        try {
            final Object[] row = {null};
            jdbc.query(sql, rs -> {
                try {
                    row[0] = rs.getObject(1);
                } catch (Exception e) {
                    throw new IllegalStateException("rs access", e);
                }
            });
            return formatTs(row[0]);
        } catch (Exception ex) {
            log.warn("knowledge stats max-timestamp query failed: {} ({})", shortMsg(ex), sql);
            return null;
        }
    }

    private String formatTs(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            if (raw instanceof java.sql.Timestamp ts) {
                return ts.toInstant().atOffset(java.time.ZoneOffset.UTC).format(TS_FMT);
            }
            if (raw instanceof java.time.LocalDateTime ldt) {
                return ldt.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().format(TS_FMT);
            }
            if (raw instanceof java.time.OffsetDateTime odt) {
                return odt.format(TS_FMT);
            }
            if (raw instanceof java.time.Instant inst) {
                return inst.atOffset(java.time.ZoneOffset.UTC).format(TS_FMT);
            }
            if (raw instanceof Number n) {
                // BIGINT epoch ms 列 (sys_compliance_rule/changed_at)
                return java.time.Instant.ofEpochMilli(n.longValue()).atOffset(java.time.ZoneOffset.UTC).format(TS_FMT);
            }
            return String.valueOf(raw);
        } catch (Exception ex) {
            log.warn("knowledge stats formatTs failed for {}: {}", raw, shortMsg(ex));
            return String.valueOf(raw);
        }
    }

    private static String shortMsg(Throwable t) {
        String msg = t.getClass().getSimpleName();
        if (t.getMessage() != null) {
            msg = msg + ": " + t.getMessage();
        }
        return msg.length() > 160 ? msg.substring(0, 160) : msg;
    }
}
