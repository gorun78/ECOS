package com.chinacreator.gzcm.engine.security.service.decision;

/**
 * 裁决主体（服务端上下文还原后的可信身份；Q4 通用 subject，禁 scenario: 前缀）。
 *
 * @param userId     服务端上下文用户 ID（mandatory，客户端不可控）
 * @param tenantId   服务端上下文租户 ID
 * @param orgId      服务端机构 ID（TD_USER 解析，W40：X-Org-Id 已剥离）
 * @param clearance  准入等级（L0~L4）
 * @param roles      角色集合（可空）
 */
public record DecisionSubject(String userId, String tenantId, String orgId,
                               Integer clearance, java.util.List<String> roles) {

    public DecisionSubject(String userId) {
        this(userId, null, null, null, null);
    }
}
