package com.chinacreator.gzcm.workspace.scenario;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 完整度唯一计算点（卷07 F07-02，铁律 §0.6.2 / REQ-PLT-13）。
 * <p>{@code coverage = |E_present ∩ E_req| / |E_req|}，后端单源，前端只消费不重算。
 * 本类<b>不得出现任何节点除法</b>（X-23 的 totalObjects 占比算法在此被彻底取代）。</p>
 *
 * <p>短 TTL 只读缓存（单点 Caffeine，禁各自 {@code new ConcurrentHashMap}）。</p>
 */
@Service
public class ScenarioCompletenessService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final RequiredEdgePolicy edgePolicy;
    private final EdgeContractProbe probe;
    private final IslandBindingGuard islandGuard;
    private final Cache<String, CompletenessReport> cache;

    public ScenarioCompletenessService(RequiredEdgePolicy edgePolicy,
                                      EdgeContractProbe probe,
                                      IslandBindingGuard islandGuard) {
        this.edgePolicy = edgePolicy;
        this.probe = probe;
        this.islandGuard = islandGuard;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL)
                .maximumSize(512)
                .build();
    }

    /** 纯计算入口（无 IO），供 Service/Controller/守卫复用同一份算法。 */
    public CompletenessReport compute(String scenarioId, List<BindingNode> bindings, List<LinkEdge> links) {
        List<BindingNode> bs = bindings == null ? List.of() : bindings;
        List<LinkEdge> ls = links == null ? List.of() : links;

        Set<BindingType> boundTypes = EnumSet.noneOf(BindingType.class);
        for (BindingNode b : bs) {
            if (b.getType() != null) {
                boundTypes.add(b.getType());
            }
        }

        List<RequiredEdge> requiredEdges = edgePolicy.derive(boundTypes);
        List<CompletenessReport.IslandView> islands = islandGuard.findIslands(bs, ls);

        // 空场景：既无绑定也无岛可言 → coverage=null + EMPTY_SCENARIO（禁 1.0）
        if (bs.isEmpty()) {
            CompletenessReport r = new CompletenessReport(scenarioId, null, CompletenessReport.Verdict.EMPTY_SCENARIO);
            for (RequiredEdge e : requiredEdges) {
                r.getRequiredEdges().add(toRequiredView(e));
            }
            r.getIslands().addAll(islands);
            return r;
        }

        // 分母为 0（动态口径下未绑定任何节点对）→ coverage=null + NOT_APPLICABLE（禁折算 100%）
        if (requiredEdges.isEmpty()) {
            CompletenessReport r = new CompletenessReport(scenarioId, null, CompletenessReport.Verdict.NOT_APPLICABLE);
            r.getIslands().addAll(islands);
            return r;
        }

        int presentCount = 0;
        CompletenessReport report = new CompletenessReport(scenarioId, null, CompletenessReport.Verdict.PARTIAL);
        for (RequiredEdge e : requiredEdges) {
            report.getRequiredEdges().add(toRequiredView(e));
            boolean sourceBound = boundTypes.contains(e.getSourceType());
            boolean targetBound = boundTypes.contains(e.getTargetType());
            String[] linkIds = findLinkEndpoints(e, bs, ls);
            boolean linkExists = linkIds != null;
            ProbeResult pr = probe.probe(e, sourceBound, targetBound, linkExists);
            if (pr.isPresent()) {
                presentCount++;
                report.getPresentEdges().add(new CompletenessReport.PresentEdgeView(
                        e.getType().name(), linkIds[0], linkIds[1], pr.getContractCount(), pr.getLastVerifiedAt()));
            } else {
                report.getMissingEdges().add(new CompletenessReport.MissingEdgeView(
                        e.getType().name(), e.labelKey(), missingReason(e, sourceBound, targetBound, linkExists)));
            }
            if (pr.getProbeError() != null) {
                report.getProbeErrors().add(e.getType() + ": " + pr.getProbeError());
            }
        }

        double coverage = (double) presentCount / (double) requiredEdges.size();
        CompletenessReport.Verdict verdict = presentCount == requiredEdges.size()
                ? CompletenessReport.Verdict.OK : CompletenessReport.Verdict.PARTIAL;

        CompletenessReport finalReport = new CompletenessReport(scenarioId, coverage, verdict);
        finalReport.getRequiredEdges().addAll(report.getRequiredEdges());
        finalReport.getPresentEdges().addAll(report.getPresentEdges());
        finalReport.getMissingEdges().addAll(report.getMissingEdges());
        finalReport.getIslands().addAll(islands);
        finalReport.getProbeErrors().addAll(report.getProbeErrors());
        return finalReport;
    }

    /** 带短 TTL 缓存的读入口（写路径与激活校验绕过缓存调用 {@link #compute}）。 */
    public CompletenessReport cached(String scenarioId, List<BindingNode> bindings, List<LinkEdge> links) {
        CompletenessReport hit = cache.getIfPresent(scenarioId);
        if (hit != null) {
            return hit;
        }
        CompletenessReport r = compute(scenarioId, bindings, links);
        cache.put(scenarioId, r);
        return r;
    }

    public void invalidate(String scenarioId) {
        cache.invalidate(scenarioId);
    }

    // ── helpers ──────────────────────────────────────

    private CompletenessReport.RequiredEdgeView toRequiredView(RequiredEdge e) {
        return new CompletenessReport.RequiredEdgeView(e.getType().name(), e.getSourceType().name(), e.getTargetType().name());
    }

    /** 找到一条把 source 类型实例连到 target 类型实例的对应必需边连边；命中返回 [sourceId,targetId]，否则 null。 */
    private String[] findLinkEndpoints(RequiredEdge e, List<BindingNode> bs, List<LinkEdge> ls) {
        List<String> sources = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        for (BindingNode b : bs) {
            if (b.getType() == e.getSourceType()) {
                sources.add(b.getBindingId());
            } else if (b.getType() == e.getTargetType()) {
                targets.add(b.getBindingId());
            }
        }
        Set<String> sset = new HashSet(sources);
        Set<String> tset = new HashSet(targets);
        for (LinkEdge l : ls) {
            if (l.getType() != e.getType()) {
                continue;
            }
            if (sset.contains(l.getSourceBindingId()) && tset.contains(l.getTargetBindingId())) {
                return new String[]{l.getSourceBindingId(), l.getTargetBindingId()};
            }
        }
        return null;
    }

    private String missingReason(RequiredEdge e, boolean sourceBound, boolean targetBound, boolean linkExists) {
        if (!sourceBound) {
            return e.getSourceType() + " 未绑定";
        }
        if (!targetBound) {
            return e.getTargetType() + " 未绑定";
        }
        if (!linkExists) {
            return "缺少 " + e.getType() + " 连边";
        }
        return e.getTargetType() + " 未引用任何 " + e.getSourceType() + "（契约未通过）";
    }
}
