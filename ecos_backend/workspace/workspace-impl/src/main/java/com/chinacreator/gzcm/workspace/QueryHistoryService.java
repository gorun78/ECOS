package com.chinacreator.gzcm.workspace;

import com.chinacreator.gzcm.common.context.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 查询历史服务 — <b>DB 落库实现</b>（F07-21 / C167 / W185，X-55 内存态退役）。
 *
 * <p><b>安全红线（E-5 / X-55 定版）</b>：
 * <ul>
 *   <li><b>禁存查询原文/结果集/自然语言问题原文</b> — {@code sql_digest} 列存
 *       SHA-256 hex 摘要（64 字符），{@code question}/{@code queryJson} 仅作
 *       日志侧瞬时量，不入库。与分册 06 F06-03 审计载荷"ID 与摘要，禁结果原文"
 *       同规则。</li>
 *   <li>{@code subject_id} 为执行主体<b>用户 ID</b>（非 PII），来自
 *       {@link SecurityContextHolder#getContext()}，无匿名上下文时落
 *       {@code "anonymous"}（V212 {@code subject_id VARCHAR(36) NOT NULL} 满足约束）。</li>
 *   <li>{@code trace_id} 由 gated by {@link TraceContext#current()}（MDC key
 *       {@code traceId}），与 F07-21 要点 2 "traceId 全链贯通" 同源，不另造生成器。</li>
 * </ul>
 *
 * <p><b>服务行为契约（全部四操作均<b>切断线 fail-soft</b>，与 C150 岛标降级同口径）</b>：
 * <ul>
 *   <li>{@link #save(String, String, int)}：任何 DB 异常（V212 表未建 / PG 短暂不可用）
 *       记 WARN 并返回已生成 id 的记录，调用方（ObjectQL:105，自身另有 catch 兜底）不受影响。</li>
 *   <li>{@link #getHistory(int)} / {@link #delete(String)} / {@link #clear()}：DB 异常降级
 *       （空清单 / false / no-op）+ WARN 日志，不抛 500 拖垮 `/api/query/history` 端点。
 *       V212 对 PG 实跑后自动恢复正常语义；V212 未实跑期间这四处降级是<b>可观测的切断线形态</b>，
 *       <b>不构成</b>假阴性（真库落库/回读由 F07-21 的 P-2 container IT 验证，本窗不建）。</li>
 *   <li>方法签名与 X-55 前保持<b>不改变</b>（API 只增不改铁律）；读侧 VO 追加
 *       {@code sqlDigest/traceId/subjectId} 字段（新增不破坏旧前端固定字段读取）。</li>
 * </ul>
 *
 * <p><b>切断线说明</b>：V212 `public.ecos_scenario_query_history` 迁移脚本已落、
 * <b>未对 PG 实跑</b>（§14.4 未授权项）。本服务的持久化语义在实跑前以 fail-soft 降级形态
 * 交付，不 mock 也不伪造空表成功；实跑授权后四条路径转真语义，无需再改本服务。</p>
 */
@Service
public class QueryHistoryService {

    private static final Logger log = LoggerFactory.getLogger(QueryHistoryService.class);

    /** 表名常量与 V212 迁移名严格一致（ST07 控制域落 public 显式限定，禁裸表名 — 数据库访问规范附则 1） */
    private static final String TABLE = "public.ecos_scenario_query_history";

    private final JdbcTemplate jdbc;

    public QueryHistoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 保存一条查询记录（best-effort）。
     *
     * <p>调用方 {@code ObjectQLController:105} 位于成功分支内，历史写失败不
     * 影响已返回的 rows，故 fail-soft。</p>
     */
    public QueryRecord save(String question, String queryJson, int resultCount) {
        String id = newFullId();
        String traceId = normalizeTraceId(TraceContext.current());
        String subjectId = currentSubjectId();
        String sqlDigest = sha256Hex(trimToNull(queryJson));
        QueryRecord r = new QueryRecord(question, queryJson, resultCount);
        r.setId(id);
        r.setTraceId(traceId);
        r.setSubjectId(subjectId);
        r.setSqlDigest(sqlDigest);
        try {
            jdbc.update(
                "INSERT INTO " + TABLE
                    + " (id, trace_id, subject_id, sql_digest, row_count, create_time, update_time, create_by, update_by, is_deleted, domain, version_no)"
                    + " VALUES (?, ?, ?, ?, ?, LOCALTIMESTAMP, LOCALTIMESTAMP, ?, ?, 0, 'default', '1')",
                id, traceId, subjectId, sqlDigest, resultCount, subjectId, subjectId);
            log.debug("QueryHistory: saved id={} trace={} rows={}", id, traceId, resultCount);
        } catch (Exception e) {
            // V212 未实跑切断线 / PG 短暂不可用 → fail-soft；本行 <b>非</b> 假阴性
            log.warn("QueryHistory save fell back (fail-soft): {} — record id={} not persisted this call",
                e.getClass().getSimpleName(), id);
        }
        return r;
    }

    /**
     * 获取最近 N 条历史记录（按 create_time 倒序，软删过滤）。
     *
     * <p>读侧不回读问题原文/查询原文（禁存已剥离）；{@code question} / {@code queryJson}
     * 保持 null（前端需调整展示口径——历史列表只显示 digest 摘要 + 行数 + traceId/主体，
     * 满足 X-55 "多实例一致" 且 <b>不暴露查询内容</b>）。这是 <b>安全红线改进</b>，
     * 视觉上比旧版（明文 queryJson + question）少信息。</p>
     */
    public List<QueryRecord> getHistory(int limit) {
        int capped = Math.max(0, Math.min(limit, 1000));
        List<QueryRecord> out = new ArrayList<>();
        try {
            jdbc.query(
                "SELECT id, trace_id, subject_id, sql_digest, row_count, create_time"
                    + " FROM " + TABLE
                    + " WHERE is_deleted = 0"
                    + " ORDER BY create_time DESC, id DESC"
                    + " LIMIT ?",
                rs -> {
                    QueryRecord r = new QueryRecord();
                    r.setId(rs.getString("id"));
                    r.setTraceId(rs.getString("trace_id"));
                    r.setSubjectId(rs.getString("subject_id"));
                    r.setSqlDigest(rs.getString("sql_digest"));
                    r.setResultCount(rs.getInt("row_count"));
                    java.sql.Timestamp ts = rs.getTimestamp("create_time");
                    r.setTimestamp(ts != null ? ts.toLocalDateTime() : null);
                    r.setQuestion(null);      // 禁原文回读（E-5）
                    r.setQueryJson(null);     // 禁原文回读（E-5）
                    out.add(r);
                },
                capped);
            return out;
        } catch (Exception e) {
            // 切断线：V212 未实跑时表不存在 → 降级空清单（同 C150 岛标"读失败降级空清单"口径），
            // 不拖垮 /api/query/history 端点；V212 落地后即正常回读。
            log.warn("QueryHistory getHistory degraded to empty (cutover-bound): {}", e.getClass().getSimpleName());
            return out;
        }
    }

    /**
     * 软删指定记录，成功返 true。
     */
    public boolean delete(String id) {
        if (id == null || id.isBlank()) return false;
        try {
            int affected = jdbc.update(
                "UPDATE " + TABLE
                    + " SET is_deleted = 1, update_time = LOCALTIMESTAMP, update_by = ?"
                    + " WHERE id = ? AND is_deleted = 0",
                currentSubjectId(), id);
            if (affected > 0) {
                log.debug("QueryHistory: soft-deleted id={}", id);
            }
            return affected > 0;
        } catch (Exception e) {
            // 切断线：V212 未实跑 → 无法删（返回 false，不抛 500）
            log.warn("QueryHistory delete degraded (cutover-bound): {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 软删全部非已删记录。
     *
     * <p>审计敏感：此处 <b>仅 mock 阶段使用</b>（V212 上线后 code 保留但建议改为
     * 定时任务清理超期数据），不做批量 DELETE 硬删。</p>
     */
    public void clear() {
        try {
            jdbc.update(
                "UPDATE " + TABLE
                    + " SET is_deleted = 1, update_time = LOCALTIMESTAMP, update_by = ?"
                    + " WHERE is_deleted = 0",
                currentSubjectId());
        } catch (Exception e) {
            log.warn("QueryHistory clear degraded (cutover-bound): {}", e.getClass().getSimpleName());
        }
    }

    // ─────────────── 审计字段 util ──────────────────────────────────────

    private static String newFullId() {
        return UUID.randomUUID().toString(); // 36 char, MC01 应用侧 UUID
    }

    /** V212 {@code subject_id VARCHAR(36) NOT NULL} — 无上下文时落 "anonymous" 不空。 */
    static String currentSubjectId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                String p = String.valueOf(auth.getPrincipal());
                if (p != null && !p.isBlank() && !"anonymousUser".equals(p)) {
                    return p.length() > 36 ? p.substring(0, 36) : p;
                }
            }
        } catch (Exception ignored) {
            // SecurityContext 不可用属残留环境（比如 independent 进程外单测），不阻断历史写
        }
        return "anonymous";
    }

    /** V212 {@code trace_id VARCHAR(64)} — 空值允许，traceId 过长时截断不拒写。 */
    private static String normalizeTraceId(String t) {
        if (t == null || t.isBlank()) return null;
        String s = t.trim();
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    /** V212 {@code sql_digest VARCHAR(128)} — SHA-256 hex = 64 字符，稳定且不可逆摘要；空白/空输入落 null（V212 允许，摘要对空串无意义）。 */
    static String sha256Hex(String input) {
        if (input == null) return null;
        String t = input.trim();
        if (t.isEmpty()) return null;
        try {
            MessageDigest md5 = MessageDigest.getInstance("SHA-256");
            byte[] digest = md5.digest(t.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // JCE 缺 SHA-256 属极度假设，上报为 IllegalStateException
            throw new IllegalStateException("SHA-256 unavailable on JRE — 不应发生", e);
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
