package com.chinacreator.gzcm.common.error;

/**
 * 场景装配族（第11篇 ACT/SCN/SBX/INT：scenario + action + sandbox + blueprint）统一错误码常量 —
 * 《ECOS详细设计说明书-从零实施版-2026-10-12》附录C（10401~10409 行）第11篇冻结表单源。
 *
 * <p>#146am（差距分析台账附录C 第 7 条「第11篇 ECOS-ACT/SCN/SBX/INT 前缀族 0 命中」
 * 按《从零实施版》口径消案）。冻结表第11篇共 13 枚语义位（10401~10410 行，
 * 400~503 段），逐枚定性如下：
 * <b>① 8 枚归本族属主</b>：400 ACTION_FIELDS_MISSING（10401 行，归因坐标即
 * {@code ECOS-ACT} 五必填段）、404 SCENARIO_NOT_FOUND（10404 行）、409
 * ISLAND_BINDING（10405 行）、409 ILLEGAL_TRANSITION／TERMINAL_STATE／
 * NO_FORMAL_RUN／INCOMPLETE_SCENARIO（10406 行状态机四枚）、409
 * LAYOUT_CONFLICT（10407 行）——全部有生产出口，字面量散落 workspace-impl
 * <b>11 个载体文件共 17 处 {@code super(...)}／工厂调用</b>
 * （ACTION_FIELDS_MISSING 3 处含 {@code WorkspaceExceptionHandler} 出口腿；
 * SCENARIO_NOT_FOUND／ISLAND_BINDING／LAYOUT_CONFLICT 各双包孪生 2 处；
 * 状态机四枚 10 处：scenario 包 4 枚工厂字面量＋blueprint 面 1＋2 枚），
 * 本批改指本类 {@code CODE_*} 常量、字面值逐字节不变、wire 零变更。
 * <b>② 2 枚跨片共用托管位本类不立常</b>：403 PERMISSION_DENIED／503
 * SECURITY_UNAVAILABLE（10403／10409 行）同归 ECOS-SEC 域主，生产出口在
 * security 裁决面，逐枚搬流会跨族引用违铁律，归属以冻结表原文为准。
 * <b>③ COGNITIVE_UNAVAILABLE（10408 行）与 SANDBOX_REF_FORBIDDEN
 * （10402 行，ECOS-SBX 主表）同型 FC 先例</b>——语义位载体均已在
 * workspace-impl 场景／演练层落地，属主落 {@link FCScenarioErrorCode}
 * （{@code CODE_COGNITIVE_UNAVAILABLE}／{@code CODE_SANDBOX_REF_FORBIDDEN}），
 * 本类不重复立常，防同值双属主。</p>
 *
 * <p><b>两处既有口径的勘正/追认（治理时点 2026-10-30 复核）</b>：
 * 其一，{@code FCScenarioErrorCode} javadoc 谓「10422 行 ECOS-INT-500（INT）
 * 全仓归 {@code SystemErrorCode}」——名实不符，勘正为：500 INTERNAL 的既有
 * 平台承载面取 {@code ECOS-SYS-500} 前缀（gateway 兜底 3 腿＋workspace 面
 * {@code WorkspaceExceptionHandler} 3 腿 {@code build(500, "INTERNAL", …)}），
 * 冻结表 500 段以实存 wire 面为准，本类不另建 INT 段常量；
 * 其二，同 javadoc 谓 ACTION_FIELDS_MISSING「本类不立常（归 ECOS-ACT 域主）」
 * 仅对第12篇 10414 行成立（FC 段不重复立）；第11篇 10401 行 ACT 段本族
 * 属主即本批落位，见 {@link #CODE_ACTION_FIELDS_MISSING}。</p>
 *
 * <p>治理时点（2026-10-30）main 面实况取证＝全仓带引号扫描口径：
 * {@code "ECOS-ACT-}／{@code "ECOS-SCN-}／{@code "ECOS-SBX-}／
 * {@code "ECOS-INT-} 带引号机读码 <b>0 枚</b>（含 test 面）——冻结表第11篇
 * 各码位的归因坐标以上列 {@code ECOS-xx} 域锚为准，码值即语义位本身（如
 * {@code 400 ACTION_FIELDS_MISSING}），不存在 {@code ECOS-<域>-<三位>} 形态
 * 的机读码位 ⇒ 本类不建机读码常（建即死码，DG-9/X-43 红线；「为抬活度
 * 建伪出口」先例承流）。本类只收 8 枚属主语义位常量。</p>
 *
 * <p>本类只含常量，无任何业务 import（common-api enforcer 白名单内，与
 * {@link FCScenarioErrorCode}／{@link OntologyErrorCode}／
 * {@link SecurityErrorCode} 同形）。</p>
 */
public final class ScenarioErrorCode {

    private ScenarioErrorCode() {
        throw new UnsupportedOperationException("常量类，禁止实例化");
    }

    // ==================== 第11篇属主语义位 (8 枚有生产出口, 17 处载体字面量) ====================

