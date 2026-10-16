package com.chinacreator.gzcm.engine.security.rls;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 详细设计-01 C.2.2 / W31 —— {@code predicateTemplate}（{@code :name} 占位符）到
 * <b>JDBC {@code ?} 形态 WHERE 片段 + 有序参数</b>的渲染器（#142/B4-3c-2，收口差距 D-B4-17）。
 *
 * <p><b>为什么必须有这个类</b>：{@code /rls/apply} 的应答此前只有
 * {@code predicate.predicateTemplate}（占位符文本）与 {@code params}（名→值），
 * 三个引擎侧消费者（workspace / ontology / ai）都只读一个<b>不存在</b>的
 * {@code whereClause} 字段，取到 {@code null} 后返回空串 ⇒ 行级安全在页面/本体/AI
 * 通道上恒等于"不加过滤"（fail-open）。本类把"渲染出可绑定 SQL 片段"这件事做成
 * 生产者的单源职责，消费者只需绑定参数，不再各自拼 SQL。</p>
 *
 * <p><b>三条不可让的口径</b>（违反任何一条本类返拒绝形态，绝不返"看起来能用"的片段）：</p>
 * <ol>
 *   <li><b>IR05 参数化</b>：绑定值一律以 {@code ?} 占位交付，值走
 *       {@link Rendered#whereParams()}；任何情况下不把字面值内联进 SQL 文本
 *       （数据库访问规范 IR05 禁裸 SQL 拼接）。</li>
 *   <li><b>按名解析、按模板出现顺序出参</b>：{@code RlsPredicate.getBindings()} 的
 *       清单顺序是"策略段声明顺序"，与模板里 {@code :name} 的<b>从左到右出现顺序无关</b>
 *       （多条策略合并时两者必然错位）。按清单顺序发参会把值绑到别的列上 ⇒
 *       这里以模板 token 流为准逐个解析，参数顺序 = token 出现顺序。</li>
 *   <li><b>未解析即拒绝</b>：{@code CONTEXT_*} 解析不到值（现网无组织域单源，见
 *       {@link RlsSubjectContext}）、{@code STATIC_LIST} 为空、同一名字解析出两个不同值、
 *       子查询型 source —— 全部转拒绝形态 {@link #DENY_CLAUSE}，
 *       禁把 {@code col = ?} 配 {@code null} 参数下发（那会把语义变成"永假"或"未知"，
 *       同时掩盖"上下文解析未接"这一缺陷）。</li>
 * </ol>
 *
 * <p><b>模板合规复用单源校验器</b>：渲染前调用 {@link RlsPredicateValidator}
 * （同一份黑名单 + 占位符-绑定配对规则），因此 {@code legacy_text} 存量策略里的裸 SQL
 * 文本若含 {@code --}/{@code ;}/{@code ||}/{@code union} 等形态会在渲染面变红，
 * 而不是被原样拼进消费者 SQL。本类<b>不</b>另立第二套词法。</p>
 */
public final class RlsPredicateRenderer {

    /**
     * 拒绝形态的 WHERE 片段（#142/B4-3d-5 起为单源别名，值来自
     * {@link RlsExecutionContract#DENY_CLAUSE}；拼进 WHERE 即零行，默认 DENY）。
     */
    public static final String DENY_CLAUSE = RlsExecutionContract.DENY_CLAUSE;

    /** 恒真片段（{@code rls-finance-all} 的"全量可见"策略形态）；供消费者分态判定。 */
    public static final String ALLOW_ALL_CLAUSE = RlsExecutionContract.ALLOW_ALL_CLAUSE;

    /** 占位符识别与 {@link RlsPredicateValidator#PLACEHOLDER} 同形（{@code :name}）。 */
    private static final Pattern PLACEHOLDER = Pattern.compile(":(\\w+)");

    private final RlsPredicateValidator validator = new RlsPredicateValidator();

    /**
     * 渲染结果。
     *
     * @param whereClause       JDBC 可绑定的 WHERE 片段；拒绝态为 {@link #DENY_CLAUSE}
     * @param whereParams       与 {@code whereClause} 里 {@code ?} 同序的参数表（可为空列表）
     * @param unresolvedBindings 解析不到值的上下文绑定名（正常态为空）
     * @param denyReason        拒绝原因（{@code null}=未拒绝）；随应答外发，禁消费者折叠成"无数据"
     * @param noPolicyMatched   是否"一条策略都没命中"（配合 {@code denyIfEmpty} 的拒绝依据）
     */
    public record Rendered(String whereClause,
                           List<Object> whereParams,
                           List<String> unresolvedBindings,
                           String denyReason,
                           boolean noPolicyMatched) {

        public boolean denied() { return denyReason != null; }

        /** 恒真（无行级限制）而非"未给谓词"——两者在消费端必须分态。 */
        public boolean allowAll() {
            return !denied() && ALLOW_ALL_CLAUSE.equals(normalize(whereClause));
        }

        /** REST 序列化（{@code /rls/apply} 与 {@code DecisionBundle.rls} 共用）。
         *
         * <p>#142/B4-3d-5（D-B4-24）：键名一律取 {@link RlsExecutionContract} 常量——
         * 生产者与消费者读的是同一份键面，不存在"生产者改键、消费者不知"的漂移空间。</p>
         */
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(RlsExecutionContract.KEY_WHERE_CLAUSE, whereClause);
            m.put(RlsExecutionContract.KEY_WHERE_PARAMS, whereParams);
            m.put(RlsExecutionContract.KEY_UNRESOLVED_BINDINGS, unresolvedBindings);
            m.put(RlsExecutionContract.KEY_NO_POLICY_MATCHED, noPolicyMatched);
            if (denyReason != null) m.put(RlsExecutionContract.KEY_RLS_DENY_REASON, denyReason);
            return m;
        }

        static Rendered deny(String reason, boolean noPolicyMatched, List<String> unresolved) {
            return new Rendered(DENY_CLAUSE, List.of(), List.copyOf(unresolved), reason, noPolicyMatched);
        }
    }

    /**
     * 渲染合并后的谓词。
     *
     * @param predicate   {@code RowLevelSecurityServiceImpl#mergePolicies} 的产物
     * @param subjectCtx  主体上下文（服务端派生；{@code null} 时按"全属性未解析"处理）
     */
    public Rendered render(RlsPredicate predicate, RlsSubjectContext subjectCtx) {
        if (predicate == null) {
            return Rendered.deny("RLS_MERGED_PREDICATE_ABSENT", true, List.of());
        }
        // [1] 无命中策略：segments 是"命中了几段"的唯一单源（模板 "1=1" 只是兜底文本，
        //     不能用模板区分"财务全量策略"与"一条策略都没有"）
        List<String> segments = predicate.getSegments();
        if (segments == null || segments.isEmpty()) {
            return Rendered.deny("RLS_NO_POLICY_MATCHED", true, List.of());
        }
        if (!predicate.hasPredicate()) {
            return Rendered.deny("RLS_TEMPLATE_BLANK", false, List.of());
        }

        String template = predicate.getPredicateTemplate();
        List<PredicateBinding> bindings =
                predicate.getBindings() == null ? List.of() : predicate.getBindings();

        // [2] 词法合规走单源校验器（黑名单 + 占位符必须有绑定），非法即拒绝，不降级拼接
        try {
            validator.validate(template, bindings);
        } catch (RlsPredicateValidator.TemplateRejectedException e) {
            return Rendered.deny("RLS_TEMPLATE_REJECTED: " + e.getMessage(), false, List.of());
        }

        // [3] 名字 → 解析值（同名多绑定必须同值，否则拒绝）
        Map<String, Object> resolvedByName = new LinkedHashMap<>();
        List<String> unresolvedNames = new ArrayList<>();
        for (PredicateBinding b : bindings) {
            String name = b.getName();
            if (name == null) continue;
            Object value;
            if (PredicateBinding.isContextSource(b.getSource())) {
                value = subjectCtx == null ? null : subjectCtx.resolveBySource(b.getSource());
            } else if (PredicateBinding.SRC_STATIC_LIST.equals(b.getSource())) {
                value = b.getValue();
                if (!(value instanceof List<?> lst) || lst.isEmpty()) {
                    return Rendered.deny("RLS_STATIC_LIST_INVALID(name=" + name + ")", false, unresolvedNames);
                }
            } else {
                // PROJECT_ATTRIBUTION_SUBQUERY：值无法作为参数绑定（它是 SQL 片段），
                // 现网 0 处使用（V246 的归属子查询写在模板里、绑定用 CONTEXT_ORG）。
                // 保持拒绝而不是把 SQL 片段拼进模板 —— 那是 IR05 的拼接面。
                return Rendered.deny("RLS_SOURCE_NOT_RENDERABLE(source=" + b.getSource()
                        + ", name=" + name + ")", false, unresolvedNames);
            }
            if (value == null) {
                if (!unresolvedNames.contains(name)) unresolvedNames.add(name);
                continue;
            }
            Object previous = resolvedByName.get(name);
            if (previous != null && !previous.equals(value)) {
                return Rendered.deny("RLS_BINDING_CONFLICT(name=" + name + ")", false, unresolvedNames);
            }
            resolvedByName.put(name, value);
        }
        if (!unresolvedNames.isEmpty()) {
            return Rendered.deny("RLS_CONTEXT_UNRESOLVED" + unresolvedNames, false, unresolvedNames);
        }

        // [4] 按模板 token 顺序渲染：`:name` → 1 个或 N 个 `?`，参数同序追加
        StringBuilder clause = new StringBuilder(template.length() + 16);
        List<Object> params = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template);
        int cursor = 0;
        while (m.find()) {
            clause.append(template, cursor, m.start());
            Object value = resolvedByName.get(m.group(1));
            if (value instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    clause.append(i == 0 ? "?" : ", ?");
                    params.add(list.get(i));
                }
            } else {
                clause.append('?');
                params.add(value);
            }
            cursor = m.end();
        }
        clause.append(template.substring(cursor));
        return new Rendered(clause.toString(), Collections.unmodifiableList(params),
                List.of(), null, false);
    }

    /**
     * 归一化：剥括号与空白（与 workspace 侧 ExportDecision 的分态口径一致）。
     *
     * <p>#142/B4-3d-5（D-B4-24）：口径单源于 {@link RlsExecutionContract#normalize}——
     * 生产者与四通道必须用同一个"什么算同一条谓词"的判据，否则 {@code "(1=1)"} 在一侧是恒真、
     * 在另一侧是非平凡谓词，分态就又分裂了。</p>
     */
    public static String normalize(String clause) {
        return RlsExecutionContract.normalize(clause);
    }

    /** 供消费者复用的"名字集合稳定序"辅助（测试与序列化用）。 */
    static Set<String> namesOf(List<PredicateBinding> bindings) {
        Set<String> out = new LinkedHashSet<>();
        if (bindings != null) {
            for (PredicateBinding b : bindings) if (b.getName() != null) out.add(b.getName());
        }
        return out;
    }
}
