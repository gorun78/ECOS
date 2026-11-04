package com.chinacreator.gzcm.gateway.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 请求参数绑定失败必须落 4xx，不得被兜底写成 500（R7~R11 补册 §2.9.8 挂账 ⑥；#147AS）。
 *
 * <p><b>为什么需要这一门</b>：gateway 组合面里 sysman-boot 的 {@code GlobalExceptionHandler}
 * 被排除（`GatewayApplication` 的 excludeFilters），实际生效的是本包这份。它原先只声明了
 * {@code ValidationException} / {@code MethodArgumentNotValidException} 一类的 400 腿，
 * <b>没有</b> {@code MissingServletRequestParameterException} 与类型不匹配——于是「客户端少传/传错参数」
 * 一路落到 {@code Exception} 兜底，HTTP 面变成 <b>500 ECOS-SYS-500</b>。实测（18080 独立实例，
 * 2026-11-04）：{@code GET /api/v1/knowledge/articles/search}（缺 {@code q}）与
 * {@code ?q=abc&limit=xyz} 都是 500，而同端点带合法 {@code q} 是 200 ⇒ 状态行错误地指向「服务坏了」，
 * 客户端会按 5xx 重试/告警，真实语义是「调用方参数错」。</p>
 *
 * <p><b>它证明什么</b>：用 standalone MockMvc 走真实的参数解析 + advice 选择链（不是直调 handler 方法），
 * 三条腿分别钉：缺必填参数 → 400；类型不匹配 → 400；未识别异常仍是 500 ECOS-SYS-500（新增 400 腿
 * 不得把兜底挪走）。<b>它不证明什么</b>：不覆盖 kb 控制器真实业务链路（那是活体腿的事）。</p>
 *
 * <p>断言用「状态行 + body 文本」而非 {@code jsonPath}：本仓测试类路径无 Hamcrest，
 * {@code MockMvcResultMatchers} 的匹配器签名引用 {@code org.hamcrest.Matcher} 会编译失败。</p>
 *
 * @author ECOS gateway #147AS
 */
class RequestBindingFailureStatusTest {

    /** 探针控制器：只承载绑定失败的触发面，不参与任何业务。 */
    @RestController
    static class ProbeController {

        @GetMapping("/probe/search")
        String search(@RequestParam String q, @RequestParam(defaultValue = "20") int limit) {
            return q + ':' + limit;
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("probe boom");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("缺必填参数 → 400（不是兜底 500）")
    void missingRequiredParamIsBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe/search").accept(MediaType.APPLICATION_JSON)).andReturn();

        assertEquals(400, result.getResponse().getStatus(), "缺必填参数属调用方错误，状态行必须是 400");
        assertTrue(bodyOf(result).contains("\"code\":400"), "信封 code 必须与状态行一致: " + bodyOf(result));
    }

    @Test
    @DisplayName("参数类型不匹配 → 400（同族，不落 500）")
    void typeMismatchIsBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe/search")
                        .param("q", "abc")
                        .param("limit", "xyz")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEquals(400, result.getResponse().getStatus(), "参数类型错属调用方错误，状态行必须是 400");
        assertTrue(bodyOf(result).contains("\"code\":400"), "信封 code 必须与状态行一致: " + bodyOf(result));
    }

    @Test
    @DisplayName("回归：未识别异常仍是 500 ECOS-SYS-500（新增 400 腿不得改写兜底）")
    void unknownFailureStillServerError() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe/boom").accept(MediaType.APPLICATION_JSON)).andReturn();

        assertEquals(500, result.getResponse().getStatus());
        assertTrue(bodyOf(result).contains("ECOS-SYS-500"), "兜底机读码不得被新增腿改写: " + bodyOf(result));
    }
}
