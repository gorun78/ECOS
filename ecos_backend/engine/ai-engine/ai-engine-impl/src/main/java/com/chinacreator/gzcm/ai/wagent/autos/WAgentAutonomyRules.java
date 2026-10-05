package com.chinacreator.gzcm.ai.wagent.autos;

/**
 * 分册10 F10-21/F10-22 · ADR-16 自治边界规则单源（"W 语义 ≠ Agent" 落地常量，
 * 解 WC-01/02/06 → R-31/R-32/R-37；与 {@code WAgentEngineCatalog} +
 * {@code ArchitectureTest} 同源的属主/禁包前缀约束）。
 *
 * <p>本类<b>仅承载纯常量与规则枚举</b>，不含任何可执行能力实现：
 * W 域的"决策底座"归属 ai-engine 编排域，认知侧 {@code ecos_decision*} 定性为
 * Decision Basis（只读引用），编排流程属主恒为 ai-engine（gold I 侧只读）。</p>
 */
public final class WAgentAutonomyRules {

    private WAgentAutonomyRules() {}

    /** ADR-16 核心开关：W 域永不 alias 到"通用 Agent"语义（恒真，供 ArchUnit 断言常量不可篡改）。 */
    public static final boolean W_DOMAIN_NEVER_ALIASED_TO_AGENT = true;

    /**
     * ArchUnit 守护用禁包前缀：wagent 编排域<b>不得</b> import 引擎 impl / JDBC 实现细节，
     * 也永不直连 JDBC Driver（Driver 收敛 runtime-access，铁律 §2.1）。
     * 供 gateway 侧 {@code ArchitectureTest.wagentForbiddenPackages} 逐条断言。
     */
    public static final String[] FORBIDDEN_PACKAGE_PREFIXES = {
            "com.chinacreator.gzcm.engine..impl..",
            "com.chinacreator.gzcm.engine..jdbc..",
            "javax.sql",
            "java.sql.Driver"
    };

    /** ADR-16 三条自治边界规则（分层守护点，供编排控制器逐条校验）。 */
    public enum Rule {
        /** W 不构成独立 "Agent"：W 是编排域对象，禁止以 Agent 名义对外暴露独立身份。 */
        W_NOT_AGENT,
        /** 编排流程属主恒为 ai-engine（aiming:18084），禁在 service/workspace 侧重复实现编排。 */
        ORCHESTRATION_OWNED_BY_AI_ENGINE,
        /** 决策依据（Decision Basis）= 认知侧 ecos_decision*，只读引用，wagent 不改表不改 API。 */
        DECISION_BASIS_READ_ONLY_ONT
    }
}
