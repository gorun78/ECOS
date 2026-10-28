package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.Disclosure;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ToolContractStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentToolContractDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentToolContractDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.IdentityWithRoles;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工具契约端点（详细设计-10 §5.2 #14 searchTools / #15 describeTool / #16 validateToolContract，
 * F10-17/18，渐进披露入口）。
 *
 * <p><b>#14 权限过滤口径（诚实标注）</b>：本批执行<b>本地地板过滤</b>——状态词表合法性、
 * disclosure/engine/toolset/keyword 收窄，且 {@code required_permission} 非空的工具仅当
 * 认证主体角色字面命中才返回（角色缺失即剔除，fail-closed 方向）。OPA/ABAC 细粒度裁决
 * （{@code WAgentPolicyGuardPort} 占位）未接线 ⇒ 响应以 {@code permission_filter} 明示
 * 当前为 local-floor 形态，<b>禁</b>宣称已完成属主引擎裁决。</p>
 *
 * <p><b>#16 明确降级</b>：契约测试五关（input/output schema 试调用等）无执行器实现 ⇒
 * 存在性校验通过后如实 501；禁返回"validated"伪成功。</p>
 */
@RestController
public class WAgentToolContractController {

    /** 权限过滤形态标注单源（接线后改 "policy-engine"）。 */
    static final String PERMISSION_FILTER = "local-floor#F10-18(OPA WAgentPolicyGuardPort 占位未接线)";

    private final WAgentToolContractDao toolContractDao;
    private final WAgentIdentityResolver identityResolver;

    public WAgentToolContractController(WAgentToolContractDao toolContractDao,
                                        WAgentIdentityResolver identityResolver) {
        this.toolContractDao = toolContractDao;
        this.identityResolver = identityResolver;
    }

