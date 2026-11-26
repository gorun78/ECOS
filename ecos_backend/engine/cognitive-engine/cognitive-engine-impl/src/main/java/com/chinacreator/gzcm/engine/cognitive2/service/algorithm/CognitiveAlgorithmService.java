package com.chinacreator.gzcm.engine.cognitive2.service.algorithm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;

/**
 * 认知算法调度（V367 批次 A）：按执行器键路由到内置 {@link IAlgorithmExecutor}。
 *
 * <p>只做确定性分发与计时；算法可用性（ACTIVE/DISABLED）由 workspace 的登记表判定，
 * 本类不认识 {@code ecos_control}，避免跨域读表。未登记的执行器 404，入参非法 400，
 * 运行期异常 500 并带执行器键（不吞成「成功空结果」）。</p>
 */
@Service
public class CognitiveAlgorithmService {

    private static final Logger log = LoggerFactory.getLogger(CognitiveAlgorithmService.class);

    private final Map<String, IAlgorithmExecutor> executors = new TreeMap<>();

    public CognitiveAlgorithmService(List<IAlgorithmProvider> providers) {
        if (providers != null) {
            for (IAlgorithmProvider p : providers) {
                if (p == null || p.executors() == null) continue;
                executors.putAll(p.executors());
            }
        }
        log.info("认知算法执行器已装配: {}", executors.keySet());
    }

    public Map<String, Object> execute(String code, Map<String, Object> inputs) {
        IAlgorithmExecutor executor = executors.get(code);
        if (executor == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_MODEL_ROW,
                    "认知算法执行器不存在: " + code, code);
        }
        long t0 = System.currentTimeMillis();
        IAlgorithmExecutor.AlgorithmResult result;
        try {
            result = executor.execute(inputs == null ? Map.of() : inputs);
        } catch (IllegalArgumentException e) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    "算法入参非法[" + code + "]: " + e.getMessage(), code);
        } catch (CognitiveGuardException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("认知算法执行失败 code={}", code, e);
            throw new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                    "算法执行失败[" + code + "]: " + e.getMessage(), code);
        }
        long elapsed = System.currentTimeMillis() - t0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("status", result.degraded() ? "DEGRADED" : "SUCCEEDED");
        out.put("degraded", result.degraded());
        out.put("message", result.message());
        out.put("metrics", result.metrics());
        out.put("assumptions", result.assumptions());
        out.put("elapsedMs", elapsed);
        return out;
    }

    public List<String> codes() {
        return new ArrayList<>(executors.keySet());
    }
}
