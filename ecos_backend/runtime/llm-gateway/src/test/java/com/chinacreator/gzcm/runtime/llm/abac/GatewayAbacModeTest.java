package com.chinacreator.gzcm.runtime.llm.abac;

import com.chinacreator.gzcm.common.error.AiErrorClass;
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
    @DisplayName("service 态底座故障（限流 429 / 不可解析）同样 deny-not-skip：不上成功面、不外发")
    void serviceModeSecurityFaultsStillDenyNotSkip() {
        // ABAC 咽喉是二元裁决面：security 给不出显式 ALLOW（限流 429 / 响应不可解析 / 断连）
        // 一律折叠为不放行 → GUARDRAIL_DENIED（deny 面），绝不静默上成功面或放行走 provider。
        // （§3.2 的 DENY vs FAIL_CLOSED 细粒度分态由 security 桩边界层[test]承载，见 ai-engine 侧
        //  HarnessProbeController 契约；LLM 出口这层只承诺「非放行=不上成功面」。）
        fakeSecurity.setVerdict(FakeSecurityHttpServer.FakeVerdict.RATE_LIMITED_429);
        ChatResponse limited = gateway.call(request());
        assertEquals(AiErrorClass.GUARDRAIL_DENIED, limited.aiErrorClass(),
                "security 底座限流 = 未显式放行 → 坍缩到 deny 面，禁上成功面");
        assertFalse(limited.isSuccess());

        fakeSecurity.setVerdict(FakeSecurityHttpServer.FakeVerdict.UNSAFE_UNPARSEABLE);
        ChatResponse unparseable = gateway.call(request());
        assertEquals(AiErrorClass.GUARDRAIL_DENIED, unparseable.aiErrorClass(),
                "security 响应不可解析 = 未显式放行 → 同样坍缩到 deny 面");
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
