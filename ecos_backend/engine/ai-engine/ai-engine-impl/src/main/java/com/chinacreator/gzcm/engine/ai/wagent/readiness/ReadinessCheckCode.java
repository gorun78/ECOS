package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.DegradeCode;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessCheckStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessLayer;

import java.math.BigDecimal;
import java.util.List;

/**
 * DIKC 四层就绪检查项单源（详细设计-10 F10-08 探测项 + F10-09 缺项；V231 {@code check_code} 列词表）。
 *
 * <p><b>check_code 词表取 V265 seed 的度量名而非卷 10 §2.4 的探测动作名</b>
 * （{@code data.check_coverage} 而非 {@code data.run_dq}）：阈值配置键与检查项必须可互推
 * （{@code wagent.readiness.{profile}.{check_code}}），seed 已按度量名落 12 行，
 * 探测动作名（run_dq / check_metric / detect_conflict / select_profiles / model_availability）
 * 属"怎么取数"，一个动作可产出多个度量，落进 {@code check_code} 列会造成一码多义。
 * ——裁决 D-B2④-5，登记于差距分析 v1.0.7。</p>
 *
 * <p><b>判档语义逐条来自 V265 seed 行 {@code description} 原文</b>（不是本类自创）：
 * 每条 judge 的注释都标出它兑现的是哪一句 seed。取值域越界、类型不符、阈值不可解析
 * 一律 {@code UNKNOWN}（F10-08 硬规则②「禁 fail-open」——不确定的界不能给出好看的档）。</p>
 *
 * <p><b>本枚举是纯函数面</b>：不探测、不读配置、不落库、不调 LLM；输入 =
 * {@link MetricValue} + {@link ThresholdSet}，输出 = {@link Judgement}（档位 + 可解释原因）。
 * 由 {@code ReadinessProbeTest#judgementSemanticsFollowSeedRows} 逐条锁死。</p>
 */
public enum ReadinessCheckCode {

    /** D 层：目标口径覆盖的期间/组织单元比例（seed：{@code < 本值 ⇒ fail}，域 (0,1]）。 */
    DATA_COVERAGE(ReadinessLayer.D, WAgentEngineCatalog.EngineKey.DATA, "data.check_coverage",
            List.of("data.check_coverage"), DegradeCode.DG_D1) {
        @Override
        public Judgement judge(MetricValue metric, ThresholdSet thresholds) {
            Judgement shape = requireNumber(metric, thresholds);
            if (shape != null) {
                return shape;
            }
            BigDecimal min = thresholds.value("data.check_coverage").orElseThrow().number().orElse(null);
            if (min == null || min.signum() <= 0 || min.compareTo(ONE) > 0) {
                // seed：「取值域 (0,1]，越界/非数 ⇒ 本项记 UNKNOWN（禁算 pass）」
                return Judgement.unknown("threshold-out-of-domain:data.check_coverage");
            }
            if (metric.number().compareTo(min) < 0) {
                return Judgement.of(ReadinessCheckStatus.FAIL, "coverage<" + min.toPlainString());
            }
            return Judgement.of(ReadinessCheckStatus.PASS, "coverage>=" + min.toPlainString());
        }
    },

    /** D 层：最近批次入湖距 as-of 的天数（seed：{@code > 本值 ⇒ warn，> 本值×2 ⇒ fail}）。 */
    DATA_STALENESS(ReadinessLayer.D, WAgentEngineCatalog.EngineKey.DATA, "data.staleness_days",
            List.of("data.staleness_days"), DegradeCode.DG_D1) {
        @Override
        public Judgement judge(MetricValue metric, ThresholdSet thresholds) {
            Judgement shape = requireNumber(metric, thresholds);
            if (shape != null) {
                return shape;
            }
            BigDecimal limit = thresholds.value("data.staleness_days").orElseThrow().number().orElse(null);
            if (limit == null || limit.signum() <= 0) {
                return Judgement.unknown("threshold-out-of-domain:data.staleness_days");
            }
            BigDecimal days = metric.number();
            // seed 两界：> 本值 ⇒ warn，> 本值×2 ⇒ fail（等于界不越界，严格大于）
            if (days.compareTo(limit.multiply(TWO)) > 0) {
                return Judgement.of(ReadinessCheckStatus.FAIL,
                        "staleness>" + limit.multiply(TWO).toPlainString() + "d");
            }
            if (days.compareTo(limit) > 0) {
                return Judgement.of(ReadinessCheckStatus.WARN,
                        "staleness>" + limit.toPlainString() + "d");
            }
            return Judgement.of(ReadinessCheckStatus.PASS, "staleness=" + days.toPlainString() + "d");
        }
    },

