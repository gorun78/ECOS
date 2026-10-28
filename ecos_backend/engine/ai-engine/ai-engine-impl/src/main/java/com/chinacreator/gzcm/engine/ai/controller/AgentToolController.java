package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.service.ToolRegistry;
import com.chinacreator.gzcm.engine.ai.service.ToolSchema;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 工具管理端点 — 查看已注册工具、校验参数 Schema、开发期干跑。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code GET /api/v1/agent/tools} — 列出所有已注册工具</li>
 *   <li>{@code GET /api/v1/agent/tools/{name}/validate?args=...} — 校验参数是否符合 Schema</li>
 *   <li>{@code POST /api/v1/agent/tools/{name}/dry-run} — 场景工具干跑（开发期验收，
 *       <b>不进产品路径</b>，详设-06 §5.2 #10 / 详设说明书 §9.3.1 第 10 条）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/agent/tools")
public class AgentToolController {

    /** 干跑准入角色词表（与 F10-21 {@code WAgentIdentityResolver#isAdmin} 同一词表口径）。 */
    private static final Set<String> DRY_RUN_ADMIN_ROLES = Set.of("admin", "role_admin");

    private final ToolRegistry toolRegistry;
    private final AgentToolPolicyGate policyGate;
    private final ToolSubjectResolver subjectResolver;
    private final Environment environment;

    public AgentToolController(@Lazy ToolRegistry toolRegistry,
                               @Lazy AgentToolPolicyGate policyGate,
                               @Lazy ToolSubjectResolver subjectResolver,
                               Environment environment) {
        this.toolRegistry = toolRegistry;
        this.policyGate = policyGate;
        this.subjectResolver = subjectResolver;
        this.environment = environment;
    }

    /**
     * 列出所有已注册工具。
     *
     * <pre>GET /api/v1/agent/tools</pre>
     *
     * @return 工具列表，每项包含 name, description, parameters, functionCallSchema
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAll() {
        List<ToolSchema> schemas = toolRegistry.listAll();
        List<Map<String, Object>> result = schemas.stream()
                .map(schema -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", schema.getName());
                    item.put("description", schema.getDescription());

                    // 扁平化参数为 list（前端更易用）
                    List<Map<String, Object>> params = new ArrayList<>();
                    if (schema.getParameters() != null) {
                        for (Map.Entry<String, ToolSchema.ParamDef> e : schema.getParameters().entrySet()) {
                            Map<String, Object> param = new LinkedHashMap<>();
                            param.put("name", e.getKey());
                            param.put("type", e.getValue().getType());
                            param.put("required", e.getValue().isRequired());
                            param.put("description", e.getValue().getDescription());
                            if (e.getValue().getDefaultValue() != null) {
                                param.put("default", e.getValue().getDefaultValue());
                            }
                            if (e.getValue().getEnumValues() != null) {
                                param.put("enum", e.getValue().getEnumValues());
                            }
                            params.add(param);
                        }
                    }
                    item.put("parameters", params);

                    // 附上完整的 function-calling schema
                    item.put("functionCallSchema", schema.toFunctionCallSchema());
                    return item;
                })
                .collect(Collectors.toList());

        return ApiResponse.success(result);
    }

    /**
     * 校验某工具的参数是否符合 Schema 定义。
     *
     * <pre>GET /api/v1/agent/tools/{name}/validate?args=...</pre>
     *
     * <h4>参数</h4>
     * <ul>
     *   <li>{@code name} (path) — 工具名称</li>
     *   <li>{@code args}  (query, JSON string) — 待校验的参数字符串，例如 {@code ?args={"sql":"SELECT 1"}}</li>
     * </ul>
     *
     * @param name 工具名称
     * @param args 待校验的参数 JSON
     * @return 校验结果：valid=true/false，附带缺失必填项和类型提示
     */
    @GetMapping("/{name}/validate")
    public ApiResponse<Map<String, Object>> validate(
            @PathVariable String name,
            @RequestParam(required = false, defaultValue = "{}") String args) {

        ToolSchema schema = toolRegistry.get(name);
        if (schema == null) {
            return ApiResponse.badRequest("工具未注册: " + name);
        }

        Map<String, Object> argMap;
        try {
            argMap = parseJson(args);
        } catch (IOException ignored) {
            // F06-15：仅窄捕 parseJson 声明的受检 IOException；异常原文不外泄，只回固定 400 提示。
            return ApiResponse.badRequest("args 参数不是合法的 JSON");
        }

        List<String> missingRequired = new ArrayList<>();
        List<Map<String, Object>> typeWarnings = new ArrayList<>();

        if (schema.getParameters() != null) {
            for (Map.Entry<String, ToolSchema.ParamDef> entry : schema.getParameters().entrySet()) {
                String paramName = entry.getKey();
                ToolSchema.ParamDef def = entry.getValue();
                Object value = argMap.get(paramName);

                // 必填检查
                if (def.isRequired() && (value == null || (value instanceof String s && s.isBlank()))) {
                    missingRequired.add(paramName);
                    continue;
                }

                // 类型检查（宽松）
                if (value != null && def.getType() != null) {
                    String typeMismatch = checkType(paramName, value, def.getType());
                    if (typeMismatch != null) {
                        Map<String, Object> warning = new LinkedHashMap<>();
                        warning.put("param", paramName);
                        warning.put("expected", def.getType());
                        warning.put("actual", value.getClass().getSimpleName().toLowerCase());
                        warning.put("message", typeMismatch);
                        typeWarnings.add(warning);
                    }
                }
            }
        }

        boolean valid = missingRequired.isEmpty();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("toolName", name);
        if (!missingRequired.isEmpty()) {
            result.put("missingRequired", missingRequired);
        }
        if (!typeWarnings.isEmpty()) {
            result.put("typeWarnings", typeWarnings);
        }
        result.put("message", valid ? "参数校验通过" : "缺少必填参数: " + String.join(", ", missingRequired));

        return ApiResponse.success(result);
    }

