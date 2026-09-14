package com.chinacreator.gzcm.engine.cognitive2.service.mental;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive2.dto.counterfactual.CounterfactualRequest;
import com.chinacreator.gzcm.engine.cognitive2.dto.counterfactual.CounterfactualResult;
import com.chinacreator.gzcm.engine.cognitive2.service.BeliefStore;
import com.chinacreator.gzcm.engine.cognitive2.service.EvidenceStore;
import com.chinacreator.gzcm.engine.cognitive2.service.HypothesisStore;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 反事实推演器（PMO-59 P3a / ADR-9 推演核心）— do(A) 干预 + 蒙特卡洛，纯 Java 数值，LLM 零参与。
 *
 * <p><b>实现选择声明（指令 T2 验收口径）</b>：指标联动走<b>轻量线性响应模型</b>
 * （{@code metric_t = Σ_i w_i · x_{i,t}}），不委托既有 {@code ScenarioSimulatorService}
 * ——后者指标预测委托 LLM Agent completion，违反认知计算 LLM 零参与红线（ADR-5 口径）。</p>
 *
 * <p><b>do(A) 语义</b>：
 * <ul>
 *   <li>输入装配：主变量 + 干预变量的 belief 当前版本分布（只读）；outcome→数值按
 *       {@code baseline.outcomeValues} 映射，缺省按序数 i+1；</li>
 *   <li>SET → 该变量每次采点后强制覆写为 value（单点化）；</li>
 *   <li>DELTA → 该变量每个 outcome 数值 +value（基线平移）；</li>
 *   <li>蒙特卡洛：{@link Random(long)} 种子可传入（缺省 42）；基线组/干预组同一采样流
 *       <b>配对</b>采样（同 t 同 outcome），消随机性方差，lossProbability 按配对逐样本计。</li>
 * </ul>
 *
 * <p><b>风险四指标</b>（干预组相对基线组）：
 * expectedBenefit=mean(interv)−mean(base)；lossProbability=count(interv_t&lt;base_t)/N；
 * maxDrawdown=max(0, median(interv)−min(interv))；volatilityRange=[p05(p95), interv]。</p>
 *
 * <p><b>敏感性 Top3</b>：对每个输入变量单独做 outcome 数值 +5% 扰动（SET 定点同幅缩放），
 * 线性响应下结论（E[metric] 与结论方差）的解析响应：
 * {@code sensitivity = |ΔE| + |ΔVar|}，其中 ΔE=0.05·w·E[y_v]、ΔVar=0.10·w²·Var(y_v)
 * （未扰动变量独立采样 → 协方差项为 0）；降序取 Top3。SET 扰动变量 Var=0，仅均值响应。</p>
 *
 * <p><b>假设有效性过滤</b>：domain 下 VALID 假设进入 {@code assumptionRefs}（推演前提）；
 * INVALIDATED/ARCHIVED 假设计入 {@code excludedAssumptions}（不进入推演前提，作废联动溯源用）。</p>
 */
@Service
public class CounterfactualSimulator {

    private static final Logger log = LoggerFactory.getLogger(CounterfactualSimulator.class);

    /** 干预操作白名单 */
    private static final Set<String> OPERATIONS = Set.of("SET", "DELTA");
    /** 敏感性扰动幅度（outcome 数值相对 +5%） */
    private static final double SENSITIVITY_PERTURB = 0.05d;

    private final BeliefStore beliefStore;
    private final HypothesisStore hypothesisStore;
    private final EvidenceStore evidenceStore;

    /** 审计通道（铁律 §2.4#5 写操作/关键推理留痕发 Kafka {@code ecos.audit}）— 可选装配（required=false + null 兜底） */
    @Autowired(required = false)
    private EventBusService eventBusService;

    public CounterfactualSimulator(BeliefStore beliefStore,
                                   HypothesisStore hypothesisStore,
                                   EvidenceStore evidenceStore) {
        this.beliefStore = beliefStore;
        this.hypothesisStore = hypothesisStore;
        this.evidenceStore = evidenceStore;
    }

