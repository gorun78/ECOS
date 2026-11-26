package com.chinacreator.gzcm.workspace.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.workspace.scenario.CognitiveEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.DcchengClient;

/**
 * 场景算法执行（V367 批次 A）：登记表 → 执行器 → 统一结构化结果。
 *
 * <p>真值纪律：JAVA 执行器走 cognitive 通用执行面，数值原样回传；PYTHON 沙盒未装配、
 * 旧 L2 URL 未接执行器时返回 {@code UNAVAILABLE}，<b>不</b>伪造数值、不静默回退。
 * 算法失败只降级整轮运行，不抛 503 打断用户。</p>
 */
@Service
public class AlgorithmExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmExecutionService.class);

    private final WorkspaceDataDao dao;
    private final DcchengClient cognitive;
    private final PythonAlgorithmExecutor pythonExecutor;

    public AlgorithmExecutionService(WorkspaceDataDao dao, DcchengClient cognitive,
                                     PythonAlgorithmExecutor pythonExecutor) {
        this.dao = dao;
        this.cognitive = cognitive;
        this.pythonExecutor = pythonExecutor;
    }

    /** 执行一次算法；返回结构固定含 algorithmId/name/category/status/metrics/elapsedMs。 */
    public Map<String, Object> execute(String algorithmId, Map<String, Object> inputs) {
        List<Map<String, Object>> rows = dao.findAlgorithmById(algorithmId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "SCENARIO-ALGO-404: 算法未登记: " + algorithmId);
        }
        Map<String, Object> row = rows.get(0);
        String status = str(row, "status");
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            throw new BusinessException(400, "SCENARIO-ALGO-400: 算法未启用: " + algorithmId);
        }
        String kind = str(row, "executor_kind");
        String ref = str(row, "implementation_ref");
        long t0 = System.currentTimeMillis();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("algorithmId", algorithmId);
        out.put("name", row.get("name"));
        out.put("category", row.get("category"));

        if ("JAVA".equalsIgnoreCase(kind)) {
            if (ref == null || !ref.startsWith("java:")) {
                return unavailable(out, "执行器键非法（缺 java: 前缀）: " + ref, t0);
            }
            String code = ref.substring("java:".length());
            out.put("code", code);
            try {
                Map<String, Object> result = cognitive.executeAlgorithm(code, inputs);
                out.put("status", result.getOrDefault("status", "SUCCEEDED"));
                out.put("degraded", Boolean.TRUE.equals(result.get("degraded")));
                out.put("metrics", result.getOrDefault("metrics", Map.of()));
                out.put("assumptions", result.getOrDefault("assumptions", List.of()));
                out.put("message", result.get("message"));
            } catch (CognitiveEngineUnavailableException e) {
                log.warn("场景算法执行：认知通道不可用 code={} err={}", code, e.getMessage());
                out.put("status", "FAILED");
                out.put("degraded", true);
                out.put("metrics", Map.of());
                out.put("assumptions", List.of());
                out.put("message", "认知算法通道不可用: " + e.getMessage());
            }
        } else if ("PYTHON_SANDBOX".equalsIgnoreCase(kind)) {
            if (ref == null || !ref.startsWith("python:")) {
                return unavailable(out, "执行器键非法（缺 python: 前缀）: " + ref, t0);
            }
            if (!pythonExecutor.available()) {
                return unavailable(out, "研究沙盒未装配（ecos.research.sandbox.base-url 未配置）", t0);
            }
            String code = ref.substring("python:".length());
            out.put("code", code);
            Map<String, Object> result = pythonExecutor.execute(code, inputs);
            out.putAll(result);
            out.put("algorithmId", algorithmId);
            out.put("name", row.get("name"));
            out.put("category", row.get("category"));
        } else {
            out.put("status", "UNAVAILABLE");
            out.put("degraded", true);
            out.put("metrics", Map.of());
            out.put("assumptions", List.of());
            out.put("message", "旧 L2 算法面（implementation_ref URL）不参与问题运行");
        }
        out.put("elapsedMs", System.currentTimeMillis() - t0);
        return out;
    }

    /** prepare 阶段标注可用性（只读，不执行）。 */
    public Map<String, Object> decorate(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        String kind = str(row, "executor_kind");
        String ref = str(row, "implementation_ref");
        boolean executable = ("JAVA".equalsIgnoreCase(kind) && ref != null && ref.startsWith("java:"))
                || ("PYTHON_SANDBOX".equalsIgnoreCase(kind) && pythonExecutor.available()
                    && ref != null && ref.startsWith("python:"));
        out.put("executable", executable);
        if (!executable) {
            out.put("unavailableReason", "PYTHON_SANDBOX".equalsIgnoreCase(kind)
                    ? "研究沙盒未装配"
                    : "旧 L2 算法面不参与问题运行");
        }
        return out;
    }

    private static Map<String, Object> unavailable(Map<String, Object> out, String reason, long t0) {
        out.put("status", "UNAVAILABLE");
        out.put("degraded", true);
        out.put("metrics", Map.of());
        out.put("assumptions", List.of());
        out.put("message", reason);
        out.put("elapsedMs", System.currentTimeMillis() - t0);
        return out;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }
}
