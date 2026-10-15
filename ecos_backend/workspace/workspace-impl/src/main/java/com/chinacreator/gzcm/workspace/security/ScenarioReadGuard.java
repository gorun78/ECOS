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
}
