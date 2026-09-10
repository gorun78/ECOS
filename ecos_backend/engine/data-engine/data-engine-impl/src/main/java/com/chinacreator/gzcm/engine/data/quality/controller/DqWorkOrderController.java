package com.chinacreator.gzcm.engine.data.quality.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.quality.DqWorkOrderService;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * DQ 工单流转 REST API（PMO-48-C T13）— 工单状态机 7 动作 + RCA + 排行。
 *
 * <pre>
 * GET    /api/v1/dq/work-orders                 — 列表（status/severity/handlingMode 过滤 + 分页）
 * GET    /api/v1/dq/work-orders/{id}            — 详情
 * POST   /api/v1/dq/work-orders/{id}/assign?to=u001 — 指派（PENDING → ASSIGNED）
 * POST   /api/v1/dq/work-orders/{id}/start      — 开始处理（ASSIGNED → IN_WORK；P0/P1 自动触发异步 RCA）
 * POST   /api/v1/dq/work-orders/{id}/resolve    — 完成修复（IN_WORK → RESOLVED）
 * POST   /api/v1/dq/work-orders/{id}/verify     — 验证（RESOLVED → VERIFIED / 失败 → IN_WORK，retry_count++）
 * POST   /api/v1/dq/work-orders/{id}/close      — 关闭（VERIFIED → CLOSED）
 * POST   /api/v1/dq/work-orders/{id}/reject     — 驳回（任意非终态 → REJECTED）
 * POST   /api/v1/dq/work-orders/{id}/run-rca    — 手动 RCA（P2/P3；全部允许）
 * GET    /api/v1/dq/work-orders/rank?grade=P0   — 风险工单排行（top N 摘要，按 severity+retry DESC）
 * </pre>
 *
 * <p><b>三滤波器</b>（铁律 1.2）：本路径 {@code /api/v1/dq/work-orders/**} 由 T2 已注册的
 * {@code /api/v1/dq/**} 通配覆盖：VersionPrefixRewriteFilter KEEP /
 * SecurityConfig permitAll + ClearanceInterceptor 双路径豁免 / application.yml
 * auth.whitelist — 无需改三滤波器。</p>
 *
 * <p><b>安全卡</b>（铁律 2.4）：写操作（assign/start/resolve/verify/close/reject/run-rca）
 * 异步 {@code auditWrite}；读操作 {@code auditRead}；默认 DENY 由
 * {@code DqSecurityService} 内部兜底（不可达仅 warn 日志不阻塞）。</p>
 *
 * <p><b>工单状态机</b>（7 态）：PENDING → ASSIGNED → IN_WORK → RESOLVED →
 * VERIFIED → CLOSED；任意非终态 → REJECTED（白名单 + 终态保护）。
 * 非法转换抛 {@code BusinessException("illegal status transition: current->target")}，
 * 由 {@code GlobalExceptionHandler} 映射 400。</p>
 *
 * @author PMO-48-C T13
 */
@RestController
@RequestMapping("/api/v1/dq/work-orders")
public class DqWorkOrderController {

    private final DqWorkOrderService workOrderService;

    public DqWorkOrderController(DqWorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    // ==================== 查询 ====================

    /**
     * 工单列表（status/severity/handlingMode 过滤 + 分页）。
     *
     * @param status       状态过滤（可选）
     * @param severity     严重级别过滤（可选）
     * @param handlingMode 处理模式过滤（可选）
     * @param assetId      资产 ID 过滤（可选）
     * @param pageNum      页码（从 1 开始，缺省 1）
     * @param pageSize     每页大小（缺省 20，上限 200）
     */
    @GetMapping
    public ApiResponse<PageResult<DqWorkOrderVO>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "handlingMode", required = false) String handlingMode,
            @RequestParam(value = "assetId", required = false) String assetId,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {
        DqWorkOrderQuery q = new DqWorkOrderQuery();
        q.setStatus(status);
        q.setSeverity(severity);
        q.setHandlingMode(handlingMode);
        q.setAssetId(assetId);
        q.setPageNum(pageNum);
        q.setPageSize(pageSize);
        return ApiResponse.success(workOrderService.listWorkOrders(q));
    }

    /**
     * 工单详情。
     *
     * @param id 工单 ID
     */
    @GetMapping("/{id}")
    public ApiResponse<DqWorkOrderVO> get(@PathVariable String id) {
        DqWorkOrderVO vo = workOrderService.getWorkOrder(id);
        if (vo == null) {
            return ApiResponse.<DqWorkOrderVO>error(ApiResponse.CODE_NOT_FOUND,
                    "NOT_FOUND", "工单 " + id + " 不存在或已删除");
        }
        return ApiResponse.success(vo);
    }

    // ==================== 状态机 7 动作（POST） ====================

    /**
     * 指派工单 — PENDING → ASSIGNED（手动 / 告警创建后的下一动作）。
     *
     * @param id 工单 ID
     * @param to 指派人业务账号（必填）
     * @param by 指派操作人（默认 system，可空）
     */
    @PostMapping("/{id}/assign")
    public ApiResponse<Void> assign(@PathVariable String id,
                                    @RequestParam("to") String to,
                                    @RequestParam(value = "by", required = false, defaultValue = "system") String by) {
        workOrderService.assign(id, to, by);
        return ApiResponse.success();
    }

    /**
     * 开始处理 — ASSIGNED → IN_WORK。
     *
     * <p>severity ∈ {CRITICAL, HIGH}（P0/P1）时异步触发自动 RCA；
     * 返回 200 不等 RCA 完成（铁律 决策 #4：失败不阻塞 IN_WORK 落地）。</p>
     *
     * @param id 工单 ID
     * @param by 操作人（可空，缺省 system）
     */
    @PostMapping("/{id}/start")
    public ApiResponse<Void> start(@PathVariable String id,
                                   @RequestParam(value = "by", required = false, defaultValue = "system") String by) {
        workOrderService.startWork(id, by);
        return ApiResponse.success();
    }

    /**
     * 完成修复 — IN_WORK → RESOLVED。
     *
     * @param id 工单 ID
     * @param body {@code {by, note}}：by 处理人（必填）、note 处理说明（可空）
     */
    @PostMapping("/{id}/resolve")
    public ApiResponse<Void> resolve(@PathVariable String id,
                                     @RequestBody(required = false) ResolveBody body) {
        workOrderService.resolve(id, body != null ? body.by : null, body != null ? body.note : null);
        return ApiResponse.success();
    }

    /**
     * 验证修复结果 — RESOLVED → VERIFIED（pass=true）或 RESOLVED → IN_WORK（pass=false，retry_count++）。
     *
     * @param id   工单 ID
     * @param body {@code {by, pass, note}}
     */
    @PostMapping("/{id}/verify")
    public ApiResponse<Void> verify(@PathVariable String id,
                                    @RequestBody VerifyBody body) {
        if (body == null) {
            return ApiResponse.<Void>error(ApiResponse.CODE_BAD_REQUEST, "BAD_REQUEST", "请求体不能为空");
        }
        workOrderService.verify(id, body.verifiedBy, body.pass != null && body.pass, body.note);
        return ApiResponse.success();
    }

    /**
     * 关闭工单 — VERIFIED → CLOSED。
     *
     * @param id 工单 ID
     * @param by 关闭人（可空，缺省 system）
     */
    @PostMapping("/{id}/close")
    public ApiResponse<Void> close(@PathVariable String id,
                                   @RequestParam(value = "by", required = false, defaultValue = "system") String by) {
        workOrderService.close(id, by);
        return ApiResponse.success();
    }

    /**
     * 驳回工单 — 任意非终态 → REJECTED（PENDING/ASSIGNED/IN_WORK/RESOLVED）。
     *
     * @param id   工单 ID
     * @param body {@code {by, reason}}
     */
    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable String id,
                                    @RequestBody(required = false) RejectBody body) {
        workOrderService.reject(id, body != null ? body.by : null, body != null ? body.reason : null);
        return ApiResponse.success();
    }

    /**
     * 升级策略单工单 — PENDING + 5min 未认领 → severity 升级（MEDIUM/LOW→HIGH；HIGH→CRITICAL）。
     *
     * @param id 工单 ID
     * @return {@code data=true} 实际升级，{@code data=false} 未触发（非 PENDING / 未 5min / 已 CRITICAL）
     */
    @PostMapping("/{id}/escalate")
    public ApiResponse<Boolean> escalate(@PathVariable String id) {
        boolean escalated = workOrderService.escalateUnack(id);
        return ApiResponse.success(escalated);
    }

    // ==================== T13: RCA + 排行 ====================

    /**
     * 手动 RCA（P2/P3 工单详情页按钮触发；任何状态都允许）。
     *
     * @param id 工单 ID
     * @return RCA 结果 Map：rootCause / confidence / causalChain / candidates / analyzedAt
     *      （本波 stub 阶段 confidence=0.0，rootCause=STUB:xxx）
     */
    @PostMapping("/{id}/run-rca")
    public ApiResponse<Map<String, Object>> runRca(@PathVariable String id) {
        try {
            return ApiResponse.success(workOrderService.runRca(id));
        } catch (RuntimeException e) {
            // 工单不存在 / JdbcTemplate 不可用 等 — 返回结构化错误，不抛 500
            return ApiResponse.<Map<String, Object>>error(ApiResponse.CODE_BAD_REQUEST,
                    "RCA_FAILED", e.getMessage());
        }
    }

    /**
     * 风险工单排行（统计端点）。
     *
     * @param grade 严重级别过滤：CRITICAL / HIGH / MEDIUM / LOW / P0-P3（不传则全部，按 severity priority + retry + 时间排）
     * @param limit 返回上限（>0，缺省 10，上限 100）
     */
    @GetMapping("/rank")
    public ApiResponse<List<Map<String, Object>>> rank(
            @RequestParam(value = "grade", required = false, defaultValue = "") String grade,
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {
        String g = (grade == null || grade.isBlank()) ? null : grade.trim().toUpperCase();
        // P0-P3 语义转 CRITICAL/HIGH/MEDIUM/LOW（兼容前端 #12 告警级别 badge 习惯）
        String normalized = switch (g == null ? "" : g) {
            case "P0", "CRITICAL" -> "CRITICAL";
            case "P1", "HIGH" -> "HIGH";
            case "P2", "MEDIUM" -> "MEDIUM";
            case "P3", "LOW" -> "LOW";
            default -> g;
        };
        return ApiResponse.success(workOrderService.riskRanking(normalized, limit));
    }

    // ==================== 请求体（强类型 — 铁律 后端开发规范 §四，不传 Map） ====================

    /** resolve 请求体：by + note。 */
    public static class ResolveBody {
        /** 处理人 */
        public String by;
        /** 处理说明 */
        public String note;
    }

    /** verify 请求体：verifiedBy + pass + note。 */
    public static class VerifyBody {
        /** 验证人 */
        public String verifiedBy;
        /** 验证通过与否 */
        public Boolean pass;
        /** 验证备注（失败原因 / 通过说明） */
        public String note;
    }

    /** reject 请求体：by + reason。 */
    public static class RejectBody {
        /** 驳回人 */
        public String by;
        /** 驳回原因 */
        public String reason;
    }
}
