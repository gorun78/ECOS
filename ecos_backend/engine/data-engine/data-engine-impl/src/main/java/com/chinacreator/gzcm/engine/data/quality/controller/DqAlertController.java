package com.chinacreator.gzcm.engine.data.quality.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.quality.DqAlertService;
import com.chinacreator.gzcm.engine.data.quality.model.DqAlertQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqAlertVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * DQ 告警 REST API（PMO-48-C T12）。
 *
 * <pre>
 * GET    /api/v1/dq/alerts             — 告警分页/过滤列表
 * GET    /api/v1/dq/alerts/{id}        — 告警详情
 * POST   /api/v1/dq/alerts/{id}/ack    — 确认告警
 * POST   /api/v1/dq/alerts/{id}/resolve — 解决告警（附 resolvedBy/note）
 * GET    /api/v1/dq/alerts/levels      — 告警级别枚举（P0-P3 占位，静默返）
 * </pre>
 *
 * <p>三滤波器：本路径由既有 {@code /api/v1/dq/**} 通配覆盖（T2 已注册
 * VersionPrefixRewriteFilter KEEP + SecurityConfig permitAll + ClearanceInterceptor
 * 豁免双路径 + auth.whitelist），无需追加。反向 {@code /api/dq/alerts/**} 由
 * REVERSE_PREFIX_MAP 已覆盖。</p>
 *
 * <p>安全卡（铁律 2.4 #5）：读/写操作由 {@code DqAlertServiceImpl} 统一异步
 * {@code DqSecurityService.auditRead/auditWrite}。</p>
 *
 * @author PMO-48-C T12
 */
@RestController
@RequestMapping("/api/v1/dq/alerts")
public class DqAlertController {

    /** 告警级别枚举（静默返回，前端用于下拉/标签）。 */
    private static final List<String> ALERT_LEVELS = List.of("P0", "P1", "P2", "P3");

    private final DqAlertService alertService;

    public DqAlertController(DqAlertService alertService) {
        this.alertService = alertService;
    }

    /** 告警分页列表（alertLevel/status/ruleId/assetId/keyword 过滤，created_at 倒序）。 */
    @GetMapping
    public ApiResponse<PageResult<DqAlertVO>> listAlerts(
            @RequestParam(value = "alertLevel", required = false) String alertLevel,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "ruleId", required = false) String ruleId,
            @RequestParam(value = "assetId", required = false) String assetId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        DqAlertQuery q = new DqAlertQuery();
        q.setAlertLevel(alertLevel);
        q.setStatus(status);
        q.setRuleId(ruleId);
        q.setAssetId(assetId);
        q.setKeyword(keyword);
        if (pageNum != null && pageNum > 0) {
            q.setPageNum(pageNum);
        }
        if (pageSize != null && pageSize > 0) {
            q.setPageSize(pageSize);
        }
        return ApiResponse.success(alertService.listAlerts(q));
    }

    /** 告警级别枚举（占位：P0-P3 与 dq_rule severity 映射一一对应）。 */
    @GetMapping("/levels")
    public ApiResponse<List<String>> levels() {
        return ApiResponse.success(ALERT_LEVELS);
    }

    /** 告警详情。 */
    @GetMapping("/{id}")
    public ApiResponse<DqAlertVO> getAlert(@PathVariable String id) {
        DqAlertVO vo = alertService.getAlert(id);
        if (vo == null) {
            return ApiResponse.<DqAlertVO>error(ApiResponse.CODE_NOT_FOUND,
                    "NOT_FOUND", "DQ 告警 " + id + " 不存在");
        }
        return ApiResponse.success(vo);
    }

    /** 确认告警（PENDING/NOTIFIED/ESCALATED → ACKED）。 */
    @PostMapping("/{id}/ack")
    public ApiResponse<Void> ackAlert(@PathVariable String id,
                                       @RequestParam(value = "ackBy", required = false) String ackBy) {
        alertService.ackAlert(id, ackBy);
        return ApiResponse.success();
    }

    /** 解决告警（附处理说明，进入终态 RESOLVED）。 */
    @PostMapping("/{id}/resolve")
    public ApiResponse<Void> resolveAlert(@PathVariable String id,
                                           @RequestParam(value = "resolvedBy", required = false) String resolvedBy,
                                           @RequestParam(value = "note", required = false) String note,
                                           @RequestBody(required = false) Object ignored) {
        alertService.resolveAlert(id, resolvedBy, note);
        return ApiResponse.success();
    }
}
