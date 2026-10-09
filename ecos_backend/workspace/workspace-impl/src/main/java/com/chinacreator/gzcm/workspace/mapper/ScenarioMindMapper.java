package com.chinacreator.gzcm.workspace.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 心智表 Mapper（V208 {@code ecos_scenario_mind_variant}，卷07 F07-19）。 */
public interface ScenarioMindMapper {

    List<Map<String, Object>> selectByScenario(@Param("schema") String schema, @Param("scenarioId") String scenarioId);

    Map<String, Object> selectById(@Param("schema") String schema, @Param("mindId") String mindId);
}
