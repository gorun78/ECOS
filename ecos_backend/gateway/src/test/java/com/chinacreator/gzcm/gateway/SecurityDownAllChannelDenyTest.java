package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.AgentToolSqlWhitelist;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.security.GuardrailAuditPublisher;
import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
import com.chinacreator.gzcm.workspace.exception.WorkspaceExceptionHandler;
import com.chinacreator.gzcm.workspace.forecast.ForecastAuditPackService;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import com.chinacreator.gzcm.workspace.security.AbacQueryFilter;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient;

/**
 * 详细设计-01 §4.7.1「三通道全 DENY」门禁（从零实施版 line 2901 / §4.7.2 line 2911
 * "security 不可用（全角色）"格）—— security-engine 进程不可用时，页面 / 导出 / AI
 * 三条通道一律拒绝且<b>不得降级放行</b>。验收标识：
 * {@code mvn -o test -pl :gateway -Dtest='SecurityDownAllChannelDenyTest#page+#export+#ai'}。
 *
 * <h3>为什么此前是一个真缺口（不是缺测试）</h3>
 * <p>三条通道的兜底形态原先都是<b>把可用性故障冒充成策略拒绝</b>：
 * {@link WorkspaceSecurityEngineClient} 的传输层 catch 把连不上 / 超时 / 5xx 与
 * security 正常应答后的 DENY 一并折叠成 {@code "1=0"} / 空列集 / {@code false}，
 * 于是页面通道得到 HTTP 200 + 空列表（从零实施版 line 2602「禁渲染空列表冒充无数据」
 * 的失败判据），读咽喉 {@code ScenarioReadGuard} 又把"引擎坏了"报成 403"你没权限"。
 * 既有 {@code AbacQueryFilterDenyTest} / {@code GuardrailFailClosedTest} 都是在
 * <b>客户端已折叠之后</b>的层面断言，因此看不见这个折叠动作本身 —— 门禁虚位的根因在此。</p>
 *
 * <h3>本批改造的载体（唯一分类点）</h3>
 * <ul>
 *   <li>{@link WorkspaceSecurityEngineClient#postJson}：{@code ResourceAccessException}
 *       （连不上/读超时）与 {@code HttpServerErrorException}（security 侧 5xx）
 *       单点转 {@link SecurityEngineUnavailableException}（HTTP 503 / ECOS-SEC-501）上抛；
 *       4xx 与响应缺段仍走既有 DENY 兜底 —— <b>不可用 ≠ 无权限</b>（§4.2 / F06-14 同源口径）；</li>
 *   <li>页面通道：{@link AbacQueryFilter} → {@link ScenarioReadGuard} →
 *       {@link WorkspaceExceptionHandler} 全链不再吞掉该异常，
 *       两个查询面（{@code ObjectController#search} 逐表 catch、
 *       {@code ObjectQLController#query} 整查询 catch）各自单独 rethrow（见
 *       {@link #pageChannelSearchSurfaceKeepsAvailabilityLoud()} 的源码级钉）；</li>
 *   <li>导出通道：{@link ForecastAuditPackService#buildAuditPack} 的
 *       {@code audit:export} 裁决门在取数之前，故障态直接上抛，不产出任何字节；</li>
 *   <li>AI 通道：{@link AgentToolPolicyGate} 既有 FAIL_CLOSED 三态（本类只做接线核验，
 *       深度断言见 ai-engine {@code AgentToolPolicyGateTest} / {@code GuardrailFailClosedTest}）。</li>
 * </ul>
 *
 * <h3>载体模块落差（诚实登记）</h3>
 * <p>设计写的是 {@code -pl gateway}，三条通道的生产代码实际分属
 * workspace-impl（页面/导出）与 ai-engine-impl（AI）。本类落在 gateway 的
 * 测试面是可行的，因为 gateway 是唯一 monolith fat-JAR、编译期聚合两个 impl 模块
 * （{@code gateway/pom.xml} 直列 {@code workspace-impl} / {@code ai-engine-impl}）。
 * 本类不引 Spring 上下文、不连真实 security 进程（传输故障以 {@link RestTemplate}
 * 子类桩注入），真实"关掉 security 容器再点页面"属环境级集成，不在本门禁主张范围内。</p>
 *
 * <h3>反向对照（防"恒抛 503"式新假绿）</h3>
 * <p>{@link RestTemplate} 若无条件抛异常也能让前三个用例变绿，故每个通道都配一条
 * "security 正常应答"的对照：RLS 明确 {@code whereClause="1=0"} 仍是 DENY 兜底态（403 语义、
 * 非 503）、ABAC {@code result=false} 仍抛 {@link ForbiddenOperationException}、
 * ABAC 应答 DENY 仍是 {@link ToolDecision.Verdict#DENY} 而非 FAIL_CLOSED。</p>
 *
 * @author ECOS Security Gate / 详设-01 §4.7.1（#140 子批 B-2）
 */
