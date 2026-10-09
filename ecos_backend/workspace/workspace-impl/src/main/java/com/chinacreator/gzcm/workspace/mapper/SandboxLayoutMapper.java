package com.chinacreator.gzcm.workspace.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 沙盘布局表 Mapper（V210 {@code ecos_scenario_canvas_layout}，乐观锁 layout_version + version_no，卷07 F07-19）。 */
public interface SandboxLayoutMapper {

    List<Map<String, Object>> selectByScenario(@Param("schema") String schema, @Param("scenarioId") String scenarioId);

    /** 乐观锁更新：仅当 layout_version 命中期望值才写回，返回 0 表示冲突（F07-13 LAYOUT_CONFLICT）。 */
    int updateWithVersion(@Param("schema") String schema, Map<String, Object> row);

    int insert(@Param("schema") String schema, Map<String, Object> row);
}
