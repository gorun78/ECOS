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
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    /** #14 摘要视图（10 键；渐进披露：列表只出 L1 摘要字段，完整契约走 #15）。 */
    public record ToolSummaryVO(@JsonProperty("name") String name,
                                @JsonProperty("version") String version,
                                @JsonProperty("engine") String engine,
                                @JsonProperty("category") String category,
                                @JsonProperty("side_effect") String sideEffect,
                                @JsonProperty("disclosure") String disclosure,
                                @JsonProperty("toolset") String toolset,
                                @JsonProperty("summary") String summary,
                                @JsonProperty("status") String status,
                                @JsonProperty("cost_class") String costClass) {

        static ToolSummaryVO of(WAgentToolContractDO t) {
            return new ToolSummaryVO(t.getName(), t.getVersion(), t.getEngine(), t.getCategory(),
                    t.getSideEffect(), t.getDisclosure(), t.getToolset(), t.getSummary(),
                    t.getStatus(), t.getCostClass());
        }
    }

    /** #15 完整契约视图（28 键 = 摘要 10 + V236 受控列 18）。 */
    public record ToolContractVO(@JsonProperty("name") String name,
                                 @JsonProperty("version") String version,
                                 @JsonProperty("engine") String engine,
                                 @JsonProperty("category") String category,
                                 @JsonProperty("side_effect") String sideEffect,
                                 @JsonProperty("disclosure") String disclosure,
                                 @JsonProperty("toolset") String toolset,
                                 @JsonProperty("summary") String summary,
                                 @JsonProperty("status") String status,
                                 @JsonProperty("cost_class") String costClass,
                                 @JsonProperty("tool_id") String toolId,
                                 @JsonProperty("min_level") Integer minLevel,
                                 @JsonProperty("required_permission") String requiredPermission,
                                 @JsonProperty("input_schema_json") String inputSchemaJson,
                                 @JsonProperty("output_schema_json") String outputSchemaJson,
                                 @JsonProperty("timeout_ms") Integer timeoutMs,
                                 @JsonProperty("retry_text") String retryText,
                                 @JsonProperty("is_idempotent") Integer isIdempotent,
                                 @JsonProperty("is_evidence_output") Integer isEvidenceOutput,
                                 @JsonProperty("data_classification_text") String dataClassificationText,
                                 @JsonProperty("rollback_plan_text") String rollbackPlanText,
                                 @JsonProperty("endpoint_kind") String endpointKind,
                                 @JsonProperty("endpoint_url") String endpointUrl,
                                 @JsonProperty("endpoint_method") String endpointMethod,
                                 @JsonProperty("owner") String owner,
                                 @JsonProperty("sla_text") String slaText,
                                 @JsonProperty("deprecated_by") String deprecatedBy,
                                 @JsonProperty("capability_deps_text") String capabilityDepsText) {

        public static ToolContractVO of(WAgentToolContractDO t) {
            return new ToolContractVO(t.getName(), t.getVersion(), t.getEngine(), t.getCategory(),
                    t.getSideEffect(), t.getDisclosure(), t.getToolset(), t.getSummary(),
                    t.getStatus(), t.getCostClass(), t.getId(), t.getMinLevel(),
                    t.getRequiredPermission(), t.getInputSchemaJson(), t.getOutputSchemaJson(),
                    t.getTimeoutMs(), t.getRetryText(), t.getIsIdempotent(), t.getIsEvidenceOutput(),
                    t.getDataClassificationText(), t.getRollbackPlanText(), t.getEndpointKind(),
                    t.getEndpointUrl(), t.getEndpointMethod(), t.getOwner(), t.getSlaText(),
                    t.getDeprecatedBy(), t.getCapabilityDepsText());
        }

        ToolDescriptionVO withPermissionFilter(String permissionFilter) {
            return new ToolDescriptionVO(name, version, engine, category, sideEffect, disclosure,
                    toolset, summary, status, costClass, toolId, minLevel, requiredPermission,
                    inputSchemaJson, outputSchemaJson, timeoutMs, retryText, isIdempotent,
                    isEvidenceOutput, dataClassificationText, rollbackPlanText, endpointKind,
                    endpointUrl, endpointMethod, owner, slaText, deprecatedBy, capabilityDepsText,
                    permissionFilter);
        }
    }

    /** #14 列表响应（5 键）。 */
    public record ToolSearchVO(@JsonProperty("tools") List<ToolSummaryVO> tools,
                               @JsonProperty("total") int total,
                               @JsonProperty("filtered_by_permission") int filteredByPermission,
                               @JsonProperty("permission_filter") String permissionFilter,
                               @JsonProperty("status_used") String statusUsed) {
    }

    /** #15 完整契约响应（28 键 + 尾部 permission_filter）。 */
    public record ToolDescriptionVO(@JsonProperty("name") String name,
                                    @JsonProperty("version") String version,
                                    @JsonProperty("engine") String engine,
                                    @JsonProperty("category") String category,
                                    @JsonProperty("side_effect") String sideEffect,
                                    @JsonProperty("disclosure") String disclosure,
                                    @JsonProperty("toolset") String toolset,
                                    @JsonProperty("summary") String summary,
                                    @JsonProperty("status") String status,
                                    @JsonProperty("cost_class") String costClass,
                                    @JsonProperty("tool_id") String toolId,
                                    @JsonProperty("min_level") Integer minLevel,
                                    @JsonProperty("required_permission") String requiredPermission,
                                    @JsonProperty("input_schema_json") String inputSchemaJson,
                                    @JsonProperty("output_schema_json") String outputSchemaJson,
                                    @JsonProperty("timeout_ms") Integer timeoutMs,
                                    @JsonProperty("retry_text") String retryText,
                                    @JsonProperty("is_idempotent") Integer isIdempotent,
                                    @JsonProperty("is_evidence_output") Integer isEvidenceOutput,
                                    @JsonProperty("data_classification_text") String dataClassificationText,
                                    @JsonProperty("rollback_plan_text") String rollbackPlanText,
                                    @JsonProperty("endpoint_kind") String endpointKind,
                                    @JsonProperty("endpoint_url") String endpointUrl,
                                    @JsonProperty("endpoint_method") String endpointMethod,
                                    @JsonProperty("owner") String owner,
                                    @JsonProperty("sla_text") String slaText,
                                    @JsonProperty("deprecated_by") String deprecatedBy,
                                    @JsonProperty("capability_deps_text") String capabilityDepsText,
                                    @JsonProperty("permission_filter") String permissionFilter) {
    }

    /** #14 列表（渐进披露入口：状态/披露形态/engine/toolset/keyword 收窄 + 本地权限地板过滤）。 */
    @GetMapping(WAgentApiPaths.TOOLS_COLLECTION)
    public ApiResponse<ToolSearchVO> searchTools(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "engine", required = false) String engine,
            @RequestParam(value = "toolset", required = false) String toolset,
            @RequestParam(value = "disclosure", required = false) String disclosure,
            @RequestParam(value = "keyword", required = false) String keyword) {
        IdentityWithRoles identity = identityResolver.requireWithRoles(); // 认证闸（JWT 前提，fail-closed 401）
        String effectiveStatus = normalizeStatus(status);
        List<WAgentToolContractDO> base = selectBase(engine, toolset, disclosure, effectiveStatus);

        String kw = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<ToolSummaryVO> tools = new ArrayList<>();
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
            tools.add(ToolSummaryVO.of(t));
        }
        return WAgentRunController.ok(new ToolSearchVO(tools, tools.size(), filteredByPermission,
                PERMISSION_FILTER, effectiveStatus));
    }

    /** #15 完整契约（name + version 闭合定位，uniq(name,version)）。 */
    @GetMapping(WAgentApiPaths.TOOL_BY_NAME)
    public ApiResponse<ToolDescriptionVO> describeTool(
            @PathVariable(WAgentApiPaths.P_NAME) String name,
            @RequestParam(value = "version", required = false) String version) {
        identityResolver.require();
        if (version == null || version.isBlank()) {
            throw WAgentOrchestrationException.validation("version 必填（契约按 name+version 闭合定位）");
        }
        WAgentToolContractDO tool = loadTool(name.trim(), version.trim());
        return WAgentRunController.ok(ToolContractVO.of(tool).withPermissionFilter(PERMISSION_FILTER));
    }

    /** #16 契约测试五关——执行器未实现：存在性校验先行，随后如实 501。 */
    @PostMapping(WAgentApiPaths.TOOL_VALIDATE)
    public ResponseEntity<ApiResponse<WAgentDegradation.NotImplementedVO>> validateToolContract(
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
}
