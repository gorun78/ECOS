package com.chinacreator.gzcm.engine.kb.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.context.TraceContext;
import com.chinacreator.gzcm.engine.kb.profile.KbProfileServiceImpl;
import com.chinacreator.gzcm.engine.kb.profile.ProfileGenerateRequest;
import com.chinacreator.gzcm.engine.kb.profile.ProfileResolveVO;
import com.chinacreator.gzcm.engine.kb.profile.ProfileVO;
import com.chinacreator.gzcm.engine.kb.shared.PageVO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 历史画像端点（F04-06 / D.3）。
 * <p>
 * 路径前缀 {@code /api/v1/knowledge/profiles}：
 * <ul>
 *   <li>POST /generate → 202 {taskId, status, traceId}；dryRun=true 同步返回不落库。</li>
 *   <li>GET  / → PageVO&lt;ProfileVO&gt;（D.2-4 分页 limit 量）。</li>
 *   <li>POST /{id}/publish → 403 KB_062（非知识管理员） / 409 KB_061（已发布） / 409 KB_060（非法跃迁）。</li>
 *   <li>GET  /resolve → 单条 ProfileResolveVO 或 404。</li>
 * </ul>
 * <p>
 * 与 {@code VersionPrefixRewriteFilter}（F04-01）配合：v1→bare 正向条目不存在 /api/v1/knowledge/
 * （同 knowledge-bases 双路径同规则），故本控制器映射即正典路径，bare 反向不经 rewrite 亦可达
 * （同 profile/ 前缀在 allow-list 内）。
 *
 * @author ECOS KB Team
 */
@RestController
@RequestMapping("/api/v1/knowledge/profiles")
public class KbProfileController {

    private static final Logger log = LoggerFactory.getLogger(KbProfileController.class);
    private final KbProfileServiceImpl service;

    public KbProfileController(KbProfileServiceImpl service) {
        this.service = service;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequirePermission(permission = "knowledge:profile:generate")
    public ApiResponse<Map<String, Object>> generate(@RequestBody ProfileGenerateRequest req) {
        log.debug("KbProfileController.generate: metricCodes={} window={}~{} dryRun={}",
                req.getMetricCodes(), req.getWindowFrom(), req.getWindowTo(), req.isDryRun());
        Map<String, Object> out = service.generate(req);
        return ApiResponse.success(out);
    }

    @GetMapping
    public ApiResponse<PageVO<ProfileVO>> list(
            @RequestParam(value = "metric", required = false) String metric,
            @RequestParam(value = "dims", required = false) String dimsFilter,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        PageVO<ProfileVO> po = service.list(metric, dimsFilter, status, page, size);
        return ApiResponse.success(po);
    }

    @PostMapping("/{id}/publish")
    @RequirePermission(permission = "knowledge:profile:publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable("id") String id) {
        service.publish(id, com.chinacreator.gzcm.engine.kb.shared.KbActorSupport.currentActor());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", KbProfileServiceImpl.STATUS_PUBLISHED);
        out.put("traceId", TraceContext.current());
        return ApiResponse.success(out);
    }

    @GetMapping("/resolve")
    public ApiResponse<ProfileResolveVO> resolve(
            @RequestParam("metric") String metric,
            @RequestParam(value = "dims", required = false) String dimsFilter,
            @RequestParam(value = "atVersion", required = false) String atVersion) {
        ProfileResolveVO vo = service.resolve(metric, dimsFilter, atVersion);
        if (vo == null) {
            return ApiResponse.error(404, "NOT_FOUND",
                    "画像不存在：metric=" + metric + " atVersion=" + atVersion);
        }
        return ApiResponse.success(vo);
    }
}
