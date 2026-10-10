package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildResult;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextPack;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef;
import com.chinacreator.gzcm.engine.cognitive.api.context.Gap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Context Builder 组装服务（《认知域与智能域补充详细设计 v1.0》§5；验收 SUP-CTX-01/02/03）。
 *
 * <p>职责（§5.1）：一次任务/阶段执行前，按当前主体权限与目标范围，把请求声明的权威资产
 * 组织成结构化 Context Pack，回答"本次任务应信任什么、缺什么、哪些信息冲突"。
 * <b>不新建 cognitive_context 表</b>（§5.2 / D 层红线）：Pack 纯请求态 DTO，
 * 本服务成功路径零写动作（推理结果不落盘，ADR-9 口径）；审计/事件如需记录，
 * 由既有 EventBus/AuditEvent 单通道落 digest 与引用 ID（载荷禁带敏感正文）。</p>
 *
 * <h3>组装算法（§5.4 十步，build 内顺序）</h3>
 * <ol>
 *   <li>校验请求：scenarioId/scope/预算 + 权威源清单 fail-loud（SUP-CTX-01 缺三要素拒绝）；</li>
 *   <li>建立权威源清单：仅处理 requiredAssets 显式声明项（禁无界检索全平台）；</li>
 *   <li>读取元数据：按 sourceType 路由 {@link ContextSourceReader} SPI；未接线 ⇒
 *       依赖缺失语义（必需 503 / 可选 warnings），<b>禁伪造空集合</b>（§5.5）；</li>
 *   <li>权限与密级裁剪：{@link ContextSecurityTrimmer} 检查点；<b>不可用 ⇒ fail-closed
 *       默认 DENY</b>（SUP-CTX-02：0 泄露，权限依赖失败默认 DENY）；</li>
 *   <li>统一引用和版本：ref.version / expectedAsOfTime 失配 ⇒ 409 E409_VERSION_STALE；</li>
 *   <li>冲突检测：同 fieldKey 多值且权威不可定 ⇒ UNRESOLVED conflict（禁 LLM 裁决）；
 *       successCritical 源未解冲突 ⇒ 422 E422_CONFLICT_UNRESOLVED；</li>
 *   <li>缺口识别：依赖缺失 / 权限裁剪 / 能力缺口 / 成功条件未满足（§6.5 结构）；</li>
 *   <li>预算裁剪：maxSources/maxTokens 超限 ⇒ 429 E429_BUDGET_EXCEEDED（禁"已完整"话术）；</li>
 *   <li>生成摘要哈希：{@link ContextDigestCalculator}（固定序 SHA-256，可复现）；</li>
 *   <li>过期与刷新：TTL 生效窗口写入 expiresAt；过期须重建，禁把旧 Pack 当最新事实。</li>
 * </ol>
 *
 * <p>失败语义红线（§5.5 / SUP-CTX-03）：依赖缺失=503、权限拒绝=403、版本失配=409、
 * 冲突未解=422、超预算=429、引用缺三要素=400 —— 一律经 {@link ContextBuildException}
 * 抛出，由 {@code ContextBuildErrorAdvice} 转 ApiResponse 错误体（traceId 走 MDC 回显）。
 * 构造器注入；{@code List<ContextSourceReader>} 无 Bean 注入时 Spring 给空 List（SPI 全未接线）。</p>
 */
