package com.chinacreator.gzcm.engine.cognitive2.service.decision;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.dao.DecisionDao;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decision Basis 只读解析服务（差距分析附录A 第 19 条 = 详设接口总表第 5416 行
 * {@code getDecisionBasis}；语义单源 = 《认知域与智能域补充详细设计》§10.2.9）。
 *
 * <h3>本类是什么</h3>
 * Agent 引用 {@code (decisionId, version, hash)} 三要素请求"这条决策当时依据的是什么"，
 * 本服务把认知属主内的决策底座族行集（V103 五元组 + 溯源）如实解析出来，并对 §10.2.9 冻结的
 * <b>八段依据</b>逐段给出可核验的解析态。Decision Basis <b>不是新表</b>（§10.2.9 原文定性 =
 * 跨域契约），本批零 DDL、零写口（铁律 REQ-COG-05「认知不新增 DB 表」+ 只读面）。
 *
 * <h3>三段诚实口径（禁把未解析冒充已解析）</h3>
 * <ul>
 *   <li>{@code RESOLVED} —— 该段有认知属主内可解析载体且<b>查到行</b>，载荷随行原样输出；</li>
 *   <li>{@code EMPTY} —— 载体在位、查询真实执行、结果为空集（与"没实现"严格区分）；</li>
 *   <li>{@code UNRESOLVED} —— 无可解析载体（跨属主接缝缺位或列面不存在），只出
 *       {@code owner} + {@code reason}，<b>绝不出载荷键</b>，禁合成占位值。</li>
 * </ul>
 * 八段中当前可解析的只有"授权范围/审批/失效条件"一段：审批链、失效区间
 * （{@code valid_from} / {@code valid_until} 实列）与未批准例外都在认知属主内。
 * 其余七段的不可解析原因是<b>逐段核实过的列面事实</b>，不是猜测：
 * 认知属主内 {@code ecos_cognitive.ecos_cognitive_evidence} / {@code _belief} / {@code _hypothesis} /
 * {@code _outcome_evaluation} / {@code _run_invalidation} 五张表均无指向决策底座的关联列
 * （2026-10-28 对 dev PG {@code information_schema.columns} 实测列清单），
 * 因此从 {@code decisionId} 反查这些资产必然要猜关联 ⇒ 本面拒绝猜。
 *
 * <h3>时间锚定原则（§10.2.9 原文「按当时 pinned 版本解析，不静默升级到最新版本」）</h3>
 * 调用方一旦带上 {@code version} 或 {@code hash}，就是在请求一个<b>锚定到当时版本</b>的快照。
 * 认知决策底座表 {@code ecos_cognitive.ecos_decision}（V103）没有 version/hash 列；唯一同时具备
 * {@code (decision_id, version_no, hash)} 三要素的载体 {@code ecos_cognitive.ecos_decision_source_ref}
 * 的 {@code decision_id} 按 V211 表内注释指向<b>控制域场景回执</b>（现
 * {@code ecos_control.ecos_scenario_decision_record}），不属认知决策 id 空间，且全仓零写口。
 * 两条腿都不成立 ⇒ 本服务对带 pinned 引用的请求回 404 {@code ECOS-COG-404D} +
 * {@code data.missing} 取证，<b>不</b>退化成"当前行 + 空 hash"的伪锚定 200
 * （那正是 §10.2.9 禁止的静默升级）。不引用版本时按当前态解析，并在
 * {@code citation.anchorStatus} 显式声明"未锚定"。
 *
 * <h3>三滤波器与门禁</h3>
 * 类前缀复用冻结常量 {@code CognitiveApiPaths.COGNITIVE}（{@code /api/v1/cognitive/} 族在网关
 * {@code VersionPrefixRewriteFilter} 属 KEEP，无新增改写条目 ⇒ {@code RouteManifestParityTest}
 * 25 条基线不动）；未在 {@code AnonymousEndpointRegistry} 登记 ⇒ 默认 {@code authenticated()}，
 * {@code ClearanceInterceptor} 前缀规则要求 L1（与同族 {@code DecisionController} 口径一致）；
 * 错误矩阵由 {@code CognitiveErrorAdvice} 承接（本包异常经 {@code controller} 包 advice 映射）。
 * 本类与 {@code DecisionBasisController} 已加入 {@code CogScope} 契约面白名单，
 * 受 schema 限定 / 方言 / 零 DML / 空集回落四门逐行判定。
 */
