package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
import com.chinacreator.gzcm.engine.security.controller.DecisionController;
import com.chinacreator.gzcm.engine.security.dao.SecurityDecisionDao;
import com.chinacreator.gzcm.engine.security.decision.AssetRef;
import com.chinacreator.gzcm.engine.security.decision.DeclaredScopeResolutionService;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.chinacreator.gzcm.engine.security.decision.DecisionContext;
import com.chinacreator.gzcm.engine.security.decision.SecurityDecisionService;
import com.chinacreator.gzcm.engine.security.service.ColumnLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.rls.RlsSubjectContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 三通道裁决入口的<b>列面证据面</b>用例（#142/B4-3d-2，差距 D-B4-16 + D-B4-27）。
 *
 * <p><b>D-B4-16 缺陷本体</b>：{@code bundle.columns} 段此前只有 {@code mode}/{@code names} 两键 ——
 * 列面的<b>第三种叫法</b>（生产者叫 {@code visibleColumns}、legacy 契约册叫 {@code allowedColumns}、
 * bundle 又叫 {@code names}），且 {@code mode} 由生产者恒写 {@code "allow"}：一个取值恒定的键
 * 等于没有键。后果不是"读错列"而是<b>读不到结论的来历</b>：{@code names=[]} 在线上同时是
 * 「策略把可读集清空」「调用方没给列全集」「CLS 源抛异常」「键面漂移」四种事实的写法，
 * 消费方只能一律报 403（把引擎缺陷伪装成"该用户无权限"，违 §4.5.4），
 * 或者干脆回读 {@code /cls/columns}（第二条裁决腿，D-B4-15）——页面通道合流因此不可能成立。</p>
 *
 * <p><b>本类钉住的三点</b>：</p>
 * <ol>
 *   <li>三键证据面（{@code columnUniverseSource} / {@code columnUniverseResolved} /
 *       {@code clsDenyReason}）在生产侧<b>必写</b>，四态各自可区分
 *       （{@link ColumnStates}，含优先级：源异常 &gt; 宇宙未解析 &gt; 键面漂移 &gt; 策略拒绝）；</li>
 *   <li>列面读数与三消费者、{@code /cls/columns} 生产者<b>共用同一份</b>
 *       {@link ColumnSurfaceContract#read}，{@code names} 只作逐字节等值的兼容别名（API 只增不改）；</li>
 *   <li>{@code columnsDenied()} 语义收紧后"键面漂移"计入拒绝（fail-closed），
 *       而"未判定"与"策略拒绝"由 {@link DecisionBundle#columnsUndecidable()} /
 *       {@link DecisionBundle#columnsPolicyDenied()} 分立。<b>漂移有两级读位，本类分开钉</b>：
 *       <em>源应答</em>的键面漂移经 {@code clsDenyReason} + {@code columnsUndecidable()} 上报
 *       （{@code asColumnsPart} 总会把 bundle 段重写为规范形态，故源侧漂移<b>不</b>表现为
 *       bundle 级 {@code columnsSurfaceDrifted()}）；<em>bundle 段自身</em>缺键才是
 *       {@link DecisionBundle#columnsSurfaceDrifted()} 为真（见
 *       {@link ColumnStates#absentColumnsSegmentIsExplainableNotCrash}）。
 *       混淆两级会让"哪一侧失配"不可定位 —— 与 D-B4-25 只有一侧可见是同一类病灶。</li>
 * </ol>
 *
 * <p><b>D-B4-27 镜像面</b>：{@code DecisionController} 的入参解析与 {@code safeOpa} 的出参载荷
 * 必须共用 {@link DecisionChannelContract}（{@link ChannelVocabulary}）——
 * 入口"认不出就当 page"是 fail-open 伪装成成功，出口"枚举名与词表失配还照发"是把配置错误
 * 伪装成策略拒绝，两个方向都要能变红。</p>
 *
 * <p>反假绿：每例断到具体理由码而非"非空"；{@link ColumnStates#undecidableAndPolicyDeniedAreMutuallyExclusive}
 * 用同一断言集遍历四态，防止"访问器各写各的默认值"；空宇宙用例刻意断言
 * {@code columnsUndecidable()} 而非 {@code columnsDenied()}，使"宇宙没给＝用户无权限"的错误折叠不可能通过。</p>
 */
@DisplayName("裁决束列面证据：四态可区分 + 单源读数 + 通道词双向唯一")
class BundleColumnSurfaceTest {

    private static final String TABLE = "fc_project_period_fact";
    private static final List<String> UNIVERSE = List.of("col1", "col2", "sensitive_col");

    private RowLevelSecurityServiceImpl rls;
    private ColumnLevelSecurityServiceImpl cls;
    private OpaPolicyService opa;
    private SecurityDecisionDao dao;
    private SecurityDecisionService svc;

    @BeforeEach
    void setUp() {
        rls = mock(RowLevelSecurityServiceImpl.class);
        cls = mock(ColumnLevelSecurityServiceImpl.class);
        opa = mock(OpaPolicyService.class);
        dao = mock(SecurityDecisionDao.class);
        svc = new SecurityDecisionService(rls, cls, opa, dao);

        Map<String, Object> pred = new LinkedHashMap<>();
        pred.put("predicateTemplate", "1=1");
        pred.put("denyIfEmpty", true);
        Map<String, Object> rlsRes = new LinkedHashMap<>();
        rlsRes.put("predicate", pred);
        rlsRes.put("whereClause", "1=1");
        rlsRes.put("whereParams", List.of());
        when(rls.apply(anyString(), any(RlsSubjectContext.class))).thenReturn(rlsRes);

        Map<String, Object> opaRes = new LinkedHashMap<>();
        opaRes.put("allow", true);
        opaRes.put("policyState", OpaPolicyService.STATE_ALLOW);
        opaRes.put("obligations", List.of());
        when(opa.evaluate(eq(OpaPolicyService.POLICY_DATA_ACCESS), anyMap())).thenReturn(opaRes);

        when(dao.masksForTable(anyString(), any())).thenReturn(List.of());
    }

    private DecisionContext ctx() {
        return DecisionContext.of("u1", "t1", null, null, List.of("ROLE_USER"), "trace-1");
    }

    private AssetRef asset(List<String> columns) {
        return new AssetRef("public", TABLE, columns);
    }

    /** 默认放行两列的 CLS 源应答（与 {@code ColumnLevelSecurityServiceImpl} 的写出形态同源）。 */
    private void clsAllows(String... columns) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("visibleColumns", List.of(columns));
        r.put("blockedColumns", List.of("sensitive_col"));
        r.put("policies", List.of("cls-finance"));
        when(cls.getColumns(anyString(), anyString(), anyList())).thenReturn(r);
    }

    // ═══════════════ 1. 列面四态（D-B4-16 本体） ═══════════════

    @Nested
    @DisplayName("columns 段四态：可用 / 策略拒绝 / 宇宙未解析 / 源异常 / 键面漂移")
    class ColumnStates {

        @Test
        @DisplayName("常态（宇宙=请求列，CLS 给非空可读集）→ 三键证据齐、无拒绝理由")
        void usableSurfaceCarriesEvidence() {
            clsAllows("col1", "col2");

            DecisionBundle b = svc.decide(asset(UNIVERSE), DecisionBundle.Purpose.PAGE, ctx());

            assertFalse(b.columnsDenied());
            assertTrue(b.columnsReading().usable());
            assertEquals(List.of("col1", "col2"), b.columnsVisible());
            assertEquals(List.of("sensitive_col"), b.columnsBlocked());
            assertEquals(ColumnSurfaceContract.UNIVERSE_REQUEST, b.columnUniverseSource());
            assertTrue(b.columnUniverseResolved());
            assertNull(b.clsDenyReason(), "可用列面禁带理由（写空串冒充「无理由」同样是第二套真值）");
            assertFalse(b.columnsUndecidable());
            assertFalse(b.columnsPolicyDenied());
            assertEquals("allow", b.columns().get("mode"));
            // 兼容别名与单源读数逐字节等值（既有读 names 的用例/消费方不受影响）
            assertEquals(b.columnsVisible(), b.columns().get(ColumnSurfaceContract.KEY_VISIBLE_COLUMNS));
            assertEquals(b.columnsVisible(), b.columns().get("names"));
        }

        @Test
        @DisplayName("宇宙在位 + 可读集为空 → CLS_POLICY_DENY：这才是可报 403 的「策略拒绝全部列」")
        void policyDenyIsDistinctFromTheOtherThree() {
            clsAllows(); // visibleColumns = []

            DecisionBundle b = svc.decide(asset(UNIVERSE), DecisionBundle.Purpose.PAGE, ctx());

            assertTrue(b.columnsDenied());
            assertTrue(b.columnsPolicyDenied());
            assertFalse(b.columnsUndecidable(), "策略拒绝不是「裁决没结论」，禁落到 503 分支");
            assertEquals(ColumnSurfaceContract.DENY_POLICY, b.clsDenyReason());
            assertEquals("deny", b.columns().get("mode"),
                    "mode 恒写 allow＝零信息（D-B4-16 的一半）；现在按可读集是否为空计算");
        }

        @Test
        @DisplayName("调用方未给列全集 → COLUMN_UNIVERSE_UNRESOLVED（未判定，不是「该主体无权限」）")
        void absentUniverseIsUndecidableNotPolicyDeny() {
            clsAllows(); // 空宇宙下 CLS 源本就只能给空集

            DecisionBundle b = svc.decide(asset(List.of()), DecisionBundle.Purpose.PAGE, ctx());

            assertTrue(b.columnsDenied(), "不可判定也必须拒绝（fail-closed：拿不到列面就不给列）");
            assertTrue(b.columnsUndecidable());
            assertFalse(b.columnsPolicyDenied(),
                    "把「没给宇宙」报成「策略拒绝」＝用户看到 403 而真相是调用方少传参数（§4.5.4）");
            assertEquals(ColumnSurfaceContract.DENY_UNIVERSE_UNRESOLVED, b.clsDenyReason());
            assertEquals(ColumnSurfaceContract.UNIVERSE_NONE, b.columnUniverseSource());
            assertFalse(b.columnUniverseResolved());
            assertFalse(b.columns().containsKey("catalogFallback"),
                    "刻意不给目录宇宙兜底：ecos_security_asset 现网 0 行（D-B4-19），兜底等于伪造单源");
        }

        @Test
        @DisplayName("CLS 源抛异常 → CLS_SOURCE_ERROR，且被阻止列如实记为请求全量")
        void sourceErrorIsAvailableStateNotPermission() {
            when(cls.getColumns(anyString(), anyString(), anyList()))
                    .thenThrow(new IllegalStateException("cls dao down"));

            DecisionBundle b = svc.decide(asset(UNIVERSE), DecisionBundle.Purpose.PAGE, ctx());

            assertTrue(b.columnsDenied());
            assertTrue(b.columnsUndecidable());
            assertFalse(b.columnsPolicyDenied());
            assertEquals(ColumnSurfaceContract.DENY_SOURCE_ERROR, b.clsDenyReason());
            assertEquals(UNIVERSE, b.columnsBlocked(),
                    "源异常时「哪些列被挡」只能如实记为未判定全量，禁伪装成策略选择的結果");
        }

        @Test
        @DisplayName("CLS 源应答不含任何列面键（形态漂移）→ 计入拒绝且理由=漂移码")
        void keyDriftNowCountsAsDenied() {
            Map<String, Object> drift = new LinkedHashMap<>();
            drift.put("tableName", TABLE); // 生产者改键名后的应答形态（D-B4-25 同族）
            when(cls.getColumns(anyString(), anyString(), anyList())).thenReturn(drift);

            DecisionBundle b = svc.decide(asset(UNIVERSE), DecisionBundle.Purpose.PAGE, ctx());

            assertTrue(b.columnsDenied(),
                    "语义收紧：漂移态读不到列面即拒绝。旧形态「mode 不为 allow 所以没拒绝」会放行空列集");
            assertTrue(b.columnsUndecidable());
            assertFalse(b.columnsPolicyDenied(),
                    "源改键名是缺陷态，不是「该主体无权限」——折叠成 403 会把工程问题报成权限问题（§4.5.4）");
            assertTrue(b.clsDenyReason().startsWith(ColumnSurfaceContract.DRIFT_ABSENT),
                    "漂移理由必须带可机读前缀，实际=" + b.clsDenyReason());
            assertEquals("deny", b.columns().get("mode"));
            assertTrue(b.columnsVisible().isEmpty(), "不变量：漂移态可读集恒空");
            // 漂移的两级读数刻意分开：源应答的键面漂移由「理由码 + undecidable」承载；
            // bundle 自身段是生产者按单源契约重写的规范形态，因此恒不属 bundle 级漂移
            // （bundle 级漂移见 absentColumnsSegmentIsExplainableNotCrash，即段真缺键时）。
            assertFalse(b.columnsSurfaceDrifted(),
                    "asColumnsPart 总会重写 visibleColumns/blockedColumns 两键：bundle 段是规范形态，"
                            + "源侧漂移禁被读成 bundle 侧漂移（否则「哪个环节失配」无法定位）");
        }

        @Test
        @DisplayName("理由优先级：源异常 > 宇宙未解析（两者同现时取前者，一个列面只一个解释）")
        void sourceErrorBeatsUnresolvedUniverse() {
            when(cls.getColumns(anyString(), any(), anyList()))
                    .thenThrow(new IllegalStateException("cls dao down"));

            DecisionBundle b = svc.decide(asset(List.of()), DecisionBundle.Purpose.PAGE, ctx());

            assertEquals(ColumnSurfaceContract.DENY_SOURCE_ERROR, b.clsDenyReason(),
                    "裁决腿自身失败排在参数缺失之前——否则运维只看到「调用方没给列」，看不到源挂了");
        }

        @Test
        @DisplayName("理由优先级：源自带理由 > 键面漂移（源解释了就不由消费侧猜）")
        void sourceReasonBeatsDrift() {
            Map<String, Object> clsRes = new LinkedHashMap<>();
            clsRes.put(ColumnSurfaceContract.KEY_CLS_DENY_REASON, ColumnSurfaceContract.DENY_SOURCE_ERROR);
            when(cls.getColumns(anyString(), anyString(), anyList())).thenReturn(clsRes);

            DecisionBundle b = svc.decide(asset(UNIVERSE), DecisionBundle.Purpose.PAGE, ctx());

            assertTrue(b.columnsDenied(), "源异常态同样必须拒绝（不给任何列）");
            assertTrue(b.columnsUndecidable(), "源自带 SOURCE_ERROR ⇒ 属未判定态，非策略结论");
            assertFalse(b.columnsPolicyDenied());
            // 源应答同样没给列面键（bundle 段由生产者重写为规范形态），但本例考的是理由优先级：
            // 源解释了就不由消费侧猜成「键名不对」。bundle 级漂移见 absentColumnsSegmentIsExplainableNotCrash。
            assertFalse(b.columnsSurfaceDrifted());
            assertEquals(ColumnSurfaceContract.DENY_SOURCE_ERROR, b.clsDenyReason(),
                    "源给了理由就用源的理由，禁被漂移码覆盖成「键名不对」这种误导性结论");
        }

        @Test
        @DisplayName("四态可区分性遍历：undecidable 与 policyDenied 恰有其一（禁两真或两假）")
        void undecidableAndPolicyDeniedAreMutuallyExclusive() {
            Map<String, DecisionBundle> states = new LinkedHashMap<>();
            clsAllows("col1");
            states.put("usable", svc.decide(asset(UNIVERSE), DecisionBundle.Purpose.PAGE, ctx()));

            clsAllows();
            states.put("policyDeny", svc.decide(asset(UNIVERSE), DecisionBundle.Purpose.PAGE, ctx()));
            states.put("unresolved", svc.decide(asset(List.of()), DecisionBundle.Purpose.PAGE, ctx()));

            // 重桩必须走 doThrow/doReturn：当上一次桩是 thenThrow 时，
            // when(cls.getColumns(...)) 会真的调用 mock 并在打桩阶段抛出（用例红得毫无意义）。
            doThrow(new RuntimeException("x")).when(cls).getColumns(anyString(), anyString(), anyList());
            states.put("sourceError", svc.decide(asset(UNIVERSE), DecisionBundle.Purpose.PAGE, ctx()));

            Map<String, Object> drift = new LinkedHashMap<>();
            drift.put("policies", List.of());
            doReturn(drift).when(cls).getColumns(anyString(), anyString(), anyList());
            states.put("drift", svc.decide(asset(UNIVERSE), DecisionBundle.Purpose.PAGE, ctx()));

            for (Map.Entry<String, DecisionBundle> e : states.entrySet()) {
                DecisionBundle b = e.getValue();
                boolean undecidable = b.columnsUndecidable();
                boolean policyDenied = b.columnsPolicyDenied();
                String reason = b.clsDenyReason();
                if (reason == null) {
                    assertFalse(undecidable || policyDenied, e.getKey() + "：可用态不得带任何拒绝结论");
                    continue;
                }
                assertTrue(undecidable ^ policyDenied,
                        e.getKey() + " 必须恰属「未判定」或「策略拒绝」之一，实际 undecidable="
                                + undecidable + " policyDenied=" + policyDenied + " reason=" + reason);
                assertTrue(b.columnsDenied(), e.getKey() + "：有拒绝结论却不拒绝＝fail-open");
            }
        }

        @Test
        @DisplayName("columns 段整体为 null 的 bundle：拒绝且理由=DRIFT_NO_DATA，不抛 NPE")
        void absentColumnsSegmentIsExplainableNotCrash() {
            DecisionBundle b = new DecisionBundle(new LinkedHashMap<>(), null, List.of(),
                    new LinkedHashMap<>(), "d-1", "t-1");

            assertTrue(b.columnsDenied());
            assertTrue(b.columnsSurfaceDrifted());
            assertTrue(b.columnsUndecidable());
            assertEquals(ColumnSurfaceContract.DRIFT_NO_DATA, b.clsDenyReason());
            assertTrue(b.columnsVisible().isEmpty());
            // 证据段缺失必须仍可产出范围证据串（导出件写页眉时禁撞 500）
            assertEquals(List.of(), b.clsPolicyRefs());
            assertTrue(b.scopeEvidence(DecisionBundle.Purpose.EXPORT).contains("cls=[NONE]"));
        }
    }

    // ═══════════════ 2. 通道词双向唯一（D-B4-27） ═══════════════

    @Nested
    @DisplayName("通道词表：入口拒绝未识别 / 出口载荷用规范词 / 枚举与词表同源")
    class ChannelVocabulary {

        @Test
        @DisplayName("三通道逐一：safeOpa 发给护栏的 purpose 词 = 契约规范词，action=data:<同词>")
        void opaPayloadUsesContractVocabularyPerChannel() {
            clsAllows("col1");
            for (DecisionBundle.Purpose purpose : DecisionBundle.Purpose.values()) {
                svc.decide(asset(UNIVERSE), purpose, ctx());
            }

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> input = ArgumentCaptor.forClass(Map.class);
            verify(opa, times(DecisionBundle.Purpose.values().length))
                    .evaluate(eq(OpaPolicyService.POLICY_DATA_ACCESS), input.capture());
            List<Map<String, Object>> payloads = input.getAllValues();

            int i = 0;
            for (DecisionBundle.Purpose purpose : DecisionBundle.Purpose.values()) {
                String expected = DecisionChannelContract.normalize(purpose.name());
                assertNotNull(expected, purpose
                        + " 未命中通道词表＝枚举与词表两侧失配（只改一处的回归形态）");
                assertEquals(expected, payloads.get(i).get("purpose"),
                        "线上 purpose 词不得内联（第四处词面真值＝改名时静默漏改的那一处）");
                assertEquals("data:" + expected, payloads.get(i).get("action"),
                        "rego 的角色×通道矩阵读 action/purpose，两者分叉＝按页面档评估导出请求");
                i++;
            }
        }

        @Test
        @DisplayName("Purpose 枚举名与词表逐一对应（顺序与成员都不得单侧改）")
        void enumAndVocabularyStayPaired() {
            List<String> enumWords = java.util.Arrays.stream(DecisionBundle.Purpose.values())
                    .map(DecisionBundle.Purpose::name)
                    .map(String::toLowerCase)
                    .toList();
            assertEquals(DecisionChannelContract.known(), enumWords,
                    "新增/改名通道必须同时改词表与枚举（本用例是唯一对照点，禁只改一侧）");
        }

        @Test
        @DisplayName("scopeEvidence 的 purpose 词同样取单源（导出件权限范围串不得自造词形）")
        void scopeEvidenceUsesCanonicalWord() {
            clsAllows("col1");

            DecisionBundle b = svc.decide(asset(UNIVERSE), DecisionBundle.Purpose.EXPORT, ctx());

            assertTrue(b.scopeEvidence(DecisionBundle.Purpose.EXPORT)
                    .contains("purpose=" + DecisionChannelContract.PURPOSE_EXPORT),
                    "实际=" + b.scopeEvidence(DecisionBundle.Purpose.EXPORT));
        }
    }

    // ═══════════════ 3. 入口解析：缺失默认 PAGE，未识别必拒（D-B4-27 主面） ═══════════════

    @Nested
    @DisplayName("DecisionController 入参解析：认不出就 400，禁静默降级页面通道")
    class EntryParsing {

        private final SecurityDecisionService service = mock(SecurityDecisionService.class);
        /**
         * #142/B4-3e-79（D-B4-36）：入口解析组只验「词 → 通道」，声明域真值解析器原样回显
         * 入参（不取数）。真值解析的正反判据在
         * {@code DeclaredScopeResolutionServiceTest}／{@code DecisionControllerDeclaredScopeWiringTest}。
         */
        private final DeclaredScopeResolutionService declaredScopeResolver =
                mock(DeclaredScopeResolutionService.class);
        private final DecisionController controller =
                new DecisionController(service, declaredScopeResolver);

        {
            when(declaredScopeResolver.resolveForAiTool(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        private Map<String, Object> body(Object purpose) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("assetRef", Map.of("schema", "public", "table", TABLE, "columns", UNIVERSE));
            if (purpose != null) m.put("purpose", purpose);
            m.put("subject", Map.of("subjectId", "u1"));
            return m;
        }

        private DecisionBundle stubBundle() {
            return new DecisionBundle(new LinkedHashMap<>(), new LinkedHashMap<>(),
                    List.of(), new LinkedHashMap<>(), "d-1", "t-1");
        }

        /**
         * #142/B4-3e-35（收口 D-B4-59 属主侧）：{@code decide} 现返回
         * {@code ResponseEntity<ApiResponse<...>>}，信封经此取出。
         * body 为 null 即红——静默 NPE 会让"状态行有了、信封丢了"读成绿。
         */
        private ApiResponse<Map<String, Object>> envelope(
                ResponseEntity<ApiResponse<Map<String, Object>>> resp) {
            assertNotNull(resp.getBody(), "ResponseEntity 的 body 不得为 null");
            return resp.getBody();
        }

        @Test
        @DisplayName("成功腿状态行 200：信封 code=0 与状态行同号")
        void successLegCarries200() {
            when(service.decide(any(), any(), any())).thenReturn(stubBundle());

            Map<String, Object> m = body(DecisionChannelContract.PURPOSE_PAGE);
            m.put("subject", Map.of(DecisionChannelContract.KEY_SUBJECT_ID, "u1"));

            assertEquals(HttpStatus.OK.value(), controller.decide(m).getStatusCode().value(),
                    "成功腿状态行不是 200＝把成功报成失败（§4.7.2 禁反向折叠）");
        }

        @Test
        @DisplayName("无用户上下文的 403 腿状态行＝403，不再是恒 200 的裸回（D-B4-59 本尊）")
        void forbiddenLegCarries403OnTheStatusLine() {
            Map<String, Object> m = body(DecisionChannelContract.PURPOSE_PAGE);
            m.remove("subject"); // 信任链在单测里为空 ＋ 请求体无 subject ⇒ userId 空 ⇒ 403 腿

            ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.decide(m);

            assertEquals(HttpStatus.FORBIDDEN.value(), resp.getStatusCode().value(),
                    "拒绝语义只活在信封 code 里、状态行仍 200 ⇒ D-B4-59 在本端点复现");
            assertEquals(ApiResponse.CODE_FORBIDDEN, envelope(resp).getCode(),
                    "状态行与信封必须同号，否则只看其中一层的中间层读到的是两种结论");
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("内部裁决失败的不可用腿状态行＝503 单源常量，且不落成 403（§4.7.2 不可用≠无权限）")
        void unavailableLegCarries503FromSingleSource() {
            when(service.decide(any(), any(), any())).thenThrow(new RuntimeException("opa down"));

            Map<String, Object> m = body(DecisionChannelContract.PURPOSE_PAGE);
            m.put("subject", Map.of(DecisionChannelContract.KEY_SUBJECT_ID, "u1"));

            ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.decide(m);

            assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, resp.getStatusCode().value(),
                    "不可用腿的状态行未取单源常量＝状态行与信封两处各写一遍、会漂移");
            assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, envelope(resp).getCode(),
                    "状态行与信封的不可用态必须同号");
        }

        @Test
        @DisplayName("入参校验 400 腿状态行＝400：参数错误不得与拒绝/不可用同形")
        void badRequestLegCarries400() {
            // assetRef 缺 table ⇒ AssetRef.fromMap 返回 null ⇒ 走 400 腿（service 不得被调用：
            // 未打桩的 mock 返回 null 会在裁决腿抛 NPE、被 catch 折成 503，那样本例读到的就不是 400 腿）
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("assetRef", Map.of("columns", UNIVERSE));
            m.put("purpose", DecisionChannelContract.PURPOSE_PAGE);

            ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.decide(m);

            assertEquals(HttpStatus.BAD_REQUEST.value(), resp.getStatusCode().value());
            assertEquals(ApiResponse.CODE_BAD_REQUEST, envelope(resp).getCode());
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("purpose 缺失 → 按既约定默认 PAGE 且不报错（API 只增不改）")
        void absentPurposeKeepsPageDefault() {
            when(service.decide(any(), any(), any())).thenReturn(stubBundle());

            ApiResponse<Map<String, Object>> resp = envelope(controller.decide(body(null)));

            assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
            verify(service).decide(any(AssetRef.class), eq(DecisionBundle.Purpose.PAGE), any());
        }

        @Test
        @DisplayName("purpose 空白 → 仍默认 PAGE（与「未识别」分态，禁把空串当错词）")
        void blankPurposeKeepsPageDefault() {
            when(service.decide(any(), any(), any())).thenReturn(stubBundle());

            assertEquals(ApiResponse.CODE_SUCCESS, envelope(controller.decide(body("   "))).getCode());
            verify(service).decide(any(AssetRef.class), eq(DecisionBundle.Purpose.PAGE), any());
        }

        @Test
        @DisplayName("大小写/空白合法的 export → 仍解析为 EXPORT（兼容既有调用方）")
        void casedKnownWordResolves() {
            when(service.decide(any(), any(), any())).thenReturn(stubBundle());

            assertEquals(ApiResponse.CODE_SUCCESS, envelope(controller.decide(body(" Export "))).getCode());
            verify(service).decide(any(AssetRef.class), eq(DecisionBundle.Purpose.EXPORT), any());
        }

        @Test
        @DisplayName("未识别词（exprt/download）→ 400 带机读理由，且 service 零调用")
        void unknownWordIsRejectedNotDowngraded() {
            for (String raw : List.of("exprt", "download", "pages")) {
                ApiResponse<Map<String, Object>> resp = envelope(controller.decide(body(raw)));

                assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode(),
                        raw + " 被接受＝静默降级回潮：导出请求以页面档被裁决并返回 200");
                assertTrue(resp.getMessage().startsWith(DecisionChannelContract.REASON_UNRECOGNIZED),
                        raw + " 的理由必须可机读，实际=" + resp.getMessage());
                assertTrue(resp.getMessage().contains(raw), "400 必须回显实际收到的词，否则调用方猜不到拼写");
            }
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("正向对照：上面不是「decide 恒 400」——已知三词都能过解析")
        void knownWordsAreAccepted() {
            when(service.decide(any(), any(), any())).thenReturn(stubBundle());

            for (String word : DecisionChannelContract.known()) {
                assertEquals(ApiResponse.CODE_SUCCESS, envelope(controller.decide(body(word))).getCode(), word);
            }
            // 三词各解析一次 ⇒ 服务被调三次（若未识别词也被接受，这里会多调）
            verify(service, times(DecisionChannelContract.known().size()))
                    .decide(any(AssetRef.class), any(DecisionBundle.Purpose.class), any());
        }

        @Test
        @DisplayName("资产缺 table → 400（列面/通道改动不得影响既有入参校验面）")
        void missingTableStillRejected() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("assetRef", Map.of("columns", UNIVERSE));
            m.put("purpose", DecisionChannelContract.PURPOSE_PAGE);
            m.put("subject", Map.of("subjectId", "u1"));

            assertEquals(ApiResponse.CODE_BAD_REQUEST, envelope(controller.decide(m)).getCode());
            verifyNoInteractions(service);
        }
    }

    // ═══════════════ 4. 结构性约束：生产侧不得再写第四种叫法 ═══════════════

    @Test
    @DisplayName("bundle 列面段键集合固定（mode/names/visibleColumns/blockedColumns/policyRefs/三证据键）")
    void columnSegmentKeySetIsClosed() {
        clsAllows("col1");

        DecisionBundle b = svc.decide(asset(UNIVERSE), DecisionBundle.Purpose.PAGE, ctx());

        assertEquals(java.util.Set.of("mode", "names",
                        ColumnSurfaceContract.KEY_VISIBLE_COLUMNS,
                        ColumnSurfaceContract.KEY_BLOCKED_COLUMNS,
                        "policyRefs",
                        ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_SOURCE,
                        ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_RESOLVED),
                b.columns().keySet(),
                "列面段加键必须同步登记（加键＝消费者要重新分态）；缺键＝证据面缩水");
    }

    @Test
    @DisplayName("CLS 源拿到的投影列 = 调用方请求的列全集（universe 不得被改写或放大）")
    void clsSourceReceivesExactlyTheRequestedUniverse() {
        clsAllows("col1");

        svc.decide(asset(UNIVERSE), DecisionBundle.Purpose.PAGE, ctx());

        ArgumentCaptor<List<String>> cols = ArgumentCaptor.forClass(List.class);
        verify(cls).getColumns(eq(TABLE), eq("u1"), cols.capture());
        assertEquals(UNIVERSE, cols.getValue(),
                "裁决入口若擅自把空宇宙放大成「整表」，就等于伪造了一个不存在的列全集来源");
    }

    @Test
    @DisplayName("入口异常（table 空白）→ IllegalArgumentException，不得静默产出一个空 bundle")
    void blankTableFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> svc.decide(new AssetRef("public", "   ", UNIVERSE),
                        DecisionBundle.Purpose.PAGE, ctx()));
    }

    @Test
    @DisplayName("purpose 传 null 的入口调用 → 兜底 PAGE，且兜底词也取自词表")
    void nullPurposeDefaultsToPage() {
        clsAllows("col1");

        DecisionBundle b = svc.decide(asset(UNIVERSE), null, ctx());

        assertFalse(b.columnsDenied(), "入口兜底不得影响列面裁决（兜底只作用于通道词）");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> input = ArgumentCaptor.forClass(Map.class);
        verify(opa).evaluate(eq(OpaPolicyService.POLICY_DATA_ACCESS), input.capture());
        assertEquals(DecisionChannelContract.PURPOSE_PAGE, input.getValue().get("purpose"),
                "PAGE 兜底词必须与词表同源（此处内联 \"page\" 就是第四处词面真值）");
    }
}
