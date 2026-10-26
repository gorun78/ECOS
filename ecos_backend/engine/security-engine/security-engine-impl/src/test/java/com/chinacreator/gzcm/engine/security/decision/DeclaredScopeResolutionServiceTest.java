package com.chinacreator.gzcm.engine.security.decision;

import com.chinacreator.gzcm.common.security.decision.DataScopeContract;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3e-79（差距 D-B4-36）— AI 工具通道「声明域」<b>服务端真值解析</b>的门。
 *
 * <p><b>本门要钉住的缺陷形态</b>：D-B4-29 把 {@code departmentId} 从 AI 工具入参白名单剔除
 * 之后，仓内没有任何一条腿能把 {@code projectIds} 展开成真实所属域 ⇒ 声明域只剩两种形态：
 * 不写（策略落 {@code AI_DEPARTMENT_NOT_DECLARED}）或由调用方在 {@code /decision} 的
 * {@code resource} 段自报（＝把刚关掉的洞重新打开）。本批补的解析腿必须同时满足三件事，
 * 每件都有对应判据，缺一条即"半收口冒充全收口"：</p>
 * <ol>
 *   <li><b>入参一律不信</b>：调用方带进来的部门被无条件丢弃，写进声明域的部门只可能来自属主取数；</li>
 *   <li><b>判定不猜</b>：任一项无权威归属行／空白归属／多部门歧义 ⇒ 部门留空（落"未声明"），
 *       绝不伪造一个域、绝不取其中之一；</li>
 *   <li><b>不可用与"没归属"分态</b>：寻址失败／非 2xx／信封非成功码／缺分组段／回读项目集
 *       不一致 ⇒ 抛 {@link DeclaredScopeResolutionService.ScopeSourceUnavailableException}
 *       （由调用方转 503），禁折成"解析不出域"（那是 403 语义，把部署缺陷伪装成用户没权限）。</li>
 * </ol>
 *
 * <p>另钉两条"不得扩面"的凭据：未声明项目集<b>零 HTTP 调用</b>（页面/导出与非声明 AI 调用
 * 不受本批影响），以及寻址缺位时<b>不回落到硬编码端口</b>（F02-15 两态寻址口径）。</p>
 *
 * <p>传输面用 JDK 自带 {@link HttpServer} 起本地桩（同 {@code PipelineSecurityRestTimeoutTest}
 * 一族），端口动态分配避免与本机其它测试撞端口。</p>
 */
