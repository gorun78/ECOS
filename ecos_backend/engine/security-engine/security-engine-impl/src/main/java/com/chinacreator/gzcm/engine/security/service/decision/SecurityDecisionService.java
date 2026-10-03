package com.chinacreator.gzcm.engine.security.service.decision;

import com.chinacreator.gzcm.common.context.TraceContext;
import com.chinacreator.gzcm.engine.security.service.ColumnLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.service.predicate.RlsParamSpec;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 详细设计-01 C.1 [3] / F01-02 / W33 — 三通道（页面/导出/AI）唯一裁决入口。
 *
 * <p>{@code decide(AssetRef, Purpose, subject)} 一次返回
 * {@link DecisionBundle}{rls, columns, masks, opa, decisionId, traceId}；
 * "绕过"在结构上不可表达 —— 漏接任一通道即绕过，合并单入口后旁路无处可藏。</p>
 *
 * <p>降级口径（C.6 矩阵的结构性实现）：
 * <ul>
 *   <li>security 存储链不可用 → {@link SecurityUnavailableException}（503 ECOS-SEC-501）</li>
 *   <li>OPA 不可用：写/AI 通道 = FAIL_CLOSED（503 ECOS-SEC-502）；
 *       读通道 = policySkipped=true + 强制全列 mask（L0/L1 语义）+ 审计</li>
 *   <li>RLS 无策略且 denyIfEmpty → rls.denyAll=true（403 ECOS-SEC-401 由调用方落错码）</li>
 *   <li>CLS 允许集为空 → columns.mode=deny（403 ECOS-SEC-402）</li>
 * </ul></p>
 */
@Service
public class SecurityDecisionService {

    private static final Logger log = LoggerFactory.getLogger(SecurityDecisionService.class);

    /** D.5 错误码 */
    public static final String ERR_SECURITY_DOWN = OpaPolicyService.ERROR_CODE_SECURITY_DOWN;   // 501
    public static final String ERR_OPA_DOWN = OpaPolicyService.ERROR_CODE_OPA_DOWN;             // 502
    public static final String ERR_RLS_EMPTY = "ECOS-SEC-401";
    public static final String ERR_CLS_EMPTY = "ECOS-SEC-402";

    /** 四通道统一异常（security 不可用 / OPA fail-closed → 上层 503） */
    public static class SecurityUnavailableException extends RuntimeException {
        public final String errorCode;
        public final int httpStatus;
        public SecurityUnavailableException(String errorCode, int httpStatus, String message) {
            super(message);
            this.errorCode = errorCode;
            this.httpStatus = httpStatus;
        }
    }

    private final RowLevelSecurityServiceImpl rlsService;
    private final ColumnLevelSecurityServiceImpl clsService;
    private final OpaPolicyService opaService;
    /** 可选注入：资产目录标记（F01-09）；null 时按"受保护资产"保守口径 */
    private final Object assetCatalogMarker;

    public SecurityDecisionService(RowLevelSecurityServiceImpl rlsService,
                                   ColumnLevelSecurityServiceImpl clsService,
                                   OpaPolicyService opaService) {
        this(rlsService, clsService, opaService, null);
    }

    public SecurityDecisionService(RowLevelSecurityServiceImpl rlsService,
                                   ColumnLevelSecurityServiceImpl clsService,
                                   OpaPolicyService opaService,
                                   Object assetCatalogMarker) {
        this.rlsService = rlsService;
        this.clsService = clsService;
        this.opaService = opaService;
        this.assetCatalogMarker = assetCatalogMarker;
    }

