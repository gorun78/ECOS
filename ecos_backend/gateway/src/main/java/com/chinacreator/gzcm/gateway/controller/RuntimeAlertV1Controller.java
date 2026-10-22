package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.gateway.service.RuntimeAlertRepository;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 详细设计-00 B.4.2 告警中心页面（F00-09）— {@code /api/v1/monitor/alerts} 族。
 *
 * <p>承载 §6.2 落库表 {@code ecos_runtime_alert_record} 的列表 / 详情 / ack，
 * 以及 DLQ 列表 / 重放。持久化经 {@link RuntimeAlertRepository}（Controller 不直连
 * JdbcTemplate — ArchitectureTest 门禁）。ack 权限 = L2+，replay = L3+。</p>
 *
 * <p><b>状态行保真（#142/B4-3e-32，收口裸回面 D-B4-59 在本类的类型见证腿）</b>：
 * {@code list}／{@code close}／{@code dlq} 三枚方法原先直接 {@code return ApiResponse.xxx(...)}，
 * Spring 落的状态行因此恒 <b>HTTP 200</b>——信封里写着 503/404/400，传输层却报成功，
 * 前端 {@code apiFetchData} 只看 {@code res.ok} 会把「依赖不可用」解成 {@code data=undefined}
 * 并渲染成「成功 0 条」（§4.5.4／§4.7.2 的分态在状态行上同形）。现三枚方法的失败腿逐枚经
 * {@code ResponseEntity.status(...)} 落真状态行：不可用态取 {@link RlsExecutionContract}
 * 的 503 单源常量（信封码与状态行同源，禁两处各写一遍），其余腿取对应 {@link HttpStatus}；
 * <b>路径、入参、信封字段三样零改动</b>（API 只增不改红线：只补状态行，同 D-B4-59 属主端点先例）。
 * {@code detail}／{@code ack}／{@code replay} 腿本批未动——它们不含安全裁决线出口，
 * 属非拒绝态裸回登记面（判据 B 只减不增），改判随该面另批。</p>
 */
@RestController
@RequestMapping("/api/v1/monitor")
public class RuntimeAlertV1Controller {

    private static final Logger log = LoggerFactory.getLogger(RuntimeAlertV1Controller.class);

    private final RuntimeAlertRepository repo;

