package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.exception.ScenarioNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 场景完整度唯一计算点（详细设计-07 F07-02 / C-1 / C-3）。
 *
 * <p>完整度 = {@code |E_present ∩ E_req| / |E_req|}（铁律 §0.6.2，连边覆盖率而非节点占比）。
 * 类内不出现任何"绑定数除法"（F07-02 验收 nodeRatioFormulaIsGone）；空场景 coverage=null 且
 * verdict=EMPTY_SCENARIO（禁 100%），必需边为空 → NOT_APPLICABLE（禁 100%）。</p>
 *
 * <p>孤岛判定（F07-04）：绑定 node 在边图上孤立（无任何入/出边）→ 孤岛，依据<b>边的可达性</b>
 * 而非节点类型（破 X-27 的 resource 节点计数桩）。激活守卫消费 {@link #islands} 判 409。</p>
 */
@Service
public class ScenarioCompletenessService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioCompletenessService.class);

    private final JdbcTemplate jdbc;
    private final RequiredEdgePolicy requiredEdgePolicy;
    private final EdgeContractProbe edgeContractProbe;

    public ScenarioCompletenessService(JdbcTemplate jdbc,
                                       RequiredEdgePolicy requiredEdgePolicy,
                                       EdgeContractProbe edgeContractProbe) {
        this.jdbc = jdbc;
        this.requiredEdgePolicy = requiredEdgePolicy;
        this.edgeContractProbe = edgeContractProbe;
    }

    /** 完整度端点 N1：查库 + 计算。 */
    public CompletenessVO computeFor(String scenarioId) {
        if (!exists(scenarioId)) {
            throw ScenarioNotFoundException.ofId(scenarioId);
        }
        List<EdgeContractProbe.BindingRef> bindings = loadBindings(scenarioId);
        List<EdgeContractProbe.LinkRef> links = loadLinks(scenarioId);
        return new CompletenessVO(scenarioId, compute(bindings, links));
    }

    /** 孤岛清单（F07-04 激活守卫消费；只取 islands）。 */
    public List<CompletenessVO.Island> islandsOf(String scenarioId) {
        List<EdgeContractProbe.BindingRef> bindings = loadBindings(scenarioId);
        List<EdgeContractProbe.LinkRef> links = loadLinks(scenarioId);
        return compute(bindings, links).islands();
    }

    /**
     * 纯计算核心（唯一 coverage 源，可单测）。
     *
     * @param bindings 场景绑定节点
     * @param links    场景连边
     * @return 完整度结果（coverage/verdict/required/present/missing/islands/probeErrors）
     */
    CompletenessVO.Result compute(List<EdgeContractProbe.BindingRef> bindings,
                                  List<EdgeContractProbe.LinkRef> links) {
        List<CompletenessVO.RequiredEdgeVO> required = new ArrayList<>();
        for (RequiredEdgePolicy.RequiredEdge e : requiredEdgePolicy.requiredEdges()) {
            required.add(new CompletenessVO.RequiredEdgeVO(e.edgeType()));
        }

        // 空场景：无任何绑定 → EMPTY_SCENARIO，coverage=null
        if (bindings.isEmpty()) {
            return CompletenessVO.Result.emptyScenario(required, List.of());
        }
        // 必需边为空（理论上固定分母恒 3，dynamic 口径下可能空）→ NOT_APPLICABLE
        if (required.isEmpty()) {
            return CompletenessVO.Result.notApplicable(List.of(), List.of(), islands(bindings, links));
        }

        Set<String> presentTypes = new LinkedHashSet<>();
        List<CompletenessVO.PresentEdgeVO> presentVOs = new ArrayList<>();
        List<CompletenessVO.MissingEdgeVO> missingVOs = new ArrayList<>();
        List<String> probeErrors = new ArrayList<>();

        for (RequiredEdgePolicy.RequiredEdge e : requiredEdgePolicy.requiredEdges()) {
            EdgeContractProbe.EdgePresence p;
            try {
                p = edgeContractProbe.probe(e, bindings, links);
            } catch (RuntimeException ex) {
                p = EdgeContractProbe.EdgePresence.missing("probe-failed:" + e.edgeType()); // 降级 missing
            }
            if (p.present()) {
                presentTypes.add(e.edgeType());
                presentVOs.add(new CompletenessVO.PresentEdgeVO(
                        e.edgeType(), null, null, p.contractCount(), p.lastVerifiedAt()));
            } else if (p.probeError() != null) {
                probeErrors.add(e.edgeType() + ":" + p.probeError());
                missingVOs.add(new CompletenessVO.MissingEdgeVO(e.edgeType(), e.labelKey(), p.probeError()));
            } else {
                missingVOs.add(new CompletenessVO.MissingEdgeVO(e.edgeType(), e.labelKey(), "edge-absent"));
            }
        }

        List<CompletenessVO.Island> islands = islands(bindings, links);
        double coverage = round3((double) presentTypes.size() / required.size());
        String verdict = presentTypes.size() == required.size() ? "OK"
                : presentTypes.isEmpty() ? "ISOLATED" : "PARTIAL";
        return new CompletenessVO.Result(coverage, verdict, required, presentVOs, missingVOs, islands, probeErrors);
    }

    /** 孤岛（边缘 graph）：无入/出边的绑定（F07-04，依据可达性非类型）。 */
    List<CompletenessVO.Island> islands(List<EdgeContractProbe.BindingRef> bindings,
                                        List<EdgeContractProbe.LinkRef> links) {
        Set<String> touched = new LinkedHashSet<>();
        for (EdgeContractProbe.LinkRef l : links) {
            touched.add(l.sourceBindingId());
            touched.add(l.targetBindingId());
        }
        List<CompletenessVO.Island> out = new ArrayList<>();
        for (EdgeContractProbe.BindingRef b : bindings) {
            if (!touched.contains(b.id())) {
                out.add(new CompletenessVO.Island(b.id(), b.type(), b.targetRef(), "no-incident-edge"));
            }
        }
        return out;
    }

    private boolean exists(String scenarioId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(1) FROM ecos_business_scenario WHERE id = ? AND is_deleted = 0",
                Integer.class, scenarioId);
        return n != null && n > 0;
    }

    private List<EdgeContractProbe.BindingRef> loadBindings(String scenarioId) {
        return jdbc.query(
                "SELECT id, binding_type, COALESCE(target_ref,'') AS target_ref " +
                "FROM ecos_scenario_binding WHERE scenario_id = ? AND is_deleted = 0 ORDER BY id",
                (rs, rn) -> new EdgeContractProbe.BindingRef(
                        rs.getString("id"),
                        rs.getString("binding_type") == null ? "" : rs.getString("binding_type").toUpperCase(),
                        rs.getString("target_ref")),
                scenarioId);
    }

    private List<EdgeContractProbe.LinkRef> loadLinks(String scenarioId) {
        return jdbc.query(
                "SELECT source_binding_id, target_binding_id, link_type, source_contract " +
                "FROM ecos_scenario_binding_link WHERE scenario_id = ? AND is_deleted = 0 ORDER BY id",
                (rs, rn) -> new EdgeContractProbe.LinkRef(
                        rs.getString("source_binding_id"), rs.getString("target_binding_id"),
                        rs.getString("link_type"), rs.getString("source_contract")),
                scenarioId);
    }

    private static double round3(double d) {
        return Math.round(d * 1000.0) / 1000.0;
    }
}
