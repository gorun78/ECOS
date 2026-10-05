package com.chinacreator.gzcm.ai.wagent.tool;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolCategory;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolStatus;

import java.util.Objects;

/**
 * 分册10 F10-17 · Tool Contract（工具能力契约，六态生命周期由 {@link ToolContractRegistry} 管）。
 *
 * <p>immutable record：name+version 构成契约主键（PK）；category=COMMIT ⇒
 * {@code rollbackPlanText} 强制非空（DB CHECK + 应用双校，铁律 pre-commit 回滚通道）。
 * 成本/金额类字段一律 String（NUMERIC(p,s) 语义，禁 double/float）。</p>
 */
public record ToolContract(
        String name, String version, String engine, ToolCategory category, String sideEffect,
        int minLevel, String requiredPermission, String inputSchemaJson, String outputSchemaJson,
        int timeoutMs, boolean isIdempotent, String costClass, boolean isEvidenceOutput,
        String dataClassificationText, String rollbackPlanText,
        String endpointKind, String endpointUrl, String endpointMethod,
        String owner, String slaText, ToolStatus status, String deprecatedBy, String toolset,
        String summary, String disclosure, String capabilityDepsText) {

    /** 默认覆盖：PK（name/version/engine）非空 + COMMIT 必有 rollbackPlan；否则抛 IAE。 */
    public static ToolContract validateDefaults(ToolContract tc) {
        Objects.requireNonNull(tc, "tool contract null");
        if (tc.name() == null || tc.name().isBlank()) throw new IllegalArgumentException("ToolContract.name 缺失 (PK)");
        if (tc.version() == null || tc.version().isBlank()) throw new IllegalArgumentException("ToolContract.version 缺失 (PK)");
        if (tc.engine() == null || tc.engine().isBlank()) throw new IllegalArgumentException("ToolContract.engine 缺失");
        if (tc.category() == null) throw new IllegalArgumentException("ToolContract.category null");
        if (tc.status() == null) throw new IllegalArgumentException("ToolContract.status null");
        if (tc.category() == ToolCategory.COMMIT && (tc.rollbackPlanText() == null || tc.rollbackPlanText().isBlank())) {
            throw new IllegalArgumentException("COMMIT 工具 " + tc.name() + " 必须有 rollbackPlanText（pre-commit 回滚通道）");
        }
        if (tc.minLevel() < 0 || tc.minLevel() > 3) throw new IllegalArgumentException("minLevel 越界 " + tc.minLevel());
        if (tc.timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs 必须 >0");
        return tc;
    }
}
