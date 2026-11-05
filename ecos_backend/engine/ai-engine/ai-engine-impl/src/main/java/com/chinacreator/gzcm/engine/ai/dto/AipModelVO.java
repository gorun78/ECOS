package com.chinacreator.gzcm.engine.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * AIP 模型行（内存 store 的唯一行模型）。
 *
 * <p>键序：{@code id/name/provider/modelType/description[/endpoint][/apiKeyRef]/status/
 * createdAt/updatedAt}。条件键：内置模型不带 {@code endpoint/apiKeyRef}（NON_NULL 省略），
 * 手工注册模型两键恒在（缺省空串），与旧 Map 两腿同形。</p>
 */
@JsonPropertyOrder({"id", "name", "provider", "modelType", "description", "endpoint",
        "apiKeyRef", "status", "createdAt", "updatedAt"})
public record AipModelVO(
        String id,
        String name,
        String provider,
        String modelType,
        String description,
        @JsonInclude(JsonInclude.Include.NON_NULL) String endpoint,
        @JsonInclude(JsonInclude.Include.NON_NULL) String apiKeyRef,
        String status,
        String createdAt,
        String updatedAt) {

    /** 部分更新：仅覆盖请求体出现的键（语义与旧 Map 拷贝+覆盖一致）。 */
    public AipModelVO withUpdated(String newName, String newProvider, String newModelType,
                                  String newDescription, String newEndpoint, String newApiKeyRef,
                                  String newStatus, String newUpdatedAt) {
        return new AipModelVO(id,
                newName != null ? newName : name,
                newProvider != null ? newProvider : provider,
                newModelType != null ? newModelType : modelType,
                newDescription != null ? newDescription : description,
                newEndpoint != null ? newEndpoint : endpoint,
                newApiKeyRef != null ? newApiKeyRef : apiKeyRef,
                newStatus != null ? newStatus : status,
                createdAt, newUpdatedAt);
    }
}
