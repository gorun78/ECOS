package com.chinacreator.gzcm.engine.ai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * H8-T4（PMO-74 L3）— Agent 工具 SQL 安全闸：静态只读校验 + 表/列白名单 +
 * security-engine {@code POST /api/v1/security/policy-engine/evaluate} ABAC 裁决。
 *
 * <p>铁律 §2.4：安全裁决外置到 security-engine，引擎内不重复实现权限判定；
 * <b>security-engine 不可用 / 超时 / 返回不可解析 → 一律 DENY（fail-closed，
 * §2.4-6 宁可误拒不可误放）</b>，并 {@code log.warn} 带堆栈。裁决 allow 与 deny
 * 均经 {@link AiSecurityEngineClient#audit} 发 Kafka {@code ecos.audit} 留痕
 * （§2.4-5），主体 userId/tenantId 由调用方从 token 上下文（SecurityContext /
 * UserContext / TenantContextHolder）传入，禁止取请求体自报值。</p>
 *
 * <p>REST 客户端写法参照本模块 {@link AiSecurityEngineClient#evaluate} 与
 * data-engine {@code PipelineSecurityService} 先例（自构带超时 RestTemplate，
 * gateway / aiming / ai-engine-boot 各进程均可装配）。与 AiSecurityEngineClient
 * 的差异：本闸保留并输出异常堆栈（H8-T4 验收要求 fail-closed 日志带堆栈）。</p>
 *
 * <p>闸内规则（全部 fail-closed，任一命中即拒）：</p>
 * <ol>
 *   <li>只读约束：SQL 必须以 SELECT / WITH 开头；</li>
 *   <li>拒绝注释（{@code --} / {@code /* *}{@code /} / {@code #}），拒绝多条语句
 *       （仅允许结尾一个 {@code ;}）；</li>
 *   <li>拒绝 DML/DDL/权限/文件/危险函数关键字：DROP/ALTER/GRANT/REVOKE/COPY/
 *       INSERT/UPDATE/DELETE/TRUNCATE/CREATE/EXEC/CALL/SET/pg_read_file 等；</li>
 *       注意：SELECT 列名如 {@code updated_at} 属合法标识符（词边界不会误杀），
 *       被误拒的裸列名 {@code update}/{@code set} 等应在白名单侧改名或做列授权评审；</li>
 *   <li>FROM/JOIN 引用的每张表（CTE 别名除外）必须在 {@link AgentToolSqlWhitelist}
 *       中显式授权；</li>
 *   <li>列白名单：任一被引用表若为显式列清单（非 {@code "*"}），则 SQL 中出现的
 *       标识符必须 ∈ {授权列 ∪ 表名/别名/CTE ∪ SQL 关键字/函数}，未知标识符拒绝；</li>
 *   <li>schema 声明的绑定参数名同样须为授权列；</li>
 *   <li>security-engine ABAC 裁决（不可用/超时/不可解析 → DENY + 堆栈日志）。</li>
 * </ol>
 */
@Component
public class AgentToolSqlSecurityGate {

    private static final Logger log = LoggerFactory.getLogger(AgentToolSqlSecurityGate.class);

    /** ABAC 动作名（security 侧策略可按此授权/审计过滤） */
    public static final String ACTION = "agent:tool:sql";

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
    private final String securityBaseUrl;
    private final RestTemplate restTemplate;

    public AgentToolSqlSecurityGate(
            AgentToolSqlWhitelist whitelist,
            AiSecurityEngineClient securityEngineClient,
            @Value("${service.security.base-url:http://localhost:18081}") String securityBaseUrl,
            @Value("${service.security.timeout-ms:5000}") int timeoutMs) {
        this.whitelist = whitelist;
        this.securityEngineClient = securityEngineClient;
        this.securityBaseUrl = securityBaseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, timeoutMs));
        factory.setReadTimeout(Math.max(1000, timeoutMs));
        this.restTemplate = new RestTemplate(factory);
    }

    /** 裁决结论 */
    public record Decision(boolean allowed, String reason) {
        static Decision deny(String reason) { return new Decision(false, reason); }
        static Decision allow() { return new Decision(true, null); }
    }

    /**
     * 完整安全闸：静态校验 → 白名单 → security-engine ABAC 裁决 → 审计留痕。
     *
     * @param toolName    工具名（资源标识，非用户自报主体）
     * @param sql         待执行 SQL 全文（不信任来源：schema 定义或 LLM 入参一律同等校验）
     * @param boundParams schema 声明的绑定参数/列名（可空）
     * @param userId      主体 —— 必须由调用方从 token 上下文取得（SecurityContext/UserContext）
     * @param tenantId    租户 —— TenantContextHolder（token 链路），可为 null
     */
    public Decision review(String toolName, String sql, Collection<String> boundParams,
                           String userId, String tenantId) {
        Decision d = doReview(toolName, sql, boundParams, userId, tenantId);
        // §2.4-5：allow 与 deny 均留痕（Kafka ecos.audit，security 侧消费落库）
        try {
            securityEngineClient.audit(userId, ACTION + ":" + toolName,
                    "sql_digest=" + digest(sql) + " tables=" + extractTablesForAudit(sql),
                    d.allowed() ? "ALLOW" : "DENY:" + d.reason());
        } catch (Exception e) {
            log.warn("H8-T4 审计留痕发送失败（不阻塞拒绝路径，允许路径继续）: tool={}", toolName, e);
        }
        return d;
    }

    private Decision doReview(String toolName, String sql, Collection<String> boundParams,
                              String userId, String tenantId) {
        // 0) 主体必须来自 token 上下文
        if (userId == null || userId.isBlank() || "anonymousUser".equals(userId)) {
            log.warn("H8-T4 DENY: 缺少 token 主体（userId=null/anonymous），Agent SQL 工具拒绝匿名执行: tool={}",
                    toolName);
            return Decision.deny("无 token 主体，禁止执行 Agent SQL（fail-closed）");
        }
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
                log.warn("H8-T4 DENY: 表未列入白名单: tool={} table={}", toolName, table);
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

        // 5) security-engine ABAC 裁决（不可用/超时/不可解析 → DENY，§2.4-6）
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("resource", "agent_tool:" + toolName);
        attrs.put("tables", tables);
        attrs.put("sql", sql);
        if (!evaluateFailClosed(userId, tenantId, attrs)) {
            return Decision.deny("security-engine policy-engine/evaluate 裁决为 DENY 或不可用（默认 DENY）");
        }
        return Decision.allow();
    }

    // ── 静态校验 ──────────────────────────────────────────────────────────

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

    private String extractTablesForAudit(String sql) {
        if (sql == null) return "";
        try {
            String masked = maskLiterals(sql).toLowerCase(Locale.ROOT);
            Set<String> cte = extractCteNames(masked);
            Set<String> tables = new LinkedHashSet<>();
            Matcher tm = TABLE_REF.matcher(masked);
            while (tm.find()) {
                if (tm.group(1) != null && !cte.contains(tm.group(1))) tables.add(tm.group(1));
            }
            return String.join(",", tables);
        } catch (Exception e) {
            return "?";
        }
    }

    private String digest(String sql) {
        if (sql == null) return "";
        String s = sql.replaceAll("\\s+", " ").trim();
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    // ── security-engine 裁决（fail-closed，日志带堆栈） ──────────────────

    /**
     * POST {securityBaseUrl}/api/v1/security/policy-engine/evaluate。
     *
     * @return true 仅当 security 明确返回 result=true；
     *         不可用 / 超时 / HTTP 非 2xx / 响应不可解析 / 明确 DENY → false（默认 DENY，§2.4-6），
     *         且每种 DENY 路径均 log.warn 携带异常堆栈。
     */
    @SuppressWarnings("unchecked")
    private boolean evaluateFailClosed(String userId, String tenantId, Map<String, Object> attrs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("policy", "rbac");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", ACTION);
        if (attrs != null) input.putAll(attrs);
        input.put("subject", Map.of(
                "userId", userId,
                "tenantId", tenantId == null ? "default" : tenantId,
                "role", "user"));
        body.put("input", input);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    securityBaseUrl + "/api/v1/security/policy-engine/evaluate",
                    new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> respBody = resp == null ? null : resp.getBody();
            if (respBody == null || !(respBody.get("data") instanceof Map)) {
                log.warn("H8-T4 security 裁决响应不可解析 → 默认 DENY: user={} resp={}",
                        userId, respBody,
                        new IllegalStateException("policy-engine/evaluate response unparsable"));
                return false;
            }
            Object result = ((Map<String, Object>) respBody.get("data")).get("result");
            boolean allowed = Boolean.TRUE.equals(result);
            if (!allowed) {
                log.warn("H8-T4 security-engine 明确裁决 DENY: user={} action={} data={}",
                        userId, ACTION, respBody.get("data"));
            }
            return allowed;
        } catch (Exception e) {
            // 不可用 / 超时 / IO 异常 → fail-closed DENY（宁可误拒不可误放）
            log.warn("H8-T4 security-engine 不可用/超时 → 默认 DENY: user={} action={}",
                    userId, ACTION, e);
            return false;
        }
    }
}