    /**
     * 执行反事实推演（同步，只读心智状态，0 写库）。
     *
     * @param request 推演请求（domain/variableName 必填；interventions 可空=纯基线采样）
     * @return 四指标 + 敏感性 Top3 + 假设前提留痕
     */
    public CounterfactualResult simulate(CounterfactualRequest request) {
        if (request == null) {
            throw new BusinessException(400, "COG-400: 请求体必填");
        }
        String domain = require(request.getDomain(), "domain");
        String variableName = require(request.getVariableName(), "variableName");
        int n = request.resolvedSampleCount();
        long seed = request.resolvedSeed();

        // ── 1. 输入装配：主变量 + 干预变量（顺序去重主变量在前） ──
        List<String> variables = new ArrayList<>();
        variables.add(variableName);
        Map<String, CounterfactualRequest.Intervention> interventions = new LinkedHashMap<>();
        if (request.getInterventions() != null) {
            for (CounterfactualRequest.Intervention itv : request.getInterventions()) {
                if (itv == null || itv.getVariableName() == null || itv.getVariableName().isBlank()) {
                    throw new BusinessException(400, "COG-400: interventions[].variableName 必填");
                }
                String op = itv.getOp() == null ? "" : itv.getOp().toUpperCase(Locale.ROOT);
                if (!OPERATIONS.contains(op)) {
                    throw new BusinessException(400, "COG-400: 非法干预 op=" + itv.getOp() + "（允许 SET/DELTA）");
                }
                itv.setOp(op);
                String iv = itv.getVariableName().trim();
                // 同变量多干预 last-wins（LinkedHashMap 保序，后写覆盖）
                interventions.put(iv, itv);
                if (!variables.contains(iv)) {
                    variables.add(iv);
                }
            }
        }

        // 每个输入变量的分布 + outcome→数值映射（基线值）
        List<List<DistinctOutcome>> outcomeSets = new ArrayList<>(variables.size());
        List<Map<String, Double>> baseValueMaps = new ArrayList<>(variables.size());
        for (String v : variables) {
            Map<String, Object> row = beliefStore.findLatest(v, domain);
            if (row == null) {
                throw new BusinessException(404, "COG-404: 不确定性判断不存在: variable=" + v + ", domain=" + domain);
            }
            List<DistinctOutcome> outcomes = toOutcomes(v, row, request);
            outcomeSets.add(outcomes);
            baseValueMaps.add(valuesOf(outcomes));
        }
        double[] weights = weightsOf(variables, request);

        // ── 2. 蒙特卡洛（同 seed 流配对采样：基线/干预同一 t 同一 outcome 序列） ──
        double[] baseMetrics = new double[n];
        double[] intervMetrics = new double[n];
        Random random = new Random(seed);
        for (int t = 0; t < n; t++) {
            double base = 0d;
            double interv = 0d;
            for (int i = 0; i < variables.size(); i++) {
                int pick = sampleOutcome(random, outcomeSets.get(i));
                double baseVal = baseValueMaps.get(i).get(outcomeSets.get(i).get(pick).outcome);
                base += weights[i] * baseVal;
                CounterfactualRequest.Intervention itv = interventions.get(variables.get(i));
                double intervVal;
                if (itv == null) {
                    intervVal = baseVal;
                } else if ("SET".equals(itv.getOp())) {
                    intervVal = itv.getValue();
                } else {
                    intervVal = baseVal + itv.getValue();
                }
                interv += weights[i] * intervVal;
            }
            baseMetrics[t] = base;
            intervMetrics[t] = interv;
        }

        // ── 3. 风险四指标 ──
        CounterfactualResult result = new CounterfactualResult();
        result.setVariableName(variableName);
        result.setSampleCount(n);
        result.setSeed(seed);
        double baseMean = mean(baseMetrics);
        double intervMean = mean(intervMetrics);
        result.setBaselineMean(baseMean);
        result.setIntervenedMean(intervMean);

        CounterfactualResult.RiskMetrics rm = result.getRiskMetrics();
        rm.setExpectedBenefit(intervMean - baseMean);
        int lossCount = 0;
        for (int t = 0; t < n; t++) {
            if (intervMetrics[t] < baseMetrics[t]) {
                lossCount++;
            }
        }
        rm.setLossProbability((double) lossCount / n);
        double[] intervSorted = intervMetrics.clone();
        Arrays.sort(intervSorted);
        rm.setMaxDrawdown(Math.max(0d, percentile(intervSorted, 0.50d) - percentile(intervSorted, 0.00d)));
        rm.setVolatilityRange(new double[] { percentile(intervSorted, 0.05d), percentile(intervSorted, 0.95d) });

        // ── 4. 敏感性 Top3（分析式：+5% 扰动 outcome 数值 → |ΔE| + |ΔVar|） ──
        List<CounterfactualResult.SensitivityItem> items = new ArrayList<>();
        for (int i = 0; i < variables.size(); i++) {
            items.add(sensitivityOf(variables.get(i), outcomeSets.get(i), weights[i], interventions));
        }
        items.sort((a, b) -> Double.compare(b.getSensitivity(), a.getSensitivity()));
        result.getSensitivityTop3().addAll(items.subList(0, Math.min(3, items.size())));

        // ── 5. 假设前提留痕（有效性过滤） ──
        for (Map<String, Object> row : hypothesisStore.list(domain, null)) {
            String status = evidenceStore.fieldString(row, "status");
            String id = evidenceStore.fieldString(row, "id");
            if ("VALID".equals(status)) {
                result.getAssumptionRefs().add(id);
            } else {
                result.getExcludedAssumptions().add(new CounterfactualResult.ExcludedAssumption(
                    id, status, evidenceStore.fieldString(row, "invalid_reason")));
            }
        }

        // ── 6. 回显 + 摘要（纯模板，零 LLM） ──
        CounterfactualResult.RequestEcho echo = result.getRequestEcho();
        echo.setScenarioId(request.getScenarioId());
        echo.setDomain(domain);
        echo.setVariableName(variableName);
        echo.setSampleCount(n);
        echo.setSeed(seed);
        for (Map.Entry<String, CounterfactualRequest.Intervention> e : interventions.entrySet()) {
            echo.getInterventions().put(e.getKey(), e.getValue().getOp() + "=" + e.getValue().getValue());
        }
        result.setScenarioSummary(String.format(Locale.ROOT,
            "domain=%s 对变量 %-40s 施加 %d 项干预，N=%d seed=%d 蒙特卡洛: 期望收益 %+.6g，亏损概率 %.2f%%，波动区间 [%+.4g, %+.4g]",
            domain, variableName, interventions.size(), n, seed,
            rm.getExpectedBenefit(), rm.getLossProbability() * 100d,
            rm.getVolatilityRange()[0], rm.getVolatilityRange()[1]));
        log.info("反事实推演完成: domain={} variable={} interventions={} N={} seed={} benefit={}",
                domain, variableName, interventions.size(), n, seed, rm.getExpectedBenefit());
        audit("cognitive.counterfactual", "ecos_cognitive_counterfactual", "success",
            "domain=" + domain + ", variable=" + variableName
                + ", interventions=" + interventions.size() + ", N=" + n + ", seed=" + seed);
        return result;
    }

