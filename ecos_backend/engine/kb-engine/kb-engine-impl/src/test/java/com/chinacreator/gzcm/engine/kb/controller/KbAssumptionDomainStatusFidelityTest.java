package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.kb.service.assumption.KbAssumptionDO;
import com.chinacreator.gzcm.engine.kb.service.assumption.KbAssumptionService;
import com.chinacreator.gzcm.engine.kb.service.assumption.KbAssumptionStore;
import com.chinacreator.gzcm.engine.kb.support.KbErrorAdvice;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * kb 域「状态行保真」行为门（#142/B4-3e-30，收口差距 <b>D-B4-67 前半＋后半</b>）。
 *
 * <p><b>缺陷形态</b>：D.3 把「409 非法跃迁／403 非知识管理员／410 假设已过期」写进契约，
 * 但渲染这两枚态的两条腿都不把态落到 HTTP 状态行——
 * <ul>
 *   <li>后半：{@code KbAssumptionController#mapDomain} 返裸 {@code ApiResponse}（Spring 落
 *       <b>HTTP 200</b>），拒绝语义只活在信封 {@code code} 里；并且它自写
 *       {@code e.getHttpStatus() == 0 ? 500 : …} 作第二判据源，与 common-api 单源可各自漂移。</li>
 *   <li>前半：{@code KbErrorAdvice#onBridge} 恒写 502，把别域携带的 403 策略拒绝／503 不可用
 *       折成「上游坏掉」，客户端按状态行分派时会重试一枚本不该重试的 DENY（§4.7.2
 *       「不可用 ≠ 无权限」的反向折叠面）。</li>
 * </ul>
 *
 * <p><b>本门判什么</b>：只判<b>状态行</b>与<b>携带态的同号性</b>，不判业务语义——
 * 信封字段（{@code code}/{@code errorCode}/{@code message}）保持既有形态，路径与入参签名不变
 * （API 只增不改红线）。合法 4xx/5xx 逐字透出；区间外的携带值（0/负数/2xx/6xx/7xx）一律回退 500；
 * 任何一条腿退回头＝本门即刻变红（无注解锁死、无第二判据源、无恒 502）。
 *
 * <p><b>取证方式</b>：{@code standaloneSetup}（模块既有先例 {@code KnowledgeCognitionSupplyControllerTest}），
 * 显式 {@code Accept: application/json} 钉住 JSON；状态行读 {@code MvcResult#getResponse().getStatus()}，
 * 不经 body 码反推——反推正是 D-B4-53 已否决的修法。
 */
@ExtendWith(MockitoExtension.class)
class KbAssumptionDomainStatusFidelityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String BASE = "/api/v1/knowledge/assumptions";

    /** 单源回退值（与 {@code DataBridgeException.resolveHttpStatus} 一致，本门不得自写区间）。 */
    private static final int FALLBACK = 500;

    @Mock
    private KbAssumptionService service;

    @Mock
    private KbAssumptionStore store;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new KbAssumptionController(service, store))
                .build();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req) {
        return req.header("Accept", MediaType.APPLICATION_JSON_VALUE);
    }

    private static JsonNode body(MvcResult res) throws Exception {
        String content = res.getResponse().getContentAsString();
        try {
            return MAPPER.readTree(content);
        } catch (Exception e) {
            throw new AssertionError("非 JSON 响应体: " + content.substring(0, Math.min(400, content.length())), e);
        }
    }

    private static KbErrorCodes.DomainException domain(int status, String code) {
        return new KbErrorCodes.DomainException(status, code, "kb 域拒绝：" + status + "/" + code);
    }

    // ── 腿 1：拒绝态必须落到状态行（后半的主面） ──────────────────────────

    @Test
    @DisplayName("①409/403/410 领域拒绝态落真状态行，且 body code 与状态行同号（收口前恒 200）")
    void domainRejectionsRideTheStatusLine() throws Exception {
        // 每枚用例用独立 id：同参数重复 when(...) 会真的调用 mock 并把上一条 thenThrow 抛出（首跑实测）
        Object[][] cases = {
                {"a-409", 409, "ECOS-KB-060", "submit"},
                {"a-403", 403, "ECOS-KB-062", "approve"},
                {"a-410", 410, "ECOS-KB-072", "submit"},
        };
        for (Object[] c : cases) {
            String id = (String) c[0];
            int status = (Integer) c[1];
            String code = (String) c[2];
            String action = (String) c[3];
            if ("submit".equals(action)) {
                when(service.submit(id, "tester")).thenThrow(domain(status, code));
            } else {
                when(service.approve(id, "tester")).thenThrow(domain(status, code));
            }
            MvcResult res = mockMvc.perform(json(post(BASE + "/" + id + "/" + action)
                    .header("X-Ecos-User", "tester"))).andReturn();

            assertEquals(status, res.getResponse().getStatus(),
                    action + " 腿的 " + status + " 未上状态行（恒 200＝D-B4-67 后半复现：前端 if (!res.ok) 读成成功）");
            JsonNode b = body(res);
            assertEquals(status, b.get("code").asInt(),
                    action + " 腿 body code 与状态行不同号");
            assertEquals(code, b.get("errorCode").asText(),
                    action + " 腿机读码必须取异常自带的 ECOS-KB-***");
        }
    }

    // ── 腿 2：成功腿不得被修法误伤 ────────────────────────────────────────

    @Test
    @DisplayName("②成功腿仍 200（修法只补失败态状态行，禁把成功折成 4xx/5xx）")
    void successLegStaysTwentyHundred() throws Exception {
        when(service.submit("a-2", "tester")).thenReturn(new KbAssumptionDO());
        MvcResult res = mockMvc.perform(json(post(BASE + "/a-2/submit")
                .header("X-Ecos-User", "tester"))).andReturn();
        assertEquals(200, res.getResponse().getStatus(), "成功腿的状态行被改坏＝API 面被动过");
    }

    // ── 腿 3：区间裁决只经单源 ────────────────────────────────────────────

    @Test
    @DisplayName("③非法携带值（0/负数/2xx/6xx/7xx）一律回退 500；合法携带值状态行恒 ≥400")
    void illegalCarriedStatusFallsBackToServerDefect() throws Exception {
        for (int bad : new int[] {0, -1, -100, 200, 302, 600, 700}) {
            String id = "bad-" + bad;
            when(service.submit(id, "tester")).thenThrow(domain(bad, "ECOS-KB-060"));
            MvcResult res = mockMvc.perform(json(post(BASE + "/" + id + "/submit")
                    .header("X-Ecos-User", "tester"))).andReturn();
            assertEquals(FALLBACK, res.getResponse().getStatus(),
                    "携带值 " + bad + " 未走单源区间裁决（越界值原样上状态行＝第二判据源）");
            assertEquals(FALLBACK, body(res).get("code").asInt(),
                    "回退态的 body code 必须与状态行同号");
        }
        for (int legal : new int[] {400, 403, 409, 410, 500, 502, 503}) {
            String id = "legal-" + legal;
            when(service.submit(id, "tester")).thenThrow(domain(legal, "ECOS-KB-060"));
            MvcResult res = mockMvc.perform(json(post(BASE + "/" + id + "/submit")
                    .header("X-Ecos-User", "tester"))).andReturn();
            assertTrue(res.getResponse().getStatus() >= 400,
                    "携带值 " + legal + " 下状态行退回头 200（裸回成功＝拒绝被渲染成成功）");
            assertEquals(legal, res.getResponse().getStatus(), "合法携带值必须逐字透出");
        }
    }

    // ── 腿 4：onBridge 不再恒 502（前半的主面） ──────────────────────────

    @Test
    @DisplayName("④onBridge 跟随别域自述状态：403 报 403、503 报 503，禁折成恒 502")
    void bridgeLegFollowsTheCarrierInsteadOfHard502() {
        KbErrorAdvice advice = new KbErrorAdvice();

        ResponseEntity<ApiResponse<Void>> deny =
                advice.onBridge(new DataBridgeException(403, 403, "策略 DENY"));
        assertEquals(403, deny.getStatusCode().value(),
                "别域 403 又被报成 502＝§4.7.2 反向折叠复现（客户端会把 DENY 当上游故障重试）");
        assertEquals("ECOS-KB-501", deny.getBody() == null ? null : deny.getBody().getErrorCode(),
                "渲染域归属仍须由机读码承载（状态行改跟携带值不等于丢掉归属证据）");

        ResponseEntity<ApiResponse<Void>> unavailable =
                advice.onBridge(new DataBridgeException(503, 503, "安全引擎不可用"));
        assertEquals(503, unavailable.getStatusCode().value(), "别域 503 不可用态必须与拒绝态分态");

        for (int bad : new int[] {0, -1, 200, 302, 600, 700}) {
            ResponseEntity<ApiResponse<Void>> fallback =
                    advice.onBridge(new DataBridgeException(bad, bad, "越界携带值"));
            assertEquals(FALLBACK, fallback.getStatusCode().value(),
                    "越界携带值 " + bad + " 未走单源回退（502 硬编码被改回硬编码其它值同样红）");
        }
    }

    // ── 腿 5：静态回退面（防止下一版把修法摘掉） ──────────────────────────

    @Test
    @DisplayName("⑤两腿的源码形态锚：走单源、无第二判据源、返体为 ResponseEntity")
    void sourceShapeStaysOnTheSingleSourcePath() throws Exception {
        String controller = readRepoFile("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/"
                + "com/chinacreator/gzcm/engine/kb/controller/KbAssumptionController.java");
        assertTrue(controller.contains("DataBridgeException.resolveHttpStatus("),
                "mapDomain 不再经 common-api 单源裁决＝判据回到本域第二源");
        assertFalse(controller.contains("getHttpStatus() == 0"),
                "「== 0 ? 500 : 携带值」第二判据源复现（只判零不判区间）");
        assertTrue(controller.contains("ResponseEntity<ApiResponse<KbAssumptionDO>> submit(")
                        && controller.contains("ResponseEntity<ApiResponse<KbAssumptionDO>> approve("),
                "两条失败腿的返体又退回裸 ApiResponse＝状态行将恒 200");

        String advice = readRepoFile("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/"
                + "com/chinacreator/gzcm/engine/kb/support/KbErrorAdvice.java");
        assertFalse(advice.contains("HttpStatus.BAD_GATEWAY"),
                "onBridge 的恒 502 复现（别域 403/503 将被折成上游故障）");

        for (Method m : KbAssumptionController.class.getDeclaredMethods()) {
            if ("submit".equals(m.getName()) || "approve".equals(m.getName())) {
                assertEquals(ResponseEntity.class, m.getReturnType(),
                        m.getName() + " 返回类型已变更＝D-B4-67 后半回退");
            }
        }
    }

    private static String readRepoFile(String relative) throws Exception {
        Path p = repoRoot().resolve(relative);
        assertTrue(Files.isRegularFile(p), "门禁依赖的文件不存在，不得当作\"已收口\": " + relative);
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static Path repoRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && start != null; i++) {
            if (Files.isDirectory(start.resolve("ecos_backend"))
                    && Files.isDirectory(start.resolve("ecos-docker"))) {
                return start;
            }
            start = start.getParent();
        }
        throw new IllegalStateException("未能从 user.dir 定位仓根（需同时含 ecos_backend 与 ecos-docker）");
    }
}
