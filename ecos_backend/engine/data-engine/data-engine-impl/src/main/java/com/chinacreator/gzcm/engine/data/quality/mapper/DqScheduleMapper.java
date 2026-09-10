package com.chinacreator.gzcm.engine.data.quality.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import com.chinacreator.gzcm.engine.data.quality.model.DqScheduleVO;

/**
 * DQ 监控调度 MyBatis Mapper — 读写 {@code ecos_dq.dq_schedule} / {@code ecos_dq.dq_throttle}（PMO-48-C T11）。
 *
 * <p>跨 schema 查询显式 {@code ecos_dq.} 前缀（同 DqRuleMapper 先例）；
 * {@code rule_ids} JSONB 列读出为原始 JSON 字符串，由 Service 层反序列化。</p>
 *
 * @author PMO-48-C T11
 */
@Mapper
public interface DqScheduleMapper {

    /** dq_schedule 列表查询列（rule_ids 读原始 JSON，Service 层反序列化为 List） */
    String COLUMNS = "id, name, trigger_type AS triggerType, cron_expression AS cronExpression, "
            + "event_type AS eventType, rule_ids::text AS ruleIdsJson, "
            + "scope_type AS scopeType, scope_id AS scopeId, enabled, "
            + "max_runtime_seconds AS maxRuntimeSeconds, created_by AS createdBy, "
            + "created_at AS createdAt, updated_at AS updatedAt";

    /**
     * 查询所有启用的调度计划（调度器/EVENT 监听共用）。
     *
     * @return 启用计划列表（is_deleted=FALSE AND enabled=TRUE）
     */
    @Select("SELECT " + COLUMNS + " FROM ecos_dq.dq_schedule "
            + "WHERE enabled = TRUE AND is_deleted = FALSE ORDER BY created_at DESC")
    List<DqScheduleVO> listEnabled();

    /**
     * 查询全部调度计划（含停用，管理列表用）。
     *
     * @return 计划列表（排除已逻辑删除）
     */
    @Select("SELECT " + COLUMNS + " FROM ecos_dq.dq_schedule "
            + "WHERE is_deleted = FALSE ORDER BY created_at DESC")
    List<DqScheduleVO> listAll();

    /**
     * 按 ID 查调度计划详情。
     *
     * @param id 调度 ID
     * @return 计划 VO；不存在返回 null
     */
    @Select("SELECT " + COLUMNS + " FROM ecos_dq.dq_schedule WHERE id = #{id} AND is_deleted = FALSE")
    DqScheduleVO findById(@Param("id") String id);

    /**
     * 按 ID 查调度计划（含停用，runRuleBatch 入口用，逻辑删除的不可执行）。
     *
     * @param id 调度 ID
     * @return 计划 VO；不存在或已删除返回 null
     */
    @Select("SELECT " + COLUMNS + " FROM ecos_dq.dq_schedule WHERE id = #{id} AND is_deleted = FALSE")
    DqScheduleVO findByIdForRun(@Param("id") String id);

    /**
     * 插入调度计划。
     *
     * @param id        调度 ID（Service 生成 UUID）
     * @param vo        计划属性（ruleIdsJson 为 JSON 数组字符串）
     * @param createdBy 创建人
     * @return 影响行数
     */
    @Insert("INSERT INTO ecos_dq.dq_schedule"
            + " (id, name, trigger_type, cron_expression, event_type, rule_ids, scope_type, scope_id, enabled,"
            + "  max_runtime_seconds, created_by)"
            + " VALUES (#{id}, #{vo.name}, #{vo.triggerType}, #{vo.cronExpression}, #{vo.eventType},"
            + "  #{vo.ruleIdsJson}::jsonb, #{vo.scopeType}, #{vo.scopeId}, #{vo.enabled}, "
            + "  #{vo.maxRuntimeSeconds}, #{createdBy})")
    int insert(@Param("id") String id, @Param("vo") DqScheduleVO vo, @Param("createdBy") String createdBy);

