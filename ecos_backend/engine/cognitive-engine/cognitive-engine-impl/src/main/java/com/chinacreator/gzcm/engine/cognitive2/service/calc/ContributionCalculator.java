package com.chinacreator.gzcm.engine.cognitive2.service.calc;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 因果诊断贡献复算纯函数（详细设计-05 F05-09 / C118 / REQ-COG-02）。
 *
 * <p>铁律：{@code contribution} 由 {@code ForecastRunSnapshotReader} 取只读快照后**确定性复算**，
 * 禁 LLM 生成数值（B-2）。证据/假设分离：仅当边存在 {@code evidenceRefs} 或
 * {@code statSupport}（本批以 {@code hasEvidence} 布尔承载）才入 {@code chains.path}，
 * 否则归 {@code unverifiedHypotheses}（观测项，禁静默）。同快照复算必得同值（可复算单测基准）。</p>
 */
public final class ContributionCalculator {

    /** 一条因果边：基准/情景值 + 是否有证据（evidenceRefs 或 statSupport 非空）。 */
    public record Edge(String id, String fromNode, String toNode,
                       BigDecimal baseline, BigDecimal scenario, boolean hasEvidence) {
        public BigDecimal contribution() {
            return scenario.subtract(baseline).setScale(4, RoundingMode.HALF_UP);
        }
    }

    /** 有证据边产出的一条链（chain path = from→to，contribution 确定性复算）。 */
    public record Chain(String path, BigDecimal contribution) {}

    /** 分区结果：chains（有证据）+ unverifiedHypotheses（无证据边 id）。 */
    public record Partition(List<Chain> chains, List<String> unverifiedHypotheses) {}

    private ContributionCalculator() {}

    /** 对每条边贡献复算（同输入同输出；确定性）。 */
    public static List<BigDecimal> contributions(List<Edge> edges) {
        List<BigDecimal> out = new ArrayList<>(edges.size());
        for (Edge e : edges) out.add(e.contribution());
        return out;
    }

    /**
     * 证据/假设分离：有证据边 → chains.path（{@code fromNode→toNode} + 贡献）；
     * 无证据边 → unverifiedHypotheses（保留边 id，禁丢弃）。
     */
    public static Partition partition(List<Edge> edges) {
        List<Chain> chains = new ArrayList<>();
        List<String> unverified = new ArrayList<>();
        for (Edge e : edges) {
            if (e.hasEvidence()) {
                chains.add(new Chain(e.fromNode() + "→" + e.toNode(), e.contribution()));
            } else {
                unverified.add(e.id());
            }
        }
        return new Partition(chains, unverified);
    }

    // ── D.3 结构化链（F05-09 响应增块 chains[]）───────────────────────────

    /**
     * 快照一条有向边（结构化形态）：{@code path} 为 D.3 的 {@code [{node,type}]}，
     * {@code evidenceRefs}/{@code statSupport} 任一非空即"有证据"（F05-09 要点3）。
     * 同一 chainId 的多条边聚合为一条 {@link CognitiveContractSchema.DiagnoseChainVO}。
     */
    public record SnapshotEdge(String chainId,
                               List<CognitiveContractSchema.ChainNodeVO> path,
                               BigDecimal baseline,
                               BigDecimal scenario,
                               List<String> evidenceRefs,
                               CognitiveContractSchema.StatSupportVO statSupport) {
        public BigDecimal contribution() {
            return scenario.subtract(baseline).setScale(4, RoundingMode.HALF_UP);
        }

        public boolean hasEvidence() {
            return (evidenceRefs != null && !evidenceRefs.isEmpty()) || statSupport != null;
        }
    }

    /** 无证据边的显性观测项文案（禁静默丢弃，F05-09 要点3）。 */
    public static final String UNVERIFIED_REASON = "无反向证据累计（详细设计-05 F05-09 要点3）";

    /**
     * 证据/假设分离的结构化装配（确定性：同一批边必得同一结果）。
     *
     * <p>有证据边进 {@code path} 并计入 {@code contribution}；无证据边归该链的
     * {@code unverifiedHypotheses}。若某链全无证据，则 {@code contribution=null} +
     * {@code recomputable=false}（禁把无证据数值当可复算结果）。</p>
     */
    public static List<CognitiveContractSchema.DiagnoseChainVO> chains(List<SnapshotEdge> edges) {
        Map<String, List<SnapshotEdge>> grouped = new LinkedHashMap<>();
        for (SnapshotEdge e : edges) {
            String key = e.chainId() == null ? "" : e.chainId();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        List<CognitiveContractSchema.DiagnoseChainVO> out = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<SnapshotEdge>> entry : grouped.entrySet()) {
            List<CognitiveContractSchema.ChainNodeVO> path = new ArrayList<>();
            List<String> evidenceRefs = new ArrayList<>();
            List<CognitiveContractSchema.UnverifiedHypothesisVO> unverified = new ArrayList<>();
            CognitiveContractSchema.StatSupportVO statSupport = null;
            BigDecimal sum = BigDecimal.ZERO;
            boolean anyEvidence = false;
            for (SnapshotEdge e : entry.getValue()) {
                if (e.hasEvidence()) {
                    anyEvidence = true;
                    appendPath(path, e.path());
                    if (e.evidenceRefs() != null) {
                        for (String ref : e.evidenceRefs()) {
                            if (!evidenceRefs.contains(ref)) evidenceRefs.add(ref);
                        }
                    }
                    if (statSupport == null) statSupport = e.statSupport();
                    sum = sum.add(e.contribution());
                } else {
                    unverified.add(new CognitiveContractSchema.UnverifiedHypothesisVO(
                            statement(e.path()), UNVERIFIED_REASON));
                }
            }
            CognitiveContractSchema.DiagnoseChainVO vo = new CognitiveContractSchema.DiagnoseChainVO();
            vo.setChainId(entry.getKey());
            vo.setPath(List.copyOf(path));
            vo.setContribution(anyEvidence ? sum.setScale(4, RoundingMode.HALF_UP) : null);
            vo.setEvidenceRefs(List.copyOf(evidenceRefs));
            vo.setStatSupport(statSupport);
            vo.setRecomputable(anyEvidence);
            vo.setUnverifiedHypotheses(List.copyOf(unverified));
            out.add(vo);
        }
        return List.copyOf(out);
    }

    private static void appendPath(List<CognitiveContractSchema.ChainNodeVO> acc,
                                   List<CognitiveContractSchema.ChainNodeVO> add) {
        if (add == null) {
            return;
        }
        for (CognitiveContractSchema.ChainNodeVO node : add) {
            if (node == null) {
                continue;
            }
            if (!acc.isEmpty()) {
                CognitiveContractSchema.ChainNodeVO last = acc.get(acc.size() - 1);
                if (java.util.Objects.equals(last.getNode(), node.getNode())
                        && java.util.Objects.equals(last.getType(), node.getType())) {
                    continue;
                }
            }
            acc.add(node);
        }
    }

    private static String statement(List<CognitiveContractSchema.ChainNodeVO> path) {
        if (path == null || path.isEmpty()) {
            return "(无边节点)";
        }
        StringBuilder sb = new StringBuilder();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (CognitiveContractSchema.ChainNodeVO node : path) {
            seen.add(node.getNode());
        }
        boolean first = true;
        for (String name : seen) {
            if (!first) sb.append('→');
            sb.append(name);
            first = false;
        }
        return sb.toString();
    }
}
