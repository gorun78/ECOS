package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-07 F07-13/W176/C164 验收：领域异常族携带真实 HTTP 状态码 + 业务 code + 结构化
 * details；HTTP 由 {@code WorkspaceExceptionHandler} 唯一映射（本测试仅断言抛出侧的载荷正确）。
 *
 * <p>{@code WorkspaceExceptionHandlerTest#eachDomainExceptionMapsToItsHttpStatus} 与
 * {@code ApiResponseTruthTest#noEndpointReturnsHttp200WithCode503} 属 {@code MockMvc} 层，
 * 需 F07-13 Advice 与 web 切片依赖，§8.4 P-2 未成立记"未执行"。</p>
 */
class ScenarioDomainExceptionTest {

    @Test
    @DisplayName("IslandBindingException → 409 / ISLAND_BINDING / islands 明细")
    void islandBindingExceptionCarriesHttp409AndDetails() {
        IslandBindingException e = new IslandBindingException(Collections.singletonList(
                Collections.singletonMap("bindingId", "b-1")));
        assertEquals(409, e.getHttpStatus());
        assertEquals("ISLAND_BINDING", e.getCode());
        assertNotNull(e.getDetails().get("islands"));
    }

    @Test
    @DisplayName("IllegalScenarioTransitionException.illegal(from,to) → 409 / ILLEGAL_TRANSITION")
    void illegalTransitionIs409() {
        IllegalScenarioTransitionException e = IllegalScenarioTransitionException.illegal("DRAFT", "COMPLETED");
        assertEquals(409, e.getHttpStatus());
        assertEquals("ILLEGAL_TRANSITION", e.getCode());
        assertTrue(e.getMessage().contains("DRAFT"));
        assertTrue(e.getMessage().contains("COMPLETED"));
    }

    @Test
    @DisplayName("IllegalScenarioTransitionException 另三工厂：TERMINAL_STATE / NO_FORMAL_RUN / INCOMPLETE_SCENARIO 均 409")
    void otherTransitionFactories() {
        assertEquals("TERMINAL_STATE", IllegalScenarioTransitionException.terminal("COMPLETED").getCode());
        assertEquals("NO_FORMAL_RUN", IllegalScenarioTransitionException.noFormalRun().getCode());
        assertEquals("INCOMPLETE_SCENARIO", IllegalScenarioTransitionException.incomplete().getCode());
        assertEquals(409, IllegalScenarioTransitionException.terminal("COMPLETED").getHttpStatus());
    }

    @Test
    @DisplayName("SandboxReferenceForbiddenException → 400 / SANDBOX_REF_FORBIDDEN + baselineRunId 明细（F07-09）")
    void sandboxReferenceIs400() {
        SandboxReferenceForbiddenException e = new SandboxReferenceForbiddenException("run-sb-01");
        assertEquals(400, e.getHttpStatus());
        assertEquals("SANDBOX_REF_FORBIDDEN", e.getCode());
        assertEquals("run-sb-01", e.getDetails().get("baselineRunId"));
    }

    @Test
    @DisplayName("CognitiveEngineUnavailableException → 503 / COGNITIVE_UNAVAILABLE（F07-13 X-16/X-17）")
    void cognitiveEngineUnavailableIs503() {
        CognitiveEngineUnavailableException e = new CognitiveEngineUnavailableException("cognitive");
        assertEquals(503, e.getHttpStatus());
        assertEquals("COGNITIVE_UNAVAILABLE", e.getCode());
        assertTrue(e.getTechnicalReason().contains("cognitive"));
        assertFalse(e.getMessage().contains("cognitive"),
                "下游原文不进响应体 message（X-41）");
    }

    @Test
    @DisplayName("族继承根：全部子异常都是 ScenarioDomainException（Advice 单点分派）")
    void allSubclassesAreDomainExceptions() {
        assertInstanceOf(ScenarioDomainException.class,
                new IslandBindingException(Collections.emptyList()));
        assertInstanceOf(ScenarioDomainException.class,
                IllegalScenarioTransitionException.illegal("DRAFT", "ACTIVE"));
        assertInstanceOf(ScenarioDomainException.class,
                new SandboxReferenceForbiddenException("r"));
        assertInstanceOf(ScenarioDomainException.class,
                new CognitiveEngineUnavailableException("kb"));
    }

    @Test
    @DisplayName("getDetails 返回不可变视图（禁调用方就地篡改，破坏 Advice 载荷契约）")
    void detailsAreUnmodifiable() {
        IslandBindingException e = new IslandBindingException(Collections.singletonList(
                Collections.singletonMap("k", "v")));
        try {
            e.getDetails().put("x", "y");
            // 若未抛，也不应改变原大小
            assertEquals(1, e.getDetails().size(), "unmodifiable 视图下 put 应失败或无效果");
        } catch (UnsupportedOperationException expected) {
            // 期望路径
        }
    }
}