    /** 三通道唯一入口。 */
    public DecisionBundle decide(AssetRef assetRef, Purpose purpose, DecisionSubject subject) {
        String userId = resolveUserId(subject);
        String traceId = TraceContext.current();
        String decisionId = "dec-" + UUID.randomUUID();
        String table = assetRef.table() != null && !assetRef.table().isBlank()
                ? assetRef.table() : (assetRef.schema() != null ? assetRef.schema() + ".table" : null);
        if (table == null) {
            throw new SecurityUnavailableException(ERR_SECURITY_DOWN, 503, "assetRef.table 必填");
        }
        List<String> columns = assetRef.columns() != null ? assetRef.columns() : List.of();

        // ── ① RLS：服务端强制参数化谓词（C.2.2）──
        Map<String, Object> rlsRaw;
        try {
            rlsRaw = rlsService.apply(table, userId);
        } catch (Exception e) {
            log.error("decide: RLS 链不可用 → DENY: table={}, purpose={}", table, purpose, e);
            throw new SecurityUnavailableException(ERR_SECURITY_DOWN, 503,
                    "security-engine 不可用，操作 DENY");
        }
        Map<String, Object> rls = bundleRls(rlsRaw);
        if (Boolean.TRUE.equals(rlsRaw.get("denyAll"))) {
            log.warn("decide: RLS denyIfEmpty 命中（不泄露存在性）: table={}, userId={}, purpose={}",
                    table, userId, purpose);
        }

        // ── ② CLS：投影裁剪（allow 为空集 = 一列不给 → deny）──
        Map<String, Object> clsRaw;
        try {
            clsRaw = clsService.getColumns(table, userId, columns);
        } catch (Exception e) {
            log.error("decide: CLS 链不可用 → DENY: table={}, purpose={}", table, purpose, e);
            throw new SecurityUnavailableException(ERR_SECURITY_DOWN, 503,
                    "security-engine 不可用，操作 DENY");
        }
        Map<String, Object> cols = bundleColumns(clsRaw);
        boolean clsDenied = "deny".equals(cols.get("mode"));

        // ── ③ mask：出口治理（C.2.4）— 三通道同集，导出/AI 为强制点 ──
        List<Map<String, Object>> masks = new ArrayList<>();

        // ── ④ OPA：能不能（写/AI 强护栏；读按降级矩阵）──
        Map<String, Object> opaOut;
        boolean policySkipped = false;
        try {
            Map<String, Object> input = new java.util.LinkedHashMap<>();
            input.put("subjectType", "user");
            input.put("subjectId", userId);
            input.put("resource_type", "table");
            input.put("resource", table);
            input.put("purpose", purpose.name());
            Map<String, Object> rawOpa = opaService.evaluate("data_access", input);
            boolean opaAllow = Boolean.TRUE.equals(rawOpa.get("allow"));
            @SuppressWarnings("unchecked")
            List<Object> obligations = rawOpa.get("obligations") instanceof List<?> l
                    ? (List<Object>) l : List.of();
            String policyRef = rawOpa.get("policyId") != null
                    ? rawOpa.get("policyId").toString() : ("ecos." + rawOpa.get("policy"));
            opaOut = DecisionBundle.opaSection(opaAllow, obligations, policyRef, false);
            if (!opaAllow) {
                log.warn("decide: OPA DENY: table={}, purpose={}, userId={}", table, purpose, userId);
            }
        } catch (Exception e) {
            // OPA 不可用（evaluate 已 fail-closed 返回；此处兜底网络级异常）
            boolean readChannel = purpose == Purpose.page;
            if (readChannel) {
                // C.6：读（业务域 L0/L1）= policySkipped + 强制全列 mask + 审计
                policySkipped = true;
                if (columns.isEmpty()) {
                    forceMaskAll(clsRaw, masks);
                } else {
                    for (String c : columns) masks.add(DecisionBundle.mask(c, "FREE_TEXT"));
                }
                opaOut = DecisionBundle.opaSection(true, List.of(), "policy-skipped", true);
                log.warn("decide: OPA 不可用, 读通道 policySkipped+全列 mask: table={}", table);
            } else {
                // 导出/AI/写：FAIL_CLOSED（护栏语义）
                log.error("decide: OPA 不可用 → FAIL_CLOSED: table={}, purpose={}", table, purpose);
                throw new SecurityUnavailableException(ERR_OPA_DOWN, 503,
                        "OPA 不可用（fail-close），操作 DENY");
            }
        }

        boolean allowed = !Boolean.TRUE.equals(rls.get("denyAll")) && !clsDenied
                && Boolean.TRUE.equals(opaOut.get("allow"));
        DecisionBundle bundle = new DecisionBundle(rls, cols, masks, opaOut,
                decisionId, traceId, purpose.name(), allowed);
        log.debug("decide: {} → allowed={} rls={} cols={} masks={} opa.allow={}",
                decisionId, allowed, rls.get("predicateTemplate"), cols.get("mode"),
                masks.size(), opaOut.get("allow"));
        return bundle;
    }

    private static Map<String, Object> bundleRls(Map<String, Object> raw) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("predicateTemplate", raw.getOrDefault("predicateTemplate", RlsParamSpec.DENY_TEMPLATE));
        m.put("bindings", raw.getOrDefault("bindings", List.of()));
        m.put("combine", "AND");
        m.put("denyIfEmpty", raw.getOrDefault("denyIfEmpty", true));
        m.put("denyAll", raw.getOrDefault("denyAll", false));
        return m;
    }

    private static Map<String, Object> bundleColumns(Map<String, Object> raw) {
        Object visible = raw.get("visibleColumns");
        List<String> names = visible instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("mode", names.isEmpty() ? "deny" : "allow");
        m.put("names", names);
        return m;
    }

    private static void forceMaskAll(Map<String, Object> clsRaw, List<Map<String, Object>> masks) {
        Object visible = clsRaw.get("visibleColumns");
        if (visible instanceof List<?> l && !l.isEmpty()) {
            for (Object c : l) masks.add(DecisionBundle.mask(String.valueOf(c), "FREE_TEXT"));
        }
    }

    private static String resolveUserId(DecisionSubject subject) {
        if (subject != null && subject.userId() != null && !subject.userId().isBlank()) {
            return subject.userId();
        }
        String u = UserContext.getCurrentUserId();
        if (u == null || u.isBlank()) u = UserContext.getCurrentUsername();
        return u != null && !u.isBlank() ? u : "anonymous";
    }
}
