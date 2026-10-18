package com.chinacreator.gzcm.engine.ai.tool;

import com.chinacreator.gzcm.engine.ai.dao.ToolDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService.ToolResult;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-11 集成验收（doc 冻结标识：{@code eachToolProducesExactlyOneOpaDecision}、
 * {@code actionCreateDraftYieldsDraftStateOnly}）。
 *
 * <p>与 {@code ToolGuardrailChokepointTest} 的分工：那个类钉"每种工具来源都过裁决咽喉"，
 * 本类钉"场景受控工具这一条新通道"上的两条契约 —— 一次调用恰一条 OPA 裁决（I-1）、
 * 写工具只产出 DRAFT 态载荷（PRD-06 §4.1）。</p>
 *
 * <p>出向用桩 RestTemplate：断言的是真上出去的 URL/方法/载荷，不是内部变量。
 * 真实后端（workspace 预测运行/动作服务）尚未落地，端到端连通不在本类范围，
 * 详见验收记录的「未执行」清单。</p>
 */
class ScenarioToolInvocationIT {

    private static final String BASE_URL = "http://127.0.0.1:18090";
    /** 覆盖项夹具（假 host，非本机环境值，仅验证优先级）。 */
    private static final String OVERRIDE_BASE_URL = "http://scenario-base-url.from-override";
    private static final String USER = "u-scenario";
    private static final String TENANT = "t-scenario";

    /** 引用元数据齐全的返回体（F06-11 要点 4：每个工具返回体自带引用元数据）。 */
    private static final String CITED_BODY =
            "{\"forecastRunId\":\"run-7\",\"caliberId\":\"cal-revenue\","
                    + "\"version\":\"3\",\"asOfTime\":\"2026-10-01T00:00:00Z\","
                    + "\"evidenceLinks\":[\"http://minio.local/evidence/run-7.pdf\"],"
                    + "\"rows\":[{\"year\":2026,\"amount\":120000.00}]}";

    private RestTemplate restTemplate;
    private AgentToolPolicyGate gate;
    private ToolExecutorService service;

    // ── 验收 1 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F06-11 I-1：五个受控工具每次调用恰好一条 OPA 裁决、一次声明端点出向")
    void eachToolProducesExactlyOneOpaDecision() {
        Map<String, Map<String, Object>> calls = validArgumentsByTool();

        for (Map.Entry<String, Map<String, Object>> call : calls.entrySet()) {
            setUpFixture();
            ToolResult r = service.execute(call.getKey(), call.getValue());

            assertNotNull(r, "调用必须有结果: " + call.getKey());
            verify(gate, times(1)).review(any(), any(), any());
            verify(restTemplate, times(1))
                    .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));

