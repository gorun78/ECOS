package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.capability.web.AiCapabilityApiPaths;
import com.chinacreator.gzcm.engine.ai.dao.LlmModelRegistryDao;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.engine.ai.approval.AiIdentityResolver;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
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
 *   <li>{@code POST /api/v1/ai/models} → {@code registerLlmModel}（#18，<b>已上线</b>：
 *       E-3 模型注册入表，载体 {@code ecos_ai.ecos_llm_model}（V359）；表不可用时 501 承流）；</li>
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

    /** #19 未上线的欠账清单（取证单源，响应自证；逐条均可复验）。 */
    static final List<String> HEALTH_MISSING = List.of(
            "模型维度熔断态载体：唯一熔断器 AgentCircuitBreaker 以 agentId 为键，且是 AgentLoopController 的字段级 new 实例（非 Spring 单例），改按 modelKey 取用属推断式关联（禁）",
            "探测读数载体：runtime-monitor 指标门面 IMetricRecorder 只有 increment(name, tags) 写面，无 state/errorRate/windowCount/lastProbeTime 读面");

    /** 契约 #17 要求但本面无载体的列（缺席即 null + {@code *Applicable=false}）。 */
    private static final List<String> UNSUPPORTED_FILTERS = List.of(
            AiCapabilityApiPaths.Q_CAPABILITY, AiCapabilityApiPaths.Q_DATA_CLASSIFICATION);

    private final LLMGateway llmGateway;

    /** E-3 模型注册载体（V359）。required=false：未装配时 POST 走 501 承流而非报错。 */
    private final LlmModelRegistryDao modelRegistryDao;

    /** OPA 裁决（ai:model:register，fail-closed：不可用即拒）。required=false 供单测装配。 */
    private final AiSecurityEngineClient securityClient;

    /** 身份与角色（JWT claims）。 */
    private final AiIdentityResolver identityResolver;

    /** Git 版本归档（表只承载在用版本，历史版本走 Git — 铁律 §2.5 配置/资产二分）。 */
    private final DesignAssetArchiver designAssetArchiver;

    /**
     * 模型注册归档的目标仓 —— 取 {@code ecos_control.ecos_git_repo} 已注册的六枚之一。
     *
     * <p>映射依据同 {@code WAgentCandidateController#repositoryIdFor} 的资产类型惯例：
     * {@code COGNITIVE_MODEL_DEF} / {@code MODEL_ROUTING} 等模型定义类资产归
     * {@code cognitive-model}。LLM 模型注册同属模型定义，故沿用该仓，
     * <b>不自造仓库 id</b>（自造必定查无此仓 ⇒ 归档恒 notAvailable，等于空接）。</p>
     */
    private static final String MODEL_ARCHIVE_REPO_ID = "cognitive-model";

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public AiModelCatalogController(@Autowired(required = false) LLMGateway llmGateway,
                                   @Autowired(required = false) LlmModelRegistryDao modelRegistryDao,
                                   @Autowired(required = false) AiSecurityEngineClient securityClient,
                                   @Autowired(required = false) AiIdentityResolver identityResolver,
                                   @Autowired(required = false) DesignAssetArchiver designAssetArchiver) {
        this.llmGateway = llmGateway;
        this.modelRegistryDao = modelRegistryDao;
        this.securityClient = securityClient;
        this.identityResolver = identityResolver;
        this.designAssetArchiver = designAssetArchiver;
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
     * #18 {@code registerLlmModel}：注册/升版模型 —— <b>真实写入</b>
     * （E-3，2026-11-19 用户裁决「模型注册数据入表」，载体 {@code ecos_ai.ecos_llm_model} / V359）。
     *
     * <p><b>升版语义</b>：同 {@code modelKey} 再注册 ⇒ 旧 ACTIVE 行置 {@code RETIRED}、
     * 新行 {@code version_no + 1}。不物理删（IR03）。</p>
     *
     * <p><b>密钥纪律</b>：{@code apiKeyRef} 只收引用名，落库列 {@code api_key_ref}；
     * 响应与日志<b>一律不回显</b>其值（X-49）。{@code endpoint} 同理不入日志。</p>
     *
     * <p><b>表不在</b>：{@code tableReady()} 为 false ⇒ 501 承流（契约级"载体不可用"），
     * 禁静默写失败伪装成功。</p>
     */
    @PostMapping(AiCapabilityApiPaths.MODELS_REL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerLlmModel(
            @RequestBody(required = false) Map<String, Object> body)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        String traceId = AiTraceSupport.currentTraceId();

        if (modelRegistryDao == null || !modelRegistryDao.tableReady()) {
            log.warn("[AiModelCatalogController] registerLlmModel 载体不可用（V359 未实跑或 DAO 未装配），501: traceId={}", traceId);
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("written", false);
            extra.put("missing", List.of("存储载体：ecos_ai.ecos_llm_model（V359）未就位"));
            return stub(traceId, "模型注册存储载体不可用", extra);
        }

        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("请求体不可为空（需 modelKey 与 provider）");
        }

        // ① OPA 裁决（fail-closed）：securityClient 未装配 / security-engine 不可用 / 角色不足
        //    一律拒绝。契约原文要求 role:AI_ADMIN，但该角色码全仓零命中；按平台既有超管码
        //    ROLE_SUPER_ADMIN 授权（rbac.rego 的 is_ai_model_admin）。
        //    拒绝面必须是 403 ECOS-AI-403（契约口径），不能抛 IllegalStateException 让兜底
        //    渲染成 502——"你没有权限"不是"服务端坏了"，客户端按状态行分派时会误判成可重试。
        if (securityClient == null || identityResolver == null) {
            log.warn("[AiModelCatalogController] registerLlmModel 裁决链未装配，fail-closed 拒绝: traceId={}", traceId);
            return denied(traceId, "模型注册的授权链未装配（security-engine 客户端缺失）");
        }
        var identity = identityResolver.requireWithRoles();
        if (!securityClient.evaluate(identity.operator(), identity.tenantId(),
                identity.roles(), "ai:model:register", Map.of("modelKey", String.valueOf(body.get("modelKey"))))) {
            log.warn("[AiModelCatalogController] registerLlmModel OPA 拒绝: operator={} roles={} (traceId={})",
                    identity.operator(), identity.roles(), traceId);
            return denied(traceId, "模型注册需平台管理员角色（ai:model:register 裁决未通过）");
        }

        String modelKey = str(body.get("modelKey"));
        String provider = str(body.get("provider"));
        if (modelKey == null || provider == null) {
            throw new IllegalArgumentException("modelKey 与 provider 均为必填");
        }

        String endpoint = str(body.get("endpoint"));
        String apiKeyRef = str(body.get("apiKeyRef"));
        int contextLimit = intOrDefault(body.get("contextLimit"), 0);
        String costClass = str(body.get("costClass"));
        String capabilitiesJson = jsonOf(body.get("capabilities"));
        // 操作者取 JWT 身份，不由 body 自报（否则审计可被伪造）
        String actor = identity.operator();

        String id = java.util.UUID.randomUUID().toString();
        int nextVersion = modelRegistryDao.maxVersion(modelKey) + 1;

        // 升版必须原子：先 retire 旧 ACTIVE 行、再插新行，两步任一步失败都不得留下
        // "只有 RETIRED、没有 ACTIVE"的空窗（V360 活体实测暴露过该副作用）。
        modelRegistryDao.replaceActive(id, modelKey, provider, endpoint, apiKeyRef,
                contextLimit, capabilitiesJson, costClass, String.valueOf(nextVersion), actor);

        log.info("[AiModelCatalogController] registerLlmModel 已落库: modelKey={} version={} traceId={}",
                modelKey, nextVersion, traceId);

        // ② Git 版本归档：表只承载「在用版本」，历史版本按铁律 §2.5 走 Git。
        //    归档失败**不回滚落库**（数据面已成功），但如实返回 gitRef=null + 原因，
        //    禁编造 ref 让调用方误以为已归档。
        //    异常收口在非 Controller 的 DesignAssetArchiveSupport（F06-15 卫生门禁要求
        //    控制器类内零 catch-all）：归档器抛错时不得把已成功的落库渲染成 5xx。
        DesignAssetArchiveSupport.Outcome archive = DesignAssetArchiveSupport.tryArchive(
                "models/" + modelKey,
                designAssetArchiver == null ? null : () -> {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("modelKey", modelKey);
                    snapshot.put("provider", provider);
                    snapshot.put("endpoint", endpoint);
                    // 密钥只存引用名（X-49）；表与 Git 一致口径。
                    snapshot.put("apiKeyRef", apiKeyRef);
                    snapshot.put("contextLimit", contextLimit);
                    snapshot.put("costClass", costClass);
                    snapshot.put("version", String.valueOf(nextVersion));
                    var outcome = designAssetArchiver.archive(MODEL_ARCHIVE_REPO_ID, "models", modelKey,
                            String.valueOf(nextVersion),
                            MAPPER.writeValueAsString(snapshot),
                            "chore(models): register " + modelKey + " v" + nextVersion, actor);
                    if (outcome == null) {
                        return new DesignAssetArchiveSupport.Outcome(null, null);
                    }
                    return new DesignAssetArchiveSupport.Outcome(
                            outcome.commitId(), outcome.reason());
                },
                DesignAssetArchiveSupport.absentReason("DesignAssetArchiver"));
        String gitRef = archive.gitRef();
        String archiveNote = archive.note();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("modelId", id);
        data.put("modelKey", modelKey);
        data.put("activeVersion", String.valueOf(nextVersion));
        data.put("gitRef", gitRef);
        data.put("gitRefApplicable", gitRef != null);
        if (archiveNote != null) {
            data.put("gitRefUnavailableReason", archiveNote);
        }
        data.put("written", true);
        return ResponseEntity.ok(ApiResponse.success(data)
                .withTraceId(traceId));
    }

    private static String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static int intOrDefault(Object v, int fallback) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * 能力清单序列化。
     *
     * <p><b>失败即上抛</b>（2026-11-27）：原实现 {@code catch (Exception) → return null} 既违
     * F06-15「控制器类内零 catch-all」，又是静默降级（序列化失败被伪装成「该列无值」）。
     * 改由 {@code AiErrorAdvice} 统一分类渲染，与 {@code AIPAgentController} 的
     * {@code throws JsonProcessingException} 同形。</p>
     */
    private static String jsonOf(Object v) throws com.fasterxml.jackson.core.JsonProcessingException {
        return v == null ? null : MAPPER.writeValueAsString(v);
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

    /**
     * 403 拒绝信封（契约 {@code ECOS-AI-403}）。
     *
     * <p>不抛异常的原因：{@link AiErrorAdvice} 的兜底把未预期异常渲染成 502，
     * 而「角色不足」是<b>客户端的权限问题</b>，不是服务端故障——按 502 返回会让调用方
     * 误判为可重试。故在此直接构造 403，与 {@code AiErrorClass.GUARDRAIL_DENIED} 同码。</p>
     */
    private static ResponseEntity<ApiResponse<Map<String, Object>>> denied(String traceId, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errorCode", AiErrorClass.GUARDRAIL_DENIED.errorCode());
        data.put("errorClass", AiErrorClass.GUARDRAIL_DENIED.name());
        data.put("traceId", traceId == null ? "-" : traceId);
        data.put("retryable", false);
        data.put("written", false);
        data.put("reason", reason);
        ApiResponse<Map<String, Object>> body = ApiResponse.error(403,
                AiErrorClass.GUARDRAIL_DENIED.errorCode(), reason);
        body.setData(data);
        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(body);
    }
}
