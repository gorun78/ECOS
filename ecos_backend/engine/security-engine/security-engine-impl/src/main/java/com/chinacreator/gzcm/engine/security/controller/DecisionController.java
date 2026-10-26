package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.decision.DataScopeContract;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.decision.AssetRef;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.chinacreator.gzcm.engine.security.decision.DecisionContext;
import com.chinacreator.gzcm.engine.security.decision.DeclaredScopeResolutionService;
import com.chinacreator.gzcm.engine.security.decision.SecurityDecisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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
 * <p>{@code resource}（#142/B4-3d-3 只增，D-B4-20；#142/B4-3e-79 真值化，D-B4-36）：
 * AI 通道的<b>请求数据域声明</b>段，形态为 {@code resource.dataScope} 下的
 * {@code departmentId} 与 {@code projectIds} 两键。它不是主体属性而是调用方声明，读/写
 * 一律经 {@link DataScopeContract} 单源。<b>AI 通道的 {@code departmentId} 只允许由
 * {@link DeclaredScopeResolutionService} 按 {@code projectIds} 向属主域（data-engine）
 * 展开真值后写入</b>，调用方自报值在此无条件丢弃（D-B4-29 剔除工具入参白名单后，
 * 本端点是同一个洞的另一张面孔）。页面/导出通道不传该段 ⇒ 护栏输入形态与本批之前逐字节一致。</p>
 */
@RestController
@RequestMapping({"/api/security/decision", "/api/v1/security/decision"})
public class DecisionController {

    private static final Logger log = LoggerFactory.getLogger(DecisionController.class);

    private final SecurityDecisionService service;
    private final DeclaredScopeResolutionService declaredScopeResolver;

