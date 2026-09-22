package com.chinacreator.gzcm.engine.kb.service.kb;

import com.chinacreator.gzcm.engine.kb.dto.ScheduledExtractCreatedVO;
import com.chinacreator.gzcm.engine.kb.dto.ScheduledExtractRequest;
import com.chinacreator.gzcm.engine.kb.dto.ScheduledExtractVO;

import java.util.List;

/**
 * 结构化（映射驱动）实例抽取任务调度服务 — TB-1 批次（PMO-73 W3）。
 *
 * <p>取代原 {@code ScheduledExtractController} 的自造定时器逻辑。</p>
 *
 * <p>事实源 = {@code td_runtime_task_plan}（V152 W4 持久化基石）；
 * 旧表 {@code ecos_knowledge.kb_scheduled_extract}（V141）仅作 fallback 镜像
 * 单向读，回退 UI 默认空列表使用。铁律 §1.6-2：禁止业务自建 {@code *_scheduled_*} 表，
 * 调度元信息（cron / next_run_at / last_status）全部由
 * {@link com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService#scheduleTask}
 * 经 runtime-task 统一持久化。</p>
 *
 * <p>调用方：{@code ScheduledExtractController} 5 端点（路径不变）。</p>
 */
public interface IKBExtractScheduleService {

    /**
     * 创建定时抽取任务。
     *
     * @param req payload（name / ontologyIds / mode / period / timeOfDay）
     * @return {@code { id, scheduleId, nextRunAt }}
     */
    ScheduledExtractCreatedVO create(ScheduledExtractRequest req);

    /**
     * 分页列出定时抽取任务（事实源 = {@code td_runtime_task_plan}，
     * 旧表 {@code kb_scheduled_extract} 旧行 fallback 合并，已软删过滤）。
     *
     * @param pageNum  页码（&ge;1）
     * @param pageSize 每页（1~200）
     * @param enabled  null=不过滤、true=仅启用、false=仅禁用
     * @return VO 列表
     */
    List<ScheduledExtractVO> list(int pageNum, int pageSize, Boolean enabled);

    /** 更新任务（name / cron / ontologyIds / enabled 按需 cancel + re-register）。 */
    ScheduledExtractVO update(String scheduleId, ScheduledExtractRequest req);

    /** 软删除（td_runtime_task_plan.is_deleted=1 + cancelSchedule）；旧镜像行同步软删。 */
    void delete(String scheduleId);

    /** 暂停调度（任务内保留，禁用未来触发；plan 行 task_status=PAUSED）。 */
    void pause(String scheduleId);

    /** 恢复调度。 */
    void resume(String scheduleId);

    /**
     * 立即触发一次（同 {@code StructuredExtractController 异步路径} 形态）：
     * {@code submitTask(desc) + executeTask(taskId)}，返回 taskId。
     */
    String triggerNow(String scheduleId);

    /**
     * 立即触发周期一次性触发：构造 desc + submitTask + executeTask，返回 taskId。
     * 与 {@link #triggerNow(String)} 等价，名字语义区分「按周期记录的一次性」。
     */
    String triggerPeriodic(String scheduleId);
}