    /** #14 列表（渐进披露入口：状态/披露形态/engine/toolset/keyword 收窄 + 本地权限地板过滤）。 */
    @GetMapping(WAgentApiPaths.TOOLS_COLLECTION)
    public ApiResponse<Map<String, Object>> searchTools(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "engine", required = false) String engine,
            @RequestParam(value = "toolset", required = false) String toolset,
            @RequestParam(value = "disclosure", required = false) String disclosure,
            @RequestParam(value = "keyword", required = false) String keyword) {
        IdentityWithRoles identity = identityResolver.requireWithRoles(); // 认证闸（JWT 前提，fail-closed 401）
        String effectiveStatus = normalizeStatus(status);
        List<WAgentToolContractDO> base = selectBase(engine, toolset, disclosure, effectiveStatus);

        String kw = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> tools = new ArrayList<>();
        int filteredByPermission = 0;
        for (WAgentToolContractDO t : base) {
            if (!effectiveStatus.equals(t.getStatus())) {
                continue; // toolset 分支 DAO 未按状态收窄 ⇒ 服务层后置过滤（其余分支幂等）
            }
            if (kw != null && !matchesKeyword(t, kw)) {
                continue;
            }
            if (!permissionAllowed(t, identity)) {
                filteredByPermission++;
                continue;
            }
            tools.add(summaryView(t));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tools", tools);
        data.put("total", tools.size());
        data.put("filtered_by_permission", filteredByPermission);
        data.put("permission_filter", PERMISSION_FILTER);
        data.put("status_used", effectiveStatus);
        return WAgentRunController.ok(data);
    }

    /** #15 完整契约（name + version 闭合定位，uniq(name,version)）。 */
    @GetMapping(WAgentApiPaths.TOOL_BY_NAME)
    public ApiResponse<Map<String, Object>> describeTool(
            @PathVariable(WAgentApiPaths.P_NAME) String name,
            @RequestParam(value = "version", required = false) String version) {
        identityResolver.require();
        if (version == null || version.isBlank()) {
            throw WAgentOrchestrationException.validation("version 必填（契约按 name+version 闭合定位）");
        }
        WAgentToolContractDO tool = loadTool(name.trim(), version.trim());
        Map<String, Object> data = contractView(tool);
        data.put("permission_filter", PERMISSION_FILTER);
        return WAgentRunController.ok(data);
    }

    /** #16 契约测试五关——执行器未实现：存在性校验先行，随后如实 501。 */
    @PostMapping(WAgentApiPaths.TOOL_VALIDATE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateToolContract(
            @PathVariable(WAgentApiPaths.P_NAME) String name,
            @RequestParam(value = "version", required = false) String version) {
        identityResolver.require();
        if (version == null || version.isBlank()) {
            throw WAgentOrchestrationException.validation("version 必填（契约按 name+version 闭合定位）");
        }
        loadTool(name.trim(), version.trim()); // fail-fast：不存在的契约不进入"验证"
        return WAgentDegradation.notImplemented("F10-17 契约测试五关执行器(schema 试调用/回滚预案演练)未接线");
    }

    // ── 小件 ────────────────────────────────────────────────────────

    private static String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return ToolContractStatus.ACTIVE.value(); // 缺省只披露 active（F10-17：仅 active 可被选择执行）
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (!ToolContractStatus.ALL.contains(v)) {
            throw WAgentOrchestrationException.validation("status 越界（ToolContractStatus 六值单源）: " + raw);
        }
        return v;
    }

    private List<WAgentToolContractDO> selectBase(String engine, String toolset, String disclosure,
                                                  String status) {
        if (toolset != null && !toolset.isBlank()) {
            return toolContractDao.findByToolset(toolset.trim());
        }
        if (engine != null && !engine.isBlank()) {
            return toolContractDao.findByEngineAndStatus(engine.trim(), status);
        }
        if (disclosure != null && !disclosure.isBlank()) {
            String d = disclosure.trim().toLowerCase(Locale.ROOT);
            if (!Disclosure.ALL.contains(d)) {
                throw WAgentOrchestrationException.validation("disclosure 越界（Disclosure 二值单源）: " + disclosure);
            }
            return toolContractDao.findByDisclosureAndStatus(d, status);
        }
        return toolContractDao.findByStatus(status);
    }

    private WAgentToolContractDO loadTool(String name, String version) {
        return toolContractDao.findByNameAndVersion(name, version)
                .orElseThrow(WAgentOrchestrationException::notFound);
    }

    /** 本地权限地板：required_permission 空 ⇒ 公开面；非空 ⇒ 角色字面命中才返回（fail-closed 剔除）。 */
    private static boolean permissionAllowed(WAgentToolContractDO t, IdentityWithRoles identity) {
        String required = t.getRequiredPermission();
        if (required == null || required.isBlank()) {
            return true;
        }
        return identity.hasRole(required.trim());
    }

    private static boolean matchesKeyword(WAgentToolContractDO t, String kwLower) {
        return contains(t.getName(), kwLower) || contains(t.getSummary(), kwLower)
                || contains(t.getToolset(), kwLower) || contains(t.getCategory(), kwLower);
    }

    private static boolean contains(String value, String kwLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(kwLower);
    }

    /** #14 摘要视图（渐进披露：列表只出 L1 摘要字段，完整契约走 #15）。 */
    static Map<String, Object> summaryView(WAgentToolContractDO t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", t.getName());
        m.put("version", t.getVersion());
        m.put("engine", t.getEngine());
        m.put("category", t.getCategory());
        m.put("side_effect", t.getSideEffect());
        m.put("disclosure", t.getDisclosure());
        m.put("toolset", t.getToolset());
        m.put("summary", t.getSummary());
        m.put("status", t.getStatus());
        m.put("cost_class", t.getCostClass());
        return m;
    }

    /**
     * #15 完整契约视图（V236 全受控列）。
     *
     * <p>公开投影单源：{@code GET /api/v1/wagent/tools/{name}} 与
     * {@code GET /api/v1/agent/tools/{name}}（附录 A 第 21 条）共用本方法，
     * 禁在旁路另写一套 17 要素字段表。</p>
     */
    public static Map<String, Object> contractView(WAgentToolContractDO t) {
        Map<String, Object> m = new LinkedHashMap<>(summaryView(t));
        m.put("tool_id", t.getId());
        m.put("min_level", t.getMinLevel());
        m.put("required_permission", t.getRequiredPermission());
        m.put("input_schema_json", t.getInputSchemaJson());
        m.put("output_schema_json", t.getOutputSchemaJson());
        m.put("timeout_ms", t.getTimeoutMs());
        m.put("retry_text", t.getRetryText());
        m.put("is_idempotent", t.getIsIdempotent());
        m.put("is_evidence_output", t.getIsEvidenceOutput());
        m.put("data_classification_text", t.getDataClassificationText());
        m.put("rollback_plan_text", t.getRollbackPlanText());
        m.put("endpoint_kind", t.getEndpointKind());
        m.put("endpoint_url", t.getEndpointUrl());
        m.put("endpoint_method", t.getEndpointMethod());
        m.put("owner", t.getOwner());
        m.put("sla_text", t.getSlaText());
        m.put("deprecated_by", t.getDeprecatedBy());
        m.put("capability_deps_text", t.getCapabilityDepsText());
        return m;
    }
}
