package com.chinacreator.gzcm.engine.ai.wagent.readiness;

/**
 * Readiness 度量取数端口（详细设计-10 F10-08 算法树「取度量」枝的可插拔落位）。
 *
 * <p><b>为什么是端口而不是直接调引擎</b>：八度量（{@code data.check_coverage}…{@code cog.backtest_ready}）
 * 在 data/ontology/kb/cognitive 四引擎侧<b>目前都没有读端点</b>（差距分析 v1.0.6 实测：kb 引擎
 * {@code applicability} 零命中、无 {@code /api/v1/cognitive/models} 映射），而 ai-engine 又被
 * {@code ArchitectureTest#shouldNotDependOnOtherEngines} 禁止 import 其它引擎包。
 * 于是取数面必须可插拔：引擎补上读端点 ⇒ 新增一个实现即可；没补 ⇒ 该项返回
 * {@link MetricValue#absent(String)}，检查项如实 {@code UNKNOWN} 并把总级压到 C。</p>
 *
 * <p><b>红线</b>：实现类<b>禁止</b>为让流程"跑通"而返回估算值、样例值或 0。
 * "没取到"与"取到了但很差"是两件事，前者是 UNKNOWN（证据缺席），后者才是 FAIL（证据不达标）；
 * 把前者写成后者就是差距分析要消灭的假真理。取数失败的正当表达只有两种：
 * {@code absent(reason)}，或抛异常由 {@code ReadinessService} 统一折算成 {@code absent}。</p>
 *
 * <p>实现归属：只读自己域的表（如 {@code GoalCaliberMetricSource} 读 ecos_ai 自家 Goal），
 * 跨域取数必须走属主 Engine 的 REST 端点（ST07/铁律 §0.6，禁直连对方表）。</p>
 */
public interface ReadinessMetricSource {

    /** 是否为该检查项供数（一个源可服务多项，如 D 层健康体的 components）。 */
    boolean supports(ReadinessCheckCode code);

    /**
     * 取数。
     *
     * <p>实现不得返回 {@code null}（无值请返回 {@link MetricValue#absent(String)}）；
     * 不得抛业务异常来表达"没数据"——抛异常会被按取数故障折算，语义虽同为 absent，
     * 但会在日志里留下错误噪声，掩盖真正的引擎故障。</p>
     */
    MetricValue measure(ReadinessCheckCode code, ReadinessProbeContext context);

    /** 取证标识（落 {@code measured_json.source}，使每个度量值可追到供数面）。 */
    default String sourceId() {
        return getClass().getSimpleName();
    }
}
