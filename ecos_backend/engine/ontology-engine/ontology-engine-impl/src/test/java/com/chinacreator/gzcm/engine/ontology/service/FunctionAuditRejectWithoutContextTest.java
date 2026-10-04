package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.controller.FunctionController;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyFunctionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionCacheManager;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionResult;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionSandboxEngine;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionValidator;
import com.chinacreator.gzcm.engine.ontology.gate.AuditContextGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * F03-06 W81/C64 (M0 P0)：Function 审计上下文守卫 400 拒收（PRD-03 §4.1-2 / 设计-03 D.6）。
 *
 * <p>契约：无 operator / 无 traceId → <b>400 拒收</b>，message 含 {@code ECOS-ONTO-060}，
 * 且不进入执行/缓存/审计写链（{@code FunctionValidator}/{@code FunctionSandboxEngine}/
 * {@code FunctionCacheManager} 均不被调用）。
 *
 * <p>纯 Mockito + MDC 内设 traceId，不触 Spring/DB/网络。
 */
@ExtendWith(MockitoExtension.class)
class FunctionAuditRejectWithoutContextTest {

    @Mock FunctionValidator validator;
    @Mock FunctionSandboxEngine engine;
    @Mock FunctionCacheManager cacheManager;

    private AuditContextGuard guard;
    private FunctionController controller;

    @BeforeEach
    void setUp() {
        guard = new AuditContextGuard();
        controller = new FunctionController(validator, engine, cacheManager, guard);
    }

    @AfterEach
    void tearDown() {
        org.slf4j.MDC.clear();
    }

    // ── 守卫 standalone：operator 缺失 ──

    @Test
    @DisplayName("无 operator（callerId=null）→ 400 ECOS-ONTO-060 拒收")
    void operatorNullRejected() {
        org.slf4j.MDC.put("traceId", "trace-1");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> guard.require(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode(), "缺 operator 应 400");
        assertTrue(ex.getReason().contains("ECOS-ONTO-060"), "400 消息需携 ECOS-ONTO-060 错误码字面");
    }

    @Test
    @DisplayName("无 operator（callerId=\"anonymous\" 旧哨兵 / 空白）→ 400 ECOS-ONTO-060")
    void operatorSentinelRejected() {
        org.slf4j.MDC.put("traceId", "trace-1");
        for (String bad : new String[]{"anonymous", "ANONYMOUS", "", "   "}) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> guard.require(bad));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("ECOS-ONTO-060"));
        }
    }

    @Test
    @DisplayName("无 traceId（MDC 空） + operator 合规 → 仍 400 ECOS-ONTO-060（两个条件OR）")
    void traceIdMissingRejected() {
        // MDC 无 traceId（默认）
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> guard.require("pmo-user-1"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("ECOS-ONTO-060"));
        assertTrue(ex.getReason().toLowerCase().contains("traceid"), "缺 traceId 应在消息里点名");
    }

    // ── 守卫 standalone：白线 ──

    @Test
    @DisplayName("operator 合规 + traceId 有 → 通过（不抛）")
    void validContextPasses() {
        org.slf4j.MDC.put("traceId", "trace-ok");
        assertDoesNotThrow(() -> guard.require("pmo-user-1"));
        assertDoesNotThrow(() -> guard.require("  pmo-user-2  "));
    }

    // ── Controller 集成：/test 缺操作者 → 400，下游全无副作用 ──

    @Test
    @DisplayName("POST /test：callerId 缺省('anonymous') → 400 & 不进入 validator/engine/cache")
    void controllerTestMissingOperatorRejectedAndNoSideEffects() {
        org.slf4j.MDC.put("traceId", "trace-missing-op");
        OntologyFunctionSaveDTO req = new OntologyFunctionSaveDTO();
        req.setExpression("SUM(x)");
        req.setEntityName("emp");
        // 不传 callerId → controller 内 fallback 为 "anonymous" → 视为缺

        assertThrows(ResponseStatusException.class, () -> controller.test(req));

        verifyNoInteractions(validator);
        verifyNoInteractions(engine);
        verifyNoInteractions(cacheManager);
    }

    // ── Controller 集成：/{propertyId}/execute 缺 traceId → 400 ──

    @Test
    @DisplayName("GET /{propertyId}/execute：MDC 无 traceId → 400 ECOS-ONTO-060 & 无副作用")
    void controllerExecuteMissingTraceRejectedAndNoSideEffects() {
        // MDC 无 traceId
        assertThrows(ResponseStatusException.class,
                () -> controller.execute("prop-1", "SUM(x)", "emp"));
        verifyNoInteractions(validator);
        verifyNoInteractions(engine);
        verifyNoInteractions(cacheManager);
    }

    // ── Controller 集成：合规路径 → 正常执行（白线，验 guard 不破坏主链） ──

    @Test
    @DisplayName("POST /test：完整上下文 → 正常放行（validator 被调）")
    void controllerTestValidContextProceeds() {
        org.slf4j.MDC.put("traceId", "trace-valid");
        when(validator.quickScan(anyString())).thenReturn(null);
        when(validator.validate(anyString())).thenReturn(Map.of("valid", true));
        when(cacheManager.get(anyString(), anyString())).thenReturn(null);
        FunctionResult result = new FunctionResult();
        result.setValue(42);
        result.setExecutionTimeMs(3L);
        when(engine.test(anyString(), anyString(), anyString())).thenReturn(result);

        OntologyFunctionSaveDTO req = new OntologyFunctionSaveDTO();
        req.setExpression("SUM(x)");
        req.setEntityName("emp");
        req.setCallerId("pmo-user-1");

        ApiResponse<FunctionResult> resp = controller.test(req);
        assertEquals(0, resp.getCode(), "合规上下文应正常返回");
        assertNotNull(resp.getData(), "应返回 FunctionResult");
        assertEquals(42, resp.getData().getValue());
    }
}
