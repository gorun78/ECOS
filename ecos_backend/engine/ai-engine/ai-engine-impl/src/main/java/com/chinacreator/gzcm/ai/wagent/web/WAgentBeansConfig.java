package com.chinacreator.gzcm.ai.wagent.web;

import com.chinacreator.gzcm.ai.wagent.WAgentOrchestratorFacade;
import com.chinacreator.gzcm.ai.wagent.candidate.CandidateService;
import com.chinacreator.gzcm.ai.wagent.candidate.CommitService;
import com.chinacreator.gzcm.ai.wagent.governance.KillSwitch;
import com.chinacreator.gzcm.ai.wagent.tool.CircuitBreakerView;
import com.chinacreator.gzcm.ai.wagent.tool.ToolContractRegistry;
import com.chinacreator.gzcm.ai.wagent.tool.ToolSearchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 分册10 F10 · W Agent 9 Controller 协调版 Bean 注册（R-59① 仅协调层）。
 *
 * <p>离线测试/在内存态协作单例集中登记：
 * 领域协作类（facade / kill-switch / tool-registry / circuit-breaker / candidate-service）
 * 均无 Spring 依赖，在此以 {@code @Bean} 单例化，避免在领域类上散落 {@code @Component}。
 * CommitService 的三 Port 在生产链路由 wiring 层注入真实现；离线测试给个 no-op
 * 桩，使 9 Controller 编译/启动不依赖真引擎端口。禁 JdbcTemplate / 无延迟。</p>
 */
@Configuration
public class WAgentBeansConfig {

    @Bean
    public WAgentOrchestratorFacade wAgentOrchestratorFacade() {
        return new WAgentOrchestratorFacade();
    }

    @Bean
    public KillSwitch wAgentKillSwitch() {
        return new KillSwitch();
    }

    @Bean
    public ToolContractRegistry wAgentToolContractRegistry() {
        return new ToolContractRegistry();
    }

    @Bean
    public ToolSearchService wAgentToolSearchService(ToolContractRegistry registry) {
        return new ToolSearchService(registry);
    }

    @Bean
    public CircuitBreakerView wAgentCircuitBreakerView() {
        return new CircuitBreakerView();
    }

    @Bean
    public CandidateService wAgentCandidateService() {
        return new CandidateService();
    }

    /**
     * 发布五步：Ports 未注入时 publish() 会抛 UnsupportedOperationException，
     * 由 wiring 层（生产链）或以测试钩子替换为真实现。此处占位保证 Bean 存在。
     */
    @Bean
    public CommitService wAgentCommitService(CandidateService candidates) {
        CommitService.CandidateCommitPort commitPort = (m, actor) -> {
            throw new UnsupportedOperationException("E-WA-COMMIT: CandidateCommitPort 未注入（离线态）");
        };
        CommitService.GitArchivePort gitPort = (repo, branch, message) -> {
            throw new UnsupportedOperationException("E-WA-COMMIT: GitArchivePort 未注入（离线态）");
        };
        CommitService.AuditEventPublisher audit = (topic, agg, evt, payload) -> {
            // no-op：审计事件由 wiring 层落 Kafka/DB，离线态吞。
        };
        return new CommitService(candidates, commitPort, gitPort, audit,
                "wagent-candidate-repo", "main");
    }
}