    // ── 内部结构 ──

    /** 单 outcome 的取值 + 概率（采样与解析式共用）。 */
    private record DistinctOutcome(String outcome, double prob, double baseValue) {
    }

    /** 行 → outcome 列表（分布概率 + 数值映射：outcomeValues 优先，缺省序数 i+1）。 */
    private List<DistinctOutcome> toOutcomes(String variable, Map<String, Object> row, CounterfactualRequest request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) beliefStore
            .toNormalizedRow(row, evidenceStore).get("distribution");
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(400, "COG-400: 变量分布为空无法推演: variable=" + variable);
        }
        Map<String, Double> mapping = (request.getBaseline() != null && request.getBaseline().getOutcomeValues() != null)
            ? request.getBaseline().getOutcomeValues().getOrDefault(variable, Map.of())
            : Map.of();
        List<DistinctOutcome> outcomes = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String outcome = String.valueOf(raw.get(i).get("outcome"));
            double prob = raw.get(i).get("prob") instanceof Number num ? num.doubleValue() : 0d;
            Double mapped = mapping.get(outcome);
            outcomes.add(new DistinctOutcome(outcome, prob, mapped != null ? mapped : (i + 1d)));
        }
        return outcomes;
    }

    /** outcome→基线值 映射表。 */
    private Map<String, Double> valuesOf(List<DistinctOutcome> outcomes) {
        Map<String, Double> map = new LinkedHashMap<>(outcomes.size());
        for (DistinctOutcome o : outcomes) {
            map.put(o.outcome(), o.baseValue());
        }
        return map;
    }

    /** 线性响应权重（缺省 1.0）。 */
    private double[] weightsOf(List<String> variables, CounterfactualRequest request) {
        Map<String, Double> weights = (request.getBaseline() != null && request.getBaseline().getWeights() != null)
            ? request.getBaseline().getWeights() : Map.of();
        double[] arr = new double[variables.size()];
        for (int i = 0; i < variables.size(); i++) {
            Double w = weights.get(variables.get(i));
            arr[i] = w != null ? w : 1.0d;
        }
        return arr;
    }

    /** 按分布概率采一个 outcome 下标（概率和偏离 1 时按归一化兜底防负权重）。 */
    private int sampleOutcome(Random random, List<DistinctOutcome> outcomes) {
        double sum = 0d;
        for (DistinctOutcome o : outcomes) {
            sum += Math.max(0d, o.prob());
        }
        if (sum <= 0d) {
            return 0;
        }
        double r = random.nextDouble() * sum;
        for (int i = 0; i < outcomes.size(); i++) {
            r -= Math.max(0d, outcomes.get(i).prob());
            if (r < 0d) {
                return i;
            }
        }
        return outcomes.size() - 1;
    }

    /**
     * 单变量敏感性（分析式）：outcome 数值 +5% 扰动 → 结论均值响应 |ΔE|=0.05·w·E[y]
     * + 结论方差响应 |ΔVar|=2·0.05·w²·Var(y)（未扰动变量独立采样 → 协方差项 0）。
     * 干预语义：SET 单点化 E=value/Var=0；DELTA 全量平移 E 右移 delta 而 Var 不变。
     * 均值/方差均按分布概率加权（与蒙特卡洛采样同分布）。
     */
    private CounterfactualResult.SensitivityItem sensitivityOf(String variable, List<DistinctOutcome> outcomes,
                                                               double weight,
                                                               Map<String, CounterfactualRequest.Intervention> interventions) {
        CounterfactualRequest.Intervention itv = interventions.get(variable);
        double sumP = 0d;
        double sumY = 0d;
        double sumY2 = 0d;
        for (DistinctOutcome o : outcomes) {
            double p = Math.max(0d, o.prob());
            double y = valueUnderIntervention(o, itv);
            sumP += p;
            sumY += p * y;
            sumY2 += p * y * y;
        }
        if (sumP <= 0d) {
            return new CounterfactualResult.SensitivityItem(variable, 0d);
        }
        double yMean = sumY / sumP;
        double yVar = Math.max(0d, sumY2 / sumP - yMean * yMean);
        // SET 单点化覆写（valueUnderIntervention 已含 SET 语义：E=value, Var=0）
        double deltaE = SENSITIVITY_PERTURB * weight * yMean;
        double deltaVar = 2d * SENSITIVITY_PERTURB * weight * weight * yVar;
        return new CounterfactualResult.SensitivityItem(variable, Math.abs(deltaE) + Math.abs(deltaVar));
    }

    /** 干预语义下某 outcome 的取值（SET=定点 / DELTA=基线+delta / 无=基线）。 */
    private double valueUnderIntervention(DistinctOutcome o, CounterfactualRequest.Intervention itv) {
        if (itv == null) {
            return o.baseValue();
        }
        if ("SET".equals(itv.getOp())) {
            return itv.getValue();
        }
        return o.baseValue() + itv.getValue();
    }

    private static double mean(double[] arr) {
        double sum = 0d;
        for (double v : arr) {
            sum += v;
        }
        return arr.length == 0 ? 0d : sum / arr.length;
    }

    /** 已排序数组分位数（nearest-rank）。 */
    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 0) {
            return 0d;
        }
        int rank = (int) Math.ceil(p * sorted.length);
        rank = Math.max(1, Math.min(sorted.length, rank));
        return sorted[rank - 1];
    }

    private static String require(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new BusinessException(400, "COG-400: " + name + " 必填");
        }
        return v.trim();
    }

    /** 审计（铁律 §2.4#5：关键推理留痕发 Kafka {@code ecos.audit}）；未装配/异常降级 WARN 不阻塞主流程。 */
    private void audit(String action, String resource, String result, String detail) {
        if (eventBusService == null) {
            log.warn("反事实推演审计无法发送: EventBusService 未装配, action={}", action);
            return;
        }
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("action", action);
            event.put("resource", resource);
            event.put("result", result);
            event.put("detail", detail);
            event.put("userId", "system");
            event.put("timestamp", LocalDateTime.now().toString());
            eventBusService.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            log.warn("反事实推演审计 (EventBus) failed (ignored): {}", e.getMessage());
        }
    }
}
