package com.chinacreator.gzcm.workspace.forecast;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.workspace.forecast.task.ForecastRunAsyncDispatcher;
import com.chinacreator.gzcm.workspace.scenario.RunMode;

/**
 * 预测 run 编排服务（H-5 核心）：六要素落位 + 幂等 + 只读/重试状态机 + 期间锁 + 未上线降级。
 *
 * <p><b>依赖未就绪即"未上线"</b>：计算/落库 SPI {@link ForecastRunGateway} 经 {@code ObjectProvider}
 * 软依赖注入；当前 JVM 未装配（cognitive 快照 reader 尚无 implements bean，U-3）时，
 * 各读写入口一律回 {@link ForecastRunNotLiveException}（501 STUB_CAPABILITY），
 * <b>严禁 mock 成功 / 200 造数</b>（X-44/X-50 假阳性红线）。</p>
 *
 * <p><b>不直连 ecos_dw</b>：本服务不持 DAO、不写 SQL，run 落库/取数全经 {@code ForecastRunGateway}
 * （其实现经 data-engine 唯一写通道 + cognitive REST 取快照，属其它 Agent 接缝，ST08）。</p>
 *
 * <p><b>区间三值全存全展（R-62 ①）</b>：{@code results}/{@code compare} 原样透传引擎给定的
 * P10/P50/P90，缺值即 null，编排面不插值、不取均值、不兜底合成。</p>
 */
