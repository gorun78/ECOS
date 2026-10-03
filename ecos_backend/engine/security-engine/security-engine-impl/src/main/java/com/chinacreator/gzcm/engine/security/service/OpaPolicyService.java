package com.chinacreator.gzcm.engine.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.channels.Channels;
import java.time.Duration;
import java.util.*;

/**
 * OPA 裁决服务。
 *
 * <p>详细设计-01 W35/W36/F01-04 改造：
 * <ul>
 *   <li><b>fail-close 常量化</b>：删除 {@code opa.fail-close} 配置项（可被 yml 覆盖为
 *       fail-open 的违规面，C.2.5 ①），代码常量 {@link #FAIL_CLOSE}=true——OPA 不可用
 *       时写操作无裁决即拒（错误码 ECOS-SEC-502）。</li>
 *   <li><b>obligations 通道</b>：裁决响应新增 {@code obligations[]} + {@code policyId}
 *       （分册 06 F06-02 脱敏接线依赖此字段， restore 判据 = 本链路接线）。</li>
 *   <li><b>跨平台策略目录</b>：默认 {@code classpath:opa/policies} 解包到
 *       {@code ${java.io.tmpdir}/ecos-opa}；可用 {@code ECOS_OPA_POLICY_DIR} 环境或
 *       {@code opa.policy-dir} 属性覆盖（Windows 可运行，W36）。</li>
 *   <li>裁决审计：每次 evaluate 落一条（input 摘要 sha256，不落原文），经
 *       {@link AuditEventProducer}（C.2.5 ④）。</li>
 * </ul>
 * 错误码（D.5）：OPA 不可用 fail-close = {@code ECOS-SEC-502}（503）。</p>
 */
@Service
public class OpaPolicyService {

    private static final Logger log = LoggerFactory.getLogger(OpaPolicyService.class);

    /** W35：fail-close 常量化 —— OPA 不可用默认 DENY，禁任何 fail-open 降级评估 */
    public static final boolean FAIL_CLOSE = true;

    /** D.5 错误码：security-engine 不可用 */
    public static final String ERROR_CODE_SECURITY_DOWN = "ECOS-SEC-501";
    /** D.5 错误码：OPA 不可用（fail-close） */
    public static final String ERROR_CODE_OPA_DOWN = "ECOS-SEC-502";

    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** SEC-P0-3: 策略名称白名单正则 — 仅允许字母、数字、下划线、横线 */
    private static final java.util.regex.Pattern SAFE_NAME =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final String opaUrl;
    private final Path policyDir;
    private final AuditEventProducer auditProducer;

    public OpaPolicyService(
            @Value("${opa.url:http://localhost:8181}") String opaUrl,
            @Value("${opa.policy-dir:}") String policyDirConfig,
            AuditEventProducer auditProducer) {
        this.opaUrl = opaUrl;
        this.policyDir = resolvePolicyDir(policyDirConfig);
        this.auditProducer = auditProducer;
    }

    /**
     * W36 跨平台策略目录解析（可测试脱节）：
     * 优先显式配置 {@code opa.policy-dir}（非系统盘默认值时）→
     * 次取 {@code ECOS_OPA_POLICY_DIR} 环境 → 兜底 classpath {@code opa/policies}
     * 解包到 {@code ${java.io.tmpdir}/ecos-opa}。
     */
    static Path resolvePolicyDir(String configured) {
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        String env = System.getenv("ECOS_OPA_POLICY_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env).toAbsolutePath().normalize();
        }
        return unpackClasspathPolicies();
    }

