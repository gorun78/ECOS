package com.chinacreator.gzcm.workspace.mapper;

import com.chinacreator.gzcm.workspace.scenario.BusinessScenario;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 场景主表 Mapper（V205 新表 {@code ecos_scenario_definition}，卷07 F07-19）。
 * <p>SQL 一律 schema 限定（{@code #{schema}} 由配置 {@code ecos.db.control-schema} 注入，禁硬编码 public.）；
 * 读侧统一软删过滤 {@code is_deleted = 0}。</p>
 */
public interface ScenarioMapper {

    List<BusinessScenario> selectList(@Param("schema") String schema);

    List<BusinessScenario> selectById(@Param("schema") String schema, @Param("id") String id);

    int countById(@Param("schema") String schema, @Param("id") String id);

    int insert(@Param("schema") String schema, Map<String, Object> row);

    int updateStatus(@Param("schema") String schema, @Param("id") String id,
                     @Param("status") String status, @Param("operator") String operator);

    int softDelete(@Param("schema") String schema, @Param("id") String id, @Param("operator") String operator);

    List<String> selectActiveWithoutIslandIds(@Param("schema") String schema);
}
