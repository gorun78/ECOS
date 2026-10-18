package com.chinacreator.gzcm.engine.ai.wagent.tool;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.service.ToolCall;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ToolContractStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentToolContractDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentToolContractDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.StepInvokerPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具步骤执行器（详细设计-10 F10-14/§4.2 的 {@code invoke(tool)} 通道；
 * §4.1 组件落位 {@code wagent/tool/EngineEndpointInvoker} 的实位）。
 *
 * <p><b>咽喉唯一</b>：本类不自己发 HTTP、不碰 JDBC，工具调用一律经
 * {@link ToolExecutorService#execute(ToolCall)} 的四阶段裁决
 * （policyEvaluate → sandboxReview → dispatch → obligationsApply，F06-01 要点 1/5；
 * 详设-10 F10-03「工具调用一律经 {@code EngineEndpointInvoker}」原文口径）。
 * 因此 {@code ToolChokepointArchTest#noBypassOfToolExecutorService} 天然满足——
 * 本类调的是 {@code ToolExecutorService#execute}，不是 {@code ToolRegistry#execute}。</p>
 *
 * <p><b>形态偏差（已登记，见差距分析册 v1.0.4）</b>：F10-03 原文括注「内部用既有 REST 客户端
 * {@code AiSecurityEngineClient} 同款形态」。本实现改为<b>同 JVM 直调咽喉</b>：
 * wagent 与工具咽喉同在 ai-engine-impl 宿主内，REST 外环既不增加任何裁决强度
 * （policy/沙箱/义务/审计都在咽喉内完成），又要新引一条服务间凭证传递链
 * （即 B2① 刚暴露的 H10 同类缺口，现状必 fail-closed 401）。
 * 「禁 {@code JdbcTemplate}」一条照守——本类零 JDBC、零 SQL。
 * 若 wagent 后续独立部署，需按 REST 形态重做并接服务间凭证。</p>
 *
 * <p><b>不重复设闸（分层纪律，避免同一条件四处拒绝、口径漂移）</b>：</p>
 * <ul>
 *   <li>name+version 存在性/active、input/output schema 齐备、step≤ceiling、写通道
 *       —— 计划面 {@code PlanValidator} 七检已把关（§4.2 planning 唯一闸）；</li>
 *   <li>pre-step 裁决 —— 执行面 {@code StepExecutor} 每步一次（F10-22）；</li>
 *   <li>等级双校（step vs run ceiling）—— 落库面 {@code StepWriter}；</li>
 *   <li>权限/脱敏/审计 —— 咽喉 {@code policyEvaluate} + {@code obligationsApply}。</li>
 * </ul>
 * 本类只做咽喉之前拿不到的两件事：<b>(1) 契约纵深防御</b>（计划校验与执行之间目录可能
 * 变更：命中契约后仍拒绝非 active 版，F10-17「只有 active 版本可被选择执行」）；
 * <b>(2) 输出契约校验</b>（§4.2 循环体 {@code output contract validate} 一环——设计有、
 * 此前代码无）。入参 {@code required} 预检属"不发未裁决调用"的地板，不改变裁决结论。</p>
 *
 * <p><b>错误码出口单源（§5.3）</b>：失败一律落 {@link WAgentErrorCode}，<b>禁</b>把底座
 * {@code ECOS-AI-*} 码透传到 W 面——{@code WAgentOrchestrator} 的 FAILED_DEP 分支会调
 * {@link WAgentErrorCodes#fromWireCode}（未注册码直接抛 IllegalArgumentException）。
 * 底座原文（{@code ToolResult.getError()}）只入日志，对外用固定模板（X-49）。
 * 映射表见 {@link #mapToWAgentCode}，单测逐项覆盖全部 {@link AiErrorClass} 成员。</p>
 */
@Component
public class EngineEndpointInvoker {

    private static final Logger log = LoggerFactory.getLogger(EngineEndpointInvoker.class);

    /** metrics 键单源（落 run_step.metrics_json，供对账与缺口定位；禁散写字面量）。 */
    public static final String METRIC_TOOL_CALL_ID = "tool_call_id";
    public static final String METRIC_DURATION_MS = "duration_ms";
    public static final String METRIC_CONTRACT_STATUS = "contract_status";
    public static final String METRIC_CONTRACT_MIN_LEVEL = "contract_min_level";
    public static final String METRIC_TOOL_ERROR_CODE = "tool_error_code";
    public static final String METRIC_TOOL_ERROR_CLASS = "tool_error_class";
    public static final String METRIC_INPUT_CONTRACT = "input_contract";
    public static final String METRIC_OUTPUT_CONTRACT = "output_contract";
    /** 契约校验结论三值（区分"真验过"与"契约未声明 required 因而无从校验"，禁把免检当通过）。 */
    public static final String CONTRACT_CHECKED_PASSED = "checked_passed";
    public static final String CONTRACT_NO_REQUIRED = "no_required_declared";
    public static final String CONTRACT_VIOLATED = "violated";
    /** 非 JSON 对象返回体的透传键（不编造结构化字段，原样留证）。 */
    public static final String OUTPUT_RAW_KEY = "raw_content";

    private final ToolExecutorService toolExecutorService;
    private final WAgentToolContractDao toolContractDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EngineEndpointInvoker(ToolExecutorService toolExecutorService,
                                 WAgentToolContractDao toolContractDao) {
        this.toolExecutorService = toolExecutorService;
        this.toolContractDao = toolContractDao;
    }

    /**
     * 执行工具步骤：契约解析 → 入参地板 → 咽喉调用 → 输出契约校验。
     * 任一前置拒绝都不发起调用（不留"未裁决的执行"，详设-06 F06-01 要点 4 所引
     * PRD-06 §1.5-1「无裁决记录的工具调用次数 = 0」口径）。
     */
    public StepInvokerPort.Outcome invoke(StepInvokerPort.Request request) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        String toolName = request.toolName();
        String toolVersion = request.toolVersion();
        String callId = toolCallId(request);
        metrics.put(METRIC_TOOL_CALL_ID, callId);

        if (isBlank(toolName)) {
            return StepInvokerPort.Outcome.failure(WAgentErrorCode.E_VALIDATION.wireCode(),
                    "tool 步骤缺 tool_name：" + request.stepKey(), Map.of(), metricsOf(metrics));
        }
        if (isBlank(toolVersion)) {
            // 无版本无法定位 active 契约（uniq(name,version)）⇒ fail-closed，禁"取最新版"猜测
            return StepInvokerPort.Outcome.failure(WAgentErrorCode.E_VALIDATION.wireCode(),
                    "tool 步骤缺 tool_version，无法定位 active 契约版：" + toolName
                            + "（F10-17 版本闭合）", Map.of(), metricsOf(metrics));
        }

        WAgentToolContractDO contract =
                toolContractDao.findByNameAndVersion(toolName, toolVersion).orElse(null);
        if (contract == null) {
            // 与 E-POLICY 文案不可区分（防资源枚举，§5.3）
            log.warn("[F10-14] 契约目录未命中: tool={} version={} run={} step={}",
                    toolName, toolVersion, request.runId(), request.stepKey());
            return StepInvokerPort.Outcome.failure(WAgentErrorCode.E_NOT_FOUND.wireCode(),
                    "工具契约未注册或不可见: " + toolName + "@" + toolVersion,
                    Map.of(), metricsOf(metrics));
        }
        metrics.put(METRIC_CONTRACT_STATUS, contract.getStatus());
        if (contract.getMinLevel() != null) {
            metrics.put(METRIC_CONTRACT_MIN_LEVEL, contract.getMinLevel());
        }
        if (!ToolContractStatus.ACTIVE.value().equals(contract.getStatus())) {
            // 计划校验后目录被改（下架/退役）⇒ 状态冲突，不可重试到同一结论
            return StepInvokerPort.Outcome.failure(WAgentErrorCode.E_WA_STATE.wireCode(),
                    "契约非 active 版不可执行: " + toolName + "@" + toolVersion
                            + " status=" + contract.getStatus() + "（F10-17）",
                    Map.of(), metricsOf(metrics));
        }

        Map<String, Object> input = request.input() == null ? Map.of() : request.input();
        List<String> missingIn = requiredKeys(contract.getInputSchemaJson(), input, objectMapper);
        if (!missingIn.isEmpty()) {
            metrics.put(METRIC_INPUT_CONTRACT, CONTRACT_VIOLATED);
            // §5.3 E-VALIDATION 口径：文案必须指出具体字段
            return StepInvokerPort.Outcome.failure(WAgentErrorCode.E_VALIDATION.wireCode(),
                    "工具入参缺失必需字段: " + toolName + " → " + missingIn,
                    Map.of(), metricsOf(metrics));
        }
        metrics.put(METRIC_INPUT_CONTRACT,
                missingIn.isEmpty() && hasRequired(contract.getInputSchemaJson(), objectMapper)
                        ? CONTRACT_CHECKED_PASSED : CONTRACT_NO_REQUIRED);

        ToolExecutorService.ToolResult raw;
        try {
            // #143/B5-1：版本必须一路带进咽喉 —— 契约目录按 uniq(name,version) 定位，
            // 只传名字 ⇒ 咽喉永远解析不到契约行（表现为「工具未找到」→ E-VALIDATION）
            raw = toolExecutorService.execute(new ToolCall(callId, toolName, input, toolVersion));
        } catch (Exception e) {
            log.error("[F10-14] 咽喉调用异常: tool={} run={} step={} raw={}",
                    toolName, request.runId(), request.stepKey(), e.getMessage(), e);
            metrics.put(METRIC_TOOL_ERROR_CLASS, "INVOKER_EXCEPTION");
            return StepInvokerPort.Outcome.failure(WAgentErrorCode.E_INTERNAL.wireCode(),
                    "工具调用通道异常: " + toolName, Map.of(), metricsOf(metrics));
        }
        metrics.put(METRIC_DURATION_MS, raw.getElapsedMs());

        if (!raw.isSuccess()) {
            WAgentErrorCode code = mapToWAgentCode(raw.getErrorClass());
            metrics.put(METRIC_TOOL_ERROR_CODE,
                    isBlank(raw.getErrorCode()) ? "MISSING" : raw.getErrorCode());
            metrics.put(METRIC_TOOL_ERROR_CLASS,
                    isBlank(raw.getErrorClass()) ? "UNCLASSIFIED" : raw.getErrorClass());
            log.warn("[F10-14] 咽喉裁决/执行失败: tool={} run={} step={} wCode={} aiClass={} aiCode={} err={}",
                    toolName, request.runId(), request.stepKey(), code.wireCode(),
                    raw.getErrorClass(), raw.getErrorCode(), raw.getError());
            // errorText 用固定语义模板 + 工具名（底座原文只入日志，X-49 禁外泄进响应）
            return StepInvokerPort.Outcome.failure(code.wireCode(),
                    code.renderMessage(raw.getTraceId()) + " | tool=" + toolName,
                    Map.of(), metricsOf(metrics));
        }

        Map<String, Object> output = parseContent(raw.getContent(), metrics);
        List<String> missingOut = requiredKeys(contract.getOutputSchemaJson(), output, objectMapper);
        boolean outRequired = hasRequired(contract.getOutputSchemaJson(), objectMapper);
        if (!missingOut.isEmpty()) {
            metrics.put(METRIC_OUTPUT_CONTRACT, CONTRACT_VIOLATED);
            log.warn("[F10-14] 输出契约违约: tool={} run={} step={} missing={}",
                    toolName, request.runId(), request.stepKey(), missingOut);
            return StepInvokerPort.Outcome.failure(WAgentErrorCode.E_VALIDATION.wireCode(),
                    "工具输出不符契约，缺必需字段: " + toolName + " → " + missingOut
                            + "（§4.2 output contract validate）",
                    output, metricsOf(metrics));
        }
        metrics.put(METRIC_OUTPUT_CONTRACT,
                outRequired ? CONTRACT_CHECKED_PASSED : CONTRACT_NO_REQUIRED);
        if (raw.getDecision() != null) {
            output.put("policy_decision", raw.getDecision());
        }
        if (!isBlank(raw.getCitationBlock())) {
            output.put("citation_block", raw.getCitationBlock());
        }
        return StepInvokerPort.Outcome.ok(output, metricsOf(metrics));
    }

    // ── AiErrorClass → W 域码（§5.3 出口单源，映射表全类覆盖）────────────
    // 裁决要点：
    //   • STUB_CAPABILITY（501 能力未上线）不得洗成可重试 502——与咽喉自身的同类论证一致
    //     （把不存在的能力报成"稍后重试"会诱导无谓重试并把缺口掩盖成抖动，X-50 禁伪成功同族）；
    //   • GUARDRAIL_FAIL_CLOSED 属 security-engine 依赖不可达 ⇒ 确为依赖故障，落 E-WA-DEP；
    //   • 未分类/缺失分类落 E-WA-DEP（失败发生在咽喉之内侧，属后端态，禁冒充 500 引擎自身缺陷）。
    // 禁新造 §5.3 之外的码：设计 §4.2 提到 E-WA-SCHEMA，但 §5.3 冻结表未含该码，
    // 故 schema 类违约按表内最近义落 E-VALIDATION（订正需求已登记差距文档）。

    /** 把底座失败分类映射到 W 域出口码；未知/未分类归 E-WA-DEP（永不返回 null）。 */
    public static WAgentErrorCode mapToWAgentCode(String aiErrorClassName) {
        if (isBlank(aiErrorClassName)) {
            return WAgentErrorCode.E_WA_DEP;
        }
        AiErrorClass cls;
        try {
            cls = AiErrorClass.valueOf(aiErrorClassName.trim());
        } catch (IllegalArgumentException notInMatrix) {
            return WAgentErrorCode.E_WA_DEP;
        }
        return switch (cls) {
            case GUARDRAIL_DENIED -> WAgentErrorCode.E_POLICY;
            case NOT_FOUND, STUB_CAPABILITY -> WAgentErrorCode.E_NOT_FOUND;
            case INVALID_ARGUMENT, CALIBER_CLARIFICATION -> WAgentErrorCode.E_VALIDATION;
            case CONTEXT_OVERFLOW -> WAgentErrorCode.E_WA_BUDGET;
            case LLM_RATE_LIMITED -> WAgentErrorCode.E_RATE;
            case PARSE_FAILURE -> WAgentErrorCode.E_INTERNAL;
            case CITATION_MISSING -> WAgentErrorCode.E_WA_NARRATIVE_NUM;
            case LLM_TIMEOUT, GUARDRAIL_FAIL_CLOSED, TOOL_BACKEND_ERROR -> WAgentErrorCode.E_WA_DEP;
        };
    }

    // ── 契约 schema 读取（只取 required，禁在此实现完整 JSON Schema 校验器）──

    /** 声明了 required 且载荷缺键 ⇒ 返回缺失键清单（保序）。 */
    static List<String> requiredKeys(String schemaJson, Map<String, Object> payload,
                                     ObjectMapper mapper) {
        List<String> required = requiredList(schemaJson, mapper);
        List<String> missing = new ArrayList<>();
        for (String key : required) {
            Object value = payload.get(key);
            if (value == null || (value instanceof String s && s.isEmpty())) {
                missing.add(key);
            }
        }
        return missing;
    }

    /** 契约是否声明了 required（区分"验过"与"无从校验"，禁把免检冒充实检）。 */
    static boolean hasRequired(String schemaJson, ObjectMapper mapper) {
        return !requiredList(schemaJson, mapper).isEmpty();
    }

    private static List<String> requiredList(String schemaJson, ObjectMapper mapper) {
        if (isBlank(schemaJson)) {
            return List.of();
        }
        try {
            JsonNode node = mapper.readTree(schemaJson);
            JsonNode required = node.path("required");
            if (!required.isArray()) {
                return List.of();
            }
            List<String> keys = new ArrayList<>();
            for (JsonNode item : required) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    keys.add(item.asText());
                }
            }
            return keys;
        } catch (Exception e) {
            // 契约 JSON 不可解析属数据面缺陷：无从校验 ⇒ 不假装通过，交上层按"未声明"处理并留痕
            log.warn("契约 schema 不可解析（按未声明 required 处理并留痕）: {}", e.getMessage());
            return List.of();
        }
    }

    /** 返回体解析：JSON 对象 ⇒ Map；其余形态原样透传 {@value #OUTPUT_RAW_KEY}（禁编造字段）。 */
    private Map<String, Object> parseContent(String content, Map<String, Object> metrics) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (isBlank(content)) {
            metrics.put("content_present", false);
            return output;
        }
        metrics.put("content_present", true);
        try {
            JsonNode node = objectMapper.readTree(content);
            if (node.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    output.put(field.getKey(), objectMapper.convertValue(field.getValue(), Object.class));
                }
                return output;
            }
            metrics.put("output_shape", "non_object");
        } catch (Exception e) {
            metrics.put("output_shape", "not_json");
        }
        output.put(OUTPUT_RAW_KEY, content);
        return output;
    }

    /** 幂等可审计的调用标识（run+step 唯一：同 Run 同步序列内不重复）。 */
    static String toolCallId(StepInvokerPort.Request request) {
        return "wa:" + request.runId() + ":" + request.stepKey() + ":" + request.toolName();
    }

    private static Map<String, Object> metricsOf(Map<String, Object> metrics) {
        return Map.copyOf(metrics);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
