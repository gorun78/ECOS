package com.chinacreator.gzcm.engine.ai.capability;

/**
 * 能力目录读端 VO（§13 {@code GET /api/v1/ai/capabilities} 行形态，只读快照）。
 *
 * <p>三要件（contractVersion/evidenceRequired/automationLevel）为<b>必载</b>字段——
 * 缺一即违反目录冻结契约（AiCapabilityCatalogTest 守）；这里用不可空语义承载
 * （构造器兜底从 {@link AiCapabilityId} 冻结值取，禁 null 放行）。</p>
 */
public record CapabilityReadVO(String capabilityId, String family, String contractVersion,
                               boolean evidenceRequired, int automationLevel, String costUnit,
                               String toolset) {

    /** 从能力枚举 + 工具集命中标签生成（缺一构造即抛，同目录三要件纪律）。 */
    public static CapabilityReadVO from(AiCapabilityId id, String toolset) {
        return new CapabilityReadVO(
                id.name(),
                id.family(),
                id.contractVersion(),
                id.evidenceRequired(),
                id.automationLevelDefault(),
                id.costUnit(),
                toolset);
    }

    public CapabilityReadVO {
        if (capabilityId == null || capabilityId.isBlank()
                || !AiCapabilityId.all().stream().anyMatch(c -> c.name().equals(capabilityId))) {
            throw new IllegalArgumentException("能力目录 VO 携带未知 capabilityId（词表单源 AiCapabilityId）: " + capabilityId);
        }
        if (contractVersion == null || contractVersion.isBlank()) {
            throw new IllegalArgumentException("能力目录 VO 缺 contractVersion（三要件之一，禁缺省放行）");
        }
        if (costUnit == null || costUnit.isBlank()) {
            throw new IllegalArgumentException("能力目录 VO 缺 costUnit（§10.6 计量口径）");
        }
    }
}
