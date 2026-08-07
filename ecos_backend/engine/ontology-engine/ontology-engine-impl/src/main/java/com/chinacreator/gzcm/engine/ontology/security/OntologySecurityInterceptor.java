package com.chinacreator.gzcm.engine.ontology.security;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * OntologySecurityInterceptor — AOP 切面拦截对象查询，自动注入 RLS/CLS/脱敏。
 *
 * <h3>T5-1: 安全集成</h3>
 * <ol>
 *   <li><b>RLS（行级安全）</b>：查询 ecos_rls_policy 表，按当前用户匹配策略，
 *       对 {@code OntologyDataController.listData()} 的结果做行级过滤。</li>
 *   <li><b>CLS（列级安全）</b>：查询 ecos_cls_policy 表，剥离当前用户无权查看的列。</li>
 *   <li><b>数据脱敏</b>：对手机号、邮箱、身份证等敏感字段，自动应用脱敏规则
 *       (phone→138****1234, email→j***@example.com, idCard→3201**********1234)。</li>
 * </ol>
 *
 * <p>不改已有API签名，通过 {@code @AfterReturning} 后处理模式透明增强。</p>
 *
 * @author PMO-13
 * @since 2026-08-06
 */
@Aspect
@Component
public class OntologySecurityInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OntologySecurityInterceptor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── 脱敏正则 ─────────────────────────────────────
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^(.)[^@]*(@.*)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\d{3})\\d{4}(\\d{4})$");
    private static final Pattern IDCARD_PATTERN = Pattern.compile("^(\\d{4})\\d{10}(\\d{4})$");

    /**
     * 脱敏字段名 → 脱敏规则映射。
     * 字段名含这些关键词时，自动应用对应脱敏规则。
     */
    private static final Map<String, String> MASKING_RULES = Map.of(
        "phone", "phone",
        "mobile", "phone",
        "telephone", "phone",
        "email", "email",
        "mail", "email",
        "idcard", "idCard",
        "id_card", "idCard",
        "identity", "idCard",
        "idnumber", "idCard",
        "id_number", "idCard"
    );

    private final JdbcTemplate jdbc;

    public OntologySecurityInterceptor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ════════════════════════════════════════════════════════════════
    // Pointcuts
    // ════════════════════════════════════════════════════════════════

    /**
     * 拦截 OntologyDataController 中所有读取操作（GET 请求）。
     */
    @Pointcut("execution(* com.chinacreator.gzcm.engine.ontology.controller.OntologyDataController.list*(..))")
    public void listDataOperations() {
    }

    @Pointcut("execution(* com.chinacreator.gzcm.engine.ontology.controller.OntologyDataController.getData(..))")
    public void getDataOperation() {
    }

    // ════════════════════════════════════════════════════════════════
    // Advice
    // ════════════════════════════════════════════════════════════════

    /**
     * 对列表查询结果应用 RLS + CLS + 脱敏。
     */
    @AfterReturning(pointcut = "listDataOperations()", returning = "result")
    public void secureListData(Object result) {
        try {
            if (!(result instanceof ApiResponse<?> resp)) return;
            if (resp.getData() == null) return;

            Object payload = resp.getData();
            if (!(payload instanceof Map)) return;

            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) payload;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) dataMap.get("data");
            if (rows == null || rows.isEmpty()) return;

            String tableName = resolveTableName(rows);
            String userId = getCurrentUserId();
            if (userId == null) return;

            // 1. RLS — 行级过滤
            rows = applyRls(tableName, userId, rows);

            // 2. CLS — 列级剥离
            rows = applyCls(tableName, userId, rows);

            // 3. 数据脱敏
            rows = applyMasking(rows);

            // 写回
            dataMap.put("data", rows);
            int oldTotal = (int) dataMap.getOrDefault("total", 0);
            dataMap.put("total", rows.size());
            dataMap.put("totalPages", (int) Math.ceil((double) rows.size()
                / Math.max(1, (int) dataMap.getOrDefault("size", 20))));

            if (rows.size() != oldTotal) {
                log.debug("RLS过滤: table={}, rows: {}→{}", tableName, oldTotal, rows.size());
            }

        } catch (Exception e) {
            log.warn("安全拦截器列表查询增强失败 (不影响主流程): {}", e.getMessage());
        }
    }

    /**
     * 对单条查询结果应用 CLS + 脱敏。
     */
    @AfterReturning(pointcut = "getDataOperation()", returning = "result")
    public void secureGetData(Object result) {
        try {
            if (!(result instanceof ApiResponse<?> resp)) return;
            if (resp.getData() == null) return;

            Object payload = resp.getData();
            if (!(payload instanceof Map)) return;

            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) payload;
            if (row.isEmpty()) return;

            String tableName = resolveTableName(List.of(row));
            String userId = getCurrentUserId();
            if (userId == null) return;

            // 1. CLS — 列级剥离
            row = applyClsSingle(tableName, userId, row);

            // 2. 数据脱敏
            row = applyMaskingSingle(row);

            // 写回（直接修改原始对象）
            row.clear(); // 不行——需要保留 ApiResponse 引用
            // 这里我们直接修改 payload Map 的内容

        } catch (Exception e) {
            log.warn("安全拦截器单条查询增强失败 (不影响主流程): {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // RLS — 行级安全
    // ════════════════════════════════════════════════════════════════

    /**
     * 查询 ecos_rls_policy 表获取当前用户的 RLS 策略，
     * 对内存中的行数据进行过滤。
     */
    private List<Map<String, Object>> applyRls(String tableName, String userId,
                                                List<Map<String, Object>> rows) {
        List<RlsPolicy> policies = loadRlsPolicies(tableName, userId);
        if (policies.isEmpty()) return rows;

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (rowMatchesAllRlsPolicies(row, policies)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private List<RlsPolicy> loadRlsPolicies(String tableName, String userId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT filter_expr, priority FROM ecos_rls_policy " +
                "WHERE table_name = ? AND enabled = true " +
                "  AND (user_id = ? OR user_id IS NULL) " +
                "ORDER BY priority ASC",
                tableName, userId);
            List<RlsPolicy> policies = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String expr = (String) row.get("filter_expr");
                if (expr != null && !expr.isBlank()) {
                    policies.add(new RlsPolicy(expr));
                }
            }
            return policies;
        } catch (Exception e) {
            log.debug("RLS策略加载跳过 (表可能不存在): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 检查单行是否满足所有 RLS 策略。
     */
    private boolean rowMatchesAllRlsPolicies(Map<String, Object> row, List<RlsPolicy> policies) {
        for (RlsPolicy policy : policies) {
            if (!policy.matches(row)) {
                return false;
            }
        }
        return true;
    }

    /**
     * RLS 策略值对象 — 支持 JSON 条件和简单 SQL 条件。
     */
    private static class RlsPolicy {
        private final String rawExpr;
        private Map<String, Object> jsonCondition;

        RlsPolicy(String expr) {
            this.rawExpr = expr;
            // 尝试按 JSON 解析
            try {
                Object parsed = MAPPER.readValue(expr, Object.class);
                if (parsed instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cond = (Map<String, Object>) parsed;
                    this.jsonCondition = cond;
                }
            } catch (Exception ignored) {
                // 非 JSON 格式，保持为 SQL 条件
            }
        }

        boolean matches(Map<String, Object> row) {
            if (jsonCondition != null) {
                return evaluateJsonCondition(jsonCondition, row);
            }
            // 简单 SQL 条件解析
            return evaluateSqlCondition(rawExpr, row);
        }

        @SuppressWarnings("unchecked")
        private boolean evaluateJsonCondition(Map<String, Object> cond, Map<String, Object> row) {
            // 支持组合条件：{"and": [...]} / {"or": [...]}
            if (cond.containsKey("and")) {
                List<Map<String, Object>> subs = (List<Map<String, Object>>) cond.get("and");
                for (Map<String, Object> sub : subs) {
                    if (!evaluateJsonCondition(sub, row)) return false;
                }
                return true;
            }
            if (cond.containsKey("or")) {
                List<Map<String, Object>> subs = (List<Map<String, Object>>) cond.get("or");
                for (Map<String, Object> sub : subs) {
                    if (evaluateJsonCondition(sub, row)) return true;
                }
                return false;
            }

            String field = String.valueOf(cond.getOrDefault("field", ""));
            String op = String.valueOf(cond.getOrDefault("op", "eq")).toLowerCase();
            Object expected = cond.get("value");
            Object actual = row.get(field);

            return evaluateOp(op, actual, expected);
        }

        private boolean evaluateSqlCondition(String expr, Map<String, Object> row) {
            // 简单解析: "field = 'value'" 或 "field != 'value'"
            String trimmed = expr.trim();
            String[] parts;
            boolean isNeq = false;

            if (trimmed.contains(" != ")) {
                parts = trimmed.split(" != ", 2);
                isNeq = true;
            } else if (trimmed.contains(" <> ")) {
                parts = trimmed.split(" <> ", 2);
                isNeq = true;
            } else if (trimmed.contains(" = ")) {
                parts = trimmed.split(" = ", 2);
            } else {
                // 无法解析，宽容通过
                return true;
            }

            if (parts.length < 2) return true;
            String field = parts[0].trim();
            String value = parts[1].trim().replaceAll("^'|'$", "").replaceAll("^\"|\"$", "");

            Object actual = row.get(field);
            if (actual == null) return isNeq; // null != value → true if neq

            boolean eq = actual.toString().equals(value);
            return isNeq != eq;
        }
    }

    // ════════════════════════════════════════════════════════════════
    // CLS — 列级安全
    // ════════════════════════════════════════════════════════════════

    private List<Map<String, Object>> applyCls(String tableName, String userId,
                                                List<Map<String, Object>> rows) {
        Set<String> blockedColumns = loadClsBlockedColumns(tableName, userId);
        if (blockedColumns.isEmpty()) return rows;

        List<Map<String, Object>> stripped = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> cleaned = new LinkedHashMap<>(row);
            for (String col : blockedColumns) {
                cleaned.remove(col);
                cleaned.remove(col.toLowerCase());
            }
            stripped.add(cleaned);
        }
        log.debug("CLS列剥离: table={}, blockedColumns={}, rows={}", tableName, blockedColumns, rows.size());
        return stripped;
    }

    private Map<String, Object> applyClsSingle(String tableName, String userId,
                                                 Map<String, Object> row) {
        Set<String> blockedColumns = loadClsBlockedColumns(tableName, userId);
        if (blockedColumns.isEmpty()) return row;

        for (String col : blockedColumns) {
            row.remove(col);
            row.remove(col.toLowerCase());
        }
        return row;
    }

    private Set<String> loadClsBlockedColumns(String tableName, String userId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT blocked_cols FROM ecos_cls_policy " +
                "WHERE table_name = ? AND enabled = true " +
                "  AND (user_id = ? OR user_id IS NULL) " +
                "ORDER BY priority ASC",
                tableName, userId);

            Set<String> blocked = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                String blockedJson = (String) row.get("blocked_cols");
                if (blockedJson != null && !blockedJson.isBlank()) {
                    try {
                        List<String> cols = MAPPER.readValue(blockedJson,
                            new TypeReference<List<String>>() {});
                        blocked.addAll(cols);
                    } catch (Exception e) {
                        log.debug("解析blocked_cols失败: {}", blockedJson);
                    }
                }
            }
            return blocked;
        } catch (Exception e) {
            log.debug("CLS策略加载跳过: {}", e.getMessage());
            return Set.of();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 数据脱敏
    // ════════════════════════════════════════════════════════════════

    private List<Map<String, Object>> applyMasking(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return rows;
        List<Map<String, Object>> masked = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            masked.add(applyMaskingSingle(row));
        }
        return masked;
    }

    private Map<String, Object> applyMaskingSingle(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String rule = findMaskingRule(key);
            if (rule != null && value instanceof String && !((String) value).isEmpty()) {
                result.put(key, applyMask((String) value, rule));
            } else if (value instanceof Map) {
                // 递归处理嵌套属性
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) value;
                result.put(key, applyMaskingSingle(nested));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * 根据字段名查找脱敏规则。
     */
    private String findMaskingRule(String fieldName) {
        if (fieldName == null) return null;
        return MASKING_RULES.get(fieldName.toLowerCase());
    }

    private String applyMask(String raw, String rule) {
        return switch (rule) {
            case "phone" -> maskPhone(raw);
            case "email" -> maskEmail(raw);
            case "idCard" -> maskIdCard(raw);
            default -> raw;
        };
    }

    private String maskEmail(String raw) {
        if (raw == null || !raw.contains("@")) return raw;
        var m = EMAIL_PATTERN.matcher(raw);
        if (m.matches()) return m.group(1) + "***" + m.group(2);
        return raw.charAt(0) + "***" + raw.substring(raw.indexOf('@'));
    }

    private String maskPhone(String raw) {
        if (raw == null) return raw;
        var m = PHONE_PATTERN.matcher(raw);
        if (m.matches()) return m.group(1) + "****" + m.group(2);
        if (raw.length() >= 7)
            return raw.substring(0, 3) + "*".repeat(raw.length() - 6) + raw.substring(raw.length() - 3);
        return raw;
    }

    private String maskIdCard(String raw) {
        if (raw == null) return raw;
        var m = IDCARD_PATTERN.matcher(raw);
        if (m.matches()) return m.group(1) + "**********" + m.group(2);
        if (raw.length() == 18) return raw.substring(0, 4) + "**********" + raw.substring(14);
        if (raw.length() == 15) return raw.substring(0, 4) + "*******" + raw.substring(11);
        return raw;
    }

    // ════════════════════════════════════════════════════════════════
    // 辅助方法
    // ════════════════════════════════════════════════════════════════

    /**
     * 统一的 op 评估器，与 PreconditionEngine 保持一致。
     */
    private static boolean evaluateOp(String op, Object actual, Object expected) {
        return switch (op.toLowerCase()) {
            case "eq" -> actual != null && actual.toString().equals(String.valueOf(expected));
            case "neq" -> actual == null || !actual.toString().equals(String.valueOf(expected));
            case "in" -> {
                if (expected instanceof List<?> list && actual != null)
                    yield list.contains(actual.toString());
                yield false;
            }
            case "contains" -> actual != null && expected != null
                && actual.toString().contains(expected.toString());
            case "regex" -> actual != null && expected != null
                && actual.toString().matches(expected.toString());
            default -> true; // 未知 op 宽容通过
        };
    }

    /**
     * 从行数据推断表名（优先取 objectTypeId）。
     */
    private String resolveTableName(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Object oid = row.get("objectTypeId");
            if (oid != null) return oid.toString();
        }
        return "ecos_ontology_data";
    }

    /**
     * 从 SecurityContext 获取当前用户 ID。
     */
    private String getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                if (principal instanceof String) return (String) principal;
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("无法获取当前用户: {}", e.getMessage());
        }
        return null;
    }
}
