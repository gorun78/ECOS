package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import com.chinacreator.gzcm.runtime.core.monitor.metrics.IMetricRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 计数器指标底座实现（runtime-monitor 唯一指标出口，铁律 §2.5-5）。
 *
 * <p>内存累计 + {@link #snapshot()} 只读快照，零外部依赖；宿主侧若启用 MeterRegistry/actuator，
 * 可用 micrometer 版实现经 {@code @ConditionalOnMissingBean} 覆盖本实现，各引擎调用点不变。</p>
 *
 * <p>本类不含 Spring 注解 —— 装配由宿主/引擎配置类负责（与 {@code WarnLogServiceImpl} 同款约定，
 * runtime-monitor 不反向依赖业务模块）。</p>
 */
public class MetricRecorderServiceImpl implements IMetricRecorder {

    private static final Logger log = LoggerFactory.getLogger(MetricRecorderServiceImpl.class);

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();

    @Override
    public void increment(String name, Map<String, String> tags) {
        if (name == null || name.isBlank()) {
            log.warn("指标名缺失，计数丢弃: name={}", name);
            return;
        }
        counters.computeIfAbsent(key(name, tags), k -> new LongAdder()).increment();
    }

    /** 只读快照：{@code name[,tag=value…]} → 累计次数（按 key 字典序，便于诊断比对）。 */
    public Map<String, Long> snapshot() {
        Map<String, Long> view = new TreeMap<>();
        counters.forEach((k, v) -> view.put(k, v.sum()));
        return Map.copyOf(view);
    }

    private static String key(String name, Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name);
        new TreeMap<>(tags).forEach((k, v) -> sb.append(',').append(k).append('=').append(v));
        return sb.toString();
    }
}
