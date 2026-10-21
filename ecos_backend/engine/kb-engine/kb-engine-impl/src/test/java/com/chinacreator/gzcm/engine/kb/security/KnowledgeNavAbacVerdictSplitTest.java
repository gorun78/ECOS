package com.chinacreator.gzcm.engine.kb.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.Verdict;
import com.chinacreator.gzcm.common.security.header.ServiceContext;
import com.chinacreator.gzcm.engine.kb.dao.NavTaxonDao;
import com.chinacreator.gzcm.engine.kb.nav.model.NavCategorySaveDTO;
import com.chinacreator.gzcm.engine.kb.service.nav.NavLlmRecommendService;
import com.chinacreator.gzcm.engine.kb.service.nav.NavTaxonServiceImpl;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;

/**
 * #142/B4-3e-12（差距 <b>D-B4-44</b> kb 导航写闸腿）—— 裁决三态读数与分态落面的不变式。
 *
 * <p><b>本文件只钉一件事</b>：{@code DENY}（策略判了拒绝）与 {@code UNEVALUATED}
 * （护栏没说话）在同一条写入口上<b>必须产生不同的状态串</b>（§4.7.2「不可用 ≠ 无权限」）。
 * 收口前 {@link KnowledgeNavSecurityEngineClient#evaluatePolicy} 返回 {@code boolean}，
 * 四种"护栏没说话"与真实策略 DENY 都是同一个 {@code false}，消费点统一转 403 ——
 * 链路缺陷被伪装成业务结论，上层拿到 403 就不再报依赖故障。</p>
 *
 * <p><b>为什么这组断言"能变红"</b>：它比较的是两态的<b>落面结果</b>
 * （异常类／httpStatus／机读码），不是各自文案。一旦有人把 UNEVALUATED 腿重新折回
 * {@code ForbiddenException} 或把客户端读数改回
 * {@link AbacEvaluateResponseContract#isExplicitAllow}，两侧三元组立刻相同 ⇒ 本文件红；
 * 全仓静态棘轮门 {@code AbacEvaluateResponseSingleSourceTest#unevaluatedFoldingSitesAreRatcheted}
 * 同时红。</p>
 */
@DisplayName("D-B4-44 kb 腿：ABAC 三态读数 + DENY/UNEVALUATED 分态不变式")
class KnowledgeNavAbacVerdictSplitTest {

    @AfterEach
    void clearContext() {
        ServiceContext.clear();
    }

    // ── ① 客户端读数面：同一 action，四支"护栏没说话"不得读成 DENY ──

    @Test
    @DisplayName("客户端：现契约应答 allow=false 读成 DENY；四种不可识别形态读成 UNEVALUATED（两态不得同值）")
    void clientReadsDenyAndUnevaluatedApart() {
        Verdict denied = evaluateWith(envelope(Map.of("allow", false)));
        assertEquals(Verdict.DENY, denied, "allow=false 是真策略判定 ⇒ DENY");

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put(AbacEvaluateResponseContract.KEY_SOURCE,
                AbacEvaluateResponseContract.VALUE_SOURCE_FALLBACK);
        fallback.put(AbacEvaluateResponseContract.KEY_FALLBACK,
                AbacEvaluateResponseContract.VALUE_FALLBACK_DENY_OPA_DOWN);
        fallback.put(AbacEvaluateResponseContract.KEY_ALLOW, false);

        Map<String, Verdict> unevaluated = new LinkedHashMap<>();
        unevaluated.put("source=fallback 兜底态", evaluateWith(envelope(fallback)));
        unevaluated.put("应答缺 data 段", evaluateWith(Map.of("code", 0)));
        unevaluated.put("security 侧 HTTP 200 + 失败信封", evaluateWith(Map.of(
                "code", 503, "message", RlsFailureEnvelopeSample.MESSAGE)));
        unevaluated.put("data 段无任何可识别裁决键",
                evaluateWith(envelope(Map.of("policyState", "NOT_EVALUATED"))));

        for (Map.Entry<String, Verdict> e : unevaluated.entrySet()) {
            assertEquals(Verdict.UNEVALUATED, e.getValue(),
                    e.getKey() + " 属\"护栏没说话\"，不得读成策略 DENY（§4.7.2）");
            assertNotEquals(denied, e.getValue(),
                    e.getKey() + " 与 DENY 同值＝D-B4-44 的折叠形态复发");
        }
    }

