package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import org.springframework.stereotype.Component;

/**
 * 演练基线 run_mode 解析器（详细设计-场景工作台 §5；F07-09 唯一 IO 点装配）。
 *
 * <p>读取 {@code ecos_scenario_run.run_mode}（V312 列）。查不到返回 null（诚实缺省），
 * 不把历史未知行伪造为 FORMAL；FORMAL 运行引用 SANDBOX 基线时由
 * {@link BaselineReferenceGuard} 抛 {@link SandboxReferenceForbiddenException}。</p>
 */
@Component
public class WorkspaceBaselineRunModeResolver implements BaselineReferenceGuard.BaselineRunModeResolver {

    private final WorkspaceDataDao dao;

    public WorkspaceBaselineRunModeResolver(WorkspaceDataDao dao) {
        this.dao = dao;
    }

    @Override
    public RunMode resolve(String baselineRunId) {
        return RunMode.fromNullable(dao.findRunMode(baselineRunId));
    }
}
