package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

/**
 * #142/B4-3c-2（D-B4-17）—— AI 通道行级过滤 fail-closed 具名用例。
 *
 * <p><b>缺陷本体</b>：{@code AiSecurityEngineClient.applyRls} 在应答缺 {@code whereClause}
 * 时返回空串，{@code SecurityCheckerNode} 把空串读成"无策略但放行"写入 OAG 上下文 ——
 * 生产者字段名漂移被 AI 通道折叠成"全量可见"。收口后三态唯一：</p>
 * <ul>
 *   <li>{@code "1=0"}＝拒绝（含缺字段/空白/需绑参/不可用）；</li>
 *   <li>{@code "1=1"}＝明确不限制行；</li>
 *   <li>不含 {@code ?} 的裸文本谓词＝非平凡条件原样下传。</li>
 * </ul>
 *
 * <p>{@code OrThrow} 变体承担 §4.7.2「不可用 ≠ 无权限」的分态：漂移/需绑参抛
 * {@link AiSecurityEngineClient.SecurityResponseUnparsableException}，
 * 传输异常原样上抛（调用方据此走 503 语义而不是 403）。</p>
 *
 * <p>本引擎无 RLS 参数绑定通道（差距文档登记 NOT_WIRED），故含 {@code ?} 的片段一律拒绝 ——
 * 这是"宁 DENY 不超发"（IR05），不是漏实现。</p>
 *
 * <p>#142/B4-3d-5（差距 D-B4-24）：读数改由 {@code RlsExecutionContract.read} 单源完成，
 * 本类因此覆盖<b>两类</b>拒绝码 —— 单源的构造期不变量（缺片段／arity／null 参数／占位符残留）
 * 与本通道叠加的形态约束（{@code RLS_PARAM_UNBINDABLE}）。用例刻意把两者分开断言，
 * 防止将来任一层的口径变化被另一层的断言掩盖。</p>
 */
class AiRlsFailClosedTest {

    private static final String USER = "u-42";
    private static final String TENANT = "t-7";
    private static final String ENTITY = "oag_query";
    private static final String TABLE = "ecos_dw.application_contract";

    private static AiSecurityEngineClient client(Map<String, Object> resp) {
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(respOf(resp));
        return clientWith(stub);
    }