    /**
     * 更新调度计划（全字段覆盖 — 调用方先 get 再合并非 null 字段）。
     *
     * @param id 调度 ID
     * @param vo 完整计划属性
     * @return 影响行数
     */
    @Update("UPDATE ecos_dq.dq_schedule SET"
            + " name = #{vo.name},"
            + " trigger_type = #{vo.triggerType},"
            + " cron_expression = #{vo.cronExpression},"
            + " event_type = #{vo.eventType},"
            + " rule_ids = #{vo.ruleIdsJson}::jsonb,"
            + " scope_type = #{vo.scopeType},"
            + " scope_id = #{vo.scopeId},"
            + " enabled = #{vo.enabled},"
            + " max_runtime_seconds = #{vo.maxRuntimeSeconds},"
            + " updated_at = NOW() WHERE id = #{id} AND is_deleted = FALSE")
    int update(@Param("id") String id, @Param("vo") DqScheduleVO vo);

    /**
     * 逻辑删除调度计划。
     *
     * @param id 调度 ID
     * @return 影响行数
     */
    @Update("UPDATE ecos_dq.dq_schedule SET is_deleted = TRUE, updated_at = NOW() WHERE id = #{id}")
    int logicDelete(@Param("id") String id);

    /**
     * 按规则 ID 列表统计当日已执行检查数（限流判定）。
     *
     * @param ruleIds 规则 ID 列表（由 Service 展开 schedule.rule_ids 后传入）
     * @return 当日 dq_rule_check 行数
     */
    @SelectProvider(type = DqScheduleSqlProvider.class, method = "countChecksToday")
    int countChecksToday(@Param("ruleIds") List<String> ruleIds);

    /**
     * 查限流行当前状态（上限读取 + 跨日重置判定）。
     *
     * @param scopeType 作用域类型
     * @param scopeId   作用域 ID
     * @return 限流行；未配置过返回 null
     */
    @Select("SELECT scope_type AS scopeType, scope_id AS scopeId, max_checks_per_day AS maxPerDay,"
            + " current_count AS currentCount, reset_at AS resetAt"
            + " FROM ecos_dq.dq_throttle WHERE scope_type = #{scopeType} AND scope_id = #{scopeId}")
    DqThrottleRow findThrottle(@Param("scopeType") String scopeType, @Param("scopeId") String scopeId);

    /**
     * 限流计数自增（幂等 upsert）：
     * 行不存在 → 插入 current_count=1；行存在跨日 → 重置为 1；行存在当日 → +1。
     *
     * @param id         限流行 ID（scopeType:scopeId，与表 PK 对齐）
     * @param scopeType  作用域类型
     * @param scopeId    作用域 ID
     * @param maxPerDay  每日上限（无配置行时按此初始化，默认 100）
     * @return 影响行数
     */
    @Insert("INSERT INTO ecos_dq.dq_throttle"
            + " (id, scope_type, scope_id, max_checks_per_day, current_count, reset_at, updated_at)"
            + " VALUES (#{id}, #{scopeType}, #{scopeId}, #{maxPerDay}, 1, NOW(), NOW())"
            + " ON CONFLICT (scope_type, scope_id) DO UPDATE SET"
            + "   current_count = CASE WHEN dq_throttle.reset_at >= CURRENT_DATE"
            + "     THEN dq_throttle.current_count + 1 ELSE 1 END,"
            + "   reset_at = CASE WHEN dq_throttle.reset_at >= CURRENT_DATE"
            + "     THEN dq_throttle.reset_at ELSE NOW() END,"
            + "   updated_at = NOW()")
    int incrementThrottle(@Param("id") String id,
                          @Param("scopeType") String scopeType,
                          @Param("scopeId") String scopeId,
                          @Param("maxPerDay") int maxPerDay);
}