    @Test
    @DisplayName("客户端：RestTemplate 未装配／传输异常 → UNEVALUATED（不是 DENY，也不放行）")
    void transportFailureIsUnevaluatedNotDeny() {
        KnowledgeNavSecurityEngineClient noClient =
                new KnowledgeNavSecurityEngineClient("http://localhost:18081", 100, null, null);
        assertEquals(Verdict.UNEVALUATED, noClient.evaluatePolicy("kb.category.create", Map.of()),
                "无 security-engine 环境＝护栏缺席，必须与策略拒绝分态");

        RestTemplate throwing = mock(RestTemplate.class);
        when(throwing.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new IllegalStateException("connect refused"));
        KnowledgeNavSecurityEngineClient client =
                new KnowledgeNavSecurityEngineClient("http://localhost:18081", 100, throwing, null);
        assertEquals(Verdict.UNEVALUATED, client.evaluatePolicy("kb.category.create", Map.of()),
                "传输故障＝没拿到裁决，禁折叠成 DENY/403");
    }

    // ── ② 消费点落面：同一个写入口，两态三元组必异 ──

    @Test
    @DisplayName("写入口：DENY→403 与 UNEVALUATED→503/ECOS-KB-020 状态串必异，两态都不落库")
    void writeEntryRendersTheTwoStatesDifferently() {
        KnowledgeNavSecurityEngineClient security = mock(KnowledgeNavSecurityEngineClient.class);
        NavTaxonDao dao = mock(NavTaxonDao.class);
        NavTaxonServiceImpl service =
                new NavTaxonServiceImpl(dao, security, mock(NavLlmRecommendService.class));
        ServiceContext.set(ServiceContext.of("trace-1", "u-42", "knowledge_admin",
                "t-1", "finance", "kb-engine"));

        when(security.evaluatePolicy(anyString(), any())).thenReturn(Verdict.DENY);
        ForbiddenException denied = assertThrows(ForbiddenException.class,
                () -> service.createCategory(categoryDto()),
                "策略 DENY → 403 无权限");

        when(security.evaluatePolicy(anyString(), any())).thenReturn(Verdict.UNEVALUATED);
        KbErrorCodes.DomainException unavailable = assertThrows(KbErrorCodes.DomainException.class,
                () -> service.createCategory(categoryDto()),
                "UNEVALUATED → 不可用态（KbErrorAdvice 以真实 HTTP 503 渲染）");

        assertEquals(403, denied.getHttpStatus(), "DENY 才映射 403");
        assertEquals(KbErrorCodes.SECURITY_UNAVAILABLE.httpStatus(), unavailable.getHttpStatus(),
                "不可用态取 ECOS-KB-020 的 503");
        assertEquals(KbErrorCodes.SECURITY_UNAVAILABLE.code(), unavailable.getCode());
        assertNotEquals(denied.getHttpStatus(), unavailable.getHttpStatus(),
                "两态 httpStatus 相同＝把\"引擎没答\"冒充成\"这人没数据\"（§4.7.2 禁止的折叠）");
        assertNotEquals(denied.getClass(), unavailable.getClass());
        assertNotEquals(denied.getMessage(), unavailable.getMessage());
        verify(dao, never()).insertCategory(anyString(), any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                anyString());
    }

    // ── helpers ──

    /** {@code ApiResponse} 成功信封（code=0）里塞入给定 data 段。 */
    private static Map<String, Object> envelope(Map<String, Object> data) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "ok");
        resp.put("data", data);
        return resp;
    }

    private static Verdict evaluateWith(Map<String, Object> respBody) {
        RestTemplate rt = mock(RestTemplate.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> ok = ResponseEntity.ok(respBody);
        when(rt.postForEntity(anyString(), any(), eq(Map.class))).thenReturn(ok);
        return new KnowledgeNavSecurityEngineClient("http://localhost:18081", 100, rt, null)
                .evaluatePolicy("kb.category.create", new HashMap<>());
    }

    private static NavCategorySaveDTO categoryDto() {
        NavCategorySaveDTO dto = new NavCategorySaveDTO();
        dto.setDomain("finance");
        dto.setName("还款策略");
        return dto;
    }

    /** 失败信封样本：security 侧内部裁决失败以 HTTP 200 + 该形态应答（无 data 段）。 */
    private static final class RlsFailureEnvelopeSample {
        static final String MESSAGE = "ECOS-SEC-501: 安全裁决服务不可用";

        private RlsFailureEnvelopeSample() {
        }
    }
}
