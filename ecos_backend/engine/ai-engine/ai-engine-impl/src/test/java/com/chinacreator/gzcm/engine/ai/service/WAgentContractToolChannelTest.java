package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.dao.ToolDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService.ContractToolDeclaration;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService.IContractToolSource;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService.ToolResult;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * 详细设计-10 F10-19 + 差距分析 B5「工具目录双轨」验收 —— W 契约目录接进咽喉后的执行面纪律。
 *
 * <p>咽喉不变式（I-1 无条件一次裁决 / I-2 拒绝态零执行）在新通道上必须重新长出一份观测面，
 * 而不是靠注释继承：契约工具的<b>端点、方法、参数面、operation、超时预算全部来自契约声明</b>，
 * 入参不可左右 scheme/host，未声明参数不得进载荷。</p>
 *
 * <p>本类每条红线都配同装置的<b>正向对照</b>（只改声明形态、不改断言强度），避免"DENY/缺参/
 * 占位符"这类拒绝断言恒真。诊断文案不参与断言 —— 按 F06-14/X-49，{@code ToolResult.error}
 * 只有模板 + 诊断码，原文只入日志，所以可观测面是「错误分类 + 是否出向 + 出向 URL/载荷」。</p>
 *
 * <p>两条 V266 现存缺陷在此钉住（登记差距台账，禁随本批顺手改 seed）：
 * ① {@code check_metric_caliber} 路径占位符 {@code {code}} 与属性名 {@code metricCode} 不同名
 * ⇒ 出向前 fail-closed（{@link #undeclaredPathPlaceholderFailsClosed()} + 同名正向对照）；
 * ② {@code submit_readiness_task} 的 {@code /api/v1/task/submit} 不在位 Engine 前缀族
 * ⇒ 由 {@code WAgentContractToolSource} 在读取端折叠成「工具未找到」，见
 * {@code WAgentContractToolSourceTest}。</p>
 */
class WAgentContractToolChannelTest {

    private static final String USER = "u-7";
    private static final String TENANT = "t-1";
    private static final String BASE = "http://127.0.0.1:8099";
    private static final String VERSION = "1.0.0";

    private static final String PROFIT_TOOL = "query_project_profit";
    private static final String PROFIT_PATH = "/api/v1/datanet/facts/STAGE";
    private static final String PROFIT_SCHEMA = "{\"type\":\"object\","
            + "\"required\":[\"projectId\",\"period\"],\"properties\":"
            + "{\"projectId\":{\"type\":\"string\"},\"period\":{\"type\":\"string\"},"
            + "\"scope\":{\"type\":\"object\"}}}";

    /** 一套独立 mock：契约接缝 + 裁决闸 + REST 出口 + 基址，计数不被上一轮污染。 */
    private static final class Harness {
        final AgentToolPolicyGate gate = mock(AgentToolPolicyGate.class);
        final ToolSubjectResolver subject = mock(ToolSubjectResolver.class);
        final ToolDao toolDao = mock(ToolDao.class);
        final RestTemplate rest = mock(RestTemplate.class);
        final IContractToolSource source = mock(IContractToolSource.class);
        final ServiceEndpointResolver resolver = mock(ServiceEndpointResolver.class);
        final ToolExecutorService service = new ToolExecutorService();

        Harness() {
            service.setToolDao(toolDao);
            service.setPolicyGate(gate);
            service.setSubjectResolver(subject);
            service.setContractToolSource(source);
            service.setContractBaseUrl(BASE);
            service.setRestTemplate(rest);
            when(subject.resolve()).thenReturn(new ToolAdjudicationRequest.Subject(
                    USER, List.of("analyst"), "dept-a", TENANT));
            when(gate.review(any(), any(), any())).thenReturn(ToolDecision.allow("p-allow", null, 1L));
        }

        void declare(String name, String path, String method, String operation,
                     String schemaJson, Long timeoutMs) {
            when(source.findActive(name, VERSION)).thenReturn(Optional.of(new ContractToolDeclaration(
                    name, VERSION, path, method, operation, schemaJson, timeoutMs)));
        }

        void respondWith(String body) {
            when(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                    eq(String.class))).thenReturn(ResponseEntity.ok(body));
        }

        void respondWithStatus(HttpStatus status) {
            when(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                    eq(String.class))).thenThrow(new HttpClientErrorException(status));
        }

        void respondAfter(long sleepMs, String body) {
            when(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                    eq(String.class))).thenAnswer(inv -> {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ResponseEntity.ok(body);
            });
        }

        ToolResult call(String tool, Map<String, Object> args) {
            return service.execute(new ToolCall("call-1", tool, args, VERSION));
        }

        ToolResult callProfit() {
            return call(PROFIT_TOOL, profitArgs());
        }

        String outboundUrl() {
            ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
            verify(rest, times(1)).exchange(urls.capture(), any(HttpMethod.class),
                    any(HttpEntity.class), eq(String.class));
            return urls.getValue();
        }

        Object outboundBody() {
            ArgumentCaptor<HttpEntity<?>> entities = ArgumentCaptor.forClass(HttpEntity.class);
            verify(rest, times(1)).exchange(anyString(), any(HttpMethod.class),
                    entities.capture(), eq(String.class));
            return entities.getValue().getBody();
        }

        ToolAdjudicationRequest adjudication() {
            ArgumentCaptor<ToolAdjudicationRequest> requests =
                    ArgumentCaptor.forClass(ToolAdjudicationRequest.class);
            verify(gate, times(1)).review(requests.capture(), any(), any());
            return requests.getValue();
        }

        void assertNoOutboundCall() {
            verify(rest, never()).exchange(anyString(), any(HttpMethod.class),
                    any(HttpEntity.class), eq(String.class));
        }
    }

    private static Map<String, Object> profitArgs() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("projectId", "p-1");
        args.put("period", "2026-Q1");
        return args;
    }

    // ── 验收 1：契约行真的可执行（双轨收敛的行为证据） ──────────────────────

    @Test
    @DisplayName("F10-19 契约 active 版进咽喉后按声明端点出向，URL = 服务端基址 + 声明路径 + 声明参数")
    void activeContractExecutesAgainstDeclaredEndpoint() {
        Harness h = new Harness();
        h.declare(PROFIT_TOOL, PROFIT_PATH, "GET", "read", PROFIT_SCHEMA, null);
        h.respondWith("{\"rows\":[],\"count\":0}");

        Map<String, Object> args = profitArgs();
        args.put("url", "http://127.0.0.1:1/ssrf-target");
        ToolResult r = h.call(PROFIT_TOOL, args);

        assertTrue(r.isSuccess(), "契约工具应可执行: " + r.getErrorCode());
        assertNotNull(r.getContent());
        assertEquals(BASE + PROFIT_PATH + "?projectId=p-1&period=2026-Q1", h.outboundUrl(),
                "入参不得左右 scheme/host，未声明参数不得进 query");
        verify(h.gate, times(1)).review(any(), any(), any());
        verify(h.toolDao, never()).queryForList(anyString(), any());
    }

    @Test
    @DisplayName("B5-1 基址未显式覆盖时经 ServiceEndpointResolver 取 gateway 承流（禁自拼各 service 端口）")
    void baseUrlFallsBackToGatewayOwnerFromRouteManifest() {
        Harness h = new Harness();
        h.service.setContractBaseUrl("");
        when(h.resolver.resolve("gateway", ServiceEndpointResolver.RouteMode.SERVICE))
                .thenReturn(Optional.of("http://localhost:18080/"));
        h.service.setEndpointResolver(h.resolver);
        h.declare(PROFIT_TOOL, PROFIT_PATH, "GET", "read", PROFIT_SCHEMA, null);
        h.respondWith("{\"ok\":true}");

        ToolResult r = h.callProfit();

        assertTrue(r.isSuccess(), "route-manifest 命中 gateway 时应可执行: " + r.getErrorCode());
        assertEquals("http://localhost:18080" + PROFIT_PATH + "?projectId=p-1&period=2026-Q1",
                h.outboundUrl(), "尾斜杠必须归一，且基址唯一来源 = gateway 承流");
        verify(h.resolver, times(1)).resolve("gateway", ServiceEndpointResolver.RouteMode.SERVICE);
    }

    @Test
    @DisplayName("B5-1 覆盖项与 route-manifest 双双不可得 → fail-loud 不出向（禁静默回落 localhost）")
    void missingBaseUrlIsFailLoudNotSilentLocalhost() {
        Harness h = new Harness();
        h.service.setContractBaseUrl("");
        h.declare(PROFIT_TOOL, PROFIT_PATH, "GET", "read", PROFIT_SCHEMA, null);
        h.respondWith("{\"ok\":true}");

        ToolResult r = h.callProfit();

        assertFalse(r.isSuccess(), "基址无法解析时不得回落硬编码地址");
        h.assertNoOutboundCall();
    }

    // ── 验收 2：参数面收口（契约通道不得退化成通用 invoke_rest） ─────────────

    @Test
    @DisplayName("F10-19 POST 载荷只带 input_schema.properties 声明的参数（url/sql 型入参丢弃）")
    void outboundPayloadCarriesOnlyDeclaredArguments() {
        Harness h = new Harness();
        String draftSchema = "{\"type\":\"object\",\"required\":[\"goalId\",\"label\"],"
                + "\"properties\":{\"goalId\":{\"type\":\"string\"},\"label\":{\"type\":\"string\"}}}";
        h.declare("create_decision_option", "/api/v1/wagent/candidates", "POST", "write", draftSchema, null);
        h.respondWith("{\"candidateId\":\"c-1\"}");

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("goalId", "g-9");
        args.put("label", "降本选项");
        args.put("url", "http://127.0.0.1:1/ssrf-target");
        args.put("sql", "DROP TABLE t_contract");
        ToolResult r = h.call("create_decision_option", args);

        assertTrue(r.isSuccess(), "候选写工具应经契约通道出向: " + r.getErrorCode());
        assertEquals(BASE + "/api/v1/wagent/candidates", h.outboundUrl());
        Object body = h.outboundBody();
        assertTrue(body instanceof Map, "POST 形态必须编成 JSON 载荷: " + body);
        assertEquals(Set.of("goalId", "label"), ((Map<?, ?>) body).keySet(),
                "未声明参数进载荷 = 通用 invoke_rest 复辟");
    }

    @Test
    @DisplayName("F10-19 缺必填参数在出向之前拒绝（INVALID_ARGUMENT，不打后端换 400）")
    void missingRequiredArgumentIsRefusedBeforeOutbound() {
        Harness h = new Harness();
        h.declare(PROFIT_TOOL, PROFIT_PATH, "GET", "read", PROFIT_SCHEMA, null);
        h.respondWith("{\"rows\":[]}");

        ToolResult r = h.call(PROFIT_TOOL, Map.of("projectId", "p-1"));

        assertFalse(r.isSuccess());
        assertEquals("INVALID_ARGUMENT", r.getErrorClass());
        h.assertNoOutboundCall();
    }

    // ── 验收 3：V266 占位符缺陷的 fail-closed（D-B5-1） ─────────────────────

    @Test
    @DisplayName("D-B5-1 {code} 与 metricCode 不同名：fail-closed 拒绝出向，禁把字面量打到后端换 404")
    void undeclaredPathPlaceholderFailsClosed() {
        Harness h = new Harness();
        h.declare("check_metric_caliber", "/api/v1/ontology/calibers/{code}/current",
                "GET", "read", PROFIT_SCHEMA, null);
        h.respondWith("{\"caliber\":\"x\"}");

        ToolResult r = h.call("check_metric_caliber", profitArgs());

        assertFalse(r.isSuccess(), "目录行缺陷必须拒绝执行，不可退化为一次后端 404");
        assertEquals("INVALID_ARGUMENT", r.getErrorClass());
        h.assertNoOutboundCall();
    }

    @Test
    @DisplayName("D-B5-1 正向对照：占位符与属性同名时路径被真实填充（证明上一条不是恒真断言）")
    void declaredPlaceholderIsFilledFromArguments() {
        Harness h = new Harness();
        String caliberSchema = "{\"type\":\"object\",\"required\":[\"metricCode\"],"
                + "\"properties\":{\"metricCode\":{\"type\":\"string\"}}}";
        h.declare("check_metric_caliber", "/api/v1/ontology/calibers/{metricCode}/current",
                "GET", "read", caliberSchema, null);
        h.respondWith("{\"caliberVersion\":\"v3\"}");

        ToolResult r = h.call("check_metric_caliber", Map.of("metricCode", "CAL-001"));

        assertTrue(r.isSuccess(), "同名占位符应正常出向: " + r.getErrorCode());
        assertEquals(BASE + "/api/v1/ontology/calibers/CAL-001/current", h.outboundUrl());
    }

    // ── 验收 4：裁决词表与分态口径在契约通道上的复现 ────────────────────────

    @Test
    @DisplayName("F10-19 operation 取契约声明值：GET 面声明 write 时不得按 HTTP 方法推断成 read")
    void operationComesFromDeclarationNotHttpMethod() {
        Harness h = new Harness();
        h.declare("freeze_data_snapshot", "/api/v1/datanet/facts/FORECAST_SNAPSHOT/import",
                "GET", "write", PROFIT_SCHEMA, null);
        h.respondWith("{\"snapshotId\":\"s-1\"}");

        h.call("freeze_data_snapshot", profitArgs());

        assertEquals("write", h.adjudication().action().operation(),
                "写判成读 = 绕过 OPA 写权限面（F06-11 同一条论证）");
    }

    @Test
    @DisplayName("F06-01 契约工具 DENY/FAIL_CLOSED：各恰 1 次裁决且零出向（I-1/I-2 复现到新通道）")
    void deniedContractToolMakesNoOutboundCall() {
        for (ToolDecision verdict : List.of(
                ToolDecision.deny("策略拒绝", "p-deny", 1L),
                ToolDecision.failClosed("security-engine 不可用", 1L))) {
            Harness h = new Harness();
            h.declare(PROFIT_TOOL, PROFIT_PATH, "GET", "read", PROFIT_SCHEMA, null);
            h.respondWith("{\"rows\":[]}");
            when(h.gate.review(any(), any(), any())).thenReturn(verdict);

            ToolResult r = h.callProfit();

            assertFalse(r.isSuccess(), "拒绝态不得执行");
            verify(h.gate, times(1)).review(any(), any(), any());
            h.assertNoOutboundCall();
        }
    }

    @Test
    @DisplayName("F10-14 契约端点 404 → STUB_CAPABILITY(501 不重试)，禁洗成 502 可重试抖动")
    void contractEndpointNotFoundMapsToStubCapability() {
        Harness h = new Harness();
        h.declare("run_profit_forecast", "/api/v1/cognitive/forecast", "POST", "write",
                PROFIT_SCHEMA, null);
        h.respondWithStatus(HttpStatus.NOT_FOUND);

        ToolResult r = h.call("run_profit_forecast", profitArgs());

        assertFalse(r.isSuccess());
        assertEquals("STUB_CAPABILITY", r.getErrorClass(),
                "缺口态不得落 TOOL_BACKEND_ERROR（可重试会诱导无谓重试）");
    }

    @Test
    @DisplayName("F10-17 无版本调用不参与契约解析：不猜最新版、不回落其它定义")
    void versionlessCallNeverConsultsContractCatalog() {
        Harness h = new Harness();
        h.declare(PROFIT_TOOL, PROFIT_PATH, "GET", "read", PROFIT_SCHEMA, null);
        h.respondWith("{\"rows\":[]}");

        ToolResult r = h.service.execute(new ToolCall("call-8", PROFIT_TOOL, profitArgs()));

        assertFalse(r.isSuccess(), "无版本无法定位 active 契约，必须拒绝");
        assertEquals("INVALID_ARGUMENT", r.getErrorClass());
        verify(h.source, never()).findActive(anyString(), anyString());
        h.assertNoOutboundCall();
    }

    @Test
    @DisplayName("F10-17 接缝判定为空（非 active／前缀不可归属）→ 工具未找到，禁另拼一份定义")
    void emptyContractLookupLeavesToolUnresolved() {
        Harness h = new Harness();
        when(h.source.findActive(eq(PROFIT_TOOL), anyString())).thenReturn(Optional.empty());
        h.respondWith("{\"rows\":[]}");

        ToolResult r = h.callProfit();

        assertFalse(r.isSuccess());
        assertEquals("INVALID_ARGUMENT", r.getErrorClass());
        verify(h.source, times(1)).findActive(PROFIT_TOOL, VERSION);
        h.assertNoOutboundCall();
        verify(h.toolDao, never()).queryForList(anyString(), any());
    }

    // ── 验收 5：超时预算取契约声明值 ────────────────────────────────────────

    @Test
    @DisplayName("B5-1 契约 timeout_ms 即执行预算：1s 声明不得按咽喉 30s 放行")
    void executionBudgetComesFromContractTimeout() {
        Harness h = new Harness();
        h.declare(PROFIT_TOOL, PROFIT_PATH, "GET", "read", PROFIT_SCHEMA, 1000L);
        h.respondAfter(4000L, "{\"rows\":[]}");

        long startMs = System.currentTimeMillis();
        ToolResult r = h.callProfit();
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertFalse(r.isSuccess(), "超声明预算必须失败，不可等后端返回成功");
        assertEquals("TOOL_BACKEND_ERROR", r.getErrorClass());
        assertTrue(elapsedMs < 3000L, "实际等待应≈声明预算(1s)，不应拖到咽喉上限 30s: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("B5-1 正向对照：声明预算内的慢响应照常成功（预算判定不是恒失败）")
    void responseWithinDeclaredBudgetSucceeds() {
        Harness h = new Harness();
        h.declare(PROFIT_TOOL, PROFIT_PATH, "GET", "read", PROFIT_SCHEMA, 1000L);
        h.respondAfter(400L, "{\"rows\":[],\"count\":0}");

        ToolResult r = h.callProfit();

        assertTrue(r.isSuccess(), "400ms 应落在 1s 预算内: " + r.getErrorCode());
    }
}
