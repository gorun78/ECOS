package com.chinacreator.gzcm.common.error;

/**
 * 本体域（金I · ontology-engine / buszhi）统一错误码常量 —— 《ECOS 详细设计说明书-从零实施版》
 * §6.3.3「错误码族」表中 {@code ECOS-ONTO-*} 族的代码侧单源。
 *
 * <p><b>#146af 新登</b>（收口差距 P1.1「错误码族字面量散落」的本体域腿）：本族此前在代码面
 * 以<b>字面量副本</b>散布于 ontology-engine-impl 的 8 个 main 面文件（枚定义常量、message
 * 起始位拼接、message 中段括号、日志格式串四种形态），common-api 侧零属主——调用方按机读码
 * 分派失败态时，任一副本被改写即静默漂移。本类是 §6.3.3 已登记码的<b>唯一 Java 字面量单源
 * 持有者</b>（DSL 号段与表外码位除外，见下），零容差判据由 {@code NoMachineCodeFactoryFaceRatchetTest#
 * ontoFamilyLiteralsAreSingleSourced} 锁在 common-api 门禁面，与 {@link SystemErrorCode}／
 * {@link RateLimitErrorCode}／{@link com.chinacreator.gzcm.common.data.DataDomainErrorCode}／
 * {@link AuthErrorCode}／{@link SecurityErrorCode}／{@link WorkflowErrorCode} 六族同型。</p>
 *
 * <p>纪律（与其余六族一致）：</p>
 * <ul>
 *   <li>码值即 §6.3.3 冻结值，禁改写——前端诊断面（{@code pages/ontology/caliber/index.tsx}
 *       直显 {@code ECOS-ONTO-040/041/042}）与测试按码匹配依赖字面量；</li>
 *   <li>本类<b>只登记 §6.3.3 在册十三枚</b>。「族里多一枚」＝用代码冒充文档裁决，因此
 *       DSL 四关机读码 {@code ECOS-ONTO-071/072/073}（{@code CaliberDslValidator} 私设号段）
 *       与成功标记 {@code ECOS-ONTO-000}（{@code CaliberController} 私设）<b>不进本类</b>，
 *       留在原属主处并按枚登记进门禁白名单，作待裁决余项入台账；</li>
 *   <li>本类只含常量，无任何业务 import（common-api enforcer 白名单内）。</li>
 * </ul>
 *
 * <p><b>在册码位与代码面语义的三处不一致（本批只如实登记，不改值）</b>：</p>
 * <ol>
 *   <li>{@code ECOS-ONTO-070} 在 §6.3.3 是「术语绑定的对象不存在（悬空）」（F6-11，验收夹具
 *       {@code GlossaryDanglingTermRejectTest}），而代码面该码位被口径 DSL 四关的
 *       <b>V1 语法关</b>占用（{@code CaliberDslValidator#CODE_SYNTAX}，workspace 侧
 *       {@code CaliberAddressing#OWNER_DSL_GATE} 已在消费该映射）⇒ 同一枚两个语义。改值＝改
 *       wire 契约且要动跨模块消费面，属裁决批；本批判据按枚做 (文件, 码) 对账，把两种占用
 *       一并钉住在白名单内，禁止出现第三种。</li>
 *   <li>{@code ECOS-ONTO-011}（版本幂等 409）与 {@code ECOS-ONTO-080}（唯一 404 语义）在册，
 *       但 main 面<b>零枚承载出口</b>（实扫 {@code grep -rn "ECOS-ONTO-011\|ECOS-ONTO-080"
 *       --include=*.java | grep src/main}＝0）⇒ 本类给出的是<b>字面量锚点</b>，承流腿仍欠，
 *       台账记为待办，不因本类落码而宣称该两枚已实现。</li>
 *   <li>{@code ECOS-ONTO-040} 在 §6.3.3 只描述「发布态指标缺 caliber_id 或口径版本非
 *       APPROVED」，代码面另有「身份缺失（approve 无 Username）」借用同一枚的腿（见
 *       {@code CaliberController} 类注释）——借用形态在本批保持原值，语义归位属裁决批。</li>
 * </ol>
 *
 * <p>与 HTTP 状态行的关系：§6.3.3 把每枚钉在确定状态码上；承载方式与 {@code ECOS-WF-020}
 * 同法——机读码写在 {@code message} 起始位或 {@code errorCode} 槽位，由 gateway
 * {@code GlobalExceptionHandler}／{@code MachineErrorCode#orNumeric} 单源取进信封
 * {@code errorCode} 字段。{@link com.chinacreator.gzcm.common.base.MachineErrorCode} 的正则
 * 只认 message 起始位，故中段拼接形态（如 {@code "(400 ECOS-ONTO-040)"}）是人读文本、不承担
 * 机读提取；门禁判据则<b>不限位置</b>（凡剥注释后露面即须按枚对账），比机读提取更严。</p>
 */
