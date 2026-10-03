package com.chinacreator.gzcm.engine.security.service.predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 详细设计-01 C.2.2 — RLS 谓词模板白名单校验器。
 *
 * <p>规则（C.2.2 落地）：
 * <ul>
 *   <li>禁止任何拼接语义：{@code ||} / {@code CONCAT}</li>
 *   <li>禁止 SQL 关键字黑名单：union|drop|insert|update|delete|exec|--|;|/* 以及
 *       select/create/alter/truncate/attach 等 DDL/DML 词（逐词边界匹配，避免误伤列名）</li>
 *   <li>只允许 {@code :name} 绑定变量 + 有限比较/逻辑操作符 + 预置子查询模板
 *       （PROJECT_ATTRIBUTION_SUBQUERY 白名单整串）</li>
 *   <li>STATIC_LIST ≤ 50 项（{@link PredicateBinding#STATIC_LIST_MAX}），source 必须白名单内</li>
 *   <li>存量裸文本兼容：等值/比较/IN(数字字面量)/AND 组合的简单数值条件可透传为
 *       {@code legacy_text}，其余裸文本一律拒收（新增策略不接受自由 SQL）</li>
 * </ul>
 * 违反 → 抛 {@link IllegalPredicateTemplateException}（Controller 映射为
 * 400 {@code ECOS-SEC-410}，消息只含可绑定变量清单，不回显输入原文）。</p>
 */
public final class RlsPredicateValidator {

    /** 非法模板错误码（D.5 错误码扩展） */
    public static final String ERROR_CODE_ILLEGAL_TEMPLATE = "ECOS-SEC-410";

    private static final Pattern ILLEGAL_SUBSTRING =
            Pattern.compile("\\|\\||;|--|/\\*|\\*/|CONCAT\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final String[] KEYWORDS = {
            "union", "drop", "insert", "delete", "exec", "select",
            "create", "alter", "truncate", "attach", "grant", "revoke"
    };
    private static final Pattern KEYWORD_BORDER = Pattern.compile(
            "(?i)(?<![a-z0-9_])(" + String.join("|", KEYWORDS) + ")(?![a-z0-9_])");

    /** 绑定变量占位符 */
    private static final Pattern BINDING = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    /** 预置项目归属子查询（E.6.2 种子基准形态） */
    public static final String PROJECT_ATTRIBUTION_SUBQUERY =
            "project_id IN (SELECT project_id FROM ecos_dw.biz_project_attribution WHERE department_id = :userDeptId)";

    /** 预置子查询通用形态：单层归属子查询（C.2.2 白名单第 6 类允许的唯一 SELECT 形态）。 */
    private static final Pattern ATTRIBUTION_SUBQUERY = Pattern.compile(
            "(?i)\\b[a-z_][a-z0-9_]*\\s+IN\\s*\\(\\s*SELECT\\s+[a-z_][a-z0-9_]*\\s+FROM\\s+[a-z_][a-z0-9_.]*\\s+WHERE\\s+[a-zA-Z0-9_.* =<>:()]+\\)");

    private RlsPredicateValidator() {
    }

    public static final class IllegalPredicateTemplateException extends RuntimeException {
        public IllegalPredicateTemplateException(String message) {
            super(message);
        }
    }

    /** 校验静态列表规模（STATIC_LIST 超限 → 400）。 */
    public static void validateStaticList(String source, List<String> items) {
        if (!BindingSource.STATIC_LIST.name().equals(source)) return;
        if (items == null || items.isEmpty()) {
            throw new IllegalPredicateTemplateException("STATIC_LIST 绑定必须提供至少 1 项值");
        }
        if (items.size() > PredicateBinding.STATIC_LIST_MAX) {
            throw new IllegalPredicateTemplateException(
                    "STATIC_LIST 超过上限 " + PredicateBinding.STATIC_LIST_MAX + " 项");
        }
        for (String item : items) {
            if (ILLEGAL_SUBSTRING.matcher(item).find() || KEYWORD_BORDER.matcher(item).find()) {
                throw new IllegalPredicateTemplateException("STATIC_LIST 项含非法内容，拒绝写入");
            }
        }
    }