@Service
public class DecisionBasisService {

    /** 溯源条目实体类型字面量（与 {@code DecisionService#recordProvenance} 的写入侧同值，词表见其 Javadoc「decision/fact/rule」）。 */
    private static final String PROVENANCE_ENTITY_TYPE_DECISION = "decision";

    /** 决策 id 列宽（V103 {@code VARCHAR(64)}）；超宽即结构非法，不必进 SQL。 */
    private static final int DECISION_ID_MAX_LEN = 64;

    /** 底座行输出列面（V103 实列原样，键名与库侧一致防口径漂移；{@code metadata} jsonb 载体列不出面，见 {@link #projectDecision}）。 */
    private static final List<String> DECISION_COLUMNS = List.of(
            "id", "category", "scenario", "reasoning", "outcome", "confidence",
            "decision_maker", "valid_from", "valid_until", "created_at", "updated_at");

    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String STATUS_EMPTY = "EMPTY";
    private static final String STATUS_UNRESOLVED = "UNRESOLVED";

    private final DecisionDao decisionDao;

    public DecisionBasisService(DecisionDao decisionDao) {
        this.decisionDao = decisionDao;
    }

    /**
     * 只读解析一条决策的 Decision Basis。
     *
     * @param decisionId 认知决策底座主键（必填；空白 ⇒ 400，超列宽 ⇒ 400）
     * @param version    Agent 引用的 pinned 版本（可缺省；出现即必须可锚定，否则 404）
     * @param hash       Agent 引用的 pinned hash（可缺省；同上）
     * @return 只读解析结果（契约原文「只读解析结果，无写回」）
     */
    public Map<String, Object> resolve(String decisionId, String version, String hash) {
        String id = requireDecisionId(decisionId);
        String pinnedVersion = requireCitation(version, CognitiveApiPaths.PARAM_VERSION);
        String pinnedHash = requireCitation(hash, CognitiveApiPaths.PARAM_HASH);

        List<Map<String, Object>> rows = decisionDao.findDecisionById(id);
        if (rows.isEmpty()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_DECISION_BASIS,
                    CognitiveErrorCode.MSG_DECISION_BASIS_NOT_FOUND,
                    "ecos_cognitive.ecos_decision:" + id);
        }
        Map<String, Object> decision = projectDecision(rows.get(0));

