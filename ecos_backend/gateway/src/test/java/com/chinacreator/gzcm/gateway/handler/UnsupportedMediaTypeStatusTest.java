package com.chinacreator.gzcm.gateway.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 不支持的媒体类型必须落 415，不得被兜底写成 500（2026-11-13 验证缺陷 D47）。
 *
 * <p><b>为什么需要这一门</b>：端点上写的 {@code consumes = MULTIPART_FORM_DATA_VALUE}
 * 在收到 JSON/text 请求时由 Spring MVC 抛 {@code HttpMediaTypeNotSupportedException}；
 * 网关组合面原先<b>没有</b>这条腿，异常一路落到 {@code handleAny} → <b>500 ECOS-SYS-500</b>。
 * 实测证据（本批）：{@code POST /api/v1/knowledge/ingest/documents} 带
 * {@code Content-Type: application/json} 经网关 → 500；<b>同一请求直连 18086 → 415</b>。
 * Spring 的默认行为本来是对的，丢语义的是网关这层异常映射。</p>
 *
 * <p><b>它证明什么</b>：用 standalone MockMvc 走真实的 handler 选择 + advice 选择链
 * （不直调 handler 方法），钉住三条腿：JSON 打 multipart-only 端点 → 415；
 * {@code Accept} 不可满足 → 406 语义不进 500 兜底（本类不声明 406 腿，此处只钉 multipart 主腿）；
 * 未识别异常仍是 500（新增腿不得挪走兜底）。</p>
 *
 * <p>断言用「状态行 + body 文本」而非 {@code jsonPath}：本仓测试类路径无 Hamcrest。</p>
 */
class UnsupportedMediaTypeStatusTest {

    /** 探针控制器：只承载 consumes 触发面，不参与任何业务。 */
    @RestController
    static class ProbeController {

        @PostMapping(value = "/probe/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        String upload(@RequestBody(required = false) String body) {
            return "ok";
        }

        @PostMapping(value = "/probe/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        String json(@RequestBody String body) {
            return "json:" + body;
        }

        @PostMapping("/probe/boom")
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
    @DisplayName("JSON 打 multipart-only 端点 → 415 + ECOS-SYS-415（不是兜底 500）")
    void jsonAgainstMultipartEndpointIsUnsupportedMediaType() throws Exception {
        MvcResult result = mockMvc.perform(post("/probe/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file\":\"evil.jsp\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEquals(415, result.getResponse().getStatus(),
                "media type 不匹配属调用方错误，状态行必须是 415（D47）: " + bodyOf(result));
        assertTrue(bodyOf(result).contains("\"code\":415"),
                "信封 code 必须与状态行一致: " + bodyOf(result));
        assertTrue(bodyOf(result).contains("ECOS-SYS-415"),
                "机读码必须是登记码 ECOS-SYS-415: " + bodyOf(result));
    }

    @Test
    @DisplayName("text/plain 打 multipart-only 端点 → 415（同族，不落 500）")
    void plainTextAgainstMultipartEndpointIsUnsupportedMediaType() throws Exception {
        MvcResult result = mockMvc.perform(post("/probe/upload")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("evil")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEquals(415, result.getResponse().getStatus(), "同族腿: " + bodyOf(result));
    }

    @Test
    @DisplayName("回归：正常 Content-Type 仍走通，新增 415 腿不劫持合法请求")
    void matchingContentTypeStillWorks() throws Exception {
        MvcResult result = mockMvc.perform(post("/probe/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"a\":1}")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus(), "合法请求不得被 415 腿劫持: " + bodyOf(result));
    }

    @Test
    @DisplayName("回归：未识别异常仍是 500 ECOS-SYS-500（新增 415 腿不得改写兜底）")
    void unknownFailureStillServerError() throws Exception {
        MvcResult result = mockMvc.perform(post("/probe/boom").accept(MediaType.APPLICATION_JSON)).andReturn();

        assertEquals(500, result.getResponse().getStatus());
        assertTrue(bodyOf(result).contains("ECOS-SYS-500"), "兜底机读码不得被新增腿改写: " + bodyOf(result));
    }
}
