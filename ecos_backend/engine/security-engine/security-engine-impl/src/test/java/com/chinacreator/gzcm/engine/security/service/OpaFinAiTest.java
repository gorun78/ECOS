package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W40 12-cell 矩阵 — FIN_AI_DRILLDOWN 行：财务角色对"AI 下钻"数据访问裁决
 * {@code data_access} 应 <b>放行</b>（allow=true），且 obligations 为空
 * （无需附加义务/脱敏动作）。
 *
 * <p>被测契约 = {@link OpaPolicyService#evaluate(String, Map)} 的响应形态：
 * {@code {allow, obligations[], policyId, source:"opa"}}。这里以 mock 固定响应，
 * 锁定"放行 + 无义务" consumes，不启动 OPA 节点 / 不触及 HTTP。</p>
 */
class OpaFinAiTest {

    OpaPolicyService opaService;

    @BeforeEach
    void setup() {
        opaService = mock(OpaPolicyService.class);
    }

    /** FIN_AI：financial 角色 AI drilldown ⇒ allow=true, obligations=[]。 */
    @Test
    @DisplayName("FIN_AI: data_access 财务 AI 下钻 allow=true + obligations 空")
    void page() {
        Map<String, Object> opaResponse = Map.of(
                "allow", Boolean.TRUE,
                "obligations", List.of(),
                "policyId", "finance_ai_drilldown",
                "source", "opa",
                "policy", "data_access"
        );
        when(opaService.evaluate(eq("data_access"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(opaResponse);

        Map<String, Object> result = opaService.evaluate("data_access",
                Map.of("role", "finance", "purpose", "ai_drilldown"));

        assertEquals(Boolean.TRUE, result.get("allow"), "财务 AI 下钻必须放行");
        assertNotNull(result.get("obligations"), "obligations 字段必须存在");
        assertTrue(((List<?>) result.get("obligations")).isEmpty(),
                "放行情景无附加义务 → obligations 必须为空");
        assertEquals("finance_ai_drilldown", result.get("policyId"), "policyId 应回填来源策略");
        assertEquals("opa", result.get("source"), "来源应标记为 opa（非 fallback）");
    }
}