@DisplayName("详设-01 §4.7.1 三通道全 DENY：security 不可用 → 页面 503 / 导出拒绝 / AI FAIL_CLOSED")
class SecurityDownAllChannelDenyTest {

    private static final String ENTITY = "scenario_graph";
    private static final String RUN_ID = "run-down-1";
    private static final String AUDIT_EXPORT_ACTION = "audit:export";

    // ═══════════════════════ ① 页面通道 ═══════════════════════

    @Test
    @DisplayName("#page 页面通道：连不上 security → 503 ECOS-SEC-501，禁 200+空列表冒充「无数据」")
    void page() {
        WorkspaceSecurityEngineClient down = client(new ConnectionRefusedRestTemplate());

        // 1) 底座分态：传输故障不再折叠成 "1=0"
        SecurityEngineUnavailableException ex = assertThrows(
                SecurityEngineUnavailableException.class,
                () -> down.applyRls(ENTITY, ENTITY, Map.of()),
                "连不上 security 必须报可用性故障，返回 \"1=0\" 会被渲染成空列表（本门禁失败判据）");
        assertEquals(503, ex.getHttpStatus(), "§4.7.2 页面格要求 503，禁 403 冒充「无权限」");
        assertEquals("ECOS-SEC-501", ex.getCode());

        // 1b) security 侧 5xx 与列裁剪面同属可用性态
        WorkspaceSecurityEngineClient fiveOhThree = client(new SecurityInternalServerErrorRestTemplate());
        assertEquals("ECOS-SEC-501",
                assertThrows(SecurityEngineUnavailableException.class,
                        () -> fiveOhThree.allowedColumns(ENTITY, List.of("id")),
                        "security 侧 5xx 属可用性故障，禁折叠成「无可见列」空行集")
                        .getCode());

        // 2) 读咽喉全链：行过滤 / 列裁剪 / 场景守卫都不得吞掉可用性故障
        AbacQueryFilter filter = new AbacQueryFilter(down);
        assertThrows(SecurityEngineUnavailableException.class,
                () -> filter.buildRowFilterCondition(ENTITY),
                "行过滤面必须原样上抛，禁落 \"1=0\" 后由守卫转 403");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("id", 1L, "amount", 2L)));
        assertThrows(SecurityEngineUnavailableException.class,
                () -> filter.filterColumns(ENTITY, rows),
                "列裁剪面必须原样上抛，禁「无可见列」式空行集冒充");
        assertThrows(SecurityEngineUnavailableException.class,
                () -> new ScenarioReadGuard(filter).assertReadable(ENTITY));

        // 3) HTTP 面：WorkspaceExceptionHandler 是唯一映射点
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                new WorkspaceExceptionHandler().handleDomain(ex);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resp.getStatusCode());
        ApiResponse<Map<String, Object>> body = resp.getBody();
        assertNotNull(body, "503 响应体必须存在（A3 禁 200 空体）");
        assertEquals("ECOS-SEC-501", body.getErrorCode());
        assertEquals(503, body.getCode());
        assertFalse(body.getData() instanceof Collection<?>,
                "响应体禁带行集（含空行集）冒充查询结果: " + body.getData());

        // 4) 反向对照：security 正常应答的明确 DENY 仍是 DENY 兜底态，不转可用性故障
        WorkspaceSecurityEngineClient up = client(new PolicyDenyRestTemplate());
        assertEquals("1=0", up.applyRls(ENTITY, ENTITY, Map.of()),
                "明确 RLS DENY 与「引擎不可用」必须分态，否则本门禁只是恒抛");
        List<String> columns = up.allowedColumns(ENTITY, List.of("id", "amount"));
        assertTrue(columns.isEmpty(), "明确 CLS 拒绝仍走空列集兜底（403 语义面）");
        assertFalse(up.evaluate(AUDIT_EXPORT_ACTION, Map.of()), "明确 ABAC 拒绝仍返回 false");
    }

    /**
     * 源码级接线钉：页面通道有两处"逐表/整查询 try-catch"面
     * （{@code ObjectController#search} 与 {@code ObjectQLController#query}），
     * 若可用性异常被泛 {@code catch (Exception)} 一起吞掉，响应分别是
     * HTTP 200 + 空 data（假"无数据"）与 HTTP 200 + code=-1（丢失 503/ECOS-SEC-501）——
     * 都是本门禁的失败判据。此用例钉住两处"单独 rethrow 可用性异常"的接线不流失
     * （行为面无法在单测里低成本触达：两个 Controller 需整套 DAO 依赖，
     * 故只钉接线，不主张已实跑 HTTP 响应）。
     */
    @Test
    @DisplayName("页面通道接线：两个查询面的逐表/整查询 catch 必须单独上抛可用性异常")
    void pageChannelSearchSurfaceKeepsAvailabilityLoud() throws Exception {
        // 路径解析沿用同仓 RlsInjectionGuardArchTest 约定：surefire cwd = gateway/
        java.nio.file.Path backendRoot = java.nio.file.Paths.get("..").toAbsolutePath().normalize();
        String controllerDir =
                "workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/";
        for (String file : List.of("ObjectController.java", "ObjectQLController.java")) {
            java.nio.file.Path src = backendRoot.resolve(controllerDir + file);
            assertTrue(java.nio.file.Files.isRegularFile(src),
                    "门禁载体源文件不可达（路径约定失效须先修门禁本身）: " + src);
            String code = java.nio.file.Files.readString(src, java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(code.contains("catch (SecurityEngineUnavailableException e)"),
                    file + " 丢失可用性 rethrow ⇒ security 不可用会退化成 200 空结果/200 code=-1（假无数据）");
            assertTrue(code.contains("import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException"),
                    file + " 的可用性异常必须显式 import，禁靠全限定名散落");
        }
    }

    // ═══════════════════════ ② 导出通道 ═══════════════════════

    @Test
    @DisplayName("#export 导出通道：security 不可用 → 拒绝产出审计包（503 上抛，取数面零触达）")
    void export() {
        // 两个取数服务故意传 null：若 audit:export 裁决门未先于取数触发，将得到 NPE 而非本异常
        // ⇒ 断言异常类型本身就证明了"先裁决后取数"的顺序不变式
        ForecastAuditPackService down =
                new ForecastAuditPackService(null, null, client(new ConnectionRefusedRestTemplate()));
        SecurityEngineUnavailableException ex = assertThrows(SecurityEngineUnavailableException.class,
                () -> down.buildAuditPack(RUN_ID),
                "导出面在 security 不可用时必须拒绝（§4.5.4 导出行 = 503 DENY），禁降级出包");
        assertEquals("ECOS-SEC-501", ex.getCode());

        // 反向对照：security 正常应答的明确拒绝仍是 403 语义（ForbiddenOperationException）
        ForecastAuditPackService denied =
                new ForecastAuditPackService(null, null, client(new PolicyDenyRestTemplate()));
        assertThrows(ForbiddenOperationException.class, () -> denied.buildAuditPack(RUN_ID),
                "明确 OPA DENY 不得被改写成可用性故障（两态分立）");
    }

    // ═══════════════════════ ③ AI 通道 ═══════════════════════

    @Test
    @DisplayName("#ai AI 通道：security 不可用 → FAIL_CLOSED（禁折叠成 DENY/ALLOW）且留痕一次")
    void ai() throws Exception {
        RecordingAuditPublisher publisher = new RecordingAuditPublisher();
        AgentToolPolicyGate gate = new AgentToolPolicyGate(
                new AgentToolSqlWhitelist(null), new DownSecurityClient(), publisher, null);

        ToolDecision decision = gate.review(request());
        assertEquals(ToolDecision.Verdict.FAIL_CLOSED, decision.verdict(),
                "不可用必须是第三态 FAIL_CLOSED: " + decision.reason());
        assertTrue(decision.failClosed());
        assertFalse(decision.allowed(), "FAIL_CLOSED 禁冒充放行");
        assertEquals(1, publisher.calls, "不可用态同样要留痕一次（§1.5-1 对账前提）");

        // 反向对照：security 正常应答 DENY 仍是 DENY，不得被转成 FAIL_CLOSED
        AgentToolPolicyGate denyGate = new AgentToolPolicyGate(
                new AgentToolSqlWhitelist(null), new DenySecurityClient(), publisher, null);
        ToolDecision deniedByPolicy = denyGate.review(request());
        assertEquals(ToolDecision.Verdict.DENY, deniedByPolicy.verdict(),
                "明确裁决 DENY 与不可用 FAIL_CLOSED 必须分态（§4.2 / F06-14）");
        assertFalse(deniedByPolicy.failClosed());
    }

    // ═══════════════════════ 夹具 ═══════════════════════

    private static WorkspaceSecurityEngineClient client(RestTemplate restTemplate) {
        // EventBus provider 传 null：本用例只走裁决面，publishAuditEvent 仅在 audit() 解引用
        return new WorkspaceSecurityEngineClient(restTemplate, null);
    }

    private static ToolAdjudicationRequest request() {
        return new ToolAdjudicationRequest(
                new ToolAdjudicationRequest.Subject(
                        "u-down-1", List.of("analyst"), "dept-a", "t-1"),
                new ToolAdjudicationRequest.Action(
                        ToolAdjudicationRequest.ACTION_TYPE, "project.query", "read"),
                new ToolAdjudicationRequest.Resource(
                        null, RUN_ID, null, null, List.of(), List.of()),
                new ToolAdjudicationRequest.Environment(
                        "trace-security-down", ToolAdjudicationRequest.CHANNEL_AGENT,
                        "2026-10-15T00:00:00Z"));
    }

    /** security 进程不可用：POST 一律 connection refused（{@link ResourceAccessException} 形态）。 */
    private static final class ConnectionRefusedRestTemplate extends RestTemplate {
        @Override
        public <T> ResponseEntity<T> postForEntity(String url, Object request,
                                                   Class<T> responseType, Object... uriVariables) {
            throw new ResourceAccessException("Connection refused: " + url);
        }
    }

    /** security 正常应答但三态皆为明确 DENY（反向对照载体，按 path 分形态应答）。 */
    private static final class PolicyDenyRestTemplate extends RestTemplate {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> ResponseEntity<T> postForEntity(String url, Object request,
                                                   Class<T> responseType, Object... uriVariables) {
            Map<String, Object> data = new LinkedHashMap<>();
            if (url.endsWith("/rls/apply")) {
                data.put("whereClause", "1=0");
            } else if (url.endsWith("/cls/columns")) {
                data.put("allowedColumns", List.of());
            } else {
                // 刻意用历史别名形态（现行生产应答写 allow）：本夹具同时覆盖契约的 result 兼容读腿
                data.put("result", false);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", 0);
            body.put("data", data);
            return (ResponseEntity<T>) ResponseEntity.ok(body);
        }
    }

    /** security 侧 5xx 同样属可用性故障（HTTP 503 应答形态）。 */
    private static final class SecurityInternalServerErrorRestTemplate extends RestTemplate {
        @Override
        public <T> ResponseEntity<T> postForEntity(String url, Object request,
                                                   Class<T> responseType, Object... uriVariables) {
            throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE,
                    "security-engine down", null, null, null);
        }
    }

    /** AI 通道：security 不可用（客户端不吞异常，交裁决门转 FAIL_CLOSED）。 */
    private static final class DownSecurityClient extends AiSecurityEngineClient {
        DownSecurityClient() {
            super("http://127.0.0.1:1", 200, null);
        }

        @Override
        public Map<String, Object> evaluateData(Map<String, Object> requestBody) throws Exception {
            throw new ResourceAccessException("Connection refused (security 进程不可用)");
        }
    }

    /** AI 通道反向对照：security 正常应答明确 DENY。 */
    private static final class DenySecurityClient extends AiSecurityEngineClient {
        DenySecurityClient() {
            super("http://127.0.0.1:1", 200, null);
        }

        @Override
        public Map<String, Object> evaluateData(Map<String, Object> requestBody) {
            return Map.of("allow", false, "policyId", "p-policy-deny");
        }
    }

    /** 审计留痕计数桩（红线面必须留痕；本类只数次数，内容合规性由 ai-engine 专项门禁主张）。 */
    private static final class RecordingAuditPublisher extends GuardrailAuditPublisher {
        private int calls;

        RecordingAuditPublisher() {
            super(null, null);
        }

        @Override
        public void auditOrThrow(ToolAdjudicationRequest request, ToolDecision decision) {
            calls++;
        }
    }
}