    /**
     * 校验谓词模板（新写路径，C.2.2 白名单）。
     *
     * @param template 谓词模板（可含 {@code :name} 占位符）
     * @param bindings 该模板声明的绑定（用于校验占位符均有出处）
     * @throws IllegalPredicateTemplateException 模板非法
     */
    public static void validateTemplate(String template, List<PredicateBinding> bindings) {
        if (template == null || template.isBlank()) {
            throw new IllegalPredicateTemplateException("谓词模板不能为空");
        }
        if (template.length() > 1000) {
            throw new IllegalPredicateTemplateException("谓词模板超长（≤1000 字符）");
        }
        String t = template.trim();

        if (ILLEGAL_SUBSTRING.matcher(t).find()) {
            throw new IllegalPredicateTemplateException("谓词模板含禁止的拼接/分号/注释符，非法（可绑定变量见返回提示）");
        }

        // 引号/反引号/美元符：一律拒绝（字符串/标识符字面量不是 C.2.2 白名单形态）
        if (HAS_FORBIDDEN_CHAR.matcher(t).find()) {
            throw new IllegalPredicateTemplateException(
                    "谓词模板含引号或字面量定界符，非法（值只能以 :绑定 变量表达）");
        }

        // 关键词黑名单：先整体剥离"单层归属子查询"白名单形态（PROJECT_ATTRIBUTION_SUBQUERY 通用），再查剩余
        String scan = ATTRIBUTION_SUBQUERY.matcher(t).replaceAll(" ");
        if (scan.toUpperCase(Locale.ROOT).contains(SELECT_SUPQUERY_STUB) || KEYWORD_BORDER.matcher(scan).find()) {
            throw new IllegalPredicateTemplateException("谓词模板含非白名单 SQL 关键字，非法");
        }

        // 占位符必须与 declared bindings 一一对应有出处
        List<String> placeholders = placeholdersOf(t);
        for (String ph : placeholders) {
            boolean declared = bindings != null && bindings.stream().anyMatch(b -> ph.equals(b.name()));
            if (!declared) {
                throw new IllegalPredicateTemplateException(
                        "谓词模板含未声明的绑定变量 :" + ph + "（可绑定来源: 当前用户/租户/机构/准入等级/静态列表/项目归属子查询）");
            }
        }

        // 剩余内容：绑定占位符剥离后必须全是"列名/操作符/括号/数字/空格"家族，禁转义家族字符
        String afterBindings = BINDING.matcher(t).replaceAll(" ");
        if (HAS_FORBIDDEN_CHAR.matcher(afterBindings).find()) {
            throw new IllegalPredicateTemplateException("谓词模板含非白名单字符，非法");
        }
    }

    private static final String SELECT_SUPQUERY_STUB = "SELECT";
    /** 禁字面量定界家族：引号 / 反引号 / 注释符残留 / 备份符 */
    private static final Pattern HAS_FORBIDDEN_CHAR = Pattern.compile("[\"'`\\\\#$]");

    /** 提取模板中全部绑定占位符名（去重保序）。 */
    public static List<String> placeholdersOf(String template) {
        List<String> out = new ArrayList<>();
        if (template == null) return out;
        Matcher m = BINDING.matcher(template);
        while (m.find()) {
            if (!out.contains(m.group(1))) out.add(m.group(1));
        }
        return out;
    }

    /**
     * 存量裸文本兼容判定（C.2.2 "兼容" 段）：仅放行简单数值条件形态，
     * 如 {@code clearance_level >= 4}、{@code clearance_level >= 4 AND is_deleted = 0}、
     * {@code role_id IN ('R1','R2')} 不用（字符串字面量不进兼容口）。
     *
     * @return true = 可透传（source_kind=legacy_text，无绑定）；false = 需管理员改写为参数化谓词
     */
    public static boolean isLegacyCompatible(String filterExpr) {
        if (filterExpr == null || filterExpr.isBlank()) return false;
        String t = filterExpr.trim();
        if (ILLEGAL_SUBSTRING.matcher(t).find()) return false;
        if (KEYWORD_BORDER.matcher(t).find()) return false;
        // 逻辑等价重写：只允许 标识符 比较符 数字 [| 逻辑连接 组合 | 括号 | AND/OR/NOT | IS NULL/NOT NULL]
        String normalized = t.toUpperCase(Locale.ROOT);
        if (ILLEGAL_LEGACY_WORD.matcher(normalized).find()) return false;
        return LEGACY_ALLOWED.matcher(t).matches();
    }

    private static final Pattern ILLEGAL_LEGACY_WORD =
            Pattern.compile("(?<![A-Z0-9_])(UNION|DROP|INSERT|UPDATE|DELETE|EXEC|SELECT|CREATE|ALTER|TRUNCATE|GRANT|REVOKE)(?![A-Z0-9_])");

    /** 存量兼容允许的完整形态（逐 token 白名单正则） */
    private static final Pattern LEGACY_ALLOWED = Pattern.compile(
            "(?is)\\s*(\\b[a-z_][a-z0-9_]*\\s*(?:=|>=|<=|<>|!=|>|<|is\\s+null|is\\s+not\\s+null)\\s*\\d+)" +
            "\\s*(?:and\\s+\\b[a-z_][a-z0-9_]*\\s*(?:=|>=|<=|<>|!=|>|<|is\\s+null|is\\s+not\\s+null)\\s*\\d+)\\s*" +
            "|\\s*1\\s*=\\s*1\\s*");

    private static List<String> bindingsSources(List<PredicateBinding> bindings) {
        if (bindings == null) return List.of();
        List<String> out = new ArrayList<>();
        for (PredicateBinding b : bindings) {
            if (b.source() != null) out.add(b.source());
        }
        return out;
    }
}