    /**
     * 400 语义位——动作五必填 / run body 缺失（10401 行归因坐标 ECOS-ACT）。
     * 生产出口＝workspace-impl {@code scenario/ActionValidationException} 与
     * {@code exception/ActionValidationException}（双包孪生）＋
     * {@code exception/WorkspaceExceptionHandler#handleValidation}（3 处）。
     */
    public static final String CODE_ACTION_FIELDS_MISSING = "ACTION_FIELDS_MISSING";

    /**
     * 404 语义位——场景不存在 / 已软删（10404 行）。
     * 生产出口＝workspace-impl {@code scenario/ScenarioNotFoundException} 与
     * {@code exception/ScenarioNotFoundException}（双包孪生 2 处）。
     */
    public static final String CODE_SCENARIO_NOT_FOUND = "SCENARIO_NOT_FOUND";

    /**
     * 409 语义位——孤岛绑定阻断激活（10405 行）。
     * 生产出口＝workspace-impl {@code scenario/IslandBindingException} 与
     * {@code exception/IslandBindingException}（双包孪生 2 处）。
     */
    public static final String CODE_ISLAND_BINDING = "ISLAND_BINDING";

    /**
     * 409 语义位——状态机迁移表违例（10406 行四枚之一）。
     * 生产出口＝workspace-impl {@code scenario/IllegalScenarioTransitionException#illegal}
     * ＋ {@code blueprint/IllegalBlueprintTransitionException#illegal}＋
     * {@code blueprint/ScenarioBlueprintStateMachine} 两枚直投（4 处）。
     */
    public static final String CODE_ILLEGAL_TRANSITION = "ILLEGAL_TRANSITION";

    /**
     * 409 语义位——终态不可回退（10406 行四枚之一）。
     * 生产出口＝workspace-impl {@code scenario/IllegalScenarioTransitionException#terminal}
     * ＋ {@code blueprint/ScenarioBlueprintStateMachine} 直投（2 处）。
     */
    public static final String CODE_TERMINAL_STATE = "TERMINAL_STATE";

    /**
     * 409 语义位——COMPLETED 需至少一条 FORMAL 运行（10406 行四枚之一）。
     * 生产出口＝workspace-impl {@code scenario/IllegalScenarioTransitionException#noFormalRun}。
     */
    public static final String CODE_NO_FORMAL_RUN = "NO_FORMAL_RUN";

    /**
     * 409 语义位——场景完整度判定为空禁激活（10406 行四枚之一）。
     * 生产出口＝workspace-impl {@code scenario/IllegalScenarioTransitionException#incomplete}。
     * （{@code exception/IllegalScenarioTransitionException} 孪生载体走
     * {@code Reason.name()} 枚举单源，字面量不重复外露，天然免改。）
     */
    public static final String CODE_INCOMPLETE_SCENARIO = "INCOMPLETE_SCENARIO";

    /**
     * 409 语义位——沙盘布局乐观锁冲突（10407 行）。
     * 生产出口＝workspace-impl {@code scenario/OptimisticLockConflictException} 与
     * {@code exception/OptimisticLockConflictException}（双包孪生 2 处）。
     */
    public static final String CODE_LAYOUT_CONFLICT = "LAYOUT_CONFLICT";

    // ==================== 跨片共用托管位（本类不立常）＋ 既有单源（不重复立） ====================

    /*
     * 403 PERMISSION_DENIED（10403 行）：属主 ECOS-SEC，生产出口在 security
     * 裁决面（denier；workspace {@code ForbiddenOperationException} 三腿
     * 载体沿用 SEC 属主口径），不搬流。
     *
     * 503 SECURITY_UNAVAILABLE（10409 行）：属主 ECOS-SEC，生产出口已走
     * {@code ECOS-SEC-501} 形态（RlsExecutionContract 单源），禁 fail-open 口径
     * 以冻结表为准，本类不立常。
     *
     * 503 COGNITIVE_UNAVAILABLE（10408 行，归因坐标 ECOS-COG）：workspace-impl
     * 双包孪生载体已指 {@link FCScenarioErrorCode#CODE_COGNITIVE_UNAVAILABLE}
     * （#146ak），认知域自有 {@code CognitiveErrorCode} 503 段是另一口径码件，不共用。
     *
     * 400 SANDBOX_REF_FORBIDDEN（10402 行，归因坐标 ECOS-SBX）：workspace-impl
     * 双包孪生载体已指 {@link FCScenarioErrorCode#CODE_SANDBOX_REF_FORBIDDEN}
     * （#146ak，FC 场景层同型承载），本类不重复立常。
     *
     * 500 INTERNAL（10410 行，归因坐标 ECOS-INT，原文不入体）：既有平台承载
     * 面取 ECOS-SYS-500 前缀机读码（gateway 兜底 3 腿）＋ workspace 面 3 腿
     * 信封语义位 "INTERNAL"，均为在案 wire 面，本类不另建 INT 段常量。
     * 上述五枚出口真建新形态时先在本类增常＋同批扩零容差白名单，禁另造同值副本。
     */
}