    /** I 层：目标指标是否绑定 PUBLISHED 口径版本（seed：{@code true=必须已定义，否则该层 fail}）。 */
    ONTO_CALIBER_DEFINED(ReadinessLayer.I, WAgentEngineCatalog.EngineKey.ONTOLOGY, "onto.caliber_defined",
            List.of("onto.caliber_defined"), null) {
        @Override
        public Judgement judge(MetricValue metric, ThresholdSet thresholds) {
            Judgement shape = requireBoolean(metric, thresholds);
            if (shape != null) {
                return shape;
            }
            Boolean enforce = thresholds.value("onto.caliber_defined").orElseThrow().bool().orElse(null);
            if (enforce == null) {
                return Judgement.unknown("threshold-unparsable:onto.caliber_defined");
            }
            if (!enforce) {
                // seed：「本值置 false 仅用于演示档，生产禁关」——运维显式关闭即按不强制出档，
                //       但开关本身已落 threshold_json，评估可复盘（禁代码私自忽略配置）
                return Judgement.of(ReadinessCheckStatus.PASS, "caliber-enforcement-disabled");
            }
            return metric.bool()
                    ? Judgement.of(ReadinessCheckStatus.PASS, "caliber-defined")
                    : Judgement.of(ReadinessCheckStatus.FAIL, "caliber-undefined");
        }
    },

    /** I 层：同指标多口径冲突数（seed：{@code > 本值 ⇒ fail；等于本值+1 ⇒ warn 并落 blocker 缺口}）。 */
    ONTO_CONFLICT_COUNT(ReadinessLayer.I, WAgentEngineCatalog.EngineKey.ONTOLOGY, "onto.conflict_count",
            List.of("onto.conflict_count_max"), null) {
        @Override
        public Judgement judge(MetricValue metric, ThresholdSet thresholds) {
            Judgement shape = requireNumber(metric, thresholds);
            if (shape != null) {
                return shape;
            }
            BigDecimal max = thresholds.value("onto.conflict_count_max").orElseThrow().number().orElse(null);
            if (max == null || max.signum() < 0) {
                return Judgement.unknown("threshold-out-of-domain:onto.conflict_count_max");
            }
            BigDecimal count = metric.number();
            if (count.signum() < 0) {
                return Judgement.unknown("metric-out-of-domain:conflict_count<0");
            }
            if (count.compareTo(max) <= 0) {
                return Judgement.of(ReadinessCheckStatus.PASS, "conflicts<=" + max.toPlainString());
            }
            // seed 两clause（「> 本值 ⇒ fail」与「等于本值+1 ⇒ warn」）在 max=0/count=1 处重叠，
            // 取更具体的那条 ⇒ 恰好越界一格 = warn（缺口表落 blocker），越两格以上才是 fail
            // ——裁决 D-B2④-6
            if (count.compareTo(max.add(ONE)) == 0) {
                return Judgement.of(ReadinessCheckStatus.WARN, "conflicts=" + count.toPlainString()
                        + "(=max+1)");
            }
            return Judgement.of(ReadinessCheckStatus.FAIL, "conflicts>" + count.toPlainString());
        }
    },

    /** K 层：可用画像/历史样本量（seed：{@code < min ⇒ warn 挂 DG-D1；< trend_floor ⇒ 只给方向}）。 */
    KNOW_SAMPLE_SIZE(ReadinessLayer.K, WAgentEngineCatalog.EngineKey.KNOWLEDGE, "know.sample_size",
            List.of("know.sample_size_min", "know.sample_size_trend_floor"), DegradeCode.DG_D1) {
        @Override
        public Judgement judge(MetricValue metric, ThresholdSet thresholds) {
            Judgement shape = requireNumber(metric, thresholds);
            if (shape != null) {
                return shape;
            }
            BigDecimal min = thresholds.value("know.sample_size_min").orElseThrow().number().orElse(null);
            if (min == null || min.signum() <= 0) {
                return Judgement.unknown("threshold-out-of-domain:know.sample_size_min");
            }
            // seed：「必须 ≤ sample_size_min，应用侧校验不满足时回落本值=sample_size_min/3 并记 WARN」
            BigDecimal floor = thresholds.value("know.sample_size_trend_floor").orElseThrow().number().orElse(null);
            boolean floorSelfCorrected = floor == null || floor.signum() <= 0 || floor.compareTo(min) > 0;
            if (floorSelfCorrected) {
                floor = min.divide(THREE, 0, java.math.RoundingMode.DOWN);
            }
            BigDecimal n = metric.number();
            if (n.compareTo(floor) < 0) {
                // 低于趋势下限＝连方向都不可给（预测档无区间即无结论），记 fail 不入 pass/warn
                return Judgement.of(ReadinessCheckStatus.FAIL, "n<" + floor.toPlainString()
                        + "(trend_floor" + (floorSelfCorrected ? ",self-corrected=min/3" : "") + ")");
            }
            if (n.compareTo(min) < 0) {
                return Judgement.of(ReadinessCheckStatus.WARN, "n<" + min.toPlainString() + " ⇒ DG-D1");
            }
            return Judgement.of(ReadinessCheckStatus.PASS, "n>=" + min.toPlainString()
                    + (floorSelfCorrected ? "(trend_floor self-corrected)" : ""));
        }
    },

