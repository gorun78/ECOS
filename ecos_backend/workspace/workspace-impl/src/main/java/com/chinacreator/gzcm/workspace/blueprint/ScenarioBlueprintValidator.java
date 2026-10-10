package com.chinacreator.gzcm.workspace.blueprint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景蓝图验收门槛校验器（补充设计 v1.0 §6.6 + §12.1，SUP-BP-02 集成验收）。
 *
 * <p>四道门槛（缺一即 {@code passed=false}，HTTP 侧 409）：
 * <ol>
 *   <li>所有 {@code BLOCKER} Gap 必须关闭（status = RESOLVED / ACCEPTED_RISK，§6.5）；</li>
 *   <li>每个 required 依赖必须存在且 {@code status = PUBLISHED}（版本约束满足）；</li>
 *   <li>每个 required capability 的契约必须可用（经 {@link CapabilityReadService} 抽象；
 *       能力中心未接线时按不可用判，fail-closed）；</li>
 *   <li>P0 测试必须全部通过（无未通过 P0 用例引用）。</li>
 * </ol>
 *
 * <p>补充：进入 {@code APPROVED} 前若 {@code approval_required=1} 且 {@code approval_ref} 为空，
 * 由 {@link ScenarioBlueprintApplicatorService} 在 approve 端点拦截（409）；
 * 本校验器负责发布前四道静态门槛。</p>
 *
 * <p>实现纯函数、无 DB/事件副作用，便于单测直接构造。</p>
 */
@Component
public class ScenarioBlueprintValidator {

    private static final Logger log = LoggerFactory.getLogger(ScenarioBlueprintValidator.class);

    /** §6.5 Gap 关闭态：已解决或明确风险接受。 */
    private static final List<String> GAP_CLOSED_STATES = List.of("RESOLVED", "ACCEPTED_RISK");

    /** 校验入参：蓝图全字段（结构化列以原始 JSON 文本传入）。 */
    public static final class BlueprintContent {
        public String gapsText;
        public String dependenciesText;
        public String capabilitiesText;
        public String testsText;
        public int approvalRequired;
        public String approvalRef;
    }

    /** 校验结果：passed=false 时 reasons 携带人类可读 + 机器可读缺口明细。 */
    public static final class ValidationResult {
        private final boolean passed;
        private final List<String> reasons;
        private final Map<String, Object> details;

        private ValidationResult(boolean passed, List<String> reasons, Map<String, Object> details) {
            this.passed = passed;
            this.reasons = reasons;
            this.details = details;
        }

        public static ValidationResult passed() {
            return new ValidationResult(true, List.of(), Map.of());
        }

        public static ValidationResult failed(List<String> reasons, Map<String, Object> details) {
            return new ValidationResult(false, reasons, details);
        }

        public boolean isPassed() {
            return passed;
        }

        public List<String> getReasons() {
            return reasons;
        }

        public Map<String, Object> getDetails() {
            return details;
        }
    }

    private final CapabilityReadService capabilityReadService;
    private final ObjectMapper objectMapper;

    public ScenarioBlueprintValidator(CapabilityReadService capabilityReadService, ObjectMapper objectMapper) {
        this.capabilityReadService = capabilityReadService;
        this.objectMapper = objectMapper;
    }

    /** 发布前四道静态门槛（§6.6）。任一不过即 passed=false。 */
    public ValidationResult validateForRelease(BlueprintContent content) {
        List<String> reasons = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();

        List<Map<String, Object>> openBlockers = checkBlockerGaps(content.gapsText, reasons);
        details.put("openBlockerGaps", openBlockers);

        List<Map<String, Object>> missingDeps = checkRequiredPublishedDeps(content.dependenciesText, reasons);
        details.put("missingRequiredDeps", missingDeps);

        List<Map<String, Object>> unavailCaps = checkRequiredCapabilities(content.capabilitiesText, reasons);
        details.put("unavailableCapabilities", unavailCaps);

        List<Map<String, Object>> failedP0 = checkP0Tests(content.testsText, reasons);
        details.put("failedP0Tests", failedP0);

        if (reasons.isEmpty()) {
            return ValidationResult.passed();
        }
        return ValidationResult.failed(reasons, details);
    }

    // ─────────────── 门槛 1：BLOCKER Gap 全关 ───────────────

