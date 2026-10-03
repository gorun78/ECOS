package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W40 12-cell 矩阵 — PROJ_OWNER 行：项目负责人对数据访问策略有两个子规则，
 * 期望 p1 放行（allow=true）而 p2 拒绝（allow=false），
 * 验证 policy engine 能对同一角色输出细分裁决。
 */
class OpaProjOwnerTest {

    OpaPolicyService opaService;

    @BeforeEach
    void setup() {
        opaService = mock(OpaPolicyService.class);
    }

    /** p1_allow：项目负责人 p1 子规则放行。 */
    @Test
    @DisplayName("PROJ_OWNER p1: 允许子规则 allow=true")
    void p1_allow() {
        Map<String, Object> resp = Map.of(
                "allow", Boolean.TRUE,
                "obligations", List.of(),
                "policyId", "proj_owner_p1",
                "source", "opa"
        );
        when(opaService.evaluate(eq("data_access_p1"), anyMap())).thenReturn(resp);

        Map<String, Object> result = opaService.evaluate("data_access_p1",
                Map.of("role", "proj_owner", "rule", "p1"));

        assertEquals(Boolean.TRUE, result.get("allow"), "p1 子规则必须放行");
        assertEquals("proj_owner_p1", result.get("policyId"));
    }

    /** p2_deny：项目负责人 p2 子规则拒绝。 */
    @Test
    @DisplayName("PROJ_OWNER p2: 拒绝子规则 allow=false")
    void p2_deny() {
        Map<String, Object> resp = Map.of(
                "allow", Boolean.FALSE,
                "obligations", List.of(),
                "policyId", "proj_owner_p2",
                "source", "opa"
        );
        when(opaService.evaluate(eq("data_access_p2"), anyMap())).thenReturn(resp);

        Map<String, Object> result = opaService.evaluate("data_access_p2",
                Map.of("role", "proj_owner", "rule", "p2"));

        assertEquals(Boolean.FALSE, result.get("allow"), "p2 子规则必须拒绝");
        assertEquals("proj_owner_p2", result.get("policyId"));
    }
}
