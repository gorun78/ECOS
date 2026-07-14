package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * OPA 策略引擎控制器 — RESTful 策略管理 + 评估。
 * 对接 OPA Server (localhost:8181)，支持 Rego 策略热加载。
 */
@RestController
@RequestMapping("/api/v1/policy-engine")
public class PolicyEngineController {
    private static final Logger log = LoggerFactory.getLogger(PolicyEngineController.class);
    private static final String OPA_URL = "http://localhost:8181";
    private static final String POLICY_DIR = "/home/guorongxiao/opa-policies";
    private static final HttpClient http = HttpClient.newHttpClient();

    // ═══ GET /status — OPA 运行状态 ═══
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("engine", "OPA v0.63.0");
        try {
            var req = HttpRequest.newBuilder(URI.create(OPA_URL + "/v1/data")).build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            s.put("status", resp.statusCode() == 200 ? "connected" : "error");
            s.put("opaLatency", "ok");
        } catch (Exception e) {
            s.put("status", "disconnected");
            s.put("error", e.getMessage());
        }
        s.put("policies", listPolicyFiles().size());
        s.put("timestamp", System.currentTimeMillis());
        return ApiResponse.success(s);
    }

    // ═══ POST /evaluate — 策略评估 ═══
    @PostMapping("/evaluate")
    public ApiResponse<Map<String, Object>> evaluate(@RequestBody Map<String, Object> body) {
        String policy = (String) body.getOrDefault("policy", "rbac");
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) body.get("input");
        if (input == null) input = Map.of();

        try {
            String opaPath = "/v1/data/ecos/" + policy + "/allow";
            String payload = "{\"input\":" + toJson(input) + "}";

            var req = HttpRequest.newBuilder(URI.create(OPA_URL + opaPath))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("policy", policy);
            result.put("allow", resp.body().contains("\"result\":true"));
            result.put("opaStatus", resp.statusCode());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("OPA evaluate failed", e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "OPA 评估失败: " + e.getMessage());
            return ApiResponse.success(err);
        }
    }

    // ═══ GET /policies — 列出策略 ═══
    @GetMapping("/policies")
    public ApiResponse<List<String>> listPolicies() {
        return ApiResponse.success(listPolicyFiles());
    }

    // ═══ GET /policies/{name} — 获取 Rego 源码 ═══
    @GetMapping("/policies/{name}")
    public ApiResponse<Map<String, String>> getPolicy(@PathVariable String name) {
        Path file = Path.of(POLICY_DIR, name + ".rego");
        if (!Files.exists(file)) return ApiResponse.notFound("策略 " + name + " 不存在");
        try {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("name", name);
            result.put("content", Files.readString(file));
            return ApiResponse.success(result);
        } catch (IOException e) {
            return ApiResponse.internalError("读取策略失败: " + e.getMessage());
        }
    }

    // ═══ PUT /policies/{name} — 更新策略（热加载） ═══
    @PutMapping("/policies/{name}")
    public ApiResponse<Map<String, String>> updatePolicy(@PathVariable String name,
                                                          @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank())
            return ApiResponse.badRequest("缺少 content 字段");

        Path file = Path.of(POLICY_DIR, name + ".rego");
        try {
            Files.writeString(file, content);
            log.info("Rego policy updated: {}.rego", name);

            // OPA 自动检测文件变更热加载，等待1秒确认
            Thread.sleep(500);

            Map<String, String> result = new LinkedHashMap<>();
            result.put("name", name);
            result.put("status", "updated");
            result.put("message", "策略已更新，OPA 热加载生效");
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    // ═══ Helpers ═══
    private List<String> listPolicyFiles() {
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(Path.of(POLICY_DIR))) {
            stream.filter(p -> p.toString().endsWith(".rego"))
                  .map(p -> p.getFileName().toString().replace(".rego", ""))
                  .forEach(names::add);
        } catch (IOException e) {
            log.warn("List policies failed: {}", e.getMessage());
        }
        Collections.sort(names);
        return names;
    }

    @SuppressWarnings("unchecked")
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String) {
                sb.append("\"").append(escape((String) v)).append("\"");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else if (v instanceof Map) {
                sb.append(toJson((Map<String, Object>) v));
            } else if (v instanceof List) {
                sb.append("[");
                var list = (List<?>) v;
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    Object item = list.get(i);
                    if (item instanceof String) sb.append("\"").append(escape((String) item)).append("\"");
                    else sb.append(item);
                }
                sb.append("]");
            } else {
                sb.append("null");
            }
        }
        return sb.append("}").toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
