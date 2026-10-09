package com.chinacreator.gzcm.workspace.mapper;

import com.chinacreator.gzcm.workspace.scenario.RunMode;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 运行表 Mapper（V209 {@code ecos_scenario_execution}，含 run_mode 正交列，卷07 F07-19）。 */
public interface ScenarioRunMapper {

    int insert(@Param("schema") String schema, Map<String, Object> row);

    List<Map<String, Object>> selectByScenario(@Param("schema") String schema, @Param("scenarioId") String scenarioId);

    /** FORMAL 运行存在性（COMPLETED 迁移前置，C-2）。 */
    int countFormalRuns(@Param("schema") String schema, @Param("scenarioId") String scenarioId);

    /** baselineRunId → run_mode 解析（F07-09 守卫唯一读点）。 */
    String selectRunModeById(@Param("schema") String schema, @Param("runId") String runId);

    default RunMode resolveRunMode(String schema, String runId) {
        return RunMode.fromNullable(selectRunModeById(schema, runId));
    }
}
