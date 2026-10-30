package com.chinacreator.gzcm.common.error;

/**
 * 预测与场景域（第12篇 FC：forecast + scenario）统一错误码常量 —
 * 《ECOS详细设计说明书-从零实施版-2026-10-12》附录C（10411~10421 行）第12篇冻结表单源。
 *
 * <p>#146ak（差距分析台账附录C 第 8 条「第12篇 ECOS-FC-* 全族 0 命中」按《从零实施版》
 * 口径消案）。附录C 冻结段（含 10402 行 第11篇 400 SANDBOX_REF_FORBIDDEN 段——
 * 该枚同受 FC 场景层 workspace-impl 承载；10411~10421 行第12篇 FC 段）共
 * <b>5 枚机读码位</b>（ECOS-FC-400/403/404/409/503）＋ <b>17 枚语义位</b>；
 * 文末 10422 行 ECOS-INT-500 INTERNAL 属 INT 域，全仓归
 * {@code SystemErrorCode}，不入本族。17 枚语义位中 3 枚为跨片共用托管位
 * （403 PERMISSION_DENIED／503 SECURITY_UNAVAILABLE 同归 ECOS-SEC 域主，
 * 400 ACTION_FIELDS_MISSING 归 ECOS-ACT 域主）——本类 <b>不立常</b>，
 * 逐枚搬流会跨族引用违铁律，归属以冻结表原文为准。余 14 枚本质属 FC 域，
 * 分三段处置：<b>① 7 枚</b> 有生产出口（400 CALIBER_DSL_INVALID 双构造；
 * 403 SANDBOX_REF_FORBIDDEN 双包孪生；404 FORECAST_RUN_NOT_FOUND；
 * 404 CALIBER_NOT_FOUND；409 CALIBER_STATE_PRECONDITION；
 * 503 COGNITIVE_UNAVAILABLE 双包孪生；503 ONTOLOGY_UNAVAILABLE），字面量
 * 散落 workspace-impl <b>9 个载体文件共 10 处 super(...) 调用</b>
 * （CALIBER_DSL_INVALID 1×2、COGN／SANDBOX 各双包孪生 2×1、其余 4 枚
 * 各 ×1），本批改指本类 {@code CODE_*} 常量、字面值逐字节不变、wire
 * 零变更；<b>② 7 枚</b> 无出口（400 FORECAST_SCOPE_INVALID／400
 * OVERRIDE_FIELD_FORBIDDEN／404
 * DETAIL_NOT_FOUND／409 FORECAST_DQ_BLOCK／409 RUN_IMMUTABLE／503
 * DATA_ENGINE_UNAVAILABLE／503 RUNTIME_ACCESS_UNAVAILABLE）由类末尾
 * 注释块逐枚定性：<b>禁</b> 建死码常／伪出口，出口真建时先在本类增常＋
 * 同批扩零容差白名单；<b>③ 1 枚</b> 偏名（409 PERIOD_ALREADY_CLOSED
 * 冻结表真词，生产出口实值 {@code PERIOD_CLOSED} 409 alias 归 data 域
 * 关账强规，既有 wire 面偏名/字面值差异）归台账附录C 第 8 条订正注，
 * 本批既不改值也不另建冻结表真词常量。</p>
 *
 * <p>治理时点（2026-10-30）main 面实况取证＝全仓带引号扫描口径：
 * {@code "ECOS-FC-} 带引号机读码 <b>0 枚</b>——信封真身是
 * {@code WorkspaceDomainException#getCode()} 承载的语义 code，
 * 前端与审计按该键分派，机读码族前缀在信封面没有生产锚（补锚＝API
 * 变更，铁律 #10 不因抬活度解锁；针对「为抬活度建伪出口」死码红线
 * DG-9/X-43 承流面先例，5 枚机读码码位只登记字面量单点，禁建伪出口）。
 * 本批因此两折收口：① 5 枚机读码首落本类（治字面量单点＋防未来伪出口）；
 * ② 7 枚有生产出口语义位字面量单源（治散落）。§2.1 计数维持不翻转，
 * 跨片共用托管位、跨域搬流、wire 变更一概不动。</p>
 *
 * <p>本类只含常量，无任何业务 import（common-api enforcer 白名单内，与
 * {@link RateLimitErrorCode}／ {@link AuthErrorCode}／{@link SecurityErrorCode}
 * 同形）。</p>
 */
