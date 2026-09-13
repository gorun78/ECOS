package com.chinacreator.gzcm.common.engine;

import java.util.List;
import java.util.Map;

/**
 * 计算属性求值引擎契约（PMO-52 附 G4 断链路）。
 *
 * <p>抽取自 {@code com.chinacreator.gzcm.engine.ontology.engine.FunctionEvaluator}
 * 被 workspace 直接依赖的对外方法。workspace 独立部署（:18090）无法解析 ontology-engine-impl
 * 具体类，改为接口契约：实现归各聚合场景（monolith 由 ontology-engine-impl 提供 Bean，
 * workspace 独立部署时由 workspace-impl 的 fallback 兜底 no-op）。</p>
 */
public interface IFunctionPropertyEvaluator {

    /**
     * 对一组对象实例计算并注入计算属性（原地写入 objects 内的计算字段）。
     *
     * @param entityCode 实体代码
     * @param objects    对象实例列表（会被原地增强）
     */
    void computeAndInject(String entityCode, List<Map<String, Object>> objects);
}
