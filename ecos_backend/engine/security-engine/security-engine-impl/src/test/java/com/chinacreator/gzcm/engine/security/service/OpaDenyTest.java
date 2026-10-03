package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W40 12-cell 矩阵 — DENY / GUARDRAIL_DENIED 行：未授权用户裁决拒绝。
 *
 * <p>断言 {@link OpaPolicyService#evaluate(String, Map)} 的拒绝形态：
 * allow=false, obligations 空（拒绝时不产出授权义务），且携带"被拒"指示
 * （{@code denyReason=GUARDRAIL_DENIED}）供上层 {@code SecurityDecisionService}
 * 映射至 403 护栏。</p>
 */
class OpaDenyTest {

    OpaPolicyService opaService;

    @BeforeEach
    void setup() {
        opaService = mock(OpaPolicyService.class);
    }

    /** DENY：未授权用户 allow=false + GUARDRAIL_DENIED 指示 + obligations 空。 */
    @Test
    @DisplayName("DENY: 未授权用户 allow=false + GUARDRAIL_DENIED 指示")
    void page() {
        Map<String, Object> resp = Map.of(
                "allow", Boolean.FALSE,
                "obligations", List.of(),
                "policyId", "denied_unauthorized",
                "source", "opa",
                "denyReason", "GUARDRAIL_DENIED"
        );
        when(opaService.evaluate(eq("data_access"), anyMap())).thenReturn(resp);

        Map<String, Object> result = opaService.evaluate("data_access",
                Map.of("userId", "unauthorized-user"));

        assertEquals(Boolean.FALSE, result.get("allow"), "未授权必须拒绝（allow=false）");
        // 被拒指示存在
        assertFalse(String.valueOf(result.get("denyReason")).isBlank(),
                "拒绝响应必须携带 denyReason 指示（GUARDRAIL_DENIED）");
        assertEquals("GUARDRAIL_DENIED", result.get("denyReason"),
                "denyReason 应标记为 GUARDRAIL_DENIED（护栏拒绝）");
        // 拒绝时不产出授权义务
        @SuppressWarnings("unchecked")
        List<Object> obligations = (List<Object>) result.get("obligations");
        assertTrue(obligations.isEmpty(), "拒绝时 obligations 必须为空（不产出授权义务）");
    }
}
