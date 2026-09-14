package com.chinacreator.gzcm.engine.data.quality.scoring.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 6 维评分引擎入口（PMO-48-B T8 评分引擎核心）— 构造器注入所有 {@link DimensionEvaluator}，
 * 按 {@link DqDimension} 分发评估，返回 {@link Map<DqDimension, DimensionScore>}。
 *
 * <p>实现约定（铁律 1.3 / 0.3）：</p>
 * <ul>
 *   <li>构造器注入 {@code List<DimensionEvaluator>} — Spring 自动收集 data-engine-impl 中 6 个 @Component</li>
 *   <li>重复维度（同 dimension() 多个 Bean）→ 仅保留第一个 + warn 级日志（防 Broken Object Graph）</li>
 *   <li>评估器内不允许写库；本类也不允许写库（落库在 DqScoreService）</li>
 *   <li>线程安全：6 个 evaluator 全部无状态 → 单例安全</li>
 * </ul>
 *
 * <p>调用约定：</p>
 * <pre>
 *   Map&lt;DqDimension, DimensionScore&gt; scores = engine.evaluateAll(ctx);
 *   // snapshot JSONB 写评测上下文 (ruleType / threshold / sampleStats ...)
 * </pre>
 *
 * @author PMO-48-B T8
 */
@Component
public class DqScoreEngine {

