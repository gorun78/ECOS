package com.chinacreator.gzcm.engine.cognitive.api;

/**
 * 认知域错误码冻结表（详细设计-05 D.5 / F05-07；{@code code} 与 HTTP 状态一致）。
 *
 * <p>用户裁定 A3：503 原样穿透，禁 200 空 body；DG-6 场景无 Mind → {@code 200 data:[]} 而非 404。</p>
 */
public final class CognitiveErrorCode {

    private CognitiveErrorCode() {}

    public static final String E400_MIND_NOT_ON_SCENARIO = "ECOS-COG-400";   // 400 mind not found on this scenario
    public static final String E400_MIND_NOT_FOUND       = "ECOS-COG-400M"; // 400 mind not found
    public static final String E400_INVALID_PARAM        = "ECOS-COG-400P"; // 400 invalid parameter
    public static final String E400_MODEL_TYPE_UNMAPPED  = "ECOS-COG-400C"; // 400 cognitive_model_type unmapped (REQ-COG-07 规格 1, fail-loud)
    public static final String E404_SCENARIO             = "ECOS-COG-404";  // 404 scenario not found / scenario deleted
    public static final String E404_MODEL_ROW            = "ECOS-COG-404R"; // 404 model registry version row not found (REQ-COG-07)
    public static final String E403_CLEARANCE            = "ECOS-COG-403";  // 403 clearance denied
    public static final String E409_MODEL_IMMUTABLE      = "ECOS-COG-409R"; // 409 published model version immutable (REQ-COG-07 规格 3)
    public static final String E422_GATES_NOT_PASSED     = "ECOS-COG-422R"; // 422 publish gates not all PASS (REQ-COG-07 规格 4)

    public static final String E500_ENGINE_DISABLED      = "ECOS-COG-500";  // 503 cognitive engine not enabled
    public static final String E501_SCHEMA_NOT_READY     = "ECOS-COG-501";  // 503 cognitive schema not ready
    public static final String E502_BALANCE_NOT_OK       = "ECOS-COG-502";  // 503 scenario attribution not balanced
    public static final String E503_NOT_IMPLEMENTED      = "ECOS-COG-503";  // 501 capability not implemented (stub=true)
    public static final String E504_INTERNAL             = "ECOS-COG-504";  // 500 cognitive internal error

    /** 冻结字面量（禁止改写，防前端 i18n 不一致）。 */
    public static final String MSG_MIND_NOT_ON_SCENARIO = "mind not found on this scenario";
    public static final String MSG_MIND_NOT_FOUND       = "mind not found";
    public static final String MSG_INVALID_PARAM        = "invalid parameter";
    public static final String MSG_SCENARIO_NOT_FOUND   = "scenario not found";
    public static final String MSG_SCENARIO_DELETED     = "scenario deleted";
    public static final String MSG_CLEARANCE_DENIED     = "clearance denied";
    public static final String MSG_ENGINE_NOT_ENABLED   = "cognitive engine not enabled";
    public static final String MSG_SCHEMA_NOT_READY     = "cognitive schema not ready";
    public static final String MSG_NOT_BALANCED         = "scenario attribution not balanced";
    public static final String MSG_NOT_IMPLEMENTED      = "capability not implemented";
    public static final String MSG_INTERNAL             = "cognitive internal error";

    /** REQ-COG-07 注册中心冻结字面量（与上方同纪律：禁改写，只可新增）。 */
    public static final String MSG_MODEL_TYPE_UNMAPPED  = "cognitive model type unmapped";
    public static final String MSG_MODEL_ROW_NOT_FOUND  = "cognitive model version not found";
    public static final String MSG_MODEL_IMMUTABLE      = "published model version is immutable";
    public static final String MSG_GATES_NOT_PASSED     = "publish gates not all passed";
}
