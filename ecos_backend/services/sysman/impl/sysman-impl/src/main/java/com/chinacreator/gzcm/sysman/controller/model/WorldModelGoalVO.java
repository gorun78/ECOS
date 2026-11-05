package com.chinacreator.gzcm.sysman.controller.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * World Model 目标行（DB 路径与内存兜底路径共用）。
 *
 * <p><b>键面冻结、值类型不冻结</b>：两条来源同键不同型，机械强转会改 wire ——</p>
 * <ul>
 *   <li>{@code id}/{@code parentId}：DB 为 {@code Long}（{@code ecos_wm_goal.id} 数值主键），
 *       内存兜底为业务字符串（如 {@code root-goal}）；</li>
 *   <li>{@code createdAt}/{@code updatedAt}：DB 为 ISO 字符串（DAO 侧 {@code toLocalDateTime()}），
 *       内存兜底为毫秒 {@code Long}；</li>
 *   <li>{@code startDate}/{@code endDate}：DB 为 {@code java.sql.Date}，内存为 {@code LocalDate}；</li>
 *   <li>{@code weight}/{@code targetValue}/{@code currentValue}/{@code alertThreshold*}：
 *       DB 侧 {@code getObject/getBigDecimal} 与实体侧数值并存，统一按 {@code Object} 透传。</li>
 * </ul>
 *
 * <p>键序：id/name/description/parentId/progress/status/goalType/weight/orgId/ownerUserId/
 * startDate/endDate/targetValue/currentValue/unit/linkedWorkflowId/kpiFormula/measureFrequency/
 * alertThresholdWarn/alertThresholdCritical/createdAt/updatedAt[/children]。</p>
 */
public record WorldModelGoalVO(
        Object id,
        String name,
        String description,
        Object parentId,
        Integer progress,
        String status,
        String goalType,
        Object weight,
        String orgId,
        String ownerUserId,
        Object startDate,
        Object endDate,
        Object targetValue,
        Object currentValue,
        String unit,
        String linkedWorkflowId,
        String kpiFormula,
        String measureFrequency,
        Object alertThresholdWarn,
        Object alertThresholdCritical,
        Object createdAt,
        Object updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<WorldModelGoalVO> children) {

    /** DB 行 → VO（键面与值型按 DAO 落 Map 原样承接）。 */
    public static WorldModelGoalVO ofRow(Map<String, Object> row) {
        return new WorldModelGoalVO(
                row.get("id"),
                (String) row.get("name"),
                (String) row.get("description"),
                row.get("parentId"),
                asInteger(row.get("progress")),
                (String) row.get("status"),
                (String) row.get("goalType"),
                row.get("weight"),
                (String) row.get("orgId"),
                (String) row.get("ownerUserId"),
                row.get("startDate"),
                row.get("endDate"),
                row.get("targetValue"),
                row.get("currentValue"),
                (String) row.get("unit"),
                (String) row.get("linkedWorkflowId"),
                (String) row.get("kpiFormula"),
                (String) row.get("measureFrequency"),
                row.get("alertThresholdWarn"),
                row.get("alertThresholdCritical"),
                row.get("createdAt"),
                row.get("updatedAt"),
                null);
    }

    /** 内存兜底实体 → VO（字符串 id/parentId、毫秒时间戳原样透出）。 */
    public static WorldModelGoalVO ofEntity(GoalEntity g) {
        return new WorldModelGoalVO(
                g.getId(),
                g.getName(),
                g.getDescription(),
                g.getParentId(),
                g.getProgress(),
                g.getStatus(),
                g.getGoalType(),
                g.getWeight(),
                g.getOrgId(),
                g.getOwnerUserId(),
                g.getStartDate(),
                g.getEndDate(),
                g.getTargetValue(),
                g.getCurrentValue(),
                g.getUnit(),
                g.getLinkedWorkflowId(),
                g.getKpiFormula(),
                g.getMeasureFrequency(),
                g.getAlertThresholdWarn(),
                g.getAlertThresholdCritical(),
                g.getCreatedAt(),
                g.getUpdatedAt(),
                null);
    }

    /** 树形：返回带 children 的副本（children 仅在非空时出现）。 */
    public WorldModelGoalVO withChildren(List<WorldModelGoalVO> childNodes) {
        return new WorldModelGoalVO(id, name, description, parentId, progress, status, goalType,
                weight, orgId, ownerUserId, startDate, endDate, targetValue, currentValue, unit,
                linkedWorkflowId, kpiFormula, measureFrequency, alertThresholdWarn,
                alertThresholdCritical, createdAt, updatedAt, childNodes);
    }

    private static Integer asInteger(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }
}
