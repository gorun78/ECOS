package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.dto.LifecycleAssetVO;
import com.chinacreator.gzcm.engine.kb.dto.LifecycleAuditEntryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 知识资产生命周期服务 — {@code GET /api/v1/knowledge/assets} 与
 * {@code GET /api/v1/knowledge/lifecycle/audit}（方案 §6.2 K6 知识治理）。
 *
 * <p><b>数据来源（真实，无 DEMO 假数据）</b>：
 * <ul>
 *   <li><b>资产列表</b>：{@code ecos_knowledge.knowledge_article}（知识文章 = 知识资产，
 *       含 {@code status} 生命周期字段）——仓库中语义最贴合的既有资产表；</li>
 *   <li><b>审计</b>：{@code ecos_knowledge.kg_sync_log}（B1/B3-2 真实作业台账）投影，
 *       详见 {@link LifecycleAuditEntryVO} 的口径声明；仓库无专表记录资产状态变更，
 *       故不新建表（遵守「Schema 尽量不要新增表」）。</li>
 * </ul>
 *
 * <p>列表与审计均支持 {@code pageNum}/{@code pageSize} 分页（pageSize 上限 100，防慢查询）
 * 与（资产）状态过滤。</p>
 *
 * @since B5-2（D6）
 */
@Service
public class KnowledgeLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeLifecycleService.class);

    /** 合法生命周期状态（与前端 LIFECYCLE_STATES 对齐） */
    private static final Set<String> LIFECYCLE_STATES = Set.of("draft", "active", "deprecated", "archived");

    /** 默认分页大小 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 分页大小上限（防慢查询） */
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbc;

    public KnowledgeLifecycleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 知识资产生命周期列表。
     *
     * @param status   生命周期状态过滤（可空；取值须 ∈ {draft,active,deprecated,archived}）
     * @param pageNum  页码（从 1 起，非法值按 1）
     * @param pageSize 每页条数（默认 20，收敛 [1,100]）
     * @return 资产条目（真实数据，无数据时为空列表）
     */
    public List<LifecycleAssetVO> listAssets(String status, int pageNum, int pageSize) {
        int effectivePage = Math.max(pageNum, 1);
        int effectiveSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int offset = (effectivePage - 1) * effectiveSize;

        String normalizedStatus = normalizeState(status);
        List<String> rawStatuses = normalizedStatus == null ? List.of() : rawStatuses(normalizedStatus);

        List<Map<String, Object>> rows;
        if (normalizedStatus == null) {
            rows = jdbc.queryForList(
                    "SELECT id, title, status, updated_at FROM ecos_knowledge.knowledge_article "
                            + "ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                    effectiveSize, offset);
        } else {
            if (rawStatuses.isEmpty()) {
                return List.of();
            }
            String placeholders = String.join(",", java.util.Collections.nCopies(rawStatuses.size(), "?"));
            List<Object> args = new ArrayList<>(rawStatuses);
            args.add(effectiveSize);
            args.add(offset);
            rows = jdbc.queryForList(
                    "SELECT id, title, status, updated_at FROM ecos_knowledge.knowledge_article "
                            + "WHERE lower(status) IN (" + placeholders + ") "
                            + "ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                    args.toArray());
        }

        List<LifecycleAssetVO> assets = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            LifecycleAssetVO asset = new LifecycleAssetVO();
            asset.setId(str(row.get("id")));
            asset.setName(str(row.get("title")));
            asset.setType("ARTICLE");
            asset.setState(toAssetState(str(row.get("status"))));
            asset.setUpdatedAt(toIso(row.get("updated_at")));
            asset.setUpdatedBy(null);
            assets.add(asset);
        }
        log.info("生命周期资产列表: status={} pageNum={} pageSize={} returned={}",
                normalizedStatus, effectivePage, effectiveSize, assets.size());
        return assets;
    }

    /**
     * 生命周期审计列表（kg_sync_log 投影）。
     *
     * @param pageNum  页码（从 1 起，非法值按 1）
     * @param pageSize 每页条数（默认 20，收敛 [1,100]）
     * @return 审计条目（真实台账投影，无记录时为空列表）
     */
    public List<LifecycleAuditEntryVO> listAudit(int pageNum, int pageSize) {
        int effectivePage = Math.max(pageNum, 1);
        int effectiveSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int offset = (effectivePage - 1) * effectiveSize;

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, object_type, op, status, created_at FROM ecos_knowledge.kg_sync_log "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                effectiveSize, offset);

        List<LifecycleAuditEntryVO> entries = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            LifecycleAuditEntryVO entry = new LifecycleAuditEntryVO();
            entry.setId("sync-" + str(row.get("id")));
            entry.setAssetId(str(row.get("object_type")));
            entry.setFrom("draft");
            entry.setTo(toStateFromRunStatus(str(row.get("status"))));
            entry.setOperator("kb-engine");
            entry.setAt(toIso(row.get("created_at")));
            entry.setAction(str(row.get("op")));
            entries.add(entry);
        }
        log.info("生命周期审计列表: pageNum={} pageSize={} returned={}",
                effectivePage, effectiveSize, entries.size());
        return entries;
    }

    // ── 私有辅助 ──────────────────────────────────────────────

    /** 校验并归一化生命周期状态；空返回 null，非法抛 {@link ValidationException}。 */
    private String normalizeState(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!LIFECYCLE_STATES.contains(normalized)) {
            throw new ValidationException("status",
                    "非法生命周期状态: " + status + "（合法值: draft/active/deprecated/archived）");
        }
        return normalized;
    }

    /** 生命周期状态 → knowledge_article.status 的可能原始取值（大小写不敏感）。 */
    private List<String> rawStatuses(String state) {
        switch (state) {
            case "draft":
                return List.of("draft");
            case "active":
                return List.of("active", "published", "ready");
            case "deprecated":
                return List.of("deprecated");
            case "archived":
                return List.of("archived");
            default:
                return List.of();
        }
    }

    /** knowledge_article.status → 生命周期状态（未知值按 draft，避免前端渲染异常）。 */
    private String toAssetState(String rawStatus) {
        String s = rawStatus == null ? "" : rawStatus.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "deprecated":
                return "deprecated";
            case "archived":
                return "archived";
            case "active":
            case "published":
            case "ready":
                return "active";
            default:
                return "draft";
        }
    }

    /** 作业结果 → 生命周期状态（SUCCESS→active / FAILED→deprecated / 其他→draft）。 */
    private String toStateFromRunStatus(String runStatus) {
        String s = runStatus == null ? "" : runStatus.trim().toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(s)) {
            return "active";
        }
        if ("FAILED".equals(s)) {
            return "deprecated";
        }
        return "draft";
    }

    /** JDBC 时间戳 → ISO-8601 字符串（空值返回 null）。 */
    private String toIso(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        return value == null ? null : String.valueOf(value);
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
