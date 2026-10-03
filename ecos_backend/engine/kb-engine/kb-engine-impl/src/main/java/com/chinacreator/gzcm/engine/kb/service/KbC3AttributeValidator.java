package com.chinacreator.gzcm.engine.kb.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * C3 属性完整性校验器（F04-05 / REQ-KB-01，从零实现）— 纯算法、无 I/O，可单测。
 *
 * <p>在抽取主流程 C1 类型存在性之后、节点 upsert 之前运行，对「本体属性引用 → 行值」的
 * 候选属性集做三规则校验（对齐既有 C1/C2/C4 的「全 WARN 不阻断、issue 入 report.issues[]」口径）：
 * <ul>
 *   <li><b>R1 未知属性</b>（{@code C3_UNKNOWN_ATTR}）：候选属性本体引用不在实体声明属性集内 →
 *       WARN，<b>仍照常写入</b>（不删除）；</li>
 *   <li><b>R2 必填缺失</b>（{@code C3_MISSING_REQUIRED}）：声明为必填的属性在行中缺失/空白 →
 *       WARN，节点仍写入；</li>
 *   <li><b>R3 类型不符</b>（{@code C3_TYPE_MISMATCH}）：属性值无法按声明类型解析 → WARN，
 *       且<b>置 null 不写入</b>（清洗后属性集里该字段为 null）。</li>
 * </ul>
 *
 * <p><b>判据来源（分册 03 G5 同期）</b>：实体属性定义（{@link AttrDef}）由分册 03 的抽取主流程实体定义
 * 供给；在其就绪前生产侧判据集为空 → 无属性可校验 → 不产生任何 C3 issue（<b>不等同 c3Skipped</b>）。
 * {@code c3_enabled=false} 才由服务层将 report 标 {@code c3Skipped=true}（禁静默关闭）。
 *
 * @author ECOS KB Team
 */
public final class KbC3AttributeValidator {

    /** 实体属性类型（C3 R3 类型判据）。 */
    public enum AttrType {
        TEXT, INTEGER, DECIMAL, BOOLEAN, DATE
    }

    /** 单个实体属性的 C3 判据（本体侧属性引用 + 是否必填 + 声明类型）。 */
    public record AttrDef(String code, boolean required, AttrType type) {
    }

    /** 一条 C3 WARN 明细（对齐 report.issues[] 的 (entityCode, code, message) 三元组）。 */
    public record Issue(String code, String attrCode, String message) {
    }

    /** C3 校验结果：清洗后属性集（R3 命中的字段已置 null）+ 本次产生的 issue 明细。 */
    public record Result(Map<String, Object> cleanedProperties, List<Issue> issues) {
    }

    private KbC3AttributeValidator() {
    }

    /**
     * 对一个实体的候选属性集做三条 C3 校验。
     *
     * @param entityCode  实体 code（用于 issue 追溯）
     * @param properties  候选属性集（本体属性引用 → 行值）；null 视为空集
     * @param attrDefs    实体声明的属性判据集（分册 03 供给；空 → 无判据，返回原样、零 issue）
     * @return 清洗后属性集 + issue 明细
     */
    public static Result validate(String entityCode, Map<String, Object> properties, List<AttrDef> attrDefs) {
        Map<String, Object> source = properties == null ? new LinkedHashMap<>() : properties;
        Map<String, Object> cleaned = new LinkedHashMap<>(source);
        List<Issue> issues = new ArrayList<>();

        if (attrDefs == null || attrDefs.isEmpty()) {
            return new Result(cleaned, issues);
        }

        Set<String> knownCodes = new LinkedHashSet<>();
        Map<String, AttrDef> defByCode = new LinkedHashMap<>();
        for (AttrDef def : attrDefs) {
            if (def.code() == null || def.code().isBlank()) {
                continue;
            }
            knownCodes.add(def.code().toUpperCase(Locale.ROOT));
            defByCode.put(def.code().toUpperCase(Locale.ROOT), def);
        }

        // R1 未知属性 WARN（仍照常写入）：候选属性本体引用不在声明集内
        for (String attr : source.keySet()) {
            if (attr == null || attr.isBlank()) {
                continue;
            }
            if (!knownCodes.contains(attr.toUpperCase(Locale.ROOT))) {
                issues.add(new Issue("C3_UNKNOWN_ATTR", attr,
                        "属性 '" + attr + "' 不在实体 " + entityCode + " 声明属性集内（R1，仍写入）"));
            }
        }

        // R2 必填缺失 WARN（节点仍写入）：声明必填但行内缺失/空白
        for (AttrDef def : attrDefs) {
            if (!def.required()) {
                continue;
            }
            if (isBlank(source.get(def.code()))) {
                issues.add(new Issue("C3_MISSING_REQUIRED", def.code(),
                        "必填属性 '" + def.code() + "' 缺失（R2）"));
            }
        }

        // R3 类型不符 WARN 且置 null 不写入：值存在但无法按声明类型解析
        for (Map.Entry<String, Object> entry : cleaned.entrySet()) {
            String attr = entry.getKey();
            if (attr == null || attr.isBlank() || isBlank(entry.getValue())) {
                continue;
            }
            AttrDef def = defByCode.get(attr.toUpperCase(Locale.ROOT));
            if (def == null || def.type() == null || def.type() == AttrType.TEXT) {
                continue;
            }
            if (!matchesType(String.valueOf(entry.getValue()).trim(), def.type())) {
                cleaned.put(attr, null);
                issues.add(new Issue("C3_TYPE_MISMATCH", attr,
                        "属性 '" + attr + "' 值 '" + entry.getValue() + "' 与声明类型 " + def.type()
                                + " 不符（R3，置 null 不写入）"));
            }
        }

        return new Result(cleaned, issues);
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }

    /** 声明类型判据（宽松解析；TEXT 恒放行不在本方法处理）。 */
    private static boolean matchesType(String value, AttrType type) {
        switch (type) {
            case INTEGER:
                try {
                    Long.parseLong(value);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            case DECIMAL:
                try {
                    new java.math.BigDecimal(value);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            case BOOLEAN:
                return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                        || "1".equals(value) || "0".equals(value);
            case DATE:
                return parsesAsDate(value);
            case TEXT:
            default:
                return true;
        }
    }

    private static boolean parsesAsDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
            // fall through to datetime
        }
        try {
            LocalDateTime.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }
}
