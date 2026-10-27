package com.chinacreator.gzcm.workspace.mapper;

import com.chinacreator.gzcm.workspace.scenario.ScenarioBinding;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 绑定表 Mapper（V206 {@code ecos_scenario_asset_binding}，含 is_island/island_reason，卷07 F07-19）。
 *
 * <p><b>【#146f / D-1 裁决定档，2026-10-27】规划未启用的休眠腿</b>：与 {@code ScenarioMapper} 同族，
 * 经 {@code @MapperScan("...workspace.mapper")} 注册但全仓生产零注入零消费方，dev
 * {@code ecos_scenario_asset_binding} 实查 0 行；运行权威＝{@code dao.WorkspaceDataDao} 读写的
 * {@code ecos_scenario_binding}。本腿<b>禁</b>在任何生产类接线，纪律由
 * {@code arch.ScenarioTableMapperLegDormancyGateTest} 构建期钉死。</p>
 */
public interface ScenarioBindingMapper {

    List<ScenarioBinding> selectByScenario(@Param("schema") String schema, @Param("scenarioId") String scenarioId);

    int insert(@Param("schema") String schema, Map<String, Object> row);

    int softDeleteByScenario(@Param("schema") String schema, @Param("scenarioId") String scenarioId,
                             @Param("operator") String operator);

    /** 岛标写回（幂等，读路径独立事务，F07-04）。 */
    int markIsland(@Param("schema") String schema, @Param("bindingId") String bindingId,
                   @Param("isIsland") boolean isIsland, @Param("islandReason") String islandReason);

    int batchClearIslands(@Param("schema") String schema, @Param("scenarioId") String scenarioId);
}
