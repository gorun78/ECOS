package com.chinacreator.gzcm.workspace.scenario;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 正式运行禁止引用演练基线（卷07 F07-09 / C-7，唯一实现点，禁两处各写一份）。
 * <p>forecast-run 服务与 workspace run 创建两处均调用本守卫；实现仅此一处持有校验逻辑。
 * FORMAL 运行时若其 {@code baselineRunId} 指向的产物 {@code run_mode==SANDBOX} → 400 SANDBOX_REF_FORBIDDEN。</p>
 *
 * <p>run_mode 解析经 {@link ObjectProvider} 惰性获取（依赖 V209 {@code ecos_scenario_execution.run_mode} 列与
 * {@code ScenarioRunMapper}，属批次 C 的 Mapper/DDL 收口接缝）；解析器未装配时跳过（诚实缺省，
 * 待收口后自动生效），绝不因缺 bean 让上下文启动失败。</p>
 */
@Component
public class BaselineReferenceGuard {

    /** baselineRunId → 该运行的 run_mode 解析（读 run 表；由调用方注入以隔离 IO）。 */
    @FunctionalInterface
    public interface BaselineRunModeResolver {
        RunMode resolve(String baselineRunId);
    }

    private final ObjectProvider<BaselineRunModeResolver> resolverProvider;

    public BaselineReferenceGuard(ObjectProvider<BaselineRunModeResolver> resolverProvider) {
        this.resolverProvider = resolverProvider;
    }

    /** 直接以已知 baseline 模式校验（可测核心，无 IO）。 */
    public void assertAllowed(RunMode creatingMode, RunMode baselineMode, String baselineRunId) {
        if (creatingMode != RunMode.FORMAL) {
            return;
        }
        if (baselineRunId == null || baselineRunId.isBlank()) {
            return;
        }
        if (baselineMode == RunMode.SANDBOX) {
            throw new SandboxReferenceForbiddenException(baselineRunId);
        }
    }

    /** FORMAL 运行创建校验：解析 baseline run_mode，引用演练产物 → 抛 {@link SandboxReferenceForbiddenException}。 */
    public void assertAllowed(RunMode creatingMode, String baselineRunId) {
        if (creatingMode != RunMode.FORMAL || baselineRunId == null || baselineRunId.isBlank()) {
            return;
        }
        assertAllowed(creatingMode, resolveMode(baselineRunId), baselineRunId);
    }

    /** 唯一 run_mode 解析点：解析给定运行的模式；解析器未装配返回 null（诚实缺省）。 */
    public RunMode resolveMode(String runId) {
        if (runId == null || runId.isBlank()) {
            return null;
        }
        BaselineRunModeResolver resolver = resolverProvider.getIfAvailable();
        return resolver == null ? null : resolver.resolve(runId);
    }
}
