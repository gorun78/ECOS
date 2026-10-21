package com.chinacreator.gzcm.runtime.llm.abac;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGatewayImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-22 验收（要点 2 两态之「service 态」侧）——
 * {@code GatewayAbacModeTest#abacDenyIsNotSilentlySkippedInServiceMode}。
 *
 * <p><b>反假阳性目标（§8.4 4.5 矩阵 / R-18 / X-26）</b>：证明 ABAC 咽喉在
 * <b>aiming service 簇</b>（{@code @SpringBootTest}(profile=abac-service) 薄切片）
 * 里同样<b>真</b>生效——桩翻 DENY 时 {@link LLMGatewayImpl#call} 必须落
 * {@link AiErrorClass#GUARDRAIL_DENIED} 且<b>零外发、零重试</b>，而非被吞成
 * 成功、被折叠成 200 空体、或被退避循环放大成绕过面。桩边界（security REST）
 * 用 {@link FakeSecurityHttpServer} 真实 HTTP 三态，生产代码经真实 OkHttp 打进
 * 桩端口，测试只翻 verdict 枚举、<b>不</b> Mockito 化 evaluate 布尔。</p>
 *
 * <p>本用例与 ai-engine-impl 侧 {@code AiTestHarnessSmokeTest}（fat-JAR 态）成对：
 * 同一条「DENY 不静默跳过」契约在两种部署形态各跑一次，杜绝"gateway 态测过
 * = 能力存在"的假阳性。</p>
 *
 * <p><b>#142/B4-3e-14（差距 D-B4-44 末腿）</b>：新增
 * {@link #denyAndUnavailableRenderDifferentStatesOnSameRequest()} 钉住 §4.7.2 的不变式 ——
 * 同一条请求在「策略说不行」与「护栏没说话」两态下，错误分类／码面／原因文案必异。
 * 收口前桥把三态折成布尔、消费方一律报 GUARDRAIL_DENIED，本用例即使存在也只能断言两态同值；
 * 原 {@code serviceModeSecurityFaultsStillDenyNotSkip} 把该折叠写成"刻意设计"，
 * 现已按 §4.7.2 改判为不可用态（FAIL_CLOSED／503）。</p>
 */
@SpringBootTest(classes = LlmAbacSliceConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("abac-service")
class GatewayAbacModeTest {

    private static final String MODEL = "deepseek-chat";

    @Autowired
    private LLMGatewayImpl gateway;

    @Autowired
    private FakeSecurityHttpServer fakeSecurity;

    @BeforeEach
    void resetStub() {
        fakeSecurity.setVerdict(FakeSecurityHttpServer.FakeVerdict.ALLOW);
    }

    @Test
    @DisplayName("service 态 ABAC DENY 不静默跳过：落 GUARDRAIL_DENIED、零外发、零重试")
    void abacDenyIsNotSilentlySkippedInServiceMode() {
        // 0) 先证切片是活的：ALLOW 桩下咽喉可达（evaluate 真打桩一次）
        fakeSecurity.setVerdict(FakeSecurityHttpServer.FakeVerdict.ALLOW);
        int baseline = fakeSecurity.hits();

        // 1) 桩翻 DENY —— 咽喉必须拦截
        fakeSecurity.setVerdict(FakeSecurityHttpServer.FakeVerdict.DENY);
        ChatResponse denied = gateway.call(request());

        assertFalse(denied.isSuccess(), "service 态 DENY 必须返回失败，禁折叠成成功/空体");
        assertEquals(AiErrorClass.GUARDRAIL_DENIED, denied.aiErrorClass(),
                "被拒必须归 GUARDRAIL_DENIED（§3.2：与 FAIL_CLOSED 严格分态）");
        assertEquals(0, denied.getRetriesAttempted(),
                "护栏拒绝属 0 次类别，禁被退避循环放大成绕过面");
        // DENY 时 evaluate 仍打桩一次（裁决确实发生），但绝不外发任何 provider 调用。
        assertTrue(fakeSecurity.hits() > baseline,
                "service 态必须真经 security REST 裁决（桩命中应增加）");
    }

    @Test
    @DisplayName("D-B4-44 末腿不变式：同一条请求在 DENY 与不可用两态下，错误分类与状态串必异（§4.7.2）")
    void denyAndUnavailableRenderDifferentStatesOnSameRequest() {
        ChatRequest request = request();

        fakeSecurity.setVerdict(FakeSecurityHttpServer.FakeVerdict.DENY);
        ChatResponse denied = gateway.call(request);

        fakeSecurity.setVerdict(FakeSecurityHttpServer.FakeVerdict.UNSAFE_UNPARSEABLE);
        ChatResponse unavailable = gateway.call(request);

        assertFalse(denied.isSuccess(), "策略拒绝不得放行");
        assertFalse(unavailable.isSuccess(), "护栏不可用同样不得放行（fail-closed 不因分态而放宽）");
        assertEquals(AiErrorClass.GUARDRAIL_DENIED, denied.aiErrorClass(),
                "DENY 腿保持收口前形态：403 策略拒绝");
        assertEquals(AiErrorClass.GUARDRAIL_FAIL_CLOSED, unavailable.aiErrorClass(),
                "不可用腿必须落 FAIL_CLOSED（收口前它和 DENY 同值，就是 D-B4-44 的折叠形态）");
        assertNotEquals(denied.aiErrorClass(), unavailable.aiErrorClass(),
                "§4.7.2「不可用 ≠ 无权限」：两态的错误分类不得相同");
        assertNotEquals(denied.getErrorCode(), unavailable.getErrorCode(),
                "码面（ECOS-AI-*）随分类单源漂移，两态必异");
        assertNotEquals(denied.getErrorMsg(), unavailable.getErrorMsg(),
                "原因文案必异：不可用态要点明「护栏没说话」，禁冒充用户无权限");
        assertTrue(unavailable.getErrorMsg().contains(RlsExecutionContract.SEC_UNAVAILABLE_CODE),
                "不可用态须带跨域机读码 " + RlsExecutionContract.SEC_UNAVAILABLE_CODE
                        + "（与 pipeline／本体／KB 三支同码面）: " + unavailable.getErrorMsg());
    }

    @Test
    @DisplayName("service 态底座故障（限流 429 / 不可解析）→ 不可用态：落 FAIL_CLOSED、不上成功面、不外发")
    void serviceModeSecurityFaultsAreFailClosedNotDeny() {
        // #142/B4-3e-14 订正本用例原口径：原注释声称"折叠成 deny 面"是刻意设计，
        // 实际正是差距 D-B4-44 —— security 给不出明确裁决（限流／不可解析）＝护栏没说话，
        // 按 §4.7.2 只能报不可用（503 FAIL_CLOSED），不得冒充策略拒绝（403）。
        // 桩的 FakeVerdict 注释（"生产代码须映射 FAIL_CLOSED 而非 DENY"）自此与生产代码一致。
        fakeSecurity.setVerdict(FakeSecurityHttpServer.FakeVerdict.RATE_LIMITED_429);
        ChatResponse limited = gateway.call(request());
        assertEquals(AiErrorClass.GUARDRAIL_FAIL_CLOSED, limited.aiErrorClass(),
                "security 底座限流 = 未给出裁决 → 不可用态，禁落 403 策略拒绝");
        assertFalse(limited.isSuccess(), "不可用同样不放行（fail-closed 不变）");

        fakeSecurity.setVerdict(FakeSecurityHttpServer.FakeVerdict.UNSAFE_UNPARSEABLE);
        ChatResponse unparseable = gateway.call(request());
        assertEquals(AiErrorClass.GUARDRAIL_FAIL_CLOSED, unparseable.aiErrorClass(),
                "security 响应不可解析 = 未给出裁决 → 同样落不可用态");
        assertFalse(unparseable.isSuccess());
    }

    private static ChatRequest request() {
        ChatRequest request = new ChatRequest();
        request.setModel(MODEL);
        request.setMessages(List.of(new ChatMessage("user", "ABAC 咽喉探针")));
        request.setTemperature(0.0);
        request.setMaxTokens(16);
        request.setStream(false);
        request.setCallerId("service:abac-slice");
        return request;
    }
}
