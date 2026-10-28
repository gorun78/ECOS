package com.chinacreator.gzcm.engine.cognitive.api;

/**
 * 认知域接口路径常量单源（详细设计-05 F05-01 / D.1 / C103/C104 定版）。
 *
 * <p>铁律：所有 cognitive Controller 的字面路径只许引用本类常量；
 * {@code CognitiveContractSingleSourceTest#noInlineCognitivePathLiterals} 以源码扫描为门禁。</p>
 *
 * <p>契约端点族（v2.0；R-14 ① 定版属主 = cognitive；workspace 场景层只编排不生产）：</p>
 * <ul>
 *   <li>E1 GET {@link #SCENARIO_COGNITION_DETECT} {@code /api/v1/business/scenarios/{id}/cognition/detect}</li>
 *   <li>E2 GET {@link #SCENARIO_COGNITION_OPERATION_EVAL} {@code /api/v1/business/scenarios/{id}/cognition/operation-eval?mind=&amp;limit=}</li>
 *   <li>E3 GET {@link #SCENARIO_COGNITION_HYPOTHESES}   {@code /api/v1/business/scenarios/{id}/cognition/hypotheses?mind=}</li>
 *   <li>E4 GET {@link #SCENARIO_COGNITION_BELIEFS}     {@code /api/v1/business/scenarios/{id}/cognition/beliefs?mind=}</li>
 * </ul>
 */
public final class CognitiveApiPaths {

    private CognitiveApiPaths() {}

    /** 场景认知契约前缀模板（%s = scenarioId）。 */
    public static final String SCENARIO_COGNITION_TEMPLATE = "/api/v1/business/scenarios/%s/cognition";

    public static final String SCENARIO_COGNITION_DETECT         = "/detect";
    public static final String SCENARIO_COGNITION_OPERATION_EVAL = "/operation-eval";
    public static final String SCENARIO_COGNITION_HYPOTHESES     = "/hypotheses";
    public static final String SCENARIO_COGNITION_BELIEFS        = "/beliefs";

    /** 场景认知前缀模板：拼接 {@code String.format(SCENARIO_COGNITION_PREFIX_TEMPLATE, id)} → {@code /api/v1/business/scenarios/{id}/cognition}。 */
    public static final String SCENARIO_COGNITION_PREFIX_TEMPLATE = "/api/v1/business/scenarios/%s/cognition";

    /** Spring 映射专用 URI 模板（{@code {scenarioId}} 占位，供 {@code @GetMapping}/{@code @PathVariable} 绑定；禁在 Controller 出现字面量）。 */
    public static final String SCENARIO_COGNITION_URI_TEMPLATE = "/api/v1/business/scenarios/{scenarioId}/cognition";

    /** 归属 cognitive 的健康端点族（F05-01：属主从 ai 迁至 cognitive；ai 侧旧路径走 Deprecation 别名）。 */
    public static final String ENGINE_COGNITIVE = "/api/v1/engine/cognitive";
    public static final String ENGINE_COGNITIVE_HEALTH = ENGINE_COGNITIVE + "/health";

    /** 认知诊断（F05-09；REQ-COG-02）。 */
    public static final String COGNITIVE = "/api/v1/cognitive";
    public static final String COGNITIVE_DIAGNOSE = COGNITIVE + "/diagnose";
    public static final String COGNITIVE_DIAGNOSE_HISTORY = COGNITIVE_DIAGNOSE + "/history";
    public static final String COGNITIVE_SIMULATE  = COGNITIVE + "/simulate";
    public static final String COGNITIVE_FORECAST  = COGNITIVE + "/forecast";

    /** 认知模型注册中心（REQ-COG-07；PMO-51 既有 /models 列表族之上只增不改的新端点族）。 */
    public static final String COGNITIVE_MODELS = COGNITIVE + "/models";
    /** 注册中心新端点相对路径（挂在 ForecastController 既有 {@code COGNITIVE} 类前缀下）。 */
    public static final String MODELS_REGISTRY_REL = "/models/registry";
    public static final String MODELS_HASH_REL = "/models/{id}/content-hash";
    public static final String MODELS_GATES_REL = "/models/{id}/gates";
    public static final String MODELS_PUBLISH_REL = "/models/{id}/publish";

    /**
     * C 层对象目录只读端点相对路径（详设-05 §8.1 接缝义务「提供对象目录与只读解析端点」，
     * 挂在 {@code COGNITIVE} 类前缀下，REQ-COG-06）。
     */
    public static final String PARAM_OBJECT_KEY = "objectKey";
    public static final String OBJECTS_REL = "/objects";
    public static final String OBJECTS_ITEM_REL = OBJECTS_REL + "/{" + PARAM_OBJECT_KEY + "}";

    // ── 《认知域与智能域补充详细设计 v1.0》增量端点族（§13 单源登记；只增不改，禁改写上方既有常量）──

    /** §5 Context Builder（请求态 Context Pack，不建认知上下文表；聚合装配见 impl）。 */
    public static final String CONTEXT_BUILD = COGNITIVE + "/context/build";

