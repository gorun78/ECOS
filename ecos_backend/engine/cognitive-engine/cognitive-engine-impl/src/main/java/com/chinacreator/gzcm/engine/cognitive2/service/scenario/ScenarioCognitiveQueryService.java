package com.chinacreator.gzcm.engine.cognitive2.service.scenario;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.HypothesisStatus;
import com.chinacreator.gzcm.engine.cognitive.api.mind.MindCapabilityMask;
import com.chinacreator.gzcm.engine.cognitive.api.mind.MindCapabilityMaskCodec;
import com.chinacreator.gzcm.engine.cognitive2.dao.MindAssetDao;
import com.chinacreator.gzcm.engine.cognitive2.dao.ScenarioDao;
import com.chinacreator.gzcm.engine.cognitive2.dao.BeliefScorer;
import com.chinacreator.gzcm.engine.cognitive2.dao.HypothesisReader;
import com.chinacreator.gzcm.engine.cognitive2.dao.BeliefReader;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.CognitiveEvalCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 场景认知只读查询服务（设计-05 F05-03~07 / C.1）— E1~E4 主入口。
 *
 * <p>铁律：只读、无 DB 写、无 LLM 参与数值、失败响应走 {@link CognitiveErrorCode}（不同 → 空列表回落）。
 * 未来 C.1 前置门 = 独立 AOP 或 Controller 层承载（本 batch 简化：以 {@link CognitiveGuardException}
 * 语义上抛，Controller advice 匹配到具体 ECOS-COG-5xx/4xx）。</p>
 */
