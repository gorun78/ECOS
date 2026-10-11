package com.chinacreator.gzcm.workspace.forecast;

import java.util.Map;

/**
 * 预测 run 计算/证据 SPI 抽象（H-5，workspace-本地契约，形态对齐 {@code DecisionGateway}）。
 *
 * <p>依赖铁律：workspace 不得直连 {@code ecos_dw}，也不得 import 引擎实现
 * （{@code WorkspaceLayeringArchTest} 分层门禁）。计算/落库/取快照经本抽象，
 * 由 cognitive 引擎（同 JVM 宿主可注入 Local bean，否则经 {@code WorkspaceRestClientConfig}
 * 共享 RestTemplate 走 cognitive REST 的适配实现）落地；<b>具体实现属其它 Agent 的接缝，
 * 本包只定义契约 + 编排</b>。装配缺失时经 {@code ObjectProvider} 软依赖判定 → 返回"未上线"语义
 * （501 STUB_CAPABILITY / 503 DEPENDENCY_MISSING），<b>严禁 mock 成功或 200 造数</b>（X-44/X-50）。</p>
 */
public interface ForecastRunGateway {

    /**
     * 计算并落一次预测 run：写六要素 + 幂等 + 区间三值 + 证据引用，返回落定后的 run 视图。
     * 幂等复用/冲突判定在上层 Service 完成后才调用本方法（本方法只负责真算真落）。
     *
     * @param command 规范化命令（六要素 + 幂等 + runMode + baselineRunId）
     * @param runMode 正交运行模式（透传，禁与 run_type 混列）
     */
    ForecastRunView submit(ForecastRunCommand command);

    /** 按 runId 读取既有 run 状态（不存在返回 null，由 Service 转 404/未上线语义）。 */
    ForecastRunView findRun(String forecastRunId);

    /**
     * 幂等查询：按 {@code idempotencyKey} 命中既有 run 返回其视图，未命中返回 null。
     * 命中后的"同 hash 复用 / 异 hash 冲突"裁决由 Service 经状态机完成（本方法只取数，不判定）。
     */
    ForecastRunView findByIdempotencyKey(String idempotencyKey);

    /**
     * 读取 run 的计算结果明细（含区间三值 P10/P50/P90 原样透传，禁插值合成 R-62 ①）。
     * 返回结构由引擎给定，编排面只搬运不改写。
     */
    Map<String, Object> readResults(String forecastRunId);

    /** 读取 run 的证据引用集（后端组装，编排面不拼公式）。 */
    ForecastRunEvidence readEvidence(String forecastRunId);

    /** 情景复制：以源 run 为基线复制出新 run，返回新 runId（R-62/反事实演练面）。 */
    String copyScenario(String sourceRunId, Map<String, Object> overrides);
}