    /**
     * 场景工具干跑（详设-06 §5.2 #10，开发期验收，<b>不进产品路径</b>）。
     *
     * <p><b>走完整咽喉、不落副作用</b>：不 dispatch —— 任何分支都不触发
     * {@code ToolRegistry.execute}/REST/SQL 出向 —— 但<b>真跑</b>
     * {@link AgentToolPolicyGate#review}：主体由 {@link ToolSubjectResolver} 自 token 上下文
     * 解析（禁取 body 自报），失败码/裁决由 {@code ToolDecision} 三态机读承载，
     * 禁合成"干跑必 ALLOW"的假预演。</p>
     *
     * <p><b>准入（三道，按序）</b>：① 生产 profile（{@code spring.profiles.active} 含
     * {@code production}）恒 403 —— 先于一切解析，禁在 prod 消耗裁决与留痕预算；
     * ② 主体必须有角色且角色词表含 admin（大小写不敏感，admin／ROLE_ADMIN），
     * 无主体无角色 ⇒ 403（审批面结论，机读码 {@code ECOS-AI-403}）；
     * ③ 真实裁决 —— 裁决闸未装配 ⇒ 503 {@code ECOS-AI-503}（依赖不可得，禁按缺闸放行），
     * DENY ⇒ 403、FAIL_CLOSED ⇒ 503（§4.7.2 两态禁折叠），仅 ALLOW 出 200 干跑载荷。</p>
     *
     * <p>请求体：{@code {arguments:{...}, asOfTime:"ISO-8601"(可选)}}。
     * 200 响应 data：{@code {name, arguments, asOfTime, dryRun:true, executed:false,
     * adjudication:{verdict,policyId,reason,operation,subject,source},
     * obligations:[摘要]}} —— obligations 只出 {@link ToolDecision#obligationSummary()}
     * （列名/规则名，禁数据值）；adjudication 不含 {@code highRisk}（干跑不落执行腿，
     * 高危沙盒不评估，不放"看似已评估"的假值）。</p>
     *
     * <p>三态分态（与 {@code SessionCompression} 不可得腿同乒乓面）：</p>
     * <ul>
     *   <li>403 生产域/角色域/裁决 DENY ⇒ {@code ECOS-AI-403}（RetryKind.NONE，禁重试）</li>
     *   <li>404 注册表未收录 ⇒ {@code ECOS-AI-404}（真不存在，禁伪"空裁决 200"）</li>
     *   <li>503 裁决闸不可得 / FAIL_CLOSED ⇒ {@code ECOS-AI-503}</li>
     * </ul>
     *
     * <p>F06-15 分态：本端点无 catch-all —— 注册表缺位走显式 404，审批失败态由
     * {@code ToolDecision} 三态机读承载，主体缺失走显式 503，参数解析失败走显式
     * 400（{@code BusinessException} → 400 映射），无裸异常上浮。</p>
     */
    @PostMapping("/{name}/dry-run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dryRun(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body) {
        // ① 生产 profile 禁干跑（设计面明示：不进产品路径；先于任何其它解析）
        if (isProductionProfile()) {
            return forbiddenResponse("生产 profile 禁止工具干跑（开发期验收通道）");
        }
        // 注册判据与 /validate 同一单源（禁在此另建第二张 tool→schema 真值表）
        ToolSchema schema = toolRegistry.get(name);
        if (schema == null) {
            return ResponseEntity.status(404).body(ApiResponse.error(
                    404, AiErrorClass.NOT_FOUND.errorCode(),
                    AiErrorClass.NOT_FOUND.renderMessage(null)));
        }
        // ② 主体与 admin 角色（主体只能来自 token 上下文，禁取 body 自报值）
        if (subjectResolver == null) {
            return failClosedResponse("裁决主体解析器未装配");
        }
        ToolAdjudicationRequest.Subject subject = subjectResolver.resolve();
        if (subject == null || !hasAdminRole(subject.roles())) {
            return forbiddenResponse(subject == null
                    ? "无 token 主体，拒绝干跑（fail-closed）"
                    : "干跑通道仅限 admin 角色");
        }
        Map<String, Object> arguments = extractArguments(body);
        // ③ 真裁决（裁决闸未装配 = 依赖不可得 ⇒ 503，禁把"闸缺失"翻成放行）
        if (policyGate == null) {
            return failClosedResponse("工具裁决闸不可用，拒绝干跑（fail-closed）");
        }
        ToolAdjudicationRequest request = new ToolAdjudicationRequest(
                subject,
                new ToolAdjudicationRequest.Action(ToolAdjudicationRequest.ACTION_TYPE,
                        name, "read"),
                ToolAdjudicationRequest.projectResource(arguments),
                new ToolAdjudicationRequest.Environment(
                        AiTraceSupport.currentTraceId(),
                        ToolAdjudicationRequest.CHANNEL_AGENT,
                        Instant.now().toString()));
        // 闸内已自持 fail-closed 面（三态归一，不外抛），本端点不再套 catch——
        // 拦一道 = 把"闸内分类"洗成第二套分类，且给 F06-15 的 catch 面新增一个判定点
        ToolDecision decision = policyGate.review(request);
        if (decision.verdict() == ToolDecision.Verdict.DENY) {
            return forbiddenResponse(decision.reason() == null
                    ? "工具被 OPA 裁决 DENY（详见 traceId 诊断码）"
                    : decision.reason());
        }
        if (decision.verdict() == ToolDecision.Verdict.FAIL_CLOSED) {
            return failClosedResponse("安全引擎不可用，无法给出裁决（fail-closed）");
        }
        // ALLOW ⇒ 组装干跑载荷（executed=false：本端点禁触发任何 dispatch/副作用）
        Map<String, Object> adjudication = new LinkedHashMap<>();
        adjudication.put("verdict", decision.verdict().name());
        adjudication.put("policyId", decision.policyId());
        adjudication.put("reason", decision.reason());
        adjudication.put("operation", "read");                 // dry-run 一律按 read 面强判
        // 高危沙盒属执行腿（阶段 2）——干跑不走 dispatch 与沙盒，故此处不评估；
        // 不放一个看似"已评估=低风险"的假 true/false（NON_NULL 使缺位天然不落 JSON）
        adjudication.put("subject", subject.userId());
        adjudication.put("source", AiTraceSupport.currentTraceId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("arguments", arguments);
        data.put("asOfTime", body == null ? null : body.get("asOfTime"));
        data.put("dryRun", true);
        data.put("executed", false);                                   // dry-run 永不执行副作用
        data.put("adjudication", adjudication);
        data.put(GuardrailDecisionContract.KEY_OBLIGATIONS,
                decision.obligationSummary());                          // 列名/规则名摘要，禁数据值
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ── 私有分态/工具方法 ───────────────────────────────────────────────

    private ResponseEntity<ApiResponse<Map<String, Object>>> forbiddenResponse(String message) {
        return ResponseEntity.status(AiErrorClass.GUARDRAIL_DENIED.httpStatus()).body(
                ApiResponse.forbidden(AiErrorClass.GUARDRAIL_DENIED.errorCode(),
                        message == null ? AiErrorClass.GUARDRAIL_DENIED.renderMessage(null) : message));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> failClosedResponse(String message) {
        return ResponseEntity.status(AiErrorClass.GUARDRAIL_FAIL_CLOSED.httpStatus()).body(
                ApiResponse.error(AiErrorClass.GUARDRAIL_FAIL_CLOSED.httpStatus(),
                        AiErrorClass.GUARDRAIL_FAIL_CLOSED.errorCode(),
                        message == null ? AiErrorClass.GUARDRAIL_FAIL_CLOSED.renderMessage(null) : message));
    }

    /** admin 角色判据：词表 admin／ROLE_ADMIN、大小写不敏感（与 F10-21 同口径）。 */
    private static boolean hasAdminRole(List<String> roles) {
        if (roles == null) return false;
        for (String r : roles) {
            if (r != null && DRY_RUN_ADMIN_ROLES.contains(r.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** 请求体 arguments 段：非 Map 一律归空集（禁把非法形态静默当 {} 打进裁决载荷）。 */
    private static Map<String, Object> extractArguments(Map<String, Object> body) {
        if (body != null && body.get("arguments") instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> {
                if (k instanceof String ks) out.put(ks, v);
            });
            return out;
        }
        return new LinkedHashMap<>();
    }

    /** 生产档判据：active profiles 含 production（开发/联调档 standard/enterprise/ultimate 不命中）。 */
    private boolean isProductionProfile() {
        if (environment == null) return false;
        for (String p : environment.getActiveProfiles()) {
            if ("production".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json) throws IOException {
        // 使用 Jackson（项目中已有 ObjectMapper）
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.readValue(json, Map.class);
    }

    /**
     * 宽松类型检查：只对明显不匹配的情况给出警告。
     */
    private static String checkType(String paramName, Object value, String expectedType) {
        if (expectedType == null) return null;

        switch (expectedType.toLowerCase()) {
            case "string":
                // 所有值都可 toString，不检查
                return null;
            case "number":
                if (value instanceof Number) return null;
                if (value instanceof String s) {
                    try {
                        Double.parseDouble(s);
                        return null;
                    } catch (NumberFormatException e) {
                        return "期望 number，实际为非数字字符串";
                    }
                }
                return "期望 number，实际为 " + value.getClass().getSimpleName().toLowerCase();
            case "boolean":
                if (value instanceof Boolean) return null;
                return "期望 boolean，实际为 " + value.getClass().getSimpleName().toLowerCase();
            case "array":
                if (value instanceof List) return null;
                return "期望 array，实际为 " + value.getClass().getSimpleName().toLowerCase();
            case "object":
                if (value instanceof Map) return null;
                return "期望 object，实际为 " + value.getClass().getSimpleName().toLowerCase();
            default:
                return null;
        }
    }
}
