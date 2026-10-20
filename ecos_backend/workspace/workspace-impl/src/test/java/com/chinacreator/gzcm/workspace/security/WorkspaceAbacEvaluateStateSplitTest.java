package com.chinacreator.gzcm.workspace.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import com.chinacreator.gzcm.workspace.scenario.SecurityEngineMigrationPermissionChecker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * workspace 导出咽喉的<b>不可用／策略拒绝分态</b>验收（#142/B4-3e-6，收口差距 <b>D-B4-41</b>）。
 *
 * <p><b>缺陷形态</b>：{@link WorkspaceSecurityEngineClient#evaluate} 收口前直接返回
 * {@code AbacEvaluateResponseContract.isExplicitAllow(...)}。该读数按契约定义对
 * {@code DENY} 与 {@code UNEVALUATED} <b>都</b>返回 {@code false}（fail-closed 方向正确），
 * 但调用方只看到一个布尔，于是 security-engine 以 HTTP <b>200</b> 返回的兜底应答
 * （{@code source=fallback} + {@code fallback=DENY_OPA_DOWN}）与 {@code allow} 键缺失的形态漂移，
 * 全被折成"策略拒绝"→ 403。而 {@code postJson} 的可用性分态只覆盖传输层
 * （连不上／超时／5xx），HTTP 200 的"护栏没说话"从不经过它 ⇒ 分态面出现结构性空洞。</p>
 *
 * <p><b>本类的判据即不变式</b>：同一份输入下，DENY 与 UNEVALUATED 两条腿的
 * 用户可见状态串（HTTP 状态 + 业务 code）必须不同 —— 前者 403/{@code PERMISSION_DENIED}，
 * 后者 503/{@code ECOS-SEC-501}。§4.7.2「不可用 ≠ 无权限」。</p>
 */
class WorkspaceAbacEvaluateStateSplitTest {

    private static final String EVALUATE_PATH = "/api/v1/security/policy-engine/evaluate";

    @SuppressWarnings("unchecked")
    private static WorkspaceSecurityEngineClient clientWith(Map<String, Object> envelope) {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(envelope));
        @SuppressWarnings("unchecked")
        ObjectProvider<EventBusService> busProvider = mock(ObjectProvider.class);
        when(busProvider.getIfAvailable()).thenReturn(null);
        return new WorkspaceSecurityEngineClient(rest, busProvider);
    }

    private static Map<String, Object> envelope(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("message", "OK");
        m.put("data", data);
        return m;
    }

    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("策略明确 DENY（allow=false）→ false，由调用方转 403，不得报不可用")
    void explicitPolicyDenyStaysDeny() {
        assertFalse(clientWith(envelope(data("allow", false,
                "policyRef", "rbac", "inputDigest", "digest-1")))
                .evaluate("audit:export", Map.of("resource", "forecast_run")));
    }

    @Test
    @DisplayName("HTTP 200 兜底应答（source=fallback/DENY_OPA_DOWN）→ 503 独立态，禁冒充策略拒绝")
    void fallbackEnvelopeOnHttp200IsUnavailableNotDeny() {
        SecurityEngineUnavailableException ex = assertThrows(SecurityEngineUnavailableException.class,
                () -> clientWith(envelope(data("allow", false,
                        "source", "fallback", "fallback", "DENY_OPA_DOWN")))
                        .evaluate("audit:export", Map.of("resource", "forecast_run")));
        assertEquals(503, ex.getHttpStatus());
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_CODE, ex.getCode());
    }

    @Test
    @DisplayName("allow 键缺失／data 段缺失／值形态不可识别 → 同归 UNEVALUATED→503")
    void silentFormsAllMapToUnavailable() {
        Map<String, Object> dataOnly = envelope(data("policyRef", "rbac", "inputDigest", "digest-1"));
        assertThrows(SecurityEngineUnavailableException.class,
                () -> clientWith(dataOnly).evaluate("audit:export", null));
        assertThrows(SecurityEngineUnavailableException.class,
                () -> clientWith(envelope(data("allow", "maybe"))).evaluate("audit:export", null));
        assertThrows(SecurityEngineUnavailableException.class,
                () -> clientWith(envelope(null)).evaluate("audit:export", null));
        assertThrows(SecurityEngineUnavailableException.class,
                () -> clientWith(envelope(data("policyState", "POLICY_UNDEFINED")))
                        .evaluate("audit:export", null));
    }

    @Test
    @DisplayName("不变式：同输入下 DENY 与 UNEVALUATED 的状态串必异（403/PERMISSION_DENIED ↔ 503/ECOS-SEC-501）")
    void denyAndUnevaluatedStateStringsMustDiffer() {
        // 同一裁决（同 policyRef + inputDigest），只差 allow 是否在场
        Map<String, Object> deniedForm = data("allow", false, "policyRef", "rbac", "inputDigest", "d-1");
        Map<String, Object> unevaluatedForm = data("policyRef", "rbac", "inputDigest", "d-1");

        assertFalse(clientWith(envelope(deniedForm)).evaluate("audit:export", null),
                "布尔腿失效 ⇒ 下面组装的 DENY 状态串没有支点");
        // 调用方（ForecastAuditPackService）对 false 的唯一处置就是这个 403 异常
        ForbiddenOperationException denied = new ForbiddenOperationException("forecast:audit:export");
        String denyState = denied.getHttpStatus() + "/" + denied.getCode();

        SecurityEngineUnavailableException unevaluated = assertThrows(
                SecurityEngineUnavailableException.class,
                () -> clientWith(envelope(unevaluatedForm)).evaluate("audit:export", null));
        String unavailableState = unevaluated.getHttpStatus() + "/" + unevaluated.getCode();

        assertNotEquals(denyState, unavailableState,
                "两态状态串相同 ⇒ 用户面把护栏故障读成'没权限'（D-B4-41 的复发形态）");
        assertEquals("403/PERMISSION_DENIED", denyState, "策略拒绝腿的可见结论漂移");
        assertEquals("503/" + RlsExecutionContract.SEC_UNAVAILABLE_CODE, unavailableState,
                "不可用腿必须走既有 503 通道（与传输故障同一分类点）");
    }

    @Test
    @DisplayName("明确放行仍放行：ALLOW 不得被分态改动顺带拦成不可用")
    void explicitAllowIsStillAllow() {
        assertTrue(clientWith(envelope(data("allow", true, "policyRef", "rbac")))
                .evaluate("audit:export", null));
        assertTrue(clientWith(envelope(data("policyState", "EVALUATED_ALLOW")))
                .evaluate("audit:export", null));
    }

    @Test
    @DisplayName("调用方不得再把不可用折回布尔：迁移权限校验器原样上抛")
    void callerDoesNotRefoldUnavailable() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.evaluate(anyString(), any())).thenThrow(
                new SecurityEngineUnavailableException(EVALUATE_PATH, "UNEVALUATED", null));
        SecurityEngineMigrationPermissionChecker checker =
                new SecurityEngineMigrationPermissionChecker(client);
        SecurityEngineUnavailableException ex = assertThrows(SecurityEngineUnavailableException.class,
                () -> checker.isAllowed("scenario:migrate", "u1"));
        assertEquals(503, ex.getHttpStatus(),
                "校验器把不可用吞成 false ⇒ 本批的分态在调用方这一腿又断了");
    }

    @Test
    @DisplayName("批次与差距标记在册：客户端与本类的对应关系不得断链")
    void registrationMarkersExist() throws IOException {
        String client = Files.readString(repoRoot().resolve(
                "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                        + "workspace/security/WorkspaceSecurityEngineClient.java"), StandardCharsets.UTF_8);
        assertTrue(client.contains("D-B4-41") && client.contains("#142/B4-3e-6"),
                "客户端丢失差距/批次标记 ⇒ 台账与本类的对应关系断链，无法用 git log --grep 溯源");
        assertTrue(client.contains("AbacEvaluateResponseContract.verdictOf"),
                "客户端不再取三态读数 ⇒ 布尔折叠形态（D-B4-41）已回归");
    }

    private static Path repoRoot() {
        Path start = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 12 && start != null; i++) {
            if (Files.isDirectory(start.resolve("ecos_backend"))
                    && Files.isDirectory(start.resolve("ecos-docker"))) {
                return start;
            }
            start = start.getParent();
        }
        throw new AssertionError("未能从 user.dir 定位仓根（需同时含 ecos_backend 与 ecos-docker）");
    }
}
