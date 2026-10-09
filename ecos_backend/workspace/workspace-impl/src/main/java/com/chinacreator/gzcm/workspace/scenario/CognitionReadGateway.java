package com.chinacreator.gzcm.workspace.scenario;

import java.util.Map;

/**
 * 认知读侧网关抽象（卷07 F07-12 N4~N7；属 cognitive-engine api 门面/REST，经 F07-17 出口）。
 * <p>workspace 只编排读取认知产物（异常检测 / 经营评估 / 假设集 / 信念状态），不生产结论、不 fallback 造数据。
 * 引擎不可用时抛异常 → 上层转 {@link CognitiveEngineUnavailableException}（真实 503）。</p>
 */
public interface CognitionReadGateway {

    Map<String, Object> detect(String scenarioId, Map<String, Object> params);

    Map<String, Object> operationEval(String scenarioId, Map<String, Object> params);

    Map<String, Object> hypotheses(String scenarioId, Map<String, Object> params);

    Map<String, Object> beliefs(String scenarioId, Map<String, Object> params);
}
