package com.chinacreator.gzcm.engine.ontology.audit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 审计读回口的 JDBC 实现（#147AD）。
 *
 * <p>表与列实测依据（2026-11-02）：审计主存 = {@code ecos_audit_log}（裸名与
 * security-engine {@code AuditDao.AUDIT_LOG} 同源，不硬编码 schema）；
 * {@code audit_event_id} 列由 V303 治理列补齐并由 {@code AuditDao.fillGovernanceColumns} 写入。
 * 同一事件可能因重试产生多行，取最新一行（{@code created_at DESC}）。</p>
 *
 * @author ECOS Ontology #147AD
 */
@Repository
public class JdbcPublishAuditLookup implements PublishAuditLookup {

    private static final String SQL = "SELECT apply_status, verification_status FROM ecos_audit_log "
            + "WHERE audit_event_id = ? ORDER BY created_at DESC LIMIT 1";

    private final JdbcTemplate jdbcTemplate;

    public JdbcPublishAuditLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AuditRow> findByEventId(String auditEventId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SQL, auditEventId);
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        return Optional.of(new AuditRow(text(row.get("apply_status")), text(row.get("verification_status"))));
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
