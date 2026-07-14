package com.chinacreator.gzcm.engine.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Service
public class OpaPolicyService {

    private static final Logger log = LoggerFactory.getLogger(OpaPolicyService.class);
    private static final HttpClient http = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String opaUrl;
    private final String policyDir;

    public OpaPolicyService(
            @Value("${opa.url:http://localhost:8181}") String opaUrl,
            @Value("${opa.policy-dir:/home/guorongxiao/opa-policies}") String policyDir) {
        this.opaUrl = opaUrl;
        this.policyDir = policyDir;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("engine", "OPA v0.63.0");
        try {
            var req = HttpRequest.newBuilder(URI.create(opaUrl + "/v1/data")).build();
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
        try {
            String opaPath = "/v1/data/ecos/" + policy + "/allow";
            String payload = "{\"input\":" + MAPPER.writeValueAsString(input) + "}";

            var req = HttpRequest.newBuilder(URI.create(opaUrl + opaPath))
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
        Path file = Path.of(policyDir, name + ".rego");
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
        Path file = Path.of(policyDir, name + ".rego");
        try {
            Files.writeString(file, content);
            log.info("Rego policy updated: {}.rego", name);
            Thread.sleep(500);
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
        try (var stream = Files.list(Path.of(policyDir))) {
            stream.filter(p -> p.toString().endsWith(".rego"))
                  .map(p -> p.getFileName().toString().replace(".rego", ""))
                  .forEach(names::add);
        } catch (IOException e) {
            log.warn("List policies failed: {}", e.getMessage());
        }
        Collections.sort(names);
        return names;
    }
}
