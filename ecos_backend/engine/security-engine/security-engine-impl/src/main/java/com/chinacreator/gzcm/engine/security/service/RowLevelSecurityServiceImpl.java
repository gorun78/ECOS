package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.dao.RowLevelSecurityDao;
import com.chinacreator.gzcm.engine.security.rls.PredicateBinding;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicate;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicateRenderer;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicateValidator;
import com.chinacreator.gzcm.engine.security.rls.RlsSubjectContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service("ecosRlsService")
public class RowLevelSecurityServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(RowLevelSecurityServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 无状态渲染器（模板词法单源＝{@link RlsPredicateValidator}），静态共享不引入可变状态。 */
    private static final RlsPredicateRenderer RENDERER = new RlsPredicateRenderer();

    private final RowLevelSecurityDao dao;

    public RowLevelSecurityServiceImpl(RowLevelSecurityDao dao) {
        this.dao = dao;
    }

    /**
     * 根据 tableName + userId 查询所有匹配的行级安全策略，
     * 按 priority 升序排序，用 AND 合并所有 filter_expr。
     *
     * <p>PMO-data10：V154 已 ADD {@code resource_id}（IR03 只加不删），运行期
     * 双轨路由 {@code (table_name = ? OR resource_id = ?)} — 老行
     * {@code resource_id} 为空走 {@code table_name} 兜底，新行优先按
     * {@code resource_id} 命中。新增 L4 资产准入约束 {@code clearance_level >= 4}
     * 由 {@code DataSecurityLevelEventListener} 写 policy（{@code filter_expr = clearance_level
     * >= 4}），本方法合并在 WHERE 子句，调用方需把 {@code clearance_level} 作为 WHERE
     * 绑定变量传入（参见 {@code data-engine} 查询调用方）。
     */
    public Map<String, Object> apply(String tableName, String userId) {
        return apply(tableName, RlsSubjectContext.ofUserId(userId));
    }

    /**
     * 上下文形态的裁决（#142/B4-3c-2 只增）：{@code /rls/apply} 与三通道入口共用一条生产链，
     * 区别只在主体属性来源 —— 组织域/密级只能服务端派生（见 {@link RlsSubjectContext}）。
     *
     * <p><b>D-B4-17 收口点</b>：应答在此发出 {@code whereClause} + {@code whereParams}
     * （JDBC {@code ?} 形态与同序参数），消费者不再"读不到 whereClause 就当无策略放行"。
     * 渲染失败（模板非法 / 上下文未解析 / 空列表 / 不可绑定 source）一律是
     * {@code whereClause="1=0"} + {@code rlsDenyReason}，即<b>拒绝</b>而非空串。</p>
     */
    public Map<String, Object> apply(String tableName, RlsSubjectContext subjectCtx) {
        List<Map<String, Object>> policies;
        try {
            policies = dao.findPoliciesForUser(tableName, subjectCtx == null ? null : subjectCtx.userId());
        } catch (Exception e) {
            log.error("查询RLS策略失败: table={}, userId={}", tableName,
                    subjectCtx == null ? null : subjectCtx.userId(), e);
            policies = Collections.emptyList();
        }

        RlsPredicate predicate = mergePolicies(policies);
        RlsPredicateRenderer.Rendered rendered = RENDERER.render(predicate, subjectCtx);

        Map<String, Object> result = new LinkedHashMap<>();
        // ── 新契约（C.2.2 / D 章 DecisionBundle.rls）：参数化谓词 ──
        // 键面单源（#142/B4-3d-5，差距 D-B4-24）：生产者与四消费者读写同一份键常量，
        // 不存在「生产者改键、消费者不知」的漂移空间。
        result.put(RlsExecutionContract.KEY_PREDICATE_SEGMENT, predicate.toMap());
        // ── 兼容旧契约（D.5.3）：condition 标注 deprecated，仅供日志核对，禁用于拼 SQL ──
        result.put("condition", predicate.legacyConditionForLog());
        result.put("conditionDeprecated", true);
        // ── 执行契约（D-B4-17）：可直接绑定 PreparedStatement 的片段 + 同序参数 ──
        result.putAll(rendered.toMap());
        result.put(RlsExecutionContract.KEY_DENY_IF_EMPTY, predicate.isDenyIfEmpty());
        // 非空 = 调用方可据此绑定 PreparedStatement 参数（W30 params 恒空已关闭）
        result.put("params", new LinkedHashMap<String, Object>(
                bindingNameValueMap(predicate.getBindings(), subjectCtx)));
        result.put("policies", policies.stream()
                .map(m -> {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("name", m.get("policy_name"));
                    p.put("filterExpr", m.get("filter_expr"));
                    p.put("priority", m.get("priority"));
                    p.put("sourceKind", m.getOrDefault("source_kind", "legacy_text"));
                    return p;
                }).collect(Collectors.toList()));
        result.put("tableName", tableName);
        return result;
    }

    /**
     * 绑定名 → 值（{@code CONTEXT_*} 现按上下文解析后回填，未解析仍为 {@code null}）。
     *
     * <p>原实现只发 {@code b.getValue()}，而 {@code CONTEXT_*} 的 value 按契约恒为 {@code null}
     * （{@link PredicateBinding} 注释："在应用期由上下文解析"）—— 全仓从来没有那个"应用期解析"，
     * 所以本 Map 历史上恒空值，消费者据此绑参等于绑 {@code null}（差距 D-B4-23）。
     * 现在解析单源在 {@link RlsPredicateRenderer}／{@link RlsSubjectContext}，此处复用同一口径，
     * 避免出现第二套"名字→值"的真值。</p>
     */
    private static Map<String, Object> bindingNameValueMap(List<PredicateBinding> bindings,
                                                           RlsSubjectContext subjectCtx) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (PredicateBinding b : bindings) {
            Object value = PredicateBinding.isContextSource(b.getSource())
                    ? (subjectCtx == null ? null : subjectCtx.resolveBySource(b.getSource()))
                    : b.getValue();
            m.put(b.getName(), value);
        }
        return m;
    }

    /**
     * 把命中的策略集合并成一条 {@link RlsPredicate}。
     * 有任一条 {@code predicate_template}（参数化）→ 用占位符模板 + bindings；
     * 否则回退到存量 {@code filter_expr} 裸文本（source_kind=legacy_text 兜底，迁移前兼容）。
     */
    private RlsPredicate mergePolicies(List<Map<String, Object>> policies) {
        RlsPredicate merged = new RlsPredicate();
        merged.setCombine(RlsPredicate.COMBINE_AND);
        merged.setDenyIfEmpty(true);

        List<String> segments = new ArrayList<>();
        List<PredicateBinding> allBindings = new ArrayList<>();

        for (Map<String, Object> p : policies) {
            String sourceKind = p.get("source_kind") == null ? "legacy_text" : p.get("source_kind").toString();
            String template = p.get("predicate_template") == null ? null : p.get("predicate_template").toString();

            if ("parameterized".equals(sourceKind) && template != null && !template.isBlank()) {
                segments.add("(" + template + ")");
                allBindings.addAll(parseBindingsJson(p.get("bindings_json")));
            } else {
                String expr = (String) p.get("filter_expr");
                if (expr != null && !expr.isBlank()) {
                    segments.add("(" + expr + ")");
                }
            }
        }

        merged.setSegments(segments);
        merged.setBindings(allBindings);
        merged.setPredicateTemplate(segments.isEmpty() ? "1=1" : String.join(" AND ", segments));
        return merged;
    }

    private List<PredicateBinding> parseBindingsJson(Object raw) {
        if (raw == null) return Collections.emptyList();
        try {
            List<Map<String, Object>> lst =
                    MAPPER.readValue(raw.toString(), new TypeReference<List<Map<String, Object>>>() {});
            List<PredicateBinding> out = new ArrayList<>();
            for (Map<String, Object> r : lst) out.add(PredicateBinding.fromMap(r));
            return out;
        } catch (Exception e) {
            log.warn("解析 bindings_json 失败，按 legacy 处理: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── CRUD ──────────────────────────────────────────

    public List<Map<String, Object>> listPolicies(String tableName) {
        return dao.listPolicies(tableName);
    }

    public Map<String, Object> getPolicy(String id) {
        List<Map<String, Object>> rows = dao.findByPolicyId(id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> createPolicy(Map<String, Object> body) {
        String id = UUID.randomUUID().toString().replace("-", "");
        dao.createPolicy(
            id,
            body.getOrDefault("policyName", ""),
            body.getOrDefault("tableName", ""),
            body.getOrDefault("filterExpr", ""),
            body.getOrDefault("roleId", null),
            body.getOrDefault("userId", null),
            body.getOrDefault("priority", 0),
            body.getOrDefault("enabled", true),
            body.getOrDefault("description", null),
            body.getOrDefault("createdBy", null)
        );
        log.info("RLS策略创建: id={}, name={}", id, body.get("policyName"));
        return getPolicy(id);
    }

    public Map<String, Object> updatePolicy(String id, Map<String, Object> body) {
        Map<String, Object> existing = getPolicy(id);
        if (existing == null) return null;

        dao.updatePolicy(
            body.getOrDefault("policyName", existing.get("policy_name")),
            body.getOrDefault("tableName", existing.get("table_name")),
            body.getOrDefault("filterExpr", existing.get("filter_expr")),
            body.getOrDefault("roleId", existing.get("role_id")),
            body.getOrDefault("userId", existing.get("user_id")),
            body.getOrDefault("priority", existing.get("priority")),
            body.getOrDefault("enabled", existing.get("enabled")),
            body.getOrDefault("description", existing.get("description")),
            id
        );
        log.info("RLS策略更新: id={}", id);
        return getPolicy(id);
    }

    public boolean deletePolicy(String id) {
        int rows = dao.deletePolicy(id);
        if (rows > 0) {
            log.info("RLS策略删除: id={}", id);
        }
        return rows > 0;
    }

    /**
     * 详细设计-01 D.5.2 {@code upsertRlsPolicy}：携带 {@code predicateTemplate} + {@code bindings[]}。
     * 经 {@link RlsPredicateValidator} 校验后落 V164 新列（{@code predicate_template} /
     * {@code bindings_json} / {@code source_kind='parameterized'}）。非法模板 → 抛
     * {@link RlsPredicateValidator.TemplateRejectedException}（端点 400 ECOS-SEC-410）。
     */
    public Map<String, Object> upsertPolicy(Map<String, Object> body) {
        String policyName = (String) body.get("policyName");
        String tableName  = (String) body.get("tableName");
        String template   = (String) body.get("predicateTemplate");
        if (policyName == null || policyName.isBlank()) throw new IllegalArgumentException("policyName 不能为空");
        if (tableName  == null || tableName.isBlank())  throw new IllegalArgumentException("tableName 不能为空");
        if (template   == null || template.isBlank())   throw new IllegalArgumentException("predicateTemplate 不能为空");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) body.get("bindings");
        List<PredicateBinding> bindings = new ArrayList<>();
        if (raw != null) for (Map<String, Object> r : raw) bindings.add(PredicateBinding.fromMap(r));
        new RlsPredicateValidator().validate(template, bindings);

        String bindingsJson;
        try {
            List<Map<String, Object>> asMaps = new ArrayList<>();
            for (PredicateBinding b : bindings) asMaps.add(b.toMap());
            bindingsJson = MAPPER.writeValueAsString(asMaps);
        } catch (Exception e) {
            throw new IllegalArgumentException("bindings 序列化失败: " + e.getMessage());
        }

        String id = body.get("id") == null ? UUID.randomUUID().toString().replace("-", "") : body.get("id").toString();
        int priority = body.get("priority") == null ? 10 : ((Number) body.get("priority")).intValue();
        Object enabled = body.getOrDefault("enabled", true);
        int enabledInt = enabled instanceof Boolean b ? (b ? 1 : 0) : ((Number) enabled).intValue();

        Object existing = dao.existsProbe(id);
        if (existing == null) {
            dao.insertParameterized(
                id, policyName, tableName, template,
                body.get("roleId"), body.get("userId"), priority, enabledInt,
                bindingsJson, body.get("createdBy"));
        } else {
            dao.updateParameterized(
                policyName, tableName, template,
                body.getOrDefault("roleId", null), body.getOrDefault("userId", null), priority, enabledInt,
                bindingsJson, id);
        }
        log.info("RLS策略 upsert(参数化): id={}, name={}, table={}, template={}", id, policyName, tableName, template);
        return getPolicy(id);
    }
}
