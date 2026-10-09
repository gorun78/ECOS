package com.chinacreator.gzcm.workspace.harness;

import com.chinacreator.gzcm.workspace.exception.WorkspaceExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 卷07 F07-24（W188 / §八.4 前置件 P-2）—— workspace 测试底座起动机。
 *
 * <p><b>门禁语义（硬约束）</b>：本用例未通过前，本册所有 {@code *IT} 与 {@code *.spec.ts}
 * 一律记"未执行"，禁止写"通过"；"编译通过"永远不等于"测过"。</p>
 *
 * <p>一个方法内跑两形态（对应 C-4 两态可达矩阵的两列）：
 * <ol>
 *   <li><b>standalone 形态</b>（外层 {@code @SpringBootTest} MOCK 上下文）——
 *       Controller + advice 都在容器里，经真实 dispatcher 断言
 *       §四 C-5 的每个状态码都落到 HTTP 状态行上；</li>
 *   <li><b>gateway-hosted 形态</b>（方法内独立 {@code AnnotationConfigApplicationContext}）——
 *       同一份 config 换 profile，断言上下文<b>起得来但 0 个 workspace 端点</b>，
 *       即 ADR-15「制品 ≠ 承流」。这条断言本身就是 {@code GatewayRouteIntegrityTest}
 *       源码 regex 假阳性的<b>反证</b>：静态扫到 workspace controller 文件
 *       并不等于该端点能在 gateway 里被请求到。</li>
 * </ol>
 * 走 standalone MockMvc 而非 {@code webAppContextSetup}：薄切片刻意不启
 * {@code WebMvcAutoConfiguration}（以免被 datasource/kafka 拖起），
 * 因此没有 {@code RequestMappingHandlerMapping}，需手工挂 dispatcher。</p>
 */
@SpringBootTest(classes = WorkspaceTestHarnessConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("harness-workspace-standalone")
class WorkspaceTestHarnessSmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** C-5 映射表逐行：探针 kind → 期望真实 HTTP 状态码。 */
    private static final Map<String, Integer> EXPECTED_STATUS = Map.ofEntries(
            Map.entry("island", 409),
            Map.entry("transition", 409),
            Map.entry("terminal", 409),
            Map.entry("no-formal-run", 409),
            Map.entry("sandbox", 400),
            Map.entry("cognitive", 503),
            Map.entry("forbidden", 403),
            Map.entry("lock", 409),
            Map.entry("notfound", 404),
            Map.entry("action", 400),
            Map.entry("internal", 500)
    );

    @Autowired
    private WorkspaceHarnessProbeController standaloneProbe;

    @Autowired
    private WorkspaceExceptionHandler standaloneAdvice;

    @Test
    @DisplayName("P-2：workspace 薄切片必须在 standalone / gateway-hosted 两形态都起得来")
    void springContextBootsInBothShapes() throws Exception {
        // ── 形态 1：workspace 独立进程 :18090（controller 在册）──
        assertNotNull(standaloneProbe, "standalone 形态须有 workspace controller bean");
        assertNotNull(standaloneAdvice, "standalone 形态须有 WorkspaceExceptionHandler bean");
        runStatusMatrix(mvc(standaloneProbe, standaloneAdvice), "standalone");

        // ── 形态 2：gateway fat-JAR（controller 被 REGEX 排除 ⇒ 0 端点）──
        try (AnnotationConfigApplicationContext gwCtx = new AnnotationConfigApplicationContext()) {
            gwCtx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("gw",
                    Map.of("spring.profiles.active", "harness-gateway-hosted")));
            gwCtx.register(WorkspaceTestHarnessConfig.class);
            gwCtx.refresh();

            assertNotNull(gwCtx.getBean(WorkspaceExceptionHandler.class),
                    "gateway 形态仍须装 advice（advice 不在被排除包内）");
            assertEquals(0, gwCtx.getBeansWithAnnotation(RestController.class).size(),
                    "gateway 形态下 workspace 可达端点数必须为 0（承流口径），而制品口径是"
                            + " 17 Controller / 68 端点 —— 静态源码扫描不得替代本断言（X-3/F07-24）");
        }
    }

    /** 逐 kind 走 dispatcher，断言状态行是真码；同时断言 500 分支不含异常原文。 */
    private static void runStatusMatrix(MockMvc mvc, String shape) throws Exception {
        MvcResult ping = mvc.perform(get("/api/v1/workspace/_harness/ping")).andReturn();
        assertEquals(200, ping.getResponse().getStatus(), shape + " 形态 ping 必须 200");

        for (Map.Entry<String, Integer> e : EXPECTED_STATUS.entrySet()) {
            MvcResult res = mvc.perform(get("/api/v1/workspace/_harness/throw/" + e.getKey())).andReturn();
            assertEquals(e.getValue().intValue(), res.getResponse().getStatus(),
                    shape + " 形态 kind=" + e.getKey() + " 必须落到真状态码（禁 200 装 5xx，X-16/X-17）");
            String body = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
            Map<?, ?> json = MAPPER.readValue(body, Map.class);
            assertEquals(e.getValue().intValue(), json.get("code"),
                    shape + " 形态 body.code 必须与状态行一致");
            assertNotNull(json.get("traceId"), shape + " 形态 body.traceId 必填（F07-21）");
            if (e.getValue() == 500) {
                assertTrue(!body.contains("hunter2") && !body.contains("jdbc:postgresql"),
                        shape + " 形态内部异常原文禁止进响应体（X-41）：实际=" + body);
            }
        }
    }

    private static MockMvc mvc(WorkspaceHarnessProbeController probe, WorkspaceExceptionHandler advice) {
        return org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(probe)
                .setControllerAdvice(advice)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(MAPPER))
                .build();
    }
}
