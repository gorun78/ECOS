package com.chinacreator.gzcm.engine.ai.capability.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.chinacreator.gzcm.engine.ai.capability.CapabilityReadService;
import com.chinacreator.gzcm.engine.ai.capability.ModelCapabilityExecutor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * D-CLI-1 回归门禁：能力端点必须注册在**单前缀** {@code /api/v1/ai/capabilities/**}。
 *
 * <p><b>缺陷形态</b>：类级 {@code @RequestMapping(BASE_PREFIX)} 与方法常量（常量本身已含
 * {@code /api/v1/ai} 全路径）叠加 ⇒ 实际注册为 {@code /api/v1/ai/api/v1/ai/capabilities/**}，
 * 前端与 OpenAPI 契约使用的单前缀路径 404（2026-11-09 活体 OPTIONS 实测：单前缀 404 / 双前缀 200）。</p>
 *
 * <p><b>判据</b>：{@link MvcResult#getHandler()} 是否命中——未映射时 handler 为 null，
 * 已映射但业务 404（未知能力 ID）时 handler 非 null。因此本测试能区分「路由没注册」与
 * 「路由在、业务拒绝」两种 404，避免用状态码做假绿。</p>
 */
class AiCapabilityControllerPathContractTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CapabilityReadService readService = mock(CapabilityReadService.class);
        when(readService.listCapabilities()).thenReturn(List.of());
        ModelCapabilityExecutor executor = mock(ModelCapabilityExecutor.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new AiCapabilityController(readService, executor))
                .build();
    }

    @Test
    @DisplayName("单前缀四条端点均命中 handler（不存在的能力 ID 允许业务 404，但路由必须在）")
    void singlePrefixRoutesAreRegistered() throws Exception {
        MvcResult list = mvc.perform(get("/api/v1/ai/capabilities")).andReturn();
        assertNotNull(list.getHandler(), "GET /api/v1/ai/capabilities 未注册");
        assertEquals(200, list.getResponse().getStatus());

        MvcResult execute = mvc.perform(post("/api/v1/ai/capabilities/UNKNOWN_ID/execute")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertNotNull(execute.getHandler(), "POST /api/v1/ai/capabilities/{id}/execute 未注册");
        assertEquals(404, execute.getResponse().getStatus(), "未知能力 ID 应为业务 404");

        MvcResult tasks = mvc.perform(post("/api/v1/ai/capabilities/UNKNOWN_ID/tasks")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertNotNull(tasks.getHandler(), "POST /api/v1/ai/capabilities/{id}/tasks 未注册");
        assertEquals(404, tasks.getResponse().getStatus(), "未知能力 ID 应为业务 404");

        MvcResult status = mvc.perform(get("/api/v1/ai/capabilities/tasks/t-1/status")).andReturn();
        assertNotNull(status.getHandler(), "GET /api/v1/ai/capabilities/tasks/{taskId}/status 未注册");
    }

    @Test
    @DisplayName("双前缀路径必须不命中（防类级 @RequestMapping 前缀回归）")
    void doubledPrefixMustNotBeRegistered() throws Exception {
        assertNull(mvc.perform(get("/api/v1/ai/api/v1/ai/capabilities")).andReturn().getHandler(),
                "双前缀 GET 仍被注册 ⇒ 类级映射前缀回归（D-CLI-1）");
        assertNull(mvc.perform(post("/api/v1/ai/api/v1/ai/capabilities/UNKNOWN_ID/execute")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getHandler(),
                "双前缀 execute 仍被注册 ⇒ 类级映射前缀回归（D-CLI-1）");
    }
}
