package com.chinacreator.gzcm.workspace.mapper;

import com.chinacreator.gzcm.workspace.scenario.BusinessScenario;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 场景主表 Mapper（V205 新表 {@code ecos_scenario_definition}，卷07 F07-19）。
 * <p>SQL 一律 schema 限定（{@code #{schema}} 由配置 {@code ecos.db.control-schema} 注入，禁硬编码 public.）；
 * 读侧统一软删过滤 {@code is_deleted = 0}。</p>
 *
 * <p><b>【#146f / D-1 裁决定档，2026-10-27】规划未启用的休眠腿</b>：本接口经
 * {@code WorkspaceServiceApplication} 的 {@code @MapperScan("...workspace.mapper")} 注册为 Bean，
 * 但全仓<b>生产零注入 / 零消费方</b>（Javadoc 仅指向未建成的"批次 C 收口接缝"），dev
 * {@code ecos_scenario_definition} 实查 0 行。SCENARIO 实体运行权威＝
 * {@code dao.WorkspaceDataDao}（JdbcTemplate DAO）读写的 {@code ecos_business_scenario}（dev 4 行）。
 * 本休眠腿<b>禁</b>在任何生产类接线；接线纪律由
 * {@code arch.ScenarioTableMapperLegDormancyGateTest} 构建期钉死（生产依赖本接口即红）。</p>
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
