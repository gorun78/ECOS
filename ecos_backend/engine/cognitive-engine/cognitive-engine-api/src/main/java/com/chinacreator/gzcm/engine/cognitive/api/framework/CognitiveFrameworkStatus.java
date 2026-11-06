package com.chinacreator.gzcm.engine.cognitive.api.framework;

/**
 * 认知框架治理状态单源（详细设计-认知引擎 v1.0 §5.1）。
 *
 * <p>词表：{@code DRAFT → VALIDATING → PUBLISHED → DEPRECATED/DISABLED}。
 * 已发布版本不可原地修改（§5.1 / CE-03）；修复必须注册并发布新版本。</p>
 */
public enum CognitiveFrameworkStatus {
    DRAFT,
    VALIDATING,
    PUBLISHED,
    DEPRECATED,
    DISABLED;

    public static CognitiveFrameworkStatus fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 仅 PUBLISHED 可用于策略选择（硬约束，§6.2 第一阶段）。 */
    public boolean selectable() {
        return this == PUBLISHED;
    }

    /** 终态：不可再迁移。 */
    public boolean terminal() {
        return this == DISABLED;
    }
}
