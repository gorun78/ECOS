package com.chinacreator.gzcm.engine.ai.oag;

import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Node 4: 安全检查器 — RLS(行级安全) + CLS(列级安全) + ABAC 裁决 + 内容合规检查。
 *
 * <p><b>H10-T1 (PMO-74 N8)</b>：原「空壳放行」实现改为强制经
 * {@link AiSecurityEngineClient} 调 security-engine REST（铁律 §2.4）：</p>
 * <ul>
 *   <li>ACTION 类意图：{@code POST /api/v1/security/policy-engine/evaluate} ABAC 裁决，
 *       DENY 或 security-engine 不可用 → 默认 DENY，节点置 BLOCKED</li>
 *   <li>所有意图：{@code POST /api/v1/security/rls/apply} 取行级过滤条件，
 *       拒绝/不可用 → BLOCKED；结果经 rlsFilters 传给 KnowledgeRetriever</li>
 *   <li><b>#142/B4-3c-2（D-B4-17）执行契约三态</b>：{@code "1=0"}＝拒绝、
 *       {@code "1=1"}＝明确不限制行、其余＝非平凡谓词原样下传；
 *       <b>空/缺失片段＝形态漂移 → BLOCKED</b>（旧口径把空串当"无行级限制"放行，
 *       即本批次关掉的 fail-open，登记见差距文档）</li>
 *   <li><b>#142/B4-3d-10（D-B4-26 余项）四态</b>：本节点改消费
 *       {@link AiSecurityEngineClient#applyRlsExecution}，"security 进程不可用"
 *       与"策略拒绝"在阻止原因与审计事件名上分成两态
 *       （{@code UNAVAILABLE:ECOS-SEC-501} / {@code DENIED:理由码}）。
 *       处置仍是 BLOCKED（fail-closed 不变），变的只有可寻址性——
 *       此前本节点用 {@code applyRls} 的 {@code "1=0"}，传输故障在 OAG 侧读起来
 *       与"这人没权限"完全同形，运维与申诉两边都拿不到线索。</li>
 *   <li>敏感操作黑名单（本地第一道闸）保留不变</li>
 * </ul>
 *
 * <p>外部端点（统一经 {@link AiSecurityEngineClient}，不在引擎内重复实现安全逻辑）：</p>
 * <ul>
 *   <li>POST /api/v1/security/policy-engine/evaluate</li>
 *   <li>POST /api/v1/security/rls/apply</li>
 *   <li>订正（#142/B4-3d-6 / D-B4-28）：本节点<b>不</b>调用 CLS 端点。
 *       早期注释把它列在本节点的外部端点清单里，而代码里从未有过那次调用 ——
 *       列面在 OAG 一侧只写"未判定"证据面（见 {@link #OAG_NO_COLUMN_UNIVERSE}），
 *       AI 通道真正的列面消费腿在工具咽喉
 *       （{@code AiToolColumnSurfaceGate} → {@code POST /api/v1/security/decision}）。</li>
 * </ul>
 */
@Component
public class SecurityCheckerNode implements OagNode {

    private static final Logger log = LoggerFactory.getLogger(SecurityCheckerNode.class);

    /** 敏感操作黑名单关键词（本地第一道闸，与 security-engine 裁决叠加） */
    private static final List<String> BLOCKED_PATTERNS = List.of(
            "DROP TABLE", "DROP DATABASE", "TRUNCATE",
            "ALTER SYSTEM", "SHUTDOWN", "GRANT ALL"
    );

    /**
     * OAG 列面"未判定"的理由码（#142/B4-3d-6 / D-B4-28）。
     *
     * <p>本链路没有数据腿，列宇宙无从谈起；这与"security 说没有可读列"（策略拒绝）
     * 和"security 没答"（不可用）是三种不同的事，必须用不同的码区分，
     * 否则审计里看不出该判的到底判过没有。</p>
     */
    public static final String OAG_NO_COLUMN_UNIVERSE = "OAG_NO_COLUMN_UNIVERSE";

    private final AiSecurityEngineClient securityEngineClient;

    public SecurityCheckerNode(AiSecurityEngineClient securityEngineClient) {
        this.securityEngineClient = securityEngineClient;
    }

    @Override
    public OagPipelineContext execute(OagPipelineContext ctx) {
        ctx.setCurrentNode("SecurityChecker");

        String intent = ctx.getIntent();
        String query = ctx.getRewrittenQuery() != null ? ctx.getRewrittenQuery() : ctx.getUserQuery();

        // 1. 敏感操作检测
        if (query != null) {
            String upper = query.toUpperCase();
            for (String pattern : BLOCKED_PATTERNS) {
                if (upper.contains(pattern)) {
                    ctx.setSecurityPassed(false);
                    ctx.setSecurityBlockReason("检测到禁止操作: " + pattern);
                    ctx.setStatus("BLOCKED");
                    securityEngineClient.auditQuietly(ctx.getUserId(), "oag:blocked_pattern", String.valueOf(intent), "BLOCKED");
                    log.warn("[OAG:{}] 安全阻止: {}", ctx.getTraceId(), pattern);
                    return ctx;
                }
            }
        }

        // 2. ACTION 类意图 — security-engine ABAC 裁决（H10-T1：不可用默认 DENY，不再空壳放行）
        if ("ACTION".equals(intent)) {
            log.info("[OAG:{}] ACTION意图 执行操作权限检查 userId={} tenantId={}",
                    ctx.getTraceId(), ctx.getUserId(), ctx.getTenantId());
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("resource", "oag_action");
            attrs.put("query", query);
            boolean allowed = securityEngineClient.evaluate(
                    ctx.getUserId(), ctx.getTenantId(), "oag:action", attrs);
            if (!allowed) {
                ctx.setSecurityPassed(false);
                ctx.setSecurityBlockReason("security-engine ABAC 裁决失败或不可用（默认 DENY）");
                ctx.setStatus("BLOCKED");
                securityEngineClient.auditQuietly(ctx.getUserId(), "oag:action", "abac_evaluate", "DENIED");
                log.warn("[OAG:{}] security-engine DENY: ACTION 意图被阻止 userId={}",
                        ctx.getTraceId(), ctx.getUserId());
                return ctx;
            }
        }

        // 3. RLS — security-engine 行级过滤条件（四态读数，#142/B4-3d-10 / D-B4-26 余项）
        //    片段字面量、状态模型与理由码单源于 RlsExecutionContract，本节点只做分态消费。
        RlsExecutionContract.Execution rls = securityEngineClient.applyRlsExecution(
                ctx.getUserId(), ctx.getTenantId(), intent == null ? "default" : intent.toLowerCase(),
                "oag_query", Map.of("tenantId", String.valueOf(ctx.getTenantId())));

        // 3a. 不可用态与拒绝态在此分岔：处置相同（都 BLOCKED，fail-closed 不变），
        //     对外码与审计事件名不同。§4.5.4 要的正是这种可寻址性——
        //     折叠成一条 "1=0" 之后，"security 挂了"与"这人没权限"在日志里长得一样，
        //     前者该叫运维、后者该走申诉，混在一起就是两边都误判。
        if (rls.unavailable()) {
            ctx.setSecurityPassed(false);
            ctx.setSecurityBlockReason("security-engine 进程不可用（"
                    + RlsExecutionContract.SEC_UNAVAILABLE_CODE + "，非「用户无权限」）: " + rls.reason());
            ctx.setStatus("BLOCKED");
            securityEngineClient.auditQuietly(ctx.getUserId(), "oag:rls", "apply",
                    "UNAVAILABLE:" + RlsExecutionContract.SEC_UNAVAILABLE_CODE);
            log.warn("[OAG:{}] RLS UNAVAILABLE → BLOCKED（不可用态，禁冒充无权限）userId={} reason={}",
                    ctx.getTraceId(), ctx.getUserId(), rls.reason());
            return ctx;
        }
        if (rls.denied()) {
            ctx.setSecurityPassed(false);
            ctx.setSecurityBlockReason("security-engine RLS 拒绝: " + rls.reason());
            ctx.setStatus("BLOCKED");
            // 审计理由带上生产者的机读码：零命中／未解析上下文／需绑参在应答里都是 1=0，
            // 只有理由能把它们分开（缺理由时按兜底码，禁自造）。
            securityEngineClient.auditQuietly(ctx.getUserId(), "oag:rls", "apply",
                    "DENIED:" + (rls.reason() == null ? RlsExecutionContract.REASON_DENY_FROM_SECURITY_ENGINE
                            : rls.reason()));
            log.warn("[OAG:{}] RLS DENY → BLOCKED userId={} reason={}",
                    ctx.getTraceId(), ctx.getUserId(), rls.reason());
            return ctx;
        }

        // 4. 执行契约收口（#142/B4-3c-2 / D-B4-17）：可用态只会给出
        //    "1=1"（明确不限制行）或不含 ? 的裸文本谓词两种形态（单源四道不变量 +
        //    本通道 rejectForUnbindableChannel 已把需绑参的片段转成拒绝态）。
        //    拿到空片段＝上游形态漂移，仍须拒绝——旧口径「空串＝无行级限制」而放行，
        //    正是 D-B4-17 关掉的 fail-open；此处是纵深防御的第二道，不依赖客户端永不漂移。
        String whereClause = rls.clause();
        if (whereClause == null || whereClause.isBlank()) {
            ctx.setSecurityPassed(false);
            ctx.setSecurityBlockReason("security-engine RLS 应答未给执行契约"
                    + "（" + RlsExecutionContract.REASON_WHERE_ABSENT + "）");
            ctx.setStatus("BLOCKED");
            securityEngineClient.auditQuietly(ctx.getUserId(), "oag:rls", "apply",
                    "DENIED:" + RlsExecutionContract.REASON_WHERE_ABSENT_CODE);
            log.warn("[OAG:{}] RLS 应答缺执行契约 → BLOCKED（不再按空串放行）userId={}",
                    ctx.getTraceId(), ctx.getUserId());
            return ctx;
        }

        // 5. 通过裁决 — RLS/CLS 结果写入上下文供下游节点（KnowledgeRetriever 等）消费
        ctx.setSecurityPassed(true);

        Map<String, Object> rlsFilters = new LinkedHashMap<>();
        rlsFilters.put("tenantId", ctx.getTenantId());
        // 键名单源（#142/B4-3d-5，差距 D-B4-24）：上下文里的片段键与 /rls/apply 应答执行面同名，
        // 用契约常量而非字面量，避免下游按第三套键名读数。
        rlsFilters.put(RlsExecutionContract.KEY_WHERE_CLAUSE, whereClause);
        ctx.setRlsFilters(rlsFilters);
        // 列面证据面（#142/B4-3d-6，D-B4-28 的 OAG 半边）：OAG 管道没有数据腿
        // （KnowledgeRetrieverNode 的 knowledgeChunks 是常量桩，零行零列）⇒ 没有可裁决的列全集。
        // 旧实现写 new LinkedHashMap<>()（空 Map）冒充"CLS 已过滤"，下游 ReasoningEngineNode
        // 按 !isEmpty() 判过就宣称「返回列已由 CLS 策略过滤」——空 Map 既不是可读集也不是结论，
        // 它唯一的实效是让一句假话听起来有依据。这里显式写"未判定"并把理由带上，
        // 宣称位改由 ColumnSurfaceContract.isEvaluatedFace 把关（默认不宣称）。
        ctx.setClsColumns(ColumnSurfaceContract.notEvaluatedFace(OAG_NO_COLUMN_UNIVERSE));

        log.debug("[OAG:{}] 安全检查通过 intent={} rls={}", ctx.getTraceId(), intent, whereClause);

        return ctx;
    }
}