    private static final Logger log = LoggerFactory.getLogger(DqScoreEngine.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final Map<DqDimension, DimensionEvaluator> evaluators = new HashMap<>();

    public DqScoreEngine(java.util.List<DimensionEvaluator> beans) {
        for (DimensionEvaluator ev : beans) {
            DimensionEvaluator prev = evaluators.putIfAbsent(ev.dimension(), ev);
            if (prev != null) {
                log.warn("DqScoreEngine: dimension {} 重复 Bean 实现 {} 与 {}, 保留 {}",
                        ev.dimension(), prev.getClass().getSimpleName(), ev.getClass().getSimpleName(),
                        prev.getClass().getSimpleName());
            }
        }
        for (DqDimension d : DqDimension.all()) {
            if (!evaluators.containsKey(d)) {
                // 缺一个 Bean 日志 warning 但不阻塞 Spring 启动（生产 deploy 时二次校验）
                log.warn("DqScoreEngine: dimension {} 未注册评估器，evaluateAll 会返回 1.0 兜底", d);
            }
        }
    }

    /**
     * 统一对 6 个维度调用 evaluateAll (每个评估器独立 try/catch 隔离故障)。
     *
     * <p>异常隔离策略：评估器抛 RuntimeException 不影响其它维度，仅当前维度记
     * {@code detail.status=ERROR, scoreValue=0.0}；整体方法永不抛。</p>
     *
     * <p>边界处理：evaluators 缺失（如外部接入砍除某个评估器）→ 自动填 1.0 兜底，
     * 保证下游 6 维 radar 始终可渲染。</p>
     *
     * @param ctx 评估上下文（永不为 null，Service 层兜底 new ScoringContext()）
     * @return 6 维分数（key 为维度枚举，固定 6 个 entry）
     */
    public Map<DqDimension, DimensionScore> evaluateAll(ScoringContext ctx) {
        ScoringContext safeCtx = ctx != null ? ctx : new ScoringContext();
        if (log.isDebugEnabled()) {
            log.debug("DqScoreEngine evaluateAll: ruleId={}, scope={}:{}",
                    safeCtx.getRuleId(), safeCtx.getScopeType(), safeCtx.getScopeId());
        }
        Map<DqDimension, DimensionScore> result = new HashMap<>();
        for (DqDimension d : DqDimension.all()) {
            DimensionEvaluator ev = evaluators.get(d);
            if (ev == null) {
                DimensionScore s = new DimensionScore();
                s.setDimension(d);
                s.setScoreValue(1.0D);
                s.setSampleSize((int) Math.min(safeCtx.getTotalRows(), MAX_SAMPLE_ROWS));
                s.setRuleCount(0);
                java.util.LinkedHashMap<String, Object> det = new java.util.LinkedHashMap<>();
                det.put("status", "EVALUATOR_MISSING");
                det.put("sampleSize", safeCtx.getTotalRows());
                s.setDetails(det);
                result.put(d, s);
                continue;
            }
            result.put(d, safeEvaluate(ev, safeCtx));
        }
        return result;
    }

    /** 单维度评估隔离：异常不传播。 */
    private DimensionScore safeEvaluate(DimensionEvaluator ev, ScoringContext ctx) {
        try {
            DimensionScore s = ev.evaluate(ctx);
            if (s == null) {
                s = new DimensionScore();
                s.setDimension(ev.dimension());
                s.setScoreValue(1.0D);
                s.setSampleSize((int) Math.min(ctx.getTotalRows(), MAX_SAMPLE_ROWS));
                s.setRuleCount(0);
                java.util.LinkedHashMap<String, Object> det = new java.util.LinkedHashMap<>();
                det.put("status", "NULL_RESULT_FALLBACK");
                s.setDetails(det);
            }
            // 兜底 score clamp [0, 1]
            double v = s.getScoreValue();
            if (Double.isNaN(v) || v < 0.0D) {
                v = 0.0D;
            } else if (v > 1.0D) {
                v = 1.0D;
            }
            s.setScoreValue(v);
            return s;
        } catch (RuntimeException ex) {
            log.warn("DqScoreEngine evaluate {} failed: {}", ev.dimension(), ex.getMessage());
            DimensionScore s = new DimensionScore();
            s.setDimension(ev.dimension());
            s.setScoreValue(0.0D);
            s.setSampleSize((int) Math.min(ctx.getTotalRows(), MAX_SAMPLE_ROWS));
            s.setRuleCount(0);
            java.util.LinkedHashMap<String, Object> det = new java.util.LinkedHashMap<>();
            det.put("status", "ERROR");
            det.put("error", maybeExSummary(ex));
            s.setDetails(det);
            return s;
        }
    }

    /** 异常摘要：避免敏感原文进入 detail JSONB（控制长度 + 仅 message 前 200 字符）。 */
    private static String maybeExSummary(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) {
            return t.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    /** 样本评估上限（方案 §5：sample_size ≤ 1000） */
    static final int MAX_SAMPLE_ROWS = 1000;

    /** 单次评估上下文组织 — 把 dq_rule_check 行填充为 ScoringContext。 */
    public static ScoringContext toContext(String ruleId, String scopeType, String scopeId,
                                           java.util.Map<String, Object> parameters,
                                           java.util.Map<String, Object> connectionConfig,
                                           java.util.Map<String, Object> sampleFailures,
                                           long totalRows, long failedRows,
                                           java.time.LocalDateTime executedAt,
                                           java.util.Map<String, Object> lastCheck,
                                           String targetField) {
        ScoringContext ctx = new ScoringContext();
        ctx.setRuleId(ruleId);
        ctx.setScopeType(scopeType);
        ctx.setScopeId(scopeId);
        ctx.setParameters(safeParse(parameters));
        ctx.setConnectionConfig(safeParse(connectionConfig));
        ctx.setSampleFailures(safeParse(sampleFailures));
        ctx.setTotalRows(totalRows);
        ctx.setFailedRows(failedRows);
        ctx.setExecutedAt(executedAt);
        ctx.setLastCheck(safeParse(lastCheck));
        ctx.setTargetField(targetField);
        return ctx;
    }

    private static java.util.Map<String, Object> safeParse(java.util.Map<String, Object> raw) {
        return raw == null ? new java.util.LinkedHashMap<>() : raw;
    }

    /** 把 Map 转为 JSON 字符串（供 Service 写 dq_score_asset.rolled_up_scores 用）。 */
    public static String toJsonString(java.util.Map<String, Object> m) {
        try {
            return m == null ? "{}" : MAPPER.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 把 JSON 字符串解析为 Map（供 V112 读取 rolled_up_scores 用）。 */
    public static Map<String, Object> parseToJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return new java.util.LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return new java.util.LinkedHashMap<>();
        }
    }
}
