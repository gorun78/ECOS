package com.chinacreator.gzcm.engine.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * W 编排域 Run 进度 SSE 表的轮询泵执行器（详设-10 §5.2 #7 streamRunEvents）。
 *
 * <p><b>为什么落 {@code ai.config} 且类名不带 {@code WAgent} 前缀</b>：两道门禁互斥夹住的唯一可行位——
 * {@code WAgentForbiddenDependencyArchTest} 红线 3 禁 {@code engine.ai.wagent..} 包内自持
 * {@code org.springframework.scheduling.} 类型，而 {@code TerminologyArchTest} 又要求一切
 * {@code WAgent*} 命名的类必须住在 {@code engine.ai.wagent..}。故线程池作为<b>模块级底座</b>提供，
 * 由 wagent 侧按 Bean 名限定注入（同 {@code workspace.config.WorkspaceTaskExecutorConfig} 的兜底形态：
 * runtime-task 只有 DB 持久化的计划/定时门面，无"提交即跑"执行器可复用）。</p>
 *
 * <p><b>为什么不复用 {@code applicationTaskExecutor}</b>：Boot 的 {@code TaskExecutionAutoConfiguration}
 * 带 {@code @ConditionalOnMissingBean(Executor.class)}，gateway 单体聚合面已有其它
 * {@code ThreadPoolTaskExecutor} Bean ⇒ 该自动配置整体退避，{@code applicationTaskExecutor} 在 gateway 态
 * 并不存在；而 {@code TaskExecutor} 类型候选 5 个且无 {@code @Primary}，按类型注入必歧义
 * （2026-10-16 实测 gateway {@code APPLICATION FAILED TO START}）。本池自备 + 显式按名注入，
 * 使 ai-engine-boot 切片与 gateway 聚合面解析结果一致，且不反向依赖 workspace 的 Bean。</p>
 *
 * <p>泵任务在单条流生命周期内独占线程（最长 {@code SSE_TIMEOUT_MS}）⇒ {@code queueCapacity=0} 直接交接、
 * 按需扩容至上限；超限提交方收到 {@code RejectedExecutionException} 由请求线程 fail-loud，不静默排队出过期流。</p>
 */
@Configuration
public class RunEventStreamExecutorConfig {

    /** W 域 SSE 轮询泵执行器 Bean 名单源（供 wagent 侧 {@code @Qualifier} 引用）。 */
    public static final String RUN_EVENT_STREAM_EXECUTOR = "runEventStreamTaskExecutor";

    @Bean(name = RUN_EVENT_STREAM_EXECUTOR)
    public ThreadPoolTaskExecutor runEventStreamTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("wagent-sse-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
