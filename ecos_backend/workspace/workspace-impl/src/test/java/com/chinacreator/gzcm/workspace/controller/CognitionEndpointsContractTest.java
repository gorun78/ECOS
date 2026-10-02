package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.exception.CognitiveEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.DcchengClient;
import com.chinacreator.gzcm.workspace.scenario.ScenarioMindService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F07-12 认知四端点契约单测（详细设计-07 验收
 * {@code fourCognitionRoutesExistWithPrdPaths} + {@code engineUnavailableYieldsHttp503NotHttp200WithEmptyBody}
 * 的服务层可测部分）。
 *
 * <p>(1) 反射枚举 4 条 {@link GetMapping}，断言 PRD 要求的四条路径逐字存在（REQ-API-02）；
 * (2) 引擎不可用（client 抛 {@link IllegalStateException}）→ 控制器抛
 * {@link CognitiveEngineUnavailableException}，由 advice 落真实 503（A3 红线，禁 200 空 body）。</p>
 */
class CognitionEndpointsContractTest {

    private static final Set<String> PRD_PATHS = Set.of(
            "/api/v1/business/scenarios/{id}/cognition/detect",
            "/api/v1/business/scenarios/{id}/cognition/operation-eval",
            "/api/v1/business/scenarios/{id}/cognition/hypotheses",
            "/api/v1/business/scenarios/{id}/cognition/beliefs");

    /** 反射收集控制器上的全部 @GetMapping 路径。 */
    private static Set<String> mappedPaths() {
        return java.util.Arrays.stream(ScenarioCognitionReadController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class))
                .flatMap(m -> java.util.Arrays.stream(m.getAnnotation(GetMapping.class).value()))
                .collect(Collectors.toSet());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/business/scenarios/{id}/cognition/detect",
            "/api/v1/business/scenarios/{id}/cognition/operation-eval",
            "/api/v1/business/scenarios/{id}/cognition/hypotheses",
            "/api/v1/business/scenarios/{id}/cognition/beliefs"})
    void fourCognitionRoutesExistWithPrdPaths(String path) {
        assertTrue(mappedPaths().contains(path),
                "PRD 四端点必须逐字存在: " + path + "（实际 " + mappedPaths() + "）");
    }

    private final DcchengClient client = mock(DcchengClient.class);
    private final ScenarioMindService mindService = mock(ScenarioMindService.class);
    private final ScenarioCognitionReadController controller =
            new ScenarioCognitionReadController(client, mindService);

    @ParameterizedTest
    @ValueSource(strings = {"detect", "operationEval", "hypotheses", "beliefs"})
    void allFourEndpointsElevateClientFailureToCognitiveUnavailable(String methodName) {
        // 全部四个 client 方法统一挂 IllegalStateException，模拟超时/4xx/5xx 的统一上层信号
        when(client.detect(anyString(), any())).thenThrow(new IllegalStateException("client down"));
        when(client.operationEval(anyString(), any())).thenThrow(new IllegalStateException("client down"));
        when(client.hypothesesGet(anyString(), any())).thenThrow(new IllegalStateException("client down"));
        when(client.beliefsGet(anyString(), any())).thenThrow(new IllegalStateException("client down"));

        // 每条端点都必须把 client 失败升格为 CognitiveEngineUnavailableException（advice → 503）
        //      —— 不得吞成 200 空 body（A3 红线）
        CognitiveEngineUnavailableException ex = assertThrows(CognitiveEngineUnavailableException.class,
                () -> invokeEndpoint(methodName));
        assertEquals("COGNITIVE_UNAVAILABLE", ex.getErrorCodeString(),
                methodName + " 端点的 C-5 错误码串须为 COGNITIVE_UNAVAILABLE");
    }

    private Object invokeEndpoint(String methodName) {
        try {
            Method m = ScenarioCognitionReadController.class.getMethod(methodName, String.class, String.class);
            return m.invoke(controller, "sc-1", "mind-1");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            // 服务端异常本身是运行时异常，直接重抛
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void detectEngineDownThrowsCognitiveUnavailable() {
        when(client.detect(anyString(), any())).thenThrow(new IllegalStateException("down"));
        CognitiveEngineUnavailableException ex = assertThrows(CognitiveEngineUnavailableException.class,
                () -> controller.detect("sc-1", "mind-1"));
        // 断言这是"503 可穿透"异常，而非 200 空 body
        assertDoesNotThrow(() -> ex.getErrorCodeString());
        assertTrue(ex.getErrorCodeString() != null && !ex.getErrorCodeString().isBlank());
    }

    @Test
    void detectHappyPathReturnsSuccessBody() throws Exception {
        JsonNode payload = new ObjectMapper().readTree("{\"abnormalities\":[] }");
        when(client.detect(anyString(), any())).thenReturn(payload);
        ApiResponse<JsonNode> resp = controller.detect("sc-1", "mind-1");
        assertTrue(resp.getData() != null, "happy path 应带真实 payload（非空 body）");
    }

    private static Method findMethod(String name) throws NoSuchMethodException {
        return ScenarioCognitionReadController.class.getMethod(name, String.class, String.class);
    }
}
