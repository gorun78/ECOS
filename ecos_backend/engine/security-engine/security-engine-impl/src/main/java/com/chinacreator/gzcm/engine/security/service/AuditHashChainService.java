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
        return verifyHashChainRange(null, null);
    }

    /**
     * 详细设计-01 C.5 / D.2 {@code POST /api/v1/security/audit/verify-chain} 区间段验证。
     *
     * <p>入参 {@code from}/{@code to} 均可空（null = 端点开放）；返回
     * {@code {valid, totalChecked, brokenAt[], algorithm, from, to}}，其中
     * {@code brokenAt} 是<b>列表</b>（本区间内首次/多次断裂点，最多记录 10 处），
     * 空列表 = valid=true。</p>
     */
    public Map<String, Object> verifyHashChainRange(Long fromId, Long toId) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        int totalChecked = 0;
        java.util.List<Long> brokenAt = new java.util.ArrayList<>();
        String expectedPrevHash = EMPTY_HASH;
        boolean hasRange = fromId != null || toId != null;

        // 取区间内的 prev_hash 起点：fromId 之前的最后一行 curr_hash（若 from 有值）
        if (fromId != null) {
            try {
                java.util.List<String> prevs = jdbc.queryForList(
                        "SELECT curr_hash FROM ecos_audit_log WHERE id < ? AND curr_hash IS NOT NULL ORDER BY id DESC LIMIT 1",
                        String.class, fromId);
                if (!prevs.isEmpty()) {
                    expectedPrevHash = prevs.get(0);
                }
            } catch (Exception e) {
                log.warn("verify-chain 读取 fromId 前链头失败（用 EMPTY 头）: {}", e.getMessage());
            }
        }

        StringBuilder where = new StringBuilder();
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (hasRange) {
            where.append(" WHERE 1=1");
            if (fromId != null) { where.append(" AND id >= ?"); args.add(fromId); }
            if (toId != null)   { where.append(" AND id <= ?"); args.add(toId); }
        }

        try {
            String sql = "SELECT id, username, operation, entity_type, entity_id, created_at::text as created_at, "
                    + "prev_hash, curr_hash FROM ecos_audit_log" + where
                    + " ORDER BY id ASC LIMIT 10000";
            var rows = args.isEmpty()
                    ? jdbc.queryForList(sql)
                    : jdbc.queryForList(sql, args.toArray());

            for (Map<String, Object> row : rows) {
                totalChecked++;
                String storedPrevHash = str(row.get("prev_hash"));
                String storedCurrHash = str(row.get("curr_hash"));
                if (storedCurrHash.isEmpty()) {
                    // 尚未戳记（写入侧尚未 stamp）→ 跳过但不判定断裂
                    continue;
                }
                // 严格区间起点：忽略 expected 断链（区间外既有可以断裂，不属于本区间判定）
                boolean firstInRange = totalChecked == 1;
                if (!firstInRange && !expectedPrevHash.equals(storedPrevHash) && brokenAt.size() < 10) {
                    brokenAt.add(((Number) row.get("id")).longValue());
                    continue;
                }
                String recordContent = storedPrevHash + "|" +
                        str(row.get("username")) + "|" +
                        str(row.get("operation")) + "|" +
                        str(row.get("entity_type")) + "|" +
                        str(row.get("entity_id")) + "|" +
                        str(row.get("created_at"));
                String computedHash = sha256(recordContent);
                if (!computedHash.equals(storedCurrHash) && brokenAt.size() < 10) {
                    brokenAt.add(((Number) row.get("id")).longValue());
                }
                expectedPrevHash = storedCurrHash;
            }
            result.put("valid", brokenAt.isEmpty());
            result.put("totalChecked", totalChecked);
            result.put("brokenAt", brokenAt);
            result.put("algorithm", HASH_ALGORITHM);
            if (hasRange) {
                result.put("from", fromId);
                result.put("to", toId);
            }
        } catch (Exception e) {
            log.error("哈希链区间验证失败", e);
            result.put("valid", false);
            result.put("error", e.getMessage());
            result.put("totalChecked", totalChecked);
            result.put("brokenAt", brokenAt);
        }
        return result;
    }

    private static String str(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    static String sha256(String input) {
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
