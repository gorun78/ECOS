package com.chinacreator.gzcm.runtime.core.security.policy.engine.parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 并行策略评估器
 * 支持并行评估多个策略规则，提升性能
 * 
 * @param <T> 策略类型
 * @param <C> 上下文类型
 * @param <R> 评估结果类型
 */
public class ParallelPolicyEvaluator<T, C, R> {
    
    private static final Logger logger = LoggerFactory.getLogger(ParallelPolicyEvaluator.class);
    
    private final ExecutorService executorService;
    private final long timeoutMillis;
    
    /**
     * 创建并行评估器
     * 
     * @param threadPoolSize 线程池大小，如果<=0则使用默认值（CPU核心数）
     * @param timeoutMillis 超时时间（毫秒），如果<=0则不设置超时
     */
    public ParallelPolicyEvaluator(int threadPoolSize, long timeoutMillis) {
        int poolSize = threadPoolSize > 0 ? threadPoolSize : Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "parallel-policy-evaluator");
            t.setDaemon(true);
            return t;
        });
        this.timeoutMillis = timeoutMillis;
    }
    
    /**
     * 并行评估策略列表
     * 
     * @param policies 策略列表
     * @param context 评估上下文
     * @param evaluator 评估函数
     * @param resultMerger 结果合并函数（用于合并多个评估结果）
     * @return 合并后的评估结果
     */
    public R evaluateParallel(List<T> policies, 
                              C context, 
                              Function<T, R> evaluator,
                              Function<List<R>, R> resultMerger) {
        if (policies == null || policies.isEmpty()) {
            return null;
        }
        
        if (policies.size() == 1) {
            // 单个策略，直接评估
            return evaluator.apply(policies.get(0));
        }
        
        // 创建并行评估任务
        List<CompletableFuture<R>> futures = new ArrayList<>();
        for (T policy : policies) {
            CompletableFuture<R> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return evaluator.apply(policy);
                } catch (Exception e) {
                    logger.warn("策略评估失败", e);
                    return null;
                }
            }, executorService);
            
            // 设置超时
            if (timeoutMillis > 0) {
                future = future.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
            }
            
            futures.add(future);
        }
        
        // 等待所有任务完成并收集结果
        List<R> results = new ArrayList<>();
        for (CompletableFuture<R> future : futures) {
            try {
                R result = future.get();
                if (result != null) {
                    results.add(result);
                }
            } catch (ExecutionException e) {
                // CompletableFuture.get() 抛出 ExecutionException
                // 检查 cause 是否为 TimeoutException
                Throwable cause = e.getCause();
                if (cause instanceof TimeoutException) {
                    logger.warn("策略评估超时", cause);
                } else {
                    logger.warn("策略评估异常", e);
                }
            } catch (InterruptedException e) {
                logger.warn("策略评估被中断", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.warn("策略评估异常", e);
            }
        }
        
        // 合并结果
        if (results.isEmpty()) {
            return null;
        }
        
        return resultMerger.apply(results);
    }
    
    /**
     * 并行评估策略列表（支持短路评估）
     * 当某个策略返回特定结果时，立即返回，不等待其他策略完成
     * 
     * @param policies 策略列表
     * @param context 评估上下文
     * @param evaluator 评估函数
     * @param shortCircuitCondition 短路条件（当评估结果满足此条件时立即返回）
     * @return 评估结果
     */
    public R evaluateParallelWithShortCircuit(List<T> policies,
                                               C context,
                                               Function<T, R> evaluator,
                                               Function<R, Boolean> shortCircuitCondition) {
        if (policies == null || policies.isEmpty()) {
            return null;
        }
        
        if (policies.size() == 1) {
            return evaluator.apply(policies.get(0));
        }
        
        // 创建并行评估任务
        List<CompletableFuture<R>> futures = new ArrayList<>();
        for (T policy : policies) {
            CompletableFuture<R> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return evaluator.apply(policy);
                } catch (Exception e) {
                    logger.warn("策略评估失败", e);
                    return null;
                }
            }, executorService);
            
            if (timeoutMillis > 0) {
                future = future.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
            }
            
            futures.add(future);
        }
        
        // 等待任一任务完成，检查是否满足短路条件
        CompletableFuture<R> anyOf = CompletableFuture.anyOf(
            futures.toArray(new CompletableFuture[0])
        ).thenApply(result -> (R) result);
        
        try {
            R result = anyOf.get();
            if (result != null && shortCircuitCondition.apply(result)) {
                // 满足短路条件，取消其他任务并返回
                futures.forEach(f -> f.cancel(true));
                return result;
            }
        } catch (Exception e) {
            logger.warn("并行评估异常", e);
        }
        
        // 不满足短路条件，等待所有任务完成并合并结果
        List<R> results = new ArrayList<>();
        for (CompletableFuture<R> future : futures) {
            try {
                if (!future.isCancelled()) {
                    R result = future.get();
                    if (result != null) {
                        results.add(result);
                    }
                }
            } catch (Exception e) {
                logger.warn("策略评估异常", e);
            }
        }
        
        // 返回第一个结果（或根据业务逻辑合并）
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * 关闭线程池
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
