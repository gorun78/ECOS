package com.chinacreator.gzcm.engine.ai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F06-01 🔄（PMO-74 W140-5，§X.9.0）— Agent 工具<b>裁决咽喉闸</b>（由 H8-T4
 * {@code AgentToolSqlSecurityGate} 升格：类语义从"SQL 安全闸" → "工具裁决闸"）。
 *
 * <p>铁律 §2.4：安全裁决外置到 security-engine，引擎内不重复实现权限判定；
 * <b>security-engine 不可用 / 超时（2s）/ 响应不可解析 → 一律 FAIL_CLOSED（fail-closed，
 * §2.4-6 宁可误拒不可误放）</b>。本闸只负责"该不该放行 + 为什么"；ALLOW / DENY /
 * FAIL_CLOSED 三态的 GUARDRAIL_EVAL 审计事件由咽喉侧接线（F06-03，另批）。主体
 * userId/tenantId 一律由调用方从 token 上下文
 * （{@code UserContext}/{@code SecurityContext}/{@code TenantContextHolder}）
 * 传入，禁取请求体/LLM 入参自报值（PMO-74 H9-T1）。</p>
 *
 * <h3>两段 API（严格分工，避免同仓两套裁决 + 多次裁决）</h3>
 * <ol>
 *   <li>{@link #adjudicate(String, Map, String, String)} — <b>对所有工具类型</b>的唯一
 *       ABAC 裁决（§4.1 Y1 / §X.9.0-1）。ABS 咽喉 {@code policyEvaluate} 阶段对每个工具
 *       恰好调一次（不变式 I-1：一次工具执行 ↔ 恰好一条 GUARDRAIL_EVAL）。此不做 SQL 校验。</li>
 *   <li>{@link #reviewSqlStatic(String, String, Collection, String, String)} — <b>仅 SQL 型
 *       工具</b>的静态只读校验 + 表/列白名单子步骤（不重复 ABAC；ABAC 已由 adjudicate 完成）。
 *       把原"SQL 安全闸"的白名单校验降为其子步骤（§X.9.0-2）。</li>
 * </ol>
 *
 * <p>超时按 PRD 定 <b>2000ms</b>（原 5000ms，X-19）——由 {@link AiSecurityEngineClient}
 * 经 {@code service.security.timeout-ms}（该值缺省已同步调为 2000ms）统一施加，本闸不再
 * 自构 RestTemplate，ABAC 一律经 {@link AiSecurityEngineClient#evaluate}（§2.4-7 单通道）。</p>
 */
@Component
public class AgentToolPolicyGate {

    private static final Logger log = LoggerFactory.getLogger(AgentToolPolicyGate.class);

    /** ABAC 动作名（security 侧策略可按此授权/审计过滤；SQL 静态校验不再独立调 ABAC） */
    public static final String ACTION = "agent:tool";

    // ── SQL 静态/白名单校验用正则（自 H8-T4 原闸逐字复用，语义不变） ──────────────
    private static final Pattern COMMENT_PATTERN = Pattern.compile("--|/\\*|\\*/|#");
    private static final Pattern TRAILING_SEMICOLON = Pattern.compile(";\\s*$");
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern DANGEROUS_KEYWORDS = Pattern.compile(
            "(?i)\\b(?:DROP|ALTER|TRUNCATE|GRANT|REVOKE|COPY|VACUUM|ANALYZE|REINDEX|CLUSTER|"
            + "REFRESH|CHECKPOINT|LISTEN|NOTIFY|LOCK|INSERT|UPDATE|DELETE|MERGE|CREATE|"
            + "EXEC|EXECUTE|CALL|DO|BEGIN|COMMIT|ROLLBACK|SAVEPOINT|PREPARE|DEALLOCATE|"
            + "RESET|SET|SHUTDOWN|INTO)\\b");
    private static final Pattern DANGEROUS_FUNCTIONS = Pattern.compile(
            "(?i)\\b(?:pg_read_file|pg_read_binary_file|pg_ls_dir|pg_stat_file|pg_sleep|"
            + "pg_advisory_lock|pg_advisory_unlock|lo_import|lo_export|dblink|"
            + "pg_terminate_backend|pg_cancel_backend|pg_create_physical_replication_slot)\\b");
    private static final Pattern TABLE_REF = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+([a-z_][a-z0-9_$]*(?:\\.[a-z_][a-z0-9_$]*)?)");
    private static final Pattern TABLE_ALIAS = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+[a-z_][a-z0-9_$.]*(?:\\s+(?:as\\s+)?([a-z_][a-z0-9_$]*))?");
    private static final Pattern CTE_REF = Pattern.compile(
            "(?i)(?:\\bwith\\b|,)\\s*([a-z_][a-z0-9_$]*)\\s*(?:\\([^)]*\\)\\s*)?as\\s*\\(");
    private static final Pattern WORD_TOKEN = Pattern.compile("[a-z_][a-z0-9_$]*");

    /** SQL 关键字/内建函数/字面量词（列白名单扫描时视为合法标识符） */
    private static final Set<String> SQL_WORDS = Set.of(
            "select", "from", "where", "and", "or", "not", "in", "is", "null", "like", "ilike",
            "between", "exists", "case", "when", "then", "else", "end", "as", "on", "join",
            "inner", "left", "right", "full", "outer", "cross", "natural", "lateral", "group",
            "by", "order", "having", "limit", "offset", "fetch", "next", "first", "last", "only",
            "distinct", "with", "recursive", "union", "all", "any", "some", "except", "intersect",
            "asc", "desc", "cast", "count", "sum", "avg", "min", "max", "coalesce", "nullif",
            "greatest", "least", "round", "abs", "floor", "ceil", "lower", "upper", "trim",
            "length", "substr", "substring", "position", "concat", "concat_ws", "now", "current_date",
            "current_timestamp", "current_time", "localtime", "localtimestamp", "date_trunc",
            "date_part", "extract", "year", "month", "day", "hour", "minute", "second", "true",
            "false", "unknown", "over", "partition", "rows", "range", "preceding", "following",
            "current", "row_number", "rank", "dense_rank", "percent_rank", "cume_dist", "ntile",
            "lag", "lead", "array_agg", "string_agg", "json_agg", "jsonb_agg", "json_build_object",
            "jsonb_build_object", "json_object_agg", "to_char", "to_date", "to_timestamp",
            "interval", "date", "timestamp", "time", "boolean", "integer", "int", "bigint",
            "smallint", "numeric", "decimal", "real", "double", "precision", "varchar", "char",
            "text", "uuid", "json", "jsonb", "bytea", "using", "filter", "within", "if", "ifnull",
            "nvl", "isnull", "nulls", "distinct_from", "escape", "similar", "unnest", "generate_series");

    private final AgentToolSqlWhitelist whitelist;
    private final AiSecurityEngineClient securityEngineClient;
    private final int effectiveTimeoutMs;

    public AgentToolPolicyGate(
            AgentToolSqlWhitelist whitelist,
            AiSecurityEngineClient securityEngineClient,
            @Value("${service.security.timeout-ms:2000}") int timeoutMs) {
        // ABAC 的实际 2s 截止由 AiSecurityEngineClient 经 service.security.timeout-ms
        // 统一施加（§2.4-7 单通道）；此处记录 effective 值供诊断/验收断言（X-19 = 2000ms）。
        this.whitelist = whitelist;
        this.securityEngineClient = securityEngineClient;
        this.effectiveTimeoutMs = timeoutMs;
    }

    /** 生效 ABAC 截止（ms）——验收 X-19 断言其 = 2000 */
    public int getEffectiveTimeoutMs() {
        return effectiveTimeoutMs;
    }

    /** 裁决结论：allowed + 人类可读 reason（deny/fail 路径非空） */
    public record Decision(boolean allowed, String reason) {
        static Decision allow() { return new Decision(true, null); }
        static Decision deny(String reason) { return new Decision(false, reason); }
    }

    // ═══════════ ① 全工具类型 ABAC 裁决（唯一入口，X-11） ═══════════

    /**
     * 对所有工具类型执行唯一 ABAC 裁决 + 审计留痕。
     *
     * @param toolName    工具名（资源标识，非用户自报主体）
     * @param attrs       四段载荷投影后的 attributes（resource/environment/operation 等；可空）
     * @param userId      主体 —— 必须由调用方从 token 上下文取得（禁请求体自报）
     * @param tenantId    租户 —— TenantContextHolder（token 链路），可为 null
     * @return ALLOW / DENY / FAIL_CLOSED（不可用/超时/不可解析 → FAIL_CLOSED，§§4.2 + X-19）
     */
    public Decision adjudicate(String toolName, Map<String, Object> attrs,
                               String userId, String tenantId) {
        // 0) 主体必须来自 token 上下文（无主体 → 无法裁决 → FAIL_CLOSED，禁匿名放行）
        if (userId == null || userId.isBlank() || "anonymousUser".equals(userId)) {
            log.warn("F06-01 FAIL_CLOSED: 缺少 token 主体（userId=null/anonymous），拒绝执行工具: tool={}",
                    toolName);
            return Decision.deny("无 token 主体，禁止执行（GUARDRAIL_FAIL_CLOSED，fail-closed §2.4-6）");
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("resource", "agent_tool:" + toolName);
        if (attrs != null) merged.putAll(attrs);

        // 1) ABAC 裁决（不可用/超时/不可解析/明确 DENY → false，fail-closed）
        boolean allowed = securityEngineClient.evaluate(userId, tenantId, ACTION, merged);

        // 2) 审计留痕（§2.4-5）：allow / deny 均经 client.audit 走 Kafka ecos.audit
        String verdict = allowed ? "ALLOW" : "DENY";
        try {
            securityEngineClient.audit(userId, ACTION + ":" + toolName,
                    "tool=" + toolName, verdict);
        } catch (Exception e) {
            log.warn("F06-01 审计留痕发送失败（不阻塞裁决结果）: tool={}", toolName, e);
        }
        if (!allowed) {
            log.warn("F06-01 裁决拒绝: tool={} user={} (GUARDRAIL_DENIED / GUARDRAIL_FAIL_CLOSED)",
                    toolName, userId);
            return Decision.deny("security-engine policy-engine/evaluate 裁决拒绝或不可用（默认 DENY）");
        }
        return Decision.allow();
    }

    // ═══════════ ② SQL 型工具静态+白名单子步骤（不再自做 ABAC） ═══════════

    /**
     * 仅 SQL 型工具的静态只读校验 + 表/列白名单。<b>不做 ABAC</b>（已 {@link #adjudicate} 完成），
     * 避免同一执行两次裁决（不变式 I-1）。静态/白名单任一命中即拒。
     *
     * @param sql         待执行 SQL 全文（不信任来源：schema 定义或 LLM 入参一律同等校验）
     * @param boundParams schema 声明的绑定参数/列名（可空）
     * @param toolName    工具名（仅用于日志）
     * @param userId      主体（仅用于日志；此处不再重复裁决）
     */
    public Decision reviewSqlStatic(String sql, Collection<String> boundParams,
                                    String toolName, String userId) {
        // 1) 静态只读校验
        Decision staticD = staticCheck(sql);
        if (!staticD.allowed()) return staticD;

        // 2) 表白名单
        String masked = maskLiterals(sql).toLowerCase(Locale.ROOT);
        Set<String> cteNames = extractCteNames(masked);
        Set<String> tables = new LinkedHashSet<>();
        Matcher tm = TABLE_REF.matcher(masked);
        while (tm.find()) {
            String t = tm.group(1);
            if (t == null || cteNames.contains(t)) continue;
            tables.add(t);
        }
        if (tables.isEmpty()) {
            return Decision.deny("未识别到受白名单管控的表引用（禁止无表执行或 SQL 形态超出受控范围）");
        }
        boolean allColumnsMode = false;
        Set<String> allowedColumnsUnion = new LinkedHashSet<>();
        for (String table : tables) {
            if (!AgentToolSqlWhitelist.TABLE_IDENT.matcher(table).matches()) {
                return Decision.deny("非法表名形态: " + table);
            }
            Set<String> cols = whitelist.allowedColumns(table);
            if (cols == null) {
                log.warn("F06-01 DENY: 表未列入白名单: tool={} user={} table={}", toolName, userId, table);
                return Decision.deny("表未列入 Agent 工具 SQL 白名单: " + table);
            }
            if (cols.contains(AgentToolSqlWhitelist.ALL_COLUMNS)) {
                allColumnsMode = true;
            } else {
                allowedColumnsUnion.addAll(cols);
            }
        }

        // 3) 列白名单（存在显式列清单的表时启用标识符扫描）
        if (!allColumnsMode) {
            Set<String> known = new LinkedHashSet<>(SQL_WORDS);
            known.addAll(allowedColumnsUnion);
            for (String table : tables) {
                known.add(table);
                known.add(table.substring(table.lastIndexOf('.') + 1));
            }
            known.addAll(cteNames);
            known.addAll(extractAliases(masked, cteNames));
            Matcher wm = WORD_TOKEN.matcher(masked);
            while (wm.find()) {
                String token = wm.group();
                if (!known.contains(token)) {
                    return Decision.deny("列/标识符未获白名单授权: " + token
                            + "（引用表 " + tables + "，全列授权需白名单显式声明 \"*\"）");
                }
            }
        }

        // 4) 绑定参数列名校验
        if (boundParams != null) {
            for (String p : boundParams) {
                if (p == null || p.isBlank()) continue;
                boolean ok = allColumnsMode
                        ? AgentToolSqlWhitelist.COLUMN_IDENT.matcher(p).matches()
                        : allowedColumnsUnion.contains(p.toLowerCase(Locale.ROOT));
                if (!ok) {
                    return Decision.deny("绑定参数不是白名单授权列: " + p);
                }
            }
        }
        return Decision.allow();
    }

    // ── 静态校验（自原 H8-T4 闸逐字复用） ────────────────────────────────

    private Decision staticCheck(String sql) {
        if (sql == null || sql.isBlank()) {
            return Decision.deny("SQL 为空");
        }
        if (COMMENT_PATTERN.matcher(sql).find()) {
            return Decision.deny("检测到注释符（-- /**/ #），Agent SQL 不允许注释（规避检测风险）");
        }
        String trimmed = sql.trim();
        String noTrailing = TRAILING_SEMICOLON.matcher(trimmed).replaceAll("");
        if (noTrailing.indexOf(';') >= 0) {
            return Decision.deny("检测到多条语句（分号拼接），仅允许单条 SELECT");
        }
        String upper = noTrailing.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            return Decision.deny("Agent 工具 SQL 仅允许 SELECT / WITH 只读查询");
        }
        String masked = maskLiterals(noTrailing);
        Matcher km = DANGEROUS_KEYWORDS.matcher(masked);
        if (km.find()) {
            return Decision.deny("检测到非只读/危险关键字: " + km.group());
        }
        Matcher fm = DANGEROUS_FUNCTIONS.matcher(masked);
        if (fm.find()) {
            return Decision.deny("检测到文件系统/运维危险函数: " + fm.group());
        }
        return Decision.allow();
    }

    /** 掩掉字符串字面量，避免字面量内容误触关键字/表名扫描 */
    private String maskLiterals(String sql) {
        return STRING_LITERAL.matcher(sql).replaceAll("' '");
    }

    private Set<String> extractCteNames(String maskedLower) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = CTE_REF.matcher(maskedLower);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    private Set<String> extractAliases(String maskedLower, Set<String> cteNames) {
        Set<String> aliases = new LinkedHashSet<>();
        Matcher m = TABLE_ALIAS.matcher(maskedLower);
        while (m.find()) {
            String alias = m.group(1);
            if (alias != null && !SQL_WORDS.contains(alias) && !cteNames.contains(alias)) {
                aliases.add(alias);
            }
        }
        return aliases;
    }
}
