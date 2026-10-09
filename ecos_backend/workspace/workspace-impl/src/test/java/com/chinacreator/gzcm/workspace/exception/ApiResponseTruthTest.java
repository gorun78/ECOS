package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.workspace.harness.WorkspaceHarnessProbeController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 卷07 F07-13（W177）—— "响应体 code 与状态行同源"真相测试。
 *
 * <p>正面攻击 X-16/X-17 的两种伪装：
 * <ul>
 *   <li>X-16：{@code ScenarioCognitiveController} 四条认知 POST 把异常吞成
 *       {@code ApiResponse.badRequest("cognitive_service_unavailable: " + e.getMessage())}
 *       ⇒ HTTP 200 + 原文入体</li>
 *   <li>X-17：{@code ScenarioOptionsController:206-209 unavailable()} 返回
 *       {@code ApiResponse.error(503, "service_unreachable")} ⇒ body.code=503 但状态行仍 200
 *       （{@code ApiResponse.code} 只是 body 字段，不影响 HTTP 状态）</li>
 * </ul>
 * 本类断言"任何 code=503/500/4xx 的响应，状态行必须同码"，覆盖范围内可跑的全部面；
 * 两个越界文件（属场景 Agent）由下表的 TODO 逐条点名。</p>
 */
class ApiResponseTruthTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * C-5 全部行：任何错误响应的状态行与 body.code 必须同码 —— 不存在 200 装 5xx。
     */
    @Test
    @DisplayName("noEndpointReturnsHttp200WithCode503")
    void noEndpointReturnsHttp200WithCode503() throws Exception {
        MockMvc mvc = mvc();

        // X-16/X-17 的正解形态：认知引擎不可用 = 真 503 + 结构化 body + traceId
        MvcResult r = mvc.perform(get("/api/v1/workspace/_harness/throw/cognitive")).andReturn();
        assertEquals(503, r.getResponse().getStatus(),
                "认知不可用必须是 503 状态行（X-16 现状是 200）");
        Map<?, ?> body = json(r);
        assertEquals(503, body.get("code"), "body.code 与状态行同源（X-17 现状是 body 503 + 状态 200）");
        assertEquals("COGNITIVE_UNAVAILABLE", body.get("errorCode"));
        assertNotNull(body.get("traceId"));
        String raw = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertFalse(
                raw.contains("Exception") || raw.contains("at com.") || raw.contains("Caused by"),
                "503 响应体只能有结构化 code/message/details，禁堆栈与异常类名（X-41）：实际=" + raw);

        // 全表扫描：任何 kind 都不能给出 200，且 body.code 与状态行同码
        for (String kind : new String[]{"island", "transition", "terminal", "no-formal-run",
                "sandbox", "cognitive", "forbidden", "lock", "notfound", "action", "internal"}) {
            MvcResult res = mvc.perform(get("/api/v1/workspace/_harness/throw/" + kind)).andReturn();
            int status = res.getResponse().getStatus();
            assertNotNull(status);
            org.junit.jupiter.api.Assertions.assertNotEquals(200, status,
                    "kind=" + kind + " 是错误分支，禁止 HTTP 200（X-16/X-17）");
            assertEquals(status, json(res).get("code"),
                    "kind=" + kind + " 的 body.code 必须等于状态行");
        }
    }

    /** X-18 闭环：workspace-impl 此前完全没有 {@code @RestControllerAdvice}。 */
    @Test
    @DisplayName("workspace-impl 现在必须有且只有一个映射真源的 advice")
    void workspaceAdviceExistsAndIsScopedToWorkspaceOnly() {
        RestControllerAdvice ann =
                WorkspaceExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        assertNotNull(ann, "X-18：workspace-impl 全模块无 @RestControllerAdvice ⇒ :18090 异常走 Spring 默认");
        assertSame(1, ann.basePackages().length);
        assertEquals("com.chinacreator.gzcm.workspace", ann.basePackages()[0],
                "advice 必须只作用于 workspace 面，禁越界改写 gateway/sysman 既有映射");
    }

    /**
     * 越界面（本卷不可改文件）逐条点名 —— 由场景 Agent 落抛出侧：
     * <ol>
     *   <li>TODO: covered by other agent's controller ——
     *       {@code controller/ScenarioCognitiveController.java} diagnose（:37，异常 :45-47）</li>
     *   <li>TODO: covered by other agent's controller —— 同文件 forecast（:51，异常 :59-61）</li>
     *   <li>TODO: covered by other agent's controller —— 同文件 simulate（:65，异常 :90-92）</li>
     *   <li>TODO: covered by other agent's controller —— 同文件 policy（:96，异常 :104-106）</li>
     *   <li>TODO: covered by other agent's controller ——
     *       {@code controller/ScenarioOptionsController.java:206-209 unavailable()}</li>
     *   <li>已覆盖：本类 + {@code WorkspaceTestHarnessSmokeTest}（探针态 503）</li>
     * </ol>
     * 上述 5 处的改造契约 = 把 {@code ApiResponse.badRequest(...)/error(503,...)} 换成
     * 抛出 {@code com.chinacreator.gzcm.workspace.exception.CognitiveEngineUnavailableException}
     * （或 {@code ActionValidationException}），映射由本卷 advice 单源持有。
     */
    @Test
    @DisplayName("抛出侧契约类型必须可用（场景 Agent 的 import 目标）")
    void throwSideContractTypesArePublic() throws Exception {
        for (Class<?> c : new Class<?>[]{CognitiveEngineUnavailableException.class,
                ActionValidationException.class, ForbiddenOperationException.class,
                IslandBindingException.class, IllegalScenarioTransitionException.class,
                OptimisticLockConflictException.class, SandboxReferenceForbiddenException.class,
                ScenarioNotFoundException.class}) {
            assertEquals("com.chinacreator.gzcm.workspace.exception", c.getPackageName(),
                    c + " 必须在 exception 包（契约单源）");
            assertTruePublic(c);
        }
    }

    private static void assertTruePublic(Class<?> c) {
        org.junit.jupiter.api.Assertions.assertTrue(
                java.lang.reflect.Modifier.isPublic(c.getModifiers()), c + " 必须是 public 供跨包抛出");
    }

    private static MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new WorkspaceHarnessProbeController())
                .setControllerAdvice(new WorkspaceExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(MAPPER))
                .build();
    }

    private static Map<?, ?> json(MvcResult r) throws Exception {
        return MAPPER.readValue(r.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }
}