public final class OntologyErrorCode {

    private OntologyErrorCode() {
        throw new UnsupportedOperationException("常量类，禁止实例化");
    }

    /**
     * 409 — 版本幂等冲突：同 {@code (domain_code, version_no)} 重复发布。
     *
     * <p><b>承流欠（#146af 登记）</b>：§6.3.3 在册，但 main 面当前无出口承载该枚；本常量是
     * 字面量锚点，供后续发布幂等腿取码，禁在任何出口上以字面量副本形态出现。</p>
     */
    public static final String ECOS_ONTO_011 = "ECOS-ONTO-011";

    /**
     * 409 — 提案状态不允许该动作（含 PENDING 直执＝绕过审批）。
     *
     * <p>属主出口＝{@code OntologyProposalController} 的 execute 前置状态校验两条腿（PENDING
     * 与其他不可执行态），message 起始位承载。</p>
     */
    public static final String ECOS_ONTO_012 = "ECOS-ONTO-012";

    /** 400 — V2 单位推导冲突（单位代数不适用当前组合）。属主出口＝{@code GateResult#CODE_UNIT_CONFLICT}。 */
    public static final String ECOS_ONTO_021 = "ECOS-ONTO-021";

    /** 400 — V2 不可加指标被声明为可加聚合。属主出口＝{@code GateResult#CODE_ADDITIVITY_CONFLICT}。 */
    public static final String ECOS_ONTO_022 = "ECOS-ONTO-022";

    /** 400 — V3 血缘列在事实表上不存在（元数据已成功返回但列缺失）。属主出口＝{@code GateResult#CODE_LINEAGE_COLUMN_MISSING}。 */
    public static final String ECOS_ONTO_030 = "ECOS-ONTO-030";

    /**
     * 400 — 发布态指标缺 {@code caliber_id}，或绑定的口径版本非 APPROVED（V1）。
     *
     * <p>借用形态在册说明：{@code CaliberController} 的「approve 无 Username 身份缺失」腿同样
     * 出该枚（§6.3.3 未描述此语义），值保持不动、语义归位待裁决。</p>
     */
    public static final String ECOS_ONTO_040 = "ECOS-ONTO-040";

    /** 409 — 试图修改已冻结的 {@code caliber_snapshot}。属主出口＝{@code CaliberService#SnapshotFrozenException} 腿。 */
    public static final String ECOS_ONTO_041 = "ECOS-ONTO-041";

    /** 500 — 口径服务内部失败，fail-closed（禁折叠成空态／默认通过）。 */
    public static final String ECOS_ONTO_042 = "ECOS-ONTO-042";

    /**
     * 503 — 发布门禁依赖（data-engine 元数据 REST）不可用，提案未执行。
     *
     * <p>属主出口＝{@code GateResult#CODE_DEPENDENCY_UNAVAILABLE} 与 {@code PublishGateService#toResponseEntity}
     * 的 dependencyErrors 腿；日志面 {@code DatNetLineageGuard} 三枚 fail-closed 行取本常量。</p>
     */
    public static final String ECOS_ONTO_050 = "ECOS-ONTO-050";

    /** 400 — Function 执行审计上下文缺失（无 operator／traceId），拒收且不写审计。 */
    public static final String ECOS_ONTO_060 = "ECOS-ONTO-060";

    /** 500 — Function 审计双失败（Kafka 与本地表都写不进）。 */
    public static final String ECOS_ONTO_061 = "ECOS-ONTO-061";

    /**
     * 400 — 术语绑定的对象不存在（悬空）。
     *
     * <p><b>码位冲突（#146af 登记，待裁决）</b>：该枚在册语义属 F6-11 术语层（验收夹具
     * {@code GlossaryDanglingTermRejectTest} 尚未建立），而代码面同一枚目前承载的是口径 DSL
     * 四关的 V1 语法关。本常量登记<b>在册语义</b>；DSL 借用面的字面量留在
     * {@code CaliberDslValidator#CODE_SYNTAX}，由门禁白名单按 (文件, 码) 对账，禁第三处露面。</p>
     */
    public static final String ECOS_ONTO_070 = "ECOS-ONTO-070";

    /**
     * 404 — 对象／关系／口径编码不存在。
     *
     * <p>§6.3.3 明文：本域<b>唯一允许的 404 语义</b>，禁 5xx 掩蔽。<b>承流欠（#146af 登记）</b>：
     * main 面当前无出口承载该枚，本常量是字面量锚点。</p>
     */
    public static final String ECOS_ONTO_080 = "ECOS-ONTO-080";
}
