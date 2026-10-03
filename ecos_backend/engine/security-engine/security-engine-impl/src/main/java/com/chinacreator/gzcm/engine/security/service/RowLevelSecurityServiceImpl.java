package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.engine.security.service.predicate.BindingSource;
import com.chinacreator.gzcm.engine.security.service.predicate.PredicateBinding;
import com.chinacreator.gzcm.engine.security.service.predicate.RlsParamSpec;
import com.chinacreator.gzcm.engine.security.service.predicate.RlsPredicateValidator;
import com.chinacreator.gzcm.engine.security.service.predicate.RlsPredicateValidator.IllegalPredicateTemplateException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 行级安全（RLS）服务。
 *
 * <p>详细设计-01 W30 改造：{@link #apply(String, String)} 现在返回**参数化谓词**
 * （{@code predicateTemplate} + {@code bindings[]}，C.2.2）—— 调用方只能拿到
 * 模板 + 绑定声明，必须走 02 册 QueryExecutor 的 PreparedStatement 绑定
 * （W31）。老字段 {@code condition}/{@code params} 保留但标注
 * {@code conditionDeprecated=true}：仅供日志核对，禁用于拼 SQL。</p>
 *
 * <p>存储形态（V164 列）：{@code predicate_template} + {@code bindings_json}
 * + {@code source_kind(legacy_text|parameterized)}；存量裸 {@code filter_expr}
 * 经 {@link RlsPredicateValidator#isLegacyCompatible} 白名单透传，非法裸文本
 * 按 FAIL-CLOSED 忽略该条策略（不拼接、不降级放行）。</p>
 *
 * <p>PMO-data10：V154 已 ADD {@code resource_id}（IR03 只加不删），运行期
 * 双轨路由 {@code (table_name = ? OR resource_id = ?)}。</p>
 */
@Service("ecosRlsService")
public class RowLevelSecurityServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(RowLevelSecurityServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public RowLevelSecurityServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 显式列清单（IR04：禁 SELECT *） */
    private static final String RLS_POLICY_COLUMNS =
            "policy_name, table_name, resource_id, filter_expr, predicate_template, bindings_json, " +
            "source_kind, priority, enabled, is_deleted, user_id, role_id, version_no, description";

    /**
     * 根据 tableName + userId 查询所有匹配的行级安全策略，
     * 按 priority 升序排序，返回参数化谓词对象。
     *
     * <p>denyIfEmpty 默认 true：无匹配策略 ⇒ {@code 1=0} + denyAll（调用方 403
     * {@code ECOS-SEC-401}），不泄露存在性。查询侧路径当 {@code 1=0} 处理，
     * 空集返回（与 C.6 降级矩阵一致）。</p>
     */
    public Map<String, Object> apply(String tableName, String userId) {
        String sql = """
            SELECT """ + RLS_POLICY_COLUMNS + """
            FROM ecos_rls_policy
            WHERE (table_name = ? OR resource_id = ?) AND enabled = true
              AND (user_id = ? OR user_id IS NULL)
              AND (role_id IS NULL OR role_id IN (
                  SELECT "ROLE_ID" FROM TD_USER_ROLE WHERE "USER_ID" = ?
              ))
            ORDER BY priority ASC
            """;

        List<Map<String, Object>> policies;
        try {
            policies = jdbcTemplate.queryForList(sql, tableName, tableName, userId, userId);
        } catch (Exception e) {
            log.error("查询RLS策略失败（fail-closed 拒绝）: table={}, userId={}", tableName, userId, e);
            return denyResult("RLS 策略读取失败，默认 DENY");
        }

        // 逐条解析为参数化谓词（非法裸文本 → 忽略该条并告警，绝不拼接进 SQL）
        List<RlsParamSpec> specs = new ArrayList<>();
        List<Map<String, Object>> policySummaries = new ArrayList<>();
        for (Map<String, Object> p : policies) {
            // 逻辑删除/停用的策略 = 显式拒访（deny-any 短路，schema 只加不删）
            if (toInt(p.get("is_deleted"), 0) == 1 || !toBool(p.get("enabled"))) {
                return toApplyResult(
                        new RlsParamSpec(RlsParamSpec.DENY_TEMPLATE, List.of(), true, false),
                        tableName, List.of());
            }
            RlsParamSpec spec = toParamSpec(p);
            if (spec == null) {
                continue;
            }
            specs.add(spec);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", p.get("policy_name"));
            s.put("filterExpr", p.get("filter_expr"));
            s.put("predicateTemplate", p.get("predicate_template"));
            s.put("sourceKind", p.get("source_kind"));
            s.put("priority", p.get("priority"));
            policySummaries.add(s);
        }

        // 组合：多策略 AND 合并（C.3 策略变更流；combine 固定 AND — OR 组合放行面不可审计）
        RlsParamSpec combined;
        if (specs.isEmpty()) {
            combined = new RlsParamSpec(RlsParamSpec.DENY_TEMPLATE, List.of(), true, true);
        } else if (specs.size() == 1) {
            combined = specs.get(0);
        } else {
            List<String> parts = new ArrayList<>();
            List<PredicateBinding> bindings = new ArrayList<>();
            for (RlsParamSpec s : specs) {
                if (!RlsParamSpec.DENY_TEMPLATE.equals(s.template())
                        && !RlsParamSpec.ALL_ROWS_INLINE.equals(s.template())) {
                    parts.add("(" + s.template() + ")");
                    bindings.addAll(s.bindings());
                } else if (RlsParamSpec.DENY_TEMPLATE.equals(s.template())) {
                    combined = new RlsParamSpec(RlsParamSpec.DENY_TEMPLATE, List.of(), true, false);
                    break;
                }
            }
            String template = parts.isEmpty() ? RlsParamSpec.ALL_ROWS_INLINE : String.join(" AND ", parts);
            combined = new RlsParamSpec(template, dedupe(bindings), true, false);
        }

        return toApplyResult(combined, tableName, policySummaries);
    }

    private Map<String, Object> toApplyResult(RlsParamSpec combined, String tableName,
                                              List<Map<String, Object>> policySummaries) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("predicateTemplate", combined.template());
        result.put("bindings", combined.bindings().stream().map(RlsParamSpec::toMap).toList());
        result.put("denyIfEmpty", combined.denyIfEmpty());
        result.put("denyAll", combined.denyAll());
        // ── 兼容字段（§5.3：保留 ≥2 迭代，标注 deprecated，禁用于拼 SQL）──
        result.put("condition", combined.template());
        result.put("params", Collections.emptyMap());
        result.put("conditionDeprecated", true);
        result.put("policies", policySummaries);
        result.put("tableName", tableName);
        return result;
    }

    /** 单条策略行 → 参数化谓词；非法/不可解析 → null（该条不生效，告警留痕）。 */
    private RlsParamSpec toParamSpec(Map<String, Object> p) {
        String sourceKind = p.get("source_kind") != null ? p.get("source_kind").toString() : "legacy_text";
        String template = p.get("predicate_template") != null
                ? p.get("predicate_template").toString() : null;
        List<PredicateBinding> bindings = parseBindings(p.get("bindings_json"));

        if ("parameterized".equals(sourceKind) && template != null && !template.isBlank()) {
            try {
                RlsPredicateValidator.validateTemplate(template, bindings);
            } catch (IllegalPredicateTemplateException e) {
                log.warn("RLS 策略模板非法，策略跳过（fail-closed）: name={}, error={}",
                        p.get("policy_name"), e.getMessage());
                return null;
            }
            if (RlsParamSpec.ALL_ROWS_INLINE.equals(template.trim())) {
                // 全量行谓词唯一合法来源 = E.6.2 种子（guard_combo 登记），其余 1=1 收窄为受限
                log.debug("RLS 1=1 谓词生效: name={}", p.get("policy_name"));
            }
            return new RlsParamSpec(template, bindings, true, false);
        }

        // 存量裸文本兼容（C.2.2 兼容段）
        String raw = p.get("filter_expr") != null ? p.get("filter_expr").toString().trim() : "";
        if (raw.isEmpty()) {
            return null;
        }
        if (raw.equals(RlsParamSpec.ALL_ROWS_INLINE) || !RlsPredicateValidator.isLegacyCompatible(raw)) {
            log.warn("RLS 存量裸文本不合规，策略跳过（fail-closed）: name={}, expr={}",
                    p.get("policy_name"), raw);
            return null;
        }
        // 数值字面量透传（存量 clearance_level >= 4 形态）；不引入新绑定，防 S-3 扩散
        return new RlsParamSpec(raw, List.of(), true, false);
    }

    @SuppressWarnings("unchecked")
    private List<PredicateBinding> parseBindings(Object bindingsJson) {
        if (bindingsJson == null) return List.of();
        String s = bindingsJson.toString().trim();
        if (s.isEmpty() || "[]".equals(s)) return List.of();
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(s, new TypeReference<List<Map<String, Object>>>() {});
            List<PredicateBinding> out = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                String name = str(m.get("name"));
                String source = str(m.get("source"));
                String value = str(m.get("value"));
                List<String> items = null;
                Object itemsRaw = m.get("items");
                if (itemsRaw instanceof List<?> list) {
                    items = list.stream().map(String::valueOf).toList();
                }
                if (name == null || source == null) continue;
                RlsPredicateValidator.validateStaticList(source, items);
                out.add(new PredicateBinding(name, source, value, items));
            }
            return out;
        } catch (Exception e) {
            log.warn("RLS bindings 解析失败（策略运行时跳过）: {}", e.getMessage());
            return List.of();
        }
    }

    private static List<PredicateBinding> dedupe(List<PredicateBinding> bs) {
        Map<String, PredicateBinding> m = new LinkedHashMap<>();
        for (PredicateBinding b : bs) {
            m.putIfAbsent(b.name() + "@" + b.source(), b);
        }
        return new ArrayList<>(m.values());
    }

    private static int toInt(Object o, int dflt) {
        if (o == null) return dflt;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return "true".equalsIgnoreCase(o.toString()) ? 1 : dflt;
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private Map<String, Object> denyResult(String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("predicateTemplate", RlsParamSpec.DENY_TEMPLATE);
        result.put("bindings", Collections.emptyList());
        result.put("denyIfEmpty", true);
        result.put("denyAll", true);
        result.put("deniedReason", reason);
        result.put("condition", RlsParamSpec.DENY_TEMPLATE);
        result.put("params", Collections.emptyMap());
        result.put("conditionDeprecated", true);
        result.put("policies", Collections.emptyList());
        return result;
    }

    // ── CRUD ──────────────────────────────────────────

    public List<Map<String, Object>> listPolicies(String tableName) {
        String sql;
        Object[] args;
        if (tableName != null && !tableName.isBlank()) {
            sql = "SELECT " + RLS_POLICY_COLUMNS + " FROM ecos_rls_policy WHERE table_name = ? ORDER BY priority ASC, created_at DESC";
            args = new Object[]{tableName};
        } else {
            sql = "SELECT " + RLS_PAYLOAD_ONLY + " FROM ecos_rls_policy ORDER BY priority ASC, created_at DESC";
            args = new Object[]{};
        }
        return jdbcTemplate.queryForList(sql, args);
    }

    /** 列表最小列集（含审计列 created_at 供排序） */
    private static final String RLS_PAYLOAD_ONLY =
            RLS_POLICY_COLUMNS + ", created_at";

    public Map<String, Object> getPolicy(String id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + RLS_PAYLOAD_ONLY + " FROM ecos_rls_policy WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 新建策略（C.2.2 落地）。
     *
     * <p>参数化入参（{@code predicateTemplate} + {@code bindings[]}）与存量裸文本
     * （{@code filterExpr}）二选一；模板须经 {@link RlsPredicateValidator} 白名单校验，
     * 非法 → ECOS-SEC-410（Controller 映射 400，不回显输入原文）。</p>
     */
    public Map<String, Object> createPolicy(Map<String, Object> body) {
        PolicyWrite write = prepareWrite(null, body);

        String id = UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
            "INSERT INTO ecos_rls_policy (id, policy_name, table_name, filter_expr, predicate_template, bindings_json, source_kind, role_id, user_id, priority, enabled, description, created_by, version_no) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'1')",
            id,
            write.policyName(),
            write.tableName(),
            write.legacyFilterExpr(),
            write.template(),
            write.bindingsJson(),
            write.sourceKind(),
            write.roleId(),
            write.userId(),
            write.priority(),
            write.enabled(),
            write.description(),
            write.createdBy()
        );
        log.info("RLS策略创建: id={}, name={}, sourceKind={}", id, write.policyName(), write.sourceKind());
        return getPolicy(id);
    }

    /**
     * 更新策略（B.3.1 ④：version_no 递增；IR03 只加不删，停用走 is_deleted）。
     */
    public Map<String, Object> updatePolicy(String id, Map<String, Object> body) {
        Map<String, Object> existing = getPolicy(id);
        if (existing == null) return null;

        PolicyWrite write = prepareWrite(existing, body);
        String nextVersion = bumpVersion(str(existing.get("version_no")));

        jdbcTemplate.update(
            "UPDATE ecos_rls_policy SET policy_name=?, table_name=?, filter_expr=?, predicate_template=?, bindings_json=?, source_kind=?, role_id=?, user_id=?, priority=?, enabled=?, description=?, updated_at=NOW(), version_no=? WHERE id=?",
            write.policyName(),
            write.tableName(),
            write.legacyFilterExpr(),
            write.template(),
            write.bindingsJson(),
            write.sourceKind(),
            write.roleId(),
            write.userId(),
            write.priority(),
            write.enabled(),
            write.description(),
            nextVersion,
            id
        );
        log.info("RLS策略更新: id={}, version_no={}->{}", id, existing.get("version_no"), nextVersion);
        return getPolicy(id);
    }

    /** 策略停用（只停用不物理删 — §5.2 disableRlsPolicy，schema 只加不删）。 */
    public Map<String, Object> disablePolicy(String id) {
        Map<String, Object> existing = getPolicy(id);
        if (existing == null) return null;
        jdbcTemplate.update(
                "UPDATE ecos_rls_policy SET is_deleted = 1, updated_at = NOW(), version_no = ? WHERE id = ?",
                bumpVersion(str(existing.get("version_no"))), id);
        log.info("RLS策略停用: id={}", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("disabled", true);
        return result;
    }

    /** 校验 + 归一化写入载荷（模板非法即抛 IllegalPredicateTemplateException → 400）。 */
    private PolicyWrite prepareWrite(Map<String, Object> existing, Map<String, Object> body) {
        String policyName = str(body.getOrDefault("policyName",
                existing != null ? existing.get("policy_name") : null));
        String tableName = str(body.getOrDefault("tableName",
                existing != null ? existing.get("table_name") : null));
        if (policyName == null || policyName.isBlank() || tableName == null || tableName.isBlank()) {
            throw new IllegalPredicateTemplateException("policyName 与 tableName 必填");
        }

        String paramTemplate = str(body.get("predicateTemplate"));
        List<PredicateBinding> bindings = new ArrayList<>();
        if (body.get("bindings") instanceof List<?> bl) {
            for (Object o : bl) {
                if (!(o instanceof Map<?, ?> mm)) continue;
                String name = str(mm.get("name"));
                String source = str(mm.get("source"));
                String value = str(mm.get("value"));
                List<String> items = mm.get("items") instanceof List<?> il
                        ? il.stream().map(String::valueOf).toList() : null;
                if (name == null || source == null) {
                    throw new IllegalPredicateTemplateException("bindings 项缺 name/source");
                }
                if (!BindingSource.isAllowed(source)) {
                    throw new IllegalPredicateTemplateException(
                            "绑定来源不在白名单（可绑定: " + String.join("/", BindingSource.allowedNames().stream().toArray(String[]::new)) + "）");
                }
                RlsPredicateValidator.validateStaticList(source, items);
                bindings.add(new PredicateBinding(name, source, value, items));
            }
        }

        String template = null;
        String bindingsJson = null;
        String sourceKind = "legacy_text";
        String legacyExpr = existing != null && existing.get("filter_expr") != null
                ? existing.get("filter_expr").toString() : null;

        if ((body.containsKey("predicateTemplate") || body.containsKey("bindings"))
                && paramTemplate != null && !paramTemplate.isBlank()) {
            RlsPredicateValidator.validateTemplate(paramTemplate, bindings);
            template = paramTemplate.trim();
            sourceKind = "parameterized";
            legacyExpr = null;
            try {
                bindingsJson = MAPPER.writeValueAsString(
                        bindings.stream().map(RlsParamSpec::toMap).toList());
            } catch (JsonProcessingException e) {
                throw new IllegalPredicateTemplateException("bindings 序列化失败，策略未写入");
            }
        } else if (body.get("filterExpr") != null) {
            // 新增裸文本：必须过存量兼容白名单，否则拒收（lint 阻断新增裸文本，C.2.2 兼容段）
            String raw = str(body.get("filterExpr")).trim();
            if (!RlsPredicateValidator.isLegacyCompatible(raw)) {
                throw new IllegalPredicateTemplateException(
                        "新增策略禁止自由文本 SQL（可绑定变量见返回提示，模板形态参见 C.2.2）");
            }
            legacyExpr = raw;
        }

        if (template == null && (legacyExpr == null || legacyExpr.isBlank())) {
            throw new IllegalPredicateTemplateException("谓词模板或存量条件二选一必填");
        }

        return new PolicyWrite(
                policyName,
                tableName,
                legacyExpr,
                template,
                bindingsJson,
                sourceKind,
                str(body.getOrDefault("roleId", existing != null ? existing.get("role_id") : null)),
                str(body.getOrDefault("userId", existing != null ? existing.get("user_id") : null)),
                body.containsKey("priority") ? ((Number) body.get("priority")).intValue()
                        : (existing != null && existing.get("priority") != null ? toInt(existing.get("priority"), 0) : 0),
                body.containsKey("enabled") ? toBool(body.get("enabled"))
                        : (existing != null && existing.get("enabled") != null ? toBool(existing.get("enabled")) : true),
                str(body.getOrDefault("description", existing != null ? existing.get("description") : null)),
                str(body.getOrDefault("createdBy", existing != null ? existing.get("created_by") : null))
        );
    }

    private record PolicyWrite(String policyName, String tableName, String legacyFilterExpr,
                               String template, String bindingsJson, String sourceKind,
                               String roleId, String userId, int priority, boolean enabled,
                               String description, String createdBy) {}

    private static boolean toBool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(o)) || "1".equals(String.valueOf(o));
    }

    /** version_no 递增（存量默认 '1'）。 */
    static String bumpVersion(String current) {
        try {
            int v = current != null ? Integer.parseInt(current.trim()) : 1;
            return String.valueOf(Math.max(v, 1) + 1);
        } catch (NumberFormatException e) {
            return "2";
        }
    }

    public boolean deletePolicy(String id) {
        // §5.2：DELETE 语义 = 停用（is_deleted=1），物理删禁止（schema 只加不删）
        return disablePolicy(id) != null;
    }
}
