package com.chinacreator.gzcm.workspace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 卷07 F07-17（W181）底座收口 —— workspace 命名执行器工厂。
 *
 * <p>runtime-task（{@code ITaskManagementService}/{@code TaskSchedulerService}）只提供
 * DB 持久化的任务计划/定时调度门面，<b>无</b>通用"提交即跑"执行器门面可复用；按详细设计
 * F07-17 兜底条款，workspace 自备命名 {@link ThreadPoolTaskExecutor}，替换业务类此前的
 * 裸 {@code new Thread(...)}（PythonRuntime 流读取）与 {@code CompletableFuture.runAsync}
 * common pool 调用（ObjectKgSyncService KG 异步写）——禁止 ForkJoinPool.commonPool、
 * 禁止裸线程（gateway {@code ModuleDependencyArchTest#businessMustNotSelfSchedule}
 * 与 workspace {@code RuntimeConvergenceArchTest} 双门禁）。</p>
 *
 * <p>线程名带 {@code ws-} 前缀便于 jstack/日志归因；两个池按用途隔离：
 * <ul>
 *   <li>{@code workspaceTaskExecutor} —— 常规异步任务（KG 同步写等，可容忍排队）；</li>
 *   <li>{@code workspaceProcessIoExecutor} —— 进程 stdout/stderr 排空任务，
 *       queueCapacity=0（SynchronousQueue 直接交接）保证任务提交即起线程，
 *       语义等价于原裸线程，避免管道缓冲区写满导致进程假死。</li>
 * </ul></p>
 */
@Configuration
public class WorkspaceTaskExecutorConfig {

    /**
     * workspace 常规异步执行器（命名池，禁 common pool）。
     */
    @Bean(name = "workspaceTaskExecutor")
    public ThreadPoolTaskExecutor workspaceTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ws-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    /**
     * 进程 IO 排空执行器（PythonRuntime stdout/stderr 专用）。
     *
     * <p>queueCapacity=0 → SynchronousQueue 直接交接：有空闲线程立即执行，否则扩容至
     * maxPoolSize；超过上限时提交方收到 {@code RejectedExecutionException}，由调用方
     * 显式降级（强杀子进程并返回错误），而非静默排队造成管道死锁。</p>
     */
    @Bean(name = "workspaceProcessIoExecutor")
    public ThreadPoolTaskExecutor workspaceProcessIoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("ws-proc-io-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
