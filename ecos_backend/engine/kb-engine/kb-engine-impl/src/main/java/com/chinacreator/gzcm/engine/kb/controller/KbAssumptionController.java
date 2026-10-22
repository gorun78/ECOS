package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.kb.service.assumption.KbAssumptionDO;
import com.chinacreator.gzcm.engine.kb.service.assumption.KbAssumptionService;
import com.chinacreator.gzcm.engine.kb.service.assumption.KbAssumptionStore;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * F04-07 情景假设 REST（REQ-KB-03，D.3）。
 *
 * <ul>
 *   <li>POST /            201 → DRAFT（reason 必填，缺失 400）</li>
 *   <li>GET  /            → 列表（scenarioType/metric/status 过滤）</li>
 *   <li>POST /{id}/submit → DRAFT→PENDING_APPROVAL（409 非法跃迁）</li>
 *   <li>POST /{id}/approve→ PENDING→ACTIVE（同 key 旧 ACTIVE 转 SUPERSEDED）</li>
 *   <li>GET  /resolve     → 仅 ACTIVE 且未到期可引用；DELTA 已相对基准归一；410 ECOS-KB-072 过期</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/knowledge/assumptions")
public class KbAssumptionController {

    private final KbAssumptionService service;
    private final KbAssumptionStore store;

    public KbAssumptionController(KbAssumptionService service, KbAssumptionStore store) {
        this.service = service;
        this.store = store;
    }

    /** POST / → 201 DRAFT（reason 必填）。 */
    @PostMapping
    @RequirePermission(value = "创建情景假设", permission = "knowledge:assumption:write")
    public ApiResponse<KbAssumptionDO> create(@RequestBody KbAssumptionDO body,
                                              @RequestHeader(value = "X-Ecos-User", required = false)
                                              String user) {
        String actor = user == null || user.isBlank() ? "system" : user;
        try {
            KbAssumptionDO row = service.create(body, actor);
            return ApiResponse.<KbAssumptionDO>success("已创建 DRAFT 假设", row).withTraceIdFromMdc();
        } catch (IllegalArgumentException e) {
            return ApiResponse.<KbAssumptionDO>error(400, KbErrorCodes.STATUS_TRANSITION_INVALID.code(), e.getMessage());
        }
    }

    /** GET / → 列表。 */
    @GetMapping
    public ApiResponse<List<KbAssumptionDO>> list(
            @RequestParam(value = "scenarioType", required = false) String scenarioType,
            @RequestParam(value = "metric", required = false) String metric,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        int cap = Math.max(1, Math.min(limit, 500));
        List<KbAssumptionDO> rows = store.query(scenarioType, metric, status);
        return ApiResponse.success(rows.size() > cap ? rows.subList(0, cap) : rows);
    }

    /** POST /{id}/submit → DRAFT→PENDING_APPROVAL。 */
    @PostMapping("/{id}/submit")
    @RequirePermission(value = "提交假设审批", permission = "knowledge:assumption:write")
    public ResponseEntity<ApiResponse<KbAssumptionDO>> submit(@PathVariable String id,
                                              @RequestHeader(value = "X-Ecos-User", required = false)
                                              String user) {
        String actor = user == null || user.isBlank() ? "system" : user;
        try {
            return ResponseEntity.ok(ApiResponse.<KbAssumptionDO>success(service.submit(id, actor))
                    .withTraceIdFromMdc());
        } catch (KbErrorCodes.DomainException e) {
            return mapDomain(e);
        }
    }

    /** POST /{id}/approve → PENDING→ACTIVE（需审批角色校验由上层 SecurityDecisionService 负责，此处传 actor）。 */
    @PostMapping("/{id}/approve")
    @RequirePermission(value = "批准假设", permission = "knowledge:assumption:approve")
    public ResponseEntity<ApiResponse<KbAssumptionDO>> approve(@PathVariable String id,
                                               @RequestHeader(value = "X-Ecos-User", required = false)
                                               String user,
                                               @RequestHeader(value = ROLE_HEADER, required = false)
                                               String role) {
        String actor = user == null || user.isBlank() ? (role == null ? "system" : role) : user;
        try {
            return ResponseEntity.ok(ApiResponse.<KbAssumptionDO>success(service.approve(id, actor))
                    .withTraceIdFromMdc());
        } catch (KbErrorCodes.DomainException e) {
            return mapDomain(e);
        }
    }

    /** GET /resolve → 仅 ACTIVE 且未到期；DELTA 已相对基准归一；过期 effectiveValue=null 且 expired=true。 */
    @GetMapping("/resolve")
    public ApiResponse<Map<String, Object>> resolve(
            @RequestParam(value = "scenarioType") String scenarioType,
            @RequestParam(value = "metric") String metric,
            @RequestParam(value = "dims", required = false) String dims,
            @RequestParam(value = "period", required = false) String period) {
        KbAssumptionService.ResolveResult r =
                service.resolve(scenarioType, metric, period, System.currentTimeMillis());
        Map<String, Object> data = new HashMap<>();
        data.put("items", toItems(r));
        data.put("expired", r.expired());
        return ApiResponse.success(data).withTraceIdFromMdc();
    }

    private List<Map<String, Object>> toItems(KbAssumptionService.ResolveResult r) {
        if (r.assumption() == null) {
            return List.of();
        }
        KbAssumptionDO a = r.assumption();
        Map<String, Object> item = new HashMap<>();
        item.put("id", a.getId());
        item.put("value", r.effectiveValue());
        item.put("valueSemantic", a.getValueSemantic());
        item.put("baseAssumptionId", a.getBaseAssumptionId());
        item.put("assumptionVersion", a.getAssumptionVersion());
        item.put("unverified", a.isUnverifiedFlag());
        item.put("expireAt", a.getExpireAt());
        return List.of(item);
    }

    private static final String ROLE_HEADER = "X-Ecos-Role";

    /**
     * 领域异常 → 带状态行的失败信封（#142/B4-3e-30，收口差距 <b>D-B4-67 后半</b>）。
     *
     * <p>收口前本方法返裸 {@code ApiResponse}：路径/入参/信封字段一项未变，但 Spring 落的状态行
     * 恒 <b>HTTP 200</b>，于是 D.3 承诺的「409 非法跃迁」「403 非知识管理员」「410 假设已过期」
     * 在传输层全部同形成「成功」——前端 {@code if (!res.ok) throw} 读成成功、渲染成「操作完成 0 行」，
     * 网关/重试/Playwright 只看状态行的分派面拿不到分态（与已收口的 D-B4-59 属主端点同一缺陷形态）。</p>
     *
     * <p>修法与 {@code KbErrorAdvice#onDomain} 同构：状态行只经 common-api 单源
     * {@link DataBridgeException#resolveHttpStatus(int)} 裁决（合法 4xx/5xx 逐字透出，其余回退 500），
     * 不再在本域自写 {@code == 0 ? 500 : …} 第二判据源；body 的 {@code code} 与状态行同号，
     * 机读码仍取异常自带的 {@code ECOS-KB-***}。API 面（路径、参数、信封字段）不变，只补状态行。</p>
     */
    private static ResponseEntity<ApiResponse<KbAssumptionDO>> mapDomain(KbErrorCodes.DomainException e) {
        int status = DataBridgeException.resolveHttpStatus(e.getHttpStatus());
        return ResponseEntity.status(status)
                .body(ApiResponse.<KbAssumptionDO>error(status, e.getCode(), e.getMessage()));
    }
}
