package com.chinacreator.gzcm.engine.kb.support;

/**
 * F03-05 rule 2 / PRD-03 §3.2-2 — {@code materialized} 裁度语义（Q2_SKIP 口径）。
 *
 * <p>固化（以表驱动单测 {@code KbMappingSkipTest} 锁定）：</p>
 * <ul>
 *   <li>{@code null} / {@code ""} / {@code "true"} / {@code "1"} → <b>true</b>（加载/实例化）；</li>
 *   <li>其它取值（如 {@code "false"} / {@code "0"} / {@code "skip"} / {@code "FALSE"} / {@code "no"}）
 *       → <b>false</b>（显式跳过实例化）。</li>
 * </ul>
 *
 * <p>纯函数、无副作用，供 kb 图谱实例抽取（{@code KbEntityInstanceExtractionService}）与
 * 后续 mapper 复用，语义与 PRD-03 冻结契约
 * {@code docs/30-设计/contracts/ontology-entity-mappings.schema.json} 的 {@code materialized} 字段一致。</p>
 */
public final class MaterializedSemantics {

    private MaterializedSemantics() {
    }

    /**
     * @param value 契约里 {@code materialized} 字段的原始字符串（可能为 null）
     * @return true = 物化/实例化；false = 显式 skip
     */
    public static boolean shouldMaterialize(String value) {
        if (value == null) {
            return true;
        }
        String raw = value.trim();
        return raw.isEmpty()
                || "true".equalsIgnoreCase(raw)
                || "1".equals(raw);
    }
}
