package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.approval.ApprovalTokenService;
import com.chinacreator.gzcm.engine.ai.wagent.approval.WAgentApprovalScope;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentActionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentActionDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Decision / Action 端点（详设-10 §5.2 #23 recordDecision / #24a draftAction / #24b commitAction）。
 *
 * <p><b>2026-11-10 实况更正</b>：本类原注释称「V241 待定、存储面未落地」，实为**过期信息** ——
 * {@code ecos_ai.ecos_wagent_action/_decision/_action_effect} 已由 **V283** 建表（dev 库实测存在）。
 * 因此 {@code draftAction} 与 {@code commitAction} 不再以「无表」为由降级：</p>
 * <ul>
 *   <li>#24a 草稿：真实落库（status=DRAFT），无副作用；</li>
 *   <li>#24b 提交：凭证一次性消费 → pre-commit 安全裁决（不可用即 DENY，fail-closed）
 *       → CAS DRAFT→COMMITTING → 派发属主 Engine 写端点 → COMMITTED；
 *       派发失败保持 COMMITTING 并回 502（不写「已提交」的假成功，可查可重试）。</li>
 * </ul>
 *
 * <p>#23 决策登记仍保持 501：决策表虽在，但「引用 pinned 评估」的业务闭环（评估快照校验）
 * 未接，强行落行会产生无依据决策 —— 属设计待定，不是表缺失。</p>
 */
@RestController
public class WAgentDecisionActionController {

