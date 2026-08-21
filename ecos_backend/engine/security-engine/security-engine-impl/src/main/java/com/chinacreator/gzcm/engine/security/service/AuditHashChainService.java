package com.chinacreator.gzcm.engine.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * P1-3: 审计日志哈希链完整性保护
 *
 * 每条审计记录的 curr_hash = SHA-256(prev_hash || record_content)
 * 首条记录的 prev_hash = SHA-256("")
 * 验证时重新计算每条记录的哈希，与存储的 curr_hash 比对，任何篡改都会导致哈希不匹配
 */
@Service
public class AuditHashChainService {

    private static final Logger log = LoggerFactory.getLogger(AuditHashChainService.class);
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String EMPTY_HASH = sha256("");

    private final JdbcTemplate jdbc;

    public AuditHashChainService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 为新审计记录计算并写入哈希链
     * 在审计日志 INSERT 之后调用此方法更新哈希字段
     */
    public void stampHashChain(long auditLogId) {
        try {
            // 获取当前记录内容
            Map<String, Object> row = jdbc.queryForMap(
                "SELECT username, operation, entity_type, entity_id, created_at::text as created_at " +
                "FROM ecos_audit_log WHERE id = ?", auditLogId);

            // 获取前一条记录的 curr_hash
            String prevHash = jdbc.queryForObject(
                "SELECT COALESCE(curr_hash, ?) FROM ecos_audit_log WHERE id < ? ORDER BY id DESC LIMIT 1",
                String.class, EMPTY_HASH, auditLogId);
            if (prevHash == null) prevHash = EMPTY_HASH;

            // 计算当前记录的哈希: SHA-256(prev_hash || username || operation || entity_type || entity_id || created_at)
            String recordContent = prevHash + "|" +
                str(row.get("username")) + "|" +
                str(row.get("operation")) + "|" +
                str(row.get("entity_type")) + "|" +
                str(row.get("entity_id")) + "|" +
                str(row.get("created_at"));
            String currHash = sha256(recordContent);

            // 更新哈希字段
            jdbc.update(
                "UPDATE ecos_audit_log SET prev_hash = ?, curr_hash = ?, hash_algorithm = ? WHERE id = ?",
                prevHash, currHash, HASH_ALGORITHM, auditLogId);
        } catch (Exception e) {
            log.warn("审计哈希链戳记失败: id={}, error={}", auditLogId, e.getMessage());
        }
    }

    /**
     * 验证审计日志哈希链完整性
     * @return 验证结果 Map: { valid: boolean, totalChecked: int, brokenAt: Long (first broken id or null) }
     */
    public Map<String, Object> verifyHashChain() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        int totalChecked = 0;
        Long brokenAt = null;
        String expectedPrevHash = EMPTY_HASH;

        try {
            var rows = jdbc.queryForList(
                "SELECT id, username, operation, entity_type, entity_id, created_at::text as created_at, " +
                "prev_hash, curr_hash FROM ecos_audit_log ORDER BY id ASC");

            for (Map<String, Object> row : rows) {
                totalChecked++;
                String storedPrevHash = str(row.get("prev_hash"));
                String storedCurrHash = str(row.get("curr_hash"));

                // 验证 prev_hash 链接
                if (!expectedPrevHash.equals(storedPrevHash)) {
                    brokenAt = ((Number) row.get("id")).longValue();
                    break;
                }

                // 重新计算 curr_hash
                String recordContent = storedPrevHash + "|" +
                    str(row.get("username")) + "|" +
                    str(row.get("operation")) + "|" +
                    str(row.get("entity_type")) + "|" +
                    str(row.get("entity_id")) + "|" +
                    str(row.get("created_at"));
                String computedHash = sha256(recordContent);

                if (!computedHash.equals(storedCurrHash)) {
                    brokenAt = ((Number) row.get("id")).longValue();
                    break;
                }

                expectedPrevHash = storedCurrHash;
            }

            result.put("valid", brokenAt == null);
            result.put("totalChecked", totalChecked);
            result.put("brokenAt", brokenAt);
            result.put("algorithm", HASH_ALGORITHM);
        } catch (Exception e) {
            log.error("哈希链验证失败", e);
            result.put("valid", false);
            result.put("error", e.getMessage());
            result.put("totalChecked", totalChecked);
        }

        return result;
    }

    private static String str(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }
}
