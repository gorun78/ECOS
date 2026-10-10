package com.chinacreator.gzcm.engine.ai.wagent.web;

import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest.Subject;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import org.springframework.stereotype.Component;

/**
 * W 域 web 面身份解析单源（§5.2 全端点 JWT 前提的引擎侧兜底）。
 *
 * <p><b>服务端主体唯一来源</b>：复用 F06-02 收口的 {@link ToolSubjectResolver}
 * （token 上下文 → Subject），<b>禁</b>取请求体/头自报的 userId/tenantId
 * （与 {@code NoDirectEngineTableAccessTest} 同族纪律的应用面形态）。
 * 解析失败（tenant/user 任一缺失）即 {@code E-AUTH 401} fail-closed——
 * 禁匿名落库、禁默认租户猜测（REQ-PLT-24/25 承）。</p>
 */
@Component
public class WAgentIdentityResolver {

    /** 服务端身份三元组（operator 即 initiated_by/create_by，幂等键三段之一）。 */
    public record Identity(String tenantId, String operator, String orgId) {
    }

    private final ToolSubjectResolver subjectResolver;

    public WAgentIdentityResolver(ToolSubjectResolver subjectResolver) {
        this.subjectResolver = subjectResolver;
    }

    public Identity require() {
        Subject subject = subjectResolver.resolve();
        if (subject == null || isBlank(subject.tenantId()) || isBlank(subject.userId())) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_AUTH,
                    "W 域端点要求认证主体（tenant/user 任一缺失即拒绝，fail-closed）", null);
        }
        return new Identity(subject.tenantId().trim(), subject.userId().trim(), null);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
