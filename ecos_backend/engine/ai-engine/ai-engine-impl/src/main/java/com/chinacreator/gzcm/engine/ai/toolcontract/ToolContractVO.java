package com.chinacreator.gzcm.engine.ai.toolcontract;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 工具契约 28 要素投影（2026-11-23 从 {@code wagent.controller.WAgentToolContractController.ToolContractVO}
 * 抽取到中立包）。
 *
 * <p>消费方：{@code AgentToolController.describe}（{@code GET /api/v1/agent/tools/{name}} 的
 * {@code contract} 字段）。<b>键名与键序逐字冻结</b>：前端按 snake_case 读这 28 个键，
 * 改一名即破契约。</p>
 */
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

    public static ToolContractVO of(ToolContractDO t) {
        return new ToolContractVO(t.getName(), t.getVersion(), t.getEngine(), t.getCategory(),
                t.getSideEffect(), t.getDisclosure(), t.getToolset(), t.getSummary(),
                t.getStatus(), t.getCostClass(), t.getId(), t.getMinLevel(),
                t.getRequiredPermission(), t.getInputSchemaJson(), t.getOutputSchemaJson(),
                t.getTimeoutMs(), t.getRetryText(), t.getIsIdempotent(), t.getIsEvidenceOutput(),
                t.getDataClassificationText(), t.getRollbackPlanText(), t.getEndpointKind(),
                t.getEndpointUrl(), t.getEndpointMethod(), t.getOwner(), t.getSlaText(),
                t.getDeprecatedBy(), t.getCapabilityDepsText());
    }
}
