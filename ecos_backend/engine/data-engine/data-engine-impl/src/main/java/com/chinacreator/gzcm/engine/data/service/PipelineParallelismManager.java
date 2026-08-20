package com.chinacreator.gzcm.engine.data.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Pipeline 并行执行管理器（PMO-36 T2）。
 *
 * <p>拓扑分层后，同一层（无相互依赖）节点用线程池并发执行。</p>
 * <p>一次性线程池，用完关闭，不常驻（遵守禁止清单第3条）。</p>
 */
@Component
public class PipelineParallelismManager {

    private static final Logger log = LoggerFactory.getLogger(PipelineParallelismManager.class);

    /**
     * 并行执行同层节点。
     *
     * @param layerNodes 同层节点列表
     * @param executor 节点执行函数（输入 nodeId，返回结果）
     * @param <T> 结果类型
     * @return 各节点结果 Map（nodeId → result），异常节点值为 null + 日志记录
     */
    public <T> Map<String, T> executeLayerParallel(List<String> layerNodes, Function<String, T> executor) {
        if (layerNodes == null || layerNodes.isEmpty()) {
            return Collections.emptyMap();
        }

        // 单节点无需并行
        if (layerNodes.size() == 1) {
            String nodeId = layerNodes.get(0);
            Map<String, T> result = new LinkedHashMap<>();
            try {
                result.put(nodeId, executor.apply(nodeId));
            } catch (Exception e) {
                result.put(nodeId, null);
                log.warn("单节点执行失败: nodeId={}, error={}", nodeId, e.getMessage());
            }
            return result;
        }

        // 多节点并行：一次性线程池
        int poolSize = Math.min(4, layerNodes.size());
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        Map<String, CompletableFuture<T>> futures = new LinkedHashMap<>();

        for (String nodeId : layerNodes) {
            futures.put(nodeId, CompletableFuture.supplyAsync(() -> {
                try {
                    return executor.apply(nodeId);
                } catch (Exception e) {
                    log.warn("并行节点执行失败: nodeId={}, error={}", nodeId, e.getMessage());
                    return null;
                }
            }, pool));
        }

        // 等待全部完成
        try {
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                .get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("并行层执行等待异常: {}", e.getMessage());
        } finally {
            pool.shutdownNow();
        }

        // 收集结果
        Map<String, T> results = new LinkedHashMap<>();
        for (Map.Entry<String, CompletableFuture<T>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().getNow(null));
            } catch (Exception e) {
                results.put(entry.getKey(), null);
            }
        }

        log.info("并行层执行完成: nodes={}, poolSize={}", layerNodes, poolSize);
        return results;
    }
}
