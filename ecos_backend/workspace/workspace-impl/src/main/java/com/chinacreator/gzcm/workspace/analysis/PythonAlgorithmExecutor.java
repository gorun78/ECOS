package com.chinacreator.gzcm.workspace.analysis;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Python 算法执行（V367 批次 B3）：单模板 {@code algorithms/python_runner.py} + 参数注入 → 沙盒。
 *
 * <p>沙盒只接受 Python；模板与参数以源码字面量注入（沙盒清空环境变量且禁网络/文件）。
 * 输出必须为单行 {@code RESULT <json>}；缺失即 FAILED，不把 stdout 猜成结论。</p>
 */
@Component
public class PythonAlgorithmExecutor {

    private static final Logger log = LoggerFactory.getLogger(PythonAlgorithmExecutor.class);
    private static final String TEMPLATE_PATH = "/algorithms/python_runner.py";

    private final ObjectProvider<SandboxResearchExecutor> sandboxProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String template;

    public PythonAlgorithmExecutor(ObjectProvider<SandboxResearchExecutor> sandboxProvider) {
        this.sandboxProvider = sandboxProvider;
        this.template = loadTemplate();
    }

    public boolean available() {
        return sandboxProvider.getIfAvailable() != null && template != null;
    }

    public Map<String, Object> execute(String code, Map<String, Object> params) {
        SandboxResearchExecutor sandbox = sandboxProvider.getIfAvailable();
        if (sandbox == null) {
            throw new IllegalStateException("研究沙盒未装配（ecos.research.sandbox.base-url 未配置）");
        }
        if (template == null) {
            throw new IllegalStateException("Python 算法模板缺失: " + TEMPLATE_PATH);
        }
        long t0 = System.currentTimeMillis();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        try {
            String paramsJson = objectMapper.writeValueAsString(params == null ? Map.of() : params);
            // 二次序列化为 Python 字符串字面量：JSON 的 true/false 不进源码，统一由 json.loads 解析
            String paramsLiteral = objectMapper.writeValueAsString(paramsJson);
            String script = template
                    .replace("\"__ALGO__\"", "\"" + code + "\"")
                    .replace("__PARAMS__", paramsLiteral);
            Map<String, Object> result = sandbox.executeCode("python", script, 30_000L);
            Object measured = result.get("measuredResult");
            boolean timedOut = Boolean.TRUE.equals(result.get("timedOut"));
            if (timedOut) {
                out.put("status", "FAILED");
                out.put("degraded", true);
                out.put("metrics", Map.of());
                out.put("assumptions", List.of("沙盒 30s 超时强杀"));
                out.put("message", "算法执行超时");
            } else if (!(measured instanceof Map<?, ?> m)) {
                out.put("status", "FAILED");
                out.put("degraded", true);
                out.put("metrics", Map.of());
                out.put("assumptions", List.of());
                out.put("message", "未产出 RESULT 行（stderr=" + abbreviate(result.get("stderr")) + "）");
            } else if (m.get("error") != null) {
                out.put("status", "FAILED");
                out.put("degraded", true);
                out.put("metrics", m);
                out.put("assumptions", List.of());
                out.put("message", String.valueOf(m.get("error")));
            } else {
                out.put("status", "SUCCEEDED");
                out.put("degraded", false);
                out.put("metrics", m);
                out.put("assumptions", List.of("Python 沙盒执行；实现为最小可用版，详见算法目录 description"));
                out.put("message", null);
            }
        } catch (RuntimeException e) {
            log.warn("Python 算法执行失败 code={} err={}", code, e.getMessage());
            out.put("status", "FAILED");
            out.put("degraded", true);
            out.put("metrics", Map.of());
            out.put("assumptions", List.of());
            out.put("message", "沙盒执行失败: " + e.getMessage());
        } catch (Exception e) {
            log.warn("Python 算法模板渲染失败 code={} err={}", code, e.getMessage());
            out.put("status", "FAILED");
            out.put("degraded", true);
            out.put("metrics", Map.of());
            out.put("assumptions", List.of());
            out.put("message", "参数序列化失败: " + e.getMessage());
        }
        out.put("elapsedMs", System.currentTimeMillis() - t0);
        return out;
    }

    private static String loadTemplate() {
        try (InputStream in = PythonAlgorithmExecutor.class.getResourceAsStream(TEMPLATE_PATH)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Python 算法模板读取失败", e);
            return null;
        }
    }

    private static String abbreviate(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        return s.length() <= 200 ? s : s.substring(0, 200);
    }
}
