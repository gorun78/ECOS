package com.chinacreator.gzcm.runtime.core.monitor.metrics;

import java.util.Map;

/**
 * 指标记录门面（runtime-monitor 是监控/指标唯一出口，铁律 §2.5-5：引擎与业务服务禁自建指标注册表）。
 *
 * <p>设计-05 C.7-4 的三枚认知计数即经本门面暴露。宿主未装配实现（无 actuator/MeterRegistry）时，
 * 调用方必须降级为 DEBUG 日志而不是静默丢弃计数，也禁止在引擎侧另起一套 registry。</p>
 */
public interface IMetricRecorder {

    /**
     * 计数器自增。
     *
     * @param name 指标名（冻结字面量，由各域 api 单源提供，禁调用方拼字符串）
     * @param tags 维度标签，可为空；实现侧负责命名规范化
     */
    void increment(String name, Map<String, String> tags);

    /** 无维度自增。 */
    default void increment(String name) {
        increment(name, Map.of());
    }
}
