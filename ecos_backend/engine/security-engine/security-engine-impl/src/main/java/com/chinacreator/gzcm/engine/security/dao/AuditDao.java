package com.chinacreator.gzcm.engine.security.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 审计 DAO（R2-B3b 数据访问收口；只搬不改语义）。
 *
 * <p>表：{@code ecos_audit_log}（哈希链 + Kafka 消费者幂等落库）与
 * {@code ecos_runtime_audit_retry}（V244 Kafka 不可用兜底重试表）。裸表名与存量 SQL 一致。
 * 供 {@code AuditHashChainService}（哈希链戳记/校验）、
 * {@code AuditKafkaConsumer}（幂等落库）、
 * {@code AuditEventProducer}（重试表兜底）共用。</p>
 */
@Repository
public class AuditDao extends JdbcTemplateDaoSupport {

    private static final String AUDIT_LOG = "ecos_audit_log";

    /** 重试表兜底 INSERT（Kafka 不可用路径）。 */
    public void insertAuditRetry(String id, String payloadJson) {
        jdbcTemplate.update(
            "INSERT INTO ecos_runtime_audit_retry (id, event_type, payload, attempts, next_retry_at, status, create_time, update_time, version_no, is_deleted, domain) " +
            "VALUES (?, 'security_audit', ?, 0, NOW(), 'pending', NOW(), NOW(), '1', 0, 'default')",
            id, payloadJson);
    }

    // ── 哈希链 ──

    /** 取记录内容用于哈希计算。 */
    public Map<String, Object> findAuditRowContent(long auditLogId) {
        return jdbcTemplate.queryForMap(
            "SELECT username, operation, entity_type, entity_id, created_at::text as created_at " +
            "FROM " + AUDIT_LOG + " WHERE id = ?", auditLogId);
    }

    /** 取前一条记录 curr_hash（COALESCE 空串哈希兜底首条记录）。 */
    public String findPrevHash(long auditLogId, String emptyHash) {
        return jdbcTemplate.queryForObject(
            "SELECT COALESCE(curr_hash, ?) FROM " + AUDIT_LOG + " WHERE id < ? ORDER BY id DESC LIMIT 1",
            String.class, emptyHash, auditLogId);
    }

    /** 更新记录的哈希字段。 */
    public int updateHashFields(String prevHash, String currHash, String algorithm, long auditLogId) {
        return jdbcTemplate.update(
            "UPDATE " + AUDIT_LOG + " SET prev_hash = ?, curr_hash = ?, hash_algorithm = ? WHERE id = ?",
            prevHash, currHash, algorithm, auditLogId);
    }

    /** 全量取哈希链相关列（按 created_at, id 升序）。 */
    public List<Map<String, Object>> findHashChainRows() {
        return jdbcTemplate.queryForList(
            "SELECT id, username, operation, entity_type, entity_id, created_at::text as created_at, " +
            "prev_hash, curr_hash FROM " + AUDIT_LOG + " ORDER BY created_at ASC, id ASC");
    }

    // ── Kafka 消费者幂等落库 ──

    /** PG 幂等落库（ON CONFLICT (id) DO NOTHING）。 */
    public int insertAuditLogIdempotent(String id, String userId, String username, String action,
                                        String resource, String result, String detail, String ip) {
        return jdbcTemplate.update(
            "INSERT INTO " + AUDIT_LOG + " (id, user_id, username, action, resource, result, detail, ip_address) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
            id, userId, username, action, resource, result, detail, ip);
    }

    /** 退化路径查重（非 PG / ON CONFLICT 不支持）。 */
    public int countAuditLog(String id) {
        Integer c = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + AUDIT_LOG + " WHERE id = ?", Integer.class, id);
        return c == null ? 0 : c;
    }

    /** 退化路径普通 INSERT。 */
    public int insertAuditLog(String id, String userId, String username, String action,
                              String resource, String result, String detail, String ip) {
        return jdbcTemplate.update(
            "INSERT INTO " + AUDIT_LOG + " (id, user_id, username, action, resource, result, detail, ip_address) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            id, userId, username, action, resource, result, detail, ip);
    }
}
