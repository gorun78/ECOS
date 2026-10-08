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

    /** E2 相关查询参数名（防前后端字段漂移）。 */
    public static final String PARAM_MIND   = "mind";
    public static final String PARAM_LIMIT  = "limit";
    public static final int    E2_LIMIT_DEFAULT = 10;
    public static final int    E2_LIMIT_MAX     = 50;
}