    public RuntimeAlertV1Controller(RuntimeAlertRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('platform:alert:read') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false, defaultValue = "open") String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        int offset = Math.max(0, (page - 1) * size);
        try {
            List<Map<String, Object>> rows = repo.listAlerts(severity, status, size, offset);
            int total = repo.countAlerts(severity, status);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("rows", rows);
            out.put("total", total);
            out.put("page", page);
            out.put("size", size);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>success(out).withTraceIdFromMdc());
        } catch (Exception ex) {
            log.error("RuntimeAlertV1 list failed (fail-closed): {}", ex.getMessage());
            return ResponseEntity.status(RlsExecutionContract.SEC_UNAVAILABLE_HTTP)
                    .body(ApiResponse.<Map<String, Object>>error(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                            "ECOS-AUTH-005", "用户权限依赖不可用，请稍后重试").withTraceIdFromMdc());
        }
    }

    @GetMapping("/alerts/{id}")
    @PreAuthorize("hasAuthority('platform:alert:read') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        List<Map<String, Object>> rows = repo.findAlertById(id);
        if (rows.isEmpty()) {
            return ApiResponse.<Map<String, Object>>notFound("告警不存在或已删除").withTraceIdFromMdc();
        }
        return ApiResponse.success(rows.get(0)).withTraceIdFromMdc();
    }

    @PostMapping("/alerts/{id}/ack")
    @MinimumClearance(level = 2)
    @PreAuthorize("hasAuthority('platform:alert:ack') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> ack(@PathVariable String id) {
        String by = MDC.get("userId");
        if (by == null) by = "sys";
        int updated = repo.ackAlert(id, by);
        if (updated == 0) {
            return ApiResponse.<Map<String, Object>>badRequest("告警已被 ack 或不存在").withTraceIdFromMdc();
        }
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("id", id);
        ack.put("status", "acked");
        return ApiResponse.success(ack).withTraceIdFromMdc();
    }

    /**
     * #142/B4-1 {@code closeAlert}（设计 §3.3 L2124：{@code POST /api/v1/monitor/alerts/{id}/close}，
     * 档位「L2 及以上」，data {@code {id,status:"closed",closeTime}}，错误 {@code ECOS-AUTH-403}）。
     *
     * <p><b>本端点补齐的差距</b>：差距文档「端点缺口 5 处」第 1 项——关闭动作此前零载体，
     * 而 {@code ecos_runtime_alert_record.close_time}（V242 建表实存列）自落地起无任何代码写入，
     * 属「表有列、链路无写者」的形态。</p>
     *
     * <p><b>零响应字段不臆造</b>：V242 <b>没有</b> {@code close_by} 列，契约也不要求它，
     * 故响应只有 {@code id/status/closeTime} 三键（{@code closeTime} 取写后回查的真实列值，
     * 不由应用侧 {@code NOW()} 拼造）。要出 {@code closeBy} 必须先按「只加不删」新起 V288 加列，
     * 属 #142 之后的独立收口项，本批不为凑字段改 DDL。</p>
     *
     * <p><b>失败分支区分</b>（不照抄 {@link #ack} 的合并口径）：更新 0 行时回查一次——
     * 记录不存在/软删 → 404；存在但状态非 {@code open/acked} → 400。
     * 把「不存在」和「状态不允许」混成同一句 400 会掩盖误传 id 的真实原因。</p>
     *
     * <p><b>档位</b>：{@code /api/v1/monitor} 前缀按 {@code ClearanceInterceptor.PATH_RULES}
     * 默认落 L1，而契约对 ack/close 都写「L2 及以上」⇒ 本方法与新加的 {@link MinimumClearance}
     * {@code (level=2)} 于 {@link #ack} 上，是<b>按契约把档位补齐</b>（收紧方向，不改路径与签名，
     * 符合「只增不改」）。超管旁路仍由拦截器既有强审计通道处理。</p>
     */
    @PostMapping("/alerts/{id}/close")
    @MinimumClearance(level = 2)
    @PreAuthorize("hasAuthority('platform:alert:close') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> close(@PathVariable String id) {
        try {
            int updated = repo.closeAlert(id);
            if (updated == 0) {
                List<Map<String, Object>> rows = repo.findAlertById(id);
                if (rows.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.<Map<String, Object>>notFound("告警不存在或已删除").withTraceIdFromMdc());
                }
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.<Map<String, Object>>badRequest(
                                "告警状态不可关闭（仅 open/acked 可关闭）").withTraceIdFromMdc());
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("status", "closed");
            out.put("closeTime", readCloseTime(id));
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>success(out).withTraceIdFromMdc());
        } catch (Exception ex) {
            log.error("RuntimeAlertV1 close failed (fail-closed): {}", ex.getMessage());
            return ResponseEntity.status(RlsExecutionContract.SEC_UNAVAILABLE_HTTP)
                    .body(ApiResponse.<Map<String, Object>>error(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                            "ECOS-AUTH-005", "告警关闭依赖不可用，请稍后重试").withTraceIdFromMdc());
        }
    }

    /** 写后回查真实 {@code close_time}（列值缺失时如实返回 null，不用应用侧时间冒充）。 */
    private Object readCloseTime(String id) {
        List<Map<String, Object>> rows = repo.findAlertById(id);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        Object v = row.get("close_time");
        return v != null ? v : row.get("CLOSE_TIME");
    }

    /** DLQ 列表（W13；表由 V243 建立）。 */
    @GetMapping("/dlq")
    @PreAuthorize("hasAuthority('platform:alert:read') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dlq(
            @RequestParam(required = false, defaultValue = "pending") String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        int offset = Math.max(0, (page - 1) * size);
        try {
            List<Map<String, Object>> rows = repo.listDlq(status, size, offset);
            int total = repo.countDlq(status);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("rows", rows);
            out.put("total", total);
            out.put("page", page);
            out.put("size", size);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>success(out).withTraceIdFromMdc());
        } catch (Exception ex) {
            log.error("dlq list failed: {}", ex.getMessage());
            return ResponseEntity.status(RlsExecutionContract.SEC_UNAVAILABLE_HTTP)
                    .body(ApiResponse.<Map<String, Object>>error(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                            "ECOS-AUTH-005", "dlq 依赖不可用").withTraceIdFromMdc());
        }
    }

    @PostMapping("/dlq/{id}/replay")
    @PreAuthorize("hasAuthority('platform:alert:ack') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> replay(@PathVariable String id) {
        int updated = repo.replayDlq(id);
        if (updated == 0) {
            return ApiResponse.<Map<String, Object>>badRequest("DLQ 消费失败：记录不存在或已处理").withTraceIdFromMdc();
        }
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("id", id);
        rep.put("status", "replayed");
        return ApiResponse.success(rep).withTraceIdFromMdc();
    }
}