    private static final Logger log = LoggerFactory.getLogger(WAgentDecisionActionController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 状态字面量（DDL 尚未收紧值域，见 V283 头注；此处为唯一出口）。 */
    static final String STATUS_DRAFT = "DRAFT";
    static final String STATUS_COMMITTING = "COMMITTING";
    static final String STATUS_COMMITTED = "COMMITTED";

    /** 属主通道受控词表（publish_target 列宽仅 24，只放类别；对象 ID 落 source_object_id 36）。 */
    static final String TARGET_WORKSPACE = "workspace";
    static final String TARGET_ONTOLOGY = "ontology";
    static final String TARGET_DATANET = "datanet";

    private final WAgentIdentityResolver identityResolver;
    private final ApprovalTokenService approvalTokenService;
    private final AiSecurityEngineClient securityClient;
    private final WAgentActionDao actionDao;
    private final String ownerBaseUrl;
    private final long ownerTimeoutMs;

    public WAgentDecisionActionController(WAgentIdentityResolver identityResolver,
                                          ApprovalTokenService approvalTokenService,
                                          AiSecurityEngineClient securityClient,
                                          WAgentActionDao actionDao,
                                          @Value("${ecos.wagent.owner-dispatch.base-url:http://localhost:8080}")
                                          String ownerBaseUrl,
                                          @Value("${ecos.wagent.owner-dispatch.timeout-ms:5000}")
                                          long ownerTimeoutMs) {
        this.identityResolver = identityResolver;
        this.approvalTokenService = approvalTokenService;
        this.securityClient = securityClient;
        this.actionDao = actionDao;
        this.ownerBaseUrl = ownerBaseUrl.endsWith("/")
                ? ownerBaseUrl.substring(0, ownerBaseUrl.length() - 1) : ownerBaseUrl;
        this.ownerTimeoutMs = ownerTimeoutMs;
    }

    /** #23 决策登记请求体（readiness_id 应指向 pinned 评估记录；仅形态校验，不落库）。 */
    public record DecisionRecordRequest(String questionId, String goalId, String readinessId,
                                        String rationaleText, String orgId) {
    }

    /** #24a 行动草稿请求体。{@code targetObjectId} 是属主侧对象 ID（落 source_object_id）。 */
    public record ActionDraftRequest(String optionId, String title, String ownerId, String role,
                                     String dueDate, String kpiText, String publishTarget,
                                     String publishMode, String targetObjectId) {
    }

    /** #24b 提交请求体（L3：approval_token 必填）。 */
    public record ActionCommitRequest(
            @JsonProperty("approvalToken") @JsonAlias("approval_token") String approvalToken) {
    }

    /** #23 决策记录（引用 pinned 评估）—— 评估快照校验未接，仍 501（表已在，缺的是业务闭环）。 */
    @PostMapping(WAgentApiPaths.DECISIONS_COLLECTION)
    public ResponseEntity<ApiResponse<WAgentDegradation.NotImplementedVO>> recordDecision(
            @RequestBody DecisionRecordRequest req) {
        identityResolver.require();
        if (req == null || isBlank(req.questionId())) {
            throw WAgentOrchestrationException.validation("question_id 必填（决策必须挂接问题面）");
        }
        if (isBlank(req.readinessId())) {
            throw WAgentOrchestrationException.validation("readiness_id 必填（§5.2 #23 须引用 pinned 评估记录）");
        }
        if (isBlank(req.rationaleText())) {
            throw WAgentOrchestrationException.validation("rationale_text 必填（决策必须留痕）");
        }
        return WAgentDegradation.notImplemented(
                "pinned 评估快照校验（readiness 行存在性/版本比对）未接；决策表 V283 已在，缺的是校验闭环");
    }

    /** #24a 行动草稿（真实落库，status=DRAFT，不产生任何外部副作用）。 */
    @PostMapping(WAgentApiPaths.ACTIONS_COLLECTION)
    public ResponseEntity<ApiResponse<ActionView>> draftAction(
            @RequestBody ActionDraftRequest req) {
        Identity identity = identityResolver.require();
        if (req == null || isBlank(req.title())) {
            throw WAgentOrchestrationException.validation("title 必填（行动草稿须可读指派）");
        }
        if (isBlank(req.ownerId())) {
            throw WAgentOrchestrationException.validation("owner_id 必填（无主行动禁草稿）");
        }
        if (isBlank(req.optionId())) {
            // V283 表约束：option_id NOT NULL —— 行动必须挂接决策选项，禁建"无出处"行动
            throw WAgentOrchestrationException.validation("option_id 必填（行动须挂接决策选项）");
        }
        String traceId = AiTraceSupport.currentTraceId();

        WAgentActionDO row = new WAgentActionDO();
        row.setId(UUID.randomUUID().toString());
        row.setOptionId(req.optionId());
        row.setTitle(req.title());
        row.setOwnerId(req.ownerId());
        row.setRole(req.role());
        row.setDueDate(parseDate(req.dueDate()));
        row.setKpiText(req.kpiText());
        row.setPublishTarget(requireTarget(req.publishTarget()));
        row.setPublishMode(req.publishMode());
        row.setSourceType("ACTION");
        row.setSourceObjectId(req.targetObjectId());
        row.setStatus(STATUS_DRAFT);
        row.setTraceId(traceId);
        row.setTenantId(identity.tenantId());
        row.setOrgId(identity.orgId());
        row.setDomain("default");
        row.setVersionNo("1");
        row.setCreateBy(identity.operator());
        row.setUpdateBy(identity.operator());
        row.setIsDeleted(0);
        actionDao.insert(row);

        return ResponseEntity.status(201).body(WAgentRunController.ok(ActionView.of(row)));
    }

    /**
     * #24b 行动提交（L3 真链路）：凭证 → 裁决 → CAS → 属主派发 → COMMITTED。
     *
     * <p>顺序刻意把「裁决」放在「凭证消费」之前：策略拒绝不该烧掉一次人工审批。
     * 消费之后的失败保持 {@code COMMITTING}（可查可重试），绝不落 {@code COMMITTED}。</p>
     */
    @PostMapping(WAgentApiPaths.ACTION_COMMIT)
    public ResponseEntity<ApiResponse<ActionCommitResult>> commitAction(
            @PathVariable(WAgentApiPaths.P_ACTION_ID) String actionId,
            @RequestBody(required = false) ActionCommitRequest req,
            HttpServletRequest request) {
        Identity identity = identityResolver.require();
        if (req == null || isBlank(req.approvalToken())) {
            throw WAgentOrchestrationException.validation("approval_token 必填（L3 提交凭证，§5.2 #24）");
        }
        WAgentActionDO action = actionDao.findById(actionId)
                .filter(a -> identity.tenantId().equals(a.getTenantId()))
                .orElseThrow(WAgentOrchestrationException::notFound);
        String traceId = AiTraceSupport.currentTraceId();

        // ① 凭证校验（不消费）：无效/越权/过期/已用 一律 403，不暴露对象状态
        approvalTokenService.verify(req.approvalToken(),
                WAgentApprovalScope.ACTION_COMMIT.name(), action.getId(), identity.operator());

        // ② pre-commit 安全裁决（security 不可用即 DENY，fail-closed）。
        // 批 A 授权墙双修：真传角色集（同 WAgentCandidateController publish 面）。
        var rolesIdentity = identityResolver.requireWithRoles();
        var attrs = AiSecurityEngineClient.actionCommitAttrs(action.getId(), action.getPublishTarget());
        if (!securityClient.evaluate(identity.operator(), identity.tenantId(),
                rolesIdentity.roles(), "action:commit", attrs)) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                    "pre-commit 安全裁决未通过（含 security-engine 不可用的 fail-closed 情形）",
                    Map.of("actionId", action.getId(), "traceId", traceId));
        }