public final class FCScenarioErrorCode {

    private FCScenarioErrorCode() {
        throw new UnsupportedOperationException("常量类，禁止实例化");
    }

    // ==================== ECOS-FC 机读码（冻结表 5 枚码位） ====================

    /**
     * 400 机读码——参数非法（口径 DSL 违例／五必填缺失共用码位）。
     * 生产出口现状：无独立族机读码，三腿校验器走
     * {@code BusinessException(400, "FC-RUN-400: …")}。偏名按台账 2026-10-30
     * 订正注在案，本批不改字面量。
     */
    public static final String ECOS_FC_400 = "ECOS-FC-400";

    /**
     * 403 机读码——裁决 DENY 或默认拒绝（冻结 10415 行）。
     * 语义位口径＝403 属主族（SEC 域）PERMISSION_DENIED，本族不另立。
     * 生产出口现状：无独立族机读码，生产出口维持 SEC 属主口径，本批不搬流。
     */
    public static final String ECOS_FC_403 = "ECOS-FC-403";

    /** 404 机读码——预测 run／口径／明细不存在（语义位 404 段）。 */
    public static final String ECOS_FC_404 = "ECOS-FC-404";

    /** 409 机读码——缺格拦截／终态只读／口径状态机前置／关账期写拒绝（语义位 409 段）。 */
    public static final String ECOS_FC_409 = "ECOS-FC-409";

    /** 503 机读码——离线域引擎不可用（冻结 10421 行五签名）。 */
    public static final String ECOS_FC_503 = "ECOS-FC-503";

    // ==================== FC 域语义位 (7 枚有生产出口, 10 处载体字面量) ====================

    /**
     * 400 语义位——口径公式 DSL 四关任一违例。
     * 生产出口＝workspace-impl {@code exception/CaliberFormulaInvalidException}
     * 双构造（本批改指本常量，字面值不变，共 2 处出口）。
     */
    public static final String CODE_CALIBER_DSL_INVALID = "CALIBER_DSL_INVALID";

    /**
     * 403 语义位——正式运行禁止引用演练产物作基线（F07-09；冻结 10402 行
     * ECOS-SBX 主表，FC 域于 400 段复用同值码位）。
     * 生产出口＝workspace-impl {@code scenario/SandboxReferenceForbiddenException}
     * 与 {@code exception/SandboxReferenceForbiddenException}（双包孪生 2 处同值，
     * 本批孪生对同指本常量）。
     */
    public static final String CODE_SANDBOX_REF_FORBIDDEN = "SANDBOX_REF_FORBIDDEN";

    /**
     * 404 语义位——预测 run 不存在（H-5）。
     * 生产出口＝workspace-impl {@code forecast/ForecastRunNotFoundException}。
     */
    public static final String CODE_FORECAST_RUN_NOT_FOUND = "FORECAST_RUN_NOT_FOUND";

    /**
     * 404 语义位——口径不存在（id 基寻址）。
     * 生产出口＝workspace-impl {@code exception/CaliberNotFoundException}。
     */
    public static final String CODE_CALIBER_NOT_FOUND = "CALIBER_NOT_FOUND";

    /**
     * 409 语义位——口径状态机前置（版本行不在 DRAFT／REVIEWING，属主审批腿零调用）。
     * 生产出口＝workspace-impl {@code exception/CaliberStatePreconditionException}。
     */
    public static final String CODE_CALIBER_STATE_PRECONDITION = "CALIBER_STATE_PRECONDITION";

