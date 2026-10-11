package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildResult;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextPack;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef;
import com.chinacreator.gzcm.engine.cognitive.api.context.EvidenceRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SUP-CTX-01 补充契约测试：Context Pack "三要素引用" 不变量
 * （《认知域与智能域补充详细设计 v1.0》§5.3 / §5.6 可追溯测试 / §16 SUP-CTX-01）。
 *
 * <p>断言面分两层：
 * <ol>
 *   <li><b>契约类型自身</b>：{@link EvidenceRef#of} / {@code validateTraceability()} ——
 *       事实类引用可解析到 sourceId + version + asOfTime 三要素，缺任一即拒绝（fail-loud）；</li>
 *   <li><b>Builder 装配门禁</b>（经 {@link ContextBuilderService}，mock 装配同
 *       {@code ContextBuilderServiceTest}）：权威源清单缺 version、事实投影缺
 *       sourceId/version ⇒ 400 {@code ECOS-COG-400S}，半残引用不得混入 Pack；
 *       正常组装 ⇒ 每个 sources[]/facts[] 条目 citation 三要素非空。</li>
 * </ol>
 *
 * <p>诚实边界（生产行为原样断言，见报告）：Builder 对源侧/事实条目的
 * <b>asOfTime 缺失不做 400 拒绝</b>（源侧缺 version 会以 503 掩盖、事实 asOfTime 走
 * 源侧回退），故 asOfTime 完整性的正向断言绑定在 {@code EvidenceRef} 契约方法与
 * 正常组装路径上，而非 Builder 400 断言。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SUP-CTX-01 补充: Context Pack 三要素引用契约 (sourceId/version/asOfTime)")
class ContextPackContractTest {

    private static final String SRC = "asset-001";
    private static final String AS_OF = "2026-10-09T00:00:00Z";

    @Mock
    private ContextSecurityTrimmer trimmer;

    @Mock
    private ContextBuildEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        MDC.put("traceId", "tr-contract-test");
    }

    @AfterEach
    void cleanMdc() {
        MDC.remove("traceId");
    }

    // ── 1. 契约类型自身：EvidenceRef 三要素引用 ──

    @Test
    @DisplayName("§5.6: EvidenceRef 三要素齐全 ⇒ of() 工厂放行且 isComplete 家族一致")
    void evidenceRefCompleteTrinityPasses() {
        EvidenceRef ref = EvidenceRef.of("src-1", "v3", AS_OF);

        assertEquals("src-1", ref.getSourceId());
        assertEquals("v3", ref.getVersion());
        assertEquals(AS_OF, ref.getAsOfTime());
        assertTrue(ref.validateTraceability().isEmpty());
    }

    @Test
    @DisplayName("§5.6: 缺 sourceId ⇒ of() 立即拒绝，错误指明缺失要素（禁半残引用进入 Pack）")
    void evidenceRefMissingSourceIdRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> EvidenceRef.of(null, "v3", AS_OF));
        assertTrue(e.getMessage().contains("sourceId"), "缺失要素必须点名: " + e.getMessage());
    }

    @Test
    @DisplayName("§5.6: 缺 version ⇒ of() 立即拒绝（版本是引用可复算锚点）")
    void evidenceRefMissingVersionRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> EvidenceRef.of("src-1", "  ", AS_OF));
        assertTrue(e.getMessage().contains("version"));
    }

    @Test
    @DisplayName("§5.6: 缺 asOfTime ⇒ of() 立即拒绝（数据时点是三要素之一，非 timestamp）")
    void evidenceRefMissingAsOfTimeRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> EvidenceRef.of("src-1", "v3", null));
        assertTrue(e.getMessage().contains("asOfTime"));
    }

    @Test
    @DisplayName("validateTraceability 与 validate 职责分离: 登记面含 timestamp, 可追溯面只认 sourceId/version/asOfTime")
    void traceabilityValidationIsIndependentOfRegistrationValidation() {
        // 登记面必填 timestamp（采集时点）；可追溯面必填 asOfTime（数据时点）
        EvidenceRef regOnly = new EvidenceRef();
        regOnly.setSourceId("src-1");
        regOnly.setVersion("v3");
        regOnly.setTimestamp("2026-10-09T12:00:00Z");
        assertTrue(regOnly.isComplete(), "登记面三要素(sourceId/version/timestamp)齐备");
        assertEquals(List.of("asOfTime"), regOnly.validateTraceability(),
                "可追溯面必须独立发现 asOfTime 缺失（时序字段不得互相顶替）");

        EvidenceRef asOfOnly = new EvidenceRef();
        asOfOnly.setSourceId("src-1");
        asOfOnly.setVersion("v3");
        asOfOnly.setAsOfTime(AS_OF);
        assertTrue(asOfOnly.validateTraceability().isEmpty());
        assertFalse(asOfOnly.isComplete(), "登记面缺 timestamp 仍不可直接登记");
    }

    // ── 2. Builder 装配门禁：半残引用 400 拒绝（禁混入 Pack）──

    @Test
    @DisplayName("SUP-CTX-01: 权威源清单缺 version ⇒ 400 ECOS-COG-400S（引用不完整禁进入组装）")
    void requiredSourceMissingVersionRejected400() {
        // 步骤 1 请求校验先于步骤 3 读取/步骤 4 裁剪：本用例 400 即止，trimmer 不参与（不 stub）
        ContextBuilderService svc = new ContextBuilderService(
                List.of(), Optional.of(trimmer), eventPublisher);
        ContextSourceRef bad = new ContextSourceRef();
        bad.setSourceType("DATA_ASSET");
        bad.setSourceId(SRC);
        // version = null（半残引用）

        ContextBuildRequest req = new ContextBuildRequest();
        req.setScenarioId("s1");
        req.setScope(new ContextBuildRequest.Scope());
        req.getScope().setTenantId("tenant-1");
        req.getRequiredAssets().add(bad);

        ContextBuildException e = assertThrows(ContextBuildException.class, () -> svc.build(req));
        assertEquals(ContextBuildException.E400_SOURCE_REF_MISSING, e.errorCode);
        assertEquals(400, e.httpStatus);
    }

    @Test
    @DisplayName("SUP-CTX-01: 事实投影缺 sourceId/version ⇒ 400 ECOS-COG-400S（事实行不得无来源混入 Pack）")
    void factProjectionMissingCitationRejected400() {
        when(trimmer.trim(any(), any(), any()))
                .thenReturn(ContextSecurityTrimmer.TrimDecision.allowAll());
        ContextBuilderService svc = new ContextBuilderService(List.of(
                        reader(Set.of(SRC), "version-A", false,
                                new ContextSourceReader.FactProjection("revenue", "1000", null, null, AS_OF))),
                Optional.of(trimmer), eventPublisher);

        ContextBuildException e = assertThrows(ContextBuildException.class,
                () -> svc.build(baseRequest()));
        assertEquals(ContextBuildException.E400_SOURCE_REF_MISSING, e.errorCode);
        assertEquals(400, e.httpStatus);
    }

    // ── 3. 正向契约：正常组装的 Pack 每个来源/事实均带完整 citation 三要素 ──

    @Test
    @DisplayName("SUP-CTX-01 正向: 正常组装 ⇒ sources[]/facts[] 每项 sourceId+version+asOfTime 均非空且一致")
    void emittedSourcesAndFactsAllCarryFullCitationTrinity() {
        when(trimmer.trim(any(), any(), any()))
                .thenReturn(ContextSecurityTrimmer.TrimDecision.allowAll());
        ContextBuilderService svc = new ContextBuilderService(List.of(
                        reader(Set.of(SRC), "version-A", false,
                                new ContextSourceReader.FactProjection("revenue", "1000", SRC, "version-A", AS_OF))),
                Optional.of(trimmer), eventPublisher);

        ContextBuildResult r = svc.build(baseRequest());
        ContextPack pack = r.getPack();
        assertFalse(pack.getSources().isEmpty());
        assertFalse(pack.getFacts().isEmpty());
        for (ContextPack.SourceEntry s : pack.getSources()) {
            assertNotNull(s.getSourceId(), "source 条目 sourceId 必填");
            assertFalse(s.getSourceId().isBlank());
            assertNotNull(s.getVersion(), "source 条目 version 必填（引用可复算锚点）");
            assertFalse(s.getVersion().isBlank());
            assertNotNull(s.getAsOfTime(), "source 条目 asOfTime 必填（数据时点）");
            assertFalse(s.getAsOfTime().isBlank());
        }
        for (ContextPack.FactEntry f : pack.getFacts()) {
            assertNotNull(f.getSourceId());
            assertFalse(f.getSourceId().isBlank());
            assertNotNull(f.getVersion());
            assertFalse(f.getVersion().isBlank());
            assertNotNull(f.getAsOfTime(), "事实必须可解析到数据时点（§5.6 可追溯）");
            assertFalse(f.getAsOfTime().isBlank());
            // citation 与所属权威源同源一致（禁张冠李戴）
            assertEquals(SRC, f.getSourceId());
            assertEquals("version-A", f.getVersion());
            assertEquals(AS_OF, f.getAsOfTime());
        }
    }

    // ── fixtures（对齐 ContextBuilderServiceTest）──

    private ContextBuildRequest baseRequest() {
        ContextBuildRequest req = new ContextBuildRequest();
        req.setRequestId("req-cta");
        req.setScenarioId("scenario-contract");
        req.setScope(new ContextBuildRequest.Scope());
        req.getScope().setTenantId("tenant-1");
        req.getScope().setAsOfTime(AS_OF);
        ContextSourceRef src = new ContextSourceRef();
        src.setSourceType("DATA_ASSET");
        src.setSourceId(SRC);
        src.setVersion("version-A");
        req.getRequiredAssets().add(src);
        return req;
    }

    private static ContextSourceReader reader(Set<String> ownedSources, String version,
                                              boolean authoritative,
                                              ContextSourceReader.FactProjection... facts) {
        return new ContextSourceReader() {
            @Override
            public boolean supports(String sourceType) {
                return true;
            }

            @Override
            public SourceReadResult read(ContextBuildRequest request, ContextSourceRef ref) {
                if (!ownedSources.contains(ref.getSourceId())) {
                    return null;
                }
                SourceReadResult r = new SourceReadResult();
                r.setVersion(version);
                r.setAsOfTime(AS_OF);
                r.setAuthoritative(authoritative);
                r.setFreshness("CURRENT");
                r.getFacts().addAll(List.of(facts));
                return r;
            }
        };
    }
}