@Service
public class ScenarioCognitiveQueryService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioCognitiveQueryService.class);

    private final MindAssetDao mindDao;
    private final ScenarioDao scenarioDao;
    private final BeliefScorer beliefScorer;
    private final HypothesisReader hypothesisReader;
    private final BeliefReader beliefReader;
    private final DomainAliasResolver aliasResolver;
    private final CognitiveConfigQueryService configQueryService;

    public ScenarioCognitiveQueryService(MindAssetDao mindDao,
                                         ScenarioDao scenarioDao,
                                         BeliefScorer beliefScorer,
                                         HypothesisReader hypothesisReader,
                                         BeliefReader beliefReader,
                                         DomainAliasResolver aliasResolver,
                                         CognitiveConfigQueryService configQueryService) {
        this.mindDao = mindDao;
        this.scenarioDao = scenarioDao;
        this.beliefScorer = beliefScorer;
        this.hypothesisReader = hypothesisReader;
        this.beliefReader = beliefReader;
        this.aliasResolver = aliasResolver;
        this.configQueryService = configQueryService;
    }

    // ─── 前置门（C.1 判定序列）────────────────────────────────────
    public void assertEnabled() {
        if (!configQueryService.engineEnabled()) throw new CognitiveGuardException(CognitiveErrorCode.E500_ENGINE_DISABLED,
                CognitiveErrorCode.MSG_ENGINE_NOT_ENABLED);
    }

    public void assertScenario(String scenarioId) {
        try {
            scenarioDao.assertScenarioExists(scenarioId);
        } catch (ScenarioDao.ScenarioNotFoundException e) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_SCENARIO, CognitiveErrorCode.MSG_SCENARIO_NOT_FOUND);
        } catch (ScenarioDao.ScenarioDeletedException e) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_SCENARIO, CognitiveErrorCode.MSG_SCENARIO_DELETED);
        }
    }

    public void assertMindBound(String scenarioId, String mindId) {
        if (mindId == null || mindId.isBlank()) return; // E1 无 mind 场景：不 skip 校验
        if (!mindDao.mindExists(mindId)) throw new CognitiveGuardException(CognitiveErrorCode.E400_MIND_NOT_FOUND,
                CognitiveErrorCode.MSG_MIND_NOT_FOUND);
        if (!mindDao.isMindBoundToScenario(scenarioId, mindId))
            throw new CognitiveGuardException(CognitiveErrorCode.E400_MIND_NOT_ON_SCENARIO,
                    CognitiveErrorCode.MSG_MIND_NOT_ON_SCENARIO);
    }

    /** F05-02 schema 预检门禁：E1 查询前 mind 属主表族必须可解析（to_regclass），缺表 → 503 ECOS-COG-501。 */
    public void assertSchemaReady() {
        if (!mindDao.mindTableExists()) throw new CognitiveGuardException(CognitiveErrorCode.E501_SCHEMA_NOT_READY,
                CognitiveErrorCode.MSG_SCHEMA_NOT_READY);
    }

    // ─── E1 detect ──────────────────────────────────────────
    public List<CognitiveContractSchema.MindVO> detect(String scenarioId) {
        assertSchemaReady();
        List<Map<String, Object>> rows = mindDao.detectActiveMindsForScenario(scenarioId);
        // R4（V292）：三轴列是否已落库一次探测 + 批量投影（禁 N+1）；
        // 未迁移环境整列回显 null（诚实「未迁移」态，禁假值）
        boolean r4Ready = mindDao.r4AxisColumnsExist();
        Map<String, Map<String, Object>> axisByMind = r4Ready
                ? mindDao.readAxisViews(rows.stream()
                        .map(r -> (String) r.get("mind_id")).toList())
                : Collections.emptyMap();
        List<CognitiveContractSchema.MindVO> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            out.add(toMindVO(r, axisByMind));
        }
        return out;
    }

    private CognitiveContractSchema.MindVO toMindVO(Map<String, Object> r,
                                                    Map<String, Map<String, Object>> axisByMind) {
        String maskJson = (String) r.get("capability_mask_json");
        List<String> caps = maskJson == null ? Collections.emptyList()
                : MindCapabilityMaskCodec.decode(maskJson).stream().map(MindCapabilityMask::name).toList();
        String boundsJson = (String) r.get("closed_loop_bounds_json");
        List<String> bounds = safeBounds(boundsJson);
        Number isActive = (Number) r.get("is_active");
        String validityStatus = null, reviewStatus = null, adoptionStatus = null,
                changeReason = null, supersededBy = null;
        Map<String, Object> axis = axisByMind.get((String) r.get("mind_id"));
        if (axis != null) {
            validityStatus = (String) axis.get("validity_status");
            reviewStatus   = (String) axis.get("review_status");
            adoptionStatus = (String) axis.get("adoption_status");
            changeReason   = (String) axis.get("change_reason");
            supersededBy   = (String) axis.get("superseded_by");
        }
        return new CognitiveContractSchema.MindVO(
                (String) r.get("mind_id"),
                (String) r.get("mind_name"),
                caps,
                bounds,
                isActive == null ? Boolean.TRUE : isActive.intValue() == 1,
                validityStatus, reviewStatus, adoptionStatus, changeReason, supersededBy);
    }

    private List<String> safeBounds(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return Collections.emptyList();
        List<String> b = new ArrayList<>();
        for (String t : tokensOf(json)) {
            if (com.chinacreator.gzcm.engine.cognitive.api.ClosedLoopBound.fromName(t) != null) b.add(t);
        }
        return b;
    }

    private static List<String> tokensOf(String arr) {
        List<String> out = new ArrayList<>();
        String s = arr.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
        for (String raw : s.split(",")) {
            String t = raw.trim();
            if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) out.add(t.substring(1, t.length() - 1));
        }
        return out;
    }

    // ─── E2 operation-eval ──────────────────────────────────
    public CognitiveContractSchema.OperationEvalData operationEval(String scenarioId, String mindId, Integer limitRaw) {
        String maskJson = mindDao.readCapabilityMask(mindId);
        List<MindCapabilityMask> caps = MindCapabilityMaskCodec.decode(maskJson == null ? null : maskJson);

        double trendWeight = configQueryService.evalTrendWeight();
        List<BeliefScorer.ScoreInput> rows =
                beliefScorer.readScoreInputs(scenarioId, mindId, caps, aliasResolver::resolve);

        List<CognitiveEvalCalculator.Input> inputs = new ArrayList<>();
        Map<String, String> capabilityByMetric = new java.util.LinkedHashMap<>();
        int missingProbability = 0;
        for (BeliefScorer.ScoreInput r : rows) {
            capabilityByMetric.put(r.metricId(), r.capabilityName());
            if (r.probability() == null) {
                missingProbability++;
                continue;
            }
            inputs.add(new CognitiveEvalCalculator.Input(r.metricId(), r.name(), r.probability()));
        }

        CognitiveEvalCalculator.Result top = CognitiveEvalCalculator.topN(inputs, trendWeight, limitRaw);
        List<CognitiveContractSchema.OperationEvalMetric> metrics = new ArrayList<>();
        for (CognitiveEvalCalculator.Scored s : top.items()) {
            metrics.add(new CognitiveContractSchema.OperationEvalMetric(
                    s.metricId(), s.name(), capabilityByMetric.get(s.metricId()), s.score(), s.trend()));
        }
        CognitiveContractSchema.OperationEvalData d = new CognitiveContractSchema.OperationEvalData();
        d.setMindId(mindId);
        d.setLimit(top.limit());
        d.setTruncated(top.truncated());
        d.setTrendWeight(trendWeight);
        d.setTrendWeightSource(configQueryService.evalTrendWeightSource());
        d.setMissingProbability(missingProbability);
        d.setMetrics(metrics);
        return d;
    }

    // ─── E3 hypotheses ──────────────────────────────────────
    public List<CognitiveContractSchema.HypothesisVO> hypotheses(String scenarioId, String mindId) {
        return hypothesisReader.listForScenario(scenarioId, mindId == null || mindId.isBlank() ? null : mindId);
    }

    // ─── E4 beliefs ─────────────────────────────────────────
    public List<CognitiveContractSchema.BeliefVO> beliefs(String scenarioId, String mindId) {
        String maskJson = (mindId == null || mindId.isBlank()) ? null : mindDao.readCapabilityMask(mindId);
        List<MindCapabilityMask> caps = MindCapabilityMaskCodec.decode(maskJson);
        return beliefReader.listForScenario(scenarioId, mindId == null || mindId.isBlank() ? null : mindId, caps,
                aliasResolver::resolve);
    }
}
