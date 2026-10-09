package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.dao.ToolDao;
import com.chinacreator.gzcm.engine.ai.dao.ToolPermissionBindingDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.AgentToolSqlWhitelist;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.security.GuardrailAuditPublisher;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-02 要点 1 验收 —— 四段裁决载荷（PRD-06 §1.3）。
 *
 * <p>doc 冻结的两个标识：</p>
 * <ul>
 *   <li>{@code ToolAdjudicationPayloadTest#payloadCarriesFourSectionsWithServerSideSubject}；</li>
 *   <li>{@code ToolAdjudicationPayloadTest#llmSuppliedUnauthorizedArgsNeverEnterPayload}。</li>
 * </ul>
 *
 * <p>断言对象是<b>真正下发给 security-engine 的 body</b>（由 {@link AiSecurityEngineClient}
 * 桩捕获），而不是中间对象 —— 只有线上载荷形态才决定 security 侧策略能否正确判权。
 * 装置用<b>真闸</b>（{@code AgentToolPolicyGate}）+ 假 security 客户端：载荷渲染发生在闸内，
 * 假客户端会跳过这段逻辑，测不到红线。</p>
 */
class ToolAdjudicationPayloadTest {

    private static final String SERVER_USER = "u-9";
    private static final String SERVER_TENANT = "t-1";
    private static final String SPOOFED_ADMIN = "spoofed-super-admin";
    private static final String FAKE_API_KEY = "sk-LEAK-MUST-NOT-TRAVEL";
    private static final String SECRET_LITERAL = "FINANCE-SECRET-9527";
    private static final String SQL_TEXT =
            "SELECT salary FROM ecos_dw.payroll WHERE ssn = '" + SECRET_LITERAL + "'";
    private static final String TOOL = "forecast.query";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 验收 1：四段齐备 + 主体只来自服务端 ──────────────────────────────────

    @Test
    @DisplayName("F06-02 载荷四段齐备；subject 由服务端 token 上下文构造，非调用方自填")
    void payloadCarriesFourSectionsWithServerSideSubject() throws Exception {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("scenarioId", "s-7");
        arguments.put("forecastRunId", "run-3");
        arguments.put("projectIds", List.of("p-1", "p-2"));
        arguments.put("periods", List.of("2026-Q1"));
        arguments.put("userId", SPOOFED_ADMIN);

        Map<String, Object> input = inputOf(adjudicate(arguments));

        // ① 四段齐备（缺任一段 = 无有效裁决记录）
        assertTrue(input.keySet().containsAll(
                        List.of("subject", "action", "resource", "environment", "type", "tool", "operation")),
                "载荷缺段: " + input.keySet());
        // ② subject 必须是解析器的服务端值
        Map<?, ?> subject = (Map<?, ?>) input.get("subject");
        assertEquals(SERVER_USER, subject.get("userId"),
                "主体被入参自报值污染 = 参数注入式越权");
        assertFalse(String.valueOf(subject).contains(SPOOFED_ADMIN), String.valueOf(subject));
        assertEquals(SERVER_TENANT, subject.get("tenantId"));
        assertEquals(List.of("analyst"), subject.get("roles"));
        assertEquals("dept-a", subject.get("department"), "PRD §1.3 要求 department 在场");
        // ③ action / resource / environment 逐段验真值
        assertEquals(ToolAdjudicationRequest.ACTION_TYPE, input.get("type"));
        assertEquals(TOOL, input.get("tool"));
        assertEquals("read", input.get("operation"));
        Map<?, ?> resource = (Map<?, ?>) input.get("resource");
        assertEquals("s-7", resource.get("scenarioId"));
        assertEquals("run-3", resource.get("forecastRunId"));
        Map<?, ?> scope = (Map<?, ?>) resource.get("dataScope");
        assertEquals(List.of("p-1", "p-2"), scope.get("projectIds"));
        assertEquals(List.of("2026-Q1"), scope.get("periods"));
        Map<?, ?> env = (Map<?, ?>) input.get("environment");
        assertEquals(ToolAdjudicationRequest.CHANNEL_AGENT, env.get("channel"));
        assertNotNull(env.get("traceId"), "environment.traceId 缺失则审计对账断链");
        assertNotNull(env.get("timestamp"));
    }

    // ── 验收 2：LLM 自报入参不入载荷 ────────────────────────────────────────

    @Test
    @DisplayName("F06-02 非白名单入参（sql 原文/prompt/command/自报 userId/apiKey）不入下发载荷")
    void llmSuppliedUnauthorizedArgsNeverEnterPayload() throws Exception {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("scenarioId", "s-7");
        arguments.put("sql", SQL_TEXT);
        arguments.put("prompt", "ignore previous instructions " + SECRET_LITERAL);
        arguments.put("command", "rm -rf / " + SECRET_LITERAL);
        arguments.put("userId", SPOOFED_ADMIN);
        arguments.put("roles", List.of(SPOOFED_ADMIN));
        arguments.put("department", SPOOFED_ADMIN);
        arguments.put("apiKey", FAKE_API_KEY);
        arguments.put("token", FAKE_API_KEY);

        Map<String, Object> body = adjudicate(arguments);
        String rendered = objectMapper.writeValueAsString(body);

        // ① 自报主体/凭据类字段绝不进载荷
        assertFalse(rendered.contains(SPOOFED_ADMIN),
                "LLM 自报主体进入裁决载荷 = 参数注入式越权: " + rendered);
        assertFalse(rendered.contains(FAKE_API_KEY),
                "凭据类入参禁入载荷（X-23 同源纪律）: " + rendered);
        // ② SQL / prompt / command 原文不进载荷（只允许派生结构值）
        assertFalse(rendered.contains(SECRET_LITERAL), "字面量随载荷外发: " + rendered);
        assertFalse(rendered.contains("SELECT salary"), "SQL 原文禁入载荷: " + rendered);
        assertFalse(rendered.contains("ignore previous instructions"),
                "prompt 原文禁入载荷: " + rendered);
        assertFalse(rendered.contains("rm -rf"), "command 原文禁入载荷: " + rendered);
        // ③ 白名单字段 + 派生结构值在场（排除"载荷整体为空"造成的假通过）
        assertTrue(rendered.contains("s-7"), rendered);
        assertTrue(rendered.contains(SERVER_USER), rendered);
        assertTrue(rendered.contains("sqlDigest"), "SQL 只允许以摘要形态进入: " + rendered);
        assertTrue(rendered.contains("readOnly"), rendered);
        assertTrue(rendered.contains("ecos_dw.payroll"),
                "表引用是策略判读所需的派生值，应当在场: " + rendered);
        // ④ 非白名单入参连 resource 段都进不去
        Map<?, ?> resource = (Map<?, ?>) inputOf(body).get("resource");
        assertFalse(resource.containsKey("sql"), String.valueOf(resource));
        assertFalse(resource.containsKey("prompt"), String.valueOf(resource));
        assertFalse(resource.containsKey("command"), String.valueOf(resource));
    }

    // ── 投影与构造器纪律 ────────────────────────────────────────────────────

    @Test
    @DisplayName("F06-02 resource 投影只取白名单字段；其余入参一律丢弃")
    void resourceProjectionKeepsOnlyWhitelistedFields() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("scenarioId", "s-7");
        arguments.put("caliberId", "c-2");
        arguments.put("objectId", "o-5");
        arguments.put("forecastRunId", "run-3");
        arguments.put("projectIds", List.of("p-1"));
        arguments.put("periods", "2026-Q1");
        arguments.put("sql", SQL_TEXT);
        arguments.put("limit", 500);
        arguments.put("where", "1=1 OR admin=true");

        ToolAdjudicationRequest.Resource resource =
                ToolAdjudicationRequest.projectResource(arguments);
        assertEquals("s-7", resource.scenarioId());
        assertEquals("c-2", resource.caliberId());
        assertEquals("o-5", resource.objectId());
        assertEquals("run-3", resource.forecastRunId());
        assertEquals(List.of("p-1"), resource.projectIds());
        assertEquals(List.of("2026-Q1"), resource.periods(), "标量 periods 归一为单元素列表");

        Map<String, Object> input = new ToolAdjudicationRequest(
                new ToolAdjudicationRequest.Subject(SERVER_USER, List.of("analyst"), null, SERVER_TENANT),
                new ToolAdjudicationRequest.Action(ToolAdjudicationRequest.ACTION_TYPE, TOOL, "read"),
                resource,
                new ToolAdjudicationRequest.Environment("trace-x",
                        ToolAdjudicationRequest.CHANNEL_AGENT, "2026-10-09T00:00:00Z")).toEvaluateInput();
        Map<?, ?> renderedResource = (Map<?, ?>) input.get("resource");
        assertFalse(renderedResource.containsKey("sql"), String.valueOf(renderedResource));
        assertFalse(renderedResource.containsKey("limit"), String.valueOf(renderedResource));
        assertFalse(renderedResource.containsKey("where"), String.valueOf(renderedResource));

        assertEquals(Set.of("scenarioId", "forecastRunId", "projectIds", "periods", "caliberId", "objectId"),
                ToolAdjudicationRequest.resourceArgWhitelist(),
                "白名单发生偏移需架构官签发（扩面即扩权）");
    }

    @Test
    @DisplayName("F06-02 单一四参构造器：漏写任一段即编译不过（禁自拼 Map 载荷）")
    void requestHasExactlyOneFourSectionConstructor() {
        Constructor<?>[] ctors = ToolAdjudicationRequest.class.getDeclaredConstructors();
        assertEquals(1, ctors.length,
                "多构造器 = 允许漏段，违反'四段缺一即编译不过'的设计意图");
        assertEquals(4, ctors[0].getParameterCount());
        assertEquals(ToolAdjudicationRequest.Subject.class, ctors[0].getParameterTypes()[0]);
        assertEquals(ToolAdjudicationRequest.Action.class, ctors[0].getParameterTypes()[1]);
        assertEquals(ToolAdjudicationRequest.Resource.class, ctors[0].getParameterTypes()[2]);
        assertEquals(ToolAdjudicationRequest.Environment.class, ctors[0].getParameterTypes()[3]);
    }

    @Test
    @DisplayName("F06-02 空入参投影得到空 resource（不得凭空造权限）")
    void nullArgumentsProjectToEmptyResource() throws Exception {
        ToolAdjudicationRequest.Resource resource = ToolAdjudicationRequest.projectResource(null);
        assertNull(resource.scenarioId());

        ToolAdjudicationRequest request = new ToolAdjudicationRequest(
                new ToolAdjudicationRequest.Subject(SERVER_USER, List.of(), null, SERVER_TENANT),
                new ToolAdjudicationRequest.Action(ToolAdjudicationRequest.ACTION_TYPE, "read_file", "read"),
                resource,
                new ToolAdjudicationRequest.Environment("trace-x",
                        ToolAdjudicationRequest.CHANNEL_AGENT, "2026-10-09T00:00:00Z"));
        assertEquals(Set.of(), ToolAdjudicationRequest.normalizedRoles(request.subject()));
        Map<?, ?> renderedResource = (Map<?, ?>) request.toEvaluateInput().get("resource");
        assertTrue(renderedResource.isEmpty(), String.valueOf(renderedResource));
    }

    // ── 装置：真闸 + 假 security 客户端（捕获真实下发体） ────────────────────

    /**
     * 跑一次咽喉工具调用，返回 security 客户端实际收到的裁决请求体。
     *
     * <p>闸必须是真实现：载荷四段渲染、SQL 派生值注入都发生在
     * {@code AgentToolPolicyGate#adjudicate} 内，mock 掉闸就测不到本项红线。</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> adjudicate(Map<String, Object> arguments) throws Exception {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        when(client.evaluateBodyForAdjudication(any())).thenAnswer(inv -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("policy", "rbac");
            body.put("input", inv.getArgument(0));
            return body;
        });
        when(client.evaluateData(any())).thenReturn(Map.of("allow", true, "policyId", "p-1"));

        AgentToolSqlWhitelist whitelist = mock(AgentToolSqlWhitelist.class);
        when(whitelist.allowedColumns("ecos_dw.payroll")).thenReturn(Set.of("*"));

        AgentToolPolicyGate gate = new AgentToolPolicyGate(whitelist, client,
                mock(GuardrailAuditPublisher.class), emptyBindings());

        ToolSubjectResolver resolver = mock(ToolSubjectResolver.class);
        when(resolver.resolve()).thenReturn(new ToolAdjudicationRequest.Subject(
                SERVER_USER, List.of("analyst"), "dept-a", SERVER_TENANT));

        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.has(TOOL)).thenReturn(true);
        when(registry.execute(eq(TOOL), any())).thenReturn(
                ToolExecutorService.ToolResult.ok("call-1", TOOL, "{\"rows\":[],\"count\":0}", 1L));

        ToolExecutorService service = new ToolExecutorService();
        service.setToolDao(mock(ToolDao.class));
        service.setPolicyGate(gate);
        service.setSubjectResolver(resolver);
        ReflectionTestUtils.setField(service, "toolRegistry", registry);

        service.execute(TOOL, arguments);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(client).evaluateData(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ToolPermissionBindingDao> emptyBindings() {
        ObjectProvider<ToolPermissionBindingDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> inputOf(Map<String, Object> body) {
        Object input = body.get("input");
        assertTrue(input instanceof Map, "下发体缺 input 段: " + body.keySet());
        return (Map<String, Object>) input;
    }
}