    private static AiSecurityEngineClient throwingClient(RuntimeException failure) {
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(failure);
        return clientWith(stub);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResponseEntity<Map> respOf(Map<String, Object> resp) {
        return (ResponseEntity) ResponseEntity.ok(resp);
    }

    @SuppressWarnings("unchecked")
    private static AiSecurityEngineClient clientWith(RestTemplate stub) {
        AiSecurityEngineClient client = new AiSecurityEngineClient(
                "http://127.0.0.1:18081", 200, mock(ObjectProvider.class));
        ReflectionTestUtils.setField(client, "restTemplate", stub);
        return client;
    }

    private static String applyRls(Map<String, Object> data) {
        return client(Map.of("data", data)).applyRls(USER, TENANT, ENTITY, TABLE, Map.of());
    }

    // ═══════════════ 吞异常版：漂移即 1=0，永不空串 ═══════════════

    @Test
    @DisplayName("应答缺 whereClause → 1=0（旧实现在此返回空串＝AI 通道裸放）")
    void missingWhereClauseDeniesInsteadOfEmptyString() {
        String clause = applyRls(Map.of("allowed", true, "matchedPolicies", 0));
        assertEquals(AiSecurityEngineClient.DENY_CLAUSE, clause);
        assertNotEquals("", clause, "空串＝「无行级限制」，正是 D-B4-17 的 fail-open 形态");
    }

    @Test
    @DisplayName("whereClause 空白 / null 值 → 1=0")
    void blankAndNullWhereClauseDeny() {
        assertEquals(AiSecurityEngineClient.DENY_CLAUSE, applyRls(Map.of("whereClause", "  ")));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("whereClause", null);
        assertEquals(AiSecurityEngineClient.DENY_CLAUSE, applyRls(data));
    }

    @Test
    @DisplayName("应答无 data / data 非 Map → 1=0（外层形态漂移同样拒绝）")
    void malformedResponseDenies() {
        assertEquals(AiSecurityEngineClient.DENY_CLAUSE,
                client(Map.of("code", -1)).applyRls(USER, TENANT, ENTITY, TABLE, Map.of()));
        assertEquals(AiSecurityEngineClient.DENY_CLAUSE,
                client(Map.of("data", "1=1")).applyRls(USER, TENANT, ENTITY, TABLE, Map.of()));
    }

    @Test
    @DisplayName("片段需绑参（含 ?，参数成对）→ 1=0：AI 侧无参数通道，宁 DENY 不猜值拼接（IR05）")
    void parameterizedFragmentDeniesWithoutBindChannel() {
        assertEquals(AiSecurityEngineClient.DENY_CLAUSE,
                applyRls(Map.of("whereClause", "(org_id = ? AND owner_id = ?)",
                        "whereParams", List.of("o-1", "u-42"))));
    }

    @Test
    @DisplayName("片段含 ? 而参数缺位 → 1=0：单源 arity 不变量先拒，禁把未绑定的 ? 拼进 SQL")
    void placeholderWithoutPairedParamsDenies() {
        assertEquals(AiSecurityEngineClient.DENY_CLAUSE,
                applyRls(Map.of("whereClause", "(org_id = ?)")));
    }

    @Test
    @DisplayName("恒真 1=1 原样返回＝「明确不限制行」，与「未给谓词」分态")
    void allowAllIsDistinctFromDrift() {
        assertEquals(AiSecurityEngineClient.ALLOW_ALL_CLAUSE, applyRls(Map.of("whereClause", "1=1")));
    }

    @Test
    @DisplayName("可绑定裸文本谓词原样下传（反向对照，证明上面的 1=0 非恒真）")
    void literalPredicatePassesThrough() {
        assertEquals("tenant_id = 't-7'", applyRls(Map.of("whereClause", "tenant_id = 't-7'")));
    }

    @Test
    @DisplayName("security-engine 不可用（传输异常）→ 1=0，不折叠成放行")
    void transportFailureDenies() {
        assertEquals(AiSecurityEngineClient.DENY_CLAUSE,
                throwingClient(new ResourceAccessException("connection refused"))
                        .applyRls(USER, TENANT, ENTITY, TABLE, Map.of()));
    }

    // ═══════════════ 带态出口：不可用 ≠ 无权限（#142/B4-3d-10 / D-B4-26 余项） ═══════════════

    @Test
    @DisplayName("带态出口：传输异常 → State.UNAVAILABLE（折叠出口仍 1=0，两出口共存不互换）")
    void executionLegMarksTransportFailureAsUnavailable() {
        AiSecurityEngineClient down = throwingClient(new ResourceAccessException("connection refused"));
        RlsExecutionContract.Execution exec =
                down.applyRlsExecution(USER, TENANT, ENTITY, TABLE, Map.of());
        assertEquals(RlsExecutionContract.State.UNAVAILABLE, exec.state());
        assertTrue(exec.reason().contains(RlsExecutionContract.REASON_SERVICE_UNAVAILABLE),
                "不可用理由需带单源前缀码供告警寻址: " + exec.reason());
        assertFalse(exec.usable(), "分态不放宽处置——不可用仍 fail-closed");
        assertEquals(AiSecurityEngineClient.DENY_CLAUSE,
                down.applyRls(USER, TENANT, ENTITY, TABLE, Map.of()),
                "折叠出口对既有调用方语义不变（API 只增不改）");
    }

    @Test
    @DisplayName("带态出口：security 失败信封（HTTP 200 + code=503）→ UNAVAILABLE，禁读成缺片段漂移")
    void executionLegMarksSecurityFailureEnvelopeAsUnavailable() {
        RlsExecutionContract.Execution exec = client(Map.of(
                "code", RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                "message", RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": RLS 裁决内部失败"))
                .applyRlsExecution(USER, TENANT, ENTITY, TABLE, Map.of());
        assertEquals(RlsExecutionContract.State.UNAVAILABLE, exec.state(),
                "认不出失败信封＝把可用性故障冒充成形态漂移（§4.5.4 禁止的折叠）");
    }

    @Test
    @DisplayName("带态出口：应答体为空 → UNAVAILABLE；策略拒绝仍是 DENIED（两态互不冒充）")
    void executionLegSeparatesEmptyBodyFromPolicyDeny() {
        assertEquals(RlsExecutionContract.State.UNAVAILABLE,
                client(null).applyRlsExecution(USER, TENANT, ENTITY, TABLE, Map.of()).state());

        RlsExecutionContract.Execution denied = client(Map.of(
                "data", Map.of(RlsExecutionContract.KEY_WHERE_CLAUSE, RlsExecutionContract.DENY_CLAUSE)))
                .applyRlsExecution(USER, TENANT, ENTITY, TABLE, Map.of());
        assertEquals(RlsExecutionContract.State.DENIED, denied.state());
        assertFalse(denied.unavailable(), "「你没权限」打上不可用码＝用运维告警掩盖授权结论");
    }

    @Test
    @DisplayName("带态出口：恒真/非平凡谓词不误报不可用（反向对照）")
    void executionLegKeepsUsableFormsOutOfUnavailableState() {
        assertEquals(RlsExecutionContract.State.ALLOW_ALL,
                client(Map.of("data", Map.of(RlsExecutionContract.KEY_WHERE_CLAUSE,
                        RlsExecutionContract.ALLOW_ALL_CLAUSE)))
                        .applyRlsExecution(USER, TENANT, ENTITY, TABLE, Map.of()).state());
        RlsExecutionContract.Execution bare = client(Map.of(
                "data", Map.of(RlsExecutionContract.KEY_WHERE_CLAUSE, "tenant_id = 't-7'")))
                .applyRlsExecution(USER, TENANT, ENTITY, TABLE, Map.of());
        assertTrue(bare.usable());
        assertFalse(bare.unavailable());
    }

    // ═══════════════ 不吞异常版：漂移与不可用各自分态上抛 ═══════════════

    @Test
    @DisplayName("OrThrow：应答缺 whereClause → 抛不可解析（禁折叠成 DENY 语义）")
    void orThrowSurfacesDriftAsUnparsable() {
        AiSecurityEngineClient.SecurityResponseUnparsableException e = assertThrows(
                AiSecurityEngineClient.SecurityResponseUnparsableException.class,
                () -> client(Map.of("data", Map.of("allowed", true)))
                        .applyRlsOrThrow(USER, TENANT, ENTITY, TABLE, Map.of()));
        assertTrue(e.getMessage().contains("RLS_WHERE_CLAUSE_ABSENT"),
                "异常需指名漂移码: " + e.getMessage());
    }

    @Test
    @DisplayName("OrThrow：片段需绑参且参数成对 → 抛 RLS_PARAM_UNBINDABLE（通道级拒绝）")
    void orThrowSurfacesUnbindablePredicate() {
        AiSecurityEngineClient.SecurityResponseUnparsableException e = assertThrows(
                AiSecurityEngineClient.SecurityResponseUnparsableException.class,
                () -> applyRlsOrThrow(Map.of("whereClause", "org_id = ?",
                        "whereParams", List.of("o-1"))));
        assertTrue(e.getMessage().contains("RLS_PARAM_UNBINDABLE"),
                "异常需指名不可绑定码: " + e.getMessage());
    }

    @Test
    @DisplayName("OrThrow：片段含 ? 而参数缺位 → 抛 RLS_PARAM_ARITY_MISMATCH（单源不变量先于通道判）")
    void orThrowSurfacesArityMismatchBeforeChannelCheck() {
        AiSecurityEngineClient.SecurityResponseUnparsableException e = assertThrows(
                AiSecurityEngineClient.SecurityResponseUnparsableException.class,
                () -> applyRlsOrThrow(Map.of("whereClause", "org_id = ?")));
        assertTrue(e.getMessage().contains("RLS_PARAM_ARITY_MISMATCH"),
                "片段与参数不成对必须由单源先拒绝，不得留到通道层再判: " + e.getMessage());
    }

    @Test
    @DisplayName("OrThrow：传输异常原样上抛＝「不可用」与「无权限」分态（§4.7.2）")
    void orThrowKeepsUnavailabilitySeparateFromDeny() {
        ResourceAccessException e = assertThrows(ResourceAccessException.class,
                () -> throwingClient(new ResourceAccessException("connect timed out"))
                        .applyRlsOrThrow(USER, TENANT, ENTITY, TABLE, Map.of()));
        assertEquals("connect timed out", e.getMessage());
    }

    @Test
    @DisplayName("OrThrow：恒真与非平凡谓词正常返回（正向对照）")
    void orThrowReturnsRenderableForms() throws Exception {
        assertEquals(AiSecurityEngineClient.ALLOW_ALL_CLAUSE,
                applyRlsOrThrow(Map.of("whereClause", "1=1")));
        assertEquals("status = 'ACTIVE'",
                applyRlsOrThrow(Map.of("whereClause", "status = 'ACTIVE'")));
    }

    private static String applyRlsOrThrow(Map<String, Object> data) throws Exception {
        return client(Map.of("data", data))
                .applyRlsOrThrow(USER, TENANT, ENTITY, TABLE, Map.of());
    }
}
