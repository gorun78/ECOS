package com.chinacreator.gzcm.engine.security.decision;

import com.chinacreator.gzcm.common.security.decision.DataScopeContract;

import java.util.Collections;
import java.util.List;

/**
 * 详细设计-01 C.2 请求侧上下文（三通道同源：页面/导出/AI 都同形态进入 decide）。
 *
 * <p>字段来源：</p>
 * <ul>
 *   <li>{@code userId / tenantId / roles / traceId} — 分册 00 信任链（{@code ServiceContext.current()}）；
 *       W40 之后 orgId 只服务端解析（TD_USER.ORG_ID），客户端 X-Org-Id 被剥离，无法影响。</li>
 *   <li>{@code orgId / clearanceLevel} — 服务端派生；本页 M0 用 nullable 表示"尚未解析"，
 *       调用方不得把它当作可信输入。W40 关闭后由 org/clearance 服务端解析器填。</li>
 *   <li>{@code dataScope}（#142/B4-3d-2 之后只增，D-B4-20）— <b>调用方声明</b>的请求数据域
 *       （AI 通道的 {@code projectIds} / {@code departmentId}）。它<b>不是</b>主体属性：
 *       横向越权判据是"声明域 vs 服务端派生的 {@code orgId}"，谎报只会落 DENY，不会落放行。
 *       页面/导出通道留空（{@code null}）＝不发该段，既有载荷形态逐字不变。</li>
 * </ul>
 */
public final class DecisionContext {
    private final String userId;
    private final String tenantId;
    private final String orgId;               // 可为 null（未解析）
    private final Integer clearanceLevel;     // 可为 null（未解析）
    private final List<String> roles;
    private final String traceId;
    private final DataScopeContract.Declared dataScope;   // 可为 null（＝未声明请求域）

    public DecisionContext(String userId, String tenantId, String orgId,
                           Integer clearanceLevel, List<String> roles, String traceId) {
        this(userId, tenantId, orgId, clearanceLevel, roles, traceId, null);
    }

    /**
     * 全字段构造器（#142/B4-3d-3 只增：加声明域，不动原 6 参构造器 —— API 只增不改）。
     *
     * @param dataScope 调用方声明的请求数据域，{@code null} 或整段未声明＝不发 {@code resource}
     */
    public DecisionContext(String userId, String tenantId, String orgId,
                           Integer clearanceLevel, List<String> roles, String traceId,
                           DataScopeContract.Declared dataScope) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.orgId = orgId;
        this.clearanceLevel = clearanceLevel;
        this.roles = roles == null ? Collections.emptyList() : List.copyOf(roles);
        this.traceId = traceId;
        this.dataScope = dataScope;
    }

    public String userId()     { return userId; }
    public String tenantId()   { return tenantId; }
    public String orgId()      { return orgId; }
    public Integer clearanceLevel() { return clearanceLevel; }
    public List<String> roles()     { return roles; }
    public String traceId()    { return traceId; }

    /** 调用方声明的请求数据域；未声明时为 {@code null}（禁读成"全域"）。 */
    public DataScopeContract.Declared dataScope() {
        return dataScope;
    }

    /**
     * 只增式副本：在既有上下文上挂声明域（AI 通道用），其余字段逐字沿用。
     *
     * <p>之所以返回新实例而不是 setter：本类是不可变信任链载体，中途改写声明域会让
     * "谁声明了什么"在审计上失去出处（与 {@code orgId} 只服务端派生同纪律）。</p>
     */
    public DecisionContext withDataScope(DataScopeContract.Declared scope) {
        return new DecisionContext(userId, tenantId, orgId, clearanceLevel, roles, traceId, scope);
    }

    /** 从 raw map 组装（REST 只接受 userId/tenantId 显式字段；orgId/clearanceLevel 走服务端派生）。 */
    public static DecisionContext of(String userId, String tenantId,
                                     String orgId, Integer clearanceLevel,
                                     List<String> roles, String traceId) {
        return new DecisionContext(userId, tenantId, orgId, clearanceLevel, roles, traceId);
    }
}
