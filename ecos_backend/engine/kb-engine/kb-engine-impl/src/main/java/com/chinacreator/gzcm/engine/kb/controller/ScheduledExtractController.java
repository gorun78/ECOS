package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.dto.KbImportTriggerVO;
import com.chinacreator.gzcm.engine.kb.dto.ScheduledExtractCreatedVO;
import com.chinacreator.gzcm.engine.kb.dto.ScheduledExtractRequest;
import com.chinacreator.gzcm.engine.kb.dto.ScheduledExtractVO;
import com.chinacreator.gzcm.engine.kb.service.kb.IKBExtractScheduleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 结构化（映射驱动）实例抽取任务调度 Controller — TB-1 批次（PMO-73 W3）。
 *
 * <p>5 端点（路径与 V141 完全不变）语义：</p>
 * <ul>
 *   <li><b>POST /api/v1/knowledge/extract/scheduled</b> —
 *       创建定时任务：内部走 {@link IKBExtractScheduleService#create(ScheduledExtractRequest)}，
 *       经 runtime-task {@code scheduleTask(desc, cron)} 注册 + 写 td_runtime_task_plan
 *       + 仅 fallback 镜像旧表 kb_scheduled_extract。返回 {@code { id, scheduleId, nextRunAt }}。</li>
 *   <li><b>GET /api/v1/knowledge/extract/scheduled</b> —
 *       列出任务（按 {@code enabled} 过滤可选），主源 = td_runtime_task_plan，
 *       fallback 旧表行合并，按 created_at DESC 分页。</li>
 *   <li><b>PUT /api/v1/knowledge/extract/scheduled/{id}</b> —
 *       更新（name / cron / ontologyIds / enabled），cron 改变时 cancel + re-register。</li>
 *   <li><b>DELETE /api/v1/knowledge/extract/scheduled/{id}</b> —
 *       软删除（td_runtime_task_plan.is_deleted=1 + cancelSchedule）。</li>
 *   <li><b>POST /api/v1/knowledge/extract/scheduled/{id}/run</b> —
 *       触发执行一次（&lt;code&gt;submitTask + executeTask&lt;/code&gt;，回 {@code { taskId }}）。</li>
 * </ul>
 *
 * <p>铁律 §1.6-2：弃自建 {@code kb_scheduled_extract} 表自造定时器，统一迁至 runtime-task
 * {@code td_runtime_task_plan} 单事实源。</p>
 *
 * @group EXTRACT
 */
@RestController
@RequestMapping("/api/v1/knowledge/extract/scheduled")
public class ScheduledExtractController {

    private final IKBExtractScheduleService scheduleService;

    public ScheduledExtractController(IKBExtractScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // POST /  — 创建定时任务
    // ─────────────────────────────────────────────────────────────────────

    /** 创建定时抽取任务（runtime-task scheduleTask + td_runtime_task_plan + fallback 镜像）。 */
    @PostMapping
    public ApiResponse<ScheduledExtractCreatedVO> create(@RequestBody ScheduledExtractRequest req) {
        ScheduledExtractCreatedVO vo = scheduleService.create(req);
        return ApiResponse.success(vo);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /  — 列表（分页 + enabled 过滤）
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 列出定时抽取任务（主源 td_runtime_task_plan，fallback 旧表合并）。
     *
     * @param pageNum  页码（&ge;1，默认 1）
     * @param pageSize 每页（1~200，默认 20）
     * @param enabled  null=不过滤、true=仅启用、false=仅禁用
     */
    @GetMapping
    public ApiResponse<List<ScheduledExtractVO>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        return ApiResponse.success(scheduleService.list(pageNum, pageSize, enabled));
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUT /{id} — 更新（cron 改变时 cancel + re-register）
    // ─────────────────────────────────────────────────────────────────────

    /** 更新任务（name / cron / ontologyIds / enabled）。 */
    @PutMapping("/{id}")
    public ApiResponse<ScheduledExtractVO> update(@PathVariable String id,
                                                  @RequestBody ScheduledExtractRequest req) {
        ScheduledExtractVO vo = scheduleService.update(id, req);
        if (vo == null) {
            return ApiResponse.notFound("未找到任务: scheduleId=" + id);
        }
        return ApiResponse.success(vo);
    }

    // ─────────────────────────────────────────────────────────────────────
    // DELETE /{id} — 软删除
    // ─────────────────────────────────────────────────────────────────────

    /** 软删除（td_runtime_task_plan.is_deleted=1 + cancelSchedule）。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        scheduleService.delete(id);
        return ApiResponse.success();
    }

    // ─────────────────────────────────────────────────────────────────────
    // POST /{id}/run — 触发即时执行
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 触发一次立即执行（同 StructuredExtractController 异步路径形态）：
     * {@code submitTask(desc) + executeTask(taskId)}，回真实 {@code taskId}。</p>
     *
     * <p>前端 {@code MonitorPanel} 改以返回的 {@code taskId} 反查
     * {@code td_runtime_task_status} 拉 progress / log / 导出（铁律 §1.6-3）。</p>
     */
    @PostMapping("/{id}/run")
    public ApiResponse<KbImportTriggerVO> triggerNow(@PathVariable String id) {
        String taskId = scheduleService.triggerNow(id);
        KbImportTriggerVO vo = new KbImportTriggerVO();
        vo.setTaskId(taskId);
        vo.setJobId(taskId);   // 铁律 §1.6-1：jobId = taskId 统一标识
        return ApiResponse.success(vo);
    }
}
