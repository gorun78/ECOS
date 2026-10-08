package com.chinacreator.gzcm.engine.cognitive.api.mind;

/**
 * Mind 能力掩码枚举（PRD-05 §2.2 唯一权威，详细设计-05 F05-11 / Cg-8 定版）。
 *
 * <p>D.2 冻结：5 值不变；扩展需先在 {@link MindCapabilityMask#CONTRACT_DOC_SECTION} 指向的
 * 契约文档 v2.0 修订并在 {@code MindCapabilityMaskTest#enumMatchesContractDocTable} 通过。
 * 序列化仅经 {@link MindCapabilityMaskCodec}，读库列 (TEXT) 或写 DDL 一律不直接 new。</p>
 */
public enum MindCapabilityMask {

    /** 探测场景可用 Mind（E1）。 */
    DETECT,
    /** 运行评价（E2）。 */
    FORECAST,
    /** 情景模拟（REQ-COG-03 确定性重算）。 */
    SIMULATE,
    /** 规划/诊断（REQ-COG-02）。 */
    PLAN,
    /** 复盘/审阅。 */
    REVIEW;

    /** 契约文档 §2.2 冻结节的定位（供测试读取做漂移校验，F05-11 验收项）。 */
    public static final String CONTRACT_DOC_SECTION =
            "docs/30-设计/详细设计-05-认知域-cognitive-engine与aiming-2026-09-29.md/D_2_§2.2";

    public static MindCapabilityMask fromName(String name) {
        if (name == null) return null;
        for (MindCapabilityMask m : values()) {
            if (m.name().equals(name)) return m;
        }
        return null;
    }
}
