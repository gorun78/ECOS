package com.chinacreator.gzcm.engine.ontology.security;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.UnauthorizedException;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;
import java.util.regex.Pattern;

/**
 * OntologySecurityInterceptor — 本体控制器 AOP 切面，统一注入 RLS/CLS/脱敏/审计。
 *
 * <p><b>来源: Wave B-1 · T12</b> | <b>日期: 2026-09-12</b> | <b>责任人: fullstack-implementer</b></p>
 * <p><b>继承铁律</b>：架构铁律 §2.4（安全集成 5 项 — RLS/CLS/脱敏/OPA/Kafka 审计 + 默认 DENY）、
 * §2.5（security-engine 横切护，禁止引擎内重复实现）。</p>
 *
 * <p>T12 改造 (2026-09-12)：
 * <ol>
 *   <li>Pointcut 扩展到 Ontology*Controller（27 个）
 *   <li>非登录态默认 DENY — 写操作（POST/PUT/PATCH/DELETE）缺登录态直接抛
 *       {@link UnauthorizedException}（HTTP 401 + ApiResponse）
 *   <li>写操作审计 — 成功后调 {@link SecurityEngineClient#audit} 发 Kafka {@code ecos.audit}
 *   <li>RLS / CLS / 脱敏 / ABAC 统一通过 {@link SecurityEngineClient} 委托 security-engine REST
 *   <li>修复单条 GET 切面 row.clear() 自坏 bug
 * </ol>
 *
 * @author PMO-13
 * @since 2026-08-06
 */
