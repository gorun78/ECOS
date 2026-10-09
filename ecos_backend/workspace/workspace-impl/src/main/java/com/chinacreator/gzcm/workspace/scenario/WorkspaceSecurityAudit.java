package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient;
import org.springframework.stereotype.Component;

/**
 * 场景域审计薄适配（卷07 F07-18-2）。
 * <p>委托 security-engine 统一审计出口（Kafka {@code ecos.audit}），载荷只带 ID + 摘要，禁带结果原文。</p>
 */
@Component
public class WorkspaceSecurityAudit {

    private final WorkspaceSecurityEngineClient securityClient;

    public WorkspaceSecurityAudit(WorkspaceSecurityEngineClient securityClient) {
        this.securityClient = securityClient;
    }

    public void record(String action, String resourceId, Object result) {
        securityClient.audit(action, resourceId, result);
    }
}