    public DecisionController(SecurityDecisionService service,
                              DeclaredScopeResolutionService declaredScopeResolver) {
        this.service = service;
        this.declaredScopeResolver = declaredScopeResolver;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> decide(@RequestBody Map<String, Object> body) {
        AssetRef asset = AssetRef.fromMap(asMap(body.get(DecisionChannelContract.KEY_ASSET_REF)));
        if (asset == null) {
            return failure(ApiResponse.badRequest(DecisionChannelContract.KEY_ASSET_REF + "."
                    + DecisionChannelContract.KEY_ASSET_TABLE + " 不能为空"));
        }
        String rawPurpose = asStr(body.get(DecisionChannelContract.KEY_PURPOSE));
        DecisionBundle.Purpose purpose = resolvePurpose(rawPurpose);
        if (purpose == null) {
            // D-B4-27：未识别的 purpose 过去被静默当 PAGE 放行（导出/AI 调用一旦词面失配，
            // 就以页面档的护栏矩阵被裁决并返回 200 —— 无错误、无日志、消费方无从察觉）。
            // 现按 400 显式拒绝；缺失/空白仍是既约定的 PAGE 默认，既有调用方行为不变。
            return failure(ApiResponse.badRequest(DecisionChannelContract.unrecognizedReason(rawPurpose)));
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
            return failure(ApiResponse.forbidden("无用户上下文（ECOS-AUTH-010），拒绝裁决"));
        }

        try {
            DecisionContext ctx = DecisionContext.of(userId, tenantId, null, null, roles, traceId)
                    // #142/B4-3d-3 只增：调用方声明的请求数据域（AI 通道的 §4.7.2 判据面）。
                    // #142/B4-3e-79 语义收口（D-B4-36）：AI 通道这段的 departmentId 不再取调用方自报，
                    // 改由服务端向属主域（data-engine）按 projectIds 展开真值——信任边界原文见下。
                    .withDataScope(resolveDeclaredScope(purpose,
                            DataScopeContract.read(body.get(DataScopeContract.KEY_RESOURCE))))
                    // #142/B4-3e-78 只增（D-B4-30）：AI 主裁决腿并入 /decision 的三枚可选键
                    // （environment.channel / tables / sqlDigest）。只入参、缺省不写；
                    // safeOpa 仅在 purpose==AI 时写出，非 AI 通道即使收到也忽略（载荷不变）。
                    .withAiAdjudication(aiChannelOf(asMap(body.get(DecisionChannelContract.KEY_ENVIRONMENT))),
                            aiTablesOf(body.get(DecisionChannelContract.KEY_TABLES)),
                            aiSqlDigestOf(body.get(DecisionChannelContract.KEY_SQL_DIGEST)));
            DecisionBundle bundle = service.decide(asset, purpose, ctx);
            return ok(ApiResponse.success(bundle.toMap()));
        } catch (IllegalArgumentException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (DeclaredScopeResolutionService.ScopeSourceUnavailableException e) {
            // 真值源不可达 ＝ 不可用态（503），禁折成"该项目没归属"（那会让策略落越权理由，理由是假的）
            log.warn("decide 声明域真值源不可用: asset={}, purpose={}, reason={}",
                    asset.key(), purpose, e.getMessage());
            return failure(ApiResponse.error(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                    RlsExecutionContract.SEC_UNAVAILABLE_CODE + "：声明域真值源不可用（fail-closed）"));
        } catch (Exception e) {
            log.warn("decide 端点异常: asset={}, purpose={}", asset.key(), purpose, e);
            return failure(ApiResponse.error(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                    RlsExecutionContract.SEC_UNAVAILABLE_CODE + "：security 内部裁决失败（fail-closed）"));
        }
    }

    /**
     * 声明域的服务端真值化（#142/B4-3e-79，收口差距 D-B4-36）。
     *
     * <p><b>只有 AI 通道进解析</b>：{@code resource.dataScope} 在策略侧的唯一读者是
     * {@code data_access} 的 AI 列判据（p1 域内 ALLOW / p2 越域 DENY），页面/导出通道
     * 的护栏输入形态与本批之前<b>逐字节一致</b>（{@code DataScopeContract.read} 的读数
     * 原样透传），不引入任何取数开销，也不改变既有载荷形态。判据面里"页面通道靠 RLS 行级
     * 下推、AI 通道靠声明域比对"的不对称，正是 D-B4-29 把本键从工具入参剔除的理由。</p>
     *
     * <p><b>AI 通道的两条纪律</b>：① 调用方自报的 {@code departmentId} 无条件丢弃
     * （恢复它等于恢复 D-B4-29 刚关掉的洞——本端点没有 RLS 下推，这条比对是唯一行范围闸）；
     * ② 真值只来自属主域取数，取不到就是取不到（落策略侧
     * {@code AI_DEPARTMENT_NOT_DECLARED}），<b>禁</b>伪造或折算成其中之一。</p>
     */
    private DataScopeContract.Declared resolveDeclaredScope(DecisionBundle.Purpose purpose,
                                                            DataScopeContract.Declared declared) {
        if (purpose != DecisionBundle.Purpose.AI) {
            return declared;
        }
        return declaredScopeResolver.resolveForAiTool(declared);
    }

    /**
     * #142/B4-3e-35（收口 D-B4-59 属主侧 9 枚真裸回腿之 security 裁决端点）：
     * 成功腿显式 {@code 200}。
     *
     * <p>本端点只增信封、不改签名语义——{@code ApiResponse} 只描述 body、没有任何 HTTP 状态能力，
     * 裸回时 403「无用户上下文」与 503「security 内部裁决失败（fail-closed）」都落 HTTP 200，
     * §4.7.2 要求的「不可用 ≠ 无权限」分态在最关键的这一个端点上恰好失效，任何只看状态行的
     * 中间层（网关、重试、告警、Playwright 用例）无从分派。修法＝信封不动、包
     * {@code ResponseEntity}，状态行一律从 {@code body.getCode()} 经
     * {@link DataBridgeException#resolveHttpStatus(int)} 单源推出（改码即改行）。</p>
     */
    private static <T> ResponseEntity<ApiResponse<T>> ok(ApiResponse<T> body) {
        return ResponseEntity.ok(body);
    }

    /**
     * #142/B4-3e-35：失败腿的状态行从信封 code 单源推出。
     *
     * <p>不可用腿的 503 由 {@code ApiResponse.error(503, …)} 的字面量改取
     * {@link RlsExecutionContract#SEC_UNAVAILABLE_HTTP} 单源常量——本类此前已引用
     * {@link RlsExecutionContract#SEC_UNAVAILABLE_CODE}，把同一枚不可用态的码与行分写两处
     * 正是 D-B4-52 的漂移形态。</p>
     */
    private static <T> ResponseEntity<ApiResponse<T>> failure(ApiResponse<T> body) {
        return ResponseEntity.status(DataBridgeException.resolveHttpStatus(body.getCode())).body(body);
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

    /** AI 工具载荷的通道标识（现成词表的竖写值，与录制 oracle 的 environment.channel 同源）。 */
    private static final String CHANNEL_AGENT = "AGENT";

    /**
     * #142/B4-3e-78（D-B4-30）：从请求体 {@code environment} 段读 AI 通道标识。
     *
     * <p>只承现成词表的值（{@code ToolAdjudicationRequest.CHANNEL_AGENT} 同值
     * {@value #CHANNEL_AGENT}，security 不依赖 ai-engine 故本类内定名、词表值不新造）；
     * 段存在但 channel 缺失/非文本/空白 ⇒ 按 AI 形态承流（该段本身就是 AI 工具载荷的
     * 通道推断面，生产唯一调用方恒带 channel，此兜底只防漏键把"AI 形状"降格成"无形状"）；
     * 段缺失或非 object ⇒ {@code null}（不携带，safeOpa 整段不写）。</p>
     */
    private static String aiChannelOf(Map<String, Object> environmentSegment) {
        if (environmentSegment == null) return null;
        Object channel = environmentSegment.get(DecisionChannelContract.KEY_CHANNEL);
        if (channel instanceof String s && !s.isBlank()) return s.trim();
        // channel 缺失/空：段存在即 AI 形态，承现成词表值（任务口径；生产调用方恒带 channel）
        return CHANNEL_AGENT;
    }

    /**
     * #142/B4-3e-78（D-B4-30）：请求体 {@code tables}（list of string，只入参）。
     * 非列表形态 ⇒ {@code null}（不携带）；列表内 null/空白元素丢弃（禁替调用方伪造表名）。
     */
    @SuppressWarnings({"rawtypes"})
    private static java.util.List<String> aiTablesOf(Object raw) {
        if (!(raw instanceof java.util.List)) return null;
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Object o : (java.util.List) raw) {
            if (o instanceof String s && !s.isBlank()) out.add(s);
        }
        return out;
    }

    /** #142/B4-3e-78（D-B4-30）：请求体 {@code sqlDigest}（string，只入参；非文本 ⇒ 不携带）。 */
    private static String aiSqlDigestOf(Object raw) {
        if (raw instanceof String s && !s.isBlank()) return s;
        return null;
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
