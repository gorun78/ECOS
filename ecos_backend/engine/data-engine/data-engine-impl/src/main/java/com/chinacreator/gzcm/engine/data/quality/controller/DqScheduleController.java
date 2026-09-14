package com.chinacreator.gzcm.engine.data.quality.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.quality.DqScheduleService;
import com.chinacreator.gzcm.engine.data.quality.model.DqScheduleDTO;
import com.chinacreator.gzcm.engine.data.quality.model.DqScheduleVO;

/**
 * DQ 监控调度 REST API（PMO-48-C T11）— 调度计划 CRUD + 手动触发。
 *
 * <pre>
 * GET    /api/v1/dq/schedules            — 计划列表
 * GET    /api/v1/dq/schedules/{id}       — 计划详情
 * POST   /api/v1/dq/schedules            — 创建计划
 * PUT    /api/v1/dq/schedules/{id}       — 更新计划（非 null 字段覆盖）
 * DELETE /api/v1/dq/schedules/{id}       — 逻辑删除计划
 * POST   /api/v1/dq/schedules/{id}/trigger?triggerBy=xxx — 手动触发规则批执行
 * </pre>
 *
 * <p>三滤波器（铁律 1.2）：本路径由既有 {@code /api/v1/dq/**} 通配覆盖
 * （T2 已注册 VersionPrefixRewriteFilter KEEP + SecurityConfig permitAll
 * + ClearanceInterceptor 豁免双路径 + auth.whitelist），无需改三滤波器。</p>
 *
 * <p>安全卡（铁律 2.4）：读操作 auditRead / 写操作 auditWrite 由
 * {@code DqScheduleServiceImpl} 统一异步走 security-engine。</p>
 *
 * @author PMO-48-C T11
 */
@RestController
@RequestMapping("/api/v1/dq/schedules")
public class DqScheduleController {

    private final DqScheduleService scheduleService;

    public DqScheduleController(DqScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** 调度计划列表（含停用）。 */
    @GetMapping
    public ApiResponse<List<DqScheduleVO>> listSchedules() {
        return ApiResponse.success(scheduleService.listSchedules());
    }

    /** 调度计划详情。 */
    @GetMapping("/{id}")
    public ApiResponse<DqScheduleVO> getSchedule(@PathVariable String id) {
        DqScheduleVO vo = scheduleService.getSchedule(id);
        if (vo == null) {
            return ApiResponse.<DqScheduleVO>error(ApiResponse.CODE_NOT_FOUND,
                    "NOT_FOUND", "调度计划 " + id + " 不存在");
        }
        return ApiResponse.success(vo);
    }

    /** 创建调度计划。 */
    @PostMapping
    public ApiResponse<String> createSchedule(@RequestBody DqScheduleDTO dto) {
        if (dto == null) {
            return ApiResponse.<String>error(ApiResponse.CODE_BAD_REQUEST, "BAD_REQUEST", "请求体不能为空");
        }
        String id = scheduleService.createSchedule(dto);
        return ApiResponse.success(id);
    }

    /** 更新调度计划（非 null 字段覆盖）。 */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateSchedule(@PathVariable String id,
                                            @RequestBody DqScheduleDTO dto) {
        if (dto == null) {
            return ApiResponse.<Void>error(ApiResponse.CODE_BAD_REQUEST, "BAD_REQUEST", "请求体不能为空");
        }
        scheduleService.updateSchedule(id, dto);
        return ApiResponse.success();
    }

    /** 逻辑删除调度计划。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSchedule(@PathVariable String id) {
        scheduleService.deleteSchedule(id);
        return ApiResponse.success();
    }

    /** 手动触发一次规则批执行（支持 triggerBy 参数留痕）。 */
    @PostMapping("/{id}/trigger")
    public ApiResponse<Void> triggerManual(@PathVariable String id,
                                           @RequestParam(value = "triggerBy", required = false) String triggerBy) {
        scheduleService.triggerManual(id, triggerBy);
        return ApiResponse.success();
    }
}
