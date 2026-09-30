package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.decision.AssetRef;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.chinacreator.gzcm.engine.security.decision.DecisionContext;
import com.chinacreator.gzcm.engine.security.decision.SecurityDecisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 详细设计-01 F01-02 / C.1 [3] / W33 — 三通道唯一裁决端点。
 *
 * <p>{@code POST /api/v1/security/decision}（operationId {@code decideDataAccess}）。
 * 页面/导出/AI 三通道共用同一入口，绕不过：{@code decide} 一次返回
 * {@code {rls, columns, masks, opa, decisionId, traceId}}。</p>
 *
 * <p>上下文字段处理（W40 前置）：本端点从请求体只接受
 * {@code assetRef} / {@code purpose} / {@code subject}；
 * userId/tenantId 优先取信任链 {@link com.chinacreator.gzcm.common.security.header.ServiceContext}，
 * 缺失时回退到 subject；orgId/clearance 由服务端派生（暂不在此注入，W40 关闭时接）。</p>
 */
@RestController
@RequestMapping({"/api/security/decision", "/api/v1/security/decision"})
public class DecisionController {

    private static final Logger log = LoggerFactory.getLogger(DecisionController.class);

    private final SecurityDecisionService service;

    public DecisionController(SecurityDecisionService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> decide(@RequestBody Map<String, Object> body) {
        AssetRef asset = AssetRef.fromMap(asMap(body.get("assetRef")));
        if (asset == null) {
            return ApiResponse.badRequest("assetRef.table 不能为空");
        }
        DecisionBundle.Purpose purpose = parsePurpose(asStr(body.get("purpose")));

        String userId = ctxUserId();
        String tenantId = ctxTenantId();
        String traceId = ctxTraceId();
        List<String> roles = ctxRoleList();
        // subject 兜底：显式请求体可覆盖（用于 QA 跨主体演练场景；生产应以信任链为准）
        Map<String, Object> subject = asMap(body.get("subject"));
        if (subject != null) {
            Object sid = subject.get("subjectId");
            if (sid != null && !sid.toString().isBlank()) userId = sid.toString();
        }
        if (userId == null || userId.isBlank()) {
            return ApiResponse.forbidden("无用户上下文（ECOS-AUTH-010），拒绝裁决");
        }

        DecisionContext ctx = DecisionContext.of(userId, tenantId, null, null, roles, traceId);
        try {
            DecisionBundle bundle = service.decide(asset, purpose, ctx);
            return ApiResponse.success(bundle.toMap());
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.warn("decide 端点异常: asset={}, purpose={}", asset.key(), purpose, e);
            return ApiResponse.error(503, "ECOS-SEC-501：security 内部裁决失败（fail-closed）");
        }
    }

    private static DecisionBundle.Purpose parsePurpose(String s) {
        if (s == null) return DecisionBundle.Purpose.PAGE;
        try { return DecisionBundle.Purpose.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return DecisionBundle.Purpose.PAGE; }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    private static String asStr(Object o) { return o == null ? null : o.toString(); }

    // ── 信任链上下文（分册 00 W02）：优先 ServiceContext，缺失时 thread 无任何 → 空 ──
    private static String ctxUserId() {
        try {
            var c = com.chinacreator.gzcm.common.security.header.ServiceContext.current();
            return c != null ? c.getUserId() : null;
        } catch (Throwable t) { return null; }
    }
    private static String ctxTenantId() {
        try {
            var c = com.chinacreator.gzcm.common.security.header.ServiceContext.current();
            return c != null ? c.getTenantId() : null;
        } catch (Throwable t) { return null; }
    }
    private static String ctxTraceId() {
        try {
            var c = com.chinacreator.gzcm.common.security.header.ServiceContext.current();
            return c != null ? c.getTraceId() : null;
        } catch (Throwable t) { return null; }
    }
    private static List<String> ctxRoleList() {
        try {
            var c = com.chinacreator.gzcm.common.security.header.ServiceContext.current();
            if (c == null || c.getRoles() == null || c.getRoles().isBlank()) return List.of();
            return java.util.Arrays.stream(c.getRoles().split(","))
                    .map(String::trim).filter(x -> !x.isBlank()).toList();
        } catch (Throwable t) { return List.of(); }
    }
}