            ArgumentCaptor<ToolAdjudicationRequest> captor =
                    ArgumentCaptor.forClass(ToolAdjudicationRequest.class);
            verify(gate).review(captor.capture(), any(), any());
            ToolAdjudicationRequest issued = captor.getValue();
            assertEquals(call.getKey(), issued.tool(), "裁决载荷的工具名必须是被调用的受控工具");
            ToolAdjudicationRequest.Action action = issued.action();
            assertNotNull(action, "动作段必须齐备");
            assertEquals(ToolAdjudicationRequest.ACTION_TYPE, action.type());
            assertEquals(ScenarioToolCatalog.find(call.getKey()).operation(), action.operation(),
                    "operation 取声明值、禁按参数或 HTTP 方法猜测（forecast.explain 走 POST 仍属 read）: "
                            + call.getKey());
            assertTrue(r.isSuccess(), "ALLOW + 后端正常返回时必须成功: "
                    + call.getKey() + " err=" + r.getError());
        }
    }

    // ── 验收 2 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F06-11 要点 2：action.createDraft 出向载荷只可能是 DRAFT，身份字段不进请求体")
    void actionCreateDraftYieldsDraftStateOnly() {
        setUpFixture();

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("scenarioId", "s-9");
        arguments.put("owner", "lisi");
        arguments.put("dueDate", "2026-12-31");
        arguments.put("expectedImpact", "120000.00");
        arguments.put("controlMetric", "revenue");
        // 模型试图越权：自带终态与审批人（PRD-06 §4.1 明令审批走人工流）
        arguments.put("status", "APPROVED");
        arguments.put("approver", "boss");
        arguments.put("undeclaredField", "should-not-travel");

        ToolResult r = service.execute(ScenarioToolCatalog.ACTION_CREATE_DRAFT, arguments);

        assertTrue(r.isSuccess(), String.valueOf(r.getError()));
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpMethod> methodCaptor = ArgumentCaptor.forClass(HttpMethod.class);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(1)).exchange(urlCaptor.capture(), methodCaptor.capture(),
                entityCaptor.capture(), eq(String.class));

        assertEquals(HttpMethod.POST, methodCaptor.getValue());
        assertEquals(BASE_URL + "/api/v1/workspace/scenarios/s-9/actions", urlCaptor.getValue(),
                "URL 只由服务端基址 + 声明路径构成，入参只能填占位符");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) entityCaptor.getValue().getBody();
        assertNotNull(payload, "写工具必须带 JSON 载荷");
        assertEquals(ScenarioToolCatalog.DRAFT_STATE, payload.get("status"),
                "状态强制 DRAFT：模型给的终态必须被收口: " + payload);
        assertFalse(payload.containsKey("approver"), "审批人只从 token 主体取: " + payload);
        assertFalse(payload.containsKey("undeclaredField"), "未声明参数不得透传到后端: " + payload);
        assertEquals("lisi", payload.get("owner"));
        assertEquals("2026-12-31", payload.get("dueDate"));

        // 引用元数据齐全 → 结果自带引用块（供 F06-12 逐字引用）
        assertNotNull(r.getCitationBlock(), "写工具同样必须带回引用块");
        assertTrue(r.getContent().contains(ToolCitation.PREFIX),
                "引用块必须随结果下发，禁只存字段不给文本: " + r.getContent());
    }

    // ── 装置 ────────────────────────────────────────────────────────────────

    // ── 验收 3：F02-15 两态寻址单源收口（2026-10-11）─────────────────────────

    @Test
    @DisplayName("F02-15：未显式覆盖时基址经 ServiceEndpointResolver(workspace, SERVICE) 解析并归一张尾斜杠")
    void scenarioBaseUrlComesFromRouteManifestWhenNotOverridden() {
        restTemplate = mock(RestTemplate.class);
        ServiceEndpointResolver resolver = mock(ServiceEndpointResolver.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(String.class))).thenReturn(ResponseEntity.ok(CITED_BODY));
        when(resolver.resolve(eq(ToolExecutorService.SCENARIO_OWNER_SERVICE),
                eq(ServiceEndpointResolver.RouteMode.SERVICE)))
                .thenReturn(Optional.of(BASE_URL + "/"));

        service = newService();
        service.setEndpointResolver(resolver);

        ToolResult r = service.execute(ScenarioToolCatalog.FORECAST_QUERY,
                Map.of("runId", "run-7", "year", 2026, "level", "dept"));

        assertTrue(r.isSuccess(), String.valueOf(r.getError()));
        verify(resolver, times(1)).resolve(ToolExecutorService.SCENARIO_OWNER_SERVICE,
                ServiceEndpointResolver.RouteMode.SERVICE);
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(1)).exchange(url.capture(), any(HttpMethod.class),
                any(HttpEntity.class), eq(String.class));
        assertTrue(url.getValue().startsWith(BASE_URL + "/api/"),
                "出向基址必须取 resolver 值（尾斜杠归一，禁双斜杠）: " + url.getValue());
    }

    @Test
    @DisplayName("F02-15：显式覆盖项优先于 route-manifest，覆盖在位时 resolver 不参与寻址")
    void explicitOverrideTakesPrecedenceOverRouteManifest() {
        restTemplate = mock(RestTemplate.class);
        ServiceEndpointResolver resolver = mock(ServiceEndpointResolver.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(String.class))).thenReturn(ResponseEntity.ok(CITED_BODY));

        service = newService();
        service.setEndpointResolver(resolver);
        service.setScenarioBaseUrl(OVERRIDE_BASE_URL);

        ToolResult r = service.execute(ScenarioToolCatalog.FORECAST_QUERY,
                Map.of("runId", "run-7", "year", 2026, "level", "dept"));

        assertTrue(r.isSuccess(), String.valueOf(r.getError()));
        verify(resolver, never()).resolve(anyString(), any(ServiceEndpointResolver.RouteMode.class));
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(1)).exchange(url.capture(), any(HttpMethod.class),
                any(HttpEntity.class), eq(String.class));
        assertTrue(url.getValue().startsWith(OVERRIDE_BASE_URL + "/api/"),
                "显式覆盖项是运维逃生口，必须压过 manifest: " + url.getValue());
    }

    @Test
    @DisplayName("F02-15：resolver 未装配且无覆盖 → fail-loud 分类失败，禁静默回落硬编码地址后出向")
    void unresolvableScenarioBaseUrlFailsLoudWithoutEgress() {
        restTemplate = mock(RestTemplate.class);

        service = newService();

        ToolResult r = service.execute(ScenarioToolCatalog.FORECAST_QUERY,
                Map.of("runId", "run-7", "year", 2026, "level", "dept"));

        assertFalse(r.isSuccess(), "基址不可解析必须失败，禁回落 localhost 字面量继续出向");
        assertEquals("TOOL_BACKEND_ERROR", r.getErrorClass(), String.valueOf(r.getError()));
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class),
                any(HttpEntity.class), eq(String.class));
    }

    // ── 验收 5：D-B5-5 query 组装的"已消费"判据 ────────────────────────────────

    @Test
    @DisplayName("D-B5-5：路径占位符已消费的参数不得再重复编进 query string（判据取替换前的模板路径）")
    void pathConsumedArgumentIsNotDuplicatedIntoQuery() {
        restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(String.class))).thenReturn(ResponseEntity.ok(CITED_BODY));

        service = newService();
        service.setScenarioBaseUrl(BASE_URL);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("runId", "run-7");
        args.put("year", 2026);
        args.put("level", "dept");
        ToolResult r = service.execute(ScenarioToolCatalog.FORECAST_QUERY, args);

        assertTrue(r.isSuccess(), String.valueOf(r.getError()));
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(1)).exchange(url.capture(), any(HttpMethod.class),
                any(HttpEntity.class), eq(String.class));
        // 同一值出现在路径与 query 两处 ⇒ 后端可读到一个被填充、一个被忽略的 runId（口径漂移）
        assertEquals(BASE_URL + "/api/v1/workspace/forecast-runs/run-7/results?year=2026&level=dept",
                url.getValue(), "出向 URL 精确形态：路径填 runId，query 只带其余声明参数");
    }

    /** 除基址面外的通用装置（restTemplate 桩由调用方按需替换/注入）。 */
    private ToolExecutorService newService() {
        gate = mock(AgentToolPolicyGate.class);
        ToolSubjectResolver subject = mock(ToolSubjectResolver.class);
        when(gate.review(any(), any(), any())).thenReturn(ToolDecision.allow("p-scenario", null, 1L));
        when(subject.resolve()).thenReturn(
                new ToolAdjudicationRequest.Subject(USER, List.of("scenario-operator"), "dept-s", TENANT));

        ToolExecutorService svc = new ToolExecutorService();
        svc.setToolDao(mock(ToolDao.class));
        svc.setPolicyGate(gate);
        svc.setSubjectResolver(subject);
        svc.setScenarioRegistry(new ScenarioToolRegistry(null));
        ReflectionTestUtils.setField(svc, "restTemplate", restTemplate);
        return svc;
    }

    private void setUpFixture() {
        restTemplate = mock(RestTemplate.class);
        gate = mock(AgentToolPolicyGate.class);
        ToolSubjectResolver subject = mock(ToolSubjectResolver.class);
        ToolDao toolDao = mock(ToolDao.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(String.class))).thenReturn(ResponseEntity.ok(CITED_BODY));
        when(gate.review(any(), any(), any())).thenReturn(ToolDecision.allow("p-scenario", null, 1L));
        when(subject.resolve()).thenReturn(
                new ToolAdjudicationRequest.Subject(USER, List.of("scenario-operator"), "dept-s", TENANT));

        service = new ToolExecutorService();
        service.setToolDao(toolDao);
        service.setPolicyGate(gate);
        service.setSubjectResolver(subject);
        service.setScenarioRegistry(new ScenarioToolRegistry(null));
        service.setScenarioBaseUrl(BASE_URL);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
    }

    // ── 验收 4：分册 09 v1.2 §十一 移交项的 404 诊断 ────────────────────────────

    @Test
    @DisplayName("分册 09 移交项：声明端点未上线(404) → 真实 STUB_CAPABILITY(501)，缺口原文只入日志，禁伪成功")
    void pendingHandoverEndpoint404ReportsCapabilityNotLive() {
        restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(String.class))).thenThrow(new HttpClientErrorException(
                        org.springframework.http.HttpStatus.NOT_FOUND));

        service = newService();
        service.setScenarioBaseUrl(BASE_URL);

        ToolResult r = service.execute(ScenarioToolCatalog.FORECAST_QUERY,
                Map.of("runId", "run-7", "year", 2026, "level", "dept"));

        assertFalse(r.isSuccess(), "移交项未上线必须失败，禁 200 空数据冒充（X-50）");
        // 归 STUB_CAPABILITY 而非 TOOL_BACKEND_ERROR：后者模板是"数据服务异常请稍后重试"且可重试 1 次，
        // 会把一个不存在的能力报成临时抖动；501 = 尚未上线 + RetryKind.NONE
        assertEquals("STUB_CAPABILITY", r.getErrorClass(), String.valueOf(r.getError()));
        assertTrue(String.valueOf(r.getError()).contains("该能力尚未上线"),
                "对外必须点明能力未上线: " + r.getError());
        // X-49：404 原文与移交项定位（含"分册 09"）只进日志，响应面只有分类模板 + 诊断码
        assertFalse(String.valueOf(r.getError()).contains("分册 09"),
                "缺口原文禁进对外响应面: " + r.getError());
        assertNotNull(ScenarioToolCatalog.missingBackendHint(ScenarioToolCatalog.FORECAST_QUERY),
                "日志侧必须能定位所属移交项（属主册 + 缺口名）");
        // 裁决面不受影响：分类修正只改错误形态，仍是一次调用恰一条裁决
        verify(gate, times(1)).review(any(), any(), any());
    }

    /** 五个工具的合法入参（必填齐全 + 路径占位符有值）。 */
    private static Map<String, Map<String, Object>> validArgumentsByTool() {
        Map<String, Map<String, Object>> calls = new LinkedHashMap<>();
        calls.put(ScenarioToolCatalog.FORECAST_QUERY, Map.of(
                "runId", "run-7", "year", 2026, "level", "dept"));
        calls.put(ScenarioToolCatalog.FORECAST_DRILLDOWN, Map.of(
                "runId", "run-7", "level", "project", "entityId", "p-1"));
        calls.put(ScenarioToolCatalog.FORECAST_EXPLAIN, Map.of(
                "runId", "run-7", "projectId", "p-1", "period", "2026-Q3"));
        calls.put(ScenarioToolCatalog.SCENARIO_COPY_COMPARE, Map.of(
                "baselineRunId", "run-7", "overrides", Map.of("revenue.growth", "0.05")));
        calls.put(ScenarioToolCatalog.ACTION_CREATE_DRAFT, Map.of(
                "scenarioId", "s-9", "owner", "lisi", "dueDate", "2026-12-31",
                "expectedImpact", "120000.00", "controlMetric", "revenue"));
        return calls;
    }
}