    /** K 层：命中画像对当前 scope 的适用度（seed：{@code < 本值 ⇒ 视为缺历史画像先验，挂 DG-K1}）。 */
    KNOW_APPLICABILITY(ReadinessLayer.K, WAgentEngineCatalog.EngineKey.KNOWLEDGE, "know.applicability",
            List.of("know.applicability_min"), DegradeCode.DG_K1) {
        @Override
        public Judgement judge(MetricValue metric, ThresholdSet thresholds) {
            Judgement shape = requireNumber(metric, thresholds);
            if (shape != null) {
                return shape;
            }
            BigDecimal min = thresholds.value("know.applicability_min").orElseThrow().number().orElse(null);
            if (min == null || min.signum() <= 0 || min.compareTo(ONE) > 0) {
                return Judgement.unknown("threshold-out-of-domain:know.applicability_min");
            }
            if (metric.number().compareTo(min) < 0) {
                // seed 未给档位，只给"挂 DG-K1 并需用户确认"；DG-K1 requires_ack=true ⇒ 需确认而非阻断，
                // 取 warn（不阻断但压档到 B），并绑定 DG-K1 ——裁决 D-B2④-7
                return Judgement.of(ReadinessCheckStatus.WARN,
                        "applicability<" + min.toPlainString() + " ⇒ DG-K1");
            }
            return Judgement.of(ReadinessCheckStatus.PASS, "applicability>=" + min.toPlainString());
        }
    },

    /** C 层：意图所需 CM 类型在册且发布态（seed：{@code 未就绪 ⇒ 该层 blocker ⇒ 总级 D}）。 */
    COG_CM_READY(ReadinessLayer.C, WAgentEngineCatalog.EngineKey.COGNITIVE, "cog.cm_ready",
            List.of("cog.cm_ready"), DegradeCode.DG_C1) {
        @Override
        public Judgement judge(MetricValue metric, ThresholdSet thresholds) {
            Judgement shape = requireBoolean(metric, thresholds);
            if (shape != null) {
                return shape;
            }
            Boolean enforce = thresholds.value("cog.cm_ready").orElseThrow().bool().orElse(null);
            if (enforce == null) {
                return Judgement.unknown("threshold-unparsable:cog.cm_ready");
            }
            if (!enforce) {
                return Judgement.of(ReadinessCheckStatus.PASS, "cm-readiness-enforcement-disabled");
            }
            return metric.bool()
                    ? Judgement.of(ReadinessCheckStatus.PASS, "cm-ready")
                    : Judgement.of(ReadinessCheckStatus.BLOCKER, "cm-unavailable ⇒ DG-C1");
        }
    },

    /** C 层：该 CM 版本是否已有回测留痕（seed：default 档 {@code warn 不阻断}，forecast 档 {@code 升 blocker}）。 */
    COG_BACKTEST_READY(ReadinessLayer.C, WAgentEngineCatalog.EngineKey.COGNITIVE, "cog.backtest_ready",
            List.of("cog.backtest_ready"), null) {
        @Override
        public Judgement judge(MetricValue metric, ThresholdSet thresholds) {
            Judgement shape = requireBoolean(metric, thresholds);
            if (shape != null) {
                return shape;
            }
            Boolean enforce = thresholds.value("cog.backtest_ready").orElseThrow().bool().orElse(null);
            if (enforce == null) {
                return Judgement.unknown("threshold-unparsable:cog.backtest_ready");
            }
            if (!enforce) {
                return Judgement.of(ReadinessCheckStatus.PASS, "backtest-enforcement-disabled");
            }
            if (metric.bool()) {
                return Judgement.of(ReadinessCheckStatus.PASS, "backtest-on-record");
            }
            // 同一条 seed 在两个档位给不同强度：default 行写"未就绪记 warn 不阻断"，
            // forecast 行写"升为 blocker 语义"⇒ 强度由命中档位决定，不写死在代码里
            return PROFILE_FORECAST.equals(thresholds.profileId())
                    ? Judgement.of(ReadinessCheckStatus.BLOCKER, "no-backtest-on-forecast-profile")
                    : Judgement.of(ReadinessCheckStatus.WARN, "no-backtest-record");
        }
    };

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal THREE = BigDecimal.valueOf(3);

