package com.chinacreator.gzcm.engine.ontology.security;

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
        assertEquals(0, rowsAfterRemote(null));
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

    /** 桩掉客户端返回指定片段，反射调用拦截器私有 {@code applyRlsRemote}，返回行集大小。 */
    private static int rowsAfterRemote(String clause) {
        SecurityEngineClient client = mock(SecurityEngineClient.class);
        when(client.applyRls(anyString(), anyString(), any())).thenReturn(clause);
        OntologySecurityInterceptor interceptor = new OntologySecurityInterceptor(client);
        try {
            Method m = OntologySecurityInterceptor.class.getDeclaredMethod(
                    "applyRlsRemote", String.class, String.class, List.class);
            m.setAccessible(true);
            Object out = m.invoke(interceptor, TABLE, USER, sampleRows());
            assertTrue(out instanceof List<?>, "返回值必须是行集: " + out);
            return ((List<?>) out).size();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("applyRlsRemote 形态漂移，本用例判据失效", e);
        }
    }

    private static List<Map<String, Object>> sampleRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("tenant_id", "t-7", "name", "合同")));
        rows.add(new LinkedHashMap<>(Map.of("tenant_id", "t-8", "name", "发票")));
        return rows;
    }
}