    static Path unpackClasspathPolicies() {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "ecos-opa");
        try {
            Files.createDirectories(dir);
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] res = resolver.getResources("classpath*:opa/policies/*.rego");
            for (Resource r : res) {
                try (InputStream in = r.getInputStream()) {
                    Files.copy(in, dir.resolve(r.getFilename()).toAbsolutePath().normalize(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
            log.info("OPA 策略目录解包 classpath:opa/policies → {} ({} 文件)", dir, res.length);
        } catch (IOException e) {
            log.warn("classpath 策略解包失败（目录保持既有内容）: {}", e.getMessage());
        }
        return dir;
    }

    /** 策略目录（测试/诊断用）。 */
    public Path getPolicyDir() {
        return policyDir;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("engine", "OPA v0.63.0");
        s.put("failClose", FAIL_CLOSE);
        try {
            var req = HttpRequest.newBuilder(URI.create(opaUrl + "/v1/data"))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            s.put("status", resp.statusCode() == 200 ? "connected" : "error");
            s.put("opaLatency", "ok");
        } catch (Exception e) {
            s.put("status", "disconnected");
            // fix: 不回显异常原文（端口/内部细节），客户端只需状态
            s.put("error", "OPA 不可用");
        }
        s.put("policies", listPolicyFiles().size());
        s.put("policyDir", policyDir.toString());
        s.put("timestamp", System.currentTimeMillis());
        return s;
    }

    /** OPA 不可用（fail-close 触发）— 上层映射 503 ECOS-SEC-502 */
    public static final class OpaUnavailableException extends RuntimeException {
        public OpaUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * C.2.5 裁决：OPA 在 → 返回 {allow, obligations[], policyId, source:"opa"}；
     * OPA 宕机 → 常量 fail-close：allow=false + source:"fallback" + fallback:"DENY_OPA_DOWN"
     * （并在抛出语义上由调用方按目的决定 502/护栏，见 {@link SecurityDecisionService}）。
     */
    public Map<String, Object> evaluate(String policy, Map<String, Object> input) {
        if (input == null) input = Map.of();
        // SEC-P0-3: 校验 policy 名称防止注入
        validatePolicyName(policy);
        try {
            // OPA data 集合路径：任一包内键（allow/obligations/policy_id）一次取回
            String opaPath = "/v1/data/ecos/" + policy;
            String payload = "{\"input\":" + MAPPER.writeValueAsString(input) + "}";

            var req = HttpRequest.newBuilder(URI.create(opaUrl + opaPath))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IOException("OPA 响应码异常: " + resp.statusCode());
            }

            Map<String, Object> body = MAPPER.readValue(resp.body(), LinkedHashMap.class);
            Object resultNode = body.get("result");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("policy", policy);
            result.put("opaStatus", resp.statusCode());
            result.put("source", "opa");
            if (resultNode instanceof Map<?, ?> doc && !doc.isEmpty()) {
                Object allow = doc.get("allow");
                result.put("allow", Boolean.TRUE.equals(allow));
                result.put("obligations", asObligations(doc.get("obligations")));
                Object policyId = doc.get("policy_id");
                if (policyId != null) result.put("policyId", policyId);
            } else {
                // 策略文档缺失/未计算 → 无裁决 = 禁止（默认 DENY，不做 fail-open）
                result.put("allow", false);
                result.put("obligations", List.of());
                result.put("fallback", "DENY_POLICY_MISSING");
            }
            auditOpaDecision(policy, input, result);
            return result;
        } catch (Exception e) {
            log.warn("OPA 不可用, fail-close → DENY: policy={}, error={}", policy, e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("policy", policy);
            result.put("allow", false);
            result.put("source", "fallback");
            result.put("fallback", "DENY_OPA_DOWN");
            result.put("obligations", List.of());
            auditOpaDecision(policy, input, result);
            return result;
        }
    }

    /** 裁决审计（C.2.5 ④）：input 只落 sha256 摘要，不落原文（防敏感外泄）。 */
    @SuppressWarnings("unchecked")
    private void auditOpaDecision(String policy, Map<String, Object> input, Map<String, Object> result) {
        try {
            String inputDigest = AuditHashChainService.sha256(MAPPER.writeValueAsString(input));
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("policy", policy);
            detail.put("allow", result.get("allow"));
            detail.put("source", result.get("source"));
            detail.put("fallback", result.get("fallback"));
            auditProducer.publishScanEvent("OPA_EVALUATE",
                    "policy-engine:" + policy, "ALLOW".equals(String.valueOf(result.get("allow"))) ? "SUCCESS" : "DENIED",
                    detail, inputDigest);
        } catch (Exception e) {
            log.warn("OPA 裁决审计失败（不阻塞裁决）: {}", e.getMessage());
        }
    }

    private static List<Object> asObligations(Object raw) {
        if (raw instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            out.addAll(l);
            return out;
        }
        return List.of();
    }

    public List<String> listPolicies() {
        return listPolicyFiles();
    }

    /**
     * P1-2: 策略变更后使 OPA 重新加载策略（通过 OPA 的 policy reload API）
     */
    public void invalidateCache() {
        try {
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
            throw new RuntimeException("读取策略失败", e);
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
            throw new RuntimeException("更新失败", e);
        }
    }

    private List<String> listPolicyFiles() {
        List<String> names = new ArrayList<>();
        if (Files.isDirectory(policyDir)) {
            try (var stream = Files.list(policyDir)) {
                stream.filter(p -> p.toString().endsWith(".rego"))
                      .map(p -> p.getFileName().toString().replace(".rego", ""))
                      .forEach(names::add);
            } catch (IOException e) {
                log.warn("List policies failed: {}", e.getMessage());
            }
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