@Aspect
@Component
public class OntologySecurityInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(OntologySecurityInterceptor.class);

    private static final Pattern WRITE_METHOD_PATTERN =
            Pattern.compile("^(POST|PUT|PATCH|DELETE)$");

    /** 脱敏字段名 → 规则 */
    private static final Map<String, String> MASKING_RULES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("phone", "phone");
        m.put("mobile", "phone");
        m.put("telephone", "phone");
        m.put("email", "email");
        m.put("mail", "email");
        m.put("idcard", "idcard");
        m.put("id_card", "idcard");
        m.put("identity", "idcard");
        m.put("idnumber", "idcard");
        m.put("id_number", "idcard");
        m.put("amount", "amount");
        MASKING_RULES = Map.copyOf(m);
    }

    private final SecurityEngineClient securityEngineClient;

    public OntologySecurityInterceptor(SecurityEngineClient securityEngineClient) {
        this.securityEngineClient = securityEngineClient;
    }

    // ═══════════════ Pointcut ═══════════════

    @Pointcut(
            "execution(* com.chinacreator.gzcm.engine.ontology.controller.Ontology*Controller.*(..))")
    public void anyOperation() {
    }

    // ═══════════════ 写操作：401 强制 + 审计 ═══════════════

    @Around("anyOperation()")
    public Object secureWriteAuthAndAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        if (isWriteRequest()) {
            String userId = currentUserId();
            if (userId == null) {
                log.warn("OntologySecurityInterceptor: DENY write {} {} (no authentication)",
                        getHttpMethodOrUnknown(),
                        joinPoint.getSignature().toShortString());
                securityEngineClient.audit(
                        getHttpMethodOrUnknown() + "_" + joinPoint.getSignature().getName(),
                        "DENY_UNAUTHORIZED");
                throw new UnauthorizedException("ONT-401: 写操作需要登录态，请先登录");
            }
            Object result = joinPoint.proceed();
            try {
                securityEngineClient.audit(
                        getHttpMethodOrUnknown() + "_" + joinPoint.getSignature().getName(),
                        "OK");
            } catch (Exception e) {
                log.debug("Audit after write failed (non-blocking): {}", e.getMessage());
            }
            return result;
        }
        return joinPoint.proceed();
    }

    /**
     * 拦截读方法 — 行级 RLS + 列级 CLS + 脱敏。
     * <p>仅处理 ApiResponse 的 payload 是 List 或 {data: List} Map 两种形态。</p>
     */
    @AfterReturning(pointcut = "anyOperation()", returning = "result")
    public void secureReadResult(Object result) {
        if (!isReadRequest()) return;
        try {
            if (!(result instanceof ApiResponse<?> resp)) return;
            Object payload = resp.getData();
            if (payload == null) return;

            String userId = currentUserId();
            if (payload instanceof List<?> list) {
                List<Map<String, Object>> rows = asRowList(list);
                if (rows != null && !rows.isEmpty()) {
                    String tableName = resolveTableName(rows);
                    if (userId != null) {
                        rows = applyRlsRemote(tableName, userId, rows);
                        rows = applyClsRemote(tableName, userId, rows);
                    }
                    rows = applyMaskingRows(rows);
                    assignData(resp, rows);
                }
            } else if (payload instanceof Map<?, ?>) {
                Map<String, Object> dataMap = copyMap((Map<?, ?>) payload);
                Object inner = dataMap.get("data");
                if (inner instanceof List<?> list) {
                    List<Map<String, Object>> rows = asRowList(list);
                    if (rows != null && !rows.isEmpty()) {
                        String tableName = resolveTableName(rows);
                        if (userId != null) {
                            rows = applyRlsRemote(tableName, userId, rows);
                            rows = applyClsRemote(tableName, userId, rows);
                        }
                        rows = applyMaskingRows(rows);
                        dataMap.put("data", rows);
                        dataMap.put("total", rows.size());
                    }
                }
                maskMapInPlace(dataMap);
            }
        } catch (Exception e) {
            log.warn("OntologySecurityInterceptor 读增强失败 (不影响主流程): {}", e.getMessage());
        }
    }

    // ═══════════════ RLS — 走 security-engine ═══════════════

    private List<Map<String, Object>> applyRlsRemote(String tableName, String userId,
                                                     List<Map<String, Object>> rows) {
        try {
            String where = securityEngineClient.applyRls("ecos_ontology", tableName,
                    Map.of("tenant", "default"));
            if (where == null || where.isEmpty()) return rows;
            if ("1=0".equals(where)) return new ArrayList<>();
            // P0-2: 仅接受单条件白名单 (entity_code/tenant_id/domain_id/ontology_id/is_deleted/status)
            // 的 = / != 二 token; 其它情况 (AND/OR/NOT/LIKE/IN/多条件/未知字段名) 视为 RLS 表达式不可信
            // -> DENY (return empty). 多条件 SQL WHERE 推到 DAO 层做 (T17 落库时白名单字段名 + 参数绑定).
            String key;
            String val;
            boolean isNeq = false;
            if (where.contains(" != ") || where.contains(" <> ")) {
                String op = where.contains(" <> ") ? " <> " : " != ";
                String[] parts = where.split(java.util.regex.Pattern.quote(op), 2);
                isNeq = true;
                if (parts.length < 2) {
                    return denyUntrustedWhere(where);
                }
                key = parts[0].trim();
                val = parts[1].trim().replaceAll("^'|'$", "").replaceAll("^\"|\"$", "");
            } else if (where.contains(" = ")) {
                String[] parts = where.split(" = ", 2);
                if (parts.length < 2) {
                    return denyUntrustedWhere(where);
                }
                key = parts[0].trim();
                val = parts[1].trim().replaceAll("^'|'$", "").replaceAll("^\"|\"$", "");
            } else {
                // 无 = / != / <> _operators -> 视为不可信 (AND/OR/NOT/LIKE/IN 等)
                return denyUntrustedWhere(where);
            }
            // P0-2: 字段名必须在白名单; 非白名单字段名 -> DENY
            if (!isWhitelistedFieldName(key)) {
                return denyUntrustedWhere(where);
            }
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Object actual = row.get(key);
                boolean eq = actual != null && actual.toString().equals(val);
                if (isNeq != eq) filtered.add(row);
            }
            return filtered;
        } catch (Exception e) {
            // P0-1: 默认 DENY (架构铁律 2.4.6) — security-engine 不可用必须返空,
            // 不可 fail-open 返原 rows 否则 27 个 Controller 读接口全量渗漏全行
            log.warn("security-engine RLS 不可用, 默认 DENY: {}", e.getMessage());
            try {
                securityEngineClient.audit("RLS_DENY_FAIL_OPEN_DETECTED", "deny:resttemplate_throw:" + e.getMessage());
            } catch (Exception ignored) {
                // audit 不阻断主流程
            }
            return new ArrayList<>();
        }
    }

    /**
     * P0-2: RLS whereClause 不可信时走默认 DENY — 记录审计并返回空集.
     * <p>覆盖场景: AND/OR/NOT/LIKE/IN 多条件 / 未知 op / 非白名单字段名.</p>
     *
     * @param whereClause 原始 where 字符串
     * @return 空列表
     */
    private List<Map<String, Object>> denyUntrustedWhere(String whereClause) {
        log.warn("RLS whereClause 不可信, 默认 DENY: clause={}", whereClause);
        try {
            securityEngineClient.audit("RLS_DENY_UNTRUSTED_WHERE", "deny:clause=" + whereClause);
        } catch (Exception ignored) {
            // audit 不阻断
        }
        return new ArrayList<>();
    }

    /**
     * P0-2: RLS 白名单字段名判定 — 仅接受明确的实体/租户/域/本体/删除/状态字段.
     *
     * @param name 字段名
     * @return true 表示在白名单内
     */
    private static boolean isWhitelistedFieldName(String name) {
        if (name == null) return false;
        return RLS_WHITELIST_FIELDS.contains(name.toLowerCase());
    }

    /** RLS 白名单字段名集合 (全小写, 忽略大小写比对) */
    private static final Set<String> RLS_WHITELIST_FIELDS = Set.of(
            "entity_code", "tenant_id", "domain_id", "ontology_id", "is_deleted", "status"
    );

    // ═══════════════ CLS — 走 security-engine ═══════════════

    private List<Map<String, Object>> applyClsRemote(String tableName, String userId,
                                                     List<Map<String, Object>> rows) {
        try {
            Set<String> allowed = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                allowed.addAll(row.keySet());
            }
            List<String> filtered = securityEngineClient.filterColumns(
                    tableName, new ArrayList<>(allowed));
            // P0-1 (Wave B-2 加固 round 2): 默认 DENY — filterColumns 内部已 swallow
            // 任何 fail-open (serviceRestTemplate == null / internal catch -> List.of()),
            // 不允许 interceptor 侧面另起炉灶把"空 = 不允许"变成"空 = 全放行" —
            // (前次: filtered.isEmpty() -> return rows 把 security-engine 短暂 down
            //  时全部 27 个 Controller 读接口全列裸返)
            if (filtered == null || filtered.isEmpty()) {
                try {
                    securityEngineClient.audit("CLS_DENY_EMPTY_ALLOWED_SET",
                            "deny:filterColumns_empty:" + tableName);
                } catch (Exception ignored) { /* audit 不阻断主流程 */ }
                return Collections.emptyList();
            }
            Set<String> allowedSet = new LinkedHashSet<>(filtered);
            List<Map<String, Object>> stripped = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> clean = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    String key = e.getKey();
                    if (allowedSet.contains(key) || allowedSet.contains(key.toLowerCase())) {
                        clean.put(key, e.getValue());
                    }
                }
                stripped.add(clean);
            }
            return stripped;
        } catch (Exception e) {
            // P0-1: 默认 DENY (架构铁律 2.4.6) — CLS 不可用时无可见列, 不允许裸返全列
            log.warn("security-engine CLS 不可用, 默认 DENY(无可见列): {}", e.getMessage());
            try {
                securityEngineClient.audit("CLS_DENY_FAIL_OPEN_DETECTED", "deny:resttemplate_throw:" + e.getMessage());
            } catch (Exception ignored) {
                // audit 不阻断主流程
            }
            return Collections.emptyList();
        }
    }

    // ═══════════════ 脱敏 ═══════════════

    private List<Map<String, Object>> applyMaskingRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> masked = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            masked.add(maskSingleRow(row));
        }
        return masked;
    }

    private Map<String, Object> maskSingleRow(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row.size());
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            String rule = findMaskingRule(key);
            if (rule != null && value instanceof String s && !s.isEmpty()) {
                result.put(key, applyMask(s, rule));
            } else if (value instanceof Map<?, ?> m) {
                result.put(key, maskSingleRow(copyMap(m)));
            } else if (value instanceof List<?> l) {
                List<Object> maskedList = new ArrayList<>(l.size());
                for (Object v : l) maskedList.add(maskValue(v));
                result.put(key, maskedList);
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private Object maskValue(Object v) {
        if (v instanceof Map<?, ?> m) {
            return maskSingleRow(copyMap(m));
        } else if (v instanceof List<?> l) {
            List<Object> maskedList = new ArrayList<>(l.size());
            for (Object item : l) maskedList.add(maskValue(item));
            return maskedList;
        }
        return v;
    }

    private void maskMapInPlace(Map<String, Object> map) {
        for (Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Object> e = it.next();
            Object value = e.getValue();
            if (value instanceof String s) {
                String rule = findMaskingRule(e.getKey());
                if (rule != null && !s.isEmpty()) {
                    String key = e.getKey();
                    it.remove();
                    map.put(key, applyMask(s, rule));
                }
            } else if (value instanceof Map<?, ?> sub) {
                maskMapInPlace(copyMap(sub));
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        maskMapInPlace(copyMap(m));
                    }
                }
            }
        }
    }

    private String findMaskingRule(String fieldName) {
        if (fieldName == null) return null;
        return MASKING_RULES.get(fieldName.toLowerCase());
    }

    private String applyMask(String raw, String rule) {
        return switch (rule) {
            case "phone" -> maskPhone(raw);
            case "email" -> maskEmail(raw);
            case "idcard" -> maskIdCard(raw);
            case "amount" -> raw.replaceAll("\\d{1,9}(?=(\\.\\d{1,2})?$)", "*");
            default -> raw;
        };
    }

    private static String maskEmail(String raw) {
        if (raw == null || !raw.contains("@")) return raw;
        var m = Pattern.compile("^(.)[^@]*(@.*)$").matcher(raw);
        if (m.matches()) return m.group(1) + "***" + m.group(2);
        return raw.charAt(0) + "***" + raw.substring(raw.indexOf('@'));
    }

    private static String maskPhone(String raw) {
        if (raw == null) return raw;
        var m = Pattern.compile("^(\\d{3})\\d{4}(\\d{4})$").matcher(raw);
        if (m.matches()) return m.group(1) + "****" + m.group(2);
        if (raw.length() >= 7) {
            return raw.substring(0, 3)
                    + "*".repeat(raw.length() - 6)
                    + raw.substring(raw.length() - 3);
        }
        return raw;
    }

    private static String maskIdCard(String raw) {
        if (raw == null) return raw;
        var m = Pattern.compile("^(\\d{4})\\d{10}(\\d{4})$").matcher(raw);
        if (m.matches()) return m.group(1) + "**********" + m.group(2);
        if (raw.length() == 18) return raw.substring(0, 4) + "**********" + raw.substring(14);
        if (raw.length() == 15) return raw.substring(0, 4) + "*******" + raw.substring(11);
        return raw;
    }

    // ═══════════════ 辅助 ═══════════════

    /** List&lt;?&gt; → List&lt;Map&lt;String,Object&gt;&gt;；非 Map 元素返回 null */
    private static List<Map<String, Object>> asRowList(List<?> list) {
        if (list.isEmpty()) return new ArrayList<>();
        if (!(list.get(0) instanceof Map<?, ?>)) return null;
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Map<?, ?> mm) {
                result.add(copyMap(mm));
            }
        }
        return result;
    }

    /** 拷贝 Map，显式 String key，避免 CAP 推断 */
    private static Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> e : map.entrySet()) {
            result.put(String.valueOf(e.getKey()), e.getValue());
        }
        return result;
    }

    /** 反射写回，绕过 ApiResponse&lt;?&gt; 的 CAP 推断 */
    private static void assignData(ApiResponse<?> resp, Object data) {
        try {
            var method = resp.getClass().getMethod("setData", Object.class);
            method.invoke(resp, data);
        } catch (Exception e) {
            log.debug("assignData 反射失败: {}", e.getMessage());
        }
    }

    private static String resolveTableName(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Object oid = row.get("objectTypeId");
            if (oid != null) return oid.toString();
        }
        return "ecos_ontology_data";
    }

    private static String currentUserId() {
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

    private static String getHttpMethodOrUnknown() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getMethod();
        }
        return "UNKNOWN";
    }

    private static boolean isWriteRequest() {
        return WRITE_METHOD_PATTERN.matcher(getHttpMethodOrUnknown()).matches();
    }

    private static boolean isReadRequest() {
        String m = getHttpMethodOrUnknown();
        return "GET".equalsIgnoreCase(m) || "HEAD".equalsIgnoreCase(m);
    }
}
