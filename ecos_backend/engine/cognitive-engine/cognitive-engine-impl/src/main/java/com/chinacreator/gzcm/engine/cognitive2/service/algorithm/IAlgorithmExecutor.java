package com.chinacreator.gzcm.engine.cognitive2.service.algorithm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认知算法执行器 SPI（V367 批次 A）。
 *
 * <p>每个执行器只做确定性计算：输入 Map → 输出结构化指标；不写库、不调 LLM、不发事件。
 * 数值口径必须可复现（同输入同输出），并在 {@code assumptions} 里写明成立条件与限制。</p>
 */
@FunctionalInterface
public interface IAlgorithmExecutor {

    /** 执行一次。入参缺失时抛 {@link IllegalArgumentException}（由 service 折成 400）。 */
    AlgorithmResult execute(Map<String, Object> input);

    /** 执行结果：metrics 是唯一数值来源，assumptions 说明限制。 */
    record AlgorithmResult(Map<String, Object> metrics, List<String> assumptions,
                           boolean degraded, String message) {

        public static AlgorithmResult ok(Map<String, Object> metrics, String... assumptions) {
            return new AlgorithmResult(copy(metrics), List.of(assumptions), false, null);
        }

        public static AlgorithmResult degraded(String reason, Map<String, Object> metrics) {
            return new AlgorithmResult(copy(metrics), List.of(reason), true, reason);
        }

        private static Map<String, Object> copy(Map<String, Object> in) {
            return in == null ? new LinkedHashMap<>() : new LinkedHashMap<>(in);
        }
    }

    static Map<String, Object> metrics(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    static List<String> list(String... items) {
        return new ArrayList<>(List.of(items));
    }
}
