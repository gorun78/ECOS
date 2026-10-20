package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.decision.DataScopeContract;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
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
 * {@code assetRef} / {@code purpose} / {@code subject} / {@code resource}；
 * userId/tenantId 优先取信任链 {@link com.chinacreator.gzcm.common.security.header.ServiceContext}，
 * 缺失时回退到 subject；orgId/clearance 由服务端派生（暂不在此注入，W40 关闭时接）。</p>
 *
 * <p>{@code resource}（#142/B4-3d-3 只增，D-B4-20）：AI 通道的<b>请求数据域声明</b>段，
 * 形态为 {@code resource.dataScope} 下的 {@code departmentId} 与 {@code projectIds} 两键。
 * 它不是主体属性而是调用方声明，读/写一律经 {@link DataScopeContract} 单源
 * （信任边界与"未声明即不写"的理由在该类内声明）。页面/导出通道不传该段 ⇒ 护栏输入形态
 * 与本批之前逐字节一致。</p>
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
        AssetRef asset = AssetRef.fromMap(asMap(body.get(DecisionChannelContract.KEY_ASSET_REF)));
        if (asset == null) {
            return ApiResponse.badRequest(DecisionChannelContract.KEY_ASSET_REF + "."
                    + DecisionChannelContract.KEY_ASSET_TABLE + " 不能为空");
        }
        String rawPurpose = asStr(body.get(DecisionChannelContract.KEY_PURPOSE));
        DecisionBundle.Purpose purpose = resolvePurpose(rawPurpose);
        if (purpose == null) {
            // D-B4-27：未识别的 purpose 过去被静默当 PAGE 放行（导出/AI 调用一旦词面失配，
            // 就以页面档的护栏矩阵被裁决并返回 200 —— 无错误、无日志、消费方无从察觉）。
            // 现按 400 显式拒绝；缺失/空白仍是既约定的 PAGE 默认，既有调用方行为不变。
            return ApiResponse.badRequest(DecisionChannelContract.unrecognizedReason(rawPurpose));
        }

        String userId = ctxUserId();
        String tenantId = ctxTenantId();
        String traceId = ctxTraceId();
        List<String> roles = ctxRoleList();
        // subject 兜底：显式请求体可覆盖（用于 QA 跨主体演练场景；生产应以信任链为准）
        Map<String, Object> subject = asMap(body.get(DecisionChannelContract.KEY_SUBJECT));
        if (subject != null) {
            Object sid = subject.get(DecisionChannelContract.KEY_SUBJECT_ID);
            if (sid != null && !sid.toString().isBlank()) userId = sid.toString();
        }
        if (userId == null || userId.isBlank()) {
            return ApiResponse.forbidden("无用户上下文（ECOS-AUTH-010），拒绝裁决");
        }

        DecisionContext ctx = DecisionContext.of(userId, tenantId, null, null, roles, traceId)
                // #142/B4-3d-3 只增：调用方声明的请求数据域（AI 通道的 §4.7.2 判据面）。
                // 信任边界——本段是「声明」不是「主体属性」：策略侧的横向越权地板拿它和
                // <b>服务端派生</b>的 subject.department 比（分册 00：客户端 X-Org-Id 已被剥离），
                // 故谎报只会落 DENY（AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT），不会落放行；
                // 未声明则整段不写（页面/导出通道载荷逐字节不变）。键面走单源 DataScopeContract。
                .withDataScope(DataScopeContract.read(body.get(DataScopeContract.KEY_RESOURCE)));
        try {
            DecisionBundle bundle = service.decide(asset, purpose, ctx);
            return ApiResponse.success(bundle.toMap());
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.warn("decide 端点异常: asset={}, purpose={}", asset.key(), purpose, e);
            return ApiResponse.error(503,
                    RlsExecutionContract.SEC_UNAVAILABLE_CODE + "：security 内部裁决失败（fail-closed）");
        }
    }

    /**
     * 通道词解析（#142/B4-3d-2 语义订正，D-B4-27）。
     *
     * <p>三态：缺失/空白 ⇒ {@code PAGE}（既约定默认，保持不传 purpose 的既有调用方行为不变）；
     * 命中 {@link DecisionChannelContract} 词表 ⇒ 对应通道；<b>未命中 ⇒ {@code null}</b>，
     * 由调用方转 400。旧实现在未命中时 {@code return Purpose.PAGE}，即静默降级。</p>
     */
    private static DecisionBundle.Purpose resolvePurpose(String s) {
        if (s == null || s.isBlank()) return DecisionBundle.Purpose.PAGE;
        String normalized = DecisionChannelContract.normalize(s);
        if (normalized == null) return null;
        try {
            return DecisionBundle.Purpose.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 词表里有、枚举里没有＝两侧失配（新增通道只改了一处），同样拒绝而非降级
            return null;
        }
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
