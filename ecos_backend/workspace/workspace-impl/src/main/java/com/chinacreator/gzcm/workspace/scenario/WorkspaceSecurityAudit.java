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

    /**
     * 带治理列的审计（§2.9.9 G5 scenario 域腿；#147BE）：发布/归档成功后把
     * {@code git_repository}/{@code git_commit_sha}/{@code content_hash} 一并投递，
     * 让发布证据可被对账面关联。
     */
    public void recordGovernance(String action, String resourceId, Object result,
                                 java.util.Map<String, Object> governanceColumns) {
        securityClient.auditGovernance(action, resourceId, result, governanceColumns);
    }
}
