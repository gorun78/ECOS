package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.capability.web.AiCapabilityApiPaths;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 模型目录契约面 REST —— 详设说明书 §9.3.1 #17/#18/#19（附录 A 第 28 条，#146x）。
 *
 * <h3>三面（operationId 与契约册对齐）</h3>
 * <ul>
 *   <li>{@code GET  /api/v1/ai/models} → {@code listLlmModels}（#17，<b>本批实装真值腿</b>：
 *       modelKey/contextWindow 取 {@link LLMGateway#modelCatalog()} 的登记序读数）；</li>
 *   <li>{@code POST /api/v1/ai/models} → {@code registerLlmModel}（#18，<b>显式 501</b>：
 *       版本类资产的运行期写通道缺位，见 {@link #REGISTER_MISSING}）；</li>
 *   <li>{@code GET  /api/v1/ai/models/{modelKey}/health} → {@code checkModelHealth}（#19，
 *       <b>显式 501</b>：模型维度熔断/探测读数无载体，见 {@link #HEALTH_MISSING}）。</li>
 * </ul>
 *
 * <p><b>基址与前缀</b>：类级 {@code @RequestMapping} 只挂
 * {@link AiCapabilityApiPaths#BASE_PREFIX}（{@code /api/v1/ai}），方法级用相对路径——
 * 避免像 {@link AgentProviderController} 那样多挂一个前缀而造出 {@code /api/v1/agent/models}
 * 这类契约外路径。{@code /api/v1/*} 只增不改；本批未新增重写表条目、未新增 permitAll 匿名面
 * （#17/#19 契约要求 authn，走 {@code ClearanceInterceptor} 既有 {@code /api/v1/} ⇒ L1 兜底），
 * 与 {@code AiCapabilityApiPaths} 头注的基址策略同源。</p>
 *
 * <p><b>禁抄 legacy 假值</b>：仓内既存两处"模型清单"是不可复用的假值形态——
 * {@code AIPModelController}（ConcurrentHashMap 内存表 + 5 枚硬编码，{@code healthCheck}
 * 恒 {@code status="healthy"} / {@code latencyMs=120}）与 {@code AgentConfigController#listModels}
 * （{@code /api/v1/agents/models}，4 枚硬编码行）。本类不得抄它们的列值，也不得以固定 200＋空
 * items 冒充目录（X-22）。契约 #16 provider 面同族先例见 {@link AgentProviderController}。</p>
 *
 * <p><b>诚实键面</b>：{@code capabilities/costClass/activeVersion/gitRef} 四列在供数面上不存在
 * （{@code LLMGatewayProperties.Model} 只有 {@code contextLimit} 一个字段；版本类资产的 Git 归档
 * 属主缺位），故一律 {@code null} 并逐项落 {@code *Applicable=false}，原因由顶层单源键
 * {@code columns_source}/{@code version_source} 自证——禁按"模型名像什么"猜能力、禁把缺省模型
 * 反推成版本真值。</p>
 */
@RestController
@RequestMapping(AiCapabilityApiPaths.BASE_PREFIX)
public class AiModelCatalogController {

    private static final Logger log = LoggerFactory.getLogger(AiModelCatalogController.class);

    /** 供数单源自证键值（契约 #17 的 modelKey/contextWindow 唯一取数点）。 */
    static final String CATALOG_SOURCE = "runtime.llm.gateway.LLMGateway#modelCatalog";

    /** GET 面 501 原因之一：llm-gateway 未装配（独立引擎调试态）。 */
    static final String REASON_GATEWAY_UNASSEMBLED = "llm-gateway 未装配（独立引擎调试态），目录读数不可得";

    /** GET 面 501 原因之二：属性装配了但目录为空（运维把 {@code llm.models.catalog} 覆盖成空）。 */
    static final String REASON_CATALOG_EMPTY = "llm.models.catalog 未配置或为空，无模型登记行";

    /** 能力/成本列不可得的原因单源。 */
    static final String COLUMNS_UNAVAILABLE =
            "llm.models.catalog 仅登记 model→contextLimit（LLMGatewayProperties.Model 只有 contextLimit 字段），"
                    + "capabilities/costClass 无载体";

    /** 版本列不可得的原因单源。 */
    static final String VERSION_UNAVAILABLE =
            "版本类资产 Git 归档属主缺位：ecos-git-repos/ 实存仅 data-workbench，模型目录无 activeVersion/gitRef 归档通道";

    /** 过滤参数不可服务化的原因单源（对不存在的列做筛选会造出假筛选结果）。 */
    static final String FILTERS_UNAVAILABLE =
            "capability/dataClassification 过滤无载体：目录面不存在该二列，按其筛选只能产出假筛选结果";

    /** #18 未上线的欠账清单（取证单源，响应自证；逐条均可复验）。 */
    static final List<String> REGISTER_MISSING = List.of(
            "运行期写通道：llm.models.catalog 属 Spring yml 部署期形态（gateway application.yml 的「运维覆盖面：llm.models.catalog.&lt;model-name&gt;.context-limit」口径，与 V264 头注的部署期/业务期边界同源），注册即升版无处可写",
            "AI_ADMIN 角色：ecos_backend/ecos_frontend/ecos-tests/tools 源码零命中，仅设计文档记载 ⇒ 角色谓词无载体可判",
            "OPA 策略：rbac.rego 为 default allow=false + 4 条 allow（pipeline.execute/read/list/get），无 ai:model:* ⇒ 现挂必 fail-closed",
            VERSION_UNAVAILABLE);

    /** #19 未上线的欠账清单（取证单源，响应自证；逐条均可复验）。 */
    static final List<String> HEALTH_MISSING = List.of(
            "模型维度熔断态载体：唯一熔断器 AgentCircuitBreaker 以 agentId 为键，且是 AgentLoopController 的字段级 new 实例（非 Spring 单例），改按 modelKey 取用属推断式关联（禁）",
            "探测读数载体：runtime-monitor 指标门面 IMetricRecorder 只有 increment(name, tags) 写面，无 state/errorRate/windowCount/lastProbeTime 读面");

    /** 契约 #17 要求但本面无载体的列（缺席即 null + {@code *Applicable=false}）。 */
    private static final List<String> UNSUPPORTED_FILTERS = List.of(
            AiCapabilityApiPaths.Q_CAPABILITY, AiCapabilityApiPaths.Q_DATA_CLASSIFICATION);

    private final LLMGateway llmGateway;

    public AiModelCatalogController(@Autowired(required = false) LLMGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    /**
     * #17 {@code listLlmModels}：模型目录只读视图（真值腿）。
     *
     * <p>读数不可得（未装配 / 目录空）⇒ 501 + {@code stub=true}（{@link AiErrorClass#STUB_CAPABILITY}），
     * 禁回落 200＋空 items。契约的 {@code capability}/{@code dataClassification} 过滤在本面无载体，
     * 显式给定非空值时拒为 400（{@code ECOS-AI-400}），禁静默忽略后给出"看似已过滤"的结果。</p>
     */
    @GetMapping(AiCapabilityApiPaths.MODELS_REL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> listLlmModels(
            @RequestParam(value = AiCapabilityApiPaths.Q_CAPABILITY, required = false) String capability,
            @RequestParam(value = AiCapabilityApiPaths.Q_DATA_CLASSIFICATION, required = false)
            String dataClassification) {

        rejectUnsupportedFilter(AiCapabilityApiPaths.Q_CAPABILITY, capability);
        rejectUnsupportedFilter(AiCapabilityApiPaths.Q_DATA_CLASSIFICATION, dataClassification);

        List<LLMGateway.ModelLimit> catalog =
                llmGateway == null ? List.of() : llmGateway.modelCatalog();
        if (catalog == null || catalog.isEmpty()) {
            return stub(AiTraceSupport.currentTraceId(),
                    llmGateway == null ? REASON_GATEWAY_UNASSEMBLED : REASON_CATALOG_EMPTY,
                    Map.of("items", List.of(), "total", 0));
        }

        List<Map<String, Object>> items = new ArrayList<>(catalog.size());
        int contextWindowAvailable = 0;
        for (LLMGateway.ModelLimit row : catalog) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("modelKey", row.model());
            // 目录登记但上限 <=0 ⇒ 读数不可用，落 null（禁兜底常量、禁把 0 当真实窗口）
            item.put("contextWindow", row.available() ? row.contextLimit() : null);
            item.put("contextWindowApplicable", row.available());
            item.put("contextWindowDeclared", row.declared());
            item.put("capabilities", null);
            item.put("capabilitiesApplicable", false);
            item.put("costClass", null);
            item.put("costClassApplicable", false);
            item.put("activeVersion", null);
            item.put("activeVersionApplicable", false);
            item.put("gitRef", null);
            item.put("gitRefApplicable", false);
            items.add(item);
            if (row.available()) {
                contextWindowAvailable++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", items.size());
        data.put("contextWindowAvailable", contextWindowAvailable);
        data.put("stub", false);
        data.put("catalog_source", CATALOG_SOURCE);
        data.put("columns_source", COLUMNS_UNAVAILABLE);
        data.put("version_source", VERSION_UNAVAILABLE);
        data.put("filters_applicable", false);
        data.put("filters_source", FILTERS_UNAVAILABLE);
        data.put("unsupported_filters", UNSUPPORTED_FILTERS);
        return ResponseEntity.ok(ApiResponse.success(data)
                .withTraceId(AiTraceSupport.currentTraceId()));
    }

    /**
     * #18 {@code registerLlmModel}：注册/升版模型 —— <b>显式 501 承流，零写入</b>。
     *
     * <p>本面只回显收到的<b>字段名</b>（值一律不回显：{@code apiKeyRef} 与 {@code endpoint} 属敏感
     * 载体，禁外泄，X-49），并按 {@link #REGISTER_MISSING} 逐条自证为何未上线。禁把"参数收下但
     * 什么都没写"伪装成 200 成功，也禁就地写 yml 或内存表造出注册成功的假象。</p>
     */
    @PostMapping(AiCapabilityApiPaths.MODELS_REL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerLlmModel(
            @RequestBody(required = false) Map<String, Object> body) {
        String traceId = AiTraceSupport.currentTraceId();
        log.warn("[AiModelCatalogController] registerLlmModel 未上线，显式 501（零写入）: {} 字段 (traceId={})",
                body == null ? 0 : body.size(), traceId);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("written", false);
        extra.put("modelId", null);
        extra.put("activeVersion", null);
        extra.put("gitRef", null);
        extra.put("received_field_names", body == null ? List.of() : new ArrayList<>(
                new TreeMap<>(body).keySet()));
        extra.put("value_echo", "字段值不回显（apiKeyRef/endpoint 属敏感载体，X-49）");
        extra.put("missing", REGISTER_MISSING);
        return stub(traceId, "模型注册/升版的运行期写通道与授权载体均缺位", extra);
    }

    /**
     * #19 {@code checkModelHealth}：模型健康与熔断态 —— <b>显式 501 承流</b>。
     *
     * <p>契约标注的 {@code ECOS-AI-502} 属"接线后 runtime-monitor 不可用"的依赖态；本面尚未接线，
     * 故按「能力未上线」落 {@link AiErrorClass#STUB_CAPABILITY}（501），与 #16/#18 三面同形。
     * 四列一律 null + {@code healthApplicable=false}，禁沿用 {@code AIPModelController#healthCheck}
     * 的恒 {@code healthy}/{@code latencyMs=120} 假值形态。</p>
     */
    @GetMapping(AiCapabilityApiPaths.MODEL_HEALTH_REL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkModelHealth(
            @PathVariable(AiCapabilityApiPaths.P_MODEL_KEY) String modelKey) {
        String traceId = AiTraceSupport.currentTraceId();
        log.warn("[AiModelCatalogController] checkModelHealth 未上线，显式 501: modelKey={} (traceId={})",
                modelKey, traceId);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("modelKey", modelKey);
        extra.put("state", null);
        extra.put("errorRate", null);
        extra.put("windowCount", null);
        extra.put("lastProbeTime", null);
        extra.put("healthApplicable", false);
        extra.put("missing", HEALTH_MISSING);
        return stub(traceId, "模型维度熔断态与探测读数无载体，健康面不可判", extra);
    }

    /**
     * 过滤参数无载体 ⇒ 显式拒 400（经 {@code AiErrorAdvice} 的
     * {@link IllegalArgumentException} 臂渲染 {@code ECOS-AI-400}，禁自拼信封）。
     */
    private static void rejectUnsupportedFilter(String name, String value) {
        if (value != null && !value.isBlank()) {
            throw new IllegalArgumentException("过滤参数 " + name + " 无供数载体（" + FILTERS_UNAVAILABLE
                    + "），本面拒绝按不存在的列筛选");
        }
    }

    /** 「未上线」信封单源（禁在本类再拼第二套 501 信封）。 */
    private static ResponseEntity<ApiResponse<Map<String, Object>>> stub(
            String traceId, String reason, Map<String, Object> extra) {
        return AiResponseSupport.stubCapability(traceId, reason, extra);
    }
}