        // 带 pinned 引用 ⇒ 先判可锚定性，不可锚定即 404（时间锚定原则：禁静默升级到当前版本）
        if (pinnedVersion != null || pinnedHash != null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_DECISION_BASIS,
                    CognitiveErrorCode.MSG_DECISION_BASIS_NOT_FOUND,
                    "pinned citation carrier (version_no/hash) for decision " + id
                            + "; ecos_cognitive.ecos_decision has no version/hash columns and"
                            + " ecos_cognitive.ecos_decision_source_ref keys on the control-domain"
                            + " scenario receipt (V211) with zero writers");
        }

        List<Map<String, Object>> approvals = decisionDao.findApprovalsByDecision(id);
        List<Map<String, Object>> exceptions = decisionDao.findPendingExceptions(id);
        List<Map<String, Object>> precedents = decisionDao.findPrecedentsByDecision(id);
        List<Map<String, Object>> provenance = decisionDao.queryProvenance(PROVENANCE_ENTITY_TYPE_DECISION, id);
        List<Map<String, Object>> upstream = decisionDao.findParentLinks(id);
        List<Map<String, Object>> downstream = decisionDao.findDownstreamLinks(id);
        Object categoryValue = decision.get("category");
        String category = categoryValue instanceof String s && !s.isBlank() ? s : null;
        List<Map<String, Object>> policies = category != null
                ? decisionDao.findActivePoliciesForBasis(category)
                : List.of();

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("approvals", approvals);
        evidence.put("pending_exceptions", exceptions);
        evidence.put("precedents", precedents);
        evidence.put("provenance", provenance);
        evidence.put("causal_upstream", upstream);
        evidence.put("causal_downstream", downstream);
        evidence.put("active_policies", policies);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decisionId", id);
        payload.put("readOnly", true);
        payload.put("citation", citation(id));
        payload.put("decision", decision);
        payload.put("segments", segments(category, approvals, exceptions, precedents, provenance,
                upstream, downstream, policies));
        payload.put("cognitiveEvidence", evidence);
        return payload;
    }

    // ── 入参校验（fail-loud，禁把空白当"未提供"）──────────────────────────

    private static String requireDecisionId(String decisionId) {
        if (decisionId == null || decisionId.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, CognitiveApiPaths.PARAM_DECISION_ID);
        }
        if (decisionId.length() > DECISION_ID_MAX_LEN) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM,
                    CognitiveApiPaths.PARAM_DECISION_ID + " exceeds column width " + DECISION_ID_MAX_LEN);
        }
        return decisionId;
    }

    /** pinned 引用段：缺省（null）合法；出现但全空白 = 结构非法 ⇒ 400，不静默降级成未提供。 */
    private static String requireCitation(String raw, String paramName) {
        if (raw == null) {
            return null;
        }
        if (raw.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM,
                    paramName + " present but blank");
        }
        return raw;
    }

    // ── 载荷装配 ──────────────────────────────────────────────────────

    /**
     * 底座行投影：只出 V103 实列，键名与库侧列名逐字一致。
     *
     * <p>{@code metadata} 是 jsonb 载体列，本面不解析其内容（把未解析 JSON 塞进"依据段"
     * 就是伪结构化），故不出现在载荷里；时间列统一转 ISO-8601 字符串，避免不同驱动的
     * {@code Timestamp} 序列化形态漂移。</p>
     */
    private static Map<String, Object> projectDecision(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String column : DECISION_COLUMNS) {
            out.put(column, render(row.get(column)));
        }
        return out;
    }

    private static Object render(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        return value;
    }

    /** 引用回显：三要素原样回显 + 显式声明本响应的锚定态（禁调用方把未锚定读成已锚定）。 */
    private static Map<String, Object> citation(String decisionId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("decisionId", decisionId);
        out.put("version", null);
        out.put("hash", null);
        out.put("anchored", false);
        out.put("anchorStatus", "UNPINNED_CURRENT_READ");
        out.put("anchorNote", "cognitive decision base has no version/hash columns;"
                + " pinned citation requests are rejected with 404 ECOS-COG-404D"
                + " instead of silently resolving to the current row");
        return out;
    }

    /**
     * §10.2.9 八段依据的逐段解析态（顺序 = 设计原文编号 ①~⑧，稳定可 diff）。
     *
     * <p>只有 ⑧ 授权/审批/失效一段可在认知属主内解析；①~⑦ 的 {@code reason} 是核实过的
     * 列面/接缝事实。UNRESOLVED 段不出 {@code payload} 键。</p>
     */
    private static List<Map<String, Object>> segments(String category,
                                                      List<Map<String, Object>> approvals,
                                                      List<Map<String, Object>> exceptions,
                                                      List<Map<String, Object>> precedents,
                                                      List<Map<String, Object>> provenance,
                                                      List<Map<String, Object>> upstream,
                                                      List<Map<String, Object>> downstream,
                                                      List<Map<String, Object>> policies) {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(unresolvedSegment("fact_snapshot_quality", "事实快照与质量态", "data-engine",
                "该段供数来自 data-engine 的 Readiness layer_d 与运行的 situation_snapshot_json；"
                        + "跨属主读取必须经属主读门面，认知侧无该接缝，本批不实装跨域直读"));
        out.add(unresolvedSegment("ontology_metric_caliber_version", "本体/指标/公式版本", "ontology-engine",
                "指标与口径版本属 ontology-engine 资产面；认知决策底座行未保存本体/口径版本快照列，"
                        + "按 decisionId 无法还原当时的版本"));
        out.add(unresolvedSegment("knowledge_profile_hypothesis_evidence", "知识画像/假设/证据引用", "kb-engine",
                "认知属主的 ecos_cognitive.ecos_cognitive_evidence / _belief / _hypothesis 三表"
                        + "均无指向决策底座的关联列（2026-10-28 dev PG 列清单实测），"
                        + "从 decisionId 反查只能靠猜关联 ⇒ 拒绝"));
        out.add(unresolvedSegment("forecast_run", "预测运行", "data-engine",
                "决策底座无 forecast_run / run 外键列，且确定性产物落业务域 ecos_dw（ADR-14），"
                        + "decisionId 侧无回链可读"));
        out.add(unresolvedSegment("diagnosis_counterfactual", "诊断与反事实", "cognitive",
                "属主虽在认知，但诊断/反事实面依赖 09 册 FC-02/FC-03 接缝，接缝缺位时端点显式 501"
                        + "（X-43 取证），故本段无可用供数"));
        out.add(unresolvedSegment("evaluation_risk_open_gaps", "评估、风险与未解决问题", "cognitive",
                "结果评估记录 ecos_cognitive.ecos_cognitive_outcome_evaluation 的检索键面是"
                        + " run / forecast_run / scenario（无 decision 关联列，列清单实测），"
                        + "与本端点入参不同源"));
        out.add(unresolvedSegment("options_and_comparison", "备选方案与比较", "workspace",
                "备选方案属场景装配面载体，认知决策族无对应列 ⇒ 不跨域直读"));
        out.add(authorizationSegment(approvals, exceptions, precedents, provenance,
                upstream, downstream, category, policies));
        return out;
    }

    /** ⑧ 授权范围/审批/失效条件 + 认知属主内可解析的底座支撑段。 */
    private static Map<String, Object> authorizationSegment(List<Map<String, Object>> approvals,
                                                            List<Map<String, Object>> exceptions,
                                                            List<Map<String, Object>> precedents,
                                                            List<Map<String, Object>> provenance,
                                                            List<Map<String, Object>> upstream,
                                                            List<Map<String, Object>> downstream,
                                                            String category,
                                                            List<Map<String, Object>> policies) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approvals", approvals);
        payload.put("pending_exceptions", exceptions);
        payload.put("precedents", precedents);
        payload.put("provenance", provenance);
        payload.put("causal_upstream", upstream);
        payload.put("causal_downstream", downstream);
        payload.put("active_policies", category == null ? "category 为空，策略按 category 精确匹配无从取值" : policies);
        Map<String, Object> seg = segment("authorization_approval_and_expiry",
                "授权范围、审批与失效条件", "cognitive",
                approvals.isEmpty() ? STATUS_EMPTY : STATUS_RESOLVED);
        seg.put("carrier", "ecos_cognitive.ecos_decision_approval + ecos_cognitive.ecos_decision"
                + " (valid_from/valid_until) + ecos_cognitive.ecos_decision_exception"
                + " + ecos_cognitive.ecos_decision_precedent + ecos_cognitive.ecos_provenance_entry"
                + " + ecos_cognitive.ecos_decision_causal_link + ecos_cognitive.ecos_decision_policy");
        seg.put("payload", payload);
        return seg;
    }

    private static Map<String, Object> unresolvedSegment(String key, String title, String owner, String reason) {
        Map<String, Object> seg = segment(key, title, owner, STATUS_UNRESOLVED);
        seg.put("reason", reason);
        return seg;
    }

    private static Map<String, Object> segment(String key, String title, String owner, String status) {
        Map<String, Object> seg = new LinkedHashMap<>();
        seg.put("segment", key);
        seg.put("title", title);
        seg.put("owner", owner);
        seg.put("status", status);
        return seg;
    }
}
