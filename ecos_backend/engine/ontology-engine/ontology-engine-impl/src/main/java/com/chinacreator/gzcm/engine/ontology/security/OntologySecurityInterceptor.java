package com.chinacreator.gzcm.engine.ontology.security;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.UnauthorizedException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;

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
     *
     * <p><b>#142/B4-3d-10（D-B4-26 余项）</b>：RLS 腿改取四态读数。
     * {@code UNAVAILABLE}（security 进程没答）在本切面就地改写信封
     * （{@code code=503} + {@code errorCode=ECOS-SEC-501} + data 清空），
     * 不再折叠成"200＋零行"。选改信封而不是抛异常是两条约束的交点：
     * ① {@code @AfterReturning} 抛运行时异常会穿透成 500（本模块无对应
     * {@code @ExceptionHandler}），既不是 503 也丢机读码；
     * ② 抛出去还会跳过写回、把已过滤前的载荷留在应答里＝fail-open。
     * 503 信封与 {@code DecisionController} 的 fail-closed 出口同形。</p>
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
                        RlsOutcome rls = applyRlsRemote(tableName, userId, rows);
                        if (rls.unavailable()) {
                            markSecurityUnavailable(resp, rls.reason());
                            return;
                        }
                        rows = applyClsRemote(tableName, userId, rls.rows());
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
                        boolean rowFiltered = false;
                        if (userId != null) {
                            RlsOutcome rls = applyRlsRemote(tableName, userId, rows);
                            if (rls.unavailable()) {
                                // 不可用态：整个副本（含未过滤的行）都不许写回，
                                // 只出 503 信封＋空 data（D-B4-34 刚接通的写回腿在此必须让位于改写信封）
                                markSecurityUnavailable(resp, rls.reason());
                                return;
                            }
                            rows = applyClsRemote(tableName, userId, rls.rows());
                            rowFiltered = true;
                        }
                        dataMap.put("data", rows);
                        // #142/B4-3d-8：total 只在实际做过行级过滤时改写 —— 匿名/无登录态
                        // 没过滤却改计数，等于用零安全收益的行为变更去换分页元数据一致。
                        // 已登录且过滤＝按可见行少报（fail-closed）；分页元数据（page/size/
                        // totalPages）的口径退化需 DAO 侧带 RLS 的 count 下推，已登记不收口。
                        if (rowFiltered) {
                            dataMap.put("total", rows.size());
                        }
                    }
                }
                // #142/B4-3d-9（D-B4-35）：脱敏在本分支只走 maskSingleRow 这一条递归腿
                // （行级脱敏不再单独预跑一遍）。被删除的 maskMapInPlace 坏在两处：
                // 递归腿在 copyMap 的一次性副本上自我调用＝掩码随副本丢弃；顶层腿
                // it.remove() 后紧接 map.put() 在 LinkedHashMap 迭代中构成结构性修改，
                // 抛 ConcurrentModificationException 且被本方法外层 catch 静默吞掉——
                // 连带把 D-B4-34 刚接通的写回一起跳过。
                // maskPhone 不幂等（138****5678 再掩一次成 138*****5678），故判据是
                // 「一次遍历到位」，任何「再补一遍脱敏」的修法都算回潮。
                dataMap = maskSingleRow(dataMap);
                // #142/B4-3d-8（D-B4-34）：副本必须写回 resp。此前 copyMap 出来的 dataMap
                // 从头到尾没有 assignData，RLS/CLS/mask 三条通道对 {data: List} 形态
                // （分页读接口的权威形状）结构性恒等于关闭 —— 应答仍是未过滤的全量行。
                assignData(resp, dataMap);
            }
        } catch (Exception e) {
            log.warn("OntologySecurityInterceptor 读增强失败 (不影响主流程): {}", e.getMessage());
        }
    }

    // ═══════════════ RLS — 走 security-engine ═══════════════

    /**
     * 一次 RLS 消费的结果：行集 + 「是否 security 进程不可用」。
     *
     * <p><b>为什么不是一个布尔就够了</b>（#142/B4-3d-10，D-B4-26 余项）：不可用与拒绝
     * 都返空集，但对外形态必须不同（503 信封 vs 200＋空行）。旧签名 {@code List} 只能
     * 表达后者，于是"security 挂了"在本通道读起来就是"这人看不到数据"。</p>
     */
    record RlsOutcome(List<Map<String, Object>> rows, boolean unavailable, String reason) {

        static RlsOutcome filtered(List<Map<String, Object>> rows) {
            return new RlsOutcome(rows, false, null);
        }

        static RlsOutcome unavailable(String reason) {
            return new RlsOutcome(List.of(), true, reason);
        }
    }

    private RlsOutcome applyRlsRemote(String tableName, String userId,
                                      List<Map<String, Object>> rows) {
        try {
            RlsExecutionContract.Execution exec = securityEngineClient
                    .applyRlsExecution("ecos_ontology", tableName, Map.of("tenant", "default"));

            // 不可用态：不折叠成拒绝（§4.5.4）。审计与对外信封分别由
            // audit 事件名与 markSecurityUnavailable 的 503 承载。
            if (exec.unavailable()) {
                log.warn("security-engine 进程不可用（非无权限）: table={} reason={}",
                        tableName, exec.reason());
                try {
                    securityEngineClient.audit("RLS_UNAVAILABLE_SECURITY_ENGINE",
                            "unavailable:" + exec.reason());
                } catch (Exception ignored) {
                    // audit 不阻断主流程（fail-closed 由返回值保证，不由审计保证）
                }
                return RlsOutcome.unavailable(exec.reason());
            }
            // #142/B4-3c-2（D-B4-17）：应答缺执行契约＝漂移即拒绝（客户端已归一为 DENIED 态，
            // 理由带 REASON_WHERE_ABSENT），不再 return rows（fail-open 全量渗漏）。
            if (!exec.usable()) {
                return denyUntrustedWhere(exec.reason() == null
                        ? RlsExecutionContract.REASON_DENY_FROM_SECURITY_ENGINE : exec.reason());
            }
            String where = exec.clause();
            if (where == null || where.isBlank()) {
                return denyUntrustedWhere(RlsExecutionContract.REASON_WHERE_ABSENT);
            }
            if (isDenyClause(where)) return RlsOutcome.filtered(new ArrayList<>());
            if (isAllowAllClause(where)) return RlsOutcome.filtered(rows);
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
            return RlsOutcome.filtered(filtered);
        } catch (Exception e) {
            // P0-1: 默认 DENY (架构铁律 2.4.6) — 本方法内部异常只可能是本地过滤腿出错，
            // 与"security 进程不可用"分态由 applyRlsExecution 已经判过，此处不再冒充不可用。
            log.warn("本体 RLS 过滤腿异常, 默认 DENY: {}", e.getMessage());
            try {
                securityEngineClient.audit("RLS_DENY_LOCAL_FILTER_THROWN", "deny:filter_throw:" + e.getMessage());
            } catch (Exception ignored) {
                // audit 不阻断主流程
            }
            return RlsOutcome.filtered(new ArrayList<>());
        }
    }

    /**
     * 拒绝态判定（#142/B4-3c-2）：剥括号与空白后等于 {@code 1=0}。
     * 生产者的合并逻辑给每个策略段加括号，字面量相等会把 {@code "(1=0)"} 误判成非平凡谓词。
     * <p><b>#142/B4-3d-5（D-B4-24）</b>：归一化口径与片段字面量单源于
     * {@link RlsExecutionContract}，本类不再自写第二套剥括号实现。</p>
     */
    private static boolean isDenyClause(String clause) {
        return RlsExecutionContract.DENY_CLAUSE.equals(RlsExecutionContract.normalize(clause));
    }

    /** 恒真态判定：{@code 1=1}＝裁决明确"不限制行"，与"未给谓词"（漂移）分态。 */
    private static boolean isAllowAllClause(String clause) {
        return RlsExecutionContract.ALLOW_ALL_CLAUSE.equals(RlsExecutionContract.normalize(clause));
    }

    /**
     * P0-2: RLS 谓词不可信时走默认 DENY — 记录审计并返回空行集.
     * <p>覆盖场景: AND/OR/NOT/LIKE/IN 多条件 / 未知 op / 非白名单字段名 /
     * 单源读数给出的拒绝态（含应答缺执行契约的漂移）.</p>
     *
     * @param reason 拒绝理由（片段原文或单源理由码），只进审计与日志，不参与判定
     * @return 空行集的拒绝态读数（{@code unavailable=false}：这是策略拒绝，不是引擎没答）
     */
    private RlsOutcome denyUntrustedWhere(String reason) {
        log.warn("RLS 拒绝: clause={}", reason);
        try {
            securityEngineClient.audit("RLS_DENY_UNTRUSTED_WHERE", "deny:clause=" + reason);
        } catch (Exception ignored) {
            // audit 不阻断
        }
        return RlsOutcome.filtered(new ArrayList<>());
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

    /**
     * 就地改写信封为「不可用」态（#142/B4-3d-10，D-B4-26 余项）。
     *
     * <p>三件事一起做、缺一不可：{@code code} 从 0 改 503、机读 {@code errorCode}
     * 带 {@code ECOS-SEC-501}、{@code data} 清空。只改前两件而留着 data，
     * 客户端只要有一处按 HTTP 状态（200）读成功，渗漏照旧——写回与改写必须原子；
     * 只清 data 而不改码，又把"引擎没答"冒充成"这人没数据"（§4.5.4 禁止的折叠）。</p>
     *
     * <p>码面取 {@link RlsExecutionContract} 单源常量，禁在本类写字面量。</p>
     */
    private static void markSecurityUnavailable(ApiResponse<?> resp, String reason) {
        resp.setCode(RlsExecutionContract.SEC_UNAVAILABLE_HTTP);
        resp.setErrorCode(RlsExecutionContract.SEC_UNAVAILABLE_CODE);
        resp.setMessage(RlsExecutionContract.REASON_SERVICE_UNAVAILABLE
                + "（security-engine 进程不可用，非「用户无权限」）: " + reason);
        assignData(resp, Collections.emptyList());
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
