package com.chinacreator.gzcm.engine.kb.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.context.TraceContext;
import com.chinacreator.gzcm.engine.kb.assumption.KbAssumptionMapper;
import com.chinacreator.gzcm.engine.kb.assumption.KbAssumptionServiceImpl;
import com.chinacreator.gzcm.engine.kb.shared.PageVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 情景假设端点（F04-07 / D.3）。
 * <p>
 * 路径前缀 {@code /api/v1/knowledge/assumptions}：
 * <ul>
 *   <li>POST / → 201 {id, assumptionVersion, status:"DRAFT", unverified, traceId}</li>
 *   <li>GET  / → PageVO&lt;Map&gt;（分页）</li>
 *   <li>POST /{id}/submit → 状态机 DRAFT→PENDING_APPROVAL</li>
 *   <li>POST /{id}/approve → 状态机 PENDING_APPROVAL→ACTIVE，同 key 其余 ACTIVE 兄弟置 SUPERSEDED</li>
 *   <li>GET  /resolve → {items:[…]}；无活跃记录 → 空 items（不 404）</li>
 * </ul>
 * <p>
 * 前端路径：(通过 VersionPrefixRewriteFilter 反向) {@code /api/knowledge/assumptions} 也被识别为
 * 本控制器覆盖域（tuple with /api/v1/knowledge/**）。
 *
 * @author ECOS KB Team
 */
@RestController
@RequestMapping("/api/v1/knowledge/assumptions")
public class KbAssumptionController {

    private static final Logger log = LoggerFactory.getLogger(KbAssumptionController.class);
    private final KbAssumptionServiceImpl service;
    private final KbAssumptionMapper mapper;

    public KbAssumptionController(KbAssumptionServiceImpl service, KbAssumptionMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(permission = "knowledge:assumption:write")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> req) {
        Map<String, Object> out = service.create(req);
        return ApiResponse.success(out);
    }

    @GetMapping
    public ApiResponse<PageVO<Map<String, Object>>> list(
            @RequestParam(value = "scenarioType", required = false) String scenarioType,
            @RequestParam(value = "metric", required = false) String metric,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        int p = PageVO.clampPage(page);
        int lim = PageVO.clampSize(size);
        int off = (p - 1) * lim;
        long total = mapper.count(scenarioType, metric, status);
        List<Map<String, Object>> rows = nullSafe(mapper.list(scenarioType, metric, status, off, lim));
        return ApiResponse.success(new PageVO<>(rows, total, p, lim));
    }

    @PostMapping("/{id}/submit")
    @RequirePermission(permission = "knowledge:assumption:write")
    public ApiResponse<Map<String, Object>> submit(@PathVariable("id") String id) {
        return ApiResponse.success(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    @RequirePermission(permission = "knowledge:assumption:approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable("id") String id) {
        return ApiResponse.success(service.approve(id));
    }

    @GetMapping("/resolve")
    public ApiResponse<Map<String, Object>> resolve(
            @RequestParam("scenarioType") String scenarioType,
            @RequestParam("metric") String metric,
            @RequestParam(value = "dims", required = false) String dims,
            @RequestParam(value = "period", required = false) String period) {
        // period 预留：当前底座未加 period 维度，防误用暂时忽略（derive 层留空即可）
        Map<String, Object> out = service.resolve(scenarioType, metric, dims);
        out.put("traceId", TraceContext.current());
        return ApiResponse.success(out);
    }

    @PostMapping("/{id}/reject-update")
    @RequirePermission(permission = "knowledge:assumption:write")
    public ApiResponse<Map<String, Object>> rejectUpdate(@PathVariable("id") String id) {
        // 若已 ACTIVE 会投 409 KB_071；一侧显式端点便于前端提前探询。
        service.rejectUpdateIfActive(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("rejectable", false);
        out.put("traceId", TraceContext.current());
        return ApiResponse.success(out);
    }

    private static List<Map<String, Object>> nullSafe(List<Map<String, Object>> in) {
        return in == null ? new ArrayList<>() : in;
    }
}
