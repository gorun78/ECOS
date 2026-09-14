package com.chinacreator.gzcm.common.engine;

import java.util.Map;

/**
 * Action 钩子执行器契约（PMO-52 附 G4 断链路）。
 *
 * <p>抽取自 {@code com.chinacreator.gzcm.engine.ontology.engine.ActionHookExecutor}
 * 被 workspace 直接依赖的对外方法。实现归各聚合场景（monolith 由 ontology-engine-impl
 * 提供 Bean，workspace 独立部署时由 fallback 兜底 no-op）。</p>
 */
public interface IActionHookExecutor {

    /**
     * 执行前置钩子（VALIDATION）。
     *
     * @param actionDef Action 定义 Map
     * @param input     执行参数
     * @return 校验失败时返回含 code/message 的 Map；通过返回 null
     */
    Map<String, Object> executePreHooks(Map<String, Object> actionDef, Map<String, Object> input);

    /**
     * 执行后置钩子（NOTIFICATION + AUDIT_LOG）。
     */
    void executePostHooks(Map<String, Object> actionDef, Map<String, Object> input,
                          String executionResult, String entityCode, String userId);
}