    /** §8 认知资产目录 / 只读解析 / 受控提案（属主 cognitive，只增子路径）。 */
    public static final String PARAM_ASSET_ID   = "assetId";
    public static final String ASSETS_CATALOG   = COGNITIVE + "/assets/catalog";
    public static final String ASSETS_RESOLVE   = COGNITIVE + "/assets/{" + PARAM_ASSET_ID + "}/resolve";
    public static final String ASSETS_PROPOSALS = COGNITIVE + "/assets/{" + PARAM_ASSET_ID + "}/proposals";

    /** §9 W→D 结果评估（预测 vs 实际偏差诊断；数值只来自确定性计算，LLM 零参与数值）。 */
    public static final String OUTCOMES_EVALUATE = COGNITIVE + "/outcomes/evaluate";

    /** E2 相关查询参数名（防前后端字段漂移）。 */
    public static final String PARAM_MIND   = "mind";
    public static final String PARAM_LIMIT  = "limit";
    public static final int    E2_LIMIT_DEFAULT = 10;
    public static final int    E2_LIMIT_MAX     = 50;

    // ── SUP-C 追加（只许追加在本类末尾，禁改写上方既有行）──

    /** §8.4 认知资产受控状态迁移（治理操作者通道；非法迁移 ⇒ 409 ECOS-COG-409S 原行不变）。 */
    public static final String ASSETS_TRANSITION = COGNITIVE + "/assets/{" + PARAM_ASSET_ID + "}/transitions";

    // ── SUP-D 修补（2026-10-11，只追加本段，上方行零改写）──

    /**
     * ASSETS_* 族的相对子路径（与上方绝对常量逐字同源，绝对 = COGNITIVE + REL）。
     * <p>背景：{@code CognitiveAssetCatalogController} 类级前缀为 {@link #COGNITIVE}，
     * Spring 会把方法级映射值与类前缀<b>拼接</b>——方法级直接引用绝对常量会注册成
     * 双前缀路径（{@code /api/v1/cognitive/api/v1/cognitive/assets/catalog}），
     * 与 §13 登记契约不符（实测 gateway 单前缀 404 / 双前缀 200）。
     * 修正为方法级用 {@code *_REL}（对齐 {@link #MODELS_REGISTRY_REL} 既有先例）。</p>
     */
    public static final String ASSETS_CATALOG_REL     = "/assets/catalog";
    public static final String ASSETS_RESOLVE_REL     = "/assets/{" + PARAM_ASSET_ID + "}/resolve";
    public static final String ASSETS_PROPOSALS_REL   = "/assets/{" + PARAM_ASSET_ID + "}/proposals";
    public static final String ASSETS_TRANSITION_REL  = "/assets/{" + PARAM_ASSET_ID + "}/transitions";

    // ── 详设-09 v1.2 §十一 H-4 追加（2026-10-12，只追加本段，上方行零改写）──

    /**
     * 回测面（H-4：三指标 + {@code LOW_SAMPLE} 守卫 + 复审任务路由）。
     *
     * <p>绝对路径 = {@link #COGNITIVE} + 下列相对段，挂在 §9 既有 {@code /outcomes} 族之下
     * <b>只增子路径</b>；既有四端点（{@code POST /outcomes/evaluate}、{@code GET /outcomes/{id}}、
     * {@code GET /outcomes}、{@code GET /outcomes/by-run/{runId}}）的路径、参数签名与响应语义零改动。</p>
     */
    public static final String PARAM_EVALUATION_ID = "evaluationId";
    /** 回测指标面（只读视图，复用 {@code OutcomeEvaluateResult} 承载，新增可空 {@code backtest} 段）。 */
    public static final String OUTCOMES_BACKTEST_REL = "/outcomes/{" + PARAM_EVALUATION_ID + "}/backtest";
    /** 复审任务路由（越限 ⇒ 经 runtime-task 建复审任务；角色只来自 security-engine 裁决）。 */
    public static final String OUTCOMES_REVIEW_TASKS_REL = "/outcomes/{" + PARAM_EVALUATION_ID + "}/review-tasks";
    /** 绝对形态（供文档/契约面引用；Controller 侧用上面的相对段）。 */
    public static final String OUTCOMES_BACKTEST = COGNITIVE + "/outcomes/{evaluationId}/backtest";
    public static final String OUTCOMES_REVIEW_TASKS = COGNITIVE + "/outcomes/{evaluationId}/review-tasks";

    // ── 差距分析 P0-4（详设-09 §十一 H-5 认知侧半区，2026-10-14 追加，只追加本段，上方行零改写）──

