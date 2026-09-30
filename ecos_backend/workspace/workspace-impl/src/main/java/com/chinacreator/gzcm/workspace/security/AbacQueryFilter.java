package com.chinacreator.gzcm.workspace.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ABAC 查询过滤器 — 在 ObjectQL/ObjectRuntime 查询时做列裁剪和行过滤。
 *
 * <h3>H10-T1（PMO-74 N7/N16）整改</h3>
 * <p>原实现直查 {@code td_abac_policy} 表、本地解析 environment_condition JSON，
 * 并用 {@code rowFilterSql.replace("${userId}", userId)} 字符串插值（二阶注入面）。
 * 现按架构铁律 §2.4 全部裁决改经 {@link WorkspaceSecurityEngineClient} 调
 * security-engine REST（RLS {@code /api/v1/security/rls/apply} +
 * CLS {@code /api/v1/security/cls/columns}），workspace 进程不再自建 ABAC 解析。</p>
 *
 * <h3>默认 DENY</h3>
 * <ul>
 *   <li>行过滤：security-engine 不可用/拒绝 → {@code "1=0"}（调用方拼进 WHERE 即零行）</li>
 *   <li>列裁剪：security-engine 不可用/拒绝 → 可见列空集 → 返回空行集</li>
 * </ul>
 *
 * <h3>调用契约（保持不变，API 只增不改）</h3>
 * <ul>
 *   <li>{@link #buildRowFilterCondition(String)}：空串=无过滤条件；非空串=追加到
 *       {@code WHERE} 后的片段（由调用方拼接）</li>
 *   <li>{@link #filterColumns(String, List)}：返回列裁剪后的新行列表</li>
 * </ul>
 */
@Component
public class AbacQueryFilter {

    private static final Logger log = LoggerFactory.getLogger(AbacQueryFilter.class);

    private final WorkspaceSecurityEngineClient securityEngineClient;

    public AbacQueryFilter(WorkspaceSecurityEngineClient securityEngineClient) {
        this.securityEngineClient = securityEngineClient;
    }

    // ═══════════════ 公共 API ═══════════════════

    /**
     * 对查询结果做列裁剪：仅保留 security-engine CLS 裁决允许的列。
     *
     * @param entityCode 实体编码（如 "Contract"）
     * @param rows       查询结果行列表
     * @return 裁剪后的行列表（新 List，不影响原数据）；CLS 拒绝/security 不可用 → 空列表（默认 DENY）
     */
    public List<Map<String, Object>> filterColumns(String entityCode, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return rows;

        List<String> allColumns = new ArrayList<>(rows.get(0).keySet());
        List<String> allowed = securityEngineClient.allowedColumns(entityCode, allColumns);
        if (allowed.isEmpty()) {
            // DENY：security-engine 不可用或无可见列 —— 默认拒绝，不返回任何列数据
            log.warn("ABAC列裁剪 DENY（security-engine 不可用/无可见列）: entity={}, rows={} 被置空",
                    entityCode, rows.size());
            return List.of();
        }

        Set<String> allowedLower = new LinkedHashSet<>();
        for (String col : allowed) {
            if (col != null) allowedLower.add(col.toLowerCase());
        }

        List<Map<String, Object>> filtered = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if (key == null || allowedLower.contains(key.toLowerCase())) {
                    cleaned.put(key, entry.getValue());
                }
            }
            filtered.add(cleaned);
        }
        log.debug("ABAC列裁剪(security-engine CLS): entity={}, allowed={}, rows={}",
                entityCode, allowed.size(), rows.size());
        return filtered;
    }

    /**
     * 构建行过滤 SQL WHERE 片段 — 经 security-engine RLS 端点裁决。
     *
     * @param entityCode 实体编码
     * @return SQL WHERE 片段；无策略返回空字符串；security-engine 不可用/DENY 返回 "1=0"
     */
    public String buildRowFilterCondition(String entityCode) {
        String whereClause = securityEngineClient.applyRls(entityCode, entityCode, Map.of());
        if ("1=0".equals(whereClause)) {
            log.warn("ABAC行过滤 DENY（security-engine RLS 拒绝或不可用）: entity={}", entityCode);
        }
        return whereClause;
    }

    /**
     * 获取当前实体需要隐藏的列名集合。
     *
     * <p>H10-T1 后列级隐藏由 security-engine CLS 在 {@link #filterColumns} 内实时裁决，
     * 本方法无列全集上下文、不再返回本地策略推导结果（保留签名以兼容既有调用方）。</p>
     */
    public Set<String> getHiddenColumns(String entityCode) {
        log.debug("getHiddenColumns 已由 security-engine CLS 实时裁决取代: entity={}", entityCode);
        return Set.of();
    }

    /**
     * 策略缓存清空的兼容入口 — 裁决已收敛 security-engine，本地无缓存可刷（保留签名）。
     */
    public void invalidateCache() {
        log.info("ABAC 裁决已收敛 security-engine（无本地缓存需刷新）");
    }
}
