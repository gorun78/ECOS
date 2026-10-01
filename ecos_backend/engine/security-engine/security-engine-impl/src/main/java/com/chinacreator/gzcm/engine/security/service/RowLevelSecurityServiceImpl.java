package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.engine.security.dao.RowLevelSecurityDao;
import com.chinacreator.gzcm.engine.security.rls.PredicateBinding;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicate;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicateValidator;
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
        List<Map<String, Object>> policies;
        try {
            policies = dao.findPoliciesForUser(tableName, userId);
        } catch (Exception e) {
            log.error("查询RLS策略失败: table={}, userId={}", tableName, userId, e);
            policies = Collections.emptyList();
        }

        RlsPredicate predicate = mergePolicies(policies);

        Map<String, Object> result = new LinkedHashMap<>();
        // ── 新契约（C.2.2 / D 章 DecisionBundle.rls）：参数化谓词 ──
        result.put("predicate", predicate.toMap());
        // ── 兼容旧契约（D.5.3）：condition 标注 deprecated，仅供日志核对，禁用于拼 SQL ──
        result.put("condition", predicate.legacyConditionForLog());
        result.put("conditionDeprecated", true);
        // 非空 = 调用方可据此绑定 PreparedStatement 参数（W30 params 恒空已关闭）
        result.put("params", new LinkedHashMap<String, Object>(bindingNameValueMap(predicate.getBindings())));
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

    private static Map<String, Object> bindingNameValueMap(List<PredicateBinding> bindings) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (PredicateBinding b : bindings) {
            m.put(b.getName(), b.getValue());
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
