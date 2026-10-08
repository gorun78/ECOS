package com.chinacreator.gzcm.engine.cognitive.api;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 契约响应 VO（详细设计-05 D.3）—— 前后端双向冻结，字段名禁改。
 *
 * <p>与每个 F05-* 端点的写口径一一对齐；
 * 空集响应 = {@code data:[]} (B-4 DG-6)，故障响应经 {@link CognitiveErrorCode} 走 5xx 原样穿透。</p>
 */
public final class CognitiveContractSchema {

    private CognitiveContractSchema() {}

    /** E1 场景可用 Mind 探测。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class MindVO {
        private String  mindId;
        private String  name;
        /** {@link MindCapabilityMask#name()} 数组；null 表示解析异常（由 {@code issues[]} 记录）。 */
        private List<String> capabilityMask;
        /** {@link ClosedLoopBound#name()} 数组；非法值读侧容错为 `[]` + issue。 */
        private List<String> closedLoopBounds;
        private Boolean isActive;
    }

    /** E2 运行评价 metric 行。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class OperationEvalMetric {
        private String  metricId;
        private String  name;
        /** {@link MindCapabilityMask#name()} 或 null（未映射，不丢行）。 */
        private String  capability;
        /** 0..1；越界即 500，禁 clamp。 */
        private Double  score;
        /** 本期恒 FLAT；附响应级 trendSource。 */
        private String  trend;
    }

    /** E2 aggregate wrapper。 */
    @Data @NoArgsConstructor
    public static class OperationEvalData {
        private String  mindId;
        private int     limit;
        private boolean truncated;
        /** 本期恒 1。 */
        private double  trendWeight;
        private String  trendWeightSource = "ecos.cognitive.eval.trend_weight";
        private String  trendSource = "NOT_IMPLEMENTED";
        /**
         * 未标定概率（{@code probability IS NULL}）而被剔除排序集的行数（V262 列位 / REQ-COG-09 规格 4）。
         * 前端必须显式渲染该计数，禁把"0 分"当"无数据"（详设-05 D.3 之 E2 补字段）。
         */
        private int     missingProbability;
        private List<OperationEvalMetric> metrics = java.util.List.of();
    }

    /** E3 假设行。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class HypothesisVO {
        private String  hypothesisId;
        private String  mindId;
        private String  statement;
        private String  reason;
        /** 0..1。 */
        private Double  confidence;
        /** {@link HypothesisStatus#name()} 终态（读过 RAW 时经别名映射）。 */
        private String  status;
        private String  scenarioId;
        private String  updatedAt;
    }

    /** E4 信念行。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BeliefVO {
        private String  beliefId;
        private String  mindId;
        private String  variableName;
        private String  domain;
        /** 0..1 概率；越界即 500。 */
        private Double  probability;
        /** 0..1 强度。 */
        private Double  strength;
        /** {@link MindCapabilityMask#name()} 或 null（未映射）。 */
        private String  capability;
        private String  updatedAt;
    }

    /** 通用 issues 记录（DG-7 未映射能力等，可观测、禁静默）。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Issue {
        private String  code;
        private String  message;
        private String  subjectId;
    }

    /** 分页/截断响应基础。 */
    @Data @NoArgsConstructor
    public static class Paged<T> {
        private List<T> items = java.util.List.of();
        private int     limit;
        private boolean truncated;
    }

    // ── F05-09 / F05-10 契约块（D.3 冻结；只增不改：缺省请求路径不产出这些对象）──────

    /** {@code simulate.mode} 确定性重算字面量（D.3）。 */
    public static final String MODE_DETERMINISTIC_DIFF = "DETERMINISTIC_DIFF";
    /** 探索式（RAG+Agent）通道的口径声明字面量 —— 非核算口径。 */
    public static final String CALIBER_NON_ACCOUNTING = "NON_ACCOUNTING";
    /** 归因平衡容差（F05-10 要点3：±0.01）。 */
    public static final BigDecimal BALANCE_TOLERANCE = new BigDecimal("0.01");

    /**
     * F05-09 diagnose 请求增块（可选）。六要素与快照定位键
     * （{@code ecos_dw.ecos_application_forecast_run} 的 caliber/as_of/project/department 列）同源。
     */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ForecastContextVO {
        private String forecastRunId;
        private String caliberId;
        private String caliberVersion;
        private String asOfTime;
        private String projectId;
        private String departmentId;
    }

    /** {@code chains[].path} 节点；{@code type} 形如 {@code EVENT} / {@code METRIC:M_FC_PROFIT}。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ChainNodeVO {
        private String node;
        private String type;
    }

    /** 统计支撑（与 {@code evidenceRefs} 二选一或并存，构成 F05-09 要点3 的"有证据边"）。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StatSupportVO {
        private String  method;
        /** 0..1；越界即 500（D.2 规则 4，禁 clamp 静默）。 */
        private Double  pValue;
    }

    /** 无证据边的显性观测项（禁静默丢弃）。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UnverifiedHypothesisVO {
        private String statement;
        private String reason;
    }

    /**
     * F05-09 响应增块的一条因果链：{@code contribution} 一律由快照确定性复算
     * （禁 LLM 生成数值，B-2），{@code recomputable=true} 是复算可验证声明。
     */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class DiagnoseChainVO {
        private String  chainId;
        private List<ChainNodeVO> path;
        private BigDecimal contribution;
        private List<String> evidenceRefs;
        private StatSupportVO statSupport;
        private boolean recomputable;
        private List<UnverifiedHypothesisVO> unverifiedHypotheses;
    }

    /** F05-09 响应增块（DG-8：LLM 不可用只降级解释，数值不得缺失或伪造）。 */
    @Data @NoArgsConstructor
    public static class DiagnoseForecastData {
        private List<DiagnoseChainVO> chains = java.util.List.of();
        private String  explanation;
        private boolean explanationUnavailable;
    }

    /** F05-10 要点1：overrides 六字段结构冻结（只覆盖命中范围）。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SimulateOverrideVO {
        private String  projectId;
        private String  departmentId;
        private List<String> periods;
        private String  target;
        private String  stage;
        private BigDecimal delta;
    }

    /** F05-10 确定性重算请求（{@code mode} 缺省 = 既有探索式通道，向后兼容）。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SimulateRequestVO {
        private String  mode;
        private String  baselineRunId;
        private String  scenarioRunId;
        private List<SimulateOverrideVO> overrides;
    }

    /**
     * F05-10 确定性重算响应。平衡自检不过时调用方直接 503（{@code ECOS-COG-502}），
     * 本对象**不会**以 {@code balanceOk=false} 形态出现在响应里。
     */
    @Data @NoArgsConstructor
    public static class SimulateResponseVO {
        private String  mode;
        private String  scenarioRunId;
        private boolean balanceOk;
        private BigDecimal deltaProfit;
        private BigDecimal contributionsSum;
        private BigDecimal tolerance;
        private List<DiagnoseChainVO> chains = java.util.List.of();
    }

    /** 探索通道响应声明（无金额字段；与 {@code SimulationResult} 并行回显供 UI 打"非核算口径"徽标）。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ExploratoryCaliberVO {
        private boolean exploratory;
        private String  caliber;
    }
}
