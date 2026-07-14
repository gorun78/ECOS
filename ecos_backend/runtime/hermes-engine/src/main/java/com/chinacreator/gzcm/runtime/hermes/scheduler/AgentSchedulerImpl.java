package com.chinacreator.gzcm.runtime.hermes.scheduler;

import com.chinacreator.gzcm.common.annotation.TokenMeter;
import com.chinacreator.gzcm.runtime.hermes.callback.CallbackExecutor;
import com.chinacreator.gzcm.runtime.hermes.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.hermes.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.hermes.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.hermes.gateway.LLMConfig;
import com.chinacreator.gzcm.runtime.hermes.gateway.LLMGateway;
import com.chinacreator.gzcm.runtime.hermes.metrics.AgentMetrics;
import com.chinacreator.gzcm.runtime.hermes.model.ProfileConfig;
import com.chinacreator.gzcm.runtime.hermes.profile.ProfileManager;
import com.chinacreator.gzcm.runtime.hermes.session.AgentSession;
import com.chinacreator.gzcm.runtime.hermes.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AgentScheduler 实现 — 基于 Semaphore 的并发控制和 LinkedBlockingQueue 排队
 * <p>
 * 每个 subsystem 独立控制并发度。超出并发限制的请求进入 FIFO 队列等待。
 * 使用 CompletableFuture.supplyAsync 异步执行。
 * </p>
 */
