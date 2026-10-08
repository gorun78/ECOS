package com.chinacreator.gzcm.engine.cognitive.api.observability;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;

/**
 * 认知域事件日志编解码（详细设计-05 C.7-2 冻结行格式，纯函数、禁依赖 Spring/DB — C.8）。
 *
 * <p>冻结格式：{@code EVT=CognitiveAx op={detect|eval|hyp|belief} agent={} scenario={} mind={}
 * cost={}ms result={ok|empty|deny|stub}}。traceId 不在行内重复（由 logback pattern 从 MDC 带出，
 * C.7-1），响应 {@code data.traceId} 回显见 {@code CognitiveErrorAdvice}。</p>
 *
 * <p>{@code result} 四分只覆盖数据面结局：空集率（{@link Result#EMPTY}）与降级率
 * （{@link Result#DENY}＋{@link Result#STUB}）。故障态（{@code ECOS-COG-500/501/502/504}）
 * 按 D.5 走错误日志与错误矩阵，不进本行，禁把 5xx 混入降级率。</p>
 *
 * <p>{@code agent} 在场景认知四端点无数据源（Mind 资产表无 agent 归属列，agent 属 06 册智能域），
 * 取不到即 {@code -}，禁伪造。</p>
 */
public final class CognitiveEventLog {

    /** 事件行前缀（Grafana/日志检索的锚点，禁改写）。 */
    public static final String EVENT_PREFIX = "EVT=CognitiveAx";

    /** 无值占位（禁 null/空串/伪造值）。 */
    public static final String ABSENT = "-";

    /** 指标名单源（C.7-4，三枚计数经 runtime-monitor {@code IMetricRecorder} 暴露）。 */
    public static final String METRIC_EVAL_TRUNCATED = "cognitive_eval_truncated_total";
    public static final String METRIC_CAPABILITY_UNMAPPED = "cognitive_capability_unmapped_total";
    public static final String METRIC_BALANCE_FAIL = "cognitive_balance_fail_total";

    private CognitiveEventLog() {}

    /** C.7-2 四操作（E1~E4 与文档 token 一一对应）。 */
    public enum Op {
        DETECT("detect"),
        EVAL("eval"),
        HYP("hyp"),
        BELIEF("belief");

        private final String token;

        Op(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** C.7-2 数据面结局四分。 */
    public enum Result {
        OK("ok"),
        EMPTY("empty"),
        DENY("deny"),
        STUB("stub");

        private final String token;

        Result(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** 冻结行拼装；任一维度缺失写 {@code -}。 */
    public static String format(Op op, String agent, String scenario, String mind, long costMs, Result result) {
        return EVENT_PREFIX
                + " op=" + (op == null ? ABSENT : op.token())
                + " agent=" + value(agent)
                + " scenario=" + value(scenario)
                + " mind=" + value(mind)
                + " cost=" + costMs + "ms"
                + " result=" + (result == null ? ABSENT : result.token());
    }

    /** 非空集合 → OK，空集合 → EMPTY（DG-6：200 空集与 5xx 严格可分）。 */
    public static Result ofSize(int rows) {
        return rows <= 0 ? Result.EMPTY : Result.OK;
    }

    /**
     * guard 异常错误码 → EVT 结局。
     *
     * @return {@link Result#STUB}（DG-9）/{@link Result#DENY}（400/403/404 属主与鉴权拒答）；
     *         故障态码返回 {@code null} = 本行不记（由错误日志承载）
     */
    public static Result forErrorCode(String errorCode) {
        if (errorCode == null) {
            return null;
        }
        if (CognitiveErrorCode.E503_NOT_IMPLEMENTED.equals(errorCode)) {
            return Result.STUB;
        }
        if (CognitiveErrorCode.E400_MIND_NOT_ON_SCENARIO.equals(errorCode)
                || CognitiveErrorCode.E400_MIND_NOT_FOUND.equals(errorCode)
                || CognitiveErrorCode.E400_INVALID_PARAM.equals(errorCode)
                || CognitiveErrorCode.E403_CLEARANCE.equals(errorCode)
                || CognitiveErrorCode.E404_SCENARIO.equals(errorCode)) {
            return Result.DENY;
        }
        return null;
    }

    private static String value(String raw) {
        return raw == null || raw.isBlank() ? ABSENT : raw;
    }
}