    /** seed forecast 档档位名（V265 只落 default/forecast 两档，其余意图一律 default）。 */
    public static final String PROFILE_FORECAST = "forecast";

    private final ReadinessLayer layer;
    private final WAgentEngineCatalog.EngineKey engineKey;
    private final String checkCode;
    private final List<String> thresholdFragments;
    private final DegradeCode degradeCode;

    ReadinessCheckCode(ReadinessLayer layer, WAgentEngineCatalog.EngineKey engineKey, String checkCode,
                       List<String> thresholdFragments, DegradeCode degradeCode) {
        this.layer = layer;
        this.engineKey = engineKey;
        this.checkCode = checkCode;
        this.thresholdFragments = List.copyOf(thresholdFragments);
        this.degradeCode = degradeCode;
    }

    /**
     * 判档（纯函数）。
     *
     * @param metric     度量值（ABSENT ⇒ UNKNOWN）
     * @param thresholds 本档位阈值集合（调用方保证声明的片段已在内，未命中片段由本方法点名）
     */
    public abstract Judgement judge(MetricValue metric, ThresholdSet thresholds);

    public ReadinessLayer layer() {
        return layer;
    }

    /** 探测目标所属 Engine 目录键（R-43：端点寻址一律引 {@link WAgentEngineCatalog} 单源）。 */
    public WAgentEngineCatalog.EngineKey engineKey() {
        return engineKey;
    }

    /** V231 {@code check_code} 列值（≤40 字符，与 seed 阈值片段同源）。 */
    public String checkCode() {
        return checkCode;
    }

    /** 该检查项需要的阈值片段（全部命中才可判档）。 */
    public List<String> thresholdFragments() {
        return thresholdFragments;
    }

    /** 该项不达标时绑定的预登记降级码（{@code null}=不触发 DG，F10-10 白名单只可关不可开新）。 */
    public DegradeCode degradeCode() {
        return degradeCode;
    }

    /** 期望度量形态（类型不符 ⇒ UNKNOWN，禁把布尔当数值比）。 */
    public MetricValue.Kind expectedKind() {
        return switch (this) {
            case ONTO_CALIBER_DEFINED, COG_CM_READY, COG_BACKTEST_READY -> MetricValue.Kind.BOOLEAN;
            default -> MetricValue.Kind.NUMBER;
        };
    }

    /** 按 V231 列值反查（读回历史评估行时用；未知词表 ⇒ empty，禁造新项）。 */
    public static java.util.Optional<ReadinessCheckCode> fromCheckCode(String code) {
        if (code == null) {
            return java.util.Optional.empty();
        }
        for (ReadinessCheckCode value : values()) {
            if (value.checkCode.equals(code.trim())) {
                return java.util.Optional.of(value);
            }
        }
        return java.util.Optional.empty();
    }

    // ── judge 公共前置（三类"判不了"的形态，一律 UNKNOWN）──────────────────

    private static Judgement requireNumber(MetricValue metric, ThresholdSet thresholds) {
        return requireShape(metric, thresholds, MetricValue.Kind.NUMBER);
    }

    private static Judgement requireBoolean(MetricValue metric, ThresholdSet thresholds) {
        return requireShape(metric, thresholds, MetricValue.Kind.BOOLEAN);
    }

    private static Judgement requireShape(MetricValue metric, ThresholdSet thresholds, MetricValue.Kind want) {
        if (metric == null) {
            return Judgement.unknown("metric-absent:null");
        }
        if (!metric.available()) {
            return Judgement.unknown("metric-absent");
        }
        if (metric.kind() != want) {
            return Judgement.unknown("metric-kind-mismatch:want=" + want + " got=" + metric.kind());
        }
        if (thresholds == null || !thresholds.present()) {
            return Judgement.unknown("threshold-unset");
        }
        return null; // 形态可用，交给各常量自己的界比较
    }

    /** 判档输出：档位 + 原因（原因落 {@code measured_json}/缺项清单，禁只留字母）。 */
    public record Judgement(ReadinessCheckStatus status, String reason) {

        public Judgement {
            if (status == null) {
                throw new IllegalArgumentException("Judgement.status 必填（判不了也要显式 UNKNOWN）");
            }
        }

        static Judgement of(ReadinessCheckStatus status, String reason) {
            return new Judgement(status, reason);
        }

        static Judgement unknown(String reason) {
            return new Judgement(ReadinessCheckStatus.UNKNOWN, reason);
        }
    }
}
