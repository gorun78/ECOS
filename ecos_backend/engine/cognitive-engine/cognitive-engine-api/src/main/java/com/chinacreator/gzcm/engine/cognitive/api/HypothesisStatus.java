package com.chinacreator.gzcm.engine.cognitive.api;

import java.util.Optional;

/**
 * 假设状态终态枚举（R-15 ① 定版，详细设计-05 F05-05 / D.2）。
 *
 * <p>读侧兼容（只读别名映射，禁写入旧值）：
 * {@code VALID → BELIEVED}，{@code INVALIDATED → REFUTED}，{@code ARCHIVED → REFUTED}。
 * {@link #fromDbValue} 用于把 DB 中历史 VALID/INVALIDATED/ARCHIVED 值读进来。
 * 存量行的 {@code old_status} 审计列由迁移 V189 挪入。</p>
 */
public enum HypothesisStatus {

    PROPOSED, EVIDENCED, BELIEVED, REFUTED;

    /** 契约文档 §假设状态枚举终态定版标注（F05-05 / Cg-1）。 */
    public static final String CONTRACT_DOC_SECTION =
            "docs/30-设计/详细设计-05-认知域-cognitive-engine与aiming-2026-09-29.md/F05-05";

    /** 读侧兼容映射；仅四个终态可写（{@link #canWrite}）。 */
    public static HypothesisStatus fromDbValue(String raw) {
        if (raw == null) return null;
        switch (raw.trim()) {
            case "PROPOSED":   return PROPOSED;
            case "EVIDENCED":  return EVIDENCED;
            case "BELIEVED":   return BELIEVED;
            case "REFUTED":    return REFUTED;
            // R-15 只读别名映射（存量 VALID/INVALIDATED/ARCHIVED 7 行；禁止整数字块新写入）
            case "VALID":       return BELIEVED;
            case "INVALIDATED": return REFUTED;
            case "ARCHIVED":    return REFUTED;
            default:            return null;
        }
    }

    /** 只有四个终态可写；存量 VALID/INVALIDATED/ARCHIVED 只读不写。 */
    public static boolean canWrite(String raw) {
        if (raw == null) return false;
        for (HypothesisStatus s : values()) {
            if (s.name().equals(raw)) return true;
        }
        return false;
    }

    /** 是否为只读别名（存量旧词，禁新写入但需读侧容错）。 */
    public static boolean isLegacyAlias(String raw) {
        return "VALID".equals(raw) || "INVALIDATED".equals(raw) || "ARCHIVED".equals(raw);
    }

    public static Optional<HypothesisStatus> canonical(String raw) {
        HypothesisStatus s = fromDbValue(raw);
        return s == null ? Optional.empty() : Optional.of(s);
    }
}
