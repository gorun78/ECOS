package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.dao.ToolDao;
import com.chinacreator.gzcm.engine.ai.dao.ToolPermissionBindingDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.AgentToolSqlWhitelist;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.security.GuardrailAuditPublisher;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-02 要点 1 验收 —— 裁决下发载荷（PRD-06 §1.3）。
 *
 * <p>doc 冻结的两个方法标识（方法名换掉即视为测试丢失）：</p>
 * <ul>
 *   <li>{@code ToolAdjudicationPayloadTest#payloadCarriesFourSectionsWithServerSideSubject}；</li>
 *   <li>{@code ToolAdjudicationPayloadTest#llmSuppliedUnauthorizedArgsNeverEnterPayload}。</li>
 * </ul>
 *
 * <p>断言对象是<b>真正下发给 security-engine 的 body</b>（由 {@link AiSecurityEngineClient}
 * 桩捕获），而不是中间对象 —— 只有线上载荷形态才决定 security 侧策略能否正确判权。
 * 装置用<b>真闸</b>（{@code AgentToolPolicyGate}）+ 假 security 客户端：载荷渲染发生在闸内，
 * 假客户端会跳过这段逻辑，测不到红线。</p>
 *
 * <p><b>#142/B4-3e-78（D-B4-30 收口）</b>：主裁决腿已并入三通道唯一入口 {@code /decision}，
 * 断言面从旧四段 input 载荷改为 {@code DecisionChannelContract} 键面（purpose/environment
 * 恒在；assetRef/tables/sqlDigest 仅 SQL 派生值齐全时在场）；主体纪律从"wire 带服务端
 * subject"改为"<b>wire 根本无 subject 键位</b>，服务端 token 主体在闸内消费、自报值无处可进
 * 报文"（更严形态，四段 payload 仅本地留痕，见 {@code GuardrailAuditEventTest}）。</p>
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
    @DisplayName("F06-02 /decision 载荷按单源键面成形；主体只剩服务端解析器来源（wire 无自报位）")
    void payloadCarriesFourSectionsWithServerSideSubject() throws Exception {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("scenarioId", "s-7");
        arguments.put("forecastRunId", "run-3");
        arguments.put("projectIds", List.of("p-1", "p-2"));
        arguments.put("periods", List.of("2026-Q1"));
        arguments.put("userId", SPOOFED_ADMIN);

        Map<String, Object> body = adjudicate(arguments);
        String rendered = objectMapper.writeValueAsString(body);

        // ① /decision 请求体恒在段齐备：purpose=ai＋environment.channel=AGENT，键面全取
        //    DecisionChannelContract 单源（#142/B4-3e-78，D-B4-30）
        assertEquals(DecisionChannelContract.PURPOSE_AI, body.get(DecisionChannelContract.KEY_PURPOSE),
                "purpose 段缺失或漂移: " + rendered);
        @SuppressWarnings("unchecked")
        Map<String, Object> env = (Map<String, Object>) body.get(DecisionChannelContract.KEY_ENVIRONMENT);
        assertTrue(env != null, "environment 段缺失: " + rendered);
        assertEquals(ToolAdjudicationRequest.CHANNEL_AGENT, env.get(DecisionChannelContract.KEY_CHANNEL),
                "channel 段缺失或漂移: " + rendered);
        // ② 无 SQL 的调用不得凭空造资产段（registry 工具即使带 "sql" 入参也不进 body）
        assertFalse(body.containsKey(DecisionChannelContract.KEY_ASSET_REF), rendered);
        assertFalse(body.containsKey(DecisionChannelContract.KEY_TABLES), rendered);
        assertFalse(body.containsKey(DecisionChannelContract.KEY_SQL_DIGEST), rendered);
        // ③ 主体源纪律：/decision wire 不携带 subject（服务端 token 上下文在闸内裁决直接消费，
        //    LLM 自报 userId 连可进入报文的位置都没有）；闸内仍须以服务端解析器主体运行，
        //    匿名主体在发 HTTP 之前即 FAIL_CLOSED（AgentToolPolicyGateTest 已钉）
        assertFalse(rendered.contains(SPOOFED_ADMIN), "主体面出现 LLM 自报值: " + rendered);
        // ④ 键面枚举防漂移（只许写真实存在的键，禁静默加键）
        assertEquals(Set.of(DecisionChannelContract.KEY_PURPOSE, DecisionChannelContract.KEY_ENVIRONMENT),
                body.keySet(), rendered);
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
        // ③ 服务端派生值在场（排除"载荷整体为空"造成的假通过）
        assertEquals(DecisionChannelContract.PURPOSE_AI, body.get(DecisionChannelContract.KEY_PURPOSE), rendered);
        assertTrue(body.containsKey(DecisionChannelContract.KEY_SQL_DIGEST),
                "SQL 只允许以摘要形态进入: " + rendered);
        assertEquals(List.of("ecos_dw.payroll"), body.get(DecisionChannelContract.KEY_TABLES),
                "表引用是策略判读所需的派生值，应当在场: " + rendered);
        @SuppressWarnings("unchecked")
        Map<String, Object> assetRef = (Map<String, Object>) body.get(DecisionChannelContract.KEY_ASSET_REF);
        assertEquals("ecos_dw.payroll", assetRef == null ? null : assetRef.get(DecisionChannelContract.KEY_ASSET_TABLE),
                "恰一张受控表时资产归属必须宣告: " + rendered);
        // ④ 非白名单入参不落 wire（/decision 键面里根本没有它们的键位，连 resource 段都不存在）
        assertFalse(rendered.contains("scenarioId"), "非白名单业务参数漏出到 wire: " + rendered);
        assertEquals(Set.of(
                        DecisionChannelContract.KEY_ASSET_REF,
                        DecisionChannelContract.KEY_PURPOSE,
                        DecisionChannelContract.KEY_ENVIRONMENT,
                        DecisionChannelContract.KEY_TABLES,
                        DecisionChannelContract.KEY_SQL_DIGEST),
                body.keySet(), rendered);
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
     * 跑一次咽喉工具调用，返回 security 客户端实际收到的 /decision 裁决请求体。
     *
     * <p>闸必须是真实现：/decision 请求体渲染（{@code AiSecurityEngineClient#decisionBody}）与
     * SQL 派生值注入都发生在 {@code AgentToolPolicyGate#adjudicate} 内，mock 掉闸就测不到本项红线。</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> adjudicate(Map<String, Object> arguments) throws Exception {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        // #142/B4-3e-78（D-B4-30 收口）：主裁决腿已切 /decision（decideData），
        // 旧 evaluate 桩对本路径已死，删除（桩必须与在网腿同形，禁遗留旁路桩面）。
        doReturn(Map.of("allow", true, "policyId", "p-1")).when(client).decideData(any());

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
        verify(client).decideData(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ToolPermissionBindingDao> emptyBindings() {
        ObjectProvider<ToolPermissionBindingDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
