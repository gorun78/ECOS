package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.sysman.iam.context.UserContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * W31 RLS 谓词安全渲染（详细设计-01 C.2.2 / C.1[4]，配合 {@link QueryController}）。
 *
 * <p>security-engine 的 {@code RowLevelSecurityServiceImpl.apply} 现返回<b>参数化
 * 谓词</b>（{@code predicateTemplate} + {@code bindings[]}）。本类把它渲染成一个
 * 值已内联、占位符已消解的 SQL 片段，交给既有 {@code queryExecutionService.execute}
 * 通道执行；<b>模板白名单已在写入侧由 RlsPredicateValidator 校验</b>，此处只做
 * "服务端上下文代入 + 每格的值域白名单", 值域外的字符一律拒绝（任何引号、分号、
 * 注释符、管道符 → {@link RlsRenderException}），不存在任何把用户可控文本拼进 SQL
 * 的通道。命中 ctx 或 STATIC_LIST 缺失占位 → 同样拒（C.6 降级矩阵：RLS 不可用 → 403
 * ECOS-SEC-405，不降级放行）。</p>
 *
 * <p>支持的 source（白名单，越界即拒）：
 * <ul>
 *   <li>{@code CONTEXT_USER_ID / CONTEXT_TENANT_ID} — 取自 {@link UserContext} 静态槽</li>
 *   <li>{@code CONTEXT_ORG} — 取自 Controller 层从请求头/参数读取的机构 ID</li>
 *   <li>{@code CONTEXT_CLEARANCE_LEVEL} — UserContext 未直接暴露，此处 fail-closed
 *       （待 security-engine 把 clearance 放进 UserContext 后放开；结构不变）</li>
 *   <li>{@code STATIC_LIST} — 值列表 ≤ 50 项，逐项经 {@link #SAFE_VALUE}，越界即拒</li>
 * </ul>
 * 有意<b>不支持</b> {@code PROJECT_ATTRIBUTION_SUBQUERY}：跨库归属子查询不能在
 * 目标数据源侧执行；命中即拒（不拼接、不降级）。查询通道需要跨库归属谓词的走
 * DecisionBundle/QueryExecutor 直连 PG 通道（02 册交付项）。</p>
 */
public final class RlsQueryFilter {

    /**
     * 值域白名单：字母/数字/空格/短横/点/加号/下划线 —— 让 admin 端预置的合法
     * 用户/机构 ID 都能过；引号/分号/注释符/管道符/反引号/美元符等 SQL 高价值
     * 字符全拦。命中范围外的字符 → 拒。
     */
    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9_ +\\-.]+$");

    /** 纯数值 → 内联为裸数字（不加引号，避免与 target 库类型冲突）。 */
    private static final Pattern NUMERIC = Pattern.compile("-?\\d+(\\.\\d+)?");

    /** 占位符残留探测器（渲染后仍出现 :name → 拒，任何形态）。 */
    private static final Pattern RESIDUAL_BINDING = Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)");

    public static final Set<String> SUPPORTED_SOURCES = Set.of(
            "CONTEXT_USER_ID", "CONTEXT_TENANT_ID", "CONTEXT_ORG", "CONTEXT_CLEARANCE_LEVEL", "STATIC_LIST");

    private static final int STATIC_LIST_MAX = 50;

    private RlsQueryFilter() {
    }

    /**
     * 渲染参数化谓词为已内联 SQL 片段。
     *
     * @param data  security-engine apply 响应的 {@code data} 段
     * @param orgId 请求头 {@code X-Org-Id} 或 query {@code orgId}；可空
     * @return 已内联的谓词体（不含外层 paren；调用方用于 {@code WHERE x}）
     * @throws RlsRenderException C.6 降级矩阵的 fail-closed 拒（Controller 转 403）
     */
    public static String render(Map<String, Object> data, String orgId) throws RlsRenderException {
        if (data == null) {
            throw new RlsRenderException("REL_RLS_NO_DATA");
        }
        Boolean denyAll = asBool(data.get("denyAll"));
        Boolean denyIfEmpty = asBool(data.get("denyIfEmpty"));
        if (Boolean.TRUE.equals(denyAll) || Boolean.TRUE.equals(denyIfEmpty)) {
            // denyAll（含 1=0 短路 / 逻辑删策略）→ 显式拒；denyIfEmpty 单独成立（即"无匹配策略
            // 且 denyIfEmpty=true"）也拒（Controller 依赖 403 ECOS-SEC-401 语义，见 QueryController）。
            throw new RlsRenderException("REL_RLS_DENY");
        }

        Object tmplObj = data.get("predicateTemplate");
        if (!(tmplObj instanceof String template) || template.isBlank()) {
            throw new RlsRenderException("REL_RLS_NO_TEMPLATE");
        }
        String t = template.trim();
        if ("1=1".equals(t)) {
            return "1=1";
        }

        List<Map<String, Object>> bindingsRaw = asBindingList(data.get("bindings"));

        // source 白名单越界 → 拒（C.2.2：越界的写入侧已经阻过，这里是运行时兜底）。
        for (Map<String, Object> b : bindingsRaw) {
            Object src = b.get("source");
            if (!(src instanceof String s) || !SUPPORTED_SOURCES.contains(s)) {
                throw new RlsRenderException("REL_RLS_SOURCE_UNSUPPORTED");
            }
        }

        // 从长名到短名依次替换（同名字段 name-part 唯一，但仍保守扫描）。
        String out = t;
        for (int i = bindingsRaw.size() - 1; i >= 0; i--) {
            Map<String, Object> b = bindingsRaw.get(i);
            Object n = b.get("name");
            if (!(n instanceof String name) || name.isEmpty()) {
                throw new RlsRenderException("REL_RLS_BAD_BINDING");
            }
            Object value = resolveValue(b, orgId);
            if (value == null) {
                throw new RlsRenderException("REL_RLS_UNRESOLVED_VALUE");
            }
            String inlined = value instanceof List<?> list ? renderInList(list) : renderScalar(value);
            out = out.replace(":" + name, inlined);
        }

        // 兜底：模板有 :name 但没匹配到 declared binding 就打到这一步 → 拒（防 fail-open）。
        if (RESIDUAL_BINDING.matcher(out).find()) {
            throw new RlsRenderException("REL_RLS_UNRESOLVED_BINDING");
        }
        return out;
    }

    private static Object resolveValue(Map<String, Object> b, String orgId) {
        Object srcObj = b.get("source");
        if (!(srcObj instanceof String src)) return null;
        return switch (src) {
            case "CONTEXT_USER_ID" -> UserContext.getCurrentUserId();
            case "CONTEXT_TENANT_ID" -> UserContext.getCurrentTenantId();
            case "CONTEXT_ORG" -> (orgId != null && !orgId.isBlank()) ? orgId : null;
            // UserContext 尚未暴露 clearance；本通道上 fail-closed（占位，后续 UserContext
            // 补齐后可放开，渲染契约不变）。
            case "CONTEXT_CLEARANCE_LEVEL" -> null;
            case "STATIC_LIST" -> asList(b.get("items"));
            default -> null;
        };
    }

    private static String renderInList(List<?> items) throws RlsRenderException {
        if (items == null || items.isEmpty()) {
            throw new RlsRenderException("REL_RLS_INCLAUSE_EMPTY");
        }
        if (items.size() > STATIC_LIST_MAX) {
            throw new RlsRenderException("REL_RLS_INCLAUSE_OVERSIZE");
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(renderScalar(items.get(i)));
        }
        sb.append(")");
        return sb.toString();
    }

    private static String renderScalar(Object v) throws RlsRenderException {
        if (v == null) {
            throw new RlsRenderException("REL_RLS_UNRESOLVED_VALUE");
        }
        String s = v.toString();
        if (s.isEmpty()) {
            throw new RlsRenderException("REL_RLS_UNRESOLVED_VALUE");
        }
        if (NUMERIC.matcher(s).matches()) {
            return s;
        }
        // 值域白名单： Safe 字符族之外的字符 = SQL 高价值字符 → 拒
        if (!SAFE_VALUE.matcher(s).find()) {
            throw new RlsRenderException("REL_RLS_UNSAFE_VALUE");
        }
        if ("true".equalsIgnoreCase(s)) return "TRUE";
        if ("false".equalsIgnoreCase(s)) return "FALSE";
        return "'" + s + "'";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asBindingList(Object o) {
        if (o instanceof List<?> l) {
            List<Map<String, Object>> out = new java.util.ArrayList<>();
            for (Object x : l) {
                if (!(x instanceof Map<?, ?> m)) continue;
                out.add((Map<String, Object>) m);
            }
            return out;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<?> asList(Object o) {
        if (o instanceof List<?> l) return l;
        return null;
    }

    private static Boolean asBool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        if (o instanceof String s && !s.isBlank()) return "true".equalsIgnoreCase(s) || "1".equals(s);
        return null;
    }

    /** 渲染异常（Controller 一律按 fail-closed 拒绝：403，不降级放行）。 */
    public static final class RlsRenderException extends RuntimeException {
        public RlsRenderException(String reason) {
            super(reason);
        }
    }
}