@Timeout(60)
class DeclaredScopeResolutionServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── 判据 B：真值判定单点的三条腿（直读，不靠 HTTP 凑形态）──────

    @Test
    @DisplayName("判据B — uniqueDepartment：一致→该域；任一项缺行/空白/多部门歧义→null（禁猜）")
    void onlyAnUnambiguousDepartmentSurvives() {
        Map<String, List<String>> one = Map.of("P1", List.of("D-A"));
        assertEquals("D-A", DeclaredScopeResolutionService.uniqueDepartment(List.of("P1"), one),
                "单项单域是 §4.7.2 p1 的唯一可达形态，解析不出来就是没收口");

        // 缺行腿：声明了 P2 但 P2 没有权威归属行 ⇒ 不得用 P1 的域冒充整个请求域
        assertNull(DeclaredScopeResolutionService.uniqueDepartment(List.of("P1", "P2"), one),
                "任一项无归属行仍返回部门＝把部分事实当全部事实，越权比对会拿真值放过 P2");

        assertNull(DeclaredScopeResolutionService.uniqueDepartment(List.of("P1"),
                        Map.of("P1", List.of())), "空行集＝没有归属，不得折成任一域");
        assertNull(DeclaredScopeResolutionService.uniqueDepartment(List.of("P1"),
                        Map.of("P1", List.of("  "))), "空白部门值不是归属");
        assertNull(DeclaredScopeResolutionService.uniqueDepartment(List.of("P1"),
                        Map.of("P1", List.of("D-A", "D-B"))), "同一项目多部门＝歧义，禁取其一");
        assertNull(DeclaredScopeResolutionService.uniqueDepartment(List.of("P1", "P2"),
                        Map.of("P1", List.of("D-A"), "P2", List.of("D-B"))),
                "跨域项目集没有单一所属域；折成 D-A 会让 P2 看起来在域内");

        assertNull(DeclaredScopeResolutionService.uniqueDepartment(List.of(), one), "未声明＝不解析");
        assertNull(DeclaredScopeResolutionService.uniqueDepartment(null, one), "入参缺失＝不解析");
        assertNull(DeclaredScopeResolutionService.uniqueDepartment(List.of("P1"), null), "无读数＝不解析");

        // 归一腿：属主读数两侧带空白时取 trim 后的值（键序不影响判定）
        assertEquals("D-A", DeclaredScopeResolutionService.uniqueDepartment(
                List.of("P1", "P2"), Map.of("P1", List.of(" D-A "), "P2", List.of("D-A"))));
    }

    // ── 判据 A：入参部门不可信 ────────────────────────────

    @Test
    @DisplayName("判据A — 调用方自报的 departmentId 被丢弃，声明域部门只来自属主取数（D-B4-29 对偶）")
    void callerSuppliedDepartmentIsNeverTrusted() throws IOException {
        try (Stub stub = new Stub(envelope(okData(List.of("P1"), Map.of("P1", List.of("D-REAL")))))) {
            DeclaredScopeResolutionService svc = stub.service();
            // 调用方谎报 D-FAKE：策略侧的横向越权地板拿声明域与主体域比对，若这里照抄
            // 自报值，谎报就等于放行（D-B4-29 剔除白名单要防的正是这条）。
            DataScopeContract.Declared out = svc.resolveForAiTool(
                    new DataScopeContract.Declared("D-FAKE", List.of("P1")));

            assertEquals("D-REAL", out.departmentId(), "声明域必须来自服务端真值，自报值不得透传");
            assertEquals(List.of("P1"), out.projectIds(), "项目集原样承流（解析只补域，不改声明范围）");
        }
    }

    @Test
    @DisplayName("判据A2 — 解析不出域时如实留空（落 AI_DEPARTMENT_NOT_DECLARED），禁伪造兜底域")
    void unresolvableDepartmentStaysUndeclared() throws IOException {
        try (Stub stub = new Stub(envelope(okData(List.of("P1", "P2"),
                Map.of("P1", List.of("D-A"), "P2", List.of("D-B")))))) {
            DataScopeContract.Declared out = stub.service()
                    .resolveForAiTool(new DataScopeContract.Declared(null, List.of("P1", "P2")));

            assertNull(out.departmentId(), "跨域歧义不得折算成其中之一");
            assertTrue(out.hasProjectScope(), "项目集仍是已声明（策略侧据此落部门未声明，而非范围未声明）");
        }
    }

    // ── 判据 C：不可用与"没归属"分态 ──────────────────────

    @Test
    @DisplayName("判据C — 传输失败/非2xx/非成功码/缺段/回读不一致 ⇒ 全部显式不可用态（禁折成 403 语义）")
    void everyBrokenSourceShapeFailsClosedAsUnavailable() throws IOException {
        // 形态 1：属主根本不可达（端口未监听）
        try (ServerSocket free = new ServerSocket(0)) {
            int deadPort = free.getLocalPort();
            free.close();
            DeclaredScopeResolutionService svc =
                    new DeclaredScopeResolutionService(() -> "http://127.0.0.1:" + deadPort);
            assertUnavailable(svc, "属主不可达");
        }

        // 形态 2..5：应答形态漂移
        Map<String, Object> nonZeroCode = envelope(okData(List.of("P1"), Map.of("P1", List.of("D-A"))));
        nonZeroCode.put("code", 500);
        assertUnavailableOn(nonZeroCode, "属主非成功码不得读成\"没归属\"");

        Map<String, Object> noData = new LinkedHashMap<>();
        noData.put("code", 0);
        assertUnavailableOn(noData, "缺 data 段＝应答形态漂移");

        Map<String, Object> noByProject = new LinkedHashMap<>();
        noByProject.put("code", 0);
        noByProject.put("data", Map.of("requested", List.of("P1")));
        assertUnavailableOn(noByProject, "缺 byProject 分组段＝无法判定");

        Map<String, Object> mismatch = envelope(okData(List.of("P-OTHER"),
                Map.of("P-OTHER", List.of("D-A"))));
        assertUnavailableOn(mismatch, "属主回读项目集与请求不一致＝键面漂移，禁按半截读数裁决");

        // 形态 6：HTTP 状态行非 2xx（信封再正确也不算成功读数）
        try (Stub stub = new Stub(200, "text/plain", "not-json")) {
            assertUnavailable(stub.service(), "应答不可解析");
        }
    }

    @Test
    @DisplayName("判据C2 — 非 2xx 状态行直接不可用（禁把属主 4xx/5xx 折成\"该项目没归属\"）")
    void non2xxStatusIsUnavailableEvenWithValidEnvelope() throws IOException {
        try (Stub stub = new Stub(503, "application/json",
                "{\"code\":503,\"message\":\"datanet 内部降级\"}")) {
            assertUnavailable(stub.service(), "属主 503");
        }
    }

    // ── 判据 D：不扩面凭据 ───────────────────────────────

    @Test
    @DisplayName("判据D — 未声明项目集＝零 HTTP 调用（页面/导出与非声明 AI 调用零开销、零新风险面）")
    void undeclaredScopeDoesNotTouchTheSourceAtAll() {
        AtomicInteger calls = new AtomicInteger();
        DeclaredScopeResolutionService svc = new DeclaredScopeResolutionService(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("不该被调用");
        });

        assertEquals(DataScopeContract.none().projectIds(),
                svc.resolveForAiTool(DataScopeContract.none()).projectIds());
        assertEquals(List.of(), svc.resolveForAiTool(
                new DataScopeContract.Declared("D-FAKE", List.of())).projectIds(),
                "只报了部门没报项目集＝整段视为未声明，部门同样丢弃");
        assertEquals(List.of(), svc.resolveForAiTool(null).projectIds(), "null 入参不得 NPE");
        assertEquals(0, calls.get(), "未声明形态一次取数都不许发（否则每个非 AI 调用都多一次跨引擎调用）");
    }

    @Test
    @DisplayName("判据D2 — 寻址缺位时不回落到硬编码端口：覆写为空 + resolver 缺席 ⇒ 不可用态")
    void missingAddressingFailsLoudInsteadOfGuessingAPort() {
        DeclaredScopeResolutionService svc =
                new DeclaredScopeResolutionService("", Optional.empty());
        DeclaredScopeResolutionService.ScopeSourceUnavailableException e = assertThrows(
                DeclaredScopeResolutionService.ScopeSourceUnavailableException.class,
                () -> svc.resolveForAiTool(new DataScopeContract.Declared(null, List.of("P1"))));
        assertTrue(e.getMessage().contains(DeclaredScopeResolutionService.REASON_SOURCE_UNAVAILABLE),
                e.getMessage());
        assertTrue(e.getMessage().contains("ServiceEndpointResolver 未装配"),
                "理由必须点名缺的是哪条腿，禁读成网络故障：" + e.getMessage());

        // resolver 在场但清单没登记 datanet ⇒ 同样显式不可用（禁静默指 localhost:18082）
        ServiceEndpointResolver unregistered = mock(ServiceEndpointResolver.class);
        when(unregistered.resolve(anyString(), any())).thenReturn(Optional.empty());
        DeclaredScopeResolutionService noEntry =
                new DeclaredScopeResolutionService(null, Optional.of(unregistered));
        DeclaredScopeResolutionService.ScopeSourceUnavailableException missing = assertThrows(
                DeclaredScopeResolutionService.ScopeSourceUnavailableException.class,
                () -> noEntry.resolveForAiTool(new DataScopeContract.Declared(null, List.of("P1"))));
        assertTrue(missing.getMessage().contains("route-manifest 缺 datanet 条目"),
                missing.getMessage());
    }

    // ── 判据 E：URL 面（键名取单源 + 逐值编码）──────────────

    @Test
    @DisplayName("判据E — 请求 URL：键名取 DataScopeContract 单源、逐值 URL 编码、去重且不发空值")
    void queryFaceUsesTheSingleSourceKeyAndEncodesEachValue() throws IOException {
        AtomicReference<String> seenQuery = new AtomicReference<>("");
        try (Stub stub = new Stub(envelope(okData(List.of("P 1", "P2"),
                Map.of("P 1", List.of("D-A"), "P2", List.of("D-A")))), seenQuery)) {
            DataScopeContract.Declared out = stub.service()
                    .resolveForAiTool(new DataScopeContract.Declared(null,
                            List.of("P 1", "P2", "P 1")));
            assertEquals("D-A", out.departmentId(), out.toString());
        }
        String query = seenQuery.get();
        assertTrue(query.startsWith(DataScopeContract.KEY_PROJECT_IDS + "="),
                "查询键名必须取单源常量（禁内联 projectIds 字面量）：" + query);
        assertTrue(query.contains("P%201"), "空格未编码＝URL 注入面：" + query);
        assertTrue(query.contains("&" + DataScopeContract.KEY_PROJECT_IDS + "=P2"), query);
        assertEquals(2, query.split("&").length, "重复项应去重后只发两值：" + query);
    }

    @Test
    @DisplayName("判据E2 — 属主端点路径单源（钉死 data-engine FactController 的实际映射）")
    void ownerPathIsPinnedToTheRealMapping() {
        assertEquals("/api/v1/datanet/facts/attribution/departments",
                DeclaredScopeResolutionService.PATH_ATTRIBUTION_DEPARTMENTS,
                "路径改动必须与属主端点、gateway 路由重写表同批；本钉是为了让\"悄悄改指别的端点\"变红");
    }

    // ── 夹具 ────────────────────────────────────────────

    private static void assertUnavailable(DeclaredScopeResolutionService svc, String label) {
        DeclaredScopeResolutionService.ScopeSourceUnavailableException e = assertThrows(
                DeclaredScopeResolutionService.ScopeSourceUnavailableException.class,
                () -> svc.resolveForAiTool(new DataScopeContract.Declared(null, List.of("P1"))),
                label + " 必须落不可用态，禁折成\"解析不出域\"或放行");
        assertTrue(e.getMessage().contains(DeclaredScopeResolutionService.REASON_SOURCE_UNAVAILABLE),
                label + " 的理由必须可机读：" + e.getMessage());
    }

    private static void assertUnavailableOn(Map<String, Object> envelope, String label) throws IOException {
        try (Stub stub = new Stub(envelope)) {
            assertUnavailable(stub.service(), label);
        }
    }

    /** 属主成功信封的 data 段（与 {@code FactImportService#listAttributionDepartments} 形态同源）。 */
    private static Map<String, Object> okData(List<String> requested, Map<String, List<String>> byProject) {
        List<String> departments = new ArrayList<>(new TreeSet<>(byProject.values()
                .stream().flatMap(List::stream).toList()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requested", requested);
        data.put("byProject", byProject);
        data.put("departments", departments);
        return data;
    }

    private static Map<String, Object> envelope(Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("message", "ok");
        body.put("data", data);
        return body;
    }

    /**
     * 本地桩：单条属主端点，端口动态分配（禁写死 18082——那正是本批要防的硬编码回落形态）。
     * 默认按 {@code code=0} 成功信封应答；{@link #Stub(int, String, String)} 用于形态漂移。
     */
    private static final class Stub implements AutoCloseable {
        private final HttpServer server;
        private final String baseUrl;

        Stub(Map<String, Object> envelopeBody) throws IOException {
            this(envelopeBody, null);
        }

        Stub(Map<String, Object> envelopeBody, AtomicReference<String> querySink) throws IOException {
            this(new Response(200, "application/json",
                            MAPPER.writeValueAsString(envelopeBody).getBytes(StandardCharsets.UTF_8)),
                    querySink);
        }

        Stub(int status, String contentType, String body) throws IOException {
            this(new Response(status, contentType, body.getBytes(StandardCharsets.UTF_8)), null);
        }

        private Stub(Response response, AtomicReference<String> querySink) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(DeclaredScopeResolutionService.PATH_ATTRIBUTION_DEPARTMENTS, exchange -> {
                if (querySink != null) {
                    // 必须取 rawQuery：URI.getQuery() 是解码后的形态，拿它断"编码与否"等于自证自（%20 已被还原成空格）。
                    querySink.set(Optional.ofNullable(exchange.getRequestURI().getRawQuery()).orElse(""));
                }
                byte[] out = response.body;
                exchange.getResponseHeaders().add("Content-Type", response.contentType);
                exchange.sendResponseHeaders(response.status, out.length);
                try (var os = exchange.getResponseBody()) {
                    os.write(out);
                }
            });
            server.start();
            baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        }

        DeclaredScopeResolutionService service() {
            return new DeclaredScopeResolutionService(() -> baseUrl);
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private record Response(int status, String contentType, byte[] body) {
        }
    }
}
