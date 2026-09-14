package com.chinacreator.gzcm.engine.cognitive2.service.mental;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive2.dto.counterfactual.CounterfactualRequest;
import com.chinacreator.gzcm.engine.cognitive2.dto.counterfactual.CounterfactualResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 时间回放业务服务（PMO-59 P3b T1 / ADR-9 时间回放用户确认决策 ③ 落地点）。
 *
 * <p>语义：按指定 belief 历史版本（V129 version 链唯一索引）+ 关联假设"当时有效"状态
 * 重算推演，输出"当时同参数结论"——<b>回放只读重算，0 行更新</b>（禁止改历史数据）。
 * 计算核心复用 {@link CounterfactualSimulator#replay}（与实时推演同一蒙特卡洛内核，
 * 只换分布行与假设时点口径，不复制逻辑）。</p>
 *
 * <p>GET 端点携带 JSON 型 query 参数（interventions/baseline 为可选 JSON 串，
 * 同参数=同结论可复现；brief JSON 反序列化失败 400 带字段定位）。</p>
 */
@Service
public class BeliefReplayService {

    private static final Logger log = LoggerFactory.getLogger(BeliefReplayService.class);

    private static final TypeReference<List<CounterfactualRequest.Intervention>> INTERVENTIONS_TYPE =
        new TypeReference<>() {
        };
    private static final TypeReference<Map<String, Map<String, Double>>> OUTCOME_VALUES_TYPE =
        new TypeReference<>() {
        };

    private final CounterfactualSimulator simulator;
    private final com.chinacreator.gzcm.engine.cognitive2.service.BeliefStore beliefStore;
    private final ObjectMapper objectMapper;

    public BeliefReplayService(CounterfactualSimulator simulator,
                               com.chinacreator.gzcm.engine.cognitive2.service.BeliefStore beliefStore,
                               ObjectMapper objectMapper) {
        this.simulator = simulator;
        this.beliefStore = beliefStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 时间回放。
     *
     * @param variableName 变量名（必填）
     * @param version      回放版本号（必填）
     * @param domain       业务域（必填）
     * @param interventionsJson interventions JSON 串（可选，CounterfactualRequest.Intervention 数组）
     * @param outcomeValuesJson outcome 数值映射 JSON 串（可选，{variable:{outcome:value}}）
     * @param weightsJson     线性响应权重 JSON 串（可选，{variable:weight}）
     * @param sampleCount    采样次数（可选，缺省 1000 上限 5000）
     * @param seed           随机种子（可选，缺省 42；同参同 seed 同结论）
     * @return 四指标 + replayMeta（replayedVersion/believedDistribution/versionUpdatedAt/currentVersion）
     */
    public CounterfactualResult replay(String variableName, int version, String domain,
                                       String interventionsJson, String outcomeValuesJson,
                                       String weightsJson, Integer sampleCount, Long seed) {
        if (variableName == null || variableName.isBlank()) {
            throw new BusinessException(400, "COG-400: variableName 必填");
        }
        if (domain == null || domain.isBlank()) {
            throw new BusinessException(400, "COG-400: domain 必填");
        }
        int currentVersion = beliefStore.maxVersion(variableName.trim(), domain.trim());
        if (currentVersion == 0) {
            throw new BusinessException(404,
                "COG-404: 不确定性判断不存在: variable=" + variableName + ", domain=" + domain);
        }

        CounterfactualRequest req = new CounterfactualRequest();
        req.setVariableName(variableName.trim());
        req.setDomain(domain.trim());
        req.setSampleCount(sampleCount);
        req.setSeed(seed);

        List<CounterfactualRequest.Intervention> interventions = parseList(interventionsJson, "interventions");
        if (!interventions.isEmpty()) {
            req.setInterventions(interventions);
        }
        if (outcomeValuesJson != null && !outcomeValuesJson.isBlank()
            || (weightsJson != null && !weightsJson.isBlank())) {
            CounterfactualRequest.Baseline baseline = new CounterfactualRequest.Baseline();
            if (outcomeValuesJson != null && !outcomeValuesJson.isBlank()) {
                baseline.setOutcomeValues(parseMap(outcomeValuesJson, "outcomeValues"));
            }
            if (weightsJson != null && !weightsJson.isBlank()) {
                baseline.setWeights(parseWeights(weightsJson));
            }
            req.setBaseline(baseline);
        }

        log.info("时间回放请求: variable={} version={} domain={} N={} seed={}",
                variableName, version, domain, req.resolvedSampleCount(), req.resolvedSeed());
        return simulator.replay(req, variableName.trim(), version, currentVersion);
    }

    /** JSON 串 → Intervention 列表（空串返空列表；解析失败 400 带字段名定位）。 */
    private List<CounterfactualRequest.Intervention> parseList(String json, String field) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, INTERVENTIONS_TYPE);
        } catch (Exception e) {
            throw new BusinessException(400, "COG-400: 参数 " + field + " JSON 解析失败: " + e.getMessage());
        }
    }

    /** JSON 串 → outcome 数值映射 {variable:{outcome:value}}。 */
    private Map<String, Map<String, Double>> parseMap(String json, String field) {
        try {
            return objectMapper.readValue(json, OUTCOME_VALUES_TYPE);
        } catch (Exception e) {
            throw new BusinessException(400, "COG-400: 参数 " + field + " JSON 解析失败: " + e.getMessage());
        }
    }

    /** JSON 串 → 权重 {variable:weight}。 */
    private Map<String, Double> parseWeights(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {
            });
        } catch (Exception e) {
            throw new BusinessException(400, "COG-400: 参数 weights JSON 解析失败: " + e.getMessage());
        }
    }
}
