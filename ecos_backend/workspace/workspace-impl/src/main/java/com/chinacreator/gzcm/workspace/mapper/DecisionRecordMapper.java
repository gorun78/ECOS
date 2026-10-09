package com.chinacreator.gzcm.workspace.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 决策记录表 Mapper（V211 {@code ecos_scenario_decision_record}，五必填成实列，卷07 F07-11/F07-19）。
 * <p>owner_user_id / due_date / approver_user_id / expected_impact / control_metric 均为真实列，可直接 WHERE/统计（证伪 JSON 逃避）。</p>
 */
public interface DecisionRecordMapper {

    int insert(@Param("schema") String schema, Map<String, Object> row);

    List<Map<String, Object>> selectByScenario(@Param("schema") String schema, @Param("scenarioId") String scenarioId);

    Map<String, Object> selectById(@Param("schema") String schema, @Param("decisionId") String decisionId);

    /** 五必填 SQL 可查询性对账（DecisionRecordRoundTripTest，只读）。 */
    List<Map<String, Object>> selectMandatoryColumns(@Param("schema") String schema, @Param("decisionId") String decisionId);
}