    private List<Map<String, Object>> checkBlockerGaps(String gapsJson, List<String> reasons) {
        List<Map<String, Object>> open = new ArrayList<>();
        for (JsonNode gap : arrayNodes(gapsJson, "gaps")) {
            if (!"BLOCKER".equalsIgnoreCase(text(gap, "severity"))) {
                continue;
            }
            String status = text(gap, "status");
            if (status != null && GAP_CLOSED_STATES.contains(status.toUpperCase())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("gapId", text(gap, "gapId"));
            item.put("category", text(gap, "category"));
            item.put("status", status == null ? "OPEN" : status);
            item.put("description", text(gap, "description"));
            open.add(item);
            reasons.add("存在未关闭的 BLOCKER 缺口: " + (text(gap, "gapId") == null ? "(无 gapId)" : text(gap, "gapId"))
                    + "（status=" + (status == null ? "OPEN" : status) + "，须 RESOLVED 或 ACCEPTED_RISK，§6.6/§6.5）");
        }
        return open;
    }

    // ─────────────── 门槛 2：required PUBLISHED 依赖齐 ───────────────

    private List<Map<String, Object>> checkRequiredPublishedDeps(String depsJson, List<String> reasons) {
        List<Map<String, Object>> missing = new ArrayList<>();
        for (JsonNode dep : arrayNodes(depsJson, "dependencies")) {
            if (!asBool(dep, "required")) {
                continue;
            }
            String status = text(dep, "status");
            if ("PUBLISHED".equalsIgnoreCase(status)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", text(dep, "type"));
            item.put("id", text(dep, "id"));
            item.put("versionConstraint", text(dep, "versionConstraint"));
            item.put("status", status == null ? "NULL" : status);
            missing.add(item);
            reasons.add("必需依赖未就绪: " + text(dep, "type") + "/" + text(dep, "id")
                    + "（status=" + (status == null ? "NULL" : status) + "，须 PUBLISHED，§6.6）");
        }
        return missing;
    }

    // ─────────────── 门槛 3：required capability 契约可用 ───────────────

    private List<Map<String, Object>> checkRequiredCapabilities(String capsJson, List<String> reasons) {
        List<Map<String, Object>> unavail = new ArrayList<>();
        for (JsonNode cap : arrayNodes(capsJson, "capabilities")) {
            if (!asBool(cap, "required")) {
                continue;
            }
            String capabilityId = text(cap, "capabilityId");
            String contractVersion = text(cap, "contractVersion");
            CapabilityReadService.CapabilityAvailability availability = capabilityReadService.availability(
                    capabilityId, contractVersion);
            if (availability != null && availability.isAvailable()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("capabilityId", capabilityId);
            item.put("contractVersion", contractVersion);
            item.put("reason", availability == null ? "capability read service 未装配" : availability.getReason());
            unavail.add(item);
            String suffix = availability == null ? "" : " (" + availability.getReason() + "), section 6.6";
            reasons.add("Required capability contract unavailable: " + capabilityId
                    + (contractVersion == null ? "" : "@v" + contractVersion)
                    + suffix);
        }
        return unavail;
    }

    // ─────────────── 门槛 4：P0 测试全过 ───────────────

    private List<Map<String, Object>> checkP0Tests(String testsJson, List<String> reasons) {
        List<Map<String, Object>> failed = new ArrayList<>();
        if (testsJson == null || testsJson.isBlank()) {
            // 用例引用缺失 = 无 P0 测试证据，按 §6.6「P0 测试未通过不能进入待发审批」从严拒绝
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reason", "无测试用例引用（tests 为空），P0 门禁无证据");
            failed.add(item);
            reasons.add("蓝图缺少测试用例引用，P0 验收无证据（§6.6：P0 未通过不得进入批准线）");
            return failed;
        }
        for (JsonNode test : arrayNodes(testsJson, "tests")) {
            boolean isP0 = "P0".equalsIgnoreCase(text(test, "priority"));
            if (!isP0) {
                continue;
            }
            String status = text(test, "status");
            if (status != null && "PASSED".equalsIgnoreCase(status)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("testId", text(test, "testId"));
            item.put("status", status == null ? "UNRUN" : status);
            failed.add(item);
            reasons.add("P0 测试未通过: " + (text(test, "testId") == null ? "(无 testId)" : text(test, "testId"))
                    + "（status=" + (status == null ? "UNRUN" : status) + "，§6.6）");
        }
        return failed;
    }

    // ─────────────── JSON helpers ───────────────

    private List<JsonNode> arrayNodes(String json, String label) {
        List<JsonNode> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            // JSON 畸形禁静默吞、禁伪装通过（fail-loud，400 语义由异常承载）
            throw new BlueprintContentInvalidException(label, e.getMessage());
        }
        if (root != null && root.isArray()) {
            root.forEach(out::add);
        } else if (root != null) {
            throw new BlueprintContentInvalidException(label, "期望 JSON 数组");
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String v = node.get(field).asText();
        return v == null || v.isEmpty() ? null : v;
    }

    private static boolean asBool(JsonNode node, String field) {
        if (node == null) {
            return false;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return false;
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        return "true".equalsIgnoreCase(v.asText());
    }
}
