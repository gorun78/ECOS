package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildResult;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextPack;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ContextBuilderService 冻结测试（《认知域与智能域补充详细设计 v1.0》§5 / §16 验收矩阵）。
 *
 * <p>验收对应（SUP 号 = 文档 §16 验收矩阵 ID）：
 * <ul>
 *   <li><b>SUP-CTX-01</b>（Context Pack 来源均带 ID/版本/时点；缺少来源的事实类项被拒绝）：
 *       {@link #normalAssemblyProducesPackWithDigestAndTtl()}、
 *       {@link #factProjectionMissingSourceRejected()}、
 *       {@link #requiredAssetMissingVersionIs400()}；</li>
 *   <li><b>SUP-CTX-02</b>（未授权数据不进 Pack；权限依赖失败默认 DENY / fail-closed）：
 *       {@link #securityTrimDeniedRequiredSourceIs403()}、
 *       {@link #securityCheckpointUnavailableIsFailClosedDeny()}；</li>
 *   <li><b>SUP-CTX-03</b>（空集合/404/403/503 语义不混淆，不以空数据伪装依赖失败）：
 *       依赖缺失 503（{@link #requiredSourceReaderAbsentIs503()}、
 *       {@link #requiredSourceReadFailureIs503()}）、权限 403（上 SUP-CTX-02 两用例）、
 *       版本失配 409（{@link #versionMismatchIs409()}）、冲突未解 422
 *       （{@link #unresolvedConflictOnCriticalSourceIs422()}）、超预算 429
 *       （{@link #budgetExceededIs429()}、{@link #budgetTokensExceededIs429()}）——
 *       六类状态码各自独立；可选源降级走部分上下文 + warnings
 *       （{@link #optionalSourceDegradedToWarning()}）。</li>
 * </ul>
 *
 * <p>另覆盖 §5.6 稳定性测试：{@link #digestIsReproducible()}（相同来源版本、
 * 相同请求、相同策略 ⇒ contextDigest 稳定可复现）。</p>
 *
 * <p><b>测试文件已建、由主仓统一运行</b>（本任务不跑 mvn，主仓 SUP-Z 批次统一门禁执行）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ContextBuilderServiceTest {

    private static final String SRC = "asset-001";
    private static final String AS_OF = "2026-10-09T00:00:00Z";

    @Mock
    private ContextSecurityTrimmer trimmer;

    @Mock
    private ContextBuildEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        MDC.put("traceId", "tr-test-fixed");
    }

    @AfterEach
    void cleanMdc() {
        MDC.remove("traceId");
    }

    // ── 正常路径（SUP-CTX-01 正向） ──────────────────────────────────────────

    @Test
    @DisplayName("SUP-CTX-01/§5.3: 正常组装 ⇒ Pack 带 digest/ttl/traceId，源与事实均带 ID/版本/时点")
    void normalAssemblyProducesPackWithDigestAndTtl() {
        stubAllowAll();
        ContextBuilderService svc = builderWith(reader(Set.of(SRC), "version-A", false,
                new ContextSourceReader.FactProjection("revenue", "1000", SRC, "version-A", AS_OF)));

        ContextBuildResult result = svc.build(baseRequest());

        assertNotNull(result);
        assertTrue(result.getContextDigest().startsWith("sha256:"), "digest 前缀冻结");
        assertEquals(ContextBuilderService.DEFAULT_TTL_MILLIS, result.getTtlMillis());
        assertEquals("scenario-annual-forecast", result.getScenarioId());
        ContextPack pack = result.getPack();
        assertEquals("tr-test-fixed", pack.getTraceId(), "§12.2 统一 trace 维度回显");
        assertEquals(1, pack.getSources().size());
        ContextPack.SourceEntry s = pack.getSources().get(0);
        assertEquals(SRC, s.getSourceId());
        assertEquals("version-A", s.getVersion());
        assertEquals(AS_OF, s.getAsOfTime());
        assertEquals("GOVERNED", s.getAuthority());
        assertEquals("OK", s.getAccessResult());
        assertEquals(1, pack.getFacts().size());
        ContextPack.FactEntry f = pack.getFacts().get(0);
        assertEquals(SRC, f.getSourceId());
        assertEquals("version-A", f.getVersion());
        assertEquals(AS_OF, f.getAsOfTime());
        assertNotNull(result.getExpiresAt(), "TTL 过期时点必须回显");
    }

    @Test
    @DisplayName("§5.6 稳定性测试: 相同来源版本 + 相同请求 + 相同策略 ⇒ contextDigest 可复现")
    void digestIsReproducible() {
        stubAllowAll();
        ContextBuilderService svc = builderWith(reader(Set.of(SRC), "version-A", false,
                new ContextSourceReader.FactProjection("revenue", "1000", SRC, "version-A", AS_OF)));

        ContextBuildResult r1 = svc.build(baseRequest());
        ContextBuildResult r2 = svc.build(baseRequest());

        assertEquals(r1.getContextDigest(), r2.getContextDigest(),
                "相同来源版本/请求/策略下 digest 必须一致（§5.6 稳定性）");
    }

    // ── SUP-CTX-03：依赖缺失 503（真实透传，禁伪空集合） ────────────────────

    @Test
    @DisplayName("SUP-CTX-03: 必需源 reader 未接线 ⇒ 503 E503_DEPENDENCY_MISSING")
    void requiredSourceReaderAbsentIs503() {
        ContextBuilderService svc = builderWith(); // 零 reader = 全部未接线

        ContextBuildException e = assertThrows(ContextBuildException.class,
                () -> svc.build(baseRequest()));
        assertEquals(ContextBuildException.E503_DEPENDENCY_MISSING, e.errorCode);
        assertEquals(503, e.httpStatus);
    }

    @Test
    @DisplayName("SUP-CTX-03: 必需源读取故障 ⇒ 503（非 200 空 Pack 伪装）")
    void requiredSourceReadFailureIs503() {
        stubAllowAll();
        ContextBuilderService svc = builderWith(reader(Set.of(SRC), "version-A", false));

        ContextBuildException e = assertThrows(ContextBuildException.class,
                () -> svc.build(baseRequest()));
        assertEquals(ContextBuildException.E503_DEPENDENCY_MISSING, e.errorCode);
        assertEquals(503, e.httpStatus);
    }

    @Test
    @DisplayName("SUP-CTX-03: 可选源不可用 ⇒ 部分上下文 + warnings + UNAVAILABLE（与 OK/DENIED 语义区分）")
    void optionalSourceDegradedToWarning() {
        stubAllowAll();
        ContextSourceRef optional = new ContextSourceRef();
        optional.setSourceType("KNOWLEDGE");
        optional.setSourceId("kb-9");
        optional.setVersion("v1");
        optional.setRequired(false);
        ContextBuilderService svc = builderWith(reader(Set.of(SRC), "version-A", false));

        ContextBuildRequest req = baseRequest();
        req.getRequiredAssets().add(optional);
        ContextBuildResult r = svc.build(req);

        assertTrue(r.isReady());
        assertTrue(r.getPack().getWarnings().stream().anyMatch(w -> w.contains("kb-9")),
                "可选源降级必须留痕 warnings（禁静默丢失）");
        assertTrue(r.getPack().getSources().stream()
                        .anyMatch(s -> "UNAVAILABLE".equals(s.getAccessResult())
                                && "kb-9".equals(s.getSourceId())),
                "UNAVAILABLE 独立于 OK/DENIED（状态语义不混淆）");
    }

    // ── SUP-CTX-02：权限拒绝 403（fail-closed） ─────────────────────────────

    @Test
    @DisplayName("SUP-CTX-02/03: security 裁剪 DENY 必需源 ⇒ 403 E403_SOURCE_DENIED")
    void securityTrimDeniedRequiredSourceIs403() {
        when(trimmer.trim(any(), any(), any()))
                .thenReturn(ContextSecurityTrimmer.TrimDecision.deny());
        ContextBuilderService svc = builderWith(reader(Set.of(SRC), "version-A", false,
                new ContextSourceReader.FactProjection("revenue", "1000", SRC, "version-A", AS_OF)));

        ContextBuildException e = assertThrows(ContextBuildException.class,
                () -> svc.build(baseRequest()));
        assertEquals(ContextBuildException.E403_SOURCE_DENIED, e.errorCode);
        assertEquals(403, e.httpStatus);
        assertEquals(ContextBuildException.MSG_SOURCE_DENIED, e.getMessage());
    }

    @Test
    @DisplayName("SUP-CTX-02: 权限检查点整体不可用 ⇒ fail-closed 默认 DENY ⇒ 403（0 泄露）")
    void securityCheckpointUnavailableIsFailClosedDeny() {
        // 不注入 trimmer Bean（Optional.empty）= security 依赖缺失
        ContextBuilderService failClosed = new ContextBuilderService(
                List.of(reader(Set.of(SRC), "version-A", false,
                        new ContextSourceReader.FactProjection("revenue", "1", SRC, "version-A", AS_OF))),
                Optional.empty(), eventPublisher);

        ContextBuildException e = assertThrows(ContextBuildException.class,
                () -> failClosed.build(baseRequest()));
        assertEquals(ContextBuildException.E403_SOURCE_DENIED, e.errorCode);
        assertEquals(403, e.httpStatus);
    }

    // ── SUP-CTX-03：版本失配 409 ────────────────────────────────────────────

    @Test
    @DisplayName("SUP-CTX-03: 读回版本 ≠ 期望版本 ⇒ 409 E409_VERSION_STALE（禁旧版本执行）")
    void versionMismatchIs409() {
        stubAllowAll();
        // 请求声明 version-A，源侧实际回 version-B
        ContextBuilderService svc = builderWith(reader(Set.of(SRC), "version-B", false));

        ContextBuildException e = assertThrows(ContextBuildException.class,
                () -> svc.build(baseRequest()));
        assertEquals(ContextBuildException.E409_VERSION_STALE, e.errorCode);
        assertEquals(409, e.httpStatus);
    }

    @Test
    @DisplayName("SUP-CTX-01 fail-loud: 权威源清单缺 version ⇒ 400 E400_SOURCE_REF_MISSING")
    void requiredAssetMissingVersionIs400() {
        stubAllowAll();
        ContextBuilderService svc = builderWith();
        ContextSourceRef bad = new ContextSourceRef();
        bad.setSourceType("DATA_ASSET");
        bad.setSourceId(SRC);
        // version 缺省 null

        ContextBuildRequest req = new ContextBuildRequest();
        req.setScenarioId("s1");
        req.getScope().setTenantId("tenant-1");
        req.getRequiredAssets().add(bad);

        ContextBuildException e = assertThrows(ContextBuildException.class,
                () -> svc.build(req));
        assertEquals(ContextBuildException.E400_SOURCE_REF_MISSING, e.errorCode);
        assertEquals(400, e.httpStatus);
    }

    @Test
    @DisplayName("SUP-CTX-01: 事实投影缺 sourceId/version ⇒ 400 拒绝（不静默丢行进 Pack）")
    void factProjectionMissingSourceRejected() {
        stubAllowAll();
        ContextBuilderService svc = builderWith(reader(Set.of(SRC), "version-A", false,
                new ContextSourceReader.FactProjection("revenue", "1000", null, null, AS_OF)));

        ContextBuildException e = assertThrows(ContextBuildException.class,
                () -> svc.build(baseRequest()));
        assertEquals(ContextBuildException.E400_SOURCE_REF_MISSING, e.errorCode);
        assertEquals(400, e.httpStatus);
    }

    // ── 冲突语义：422 未解 + 非关键阻断 ─────────────────────────────────────

    @Test
    @DisplayName("§5.4-6: 两权威源同 field 不同值 + successCritical ⇒ 422 E422_CONFLICT_UNRESOLVED")
    void unresolvedConflictOnCriticalSourceIs422() {
        stubAllowAll();
        ContextBuilderService svc = builderWith(
                reader(Set.of(SRC), "version-A", true,
                        new ContextSourceReader.FactProjection("revenue", "1000", SRC, "version-A", AS_OF)),
                reader(Set.of("asset-002"), "version-2", true,
                        new ContextSourceReader.FactProjection("revenue", "2000", "asset-002", "version-2", AS_OF)));
        ContextBuildRequest req = baseRequest();
        req.getRequiredAssets().add(source("asset-002", "version-2", false));
        req.getRequiredAssets().get(0).setSuccessCritical(true);

        ContextBuildException e = assertThrows(ContextBuildException.class,
                () -> svc.build(req));
        assertEquals(ContextBuildException.E422_CONFLICT_UNRESOLVED, e.errorCode);
        assertEquals(422, e.httpStatus);
    }

    @Test
    @DisplayName("§5.4-6: 同 field 不同值但皆为非权威源 + 非成功条件 ⇒ UNRESOLVED conflict 条目（不 4xx）")
    void unresolvedConflictWithoutCriticalYieldsEntry() {
        stubAllowAll();
        ContextBuilderService svc = builderWith(
                reader(Set.of(SRC), "version-A", false,
                        new ContextSourceReader.FactProjection("revenue", "1000", SRC, "version-A", AS_OF)),
                reader(Set.of("asset-002"), "version-2", false,
                        new ContextSourceReader.FactProjection("revenue", "2000", "asset-002", "version-2", AS_OF)));
        ContextBuildRequest req = baseRequest();
        req.getRequiredAssets().add(source("asset-002", "version-2", false));

        ContextBuildResult r = svc.build(req);
        assertEquals(1, r.getPack().getConflicts().size());
        ContextPack.ConflictEntry c = r.getPack().getConflicts().get(0);
        assertEquals("revenue", c.getFieldKey());
        assertEquals(ContextPack.RESOLUTION_UNRESOLVED, c.getResolution());
        assertEquals(2, c.getValues().size());
        assertNotNull(c.getRequiredAction(), "待处理动作必须显式（禁 LLM 自行裁决为事实）");
    }

    // ── SUP-CTX-03：超预算 429 ──────────────────────────────────────────────

    @Test
    @DisplayName("§5.4-8: 超 maxSources 预算 ⇒ 429 E429_BUDGET_EXCEEDED（显式失败）")
    void budgetExceededIs429() {
        stubAllowAll();
        ContextBuilderService svc = builderWith(
                reader(Set.of(SRC), "version-A", false),
                reader(Set.of("asset-002"), "version-2", false));
        ContextBuildRequest req = baseRequest();
        req.getRequiredAssets().add(source("asset-002", "version-2", false));
        req.getBudget().setMaxSources(1); // 实际 2 源 > 1

        ContextBuildException e = assertThrows(ContextBuildException.class,
                () -> svc.build(req));
        assertEquals(ContextBuildException.E429_BUDGET_EXCEEDED, e.errorCode);
        assertEquals(429, e.httpStatus);
    }

    @Test
    @DisplayName("§5.4-8: 超 maxTokens 预算（条目×权重估算）⇒ 429")
    void budgetTokensExceededIs429() {
        stubAllowAll();
        ContextBuilderService svc = builderWith(reader(Set.of(SRC), "version-A", false,
                new ContextSourceReader.FactProjection("revenue", "1000", SRC, "version-A", AS_OF)));
        ContextBuildRequest req = baseRequest();
        req.getBudget().setMaxTokens(1); // 至少 2 条目 × 80 > 1

        ContextBuildException e = assertThrows(ContextBuildException.class,
                () -> svc.build(req));
        assertEquals(ContextBuildException.E429_BUDGET_EXCEEDED, e.errorCode);
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private void stubAllowAll() {
        when(trimmer.trim(any(), any(), any()))
                .thenReturn(ContextSecurityTrimmer.TrimDecision.allowAll());
    }

    private ContextBuilderService builderWith(ContextSourceReader... rs) {
        return new ContextBuilderService(List.of(rs), Optional.of(trimmer), eventPublisher);
    }

    private static ContextSourceRef source(String id, String version, boolean successCritical) {
        ContextSourceRef ref = new ContextSourceRef();
        ref.setSourceType("DATA_ASSET");
        ref.setSourceId(id);
        ref.setVersion(version);
        ref.setRequired(true);
        ref.setSuccessCritical(successCritical);
        return ref;
    }

    private ContextBuildRequest baseRequest() {
        ContextBuildRequest req = new ContextBuildRequest();
        req.setRequestId("req-123");
        req.setScenarioId("scenario-annual-forecast");
        req.getScope().setTenantId("tenant-1");
        req.getScope().setAsOfTime(AS_OF);
        ContextBuildRequest.BlueprintRef bp = new ContextBuildRequest.BlueprintRef();
        bp.setBlueprintId("bp-001");
        bp.setVersion("4");
        req.setBlueprintRef(bp);
        req.getRequiredAssets().add(source(SRC, "version-A", false));
        return req;
    }

    /** 测试 reader：仅承接 owned sourceId 的读取；owned 源声明读失败时抛依赖异常。 */
    private static ContextSourceReader reader(Set<String> ownedSources, String version,
                                              boolean authoritative,
                                              ContextSourceReader.FactProjection... facts) {
        return new ContextSourceReader() {
            @Override
            public boolean supports(String sourceType) {
                return true; // 归组按 owned sourceId（见 read 内判定）
            }

            @Override
            public SourceReadResult read(ContextBuildRequest request, ContextSourceRef ref) {
                if (!ownedSources.contains(ref.getSourceId())) {
                    // 非本 reader 服务面：视为面向未接线，交由 Builder 判 503/降级
                    return null;
                }
                if (facts.length == 0 && version == null) {
                    throw new ContextReadException(ref.getSourceType(), ref.getSourceId(), "模拟下游 503");
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
