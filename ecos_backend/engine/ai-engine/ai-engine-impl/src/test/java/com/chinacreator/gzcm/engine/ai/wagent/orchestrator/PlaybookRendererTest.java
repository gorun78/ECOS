package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Playbook 模板确定性填充（详细设计-10 F10-12，冻结验收名）。
 *
 * <p>同模板同参数必得逐字符相等输出（无随机/时钟/LLM 参与）；缺参或参数值注入占位
 * 一律 {@code E-VALIDATION} 拒绝（禁半填充 Plan 出闸、禁二次展开）。</p>
 */
class PlaybookRendererTest {

    private static final String TEMPLATE =
            "intent={{intent}}; metric={{metric_id}}; as_of={{as_of_date}}; note={{intent}}";

    @Test
    @DisplayName("F10-12：同模板同参数两次填充逐字符相等；Map 迭代序不参与产物（确定性）")
    void parametersFilledDeterministically() {
        PlaybookRenderer renderer = new PlaybookRenderer();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("metric_id", "M-1001");
        params.put("intent", "FORECAST");
        params.put("as_of_date", "2026-09-30");

        // 不同插入序的同内容参数表 ⇒ 产物必须一致（占位符按模板位置消费，与 Map 序无关）
        Map<String, String> reordered = new LinkedHashMap<>();
        reordered.put("as_of_date", "2026-09-30");
        reordered.put("intent", "FORECAST");
        reordered.put("metric_id", "M-1001");

        String first = renderer.fill(TEMPLATE, params);
        String second = renderer.fill(TEMPLATE, params);
        String third = renderer.fill(TEMPLATE, reordered);

        assertEquals("intent=FORECAST; metric=M-1001; as_of=2026-09-30; note=FORECAST", first);
        assertEquals(first, second, "同参数两次填充必须逐字符相等");
        assertEquals(first, third, "参数表迭代序不得影响产物");

        // 拒绝面：缺参 / null 模板 / 参数值注入占位（二次展开防线）
        WAgentOrchestrationException missing = assertThrows(WAgentOrchestrationException.class,
                () -> renderer.fill(TEMPLATE, Map.of("intent", "FORECAST")));
        assertEquals(WAgentErrorCode.E_VALIDATION, missing.errorCode());

        assertThrows(WAgentOrchestrationException.class, () -> renderer.fill(null, params));

        WAgentOrchestrationException residue = assertThrows(WAgentOrchestrationException.class,
                () -> renderer.fill("{{body}}", Map.of("body", "{{intent}}")));
        assertEquals(WAgentErrorCode.E_VALIDATION, residue.errorCode());
    }
}
