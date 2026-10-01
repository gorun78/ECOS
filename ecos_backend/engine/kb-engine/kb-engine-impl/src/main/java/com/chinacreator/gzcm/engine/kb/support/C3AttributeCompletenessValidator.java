package com.chinacreator.gzcm.engine.kb.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * REQ-KB-01 C3 属性完整性校验器（详细设计-04 F04-05，全新）。
 *
 * <p>在 C1（类型存在性）与 Q2（materialized）裁决**之后**、C4（映射有效性）与节点 upsert
 * **之前**插入 — 全 WARN 不阻断；三条规则：
 * <ul>
 *   <li>R1 <b>未知属性</b> {@code C3_UNKNOWN_ATTR}：映射引用命中了本体实体但引用不在该
 *       实体的属性集合内（关系引用除外）→ WARN，保留原值写入（不阻断）。；</li>
 *   <li>R2 <b>必填缺失</b> {@code C3_MISSING_REQUIRED}：本体实体有必填属性但行值缺 →
 *       WARN，仍允许写入（值落 null，属性集合无锚点时仅记 issue）。；</li>
 *   <li>R3 <b>类型不符</b> {@code C3_TYPE_MISMATCH}：行值形态与本体属性声明类型不符 →
 *       WARN 且该字段在 {@code propertiesJson} 中置 null（不写入实体语义内容）。；</li>
 * </ul>
 *
 * <p><b>纯算法</b>：无 DB/LLM/Neo4j 依赖，直接输入 row 值 + 字段映射 + 本体属性集合 →
 * 输出 {@link Result}（issues + 应置 null 的 property 集合）。上层服务（
 * {@code KbEntityInstanceExtractionService}）依本结果重排 {@code propertiesJson}。
 *
 * <p>静态方法 {@link #validate(...)}/{@link #isTypeCompatible(...)} 可直接单测。
 */
public final class C3AttributeCompletenessValidator {

    /** 未知属性（R1）。 */
    public static final String CODE_UNKNOWN_ATTR = "C3_UNKNOWN_ATTR";
    /** 必填缺失（R2）。 */
    public static final String CODE_MISSING_REQUIRED = "C3_MISSING_REQUIRED";
    /** 类型不符（R3）。 */
    public static final String CODE_TYPE_MISMATCH = "C3_TYPE_MISMATCH";

    /** 关系类引用前缀（分册 03 契约约定；不作属性 R1 判定）。 */
    private static final String RELATION_REF_PREFIX = "rel:";

    private C3AttributeCompletenessValidator() {
    }

    /** 单条校验输入。所有类型均为字符串（与 DW 元数据字段类型保持一致）。 */
    public record PropertyDef(String code, boolean required, String type) {
    }

    /** 单条字段映射引用 → DW 物理列。 */
    public record MappingRef(String dwColumn, String ontologyRef) {
    }

    /** 校验输出：需要置 null 的 property 集合；issues 供上层报告。 */
    public record Result(List<Issue> issues, Set<String> nullifiedRefs) {
    }

    /** C3 一条 issue 明细。 */
    public record Issue(String entityCode, String code, String message) {
    }

    /**
     * R1 未知属性检查（引用级，行无关）— 单映射一次调用。
     *
     * @return issue 列表（仅 R1；关系引用 {@code rel:*} 不参与属性 R1）
     */
    public static List<Issue> checkUnknownAttrs(String entityCode,
                                                 Set<PropertyDef> properties,
                                                 Set<String> relations,
                                                 List<MappingRef> mappingRefs) {
        List<Issue> issues = new ArrayList<>();
        if (mappingRefs == null) {
            return issues;
        }
        Set<String> knownProps = propertyCodes(properties);
        Set<String> knownRels = relationCodes(relations);
        for (MappingRef ref : mappingRefs) {
            if (ref == null || ref.ontologyRef() == null || ref.ontologyRef().isBlank()) {
                continue;
            }
            String refKey = ref.ontologyRef().trim();
            if (refKey.startsWith(RELATION_REF_PREFIX)) {
                if (!knownRels.isEmpty() && !knownRels.contains(normalize(refKey))) {
                    issues.add(new Issue(entityCode, CODE_UNKNOWN_ATTR,
                            "关系引用 " + refKey + " 不在本体实体关系集合内"));
                }
            } else if (!knownProps.isEmpty() && !knownProps.contains(normalize(refKey))) {
                issues.add(new Issue(entityCode, CODE_UNKNOWN_ATTR,
                        "映射引用 " + refKey + " 不在本体实体 " + entityCode + " 属性集合内"));
            }
        }
        return issues;
    }

    /**
     * R2 必填缺失 + R3 类型不符（行级）— 逐行调用。
     *
     * @return 行级 issue（R2/R3）与需置 null 的属性引用集合
     */
    public static RowResult checkRow(String entityCode,
                                     Set<PropertyDef> properties,
                                     List<MappingRef> mappingRefs,
                                     Map<String, Object> row) {
        List<Issue> issues = new ArrayList<>();
        Set<String> nullified = new LinkedHashSet<>();
        if (mappingRefs == null || mappingRefs.isEmpty()) {
            return new RowResult(issues, nullified);
        }
        List<PropertyDef> props = properties == null ? List.of() : new ArrayList<>(properties);
        for (MappingRef ref : mappingRefs) {
            if (ref == null || ref.ontologyRef() == null || ref.ontologyRef().isBlank()) {
                continue;
            }
            String refKey = ref.ontologyRef().trim();
            if (refKey.startsWith(RELATION_REF_PREFIX)) {
                continue;
            }
            String normRef = normalize(refKey);
            Object rawVal = row == null ? null : row.get(ref.dwColumn());
            for (PropertyDef p : props) {
                if (p == null || !normRef.equals(normalize(p.code()))) {
                    continue;
                }
                if (p.required() && isBlank(rawVal)) {
                    issues.add(new Issue(entityCode, CODE_MISSING_REQUIRED,
                            "必填属性 " + p.code() + "（dwColumn=" + ref.dwColumn() + "）缺失"));
                }
                if (!isBlank(rawVal) && !isTypeCompatible(String.valueOf(rawVal).trim(), p.type())) {
                    issues.add(new Issue(entityCode, CODE_TYPE_MISMATCH,
                            "属性 " + p.code() + "（类型 " + p.type() + "）值="
                                    + safe(String.valueOf(rawVal)) + " 类型不符，置 null"));
                    nullified.add(refKey);
                }
                break;
            }
        }
        return new RowResult(issues, nullified);
    }

    /** 行级校验结果。 */
    public record RowResult(List<Issue> issues, Set<String> nullifiedRefs) {
    }

    private static Set<String> propertyCodes(Set<PropertyDef> properties) {
        Set<String> out = new LinkedHashSet<>();
        if (properties != null) {
            for (PropertyDef p : properties) {
                if (p != null && p.code() != null) {
                    out.add(normalize(p.code()));
                }
            }
        }
        return out;
    }

    private static Set<String> relationCodes(Set<String> relations) {
        Set<String> out = new LinkedHashSet<>();
        if (relations != null) {
            for (String r : relations) {
                if (r != null && !r.isBlank()) {
                    out.add(normalize(r));
                }
            }
        }
        return out;
    }

    /**
     * 执行 C3 校验（纯函数）。等价于 {@link #checkUnknownAttrs} + 单行 {@link #checkRow}。
     *
     * @param entityCode      实体规范 code（用于 issue 标识）
     * @param properties      本体实体属性集合（可空 = 未知；空集合 → 仅 R1 空集亦触发为"无已知属性"）
     * @param relations       本体实体关系集合（引用以 {@code rel:xxx} 前缀；关系引用不清空、也不进 R1）
     * @param mappingRefs     字段映射引用（dwColumn ↔ ontologyRef）
     * @param row             DW 物理行（key = dwColumn 原大小写）
     * @return 校验结果（issues 按 R1 → R2 → R3 顺序；nullifiedRefs 为需置 null 的 ontologyRef 集合）
     */
    public static Result validate(String entityCode,
                                  Set<PropertyDef> properties,
                                  Set<String> relations,
                                  List<MappingRef> mappingRefs,
                                  Map<String, Object> row) {
        List<Issue> issues = new ArrayList<>();
        Set<String> nullified = new LinkedHashSet<>();
        if (mappingRefs == null || mappingRefs.isEmpty()) {
            return new Result(issues, nullified);
        }
        Set<String> knownProps = new LinkedHashSet<>();
        if (properties != null) {
            for (PropertyDef p : properties) {
                if (p != null && p.code() != null) {
                    knownProps.add(normalize(p.code()));
                }
            }
        }
        Set<String> knownRels = new LinkedHashSet<>();
        if (relations != null) {
            for (String r : relations) {
                if (r != null && !r.isBlank()) {
                    knownRels.add(normalize(r));
                }
            }
        }
        for (MappingRef ref : mappingRefs) {
            String ontologyRef = ref == null ? null : ref.ontologyRef();
            String dwCol = ref == null ? null : ref.dwColumn();
            if (ontologyRef == null || ontologyRef.isBlank()) {
                continue;
            }
            String refKey = ontologyRef.trim();
            if (refKey.startsWith(RELATION_REF_PREFIX)) {
                // 关系引用：只在关系集合非空时做 R1 类比判定；不影响 properties 处
                if (!knownRels.isEmpty() && !knownRels.contains(normalize(refKey))) {
                    issues.add(new Issue(entityCode, CODE_UNKNOWN_ATTR,
                            "关系引用 " + refKey + " 不在本体实体关系集合内"));
                }
                continue;
            }
            String normRef = normalize(refKey);
            // R1 未知属性
            if (!knownProps.isEmpty() && !knownProps.contains(normRef)) {
                issues.add(new Issue(entityCode, CODE_UNKNOWN_ATTR,
                        "映射引用 " + refKey + " 不在本体实体 " + entityCode + " 属性集合内"));
            }
            Object rawVal = row == null ? null : row.get(dwCol);
            // R2 必填缺失
            for (PropertyDef p : propertiesSafe(properties)) {
                if (p != null && p.required() && normRef.equals(normalize(p.code())) && isBlank(rawVal)) {
                    issues.add(new Issue(entityCode, CODE_MISSING_REQUIRED,
                            "必填属性 " + p.code() + "（dwColumn=" + dwCol + "）缺失"));
                }
            }
            // R3 类型不符
            PropertyDef matched = matchProperty(propertiesSafe(properties), normRef);
            if (matched != null && !isBlank(rawVal)) {
                if (!isTypeCompatible(String.valueOf(rawVal).trim(), matched.type())) {
                    issues.add(new Issue(entityCode, CODE_TYPE_MISMATCH,
                            "属性 " + matched.code() + "（类型 " + matched.type() + "）值 "
                                    + safe(refKey) + "=" + safe(String.valueOf(rawVal)) + " 类型不符，置 null"));
                    nullified.add(refKey);
                }
            }
        }
        return new Result(issues, nullified);
    }

    /**
     * 简单类型匹配（对齐本体属性 5 类基础类型声明：INTEGER/LONG/DOUBLE/DECIMAL/STRING/BOOLEAN/DATE
     * + 数值互容）。规范允许的类型判定基于"值为空→免校验"。
     */
    public static boolean isTypeCompatible(String rawValue, String declaredType) {
        if (declaredType == null || declaredType.isBlank()) {
            return true;
        }
        String t = declaredType.trim().toUpperCase(Locale.ROOT);
        String v = rawValue == null ? "" : rawValue.trim();
        if (v.isEmpty()) {
            return true;
        }
        switch (t) {
            case "INTEGER":
            case "INT":
            case "LONG":
                return isInteger(v);
            case "DOUBLE":
            case "FLOAT":
            case "DECIMAL":
                return isDecimal(v);
            case "BOOLEAN":
            case "BOOL":
                return isBoolean(v);
            case "DATE":
            case "DATETIME":
            case "TIMESTAMP":
                return isDateLike(v);
            default:
                // STRING / 未知 → 免校验（对齐本体契约默认宽进）
                return true;
        }
    }

    // ── 内部工具 ────────────────────────────────────────────────

    private static Set<PropertyDef> propertiesSafe(Set<PropertyDef> p) {
        return p == null ? Set.of() : p;
    }

    private static boolean isBlank(Object o) {
        return o == null || String.valueOf(o).trim().isEmpty();
    }

    private static String safe(String s) {
        return s == null ? "<null>" : s;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static PropertyDef matchProperty(Set<PropertyDef> props, String normRef) {
        for (PropertyDef p : props) {
            if (p != null && normRef.equals(normalize(p.code()))) {
                return p;
            }
        }
        return null;
    }

    private static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDecimal(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isBoolean(String s) {
        return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)
                || "1".equals(s) || "0".equals(s);
    }

    private static boolean isDateLike(String s) {
        // 简化：YYYY-MM-DD / YYYY-MM-DDTHH:MM(:SS)? / Unix ts
        if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
            return true;
        }
        return isInteger(s);
    }

    /** 类型不匹配时的属性引用存储格式（对 map 引用与 dw 列双重索引，便于上层剥离）。 */
    public record PropertyBias(String reference, String dwColumn) {
    }

    /** C3 关闭时的报告标记键（写入 report 级别）。 */
    public static final String REPORT_FLAG_C3_SKIPPED = "c3Skipped";

    /** 弱耦合 Map 工厂：便于上层以 {@link LinkedHashMap} 构建 propertiesJson。 */
    public static Map<String, Object> nullify(Map<String, Object> properties,
                                              List<MappingRef> mappingRefs,
                                              Set<String> nullifiedRefs) {
        if (properties == null || nullifiedRefs == null || nullifiedRefs.isEmpty()) {
            return properties;
        }
        Map<String, Object> out = new LinkedHashMap<>(properties);
        for (MappingRef ref : mappingRefs) {
            if (ref != null && ref.ontologyRef() != null && nullifiedRefs.contains(ref.ontologyRef())) {
                out.put(ref.ontologyRef(), null);
            }
        }
        return out;
    }
}
