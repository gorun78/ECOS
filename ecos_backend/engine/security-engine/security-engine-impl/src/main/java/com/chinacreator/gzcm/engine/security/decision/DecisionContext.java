package com.chinacreator.gzcm.engine.security.decision;

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
 * </ul>
 */
public final class DecisionContext {
    private final String userId;
    private final String tenantId;
    private final String orgId;               // 可为 null（未解析）
    private final Integer clearanceLevel;     // 可为 null（未解析）
    private final List<String> roles;
    private final String traceId;

    public DecisionContext(String userId, String tenantId, String orgId,
                           Integer clearanceLevel, List<String> roles, String traceId) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.orgId = orgId;
        this.clearanceLevel = clearanceLevel;
        this.roles = roles == null ? Collections.emptyList() : List.copyOf(roles);
        this.traceId = traceId;
    }

    public String userId()     { return userId; }
    public String tenantId()   { return tenantId; }
    public String orgId()      { return orgId; }
    public Integer clearanceLevel() { return clearanceLevel; }
    public List<String> roles()     { return roles; }
    public String traceId()    { return traceId; }

    /** 从 raw map 组装（REST 只接受 userId/tenantId 显式字段；orgId/clearanceLevel 走服务端派生）。 */
    public static DecisionContext of(String userId, String tenantId,
                                     String orgId, Integer clearanceLevel,
                                     List<String> roles, String traceId) {
        return new DecisionContext(userId, tenantId, orgId, clearanceLevel, roles, traceId);
    }
}
