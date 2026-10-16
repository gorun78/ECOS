package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
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
 *   <li>{@link #assertReadable(String)} —— 行级 RLS 裁决：取 {@link AbacQueryFilter#buildRowFilter}
 *       的<b>执行契约三态</b>（#142/B4-3c-2 订正，旧写法按"片段是否为空串"分态 ⇒ fail-open）：
 *       security-engine <b>明确拒绝</b>（谓词 {@code "1=0"}）、应答<b>未发执行契约</b>
 *       （缺 {@code whereClause}/空白，{@link RlsFilter#DRIFT_ABSENT}）、参数不可完整绑定
 *       三种形态一律 403 PERMISSION_DENIED（铁律 §0.2.1 默认 DENY）；
 *       仅恒真 {@code 1=1}（裁决明确"不限制行"）放行；<b>返回非平凡谓词而本端点无 SQL 下载体时同样拒绝</b>
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
    static final String DENY_PREDICATE = RlsFilter.DENY_CLAUSE;

    private final AbacQueryFilter abacQueryFilter;

    public ScenarioReadGuard(AbacQueryFilter abacQueryFilter) {
        this.abacQueryFilter = abacQueryFilter;
    }

    /**
     * 行级裁决咽喉：场景域读端点返回前必须调用。
     *
     * <p><b>#142/B4-3c-2 分态订正（D-B4-17）</b>：判据由"片段字符串是否为空"改为
     * {@link RlsFilter} 的三态 —— 旧写法把「应答未发执行契约」当成「无策略」直接放行
     * （fail-open）。现在只有 security <b>明确给出恒真</b>（{@code 1=1}）才放行；
     * 缺片段/空白/参数不可绑定等漂移形态在 {@link RlsFilter} 构造期已归一为拒绝，
     * 与"策略 DENY"同走 403 分支，但 {@code details.denyReason} 保留两者区别供回查。</p>
     *
     * @param entityCode security-engine 注册的实体编码（如 {@code scenario_graph}）
     * @throws ForbiddenOperationException RLS 明确拒绝 / 应答形态漂移，或存在本端点无法下推的
     *                                     非平凡策略谓词（fail-closed）；
     * @throws com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException
     *         security-engine 进程不可用（503，独立态，不由本守卫转 403）
     */
    public void assertReadable(String entityCode) {
        RlsFilter rls = abacQueryFilter.buildRowFilter(entityCode);
        if (rls.allowAll()) {
            return; // 裁决明确「不限制行」（如 rls-finance-all）——不是"没给谓词"
        }
        if (rls.denied()) {
            throw new ForbiddenOperationException("scenario:read:" + entityCode,
                    Map.of("reason", rls.denyReason() == null
                                    ? DENY_PREDICATE + "（security-engine RLS 明确拒绝）" : rls.denyReason(),
                            RlsExecutionContract.KEY_UNRESOLVED_BINDINGS, rls.unresolvedBindings()));
        }
        // 有非平凡谓词但该读端点的数据载体不接 SQL 谓词 —— fail-closed，禁超发
        throw new ForbiddenOperationException("scenario:read:" + entityCode,
                Map.of("reason", "RLS 谓词无法下推至本端点数据载体，默认 DENY",
                        "predicate", rls.clause()));
    }

    /**
     * 列级收口：Map 行集返回前按 CLS 裁决裁剪；明确拒绝 → 空行集（默认 DENY）；
     * security-engine 进程不可用 → 503 {@code ECOS-SEC-501} 上抛，禁空行集冒充「无数据」。
     */
    public List<Map<String, Object>> applyColumns(String entityCode, List<Map<String, Object>> rows) {
        return abacQueryFilter.filterColumns(entityCode, rows);
    }

    // ═══════════════ 导出通道（详设-01 §4.7.2 第 2 列 / #142-B4-3b） ═══════════════

    /**
     * 通道字面量 —— 单源取 {@link DecisionChannelContract#PURPOSE_EXPORT}（#142/B4-3d-2，D-B4-27）。
     *
     * <p>此前这里是内联 {@code "export"}。字面量重复本身只是维护成本，真正的风险在
     * security 侧 {@code parsePurpose} 对 unrecognized 值静默回落 {@code PAGE}：
     * 词面一旦失配，导出调用就以页面档被裁决且返回 200。入口侧已改成未识别即 400，
     * 消费侧的取值也必须来自同一份词表 —— 两侧各写一套正是 D-B4-25 的成因。</p>
     */
    static final String PURPOSE_EXPORT = DecisionChannelContract.PURPOSE_EXPORT;

    /**
     * 导出通道裁决咽喉：产出任何导出字节<b>之前</b>必须调用，返回值即导出件的
     * 「权限范围 / 水印」唯一出处（禁调用方再写常量，差距文档 D-B4-10/D-B4-11）。
     *
     * <p>判定表（全部 fail-closed，宁 DENY 不超发；顺序即优先级）：</p>
     * <ol>
     *   <li>{@code decide} 无裁决证据（security 明确 4xx / 应答缺段）→ 403；
     *       进程不可用不在此态 —— 503 {@code ECOS-SEC-501} 由客户端单点上抛；</li>
     *   <li><b>OPA 硬门（B4-3c 开启）</b>：护栏<b>未给出裁决</b>
     *       （{@code POLICY_UNDEFINED} / {@code OPA_UNAVAILABLE} / opa 段缺失或状态键漂移）
     *       → 503 {@code ECOS-SEC-501}。理由：这三态的含义是"护栏没说话"，报 403 会把引擎/
     *       部署缺陷伪装成"用户无权限"（§4.7.2「不可用 ≠ 无权限」，D-B4-14/18/20/21）。
     *       硬门此前不能开启的唯一原因是策略名非法（{@code data.access} 过不了
     *       {@code OpaPolicyService} 的名称白名单 ⇒ 护栏从未被咨询），B4-3c 已补名合规的
     *       {@code ecos-docker/opa-policies/data_access.rego}；</li>
     *   <li>护栏给出<b>真实策略拒绝</b>（{@code EVALUATED_DENY}）→ 403，details 带
     *       {@code deniedReason}（可回查 rego 里的 violation 名）与 {@code decision}；</li>
     *   <li>RLS 谓词 {@code 1=0} → 403；</li>
     *   <li>RLS 无命中策略且 {@code denyIfEmpty=true} → 403（§4.7.2「无权用户」行，
     *       验收载体 {@code AuditPackDenyTest}）；</li>
     *   <li>RLS 谓词非 {@code 1=1} 的非平凡形态 → 403「无法下推」（§4.7.2
     *       project/dept 行：导出件数据载体不接参数化谓词，宁 DENY 不做
     *       「全量捞回内存再滤」的超发，F07-18-1 红线同源，登记 D-B4-13）；</li>
     *   <li>以上皆不命中（护栏 ALLOW + 有命中策略 + 谓词 {@code 1=1} 或空）→ 返回
     *       {@link ExportDecision}，其 {@code permissionScope} 可被审计反查。</li>
     * </ol>
     *
     * <p><b>dev 环境后果（登记，不隐瞒）</b>：dev 的 {@code ecos-opa} 容器以
     * {@code /opa run --server} 启动、{@code /v1/policies} 实数 0 个 ⇒ 仓内 rego 未被加载，
     * 本咽喉第 2 条会把导出如实报成 503（与既有「pipeline execute 因 OPA 无策略 fail-closed」
     * 同一基线）。恢复路径＝授权把 {@code ecos-docker/docker-compose.yml} 的 OPA 启动参数
     * 改为加载 {@code opa-policies/} 目录（或挂 bundle），属运维授权项，见差距文档剩余项。</p>
     *
     * <p><b>列面仍走 {@link #applyColumns}</b>：本方法请求的 {@code columns=[]}，
     * bundle 的 {@code columns.names} 在整表请求形态下不可当白名单用（D-B4-16 在册），
     * 故裁剪仍按行集实际列经 {@code /cls/columns} 裁决 —— 两处都是 security 单源，
     * 不存在第二套真值。</p>
     *
     * @throws ForbiddenOperationException 上述任一 403 态
     * @throws com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException
     *         护栏未给出裁决（503，与"策略拒绝"分态）
     */
    public ExportDecision exportDecision(String entityCode) {
        Map<String, Object> bundle = abacQueryFilter.decisionBundle(entityCode, PURPOSE_EXPORT);
        if (bundle == null) {
            throw new ForbiddenOperationException("scenario:export:" + entityCode,
                    Map.of("reason", "导出通道无裁决证据（security-engine 明确拒绝/应答缺段），默认 DENY"));
        }
        ExportDecision decision = ExportDecision.fromBundle(entityCode, bundle);
        if (decision.guardrailUnevaluated()) {
            throw new SecurityEngineUnavailableException("security/decision?purpose=" + PURPOSE_EXPORT,
                    "OPA 护栏未给出裁决: policyState=" + decision.opaPolicyState()
                            + " decision=" + decision.decisionId(), null);
        }
        if (decision.guardrailDenied()) {
            throw new ForbiddenOperationException("scenario:export:" + entityCode,
                    Map.of("reason", "OPA 策略裁决 DENY",
                            "deniedReason", String.valueOf(decision.opaState()),
                            "decision", String.valueOf(decision.decisionId())));
        }
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
