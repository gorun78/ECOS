package com.chinacreator.gzcm.workspace.mapper;

import com.chinacreator.gzcm.workspace.scenario.LinkEdge;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 连边表 Mapper（V207 {@code ecos_scenario_binding_edge}，卷07 F07-19）。 */
public interface BindingLinkMapper {

    List<LinkEdge> selectByScenario(@Param("schema") String schema, @Param("scenarioId") String scenarioId);

    /** 完整度对账用只读计数（REQ-WS-02 §2.5-4，禁写入）。 */
    int countByScenarioAndType(@Param("schema") String schema, @Param("scenarioId") String scenarioId,
                               @Param("linkType") String linkType);
}
