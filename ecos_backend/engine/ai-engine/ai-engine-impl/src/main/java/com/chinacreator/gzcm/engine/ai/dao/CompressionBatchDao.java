package com.chinacreator.gzcm.engine.ai.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：会话压缩批次头（F06-09）的 JDBC 落点。
 *
 * <p>载体表 = {@code ecos_ai.ecos_ai_compression_batch}（迁移脚本 V203 新建合规表；
 * 与 V202 的"ALTER public 真身"不同，本表是纯新建表，按 R-19① 建于 ecos_ai）。
 * 批次头是"token 阈值 + 逻辑归档"新语义的台账：一次压缩恰一行，
 * {@code dropped_key_refs} 非空即降级证据（PRD-06 §2.2-2 抽检口径）。</p>
 *
 * <p><b>跨 schema 纪律</b>：{@code session_id} 是指向 {@code public.sys_agent_session.id} 的
 * 逻辑引用，依 ST07 不建跨 schema FK；本 DAO 不读写其它引擎的表。</p>
 */
@Repository
public class CompressionBatchDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_ai_compression_batch";

    /** 批次表是否就位（V203 未实跑 ⇒ 读侧须能判定"表不在"而非抛错掩蔽）。 */
    public boolean tableReady() {
        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_schema = 'ecos_ai' AND table_name = 'ecos_ai_compression_batch'",
            Integer.class);
        return exists != null && exists > 0;
    }

    /**
     * 登记一次压缩批次。
     *
     * @param id                     应用侧 UUID（MC01：DDL 无默认值）
     * @param mode                   {@code LLM_SUMMARY} | {@code TRUNCATE_FALLBACK}
     * @param keptKeyRefs            摘要保留的关键引用串（逗号分，MC02 禁 jsonb）
     * @param droppedKeyRefs         降级时丢失的引用串；非空即降级证据
     * @param failureReason          降级原因短码（禁异常原文）
     */
    public int insertBatch(String id, String sessionId, String mode,
                           Integer compressedFromSeq, Integer compressedToSeq,
                           Integer estimatedTokensBefore, Integer estimatedTokensAfter,
                           String keptKeyRefs, String droppedKeyRefs,
                           String failureReason, String traceId, String createBy) {
        return jdbcTemplate.update(
            "INSERT INTO " + TABLE + " (id, session_id, mode, compressed_from_seq, compressed_to_seq, " +
            "estimated_tokens_before, estimated_tokens_after, kept_key_refs, dropped_key_refs, " +
            "failure_reason, trace_id, domain, version_no, is_deleted, create_by, create_time) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'default', '1', 0, ?, CURRENT_TIMESTAMP)",
            id, sessionId, mode, compressedFromSeq, compressedToSeq,
            estimatedTokensBefore, estimatedTokensAfter, keptKeyRefs, droppedKeyRefs,
            truncate(failureReason, 120), traceId, createBy);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
