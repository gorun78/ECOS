package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 卷07 F07-18（W182 / C164，P0 安全）—— 场景域读端点安全接线单源。
 *
 * <p>X-56/X-57 的整改收口：{@link AbacQueryFilter} 客户端早已真实 fail-DENY，
 * 缺的是<b>接线</b>。场景域敏感读端点（graph / completeness / 认知四端点 /
 * runs / actions / decisions / 场景列表与详情）在返回前一律经过本守卫：</p>
 * <ul>
 *   <li>{@link #assertReadable(String)} —— 行级 RLS 裁决：security-engine <b>明确拒绝</b>
 *       （谓词 {@code "1=0"}）→ 403 PERMISSION_DENIED（铁律 §0.2.1 默认 DENY）；
 *       无策略（空串）→ 放行；<b>返回非平凡谓词而本端点无 SQL 下载体时同样拒绝</b>
 *       （宁 DENY 不超发，禁把全量捞回内存再滤，违 F07-18-1 红线）；
 *       <b>security 进程不可用</b>（连不上/超时/5xx）不再落到本守卫的 403 分支——
 *       客户端单点转 503 {@code ECOS-SEC-501}（{@link
 *       com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException}）直接上抛，
 *       详设-01 §4.7.2「不可用 ≠ 无权限」，2026-10-15 分态订正；</li>
 *   <li>{@link #applyColumns} —— 列级 CLS 收口（返回前裁剪，委托
 *       {@link AbacQueryFilter#filterColumns}，拒绝 → 空行集）。</li>
 * </ul>
 *
 * <p>#142/B4-3b 三通道合流第 1 步：{@link #exportDecision(String)} 是<b>导出通道</b>的咽喉，
 * 走三通道唯一裁决入口 {@code POST /api/v1/security/decision}（{@code purpose=export}）一次拿全
 * RLS/CLS/mask/OPA 四源，并把裁决证据（decisionId + 命中策略名 + 水印义务）交给导出件自证；
 * 页面/AI 两通道合流归 B4-3c（差距文档 D-B4-15）。</p>
 *
 * <p>依赖纪律：直接构造注入 {@link AbacQueryFilter}（禁 Optional/ObjectProvider 软依赖 ——
 * W182"缺客户端即启动失败"由 Spring 强依赖保证）。</p>
 */
@Component
public class ScenarioReadGuard {

    /** security-engine 明确 DENY（含 4xx 鉴权失败与响应不可解析）时 {@link AbacQueryFilter} 的行过滤兜底谓词。 */
    static final String DENY_PREDICATE = "1=0";

    private final AbacQueryFilter abacQueryFilter;

    public ScenarioReadGuard(AbacQueryFilter abacQueryFilter) {
        this.abacQueryFilter = abacQueryFilter;
    }

    /**
     * 行级裁决咽喉：场景域读端点返回前必须调用。
     *
     * @param entityCode security-engine 注册的实体编码（如 {@code scenario_graph}）
     * @throws ForbiddenOperationException RLS 明确拒绝，或存在本端点无法下推的非平凡策略谓词
     *                                     （fail-closed）；
     * @throws com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException
     *         security-engine 进程不可用（503，独立态，不由本守卫转 403）
     */
    public void assertReadable(String entityCode) {
        String predicate = abacQueryFilter.buildRowFilterCondition(entityCode);
        if (predicate == null || predicate.isEmpty()) {
            return; // 无策略登记 → 放行（security-engine 单源裁决，非本地推断）
        }
        if (DENY_PREDICATE.equals(predicate)) {
            throw new ForbiddenOperationException("scenario:read:" + entityCode);
        }
        // 有非平凡谓词但该读端点的数据载体不接 SQL 谓词 —— fail-closed，禁超发
        throw new ForbiddenOperationException("scenario:read:" + entityCode,
                Map.of("reason", "RLS 谓词无法下推至本端点数据载体，默认 DENY"));
    }

    /**
     * 列级收口：Map 行集返回前按 CLS 裁决裁剪；明确拒绝 → 空行集（默认 DENY）；
     * security-engine 进程不可用 → 503 {@code ECOS-SEC-501} 上抛，禁空行集冒充「无数据」。
     */
    public List<Map<String, Object>> applyColumns(String entityCode, List<Map<String, Object>> rows) {
        return abacQueryFilter.filterColumns(entityCode, rows);
    }

    // ═══════════════ 导出通道（详设-01 §4.7.2 第 2 列 / #142-B4-3b） ═══════════════

    /** 通道字面量（与 security 侧 {@code DecisionBundle.Purpose.EXPORT} 的 wire 形态同源）。 */
    static final String PURPOSE_EXPORT = "export";

    /**
     * 导出通道裁决咽喉：产出任何导出字节<b>之前</b>必须调用，返回值即导出件的
     * 「权限范围 / 水印」唯一出处（禁调用方再写常量，差距文档 D-B4-10/D-B4-11）。
     *
     * <p>判定表（全部 fail-closed，宁 DENY 不超发）：</p>
     * <ol>
     *   <li>{@code decide} 无裁决证据（security 明确 4xx / 应答缺段）→ 403；
     *       进程不可用不在此态 —— 503 {@code ECOS-SEC-501} 由客户端单点上抛；</li>
     *   <li>bundle.opa 护栏态<b>不作 403 硬门，只如实渲染进范围证据</b>（{@code opa=allow}
     *       / {@code deny:<原因>} / {@code ABSENT}）。理由（差距文档 D-B4-14/D-B4-18）：
     *       {@code SecurityDecisionService.safeOpa} 以非法策略名 {@code "data.access"}
     *       调 {@code OpaPolicyService.evaluate}（其白名单正则不含点号），护栏源
     *       <b>从未被真正咨询</b>，每次都是异常兜底 {@code allow=false +
     *       OPA_ERROR_FAIL_CLOSED}。据此 DENY 会把"护栏没接上"的引擎缺陷伪装成
     *       "策略拒绝"，并把导出功能打死＝换一种假绿。硬门待 B4-3c 补 {@code data.access}
     *       rego 后由配套用例打开；{@link ExportDecision#guardrailUnconsulted()}
     *       即该状态的消费侧读面；</li>
     *   <li>RLS 谓词 {@code 1=0} → 403；</li>
     *   <li>RLS 无命中策略且 {@code denyIfEmpty=true} → 403（§4.7.2「无权用户」行，
     *       验收载体 {@code AuditPackDenyTest}）；</li>
     *   <li>RLS 谓词非 {@code 1=1} 的非平凡形态 → 403「无法下推」（§4.7.2
     *       project/dept 行：导出件数据载体不接参数化谓词，宁 DENY 不做
     *       「全量捞回内存再滤」的超发，F07-18-1 红线同源，登记 D-B4-13）；</li>
     *   <li>以上皆不命中（有命中策略 + 谓词 {@code 1=1} 或空）→ 返回
     *       {@link ExportDecision}，其 {@code permissionScope} 可被审计反查。</li>
     * </ol>
     *
     * <p>注：审计包通道另有 {@code audit:export} 的 OPA 硬门（403/503 分态在位），
     * 那条走 {@code OpaPolicyService.evaluate} 的合法策略名，与本处"护栏未被咨询"不同态，
     * 不受本节裁决影响。</p>
     *
     * <p><b>列面仍走 {@link #applyColumns}</b>：本方法请求的 {@code columns=[]}，
     * bundle 的 {@code columns.names} 在整表请求形态下不可当白名单用（D-B4-16 在册），
     * 故裁剪仍按行集实际列经 {@code /cls/columns} 裁决 —— 两处都是 security 单源，
     * 不存在第二套真值。</p>
     *
     * @throws ForbiddenOperationException 上述任一 DENY 态
     */
    public ExportDecision exportDecision(String entityCode) {
        Map<String, Object> bundle = abacQueryFilter.decisionBundle(entityCode, PURPOSE_EXPORT);
        if (bundle == null) {
            throw new ForbiddenOperationException("scenario:export:" + entityCode,
                    Map.of("reason", "导出通道无裁决证据（security-engine 明确拒绝/应答缺段），默认 DENY"));
        }
        ExportDecision decision = ExportDecision.fromBundle(entityCode, bundle);
        // OPA 护栏态此处不作硬门（见判定表第 2 条 / D-B4-14、D-B4-18）：护栏源当前从未被
        // 真正咨询，据此 DENY 会把引擎缺陷伪装成策略拒绝。状态已经 ExportDecision
        // 如实渲染进 permissionScope 的 opa= 段，硬门待 B4-3c 补 data.access rego 后开启。
        if (decision.rlsPredicateIsDeny()) {
            throw new ForbiddenOperationException("scenario:export:" + entityCode);
        }
        if (decision.noPolicyMatched() && decision.rlsDenyIfEmpty()) {
            throw new ForbiddenOperationException("scenario:export:" + entityCode,
                    Map.of("reason", "无命中 RLS 策略且 denyIfEmpty=true，默认 DENY",
                            "decision", String.valueOf(decision.decisionId())));
        }
        if (!decision.rlsPredicateAbsent() && !decision.rlsPredicateIsAllowAll()) {
            throw new ForbiddenOperationException("scenario:export:" + entityCode,
                    Map.of("reason", "RLS 谓词无法下推至导出件载体，默认 DENY",
                            "predicate", decision.rlsPredicateTemplate(),
                            "decision", String.valueOf(decision.decisionId())));
        }
        return decision;
    }
}