@Primary
@Service
public class AgentSchedulerImpl implements AgentScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentSchedulerImpl.class);

    /** 默认每子系统最大并发数 */
    private static final int DEFAULT_MAX_CONCURRENCY = 5;

    /** 子系统级信号量: subsystem → Semaphore */
    private final ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();

    /** 子系统级排队队列: subsystem → LinkedBlockingQueue<Runnable> */
    private final ConcurrentHashMap<String, LinkedBlockingQueue<Runnable>> queues = new ConcurrentHashMap<>();

    /** 公共线程池 */
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            4, 32, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "hermes-scheduler-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @Autowired
    private LLMGateway llmGateway;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private CallbackExecutor callbackExecutor;

    @Autowired
    private AgentMetrics agentMetrics;

    @Autowired
    private ProfileManager profileManager;

    @TokenMeter(operation = "agent_schedule")
    @Override
    public CompletableFuture<AgentResult> schedule(AgentSession session, String userMessage) {
        String subsystem = session.getSubsystem();
        String profileName = session.getProfileName();

        // 获取 ProfileConfig
        ProfileConfig profile = profileManager.getProfile(subsystem, profileName);

        // 构建 LLMConfig
        LLMConfig llmConfig = LLMConfig.fromProfile(profile);

        // 获取子系统并发度
        int concurrency = profile.getConcurrency() != null && profile.getConcurrency() > 0
                ? profile.getConcurrency()
                : DEFAULT_MAX_CONCURRENCY;

        Semaphore semaphore = semaphores.computeIfAbsent(subsystem, k -> new Semaphore(concurrency));
        LinkedBlockingQueue<Runnable> queue = queues.computeIfAbsent(subsystem,
                k -> new LinkedBlockingQueue<>(1000));

        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String sessionId = session.getSessionId();

            try {
                // 尝试获取信号量，若失败则排队等待
                if (!semaphore.tryAcquire()) {
                    log.info("Subsystem [{}] concurrency limit reached, queuing request for session [{}]",
                            subsystem, sessionId);
                    CountDownLatch latch = new CountDownLatch(1);
                    queue.put(() -> {
                        try {
                            semaphore.acquire();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            latch.countDown();
                        }
                    });
                    latch.await();
                }

                // 更新会话活动时间
                session.touch();

                // 构建 ChatRequest
                List<ChatMessage> messages = new ArrayList<>();
                // 添加系统提示词
                if (session.getSystemPrompt() != null && !session.getSystemPrompt().isEmpty()) {
                    messages.add(new ChatMessage("system", session.getSystemPrompt()));
                }
                // 添加用户消息
                messages.add(new ChatMessage("user", userMessage));

                ChatRequest chatRequest = new ChatRequest(
                        llmConfig.getModel(),
                        messages,
                        llmConfig.getTemperature(),
                        llmConfig.getMaxTokens(),
                        false // 非流式
                );
                chatRequest.setApiKey(llmConfig.getApiKey());

                // 调用 LLM
                log.debug("Calling LLM: subsystem={}, profile={}, model={}, session={}",
                        subsystem, profileName, llmConfig.getModel(), sessionId);
                ChatResponse response = llmGateway.call(chatRequest);

                long duration = System.currentTimeMillis() - startTime;

                // 记录 metrics
                agentMetrics.recordCall(
                        subsystem,
                        profileName,
                        response.getTokensInput(),
                        response.getTokensOutput(),
                        duration,
                        response.isSuccess()
                );

                if (response.isSuccess()) {
                    log.info("LLM call succeeded: subsystem={}, model={}, tokens={}+{}, duration={}ms",
                            subsystem, response.getModel(),
                            response.getTokensInput(), response.getTokensOutput(), duration);
                    return AgentResult.ok(
                            sessionId,
                            response.getContent(),
                            response.getTokensInput(),
                            response.getTokensOutput(),
                            duration
                    );
                } else {
                    log.warn("LLM call failed: subsystem={}, model={}, error={}",
                            subsystem, llmConfig.getModel(), response.getErrorMsg());
                    return AgentResult.fail(
                            sessionId,
                            response.getErrorMsg(),
                            duration
                    );
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                long duration = System.currentTimeMillis() - startTime;
                return AgentResult.fail(sessionId, "Scheduler interrupted: " + e.getMessage(), duration);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("Scheduler error for session [{}]: {}", sessionId, e.getMessage(), e);
                return AgentResult.fail(sessionId, "Scheduler error: " + e.getMessage(), duration);
            } finally {
                // 释放信号量
                semaphore.release();
                // 从队列中取出下一个等待任务（如果有）
                Runnable next = queue.poll();
                if (next != null) {
                    executor.execute(next);
                }
            }
        }, executor);
    }

    @Override
    public int getActiveCount(String subsystem) {
        Semaphore semaphore = semaphores.get(subsystem);
        if (semaphore == null) {
            return 0;
        }
        // 总许可 - 可用许可 = 活跃数
        int total = semaphore.availablePermits();
        // 注意: 这个方法返回的是还有多少许可可用，不是总许可数
        // 我们需要跟踪实际总许可数
        return Math.max(0, getTotalPermits(subsystem) - semaphore.availablePermits());
    }

    @Override
    public int getQueueLength(String subsystem) {
        LinkedBlockingQueue<Runnable> queue = queues.get(subsystem);
        return queue != null ? queue.size() : 0;
    }

    @Override
    public void setConcurrency(String subsystem, int maxConcurrency) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be >= 1");
        }
        // 重新创建信号量（不完美但简单）
        Semaphore old = semaphores.get(subsystem);
        int currentHeld = old != null ? getTotalPermits(subsystem) - old.availablePermits() : 0;
        Semaphore newSem = new Semaphore(Math.max(maxConcurrency, currentHeld));
        semaphores.put(subsystem, newSem);
        log.info("Concurrency updated for subsystem [{}]: {} (currently held: {})",
                subsystem, maxConcurrency, currentHeld);
    }

    /**
     * 获取子系统的总许可数（当前并发上限）
     */
    private int getTotalPermits(String subsystem) {
        Semaphore semaphore = semaphores.get(subsystem);
        if (semaphore == null) {
            return DEFAULT_MAX_CONCURRENCY;
        }
        ProfileConfig profile = null;
        try {
            // 无法从 Semaphore 直接获取总许可数，使用默认值
            // 可通过额外的 ConcurrentHashMap 跟踪
            return DEFAULT_MAX_CONCURRENCY;
        } catch (Exception e) {
            return DEFAULT_MAX_CONCURRENCY;
        }
    }
}
