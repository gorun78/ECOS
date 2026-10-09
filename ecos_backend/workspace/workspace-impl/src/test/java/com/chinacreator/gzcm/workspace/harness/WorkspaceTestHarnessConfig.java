package com.chinacreator.gzcm.workspace.harness;

import com.chinacreator.gzcm.workspace.exception.WorkspaceExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 卷07 F07-24（W188）workspace 测试底座 —— 两形态装配骨架。
 *
 * <p><b>NO_INFRA_SCOPE</b>：本 config 只装配无 datasource / kafka / neo4j / minio 依赖的
 * bean（探针控制器 + 异常 advice），使薄切片在本机离线 {@code mvn -o} 下能起。
 * 这是 {@code *IT}（需 7-JAR 活栈）之外的<b>可执行</b>载体。</p>
 *
 * <p><b>两形态的来源</b>（ADR-15「制品口径 ≠ 承流口径」，X-3）：
 * <ul>
 *   <li>{@code harness-workspace-standalone} —— workspace-service 独立进程 :18090 形态，
 *       workspace Controller <b>注册</b>（当前唯一能承流的形态）</li>
 *   <li>{@code harness-gateway-hosted} —— gateway fat-JAR 形态，
 *       {@code GatewayApplication} 以 REGEX 排除 {@code workspace.controller..*}，
 *       因此 Controller 制品在 classpath 但<b>不进容器</b> ⇒ 生产 404</li>
 * </ul>
 * 两个 profile 都装 {@link WorkspaceExceptionHandler}（advice 不在被排除的包里），
 * 因此本底座能用同一组断言分别量化两种形态的"可达端点数"——
 * 这正是 {@code GatewayRouteIntegrityTest} 源码 regex 扫不出来的差异（F07-24 反身门禁的对象）。</p>
 */
@Configuration
public class WorkspaceTestHarnessConfig {

    @Bean
    @Profile("harness-workspace-standalone")
    public WorkspaceHarnessProbeController standaloneProbe() {
        return new WorkspaceHarnessProbeController();
    }

    @Bean
    @Profile("harness-workspace-standalone")
    public WorkspaceExceptionHandler standaloneAdvice() {
        return new WorkspaceExceptionHandler();
    }

    /** gateway 形态：advice 在、Controller 不在（复现 X-3 的排除效果）。 */
    @Bean
    @Profile("harness-gateway-hosted")
    public WorkspaceExceptionHandler gatewayHostedAdvice() {
        return new WorkspaceExceptionHandler();
    }
}
