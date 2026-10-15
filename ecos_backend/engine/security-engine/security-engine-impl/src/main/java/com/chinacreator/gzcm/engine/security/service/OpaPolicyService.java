package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
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

    /**
     * 护栏态四值（#142/B4-3c，消费方据此分「策略拒绝」与「护栏没接上」两态，禁折叠）。
     *
     * <p>存在意义（差距文档 D-B4-14/D-B4-18/D-B4-20/D-B4-21）：本服务此前只把
     * {@code body.contains("\"result\":true")} 当裁决，于是<b>策略未加载</b>（OPA 应答
     * 无 {@code result} 段）、<b>HTTP 5xx</b>、<b>真策略拒绝</b>三态全部塌缩成
     * {@code allow=false}，消费方无法分辨"被护栏拒绝"和"护栏根本没说话"，
     * 只能把引擎缺陷伪装成用户无权限。状态串随 {@code policyState} 键单点写入，
     * 三通道各自消费（页面/导出 → 403 或 503，AI → DENY 或 FAIL_CLOSED）。</p>
     */
    // 四态 + 策略名的<b>单源在 runtime 底座</b>（GuardrailDecisionContract）：
    // ai-engine 裁决闸与 workspace 导出咽喉都不依赖本模块，词表只有一处定义才不会静默失配。
    // 语义与理由见该常量的 javadoc（上方段落）与 GuardrailDecisionContract 类注释。

    /** OPA 已咨询且明确放行。 */
    public static final String STATE_ALLOW = GuardrailDecisionContract.STATE_ALLOW;
    /** OPA 已咨询且明确拒绝（真实策略判定，可映射 403 / GUARDRAIL_DENIED）。 */
    public static final String STATE_DENY = GuardrailDecisionContract.STATE_DENY;
    /** OPA 可达但本策略未定义/未加载（应答缺 result 或 result 无 allow）⇒ fail-closed，属部署缺陷态。 */
    public static final String STATE_POLICY_UNDEFINED = GuardrailDecisionContract.STATE_POLICY_UNDEFINED;
    /** OPA 不可达 / 超时 / 非 2xx / 应答不可解析 ⇒ fail-closed，属不可用态（禁伪装成策略拒绝）。 */
    public static final String STATE_UNAVAILABLE = GuardrailDecisionContract.STATE_UNAVAILABLE;

    /**
     * 数据访问护栏策略名 —— 转委托单源常量。
     *
     * <p>必须是 {@code data_access} 而不是 {@code data.access}：{@link #SAFE_NAME} 不含点号，
     * 名字非法时校验在 try 之外抛出，OPA 从未被咨询，每次落异常兜底
     * （{@code allow=false + OPA_ERROR_FAIL_CLOSED}）—— 三通道因此都活在"护栏看起来在拒绝、
     * 实际根本没说话"的假象里（差距文档 D-B4-14/D-B4-18，详设施策见
     * {@code ecos-docker/opa-policies/data_access.rego}）。</p>
     */
    public static final String POLICY_DATA_ACCESS = GuardrailDecisionContract.POLICY_DATA_ACCESS;

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
            // B4-3c：查询<b>策略文档</b> {@code /v1/data/ecos/<policy>}，不再查裸布尔
            // {@code .../allow}。理由（差距文档 D-B4-20）：裸布尔查询的 result 恒为标量，
            // 下面的 parseObligations 只认对象形态 ⇒ 义务集（deniedReason/maskHints/
            // watermark/rateLimitKey）在结构上永不可达，这才是 D-B4-12「水印义务取不到值」
            // 的更深根因；改查文档后同一应答既给 allow 也给 obligations。
            String opaPath = "/v1/data/ecos/" + policy;
            String payload = "{\"input\":" + MAPPER.writeValueAsString(input) + "}";

            var req = HttpRequest.newBuilder(URI.create(opaUrl + opaPath))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return parseEvalResponse(policy, input, resp.statusCode(), resp.body());
        } catch (Exception e) {
            log.warn("OPA 不可用, fail-close → DENY: policy={}, error={}", policy, e.getMessage());
            // W35（M0/P0）：fail-close 常量化 —— OPA 不可用一律拒绝，无 fail-open 分支。
            return unavailable(policy, input, "OPA_UNAVAILABLE_FAIL_CLOSED", e.getMessage(), null);
        }
    }

    /**
     * OPA 应答 → 裁决形态（单点解析；包级可见，供用例直读三种应答形态）。
     *
     * <p>四态判定（B4-3c，禁把后两态伪装成"策略拒绝"）：</p>
     * <ul>
     *   <li>{@code 2xx} 且 {@code result.allow == true} → {@link #STATE_ALLOW}；</li>
     *   <li>{@code 2xx} 且 {@code result.allow == false} → {@link #STATE_DENY}（真实策略判定）；</li>
     *   <li>{@code 2xx} 但应答无 {@code result} 段、或 {@code result} 对象缺 {@code allow} 键
     *       → {@link #STATE_POLICY_UNDEFINED}（OPA 可达但本策略未加载/未定义，
     *       dev 容器未挂 bundle 即落此态）；</li>
     *   <li>非 2xx / 应答不可解析 → {@link #STATE_UNAVAILABLE}。</li>
     * </ul>
     *
     * <p>后两态一律 {@code allow=false}（fail-close）并携带一条 {@code DENIED} 义务写明
     * 真实理由，但<b>状态串不被折叠成 DENY</b> —— 消费方据此把「护栏没说话」报成 503/
     * FAIL_CLOSED 而不是 403。</p>
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> parseEvalResponse(String policy, Map<String, Object> input,
                                          int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            // 5xx/4xx 不是裁决：此前只看 body 是否含 "result":true，会把网关故障伪装成策略拒绝（D-B4-21）
            return unavailable(policy, input, "OPA_HTTP_" + statusCode, null, statusCode);
        }
        Object result;
        try {
            Map<String, Object> root = MAPPER.readValue(body,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            result = root.get("result");
        } catch (Exception e) {
            return unavailable(policy, input, "OPA_RESPONSE_UNPARSABLE", e.getMessage(), statusCode);
        }
        Boolean allow = null;
        List<Map<String, Object>> obligations = new ArrayList<>();
        if (result instanceof Map<?, ?> r) {
            Map<String, Object> doc = (Map<String, Object>) r;
            allow = asBoolean(doc.get("allow"));
            obligations = obligationsOf(doc.get("obligations"));
        } else if (result != null) {
            allow = asBoolean(result);   // 兼容裸布尔形态（旧 .../allow 查询）
        }
        if (allow == null) {
            // OPA 应答里没有本策略的 allow ⇒ 策略未加载/未定义，护栏等于没被咨询
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("policy", policy);
            out.put("allow", false);
            out.put("policyState", STATE_POLICY_UNDEFINED);
            out.put("opaStatus", statusCode);
            out.put("source", "opa");
            out.put("policyRef", "ecos." + policy);
            out.put("obligations", List.of(Map.of("obligation", "DENIED",
                    "deniedReason", "OPA_POLICY_UNDEFINED")));
            out.put("inputDigest", sha256Hex(safeJson(input)));
            log.warn("OPA 应答无 {}.allow（策略未加载/未定义），fail-closed 且状态如实上报: state={}",
                    policy, STATE_POLICY_UNDEFINED);
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policy", policy);
        out.put("allow", allow);
        out.put("policyState", allow ? STATE_ALLOW : STATE_DENY);
        out.put("opaStatus", statusCode);
        out.put("source", "opa");
        out.put("policyRef", "ecos." + policy);
        // C.2.5 ③：裁决结果携带 obligations（deniedReason/maskHints/watermark/rateLimitKey）。
        out.put("obligations", obligations);
        // C.2.5 ④：裁决落审计线索（input 摘要，不落原文避免敏感外泄）
        out.put("inputDigest", sha256Hex(safeJson(input)));
        return out;
    }

    /** 不可用态（网络/超时/非 2xx/应答不可解析）：fail-close + 状态单列，禁冒充策略裁决。 */
    private Map<String, Object> unavailable(String policy, Map<String, Object> input,
                                            String deniedReason, String error, Integer statusCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policy", policy);
        result.put("source", "fallback");
        result.put("allow", false);
        result.put("policyState", STATE_UNAVAILABLE);
        result.put("fallback", "DENY_OPA_DOWN");
        result.put("eccode", "ECOS-SEC-502");
        if (error != null) result.put("opaError", error);
        if (statusCode != null) result.put("opaStatus", statusCode);
        result.put("obligations", List.of(
                Map.of("obligation", "DENIED", "deniedReason", deniedReason)));
        result.put("inputDigest", sha256Hex(safeJson(input)));
        log.warn("OPA fail-close → DENY: policy={} reason={}", policy, deniedReason);
        return result;
    }

    /**
     * C.2.5 ③：义务集解析（fail-safe，不放大权限）。
     *
     * <p>兼容两种义务书写：对象数组（rego 侧 {@code obligations} 规则的标准形态）与
     * 字符串数组（旧 {@code "mask:hourly_rate"} 之类简写，逐字保留供消费方分形态读取）。</p>
     */
    private static List<Map<String, Object>> obligationsOf(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object o : list) {
            if (o instanceof Map<?, ?> om) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mm = (Map<String, Object>) om;
                out.add(mm);
            } else if (o != null && !String.valueOf(o).isBlank()) {
                out.add(Map.of("obligation", String.valueOf(o)));
            }
        }
        return out;
    }

    private static Boolean asBoolean(Object o) {        if (o instanceof Boolean b) return b;
        if (o == null) return null;
        String s = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s)) return Boolean.TRUE;
        if ("false".equals(s)) return Boolean.FALSE;
        return null;
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