    /**
     * 预测 run 计算与读回端点族（{@code ForecastRunGateway} SPI 的认知域真实落地）。
     *
     * <p>正典前缀 = {@link #COGNITIVE} + {@link #FORECAST_RUNS_REL} = {@code /api/v1/cognitive/forecast-runs}；
     * 与既有 {@code POST /forecast}（时序预测，无 run 语义）严格分族，不复用其路径。
     * 网关侧<b>无正向 rewrite 条目</b>（对齐 {@code /api/v1/dq/}、{@code /api/v1/pipeline/} 的 KEEP 教训：
     * 全库无 {@code /api/cognitive/forecast-runs} 的 legacy Controller，重写会把正典改到空目标）。</p>
     */
    public static final String PARAM_FORECAST_RUN_ID = "forecastRunId";
    public static final String PARAM_IDEMPOTENCY_KEY = "idempotencyKey";
    /** 类级前缀下的相对段（Controller 侧只用相对段，绝对常量供契约/文档面引用）。 */
    public static final String FORECAST_RUNS_REL = "/forecast-runs";
    public static final String FORECAST_RUNS_COMPUTE_REL = FORECAST_RUNS_REL + "/compute";
    public static final String FORECAST_RUNS_ITEM_REL = FORECAST_RUNS_REL + "/{" + PARAM_FORECAST_RUN_ID + "}";
    public static final String FORECAST_RUNS_BY_KEY_REL =
            FORECAST_RUNS_REL + "/by-idempotency-key/{" + PARAM_IDEMPOTENCY_KEY + "}";
    public static final String FORECAST_RUNS_RESULTS_REL = FORECAST_RUNS_ITEM_REL + "/results";
    public static final String FORECAST_RUNS_EVIDENCE_REL = FORECAST_RUNS_ITEM_REL + "/evidence";
    /** 绝对形态（文档/契约引用）。 */
    public static final String FORECAST_RUNS = COGNITIVE + FORECAST_RUNS_REL;
    public static final String FORECAST_RUNS_COMPUTE = COGNITIVE + FORECAST_RUNS_COMPUTE_REL;
    public static final String FORECAST_RUNS_ITEM = COGNITIVE + FORECAST_RUNS_ITEM_REL;
    public static final String FORECAST_RUNS_BY_KEY = COGNITIVE + FORECAST_RUNS_BY_KEY_REL;
    public static final String FORECAST_RUNS_RESULTS = COGNITIVE + FORECAST_RUNS_RESULTS_REL;
    public static final String FORECAST_RUNS_EVIDENCE = COGNITIVE + FORECAST_RUNS_EVIDENCE_REL;

    // ── 差距分析 P0-6 / R4 Mind 聚合根（V292 + 候选更新闸门；只追加本段，上方行零改写，2026-10-27 #141/B3）──

    /**
     * Mind 治理与候选更新闸门端点族（R4 §8.4.2 / §8.5.3；属主 cognitive）。
     *
     * <p>绝对前缀 = {@link #COGNITIVE} + {@link #MINDS_REL} = {@code /api/v1/cognitive/minds}；
     * 候选闸门 V292 表 {@code ecos_cognitive.ecos_cognitive_mind_update}：
     * 提交恒 PROPOSED（W Agent 通道等价字面），受理/驳回只经受控端点置终态。
     * 网关侧<b>无正向 rewrite 条目</b>（对齐 {@code /api/v1/cognitive/forecast-runs} KEEP 教训）。
     * 拦截面上列按认知域字母序部署在同族命名器之外——此处命名刻意以 {@code *_MIND*} 区别于
     * 既有 {@code COGNITIVE_MODELS}（那是认知<b>模型资产</b>注册中心，与 Mind 聚合根两个词、两张表）。</p>
     */
    public static final String PARAM_MIND_ID    = "mindId";
    public static final String PARAM_UPDATE_ID  = "updateId";
    public static final String MINDS_REL                         = "/minds";
    public static final String MINDS_SUBMIT_REL                  = MINDS_REL + "/{" + PARAM_MIND_ID + "}/proposals";
    public static final String MINDS_PROPOSED_REL                = MINDS_REL + "/proposals";
    public static final String MINDS_ITEM_REL                    = MINDS_REL + "/proposals/{" + PARAM_UPDATE_ID + "}";
    public static final String MINDS_ITEM_STATUS_REL             = MINDS_REL + "/proposals/{" + PARAM_UPDATE_ID + "}/status";
    /** 绝对形态（文档/契约引用）。 */
    public static final String MINDS_SUBMIT = COGNITIVE + MINDS_SUBMIT_REL;
    public static final String MINDS_PROPOSED = COGNITIVE + MINDS_PROPOSED_REL;
    public static final String MINDS_ITEM = COGNITIVE + MINDS_ITEM_REL;
    public static final String MINDS_ITEM_STATUS = COGNITIVE + MINDS_ITEM_STATUS_REL;

    // ── 差距分析附录A 第 18 条（2026-10-28 #146t 追加，只追加本段，上方行零改写）──

    /**
     * C 层对象目录契约名别名（{@code GET /api/v1/cognitive/object-catalog}）。
     *
     * <p>《详细设计说明书-从零实施版》第 5416 行接口总表定名 {@code object-catalog}
     * （{@code getCognitiveObjectCatalog}，载荷 {@code {items:[{cClass,carrier,readOnlyResolvePath?}]}}），
     * 实现侧既有端点名为 {@link #OBJECTS_REL}。此处补的是<b>契约名别名</b>，
     * 既有 {@code /objects} 与 {@code /objects/{objectKey}} 路径、载荷一字未动（只增不改）。</p>
     */
    public static final String OBJECT_CATALOG_REL = "/object-catalog";
}
