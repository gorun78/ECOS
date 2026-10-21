package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.security.sql.SqlReadOnlyFloor;
import com.chinacreator.gzcm.engine.ai.dao.ToolPermissionBindingDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 工具裁决闸（详细设计-06 F06-01/F06-02/F06-03；由 {@code AgentToolSqlSecurityGate} 升格改名，
 * 类语义从"SQL 安全闸"扩为"全工具类型裁决闸"，SQL 白名单降为其子步骤）。
 *
 * <p>三条红线：</p>
 * <ol>
 *   <li><b>无条件裁决</b>：本闸对<b>所有</b> toolType（内置 7 + BUILTIN 3 + DB 注册 SQL/REST/BUILTIN
 *       + F06-11 的 5 个场景工具）都执行一次且仅一次 ABAC evaluate，
 *       不再以 {@code "SQL".equalsIgnoreCase(toolType)} 为前置条件（X-11）；</li>
 *   <li><b>三态 fail-closed</b>：security 不可用 / 超时（预算 2s，X-19/W145）/ 响应不可解析 /
 *       裁决字段缺失或不可识别（{@link AiSecurityEngineClient#verdictOf}）⇒
 *       {@link ToolDecision.Verdict#FAIL_CLOSED}
 *       （映射 {@link AiErrorClass#GUARDRAIL_FAIL_CLOSED}），与明确 DENY
 *       （{@link AiErrorClass#GUARDRAIL_DENIED}）严格分态，不折叠；</li>
 *   <li><b>必有留痕</b>：ALLOW / DENY / FAIL_CLOSED 三态都发
 *       {@code GUARDRAIL_EVAL} 审计事件（{@link GuardrailAuditPublisher#auditOrThrow}）。
 *       <b>审计发出失败 = 裁决整体失败</b>，工具不得执行（F06-03 要点 1）。</li>
 * </ol>
 *
 * <p>本类<b>不持有</b> RestTemplate：HTTP 一律经 {@link AiSecurityEngineClient}
 * （§2.5-3 底座收口，同时消掉 W148 基线里的一条 self-owned RestTemplate）。</p>
 *
 * <p>本地 SQL 地板是<b>附加</b>防御：命中即 DENY（本地硬性下限），
 * 但"通过本地地板"绝不构成放行依据 —— 放行权只在 security-engine（§2.4-7 禁重复实现裁决）。
 * 其中<em>只读形态判据</em>（注释／多语句／语句头部／危险关键字／危险函数／字面量掩码／表引用
 * 提取）自 #142/B4-3e-20 起单源在 {@link SqlReadOnlyFloor}（收口 D-B4-60「只读判据双源」），
 * 本类只保留<em>面专属</em>的表／列白名单腿；与属主查表面 {@code PhysicalQueryGuard} 的
 * 唯一口径差异是 {@code allowWith=true}（工具面允许 CTE）。</p>
 */
@Component
public class AgentToolPolicyGate {

    private static final Logger log = LoggerFactory.getLogger(AgentToolPolicyGate.class);

    /** ABAC 动作前缀（security 侧策略可按此授权/审计过滤）。 */
    public static final String ACTION_PREFIX = "agent:tool:";

    // ── SQL 地板的面专属校验（表／列白名单） ────────────────────────────
    // 只读形态判据（注释、多语句、语句头部、危险关键字、危险函数、字面量掩码、表引用提取）
    // 已单源化到 runtime/common-api 的 SqlReadOnlyFloor（#142/B4-3e-20 收口 D-B4-60）。

    private final AgentToolSqlWhitelist sqlWhitelist;
    private final AiSecurityEngineClient securityClient;
    private final GuardrailAuditPublisher auditPublisher;
    private final ObjectProvider<ToolPermissionBindingDao> bindingDaoProvider;

    public AgentToolPolicyGate(
            AgentToolSqlWhitelist sqlWhitelist,
            AiSecurityEngineClient securityClient,
            GuardrailAuditPublisher auditPublisher,
            ObjectProvider<ToolPermissionBindingDao> bindingDaoProvider) {
        this.sqlWhitelist = sqlWhitelist;
        this.securityClient = securityClient;
        this.auditPublisher = auditPublisher;
        this.bindingDaoProvider = bindingDaoProvider;
    }

    /** 非 SQL 类工具的裁决入口（等价于 {@code review(request, null, null)}）。 */
    public ToolDecision review(ToolAdjudicationRequest request) {
        return review(request, null, null);
    }

    /**
     * 完整裁决：主体校验 → SQL 地板（仅当存在待执行 SQL）→ security ABAC → 三态判定 → 审计留痕。
     *
     * @param request     四段载荷；{@code subject} 必须来自服务端 token 上下文
     * @param sqlText     待执行 SQL 原文（<b>不入载荷</b>，只做本地地板校验与摘要）
     * @param boundParams schema 声明的绑定参数名（白名单校验用，可空）
     */
    public ToolDecision review(ToolAdjudicationRequest request, String sqlText,
                               Collection<String> boundParams) {
        long startNs = System.nanoTime();
        ToolDecision decision = adjudicate(request, sqlText, boundParams, startNs);
        return withAudit(request, decision);
    }

    // ── 三态判定 ────────────────────────────────────────────────────────

    private ToolDecision adjudicate(ToolAdjudicationRequest request, String sqlText,
                                    Collection<String> boundParams, long startNs) {
        String toolKey = request == null || request.tool() == null ? "unknown" : request.tool();

        // 0) 主体必须来自 token 上下文：无主体 = 无法裁决 = FAIL_CLOSED（不是 DENY，§2.4-6）
        if (request == null || request.subject() == null || isAnonymous(request.subject().userId())) {
            log.warn("工具裁决 FAIL_CLOSED: 无 token 主体，无法裁决: tool={}", toolKey);
            return failClosed(startNs, "无 token 主体，禁止执行 Agent 工具（fail-closed）");
        }

        // 1) SQL 本地地板（子步骤；命中即 DENY，通过不作为放行依据）
        if (sqlText != null && !sqlText.isBlank()) {
            String floor = sqlLocalFloor(toolKey, sqlText, boundParams);
            if (floor != null) {
                log.info("工具裁决 DENY（SQL 本地地板）: tool={} reason={}", toolKey, floor);
                return deny(startNs, floor, null);
            }
        }

        // 2) security-engine ABAC（唯一裁决权威，一次且仅一次）
        Map<String, Object> input = request.toEvaluateInput();
        if (sqlText != null && !sqlText.isBlank()) {
            // 只给策略判定必需的结构性派生值，禁 SQL 原文入载荷（F06-02 要点 1）
            input.put("sqlDigest", digest(sqlText));
            input.put("readOnly", Boolean.TRUE);
            input.put("tables", extractTables(sqlText));
        }
        try {
            Map<String, Object> data = securityClient.evaluateData(
                    securityClient.evaluateBodyForAdjudication(input));
            long latency = elapsedMs(startNs);
            String verdict = AiSecurityEngineClient.verdictOf(data);
            if (AiSecurityEngineClient.V_DENY.equals(verdict)) {
                log.info("security-engine 明确裁决 DENY: tool={} user={} keys={}",
                        toolKey, request.subject().userId(), data.keySet());
                return deny(latency, "security-engine 裁决 DENY", policyIdOf(data));
            }
            if (AiSecurityEngineClient.V_FAIL_CLOSED.equals(verdict)) {
                // 裁决字段缺失/未知取值，或 security 侧 OPA 不可用的 fail-close 兜底（source=fallback）
                // —— 后者不是策略裁决，必须归 503 FAIL_CLOSED，禁折叠成 403 DENY（§4.2）
                log.warn("工具裁决 FAIL_CLOSED: 裁决字段缺失/不可识别或 security 侧兜底: tool={} source={} keys={}",
                        toolKey, data.get("source"), data.keySet());
                return failClosed(startNs,
                        "security-engine 未给出可识别的裁决（缺 decision/allow 字段或自身处于兜底态），已按 fail-closed 拒绝");
            }
            return ToolDecision.allow(policyIdOf(data),
                    ToolObligations.fromEvaluateData(data), latency);
        } catch (Exception e) {
            // 不可用 / 超时 / 响应不可解析 → FAIL_CLOSED（带堆栈，§2.4-6）
            log.warn("工具裁决 FAIL_CLOSED: security-engine 不可用/超时/不可解析: tool={} err={}",
                    toolKey, e.getMessage(), e);
            return failClosed(startNs, "security-engine 不可用或裁决响应不可解析（默认 DENY，fail-closed）");
        }
    }

    private ToolDecision withAudit(ToolAdjudicationRequest request, ToolDecision decision) {
        try {
            auditPublisher.auditOrThrow(request, decision);
            return decision;
        } catch (GuardrailAuditUnavailableException e) {
            log.error("护栏审计发出失败，裁决整体转为 FAIL_CLOSED（工具不执行）: tool={}",
                    request == null ? "unknown" : request.tool(), e);
            return ToolDecision.failClosed("护栏审计不可用：" + e.getMessage(), decision.latencyMs());
        }
    }

    // ── SQL 本地地板 ────────────────────────────────────────────────────

    /** @return null = 地板通过（仍须 ABAC）；非 null = 拒绝原因 */
    private String sqlLocalFloor(String toolKey, String sql, Collection<String> boundParams) {
        SqlReadOnlyFloor.Decision floor = SqlReadOnlyFloor.inspect(sql, true);
        if (!floor.allowed()) {
            return "检测到非只读形态: " + floor.reason();
        }
        Set<String> tables = floor.tables();
        if (tables.isEmpty()) {
            return "未识别到受白名单管控的表引用（禁止无表执行或 SQL 形态超出受控范围）";
        }
        boolean allColumnsMode = false;
        Set<String> allowedColumnsUnion = new LinkedHashSet<>();
        for (String table : tables) {
            if (!AgentToolSqlWhitelist.TABLE_IDENT.matcher(table).matches()) {
                return "非法表名形态: " + table;
            }
            Set<String> cols = sqlWhitelist.allowedColumns(table);
            if (cols == null) {
                log.warn("工具裁决 DENY: 表未列入白名单: tool={} table={}", toolKey, table);
                return "表未列入 Agent 工具 SQL 白名单: " + table;
            }
            if (cols.contains(AgentToolSqlWhitelist.ALL_COLUMNS)) {
                allColumnsMode = true;
            } else {
                allowedColumnsUnion.addAll(cols);
            }
        }
        if (boundParams != null) {
            for (String p : boundParams) {
                if (p == null || p.isBlank()) continue;
                boolean ok = allColumnsMode
                        ? AgentToolSqlWhitelist.COLUMN_IDENT.matcher(p).matches()
                        : allowedColumnsUnion.contains(p.toLowerCase(Locale.ROOT));
                if (!ok) {
                    return "绑定参数不是白名单授权列: " + p;
                }
            }
        }
        return null;
    }

    private Set<String> extractTables(String sql) {
        return SqlReadOnlyFloor.extractTables(sql);
    }

    /**
     * 受控表面（#142/B4-3d-6，D-B4-28 的取表入口）。
     *
     * <p>列面闸需要"这次工具调用涉及哪张（些）表"来定资产基数，而表引用提取自
     * #142/B4-3e-20 起单源在 {@link SqlReadOnlyFloor}（SQL 地板与 ABAC 载荷共用同一解析器）。
     * 本方法只是把单源结果按本面语义暴露给咽喉，禁 {@code ToolExecutorService} 或列面闸
     * 再自写一套 {@code FROM}/{@code JOIN} 解析 —— 两套解析器会对同一 SQL 给出不同表集，
     * "该裁哪张表"于是变成第二个真值。</p>
     *
     * <p>返回集合的基数不在这里判定（0/1/≥2 的语义属于
     * {@link AiToolColumnSurfaceGate.Asset#of(java.util.Collection)}），本方法只保证
     * <b>与裁决载荷同源</b>。</p>
     *
     * @param sqlText 工具 SQL 原文（可空；空/无可识别引用返回空集）
     * @return 小写归一的表名集合，顺序稳定（提取先后）
     */
    public Set<String> controlledTables(String sqlText) {
        return extractTables(sqlText);
    }

    private String digest(String sql) {
        String s = sql.replaceAll("\\s+", " ").trim();
        return "len=" + s.length() + " sha=" + Integer.toHexString(s.hashCode());
    }

    // ── 小工具 ─────────────────────────────────────────────────────────

    private static boolean isAnonymous(String userId) {
        return userId == null || userId.isBlank() || "anonymousUser".equals(userId);
    }

    private static String policyIdOf(Map<String, Object> data) {
        Object id = data.get("policyId");
        if (id == null) id = data.get("policy_id");
        return id == null ? null : String.valueOf(id);
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    private static ToolDecision deny(long latencyMs, String reason, String policyId) {
        return ToolDecision.deny(reason, policyId, latencyMs);
    }

    private static ToolDecision failClosed(long startNs, String reason) {
        return ToolDecision.failClosed(reason, elapsedMs(startNs));
    }

    /** 权限声明表中登记的必需主体属性（声明校验，不参与裁决判定，R-18 ②）。 */
    public Collection<String> declaredRequiredAttributes(String toolKey) {
        ToolPermissionBindingDao dao = bindingDaoProvider.getIfAvailable();
        if (dao == null) return Set.of();
        return dao.requiredAttributes(toolKey);
    }

    /** 错误分类映射（供咽喉/上层按 F06-14 生成结构化错误，禁自行拼串）。 */
    public static AiErrorClass errorClassFor(ToolDecision decision) {
        return switch (decision.verdict()) {
            case ALLOW -> null;
            case DENY -> AiErrorClass.GUARDRAIL_DENIED;
            case FAIL_CLOSED -> AiErrorClass.GUARDRAIL_FAIL_CLOSED;
        };
    }
}
