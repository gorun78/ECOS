package com.chinacreator.gzcm.engine.security.rls;

/**
 * 详细设计-01 C.2.2 — RLS 绑定项 {@code CONTEXT_*} 的解析输入（服务端派生的主体属性）。
 *
 * <p>绑定值只能来自这里，<b>不得</b>由请求体/前端自报（分册 00 信任链：客户端
 * {@code X-Org-Id} 已被剥离）。本对象是"策略裁决时的主体快照"，与
 * {@code decision.DecisionContext} 语义重叠但归属不同层：{@code rls} 包不依赖
 * {@code decision} 包，避免 {@code SecurityDecisionService}↔RLS 双向依赖。</p>
 *
 * <p><b>字段可得性（2026-10-15 dev PostgreSQL 实测，登记为差距 D-B4-23）</b>：</p>
 * <ul>
 *   <li>{@code userId} / {@code tenantId} —— 信任链可读（JWT sub / tenant_id，
 *       {@code ServiceContext} 单源），{@code td_user} 亦有 {@code tenant_id} 列；</li>
 *   <li>{@code orgId} / {@code clearanceLevel} —— <b>现网无单源</b>：详设-01 假设的
 *       {@code TD_USER.ORG_ID} 在 dev 库 {@code ecos_control.td_user} 的 17 列里不存在，
 *       仓内也没有 {@code td_org} 表。取不到即为 {@code null}，渲染器按"未解析"处理并
 *       <b>拒绝</b>（禁把 {@code col = NULL} 当谓词下发，那等于把所有行都过滤掉之外的
 *       一切误解 —— 且掩盖了"上下文解析没接"这一缺陷）。</li>
 * </ul>
 */
public final class RlsSubjectContext {

    private final String userId;
    private final String tenantId;
    private final String orgId;
    private final Integer clearanceLevel;

    public RlsSubjectContext(String userId, String tenantId, String orgId, Integer clearanceLevel) {
        this.userId = blankToNull(userId);
        this.tenantId = blankToNull(tenantId);
        this.orgId = blankToNull(orgId);
        this.clearanceLevel = clearanceLevel;
    }

    /** 只有主体 id 的最小上下文（导出/内部调用链常用；组织域与密级如实为未解析）。 */
    public static RlsSubjectContext ofUserId(String userId) {
        return new RlsSubjectContext(userId, null, null, null);
    }

    public String userId()          { return userId; }
    public String tenantId()        { return tenantId; }
    public String orgId()           { return orgId; }
    public Integer clearanceLevel() { return clearanceLevel; }

    /**
     * 按绑定 source 取上下文值；不可得返 {@code null}（由渲染器转"未解析 ⇒ 拒绝"）。
     *
     * <p>非 {@code CONTEXT_*} 的 source（{@code STATIC_LIST} / 子查询类）不经此方法，
     * 返回 {@code null} 是刻意的：禁在此处兜底成"值不存在就当空串"。</p>
     */
    public Object resolveBySource(String source) {
        if (source == null) return null;
        switch (source) {
            case PredicateBinding.SRC_CONTEXT_USER_ID:    return userId;
            case PredicateBinding.SRC_CONTEXT_TENANT_ID:  return tenantId;
            case PredicateBinding.SRC_CONTEXT_ORG:        return orgId;
            case PredicateBinding.SRC_CONTEXT_CLEARANCE:  return clearanceLevel;
            default:                                      return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