    /**
     * 503 语义位——认知引擎不可用（FC-03 确定性重算腿；冻结 10408 行
     * ECOS-COG 主表，FC 段跨域共用同值码位）。
     * 生产出口＝workspace-impl {@code scenario/CognitiveEngineUnavailableException}
     * 与 {@code exception/CognitiveEngineUnavailableException}（双包孪生 2 处）。
     * 认知域自有 {@code CognitiveErrorCode} 503 段是自己的码件，与本文 wire 值
     * 不共用；跨域搬值两类群读两口径，本批不搬。
     */
    public static final String CODE_COGNITIVE_UNAVAILABLE = "COGNITIVE_UNAVAILABLE";

    /**
     * 503 语义位——口径属主引擎不可用（冻结 10421 行；语义位
     * ONTOLOGY_UNAVAILABLE 属主签名归 ECOS-ONTO 域主口径）。
     * 生产出口＝workspace-impl {@code exception/CaliberOwnerUnavailableException}
     * （本批改指本常量）。
     */
    public static final String CODE_ONTOLOGY_UNAVAILABLE = "ONTOLOGY_UNAVAILABLE";

    // ==================== 无出口残留（禁建死常量）＋ 跨片共用托管位（本类不立常） ====================

    /*
     * 400 语义位 — FORECAST_SCOPE_INVALID 范围非法（10412 行）：
     * workspace-impl main 面带引号扫描 0 处合法载体 ⇒ 不建死码。
     * 出口真建时先在本类增常量＋同批扩门禁 FC 语义位词表，禁另造同值副本。
     *
     * 400 语义位 — OVERRIDE_FIELD_FORBIDDEN 覆盖超白名单（10413 行）：
     * scenario-copy overrides 白名单拦截腿面扫描 0 处 ⇒ 同上。
     *
     * 404 语义位 — DETAIL_NOT_FOUND 明细不存在（10416 行）：
     * run 明细式查询端点未建（现 404 出口到 run／口径 id 颗粒为止）⇒ 同上。
     *
     * 409 语义位 — FORECAST_DQ_BLOCK 缺格拦截（10417 行）：
     * DQ 缺格拦截能力面 501 STUB_CAPABILITY 显式进行中（backtests 附录A 第 33 条）⇒ 同上。
     *
     * 409 语义位 — RUN_IMMUTABLE 终态只读（10418 行）：
     * run 终态写拒绝腿未上线（预测 run 状态机无终态只读入口）⇒ 同上。
     *
     * 409 语义位 — PERIOD_ALREADY_CLOSED 关账期写拒绝（10420 行）：
     * 生产出口 {@code forecast/ForecastPeriodClosedException} 当前实值
     * {@code PERIOD_CLOSED}（409 alias），既有 wire 面，偏名/字面差是
     * 台账附录C 第 8 条订正注的独立判项——本批保留出口字面值不改、
     * 也不为冻结表真词另建常量（新增值的常量＝新代码，偏名归并是四
     * 枚举不是 ICV）。
     *
     * 503 语义位 — DATA_ENGINE_UNAVAILABLE（10421 行）：
     * workspace→认知→data 两跳中 data-engine 不可达现由认知面 503 兜底
     * （DataEngineForecastRunSnapshotReader），FC 段独立段用异常代码未建 ⇒ 同上。
     *
     * 503 语义位 — RUNTIME_ACCESS_UNAVAILABLE（10421 行）：
     * runtime-access 底座入口可达性站点 FC 段未建 ⇒ 同上。
     *
     * 跨片共用托管位（不立本族常量，逐枚搬流会跨族引用违铁律；
     * 归属以冻结表原文为准）：
     *   400 ACTION_FIELDS_MISSING（10414 行）：属主 ECOS-ACT 五必填段。
     *   403 PERMISSION_DENIED（10415 行）：属主 ECOS-SEC，生产出口仍在
     *         security denier，不搬流。
     *   503 SECURITY_UNAVAILABLE（10421 行）：属主 ECOS-SEC，同上。
     */
}
