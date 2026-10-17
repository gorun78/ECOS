package com.chinacreator.gzcm.engine.ontology.security;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3c-2（D-B4-17）—— 本体通道行级过滤 fail-closed 具名用例。
 *
 * <p><b>缺陷本体</b>：security-engine 的生产者从未发过 {@code whereClause} 字段，而本体侧
 * {@code SecurityEngineClient.applyRls} 写的是
 * {@code data.get("whereClause") == null ? "" : ...} —— 字段名漂移被归一成空串，
 * {@code OntologySecurityInterceptor.applyRlsRemote} 再把空串当「无行级限制」
 * {@code return rows}，于是 27 个 Ontology*Controller 读接口全量渗漏。
 * 本类把「漂移＝拒绝」钉在两处：</p>
 * <ol>
 *   <li>客户端 {@code applyRls}：只承认三种形态（拒绝 {@code 1=0} / 恒真 {@code 1=1} /
 *       可绑定的裸文本谓词），缺字段、空白、{@code data} 非 Map、需绑参（含 {@code ?}）、
 *       传输异常一律 {@code "1=0"}，且<b>任何路径都不返回空串</b>；</li>
 *   <li>拦截器 {@code applyRlsRemote}：对返回值做三态消费——空片段（上游漂移）也拒绝，
 *       恒真才保留原行集，非平凡谓词只接受白名单单条件，多条件／非白名单字段一律拒绝
 *       （纵深防御，不依赖客户端永不漂移）。</li>
 * </ol>
 *
 * <p>反向对照（证明 DENY 断言可观察而非恒真）：{@link #allowAllKeepsEveryRow()} 与
 * {@link #whitelistedSingleConditionFiltersRows()} 在同样桩点上必须放行。</p>
 *
 * <p><b>#142/B4-3d-10（D-B4-26 余项）追加第 3 节</b>：折叠出口之外还有带态出口
 * {@code applyRlsExecution}，本节钉的是「security 进程没答」在类型上不能与
 * 「策略拒绝」同值——前者 {@code State.UNAVAILABLE}、后者 {@code State.DENIED}，
 * 并由拦截器 {@code RlsOutcome.unavailable()} 把态带到对外信封。</p>
 */
class OntologyRlsFailClosedTest {

    private static final String ENTITY = "ecos_ontology";
    private static final String TABLE = "ontology_object_type";
    private static final String USER = "u-42";

    // ═══════════════ 1. 客户端契约：漂移即拒绝，永不空串 ═══════════════

    /** 生产者应答（假 data 内层 Map）→ 客户端返回的片段。 */
    private static String clauseFrom(Map<String, Object> data) {
        return clauseFromResponse(data == null ? Map.of() : Map.of("data", data));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String clauseFromResponse(Map<String, Object> resp) {
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(resp));
        return newClient(stub).applyRls(ENTITY, TABLE, Map.of("tenant", "default"));
    }

    private static SecurityEngineClient newClient(RestTemplate stub) {
        SecurityEngineClient client = new SecurityEngineClient("http://127.0.0.1:18081", 200);
        try {
            Field f = SecurityEngineClient.class.getDeclaredField("serviceRestTemplate");
            f.setAccessible(true);
            f.set(client, stub);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("桩 RestTemplate 注不进去（字段名漂移即本类判据失效）", e);
        }
        return client;
    }

    @Test
    @DisplayName("应答缺 whereClause → 1=0（旧实现在此返回空串＝全量渗漏）")
    void missingWhereClauseDeniesInsteadOfEmptyString() {
        String clause = clauseFrom(Map.of("allowed", true, "policyCount", 0));
        assertEquals(SecurityEngineClient.DENY_CLAUSE, clause);
        assertNotEquals("", clause, "空串＝「无行级限制」，正是 D-B4-17 的 fail-open 形态");
    }

    @Test
    @DisplayName("whereClause 空白 → 1=0")
    void blankWhereClauseDenies() {
        assertEquals(SecurityEngineClient.DENY_CLAUSE, clauseFrom(Map.of("whereClause", "   ")));
    }

    @Test
    @DisplayName("whereClause 为 null 值 → 1=0")
    void nullWhereClauseValueDenies() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("whereClause", null);
        assertEquals(SecurityEngineClient.DENY_CLAUSE, clauseFrom(data));
    }

    @Test
    @DisplayName("data 非 Map（外层契约漂移）→ 1=0")
    void nonMapDataDenies() {
        assertEquals(SecurityEngineClient.DENY_CLAUSE, clauseFromResponse(Map.of("data", "1=1")));
    }

    @Test
    @DisplayName("应答整体无 data → 1=0")
    void responseWithoutDataDenies() {
        assertEquals(SecurityEngineClient.DENY_CLAUSE, clauseFromResponse(Map.of("code", -1)));
    }

    @Test
    @DisplayName("片段需绑参（含 ?）→ 1=0：本引擎无参数通道，宁 DENY 不猜值拼接（IR05）")
    void parameterizedFragmentDeniesWithoutBindChannel() {
        assertEquals(SecurityEngineClient.DENY_CLAUSE,
                clauseFrom(Map.of("whereClause", "(org_id = ? AND owner_id = ?)")));
    }

    @Test
    @DisplayName("恒真 1=1 原样返回＝「明确不限制行」，与「未给谓词」分态")
    void allowAllIsDistinctFromDrift() {
        assertEquals(SecurityEngineClient.ALLOW_ALL_CLAUSE, clauseFrom(Map.of("whereClause", "1=1")));
    }

    @Test
    @DisplayName("可绑定裸文本谓词原样下传")
    void literalPredicatePassesThrough() {
        assertEquals("tenant_id = 't-7'", clauseFrom(Map.of("whereClause", "tenant_id = 't-7'")));
    }

    @Test
    @DisplayName("security-engine 不可用（传输异常）→ 1=0，不放行原行集")
    void transportFailureDenies() {
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("connection refused"));
        assertEquals(SecurityEngineClient.DENY_CLAUSE, newClient(stub).applyRls(ENTITY, TABLE, Map.of()));
    }

    // ═══════════════ 2. 拦截器三态消费（纵深防御） ═══════════════

    @Test
    @DisplayName("客户端给空片段（上游漂移）→ 拒绝空集，不再 return rows")
    void blankClauseFromClientDeniesRows() {
        assertEquals(0, rowsAfterRemote(""));
        assertEquals(0, rowsAfterRemote((String) null));
    }

    @Test
    @DisplayName("1=0 → 拒绝空集")
    void denyClauseYieldsEmptyRowSet() {
        assertEquals(0, rowsAfterRemote(SecurityEngineClient.DENY_CLAUSE));
    }

    @Test
    @DisplayName("(1=0) 带括号仍是拒绝：生产者合并逻辑逐段加括号，字面量相等会漏判")
    void parenthesisedDenyClauseStillDenies() {
        assertEquals(0, rowsAfterRemote("(1=0)"));
    }

    @Test
    @DisplayName("恒真 1=1 → 保留全部行（反向对照，证明上面的 0 不是恒真）")
    void allowAllKeepsEveryRow() {
        assertEquals(2, rowsAfterRemote(SecurityEngineClient.ALLOW_ALL_CLAUSE));
    }

    @Test
    @DisplayName("白名单单条件 → 按条件过滤，不是全留也不是全拒（第二道反向对照）")
    void whitelistedSingleConditionFiltersRows() {
        assertEquals(1, rowsAfterRemote("tenant_id = 't-7'"));
    }

    @Test
    @DisplayName("多条件（AND）不可信 → 拒绝空集")
    void multiConditionClauseDenies() {
        assertEquals(0, rowsAfterRemote("tenant_id = 't-7' AND status = 'ACTIVE'"));
    }

    @Test
    @DisplayName("非白名单字段名不可信 → 拒绝空集")
    void nonWhitelistedFieldDenies() {
        assertEquals(0, rowsAfterRemote("secret_col = 'x'"));
    }

    // ═══════════════ 3. 「不可用」与「无权限」分态（#142/B4-3d-10，D-B4-26 余项） ═══════════════

    /**
     * 带态出口读数。
     *
     * <p>折叠出口 {@code applyRls} 只能给出一个字符串，本节判据是<b>态</b>，
     * 必须走 {@link SecurityEngineClient#applyRlsExecution}。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RlsExecutionContract.Execution executionFromResponse(Map<String, Object> resp) {
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(resp));
        return newClient(stub).applyRlsExecution(ENTITY, TABLE, Map.of("tenant", "default"));
    }

    @Test
    @DisplayName("带态出口：传输异常 → UNAVAILABLE（收口前折成 1=0＝把「引擎没答」说成「你没权限」）")
    void transportFailureIsUnavailableState() {
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("connection refused"));
        RlsExecutionContract.Execution exec = newClient(stub)
                .applyRlsExecution(ENTITY, TABLE, Map.of());
        assertEquals(RlsExecutionContract.State.UNAVAILABLE, exec.state());
        assertTrue(exec.reason().contains(RlsExecutionContract.REASON_SERVICE_UNAVAILABLE),
                "不可用理由必须带单源前缀码，供日志/告警按码寻址: " + exec.reason());
        assertFalse(exec.usable(), "不可用仍是 fail-closed（分态改的是可寻址性，不是放宽处置）");
    }

    @Test
    @DisplayName("带态出口：security 失败信封（HTTP 200 + code=503）→ UNAVAILABLE，不冒充形态漂移")
    void failureEnvelopeIsUnavailableNotDrift() {
        RlsExecutionContract.Execution exec = executionFromResponse(Map.of(
                "code", RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                "message", RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": RLS 裁决内部失败"));
        assertEquals(RlsExecutionContract.State.UNAVAILABLE, exec.state(),
                "认不出失败信封即把可用性故障读成「应答缺执行契约」＝D-B4-26 的折叠");
    }

    @Test
    @DisplayName("反向对照：同一失败信封不经信封判据时，单源读数给的是 DENIED + 无 data 段")
    void envelopeBlindnessWouldProduceDeniedDriftShape() {
        RlsExecutionContract.Execution blind = RlsExecutionContract.read(null, "table=" + TABLE);
        assertEquals(RlsExecutionContract.State.DENIED, blind.state());
        assertTrue(blind.reason().contains(RlsExecutionContract.REASON_NO_DATA_SEGMENT),
                "缺 data 段的默认归一只能是漂移拒绝，说明上面的 UNAVAILABLE 来自信封判据而非巧合: "
                        + blind.reason());
    }

    @Test
    @DisplayName("带态出口：应答整体为空 → UNAVAILABLE（与「无 data 段」的漂移态分家）")
    void nullResponseIsUnavailable() {
        assertEquals(RlsExecutionContract.State.UNAVAILABLE,
                executionFromResponse(null).state(),
                "空信封＝没答，不是答了个没法解析的形态");
    }

    @Test
    @DisplayName("带态出口：正常三态不误报不可用（策略拒绝仍为 DENIED）")
    void policyDenyIsNotReportedAsUnavailable() {
        RlsExecutionContract.Execution denied = executionFromResponse(
                Map.of("data", Map.of(RlsExecutionContract.KEY_WHERE_CLAUSE,
                        RlsExecutionContract.DENY_CLAUSE)));
        assertEquals(RlsExecutionContract.State.DENIED, denied.state());
        assertFalse(denied.unavailable(), "「你没权限」打上不可用＝用告警掩盖授权问题");
    }

    @Test
    @DisplayName("拦截器：不可用态行集清空且带不可用标记（供切面写 503 信封）")
    void interceptorCarriesUnavailableFlagWithEmptyRows() {
        OntologySecurityInterceptor.RlsOutcome outcome =
                outcome(RlsExecutionContract.Execution.unavailable("table=" + TABLE + "(test)"));
        assertTrue(outcome.unavailable());
        assertEquals(0, outcome.rows().size(), "不可用态禁把未过滤行交给调用方，哪怕对外写成 503");
        assertTrue(outcome.reason() != null && !outcome.reason().isBlank(),
                "不可用必须带可解释理由，对外信封才有排障线索");
    }

    @Test
    @DisplayName("拦截器：拒绝态仍走本地过滤腿（unavailable=false，反向对照）")
    void interceptorKeepsDeniedStateDistinctFromUnavailable() {
        OntologySecurityInterceptor.RlsOutcome outcome =
                outcome(executionOf(RlsExecutionContract.DENY_CLAUSE));
        assertFalse(outcome.unavailable());
        assertEquals(0, outcome.rows().size());
    }

    @Test
    @DisplayName("可用态带空片段（上游形态漂移）→ 拦截器第二道仍拒绝，不因态可用而放行")
    void usableStateWithBlankClauseDeniesRows() {
        // 单源 read() 构造不出这一形状（缺片段即 DENIED）：它只能来自未来的生产者漂移，
        // 拦截器的 blank 判腿必须独立于态判定存在，否则「态＝可用」被当成「片段必非空」。
        assertEquals(0, rowsAfterRemote(new RlsExecutionContract.Execution(
                RlsExecutionContract.State.FILTERED, "", List.of(), List.of(), true, false, null)));
    }

    /** 桩掉客户端返回指定片段，反射调用拦截器私有 {@code applyRlsRemote}，返回行集大小。 */
    private static int rowsAfterRemote(String clause) {
        return rowsAfterRemote(executionOf(clause));
    }

    /**
     * 片段字符串 → 执行契约读数（夹具经 {@code read} 构造，与生产者应答同一形状）。
     *
     * <p>#142/B4-3d-10 起拦截器消费的是带态出口 {@code applyRlsExecution}，
     * 桩不能再停在折叠后的字符串上——那会把四种形态重新压成一个字面量，
     * 正是 D-B4-26 折叠本身的测试化形态。</p>
     */
    private static RlsExecutionContract.Execution executionOf(String clause) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (clause != null) data.put(RlsExecutionContract.KEY_WHERE_CLAUSE, clause);
        return RlsExecutionContract.read(data, "table=" + TABLE)
                .rejectForUnbindableChannel("table=" + TABLE);
    }

    private static OntologySecurityInterceptor.RlsOutcome outcome(RlsExecutionContract.Execution exec) {
        SecurityEngineClient client = mock(SecurityEngineClient.class);
        when(client.applyRlsExecution(anyString(), anyString(), any())).thenReturn(exec);
        OntologySecurityInterceptor interceptor = new OntologySecurityInterceptor(client);
        try {
            Method m = OntologySecurityInterceptor.class.getDeclaredMethod(
                    "applyRlsRemote", String.class, String.class, List.class);
            m.setAccessible(true);
            Object out = m.invoke(interceptor, TABLE, USER, sampleRows());
            assertTrue(out instanceof OntologySecurityInterceptor.RlsOutcome,
                    "applyRlsRemote 必须返回带态读数（退回 List 即分态判据消失）: " + out);
            return (OntologySecurityInterceptor.RlsOutcome) out;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("applyRlsRemote 形态漂移，本用例判据失效", e);
        }
    }

    private static int rowsAfterRemote(RlsExecutionContract.Execution exec) {
        return outcome(exec).rows().size();
    }

    private static List<Map<String, Object>> sampleRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("tenant_id", "t-7", "name", "合同")));
        rows.add(new LinkedHashMap<>(Map.of("tenant_id", "t-8", "name", "发票")));
        return rows;
    }
}
