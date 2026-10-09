package com.chinacreator.gzcm.workspace.mapper;

import com.chinacreator.gzcm.workspace.scenario.ScenarioBinding;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 绑定表 Mapper（V206 {@code ecos_scenario_asset_binding}，含 is_island/island_reason，卷07 F07-19）。 */
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
