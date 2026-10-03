package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * C.9 性能与容量基线「管道节点数 定义 ≤100 节点」护栏（F02-03 / PipelineValidator）。
 *
 * <p>C.9 行 3（doc 行 409「管道节点数 定义 ≤100 节点」）已由
 * {@link PipelineValidator#validate} 落地为 {@code MAX_NODES = 100}
 * （{@code steps.size() > 100} → 报错，纯方法不触库/不 Spring）。
 * 本护栏钉死该上限的边界语义，防未来误改为 >=100 / 1000 / 静默放行：</p>
 * <ul>
 *   <li>101 节点 → 报错且消息含 100（判据是 &gt;100 而非 &gt;=100 / 更大值）；</li>
 *   <li>恰 100 节点 → 校验通过（无 error），证明上限不误缩到 99；</li>
 * </ul>
 */
@DisplayName("C.9 容量基线「管道定义 ≤100 节点」护栏（PipelineValidator.MAX_NODES）")
class PipelineNodeCapTest {

    private final PipelineValidator validator = new PipelineValidator();

    private static List<Map<String, Object>> steps(int n) {
        List<Map<String, Object>> list = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            Map<String, Object> step = new java.util.LinkedHashMap<>();
            step.put("id", "n" + i);
            step.put("node_type", "SOURCE_JDBC");
            step.put("config_json", "{}");
            // 无 depends_on：保持链上无引用/无环，只隔离"节点数"这一维
            list.add(step);
        }
        return list;
    }

    @Test
    @DisplayName("101 节点 → 超上限报一条 max-steps 错误，消息含 100")
    void over100_nodes_rejected() {
        List<String> errors = validator.validate(steps(101));

        assertEquals(1, errors.size(), "应只有节点数超限这 1 条 error; 实际 " + errors);
        assertTrue(errors.get(0).contains("100"), "错误消息应指认 100 上限; 实际 " + errors.get(0));
        assertTrue(errors.get(0).contains("101"), "错误消息应指认实际节点数 101; 实际 " + errors.get(0));
    }

    @Test
    @DisplayName("恰 100 节点 → 校验通过（判据是 >100 而非 >100/缩到 99，上限不误伤）")
    void exactly100_nodes_passes() {
        List<String> errors = validator.validate(steps(100));

        assertTrue(errors.isEmpty(), "100 节点恰好达标应零校验错误, 实际 " + errors);
    }
}
