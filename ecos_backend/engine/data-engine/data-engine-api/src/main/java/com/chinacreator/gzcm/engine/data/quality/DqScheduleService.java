package com.chinacreator.gzcm.engine.data.quality;

import java.util.List;

import com.chinacreator.gzcm.engine.data.quality.model.DqScheduleDTO;
import com.chinacreator.gzcm.engine.data.quality.model.DqScheduleVO;

/**
 * DQ 监控调度服务契约（PMO-48-C T11）。
 *
 * <p>核心能力：</p>
 * <ol>
 *   <li>Schedule CRUD（create/update/delete 逻辑删/list/get）</li>
 *   <li>{@link #triggerManual} — 手动触发一次规则批执行（trigger_type=MANUAL）</li>
 *   <li>{@link #runRuleBatch} — 核心批执行：拉 ACTIVE 规则 → 限流检查 →
 *       逐条评估写 dq_rule_check → 重算资产评分（复用 T8 DqScoreService）</li>
 * </ol>
 *
 * <p>三种触发源统一收敛到 {@code runRuleBatch}：</p>
 * <ul>
 *   <li>SCHEDULE — runtime-task 注册的 {@code DqScheduledTask} 每天 08:00 遍历 enabled 计划</li>
 *   <li>EVENT    — {@code DqPipelineEventListener} 监听 PIPELINE_EXECUTION_SUCCEEDED</li>
 *   <li>MANUAL   — {@code POST /api/v1/dq/schedules/{id}/trigger}</li>
 * </ul>
 *
 * <p>安全卡（铁律 2.4）：每批执行完异步 {@code auditWrite(DQ_SCHEDULE_RUN, ...)}。</p>
 *
 * <p>Bean 名 {@code ecosDqScheduleService}（铁律 1.3 防多 Bean 冲突，不 implements 既有接口）。</p>
 *
 * @author PMO-48-C T11
 */
public interface DqScheduleService {

    /**
     * 创建调度计划。
     *
     * @param dto 入参（name/triggerType/ruleIds 必填）
     * @return 新调度 ID（VARCHAR(64)）
     */
    String createSchedule(DqScheduleDTO dto);

    /**
     * 更新调度计划（仅非 null 字段覆盖）。
     *
     * @param id  调度 ID
     * @param dto 待更新字段
     */
    void updateSchedule(String id, DqScheduleDTO dto);

    /**
     * 逻辑删除调度计划（is_deleted=TRUE，不物理删）。
     *
     * @param id 调度 ID
     */
    void deleteSchedule(String id);

    /**
     * 调度计划列表（含未删除，按创建时间倒序）。
     *
     * @return 计划列表
     */
    List<DqScheduleVO> listSchedules();

    /**
     * 调度计划详情。
     *
     * @param id 调度 ID
     * @return 计划 VO；不存在时返回 null
     */
    DqScheduleVO getSchedule(String id);

    /**
     * 手动触发一次规则批执行。
     *
     * @param scheduleId 调度 ID
     * @param triggerBy  触发人（审计留痕）
     */
    void triggerManual(String scheduleId, String triggerBy);

    /**
     * 执行一批规则（核心方法，三种触发源共用）。
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>取 schedule 关联的 ACTIVE 规则（status='ACTIVE' AND is_deleted=FALSE）</li>
     *   <li>限流检查：scope 当日检查数 &gt;= max_checks_per_day → 记 SKIP 日志提前返回</li>
     *   <li>逐条规则：评估 → 写 dq_rule_check（单条 try/catch 隔离，失败不中断整批）</li>
     *   <li>有成功评估的表资产 → 调 {@code DqScoreService.recomputeForAsset('TABLE', assetId)} 重算评分</li>
     *   <li>异步 audit（DQ_SCHEDULE_RUN / SUCCESS）</li>
     * </ol>
     *
     * @param scheduleId 调度 ID
     * @return 成功写入 dq_rule_check 的规则数（0 表示无 ACTIVE 规则或被限流跳过）
     */
    int runRuleBatch(String scheduleId);
}
