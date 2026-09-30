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
    // 详细设计-01 W35（P0）：opa.fail-close 配置项移除，fail-close 硬编码为 true 常量。
    // 任何"可配成 fail-open"的路径都违反铁律 §2.4-8 默认 DENY —— OPA 不可用一律拒绝。
    private static final boolean FAIL_CLOSE = true;

    public OpaPolicyService(
            @Value("${opa.url:http://localhost:8181}") String opaUrl,
            @Value("${opa.policy-dir:/opt/ecos/opa-policies}") String policyDir) {
        this.opaUrl = opaUrl;
        // W36：跨平台策略目录解析（ECOS_OPA_POLICY_DIR > 显式配置 > classpath 解包）
        this.policyDir = OpaPolicyDirResolver.resolve(policyDir);
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

            boolean allow = resp.body().contains("\"result\":true");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("policy", policy);
            result.put("allow", allow);
            result.put("opaStatus", resp.statusCode());
            result.put("source", "opa");
            // C.2.5 ③：裁决结果携带 obligations（deniedReason/maskHints/watermark/rateLimitKey）。
            // OPA 业务数据源尚未接入时返回空义务集；结构先落，接口契约保持可演进。
            result.put("obligations", parseObligations(resp.body()));
            result.put("policyRef", "ecos." + policy);
            // C.2.5 ④：裁决落审计线索（input 摘要，不落原文避免敏感外泄）
            result.put("inputDigest", sha256Hex(safeJson(input)));
            return result;
        } catch (Exception e) {
            log.warn("OPA 不可用, fail-close → DENY: policy={}, error={}", policy, e.getMessage());
            // W35（M0/P0）：fail-close 常量化 —— OPA 不可用一律拒绝，无 fail-open 分支。
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("policy", policy);
            result.put("source", "fallback");
            result.put("opaError", e.getMessage());
            result.put("allow", false);
            result.put("fallback", "DENY_OPA_DOWN");
            result.put("eccode", "ECOS-SEC-502");
            result.put("obligations", List.of(
                    Map.of("obligation", "DENIED", "deniedReason", "OPA_UNAVAILABLE_FAIL_CLOSED")));
            result.put("inputDigest", sha256Hex(safeJson(input)));
            log.warn("OPA fail-close → DENY: policy={}", policy);
            return result;
        }
    }

    /**
     * C.2.5 ③：从 OPA 响应解析 obligations。
     * <p>OPA 返回 {@code {"result": {...}}}；obligations 若能识别取 {@code result.obligations}
     * （对象数组），否则返回空集（fail-safe，不放大权限）。任何解析失败都归空。</p>
     */
    private List<Map<String, Object>> parseObligations(String opaBody) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            Map<String, Object> root = MAPPER.readValue(opaBody,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Object result = root.get("result");
            if (result instanceof Map<?, ?> r && r.get("obligations") instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> om) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mm = (Map<String, Object>) om;
                        out.add(mm);
                    }
                }
            }
        } catch (Exception ignored) {
            // 解析失败 → 空义务集
        }
        return out;
    }

    private static String safeJson(Object o) {
        try {
            return o == null ? "" : MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static String sha256Hex(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((s == null ? "" : s).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public List<String> listPolicies() {
        return listPolicyFiles();
    }

    /**
     * P1-2: 策略变更后使 OPA 重新加载策略（通过 OPA 的 policy reload API）
     */
    public void invalidateCache() {
        try {
            // OPA 自动热加载文件变更，此处主动触发一次健康检查确认策略已生效
            var req = HttpRequest.newBuilder(URI.create(opaUrl + "/v1/data"))
                    .timeout(Duration.ofSeconds(3))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("OPA 策略缓存已失效, 重新加载确认");
        } catch (Exception e) {
            log.warn("OPA 缓存清除失败(可能 OPA 不可用): {}", e.getMessage());
        }
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
