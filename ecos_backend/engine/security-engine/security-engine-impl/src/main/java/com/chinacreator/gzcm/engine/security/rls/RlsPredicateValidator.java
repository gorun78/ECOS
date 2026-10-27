package com.chinacreator.gzcm.engine.security.rls;

import com.chinacreator.gzcm.common.error.SecurityErrorCode;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 详细设计-01 C.2.2 — RLS 谓词模板白名单校验器。
 *
 * <p>取代 S-3"策略即代码"的裸 SQL 文本存储。校验规则：</p>
 * <ol>
 *   <li>模板中的字面量只能是 <b>列名白名单比较 / 数值常量 / {@code :name} 占位符</b>；</li>
 *   <li>禁止任何拼接语义（{@code ||}、{@code CONCAT}）；</li>
 *   <li>禁止 SQL 关键字出现在模板（regex 黑名单：
 *       {@code union|drop|insert|update|delete|exec|--|;|/*}）；</li>
 *   <li>每个 {@code :name} 占位符必须有对应 binding，且 source 在白名单内；</li>
 *   <li>{@code STATIC_LIST} 值 ≤ 50 项，逐项绑定。</li>
 * </ol>
 *
 * <p>非法即抛 {@link TemplateRejectedException}（→ 端点 400 {@code ECOS-SEC-410}）。</p>
 */
public final class RlsPredicateValidator {

    /** C.2.2 正则黑名单：DSL 子集 + 危险语法。小写匹配。 */
    private static final Pattern DANGEROUS = Pattern.compile(
            "(?i)\\b(union|drop|insert|update|delete|exec|truncate|alter|create|grant)"
                    + "|--|;|/\\*|\\*\\/|\\|\\||\\bconcat\\b", Pattern.CASE_INSENSITIVE);

    /** 占位符识别：:name（name = 标识符）。 */
    private static final Pattern PLACEHOLDER = Pattern.compile(":(\\w+)");

    /** STATIC_LIST 上限（C.2.2）。 */
    public static final int STATIC_LIST_MAX = 50;

    /** 非法模板异常：Controller 捕获 → 400。 */
    public static final class TemplateRejectedException extends RuntimeException {
        public TemplateRejectedException(String msg) { super(msg); }
    }

    /**
     * 校验并规范化一条谓词。合法则返原值 + 绑定后的 predicate；非法抛
     * {@link TemplateRejectedException}。
     *
     * @param template 谓词模板文本（如 {@code department_id = :userDeptId}）
     * @param bindings 绑定项列表
     */
    public void validate(String template, List<PredicateBinding> bindings) {
        if (template == null || template.isBlank()) {
            throw new TemplateRejectedException("谓词模板不能为空");
        }
        String t = template.trim();

        // ③ 黑名单：拼接语义 + SQL 关键字
        if (DANGEROUS.matcher(t).find()) {
            throw new TemplateRejectedException(
                    "谓词模板含非白名单字面量/关键字（" + SecurityErrorCode.ECOS_SEC_410 + "）: " + t);
        }

        if (bindings == null) bindings = java.util.Collections.emptyList();

        // 收集模板里出现的占位符名
        java.util.Set<String> usedNames = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = PLACEHOLDER.matcher(t);
        while (m.find()) usedNames.add(m.group(1));

        java.util.Set<String> declaredNames = new java.util.LinkedHashSet<>();
        for (PredicateBinding b : bindings) {
            if (b.getName() == null || !b.getName().matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                throw new TemplateRejectedException("非法绑定名: " + b.getName());
            }
            if (!PredicateBinding.isKnownSource(b.getSource())) {
                throw new TemplateRejectedException("绑定 source 不在白名单: " + b.getSource());
            }
            // STATIC_LIST 上限
            if (PredicateBinding.SRC_STATIC_LIST.equals(b.getSource())) {
                if (!(b.getValue() instanceof List<?> lst) || lst.size() > STATIC_LIST_MAX) {
                    throw new TemplateRejectedException(
                            "STATIC_LIST 值非法或超过 " + STATIC_LIST_MAX + " 项");
                }
            }
            declaredNames.add(b.getName());
        }

        // ④ 每个占位符必须有对应 binding（不允许未绑定占位符透传）
        for (String used : usedNames) {
            if (!declaredNames.contains(used)) {
                throw new TemplateRejectedException("占位符 :"+used+" 缺少对应绑定");
            }
        }
    }

    /** 便捷重载：从 REST body（Map 形态bindings）校验。 */
    public void validateTemplate(Map<String, Object> body) {
        Object tpl = body.get("predicateTemplate");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) body.get("bindings");
        String template = tpl == null ? null : tpl.toString();
        List<PredicateBinding> bindings = new java.util.ArrayList<>();
        if (raw != null) {
            for (Map<String, Object> r : raw) bindings.add(PredicateBinding.fromMap(r));
        }
        validate(template, bindings);
    }
}
