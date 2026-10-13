package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.engine.security.dao.AuditDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final AuditDao auditDao;

    public AuditHashChainService(AuditDao auditDao) {
        this.auditDao = auditDao;
    }

    /**
     * 为新审计记录计算并写入哈希链
     * 在审计日志 INSERT 之后调用此方法更新哈希字段
     */
    public void stampHashChain(long auditLogId) {
        try {
            // 获取当前记录内容
            Map<String, Object> row = auditDao.findAuditRowContent(auditLogId);

            // 获取前一条记录的 curr_hash
            String prevHash = auditDao.findPrevHash(auditLogId, EMPTY_HASH);
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
            auditDao.updateHashFields(prevHash, currHash, HASH_ALGORITHM, auditLogId);
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
            var rows = auditDao.findHashChainRows();

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

    /**
     * 详设-01 B 章 3.2 交互时序⑦：增量哈希链区间校验 POST verify-chain {from,to}。
     *
     * <p>口径：以主键 id 闭区间 [fromId,toId]、id 升序逐条重算
     * curr_hash = SHA-256(prev_hash||username||operation||entity_type||entity_id||created_at)
     * 并比对 prev/curr 双向链接；区间起点锚 = 区间前一条已存记录的 curr_hash
     * （与 {@code stampHashChain} 的戳记口径一致）。与全量
     * {@link #verifyHashChain()}（首断即停）不同，本方法遍历整个区间并汇总
     * 全部断点 id（契约响应 {valid, brokenAt[]}）。</p>
     *
     * @return { valid, brokenAt: List&lt;Long&gt;, totalChecked, from, to, algorithm }
     */
    public Map<String, Object> verifyHashChainRange(long fromId, long toId) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        java.util.LinkedHashSet<Long> brokenAt = new java.util.LinkedHashSet<>();
        int totalChecked = 0;

        try {
            if (fromId > toId) {
                throw new IllegalArgumentException("from 不能大于 to: from=" + fromId + ", to=" + toId);
            }

            // 区间锚：from 前一条记录的已存 curr_hash（首段回落空串哈希）
            String expectedPrevHash = auditDao.findPrevHash(fromId, EMPTY_HASH);
            if (expectedPrevHash == null) expectedPrevHash = EMPTY_HASH;

            var rows = auditDao.findHashChainRowsInRange(fromId, toId);

            for (Map<String, Object> row : rows) {
                totalChecked++;
                long id = ((Number) row.get("id")).longValue();
                String storedPrevHash = str(row.get("prev_hash"));
                String storedCurrHash = str(row.get("curr_hash"));

                // ① 链接校验：存储的 prev_hash 必须等于链上期望值
                if (!expectedPrevHash.equals(storedPrevHash)) {
                    brokenAt.add(id);
                }

                // ② 内容校验：以存储的 prev 为输入重算 curr（与戳记口径逐字一致）
                String recordContent = storedPrevHash + "|" +
                    str(row.get("username")) + "|" +
                    str(row.get("operation")) + "|" +
                    str(row.get("entity_type")) + "|" +
                    str(row.get("entity_id")) + "|" +
                    str(row.get("created_at"));
                String computedHash = sha256(recordContent);
                if (!computedHash.equals(storedCurrHash)) {
                    brokenAt.add(id);
                }

                // 向前推进：以存储值续链（区间校验目标是逐条比对，不是重放重建）
                expectedPrevHash = storedCurrHash;
            }

            result.put("valid", brokenAt.isEmpty());
            result.put("brokenAt", new java.util.ArrayList<>(brokenAt));
            result.put("totalChecked", totalChecked);
            result.put("from", fromId);
            result.put("to", toId);
            result.put("algorithm", HASH_ALGORITHM);
        } catch (IllegalArgumentException e) {
            result.put("valid", false);
            result.put("error", e.getMessage());
            result.put("brokenAt", new java.util.ArrayList<>());
            result.put("totalChecked", totalChecked);
            result.put("from", fromId);
            result.put("to", toId);
        } catch (Exception e) {
            log.error("审计哈希链区间校验失败: from={}, to={}", fromId, toId, e);
            result.put("valid", false);
            result.put("error", e.getMessage());
            result.put("brokenAt", new java.util.ArrayList<>(brokenAt));
            result.put("totalChecked", totalChecked);
            result.put("from", fromId);
            result.put("to", toId);
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
