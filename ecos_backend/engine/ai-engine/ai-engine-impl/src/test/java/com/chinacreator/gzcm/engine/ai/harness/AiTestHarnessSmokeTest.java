package com.chinacreator.gzcm.engine.ai.harness;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 详细设计-06 F06-22 前置门禁载体（P-1 / §8.4）：
 * {@code springBootTestSliceBootsInBothModes}。
 *
 * <p><b>前置门禁语义</b>（§8.4 硬约束）：</p>
 * <blockquote>
 *   该用例失败或未存在时，本册 8.1 中所有 {@code *IT} 与 {@code *.spec.ts} 项状态
 *   必须写"未执行"，禁止写"通过"。
 * </blockquote>
 *
 * <p>实现策略 —— 一个方法内跑两态：</p>
 * <ul>
 *   <li>外层类挂 {@code @SpringBootTest(webEnvironment=MOCK)} +
 *       {@code @ActiveProfiles("harness-gateway")}，classes 指向
 *       {@link AiTestHarnessConfig} —— Spring 测试框架独立拉起的
 *       <b>gateway 形态</b>最小上下文（bean 装配 + 探针端点 + SecurityEngine 桩
 *       在 profile={harness-gateway} 分支能起）</li>
 *   <li>外层用 <b>standalone MockMvc</b> 把 {@link HarnessProbeController} 挂一层
 *       dispatcher，走一遍真实 HTTP 语义（ALLOW/DENY/FAIL_CLOSED 三态 +
 *       traceId 头经 {@link #newTraceIdFilter()} 回写响应）——这保证了
 *       "探针端点<b>确实可以经 HTTP 探到</b>"契约（防"bean 存在但 controller 注解坏"）</li>
 *   <li>方法内另起一个独立 {@link AnnotationConfigApplicationContext}
 *       + {@code spring.profiles.active=harness-aiming} —— 证明<b>同一份</b>
 *       {@link AiTestHarnessConfig} 在 <b>aiming 形态</b>下也能起；
 *       直接把 bean 拿过来跑 <i>同一份断言</i>（防 X-26 / R-18
 *       "gateway 态测过 ≠ 能力存在" 假阳性）</li>
 * </ul>
 *
 * <p>为什么用 standalone MockMvc 而不用 webAppContextSetup：
 * {@code @SpringBootTest(classes = AiTestHarnessConfig.class)} 走
 * 最小 {@code @Configuration} 路径，Spring Boot 的
 * {@code WebMvcAutoConfiguration} 未激活（本设计刻意不启 auto-config
 * 以免意外拉起 sysman/security/neo4j 等），
 * webAppContextSetup 会因为没有配置好的
 * MessageConverter 集合对 {@code @RestController} 返 Map 的
 * 请求 406。standaloneSetup 内建默认 Jackson converter 集合，
 * 更贴合"薄切片"语义。</p>
 */
@SpringBootTest(
        classes = AiTestHarnessConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("harness-gateway")
class AiTestHarnessSmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockSecurityEngineStub stub;

    @Autowired
    private HarnessProbeController probe;

    @Test
    @DisplayName("P-1：@SpringBootTest 薄切片双态（gateway fat-JAR + aiming service 同一 config 起）")
    void springBootTestSliceBootsInBothModes() throws Exception {
        // 态 1 —— gateway profile（外层 @SpringBootTest 上下文拉起的 bean 集合）
        MockMvc mvcGw = buildStandaloneMockMvc(probe);
        runSliceViaHttp("gateway", mvcGw, stub);
        runSliceViaDirectCall("gateway", probe, stub);

        // 态 2 —— 用同一份 config + @Profile("harness-aiming") 独立起一个最小上下文
        try (AnnotationConfigApplicationContext aimingCtx = new AnnotationConfigApplicationContext()) {
            aimingCtx.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("aiming",
                            Map.of("spring.profiles.active", "harness-aiming")));
            aimingCtx.register(AiTestHarnessConfig.class);
            aimingCtx.refresh();

            HarnessProbeController aimingProbe = aimingCtx.getBean(HarnessProbeController.class);
            boolean stubPresent = aimingCtx.getBeanNamesForType(MockSecurityEngineStub.class).length > 0;
            MockSecurityEngineStub aimingStub = stubPresent
                    ? aimingCtx.getBean(MockSecurityEngineStub.class) : null;
            assertNotNull(aimingProbe, "aiming 上下文须暴露 HarnessProbeController bean");
            assertNotNull(aimingStub, "aiming 上下文须暴露 MockSecurityEngineStub bean");
            assertNotSame(stub, aimingStub, "两态的桩必须是独立实例（防跨态污染）");

            MockMvc mvcAim = buildStandaloneMockMvc(aimingProbe);
            runSliceViaHttp("aiming", mvcAim, aimingStub);
            runSliceViaDirectCall("aiming", aimingProbe, aimingStub);
        }
    }

    /** 三态 + traceId 头 走真实 HTTP dispatcher（standalone MockMvc）。 */
    private static void runSliceViaHttp(String mode, MockMvc mvc,
                                         MockSecurityEngineStub stub) throws Exception {
        // 1) ping —— 起动必须是活的，桩默认 = ALLOW
        MvcResult pingRes = mvc.perform(get("/test/harness/ping")).andExpect(status().isOk()).andReturn();
        Map<?, ?> ping = readJson(pingRes, mode);
        assertEquals(Boolean.TRUE, ping.get("ok"), mode + " 态 ping.ok 须 true");
        assertEquals(MockSecurityEngineStub.StubState.ALLOW.toString(),
                String.valueOf(ping.get("securityStub")),
                mode + " 态 HTTP 起动后桩默认须为 ALLOW");

        // 2) 桩 ALLOW + X-Trace-Id 头 → traceId 必须回写（Filter 生效 + controller 透传）
        MvcResult allowRes = mvc.perform(post("/test/harness/tool-adjudication")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "t-" + mode + "-http-root")
                        .content(json(java.util.Map.of("toolName", "probe.tool"))))
                .andExpect(status().isOk()).andReturn();
        Map<?, ?> allowBody = readJson(allowRes, mode + "/allow");
        assertEquals("ALLOW", allowBody.get("status"), mode + " 态 HTTP 桩 ALLOW 应返回 ALLOW");
        assertEquals("t-" + mode + "-http-root", allowBody.get("traceId"),
                "HTTP: X-Trace-Id 端上契约 —— controller 必须回写调用方给的 traceId（F06-06 MDC 贯通）");

        // 3) 桩切 DENY —— 响应 body 必须写明 status=result（禁 200 空体 / 静默 200）
        stub.setState(MockSecurityEngineStub.StubState.DENY);
        try {
            MvcResult denyRes = mvc.perform(post("/test/harness/tool-adjudication")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Trace-Id", "t-" + mode + "-http-deny")
                            .content(json(java.util.Map.of("toolName", "probe.write"))))
                    .andExpect(status().isOk()).andReturn();
            Map<?, ?> denyBody = readJson(denyRes, mode + "/deny");
            assertTrue(denyBody.containsKey("status"),
                    mode + " 态 HTTP DENY 响应必须写明状态，禁静默 200（X-26 / A3 / §3.2）");
            assertEquals("DENY", denyBody.get("status"));
            assertEquals("GUARDRAIL_DENIED", denyBody.get("errorClass"));
        } finally {
            stub.reset();
        }

        // 4) 桩切 UNSAFE_* —— 护栏不可用 ≠ DENY（分态严格；§3.2 状态矩阵）
        for (MockSecurityEngineStub.StubState unsafe : new MockSecurityEngineStub.StubState[]{
                MockSecurityEngineStub.StubState.UNSAFE_UNREACHABLE,
                MockSecurityEngineStub.StubState.UNSAFE_RATE_LIMITED,
                MockSecurityEngineStub.StubState.UNSAFE_UNPARSEABLE}) {
            stub.setState(unsafe);
            try {
                MvcResult fcRes = mvc.perform(post("/test/harness/tool-adjudication")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Trace-Id", "t-" + mode + "-http-" + unsafe)
                                .content(json(java.util.Map.of("toolName", "probe.write"))))
                        .andExpect(status().isOk()).andReturn();
                Map<?, ?> fcBody = readJson(fcRes, mode + "/" + unsafe);
                assertEquals("FAIL_CLOSED", fcBody.get("status"),
                        mode + " 态 HTTP 桩=" + unsafe + " 必须 FAIL_CLOSED；实际=" + fcBody.get("status"));
                assertEquals("GUARDRAIL_FAIL_CLOSED", fcBody.get("errorClass"));
            } finally {
                stub.reset();
            }
        }
    }

    /** 直接 controller→bean 调用（不经过 HTTP）—— 对 "bean 级契约" 状态做二次兜底。 */
    private static void runSliceViaDirectCall(String mode, HarnessProbeController probe,
                                              MockSecurityEngineStub stub) {
        Map<String, Object> allowOut = probe.adjudicate("t-" + mode + "-direct-root",
                Map.of("toolName", "probe.tool"));
        assertEquals("ALLOW", allowOut.get("status"),
                mode + " 态直调桩 ALLOW 应 ALLOW；实际=" + allowOut.get("status"));

        stub.setState(MockSecurityEngineStub.StubState.DENY);
        try {
            Map<String, Object> denyOut = probe.adjudicate("t-" + mode + "-direct-deny",
                    Map.of("toolName", "probe.write"));
            assertEquals("DENY", denyOut.get("status"));
            assertEquals("GUARDRAIL_DENIED", denyOut.get("errorClass"));
        } finally {
            stub.reset();
        }
    }

    /**
     * Standalone MockMvc + 显式带一个 traceId 过滤器 + 默认 Jackson converter 集合。
     * 与 {@code AiTestHarnessConfig} 中的 {@code FilterRegistrationBean} 版本功能等价，
     * 但走不同的装配路径 —— 双份装配刻意共存，进一步证明"traceId 传播"是
     * 与装配方式无关的<b>契约</b>。
     */
    private static MockMvc buildStandaloneMockMvc(HarnessProbeController probe) {
        return MockMvcBuilders.standaloneSetup(probe)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(MAPPER))
                .addFilter(newTraceIdFilter())
                .build();
    }

    static OncePerRequestFilter newTraceIdFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                            FilterChain chain) throws ServletException, IOException {
                String in = req.getHeader("X-Trace-Id");
                String id = (in == null || in.isBlank())
                        ? "t-" + UUID.randomUUID().toString().substring(0, 8) : in;
                MDC.put("traceId", id);
                res.setHeader("X-Trace-Id", id);
                try {
                    chain.doFilter(req, res);
                } finally {
                    MDC.remove("traceId");
                }
            }
        };
    }

    private static Map<?, ?> readJson(MvcResult r, String ctx) {
        try {
            String body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertNotNull(body, ctx + " 响应 body 不能为空");
            @SuppressWarnings("unchecked")
            Map<?, ?> m = MAPPER.readValue(body, Map.class);
            return m;
        } catch (Exception e) {
            throw new IllegalStateException(ctx + " 响应解析失败: " + e.getMessage(), e);
        }
    }

    private static String json(Map<String, Object> m) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>(m);
        try {
            return MAPPER.writeValueAsString(copy);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
