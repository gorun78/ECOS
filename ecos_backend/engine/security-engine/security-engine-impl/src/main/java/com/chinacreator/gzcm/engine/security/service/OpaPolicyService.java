package com.chinacreator.gzcm.engine.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

@Service
public class OpaPolicyService {

    private static final Logger log = LoggerFactory.getLogger(OpaPolicyService.class);
    // SEC-P0-3/P1-4: 添加连接超时和请求超时，防止 OPA 慢响应导致线程阻塞
    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** SEC-P0-3: 策略名称白名单正则 — 仅允许字母、数字、下划线、横线 */
    private static final java.util.regex.Pattern SAFE_NAME =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final String opaUrl;
    private final Path policyDir;

    public OpaPolicyService(
            @Value("${opa.url:http://localhost:8181}") String opaUrl,
            @Value("${opa.policy-dir:/opt/ecos/opa-policies}") String policyDir) {
        this.opaUrl = opaUrl;
        this.policyDir = Path.of(policyDir).toAbsolutePath().normalize();
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("engine", "OPA v0.63.0");
        try {
            var req = HttpRequest.newBuilder(URI.create(opaUrl + "/v1/data"))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            s.put("status", resp.statusCode() == 200 ? "connected" : "error");
            s.put("opaLatency", "ok");
        } catch (Exception e) {
            s.put("status", "disconnected");
            s.put("error", e.getMessage());
        }
        s.put("policies", listPolicyFiles().size());
        s.put("timestamp", System.currentTimeMillis());
        return s;
    }

    public Map<String, Object> evaluate(String policy, Map<String, Object> input) {
        if (input == null) input = Map.of();
        // SEC-P0-3: 校验 policy 名称防止注入
        validatePolicyName(policy);
        try {
            String opaPath = "/v1/data/ecos/" + policy + "/allow";
            String payload = "{\"input\":" + MAPPER.writeValueAsString(input) + "}";

            var req = HttpRequest.newBuilder(URI.create(opaUrl + opaPath))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("policy", policy);
            result.put("allow", resp.body().contains("\"result\":true"));
            result.put("opaStatus", resp.statusCode());
            return result;
        } catch (Exception e) {
            log.error("OPA evaluate failed", e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "OPA 评估失败: " + e.getMessage());
            return err;
        }
    }

    public List<String> listPolicies() {
        return listPolicyFiles();
    }

    public Map<String, String> getPolicy(String name) {
        validatePolicyName(name);
        Path file = resolveSafe(name);
        if (!Files.exists(file)) return null;
        try {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("name", name);
            result.put("content", Files.readString(file));
            return result;
        } catch (IOException e) {
            throw new RuntimeException("读取策略失败: " + e.getMessage(), e);
        }
    }

    public Map<String, String> updatePolicy(String name, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("缺少 content 字段");
        }
        validatePolicyName(name);
        Path file = resolveSafe(name);
        try {
            Files.writeString(file, content);
            log.info("Rego policy updated: {}.rego", name);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("name", name);
            result.put("status", "updated");
            result.put("message", "策略已更新，OPA 热加载生效");
            return result;
        } catch (Exception e) {
            throw new RuntimeException("更新失败: " + e.getMessage(), e);
        }
    }

    private List<String> listPolicyFiles() {
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(policyDir)) {
            stream.filter(p -> p.toString().endsWith(".rego"))
                  .map(p -> p.getFileName().toString().replace(".rego", ""))
                  .forEach(names::add);
        } catch (IOException e) {
            log.warn("List policies failed: {}", e.getMessage());
        }
        Collections.sort(names);
        return names;
    }

    /** SEC-P0-3: 校验策略名称 — 防止路径遍历和注入 */
    private void validatePolicyName(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("非法策略名称: 仅允许字母数字下划线横线(1-64字符)");
        }
    }

    /** SEC-P0-3: 安全解析文件路径 — 确保最终路径在 policyDir 内 */
    private Path resolveSafe(String name) {
        Path file = policyDir.resolve(name + ".rego").normalize();
        if (!file.startsWith(policyDir)) {
            throw new SecurityException("路径越界: 禁止访问 policyDir 之外的文件");
        }
        return file;
    }
}
