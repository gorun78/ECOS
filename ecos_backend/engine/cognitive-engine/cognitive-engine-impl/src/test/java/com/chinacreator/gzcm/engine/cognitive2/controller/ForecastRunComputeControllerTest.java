package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunComputeService;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunComputeService.ComputeRequest;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预测 run REST 面单测（差距分析 P0-4 / 详设-09 §十一 H-5 认知侧半区）。
 *
 * <p>三件事逐条验：
 * ① <b>路径单源不破</b> —— 类级前缀必须是 {@link CognitiveApiPaths#COGNITIVE}，方法级值必须是
 *    {@code *_REL} 相对段；SUP-D 记录过的"方法级写绝对路径 ⇒ Spring 拼成双前缀"缺陷必须有回归钉；
 * ② <b>透传不吞</b> —— 服务层异常一律原样上抛交 {@code CognitiveErrorAdvice} 出码，
 *    本层禁 catch 后造 200（X-44）；
 * ③ <b>未命中不是 404</b> —— 幂等键未命中回 {@code code=0 + data=null}，
 *    让编排面走"首次运行"正常分支。</p>
 */
class ForecastRunComputeControllerTest {

    private final ForecastRunComputeService computeService = mock(ForecastRunComputeService.class);
    private final ForecastRunComputeController controller = new ForecastRunComputeController(computeService);

    /** 方法名 → 期望完整模板（与 CognitiveApiPaths SUP-E 段逐字对齐）。 */
    @Test
    @DisplayName("映射值全部来自 CognitiveApiPaths 相对段：类前缀 + 方法段 = 冻结模板（无双前缀）")
    void mappingsComeFromPathConstantsWithoutDoublePrefix() {
        RequestMapping classLevel = ForecastRunComputeController.class.getAnnotation(RequestMapping.class);
        assertEquals(CognitiveApiPaths.COGNITIVE, classLevel.value()[0]);

        Map<String, String> expected = Map.of(
                "compute", CognitiveApiPaths.FORECAST_RUNS_COMPUTE,
                "getRun", CognitiveApiPaths.FORECAST_RUNS_ITEM,
                "getByIdempotencyKey", CognitiveApiPaths.FORECAST_RUNS_BY_KEY,
                "results", CognitiveApiPaths.FORECAST_RUNS_RESULTS,
                "evidence", CognitiveApiPaths.FORECAST_RUNS_EVIDENCE);
        for (Method method : ForecastRunComputeController.class.getDeclaredMethods()) {
            String segment = mappingValueOf(method);
            if (segment == null) {
                continue;
            }
            String absolute = CognitiveApiPaths.COGNITIVE + segment;
            String want = expected.get(method.getName());
            assertTrue(want != null, "未登记的映射方法: " + method.getName());
            assertEquals(want, absolute, method.getName() + " 的完整模板漂移");
            // 双前缀缺陷钉：方法级值不得自带 /api/ 段
            assertTrue(segment.startsWith("/"), segment);
            assertTrue(!segment.contains("/api/"), "方法级必须用 *_REL 相对段，禁绝对路径拼双前缀: " + segment);
        }
        assertEquals(5, expected.size(), "端点族数量不得漂移（compute + 3 读 + evidence）");
    }

    private static String mappingValueOf(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            return get.value()[0];
        }
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) {
            return post.value()[0];
        }
        return null;
    }

    /** 真实 record 实例（record 属终态类型，禁 mock 依赖 mock-maker 配置）。 */
    private static ComputeRequest sampleRequest() {
        return new ComputeRequest(null, "scn-09", "FORECAST", "cal-2026", "v3",
                "2026-10-01T00:00:00", "snap-77", "f-12", "caller-key-1", "caller-hash-1",
                null, "trace-1", null, null, null);
    }

    @Test
    @DisplayName("compute：服务视图原样装入 data，不重包装、不裁剪字段")
    void computeWrapsServiceView() {
        Map<String, Object> view = Map.of("forecastRunId", "run-1", "idempotencyKey", "caller-key-1");
        ComputeRequest request = sampleRequest();
        when(computeService.compute(request)).thenReturn(view);

        ApiResponse<Map<String, Object>> resp = controller.compute(request);

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertSame(view, resp.getData());
        verify(computeService).compute(request);
    }

    @Test
    @DisplayName("幂等键未命中 ⇒ code=0 + data=null（首次运行正常分支，禁 404 掩蔽）")
    void idempotencyMissIsSuccessWithNullData() {
        when(computeService.findByIdempotencyKey("idem-new")).thenReturn(null);

        ApiResponse<Map<String, Object>> resp = controller.getByIdempotencyKey("idem-new");

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertNull(resp.getData());
    }

    @Test
    @DisplayName("服务层异常原样上抛交 advice 出码（本层禁 catch 后造 200）")
    void guardExceptionsPropagateForAdviceRendering() {
        when(computeService.findRun("run-x")).thenThrow(new CognitiveGuardException(
                CognitiveErrorCode.E404_FORECAST_RUN, "run 不存在"));
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> controller.getRun("run-x"));
        assertEquals(CognitiveErrorCode.E404_FORECAST_RUN, e.errorCode);

        when(computeService.compute(any())).thenThrow(new CognitiveGuardException(
                CognitiveErrorCode.E400_INVALID_PARAM, "无事实禁造产物"));
        assertThrows(CognitiveGuardException.class, () -> controller.compute(sampleRequest()));
    }

    @Test
    @DisplayName("results/evidence：读面直通（三值 null 与证据集都不在 REST 层加工）")
    void readEndpointsDelegateVerbatim() {
        Map<String, Object> results = new java.util.LinkedHashMap<>();
        results.put("p10", null);
        results.put("p50", null);
        results.put("p90", null);
        results.put("quantileSource", "NOT_AVAILABLE");
        when(computeService.readResults("run-1")).thenReturn(results);
        assertSame(results, controller.results("run-1").getData());
        assertNull(results.get("p50"), "REST 层不得给缺位三值补任何值");

        Map<String, Object> evidence = Map.of("evidenceRefs", java.util.List.of("ev-1"));
        when(computeService.readEvidence("run-1")).thenReturn(evidence);
        assertSame(evidence, controller.evidence("run-1").getData());
    }
}
