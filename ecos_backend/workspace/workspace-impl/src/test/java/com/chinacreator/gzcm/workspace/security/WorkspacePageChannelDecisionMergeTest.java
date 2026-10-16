package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 页面通道列裁剪合流三通道唯一裁决入口（#142/B4-3d-2，差距 D-B4-15 列面腿 / D-B4-16）。
 *
 * <p><b>本批改的两件事，各自对应一组用例</b>：</p>
 * <ol>
 *   <li><b>bundle 列面可用（D-B4-16）</b>：裁决束的 {@code columns} 段此前只有
 *       {@code mode}/{@code names}，{@code mode} 恒 {@code "allow"}，空 {@code names} 同时是
 *       「策略拒绝」「列全集未解析」「CLS 源抛错」「键面漂移」四种事实 ——
 *       页面通道即使合流也拿不到可分态的列面。现列面读数与生产者共用
 *       {@link ColumnSurfaceContract}，四态在消费侧可区分且各自拒绝；</li>
 *   <li><b>列面腿合流（D-B4-15）</b>：{@link AbacQueryFilter#filterColumns} 由
 *       {@code /cls/columns} 改走 {@code /api/v1/security/decision}（{@code purpose=page}），
 *       导出通道已在同一入口 —— 同一主体同一张表的列面不再由两个端点各答一次。</li>
 * </ol>
 *
 * <p><b>桩下沉到 {@link RestTemplate}</b>（沿用 {@code WorkspaceClsColumnSurfaceTest} 的教训）：
 * D-B4-25 之所以在单测层完全看不见，是因为既有 workspace 用例 mock 的是 client 方法本身，
 * 键名漂移被桩遮蔽。本类用<b>生产者的真实应答形态</b>驱动，因此
 * {@link #bundleShapedOnlyWithModeAndNamesIsUndecidable()} 与
 * {@link #usableBundleTrimsThroughTheSingleEntry()} 都是"改回旧读数面就立刻变红"的用例。</p>
 *
 * <p>反假绿：每条 DENY 用例都配一条同形态的正向对照（证明空行集出自裁决而非恒空桩）；
 * 传输故障用例断言 503 独立态（§4.5.4「不可用 ≠ 无权限」）与 X-41（对外 message 不泄漏下游原文）；
 * 单腿性用「本次请求实际打到的 URL 清单」断言，不靠注释声明。</p>
 */
@DisplayName("页面通道列面：唯一裁决腿 = /decision(page)，bundle 四态可区分且全 fail-closed")
class WorkspacePageChannelDecisionMergeTest {

    private static final String ENTITY = "Contract";
    private static final String DECISION_PATH = "/api/v1/security/decision";
    private static final String CLS_PATH = "/api/v1/security/cls/columns";

    private final RestTemplate rest = mock(RestTemplate.class);
    private final WorkspaceSecurityEngineClient client =
            new WorkspaceSecurityEngineClient(rest, mock(ObjectProvider.class));
    private final AbacQueryFilter filter = new AbacQueryFilter(client);

    // ═══════════════ 夹具 ═══════════════

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static List<Map<String, Object>> rows() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(row("id", "c-1", "customer", "甲", "amount", 100, "salary", 9000));
        return out;
    }

    /** 生产者（{@code SecurityDecisionService#asColumnsPart}）真实写出的列面段。 */
    private static Map<String, Object> columnSegment(List<String> visible, List<String> blocked,
                                                    String universeSource, boolean universeResolved,
                                                    String denyReason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", visible.isEmpty() ? "deny" : "allow");
        m.put("names", visible);
        m.put(ColumnSurfaceContract.KEY_VISIBLE_COLUMNS, visible);
        m.put(ColumnSurfaceContract.KEY_BLOCKED_COLUMNS, blocked);
        m.put("policyRefs", List.of("cls-finance"));
        m.put(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_SOURCE, universeSource);
        m.put(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_RESOLVED, universeResolved);
        if (denyReason != null) m.put(ColumnSurfaceContract.KEY_CLS_DENY_REASON, denyReason);
        return m;
    }

    /** 成功信封 + 裁决束 data 段（rls/opa 给最小可解析形态，本类只裁列面）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void answerBundle(Map<String, Object> columnsSegment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rls", Map.of("predicateTemplate", "1=1", "whereClause", "1=1", "whereParams", List.of()));
        if (columnsSegment != null) data.put("columns", columnsSegment);
        data.put("masks", List.of());
        data.put("opa", Map.of("allow", true, "policyState", "ALLOW"));
        data.put("decisionId", "d-1");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 0);
        envelope.put("data", data);
        when(rest.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(envelope));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void answerEnvelope(Map<String, Object> envelope) {
        when(rest.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(envelope));
    }

    @SuppressWarnings("unchecked")
    private List<String> requestUrls() {
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(rest, org.mockito.Mockito.atLeastOnce())
                .postForEntity(url.capture(), any(HttpEntity.class), eq(Map.class));
        return url.getAllValues();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestBody() {
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rest).postForEntity(anyString(), entity.capture(), eq(Map.class));
        return (Map<String, Object>) entity.getValue().getBody();
    }

    // ═══════════════ 1. 合流本体：单腿 + 真实列面可用 ═══════════════

    @Test
    @DisplayName("可用列面 → 按 visibleColumns 裁剪，且本次裁决只打 /decision（列面腿唯一）")
    void usableBundleTrimsThroughTheSingleEntry() {
        answerBundle(columnSegment(List.of("id", "customer"), List.of("amount", "salary"),
                ColumnSurfaceContract.UNIVERSE_REQUEST, true, null));

        List<Map<String, Object>> out = filter.filterColumns(ENTITY, rows());

        assertEquals(1, out.size(), "正向对照：可读集非空必须真的裁出行（禁恒空桩）");
        assertTrue(out.get(0).containsKey("id") && out.get(0).containsKey("customer"));
        assertFalse(out.get(0).containsKey("salary"), "未列出的列必须被裁掉（列面结论生效）");

        List<String> urls = requestUrls();
        assertEquals(List.of(DECISION_PATH), urls,
                "页面通道列面只允许一条裁决腿；再出现 " + CLS_PATH + " 即双端点各答一次（D-B4-15）");
        assertFalse(urls.stream().anyMatch(u -> u.contains("/cls/")),
                "列面裁决不得回退分散面，实际请求=" + urls);
    }

    @Test
    @DisplayName("请求体形态：assetRef.columns=结果行实际列键（宇宙单源）、purpose=page 取词表")
    void requestCarriesRowColumnKeysAndPageChannelWord() {
        answerBundle(columnSegment(List.of("id"), List.of(), ColumnSurfaceContract.UNIVERSE_REQUEST, true, null));

        filter.filterColumns(ENTITY, rows());

        Map<String, Object> body = requestBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> assetRef = (Map<String, Object>) body.get("assetRef");
        assertEquals(ENTITY, assetRef.get("table"));
        assertEquals(List.of("id", "customer", "amount", "salary"), assetRef.get("columns"),
                "列全集只能来自结果行的实际列键；在此另取一份宇宙＝第二个宇宙源（D-B4-16 裁决依据）");
        assertEquals(DecisionChannelContract.PURPOSE_PAGE, body.get("purpose"),
                "通道词必须取单源常量，内联 \"page\" 是第四处词面真值（D-B4-27）");
        assertTrue(DecisionChannelContract.isKnown(String.valueOf(body.get("purpose"))),
                "workspace 自身发出的 purpose 必须落在词表内，否则 security 侧 400 会被当成无裁决");
    }

    // ═══════════════ 2. 四态分立（D-B4-16 本体：都 DENY，但理由不同且不得互相冒充） ═══════════════

    @Test
    @DisplayName("策略拒绝全部列（CLS_POLICY_DENY）→ 空行集")
    void policyDenyEmptiesRows() {
        answerBundle(columnSegment(List.of(), List.of("id", "customer", "amount", "salary"),
                ColumnSurfaceContract.UNIVERSE_REQUEST, true, ColumnSurfaceContract.DENY_POLICY));

        assertTrue(filter.filterColumns(ENTITY, rows()).isEmpty());
        assertEquals(List.of(DECISION_PATH), requestUrls());
    }

    @Test
    @DisplayName("列全集未解析（COLUMN_UNIVERSE_UNRESOLVED）→ 空行集，且不得被折进「策略拒绝」")
    void unresolvedUniverseEmptiesRows() {
        answerBundle(columnSegment(List.of(), List.of(),
                ColumnSurfaceContract.UNIVERSE_NONE, false,
                ColumnSurfaceContract.DENY_UNIVERSE_UNRESOLVED));

        assertTrue(filter.filterColumns(ENTITY, rows()).isEmpty(),
                "未判定态也必须拒绝（宁 DENY 不超发），但理由与策略拒绝分列");
    }

    @Test
    @DisplayName("CLS 源异常（CLS_SOURCE_ERROR）→ 空行集（缺陷态经列面拒绝表达，禁放行原行）")
    void sourceErrorEmptiesRows() {
        answerBundle(columnSegment(List.of(), List.of("id", "customer", "amount", "salary"),
                ColumnSurfaceContract.UNIVERSE_REQUEST, true, ColumnSurfaceContract.DENY_SOURCE_ERROR));

        assertTrue(filter.filterColumns(ENTITY, rows()).isEmpty());
    }

    @Test
    @DisplayName("bundle 只有 mode/names 的旧形态 → 判漂移并拒绝（D-B4-16 的能变红用例）")
    void bundleShapedOnlyWithModeAndNamesIsUndecidable() {
        // 旧裁决束形态：names 里有可用列，但没有单源权威键。消费者必须拒绝而非"按 names 放行"——
        // names 是列面的第三种叫法，认它就等于承认两套真值并存（D-B4-25 的成因形态）。
        Map<String, Object> legacyShape = new LinkedHashMap<>();
        legacyShape.put("mode", "allow");
        legacyShape.put("names", List.of("id", "customer"));
        answerBundle(legacyShape);

        assertTrue(filter.filterColumns(ENTITY, rows()).isEmpty(),
                "生产者若把列面改回只写 names，页面通道必须整条 DENY 而不是静默按别名放行");
        assertEquals(List.of(DECISION_PATH), requestUrls(), "拒绝仍出自同一裁决腿，不得为兜底另开分散面");
    }

    @Test
    @DisplayName("裁决束缺 columns 段 / data 段整体缺失 → 空行集（形态漂移，非策略结论）")
    void absentSegmentsEmptyRows() {
        answerBundle(null);
        assertTrue(filter.filterColumns(ENTITY, rows()).isEmpty(), "bundle 无 columns 段＝列面无出处");

        Map<String, Object> noData = new LinkedHashMap<>();
        noData.put("code", 0);
        answerEnvelope(noData);
        assertTrue(filter.filterColumns(ENTITY, rows()).isEmpty(), "应答无 data 段＝裁决缺失");
    }

    @Test
    @DisplayName("security 返回 400（通道词未识别，D-B4-27）→ 空行集 DENY，禁当页面通道放行")
    void unrecognizedChannelWordDenies() {
        // 钉现状（非认可）：本端点的 purpose 是编译期常量，400 只可能来自两侧词表漂移。
        // 现口径＝"无裁决证据"→ 空行集；把它升成 4xx 可解释拒绝属 B4-3d-5 的分态收口。
        Map<String, Object> badRequest = new LinkedHashMap<>();
        badRequest.put("code", 400);
        badRequest.put("message", DecisionChannelContract.unrecognizedReason("exprt"));
        answerEnvelope(badRequest);

        assertTrue(filter.filterColumns(ENTITY, rows()).isEmpty(),
                "词面失配必须 DENY；若还能拿到行，说明旧「静默降级 page」仍在生效");
    }

    // ═══════════════ 3. 可用性故障保持独立态（§4.5.4） ═══════════════

    @Test
    @DisplayName("连不上/超时 → 503 ECOS-SEC-501 上抛，禁空行集冒充「无可见列」")
    void transportFailureStays503() {
        when(rest.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("connect refused"));

        SecurityEngineUnavailableException ex = assertThrows(SecurityEngineUnavailableException.class,
                () -> filter.filterColumns(ENTITY, rows()));
        assertEquals(503, ex.getHttpStatus());
        assertEquals("ECOS-SEC-501", ex.getCode());
        assertTrue(ex.getTechnicalReason().contains(DECISION_PATH),
                "技术面须定位到裁决腿，实际=" + ex.getTechnicalReason());
        assertFalse(ex.getMessage().contains("security/decision"),
                "X-41：对外 message 不得泄漏下游原文，实际=" + ex.getMessage());
    }

    @Test
    @DisplayName("503 信封（security 侧内部失败 fail-closed）→ 同为独立态，不落 DENY 空行集")
    void unavailableEnvelopeStays503() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 503);
        envelope.put("message", "ECOS-SEC-501：security 内部裁决失败（fail-closed）");
        answerEnvelope(envelope);

        SecurityEngineUnavailableException ex = assertThrows(SecurityEngineUnavailableException.class,
                () -> filter.filterColumns(ENTITY, rows()));
        assertEquals(503, ex.getHttpStatus());
    }

    // ═══════════════ 4. 无裁决需求时不外呼 ═══════════════

    @Test
    @DisplayName("空行集/null → 原样返回且零外呼（无数据面就不占用裁决入口）")
    void emptyRowSetShortCircuits() {
        List<Map<String, Object>> empty = List.of();
        assertSame(empty, filter.filterColumns(ENTITY, empty));
        assertTrue(filter.filterColumns(ENTITY, null) == null);
        verifyNoInteractions(rest);
    }

    @Test
    @DisplayName("结果行连列键都没有 → 直接 DENY 且零外呼（禁把空宇宙送去裁决再拿一条拒绝）")
    void rowWithoutColumnKeysDeniesWithoutRequest() {
        List<Map<String, Object>> keyless = new ArrayList<>();
        keyless.add(new LinkedHashMap<>());

        assertTrue(filter.filterColumns(ENTITY, keyless).isEmpty());
        verifyNoInteractions(rest);
    }
}