        // ③ 派发目标必须在登记通道内（禁猜）
        Dispatch dispatch = resolveDispatch(action.getPublishTarget(), action.getSourceObjectId());

        // ④ 凭证一次性消费（CAS；重放 409）
        approvalTokenService.verifyAndConsume(req.approvalToken(),
                WAgentApprovalScope.ACTION_COMMIT.name(), action.getId(), identity.operator(), traceId);

        // ⑤ 状态 CAS：只有 DRAFT 能进 COMMITTING（并发双提交只赢一条）
        int moved = actionDao.transitionStatus(action.getId(), STATUS_DRAFT, STATUS_COMMITTING,
                req.approvalToken(), identity.operator());
        if (moved != 1) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_STATE,
                    "行动状态不允许提交（仅 DRAFT 可提交）: 当前状态=" + action.getStatus(),
                    Map.of("actionId", action.getId(), "status", String.valueOf(action.getStatus())));
        }
        actionDao.updateTraceId(action.getId(), traceId);

        // ⑥ 属主 Engine 派发（副作用唯一出口；失败不回写 COMMITTED）
        String dispatcher = dispatchOwner(dispatch, action, request.getHeader("Authorization"));
        if (dispatcher != null) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_DEP,
                    "属主 Engine 派发失败（保留 COMMITTING 供排查/重试）: " + dispatcher,
                    Map.of("actionId", action.getId(), "target", dispatch.path()));
        }

        actionDao.transitionStatus(action.getId(), STATUS_COMMITTING, STATUS_COMMITTED,
                null, identity.operator());
        return ResponseEntity.ok(WAgentRunController.ok(new ActionCommitResult(
                action.getId(), STATUS_COMMITTED, dispatch.path(), traceId)));
    }

    // ── 属主通道（V265 组 6 适配器登记的三个写端点）──────────────────

    private record Dispatch(String path) {
    }

    /**
     * 受控词表校验（列宽 24，只存类别）。草稿期就拒非法值，避免提交时才发现。
     */
    static String requireTarget(String publishTarget) {
        String t = publishTarget == null ? "" : publishTarget.trim();
        if (TARGET_WORKSPACE.equals(t) || TARGET_ONTOLOGY.equals(t) || TARGET_DATANET.equals(t)) {
            return t;
        }
        throw WAgentOrchestrationException.validation(
                "publish_target 取 workspace|ontology|datanet（属主通道受控词表，列宽 24）: " + publishTarget);
    }

    /**
     * 类别 + 对象 ID → 属主写端点（V265 组 6 适配器登记的三条路径）。
     */
    static Dispatch resolveDispatch(String publishTarget, String targetObjectId) {
        String t = requireTarget(publishTarget);
        if (targetObjectId == null || targetObjectId.isBlank()) {
            throw WAgentOrchestrationException.validation(
                    "target_object_id 必填（派发路径需要属主对象 ID）");
        }
        return switch (t) {
            case TARGET_WORKSPACE -> new Dispatch(
                    "/api/v1/workspace/scenarios/" + targetObjectId + "/actions");
            case TARGET_ONTOLOGY -> new Dispatch(
                    "/api/v1/ontology/actions/" + targetObjectId + "/execute");
            default -> new Dispatch("/api/v1/datanet/facts/action-outcomes");
        };
    }

    /** 属主派发载荷（键名/键序与旧 {@code LinkedHashMap} 一致；null 值照旧出键）。 */
    private record OwnerDispatchBody(String actionId, String title, String ownerId, String traceId) {
    }

    /** @return null = 派发成功；非 null = 失败原因（不回显堆栈/内部地址） */
    private String dispatchOwner(Dispatch dispatch, WAgentActionDO action, String authorization) {
        try {
            OwnerDispatchBody body = new OwnerDispatchBody(action.getId(), action.getTitle(),
                    action.getOwnerId(), action.getTraceId());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(ownerBaseUrl + dispatch.path()))
                    .timeout(Duration.ofMillis(ownerTimeoutMs))
                    .header("Content-Type", "application/json")
                    .header("X-Trace-Id", action.getTraceId() == null ? "" : action.getTraceId())
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
            if (authorization != null && !authorization.isBlank()) {
                builder.header("Authorization", authorization);
            }
            HttpResponse<String> resp = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Math.min(ownerTimeoutMs, 3_000L)))
                    .build()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.info("[action-commit] 属主派发成功 actionId={} target={} http={}",
                        action.getId(), dispatch.path(), resp.statusCode());
                return null;
            }
            return "HTTP " + resp.statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "派发被中断";
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        }
    }

    /** 行动视图（强类型；字段名与旧 Map 键逐字一致，键序不变）。 */
    public record ActionView(@JsonProperty("action_id") String actionId,
                             @JsonProperty("option_id") String optionId,
                             @JsonProperty("title") String title,
                             @JsonProperty("owner_id") String ownerId,
                             @JsonProperty("role") String role,
                             @JsonProperty("due_date") String dueDate,
                             @JsonProperty("kpi_text") String kpiText,
                             @JsonProperty("publish_target") String publishTarget,
                             @JsonProperty("publish_mode") String publishMode,
                             @JsonProperty("status") String status,
                             @JsonProperty("trace_id") String traceId) {

        static ActionView of(WAgentActionDO a) {
            return new ActionView(a.getId(), a.getOptionId(), a.getTitle(), a.getOwnerId(), a.getRole(),
                    a.getDueDate() == null ? null : a.getDueDate().toString(), a.getKpiText(),
                    a.getPublishTarget(), a.getPublishMode(), a.getStatus(), a.getTraceId());
        }
    }

    /** 提交结果（在行动视图之上附属主目标与 trace）。 */
    public record ActionCommitResult(@JsonProperty("action_id") String actionId,
                                     @JsonProperty("status") String status,
                                     @JsonProperty("ownerTarget") String ownerTarget,
                                     @JsonProperty("traceId") String traceId) {
    }

    private static LocalDate parseDate(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (RuntimeException e) {
            throw WAgentOrchestrationException.validation("due_date 需 ISO 日期（yyyy-MM-dd）: " + raw);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