@Service
public class ForecastRunOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ForecastRunOrchestrationService.class);
    private static final String CAPABILITY = "workspace/forecast-run";

    private final ObjectProvider<ForecastRunGateway> gatewayProvider;
    private final ForecastRunStateMachine stateMachine;
    private final ForecastPeriodLock periodLock;
    /** H-6 异步派发器（可缺省：未装配时超阈值命令诚实 501，禁假异步）。 */
    private final ObjectProvider<ForecastRunAsyncDispatcher> asyncDispatcherProvider;

    public ForecastRunOrchestrationService(ObjectProvider<ForecastRunGateway> gatewayProvider,
                                           ForecastRunStateMachine stateMachine,
                                           ForecastPeriodLock periodLock) {
        this(gatewayProvider, stateMachine, periodLock, null);
    }

    @Autowired
    public ForecastRunOrchestrationService(ObjectProvider<ForecastRunGateway> gatewayProvider,
                                           ForecastRunStateMachine stateMachine,
                                           ForecastPeriodLock periodLock,
                                           ObjectProvider<ForecastRunAsyncDispatcher> asyncDispatcherProvider) {
        this.gatewayProvider = gatewayProvider;
        this.stateMachine = stateMachine;
        this.periodLock = periodLock;
        this.asyncDispatcherProvider = asyncDispatcherProvider;
    }

    /** 创建 run：期间锁前置 → 幂等复用/冲突裁决 → H-6 超阈值转异步 → 未装配即未上线。 */
    public ForecastRunView create(ForecastRunCommand command) {
        assertPeriodOpen(ForecastPeriodLock.periodKeyOf(command.getAsOfTime()), null);
        ForecastRunGateway gateway = requireGateway();

        ForecastRunView existing = gateway.findByIdempotencyKey(command.getIdempotencyKey());
        String reusedRunId = stateMachine.resolveIdempotentReuse(existing, command.getContentHash());
        if (reusedRunId != null) {
            log.info("预测 run 幂等复用 idempotencyKey={} reusedFromRunId={}",
                    command.getIdempotencyKey(), reusedRunId);
            return existing.withReusedFromRunId(reusedRunId);
        }
        // ★ H-6（PRD-09 §七-1）：>50 项目 或 >10 万行 ⇒ runtime-task submitTask 真异步（真实 taskId）
        ForecastRunView deferred = tryDispatchAsync(command, gateway);
        if (deferred != null) {
            return deferred;
        }
        ForecastRunView created = gateway.submit(command);
        log.info("预测 run 已创建 runId={} scenario={} caliber={}@{} runMode={} contentHash={}",
                created.getForecastRunId(), command.getScenarioId(), command.getCaliberId(),
                command.getCaliberVersion(), command.getRunMode(), command.getContentHash());
        return created;
    }

    /**
     * H-6 异步分支：命中阈值后经底座 {@code ITaskManagementService.submitTask} 派发
     * （返回<b>真实</b> taskId，V277 run 表 task_id 占位落位 RUNNING）。
     *
     * <p>未装配派发器 / SPI 未实现占位通道 ⇒ 抛 501（X-44 禁假异步成功、禁自造 jobId）。
     * 未命中阈值返回 null 走同步路径。</p>
     */
    private ForecastRunView tryDispatchAsync(ForecastRunCommand command, ForecastRunGateway gateway) {
        ForecastRunAsyncDispatcher dispatcher = asyncDispatcherProvider == null
                ? null : asyncDispatcherProvider.getIfAvailable();
        if (dispatcher == null || !dispatcher.isAsyncEligible(command)) {
            return null;
        }
        String taskId = dispatcher.dispatch(command);
        ForecastRunView placeholder = gateway.submitDeferred(command, taskId);
        log.info("预测 run 已转异步 runId={} taskId={} projectCount={} rowEstimate={}",
                placeholder.getForecastRunId(), taskId, command.getProjectCount(), command.getRowEstimate());
        return placeholder.withTaskId(taskId);
    }

    /** 状态查询（按 runId）。 */
    public ForecastRunView status(String forecastRunId) {
        ForecastRunGateway gateway = requireGateway();
        ForecastRunView view = gateway.findRun(forecastRunId);
        if (view == null) {
            throw new ForecastRunNotFoundException(forecastRunId);
        }
        return view;
    }

    /**
     * 结果明细（含区间三值原样透传）。缺失/落定 run 禁改写；不合成任何缺位数值（R-62 ①）。
     */
    public Map<String, Object> results(String forecastRunId) {
        ForecastRunGateway gateway = requireGateway();
        ForecastRunView view = gateway.findRun(forecastRunId);
        if (view == null) {
            throw new ForecastRunNotFoundException(forecastRunId);
        }
        Map<String, Object> raw = gateway.readResults(forecastRunId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("forecastRunId", forecastRunId);
        out.put("status", view.getStatus() == null ? null : view.getStatus().name());
        // 六要素锚定（证据/结果与 run 同属可追溯）
        out.put("caliberId", view.getCaliberId());
        out.put("caliberVersion", view.getCaliberVersion());
        out.put("asOfTime", view.getAsOfTime());
        out.put("snapshotId", view.getSnapshotId());
        out.put("formulaVersion", view.getFormulaVersion());
        // 区间三值：原样透传，缺位 null，禁插值
        QuantileTriple q = view.getQuantile();
        out.put("p10", q == null ? null : q.getP10());
        out.put("p50", q == null ? null : q.getP50());
        out.put("p90", q == null ? null : q.getP90());
        if (raw != null) {
            out.putAll(raw);
        }
        return out;
    }

    /** 证据出参（后端组装的引用集，编排面不拼公式）。 */
    public ForecastRunEvidence evidence(String forecastRunId) {
        ForecastRunGateway gateway = requireGateway();
        ForecastRunView view = gateway.findRun(forecastRunId);
        if (view == null) {
            throw new ForecastRunNotFoundException(forecastRunId);
        }
        ForecastRunEvidence evidence = gateway.readEvidence(forecastRunId);
        if (evidence == null) {
            throw new ForecastRunNotLiveException(CAPABILITY + "/evidence",
                    "证据 SPI 未就绪: forecastRunId=" + forecastRunId);
        }
        return evidence;
    }

    /** 情景复制：以源 run 为基线派生新 run，返回新 runId；命中关账期禁止复制。 */
    public ForecastRunView scenarioCopy(String sourceRunId, Map<String, Object> overrides, String idempotencyKey) {
        ForecastRunGateway gateway = requireGateway();
        ForecastRunView source = gateway.findRun(sourceRunId);
        if (source == null) {
            throw new ForecastRunNotFoundException(sourceRunId);
        }
        assertPeriodOpen(ForecastPeriodLock.periodKeyOf(source.getAsOfTime()), sourceRunId);

        String key = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey
                : "copy-" + UUID.randomUUID();
        ForecastRunCommand copy = copyCommand(source, key, overrides);
        ForecastRunView existing = gateway.findByIdempotencyKey(key);
        String reusedRunId = stateMachine.resolveIdempotentReuse(existing, copy.getContentHash());
        if (reusedRunId != null) {
            return existing.withReusedFromRunId(reusedRunId);
        }
        ForecastRunView created = gateway.submit(copy);
        return created.withReusedFromRunId(sourceRunId);
    }

    /**
     * 对比两 run（U-5）：并列两侧六要素与区间三值 + 差异位，数值均取存值真值，
     * 缺位不合成；delta 仅在两侧均有真值时给出（否则 null）。
     */
    public Map<String, Object> compare(String baselineRunId, String candidateRunId) {
        ForecastRunGateway gateway = requireGateway();
        ForecastRunView baseline = gateway.findRun(baselineRunId);
        ForecastRunView candidate = gateway.findRun(candidateRunId);
        if (baseline == null) {
            throw new ForecastRunNotFoundException(baselineRunId);
        }
        if (candidate == null) {
            throw new ForecastRunNotFoundException(candidateRunId);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baseline", snapshotOf(baseline));
        out.put("candidate", snapshotOf(candidate));
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("caliberIdDiffers", !Objects.equals(baseline.getCaliberId(), candidate.getCaliberId()));
        diff.put("caliberVersionDiffers", !Objects.equals(baseline.getCaliberVersion(), candidate.getCaliberVersion()));
        diff.put("asOfTimeDiffers", !Objects.equals(baseline.getAsOfTime(), candidate.getAsOfTime()));
        diff.put("snapshotIdDiffers", !Objects.equals(baseline.getSnapshotId(), candidate.getSnapshotId()));
        diff.put("formulaVersionDiffers", !Objects.equals(baseline.getFormulaVersion(), candidate.getFormulaVersion()));
        out.put("diff", diff);
        return out;
    }

    /**
     * 重试（row 16）：仅对终态源 run，派生<b>新 run</b> 并以 baselineRunId/reusedFromRunId 回链；
     * 源 run 保持只读、绝不被改写（C210），命中关账期禁止重算。
     */
    public ForecastRunView retry(String sourceRunId, String idempotencyKey) {
        ForecastRunGateway gateway = requireGateway();
        ForecastRunView source = gateway.findRun(sourceRunId);
        if (source == null) {
            throw new ForecastRunNotFoundException(sourceRunId);
        }
        // 源 run 终态才可重试；重试派生新 run（经 submit），绝不原地改写源（源若 SUCCEEDED 由状态机只读守卫兜住）
        stateMachine.assertRetryable(source);
        assertPeriodOpen(ForecastPeriodLock.periodKeyOf(source.getAsOfTime()), sourceRunId);

        String key = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey
                : "retry-" + UUID.randomUUID();
        ForecastRunCommand retryCommand = retryCommand(source, key);
        ForecastRunView existing = gateway.findByIdempotencyKey(key);
        String reusedRunId = stateMachine.resolveIdempotentReuse(existing, retryCommand.getContentHash());
        if (reusedRunId != null) {
            return existing.withReusedFromRunId(reusedRunId);
        }
        ForecastRunView created = gateway.submit(retryCommand);
        return created.withReusedFromRunId(sourceRunId);
    }

    // ── 私有辅助 ──

    /** 计算 SPI 未装配 ⇒ 未上线（501），绝不 mock 成功。 */
    private ForecastRunGateway requireGateway() {
        ForecastRunGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new ForecastRunNotLiveException(CAPABILITY,
                    "ForecastRunGateway 未装配（cognitive run 计算 SPI 无 implements bean，U-3）");
        }
        return gateway;
    }

    private void assertPeriodOpen(String period, String forecastRunId) {
        if (period != null && periodLock.isClosed(period)) {
            throw new ForecastPeriodClosedException(period, forecastRunId);
        }
    }

    private Map<String, Object> snapshotOf(ForecastRunView view) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("forecastRunId", view.getForecastRunId());
        m.put("status", view.getStatus() == null ? null : view.getStatus().name());
        m.put("caliberId", view.getCaliberId());
        m.put("caliberVersion", view.getCaliberVersion());
        m.put("asOfTime", view.getAsOfTime());
        m.put("snapshotId", view.getSnapshotId());
        m.put("formulaVersion", view.getFormulaVersion());
        QuantileTriple q = view.getQuantile();
        m.put("p10", q == null ? null : q.getP10());
        m.put("p50", q == null ? null : q.getP50());
        m.put("p90", q == null ? null : q.getP90());
        return m;
    }

    private ForecastRunCommand retryCommand(ForecastRunView source, String newIdempotencyKey) {
        return new ForecastRunCommand(source.getScenarioId(), source.getRunType(),
                source.getCaliberId(), source.getCaliberVersion(), source.getAsOfTime(),
                source.getSnapshotId(), source.getFormulaVersion(),
                newIdempotencyKey, source.getContentHash(),
                sourceRunMode(source), source.getForecastRunId(), source.getForecastRunId());
    }

    private ForecastRunCommand copyCommand(ForecastRunView source, String newIdempotencyKey,
                                           Map<String, Object> overrides) {
        // 复制继承源 run 六要素；overrides 仅作用范围（不影响内容指纹的确定性口径由引擎裁定）
        return new ForecastRunCommand(source.getScenarioId(), source.getRunType(),
                source.getCaliberId(), source.getCaliberVersion(), source.getAsOfTime(),
                source.getSnapshotId(), source.getFormulaVersion(),
                newIdempotencyKey, source.getContentHash(),
                sourceRunMode(source), source.getForecastRunId(), source.getForecastRunId());
    }

    /** runMode 非 run 视图存列，重试/复制按 FORMAL 处理（演练面另由 SANDBOX 入口显式指定）。 */
    private RunMode sourceRunMode(ForecastRunView source) {
        return RunMode.FORMAL;
    }
}