@Service
public class ContextBuilderService {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilderService.class);

    /** TTL 缺省（5 分钟；与 §5.2 响应示例 expiresAt 同口径）。 */
    public static final long DEFAULT_TTL_MILLIS = 300_000L;
    /** 预算粗估：每条 context 条目（source/fact/ref/warning）的 token 权重缺省。 */
    public static final int TOKEN_WEIGHT_PER_ENTRY = 80;

    private final List<ContextSourceReader> readers;
    private final Optional<ContextSecurityTrimmer> securityTrimmer;
    private final ContextBuildEventPublisher eventPublisher;

    public ContextBuilderService(List<ContextSourceReader> readers,
                                 Optional<ContextSecurityTrimmer> securityTrimmer,
                                 ContextBuildEventPublisher eventPublisher) {
        this.readers = readers == null ? List.of() : List.copyOf(readers);
        this.securityTrimmer = securityTrimmer == null ? Optional.empty() : securityTrimmer;
        this.eventPublisher = eventPublisher;
    }

    /**
     * §5.4 十步组装入口。失败语义见类注释（503/403/409/422/429/400 各自独立，禁折叠 200）。
     * 返回体含 contextDigest / ttlMillis / scenarioId / blueprintRef / scopeRef / pack + trace 维度。
     */
    public ContextBuildResult build(ContextBuildRequest request) {
        // 步骤 1 —— 校验请求
        validateRequest(request);
        String traceId = EcosTraceContext.currentTraceId();

        // 步骤 2 —— 建立权威源清单（仅 requested 声明项，禁无界检索）
        List<ContextSourceRef> required = new ArrayList<>(request.getRequiredAssets());

        // 步骤 3 —— 读取元数据（未接线 = 依赖缺失，不伪造）
        Map<String, ContextSourceReader.SourceReadResult> readResults = new LinkedHashMap<>();
        for (ContextSourceRef ref : required) {
            readResults.put(key(ref), readOne(request, ref));
        }

        // 步骤 4 —— 权限与密级裁剪（fail-closed）+ 装入 Pack
        ContextPack pack = initializePack(request);
        for (ContextSourceRef ref : required) {
            ContextSourceReader.SourceReadResult r = readResults.get(key(ref));
            if (r == null) {
                // 可选源读失败/未接线：显式留痕（禁静默丢失），不伪造空数据
                markUnavailableOptional(pack, ref);
                continue;
            }
            TrimOutcome trim = applySecurityTrim(request, ref, pack, r);
            if (!trim.permitted) {
                if (ref.isRequired()) {
                    throw new ContextBuildException(ContextBuildException.E403_SOURCE_DENIED,
                            ContextBuildException.MSG_SOURCE_DENIED,
                            "sourceId=" + ref.getSourceId());
                }
                invalidateSource(pack, ref, trim.reason);
                continue;
            }
            ingestSource(request, pack, ref, r);
            if (trim.partialHidden) {
                markPermissionGap(pack, ref, trim.hiddenItems);
            }
        }

        // 步骤 5 —— 统一引用和版本（失配 ⇒ 409）
        enforceVersionConsistency(request, required, readResults, pack);

        // 步骤 6 —— 冲突检测（禁 LLM 裁决为事实）
        detectConflicts(required, pack);

        // 步骤 7 —— 缺口识别（successCriteria / 能力 / 权限裁剪痕迹）
        generateGaps(request, required, pack);

        // 步骤 8 —— 预算裁剪（超限 ⇒ 429 显式失败）
        enforceBudget(request, pack);

        // 步骤 9 —— 生成摘要哈希（固定字段序 SHA-256，可复现）
        pack.setContextDigest(ContextDigestCalculator.digest(pack));

        // 步骤 10 —— 过期与刷新（TTL 窗口）
        applyTtl(request, pack);

        ContextBuildResult result = toResult(request, pack, traceId);
        eventPublisher.publishBuilt(result);
        return result;
    }

    // ── 步骤 1：校验请求（SUP-CTX-01 fail-loud）───────────────────────────────

    private void validateRequest(ContextBuildRequest request) {
        if (request == null) {
            failRef("request");
        }
        if (isBlank(request.getScenarioId())) {
            failRef("scenarioId");
        }
        if (request.getScope() == null || isBlank(request.getScope().getTenantId())) {
            failRef("scope.tenantId");
        }
        for (ContextSourceRef ref : request.getRequiredAssets()) {
            if (ref == null || isBlank(ref.getSourceType()) || isBlank(ref.getSourceId())
                    || isBlank(ref.getVersion())) {
                failRef("requiredAssets[].(" + "sourceType|sourceId|version)");
            }
            List<String> evMissing = new ArrayList<>();
            for (String ev : ref.getEvidenceRefs()) {
                if (isBlank(ev)) evMissing.add("evidenceRef");
            }
            if (!evMissing.isEmpty()) {
                failRef("requiredAssets[" + ref.getSourceType() + ":" + ref.getSourceId() + "].evidenceRefs");
            }
        }
        if (request.getBudget() != null
                && (request.getBudget().getMaxTokens() < 0
                    || (request.getBudget().getMaxSources() != null
                        && request.getBudget().getMaxSources() <= 0))) {
            failRef("budget");
        }
        if (request.getTtlMillis() != null && request.getTtlMillis() <= 0) {
            failRef("ttlMillis");
        }
        if (!isBlank(request.getExpectedAsOfTime())) {
            parseInstant(request.getExpectedAsOfTime());
        }
        if (request.getScope() != null && !isBlank(request.getScope().getAsOfTime())) {
            parseInstant(request.getScope().getAsOfTime());
        }
    }

    private static void failRef(String field) {
        throw new ContextBuildException(ContextBuildException.E400_SOURCE_REF_MISSING,
                ContextBuildException.MSG_SOURCE_REF_MISSING, field);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static Instant parseInstant(String iso) {
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException e) {
            failRef("asOfTimeIso=" + iso);
            return null; // 不可达
        }
    }

    // ── 步骤 3：读取元数据（按 sourceType 路由 SPI）──────────────────────────

    private ContextSourceReader.SourceReadResult readOne(ContextBuildRequest request,
                                                         ContextSourceRef ref) {
        ContextSourceReader reader = readers.stream()
                .filter(r -> r.supports(ref.getSourceType()))
                .findFirst().orElse(null);
        if (reader == null) {
            if (ref.isRequired()) {
                throw new ContextBuildException(ContextBuildException.E503_DEPENDENCY_MISSING,
                        ContextBuildException.MSG_DEPENDENCY_MISSING,
                        "sourceType=" + ref.getSourceType() + ",sourceId=" + ref.getSourceId()
                                + " (reader 未接线)");
            }
            log.info("Context Builder 可选源未接线，记 warnings 降级: type={}", ref.getSourceType());
            return null; // 调用侧记降级痕
        }
        try {
            ContextSourceReader.SourceReadResult r = reader.read(request, ref);
            if (r == null) {
                throw new ContextReadException(ref.getSourceType(), ref.getSourceId(),
                        "源侧返回 null（视为不可用，禁按空数据处理）");
            }
            // 源侧引用三要素复核（SUP-CTX-01：来源均带 ID/版本/时点）
            if (!isBlank(ref.getSourceId()) && isBlank(r.getVersion())) {
                throw new ContextReadException(ref.getSourceType(), ref.getSourceId(),
                        "源侧返回缺 version（引用不完整）");
            }
            return r;
        } catch (ContextReadException e) {
            if (ref.isRequired()) {
                throw new ContextBuildException(ContextBuildException.E503_DEPENDENCY_MISSING,
                        ContextBuildException.MSG_DEPENDENCY_MISSING,
                        "sourceType=" + ref.getSourceType() + ",sourceId=" + ref.getSourceId());
            }
            log.info("可选源读失败降级: sourceId={} err={}", ref.getSourceId(), e.getMessage());
            return null;
        }
    }

    private static String key(ContextSourceRef ref) {
        return ref.getSourceType() + ":" + ref.getSourceId();
    }

    // ── 步骤 4：权限裁剪检查点（fail-closed）─────────────────────────────────

    private TrimOutcome applySecurityTrim(ContextBuildRequest request, ContextSourceRef ref,
                                          ContextPack pack,
                                          ContextSourceReader.SourceReadResult r) {
        ContextSecurityTrimmer trimmer = securityTrimmer.orElse(null);
        if (trimmer == null) {
            // §5.5 / SUP-CTX-02：security 依赖不可用 ⇒ 默认 DENY（fail-closed，0 泄露）
            if (ref.isRequired()) {
                throw new ContextBuildException(ContextBuildException.E403_SOURCE_DENIED,
                        ContextBuildException.MSG_SOURCE_DENIED,
                        "security-checkpoint-unavailable(fail-closed),sourceId=" + ref.getSourceId());
            }
            return TrimOutcome.denied("security-checkpoint-unavailable(fail-closed)");
        }
        try {
            ContextSecurityTrimmer.TrimDecision d = trimmer.trim(request, ref, pack);
            if (d == null) {
                throw new IllegalStateException("security trimmer 返回 null（fail-closed 视同 DENY）");
            }
            if (!d.isPermitted()) {
                return TrimOutcome.denied("security-trim-denied");
            }
            return d.isAllVisible() ? TrimOutcome.allowed(List.of())
                                    : TrimOutcome.allowed(d.getHiddenItems());
        } catch (ContextBuildException e) {
            throw e;
        } catch (RuntimeException e) {
            // 裁决异常 = 安全依赖故障 ⇒ fail-closed 禁放行
            if (ref.isRequired()) {
                throw new ContextBuildException(ContextBuildException.E403_SOURCE_DENIED,
                        ContextBuildException.MSG_SOURCE_DENIED,
                        "security-trim-exception(fail-closed),sourceId=" + ref.getSourceId());
            }
            return TrimOutcome.denied("security-trim-exception(fail-closed)");
        }
    }

    private static final class TrimOutcome {
        final boolean permitted;
        final boolean partialHidden;
        final String reason;
        final List<String> hiddenItems;

        private TrimOutcome(boolean permitted, boolean partialHidden,
                            String reason, List<String> hiddenItems) {
            this.permitted = permitted;
            this.partialHidden = partialHidden;
            this.reason = reason;
            this.hiddenItems = hiddenItems;
        }

        static TrimOutcome allowed(List<String> hiddenItems) {
            return new TrimOutcome(true, !hiddenItems.isEmpty(), null, hiddenItems);
        }

        static TrimOutcome denied(String reason) {
            return new TrimOutcome(false, false, reason, List.of());
        }
    }

    // ── 步骤 4b：源装入 Pack（引用版本统一归一）─────────────────────────────

    private ContextPack initializePack(ContextBuildRequest request) {
        ContextPack pack = new ContextPack();
        pack.setGoal(request.getGoal());
        pack.setScope(request.getScope());
        pack.setScenarioId(request.getScenarioId());
        pack.setBlueprintRef(request.getBlueprintRef());
        pack.setAsOfTime(request.getScope() != null ? request.getScope().getAsOfTime() : null);
        pack.setCreatedAt(Instant.now().toString());
        return pack;
    }

    private void ingestSource(ContextBuildRequest request, ContextPack pack,
                              ContextSourceRef ref, ContextSourceReader.SourceReadResult r) {
        ContextPack.SourceEntry entry = new ContextPack.SourceEntry();
        entry.setSourceType(ref.getSourceType());
        entry.setSourceId(ref.getSourceId());
        entry.setVersion(r.getVersion());
        entry.setAuthority(r.isAuthoritative() ? "AUTHORITATIVE" : "GOVERNED");
        entry.setFreshness(normalizeFreshness(r.getFreshness()));
        entry.setAccessResult("OK");
        entry.setAsOfTime(r.getAsOfTime());
        entry.setEvidenceRefs(new ArrayList<>(ref.getEvidenceRefs()));
        pack.getSources().add(entry);

        for (ContextSourceReader.FactProjection f : r.getFacts()) {
            if (isBlank(f.getSourceId()) || isBlank(f.getVersion())) {
                // SUP-CTX-01：事实行缺来源/版本 ⇒ 拒绝进入 Pack
                throw new ContextBuildException(ContextBuildException.E400_SOURCE_REF_MISSING,
                        ContextBuildException.MSG_SOURCE_REF_MISSING,
                        "fact[" + f.getFieldKey() + "].(sourceId|version)");
            }
            ContextPack.FactEntry fe = new ContextPack.FactEntry();
            fe.setFactId("fact-" + ref.getSourceId() + "-" + f.getFieldKey());
            fe.setFieldKey(f.getFieldKey());
            fe.setValue(f.getValue());
            fe.setSourceId(f.getSourceId());
            fe.setVersion(f.getVersion());
            fe.setAsOfTime(f.getAsOfTime() != null ? f.getAsOfTime() : r.getAsOfTime());
            pack.getFacts().add(fe);
        }
        for (ContextSourceReader.AssetProjection a : r.getAssetRefs()) {
            ContextPack.AssetRefEntry ae = new ContextPack.AssetRefEntry();
            ae.setRefId(a.getRefId());
            ae.setRefType(ref.getSourceType());
            ae.setVersion(a.getVersion());
            ae.setStatus(a.getStatus());
            ae.setAsOfTime(a.getAsOfTime() != null ? a.getAsOfTime() : r.getAsOfTime());
            bucketAssetRef(pack, ref.getSourceType(), ae);
        }
        for (ContextSourceReader.CapabilityProjection c : r.getCapabilities()) {
            ContextPack.CapabilityRefEntry ce = new ContextPack.CapabilityRefEntry();
            ce.setCapabilityId(c.getCapabilityId());
            ce.setContractVersion(c.getContractVersion());
            ce.setModelRef(c.getModelRef());
            ce.setModelVersion(c.getModelVersion());
            ce.setRiskLevel(c.getRiskLevel());
            ce.setRequired(request.getRequiredCapabilities() != null
                    && request.getRequiredCapabilities().contains(c.getCapabilityId()));
            ce.setAvailable(true);
            ce.setAccessResult("OK");
            pack.getCapabilityRefs().add(ce);
        }
    }

    private static void bucketAssetRef(ContextPack pack, String sourceType,
                                       ContextPack.AssetRefEntry ae) {
        List<ContextPack.AssetRefEntry> bucket = switch (sourceType == null ? "" : sourceType) {
            case "KNOWLEDGE" -> pack.getKnowledgeRefs();
            case "ONTOLOGY" -> pack.getOntologyRefs();
            case "COGNITIVE", "COGNITIVE_ASSET" -> pack.getCognitiveRefs();
            default -> pack.getKnowledgeRefs();
        };
        bucket.add(ae);
    }

    private void invalidateSource(ContextPack pack, ContextSourceRef ref, String reason) {
        ContextPack.SourceEntry entry = new ContextPack.SourceEntry();
        entry.setSourceType(ref.getSourceType());
        entry.setSourceId(ref.getSourceId());
        entry.setAccessResult("DENIED");
        entry.setFreshness("UNKNOWN");
        entry.setEvidenceRefs(new ArrayList<>());
        pack.getSources().add(entry);
        pack.getWarnings().add("source " + ref.getSourceId() + " excluded: " + reason);
        if (ref.isSuccessCritical()) {
            pack.setReady(false);
        }
    }

    private void markPermissionGap(ContextPack pack, ContextSourceRef ref, List<String> hiddenItems) {
        pack.getWarnings().add("source " + ref.getSourceId() + " partial-masked by security trim");
        Gap gap = newGap(Gap.CATEGORY_PERMISSION_GAP, Gap.SEVERITY_MEDIUM,
                ref.getSourceId(), "权限/密级裁剪：部分行或列对当前主体不可见（security-engine 裁决）",
                List.of("联系安全管理员确认密级/行列权限", "如需全量请走审批"));
        if (ref.isSuccessCritical()) {
            gap.setSeverity(Gap.SEVERITY_HIGH);
        }
        pack.getGaps().add(gap);
    }

    private static String normalizeFreshness(String f) {
        if (f == null || f.isBlank()) return "UNKNOWN";
        String v = f.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (v) {
            case "CURRENT", "STALE" -> v;
            default -> "UNKNOWN";
        };
    }

    // ── 步骤 5：统一引用和版本（失配 ⇒ 409）──────────────────────────────────

    private void enforceVersionConsistency(ContextBuildRequest request,
                                           List<ContextSourceRef> required,
                                           Map<String, ContextSourceReader.SourceReadResult> readResults,
                                           ContextPack pack) {
        for (ContextSourceRef ref : required) {
            ContextSourceReader.SourceReadResult r = readResults.get(key(ref));
            if (r == null) continue;
            // 5a 期望版本比对（ref.version 已在校验期强制非空；读回不等 = 版本失配，禁旧版本执行）
            if (!ref.getVersion().equalsIgnoreCase(r.getVersion())) {
                throw new ContextBuildException(ContextBuildException.E409_VERSION_STALE,
                        ContextBuildException.MSG_VERSION_STALE,
                        "sourceId=" + ref.getSourceId() + " expected=" + ref.getVersion()
                                + " actual=" + r.getVersion());
            }
            // 5b 期望时点比对（读回 asOfTime 早于期望 = 数据快照过期）
            if (!isBlank(request.getExpectedAsOfTime()) && !isBlank(r.getAsOfTime())) {
                Instant expected = parseInstant(request.getExpectedAsOfTime());
                Instant actual = parseInstant(r.getAsOfTime());
                if (actual.isBefore(expected)) {
                    throw new ContextBuildException(ContextBuildException.E409_VERSION_STALE,
                            ContextBuildException.MSG_VERSION_STALE,
                            "sourceId=" + ref.getSourceId() + " asOfTime=" + r.getAsOfTime()
                                    + " < expectedAsOfTime=" + request.getExpectedAsOfTime());
                }
            }
            // 5c 新鲜度提示：STALE 源进 warnings（§5.3 warnings "过期、部分覆盖..."），不阻断
            if ("STALE".equals(normalizeFreshness(r.getFreshness()))) {
                pack.getWarnings().add("source " + ref.getSourceId() + " freshness=STALE");
            }
        }
    }

    // ── 步骤 6：冲突检测（禁 LLM 自行裁决为事实）────────────────────────────

    private void detectConflicts(List<ContextSourceRef> required, ContextPack pack) {
        Map<String, List<ContextPack.FactEntry>> byField = new LinkedHashMap<>();
        for (ContextPack.FactEntry f : pack.getFacts()) {
            byField.computeIfAbsent(f.getFieldKey(), k -> new ArrayList<>()).add(f);
        }
        for (Map.Entry<String, List<ContextPack.FactEntry>> e : byField.entrySet()) {
            List<ContextPack.FactEntry> values = e.getValue();
            if (values.size() < 2) continue;
            boolean sameValue = values.subList(1, values.size()).stream()
                    .allMatch(v -> v.getValue() == null
                            ? values.get(0).getValue() == null
                            : v.getValue().equals(values.get(0).getValue()));
            if (sameValue) continue;

            // 权威优先级（§5.4-6）：仅当存在明确权威来源且权威方取值唯一
            // （其余竞争值为非权威方）⇒ RESOLVED；权威方自身多值/双方皆权威 ⇒ UNRESOLVED。
            // "猜一个"不在本服务任何路径出现 —— 只能由已声明的权威等级确定性裁决。
            boolean resolved = false;
            ContextPack.FactEntry authoritativeValue = null;
            for (ContextPack.FactEntry f : values) {
                ContextPack.SourceEntry owner = pack.getSources().stream()
                        .filter(s -> s.getSourceId().equals(f.getSourceId()))
                        .findFirst().orElse(null);
                if (owner == null || !"AUTHORITATIVE".equals(owner.getAuthority())) continue;
                if (authoritativeValue != null && !f.getValue().equals(authoritativeValue.getValue())) {
                    resolved = false; // 权威方自相矛盾 ⇒ 无法定版
                    break;
                }
                authoritativeValue = f;
            }
            if (authoritativeValue != null) {
                long authoritativeCount = values.stream()
                        .filter(f -> {
                            ContextPack.SourceEntry owner = pack.getSources().stream()
                                    .filter(s -> s.getSourceId().equals(f.getSourceId()))
                                    .findFirst().orElse(null);
                            return owner != null && "AUTHORITATIVE".equals(owner.getAuthority());
                        }).count();
                resolved = authoritativeCount <= 1;
            }

            ContextPack.ConflictEntry conflict = new ContextPack.ConflictEntry();
            conflict.setConflictId("conflict-" + e.getKey());
            conflict.setFieldKey(e.getKey());
            for (ContextPack.FactEntry f : values) {
                ContextPack.ContendingValue cv = new ContextPack.ContendingValue();
                cv.setSourceId(f.getSourceId());
                cv.setVersion(f.getVersion());
                cv.setValue(f.getValue());
                ContextPack.SourceEntry owner = pack.getSources().stream()
                        .filter(s -> s.getSourceId().equals(f.getSourceId()))
                        .findFirst().orElse(null);
                cv.setAuthority(owner == null ? null : owner.getAuthority());
                cv.setAsOfTime(f.getAsOfTime());
                conflict.getValues().add(cv);
            }
            conflict.setResolution(resolved
                    ? ContextPack.RESOLUTION_RESOLVED : ContextPack.RESOLUTION_UNRESOLVED);
            if (resolved) {
                conflict.setRequiredAction("按权威来源版本采用已定值（保留全部候选值供审计）");
            } else {
                conflict.setRequiredAction("人工澄清或按已批准权威策略解决（禁 LLM 自行裁决为事实）");
                boolean involvesCritical = values.stream().anyMatch(f ->
                        isSourceSuccessCritical(required, f.getSourceId()));
                if (involvesCritical) {
                    throw new ContextBuildException(ContextBuildException.E422_CONFLICT_UNRESOLVED,
                            ContextBuildException.MSG_CONFLICT_UNRESOLVED,
                            "fieldKey=" + e.getKey());
                }
            }
            pack.getConflicts().add(conflict);
        }
    }

    /** 权威单点判定：所有竞争值都来自同一权威来源（不同版本）⇒ 按最新时点定版。 */
    private static boolean isSourceSuccessCritical(List<ContextSourceRef> required, String sourceId) {
        return required.stream().anyMatch(r -> r.isSuccessCritical()
                && r.getSourceId().equals(sourceId));
    }

    /** 可选源不可用留痕（读失败/未接线）：UNAVAILABLE 源条目 + warning，禁静默丢失（§5.5）。 */
    private void markUnavailableOptional(ContextPack pack, ContextSourceRef ref) {
        ContextPack.SourceEntry entry = new ContextPack.SourceEntry();
        entry.setSourceType(ref.getSourceType());
        entry.setSourceId(ref.getSourceId());
        entry.setAccessResult("UNAVAILABLE");
        entry.setFreshness("UNKNOWN");
        entry.setEvidenceRefs(new ArrayList<>());
        pack.getSources().add(entry);
        pack.getWarnings().add("optional source " + ref.getSourceId() + " unavailable (degraded)");
        if (ref.isSuccessCritical()) {
            pack.setReady(false);
            Gap gap = newGap(Gap.CATEGORY_DEPENDENCY_MISSING, Gap.SEVERITY_HIGH,
                    ref.getSourceId(),
                    "成功条件相关可选源不可用（未接线或读失败），Pack 标记 ready=false",
                    List.of("恢复依赖后重建 Context Pack"));
            pack.getGaps().add(gap);
        }
    }

    // ── 步骤 7：缺口识别（§6.5 结构；OPEN 态）────────────────────────────────

    private void generateGaps(ContextBuildRequest request,
                              List<ContextSourceRef> required,
                              ContextPack pack) {
        // 必需能力缺口：requiredCapabilities 未在 capabilityRefs 中可得
        if (request.getRequiredCapabilities() != null) {
            for (String cap : request.getRequiredCapabilities()) {
                boolean available = pack.getCapabilityRefs().stream()
                        .anyMatch(c -> cap.equals(c.getCapabilityId()) && c.isAvailable());
                if (!available) {
                    Gap gap = newGap(Gap.CATEGORY_CAPABILITY_GAP,
                            refForCapability(required) ? Gap.SEVERITY_HIGH : Gap.SEVERITY_MEDIUM,
                            "capability:" + cap,
                            "必需能力未装配到 Pack（能力目录不可用或未声明该能力源）",
                            List.of("核对 CAPABILITY 权威源清单声明", "确认能力目录引擎可用性"));
                    if (request.getGoal() != null
                            && request.getGoal().getSuccessCriteria() != null
                            && request.getGoal().getSuccessCriteria().stream().anyMatch(cap::equals)) {
                        gap.setSeverity(Gap.SEVERITY_BLOCKER);
                    }
                    pack.getGaps().add(gap);
                }
            }
        }
        // 成功条件未满足：关键源被降级（读失败/裁剪拒绝）
        if (!pack.isReady()) {
            Gap gap = newGap(Gap.CATEGORY_DEPENDENCY_MISSING, Gap.SEVERITY_HIGH,
                    "pack.ready=false",
                    "成功条件相关来源不可用（可选源读失败或被权限裁剪拒绝），Pack 标记 ready=false",
                    List.of("补齐数据源/权限后重建 Context Pack"));
            pack.getGaps().add(gap);
        }
    }

    private boolean refForCapability(List<ContextSourceRef> required) {
        return required.stream().anyMatch(r -> "CAPABILITY".equals(r.getSourceType()));
    }

    private Gap newGap(String category, String severity, String targetRef,
                       String description, List<String> suggestedActions) {
        Gap gap = new Gap();
        gap.setGapId("gap-" + java.util.UUID.randomUUID());
        gap.setCategory(category);
        gap.setSeverity(severity);
        gap.setTargetRef(targetRef);
        gap.setDescription(description);
        gap.setSuggestedActions(new ArrayList<>(suggestedActions));
        gap.setStatus(Gap.STATUS_OPEN);
        return gap;
    }

    // ── 步骤 8：预算裁剪（超限 ⇒ 429 显式失败）───────────────────────────────

    private void enforceBudget(ContextBuildRequest request, ContextPack pack) {
        ContextBuildRequest.Budget b = request.getBudget();
        if (b == null) return;
        long entries = pack.getSources().size() + pack.getFacts().size()
                + pack.getKnowledgeRefs().size() + pack.getOntologyRefs().size()
                + pack.getCognitiveRefs().size() + pack.getCapabilityRefs().size()
                + pack.getCalculationRefs().size() + pack.getConflicts().size()
                + pack.getGaps().size() + pack.getWarnings().size();
        if (b.getMaxSources() != null && pack.getSources().size() > b.getMaxSources()) {
            throw new ContextBuildException(ContextBuildException.E429_BUDGET_EXCEEDED,
                    ContextBuildException.MSG_BUDGET_EXCEEDED,
                    "maxSources=" + b.getMaxSources() + " actual=" + pack.getSources().size());
        }
        if (b.getMaxTokens() > 0
                && entries * TOKEN_WEIGHT_PER_ENTRY > b.getMaxTokens()) {
            throw new ContextBuildException(ContextBuildException.E429_BUDGET_EXCEEDED,
                    ContextBuildException.MSG_BUDGET_EXCEEDED,
                    "maxTokens=" + b.getMaxTokens() + " estimated=" + (entries * TOKEN_WEIGHT_PER_ENTRY));
        }
    }

    // ── 步骤 10：过期与刷新（TTL）────────────────────────────────────────────

    private void applyTtl(ContextBuildRequest request, ContextPack pack) {
        long ttl = request.getTtlMillis() != null && request.getTtlMillis() > 0
                ? request.getTtlMillis() : DEFAULT_TTL_MILLIS;
        Instant created = Instant.parse(pack.getCreatedAt());
        pack.setExpiresAt(created.plusMillis(ttl).toString());
    }

    // ── 装配结果 ─────────────────────────────────────────────────────────────

    private ContextBuildResult toResult(ContextBuildRequest request, ContextPack pack, String traceId) {
        ContextBuildResult result = new ContextBuildResult();
        result.setContextDigest(pack.getContextDigest());
        result.setTtlMillis(request.getTtlMillis() != null && request.getTtlMillis() > 0
                ? request.getTtlMillis() : DEFAULT_TTL_MILLIS);
        result.setScenarioId(request.getScenarioId());
        result.setBlueprintRef(request.getBlueprintRef());
        result.setScopeRef(request.getScope());
        result.setRequestId(request.getRequestId());
        result.setCreatedAt(pack.getCreatedAt());
        result.setExpiresAt(pack.getExpiresAt());
        result.setTruncated(pack.isTruncated());
        result.setReady(pack.isReady());
        result.setPack(pack);
        pack.setTraceId(traceId);
        return result;
    }
}
