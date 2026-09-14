package com.chinacreator.gzcm.engine.cognitive2.service.mental;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive2.dto.BeliefSaveDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 不确定性判断加权贝叶斯更新器（PMO-59 P2b / ADR-9）— <b>纯 Java，LLM 零参与</b>（ADR-5 口径）。
 *
 * <p>确定性似然规则（可审计、可回放）：
 <ol>
   <li><b>显式似然优先（fact/value 关系匹配）</b>：证据 blob 携带 {@code likelihood} 字段
       （outcome→似然强度 0~1 的 Map，外部/专家标注）时直接采用：
       {@code posterior ∝ prior × (1 + α·likelihood)}（α=1.0，未知 outcome 取 0.5 弱先验）；</li>
   <li><b>confidence 分桶置顶收敛（默认规则）</b>：blob 无显式似然时，按证据可信度分档
       （0.95+ 系统数据档 / 0.8 权威档 / 0.5 中档 / 0.3 弱档，取首个满足档的 0.95/0.8/0.5/0.3 序列上限）
       为收敛权重，把分布向当前最大 outcome 收敛：
       {@code top' = top + (1 - top) · (conf/2)}，其余 outcome 按原比例缩放保持概率和=1。</li>
 </ol>
  两条规则最终都归一化，保证输出分布 prob 和=1（±1e-9）；相同输入恒同输出（Phase 3 时间回放基数）。</p>
 */
public final class BayesianUpdater {

    /** 显式似然的敏感系数 α（post = prior × (1 + α·L)，归一化） */
    public static final double LIKELIHOOD_ALPHA = 1.0d;

    private BayesianUpdater() {
    }

    /**
     * 对新分布做加权贝叶斯式更新。
     *
     * @param current      当前分布点（outcome, prob; prob 和=1 前提）
     * @param likelihood   blob 显式似然（可为 null，走默认置顶收敛规则）
     * @param confidence   证据可信度 0~1（默认规则权重）
     * @return 更新后的分布点列表（prob 和=1，保持顺序与 outcome 不变）
     */
    public static List<BeliefSaveDTO.OutcomeProb> update(List<BeliefSaveDTO.OutcomeProb> current,
                                                         Map<String, Double> likelihood,
                                                         double confidence) {
        if (current == null || current.isEmpty()) {
            throw new BusinessException(400, "COG-400: 当前分布为空，无法更新");
        }
        double[] posteriorRaw = new double[current.size()];
        if (likelihood != null && !likelihood.isEmpty()) {
            // 规则①: 显式似然 — post ∝ prior × (1 + α·L)，未知 outcome 似然取中位 0.5 弱先验
            double priorSum = 0d;
            for (BeliefSaveDTO.OutcomeProb point : current) {
                priorSum += point.getProb();
            }
            for (int i = 0; i < current.size(); i++) {
                BeliefSaveDTO.OutcomeProb point = current.get(i);
                double l = likelihood.getOrDefault(point.getOutcome().trim(), 0.5d);
                l = clamp01(l);
                posteriorRaw[i] = point.getProb() * (1d + LIKELIHOOD_ALPHA * l);
            }
            if (priorSum <= 0d) {
                throw new BusinessException(400, "COG-400: 当前分布概率和为 0，无法更新");
            }
        } else {
            // 规则②: confidence 分桶置顶收敛 — top' = top + (1-top)·(bucket(conf)/2)，其余按比例缩放
            int topIdx = 0;
            for (int i = 1; i < current.size(); i++) {
                if (current.get(i).getProb() > current.get(topIdx).getProb()) {
                    topIdx = i;
                }
            }
            double top = current.get(topIdx).getProb();
            double conf = lowerBucket(confidence);
            double topAfter = top + (1d - top) * (conf / 2d);
            double massLeft = 1d - topAfter;
            double othersSum = 0d;
            for (BeliefSaveDTO.OutcomeProb point : current) {
                othersSum += point.getProb();
            }
            for (int i = 0; i < current.size(); i++) {
                posteriorRaw[i] = (i == topIdx) ? topAfter : current.get(i).getProb() * (othersSum <= 0d ? 0d : massLeft / othersSum);
            }
        }
        // 归一化（浮点误差兜底）
        double sum = 0d;
        for (double v : posteriorRaw) {
            sum += v;
        }
        if (sum <= 0d) {
            throw new BusinessException(400, "COG-400: 更新后分布概率和为 0，似然输入非法");
        }
        List<BeliefSaveDTO.OutcomeProb> updated = new ArrayList<>(current.size());
        for (int i = 0; i < current.size(); i++) {
            BeliefSaveDTO.OutcomeProb point = new BeliefSaveDTO.OutcomeProb();
            point.setOutcome(current.get(i).getOutcome());
            point.setProb(posteriorRaw[i] / sum);
            updated.add(point);
        }
        return updated;
    }

    /**
     * 从证据 blob 解析显式似然 Map（{@code likelihood: {"outcome": 0..1}}）；缺失/非法返回 null。
     * 非法值（非 0~1 数字）按 0.5 中位处理不抛（弱先验兜底），整字段解析失败才返 null 走默认规则。
     */
    public static Map<String, Double> parseLikelihood(Map<String, Object> blob) {
        if (blob == null) {
            return null;
        }
        Object lk = blob.get("likelihood");
        if (!(lk instanceof Map)) {
            return null;
        }
        Map<String, Double> likelihood = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = (Map<String, Object>) lk;
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!(entry.getValue() instanceof Number n)) {
                likelihood.put(entry.getKey(), 0.5d);
            } else {
                likelihood.put(entry.getKey(), clamp01(n.doubleValue()));
            }
        }
        return likelihood.isEmpty() ? null : likelihood;
    }

    private static double clamp01(double v) {
        return Math.max(0d, Math.min(1d, v));
    }

    /**
     * confidence 分桶（外部方案层1可信度口径：系统数据 0.95+ / 权威新闻官宣 ~0.8 / 中档 0.5 / 小道消息 0.3）——
     * 取首个满足档位的桶上限，作为默认收敛规则的权重。
     */
    public static double lowerBucket(double confidence) {
        double conf = clamp01(confidence);
        if (conf >= 0.95d) {
            return 0.95d;
        }
        if (conf >= 0.8d) {
            return 0.8d;
        }
        if (conf >= 0.5d) {
            return 0.5d;
        }
        return 0.3d;
    }
}
