package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

/**
 * 自动发现预览·单资源条目（两腿同表异键）：
 * <ul>
 *   <li>可发现腿：{@code resourceName/entityCode/entityName/domainCode/discovered/fieldCount/
 *       typeDistribution/alreadyExists}；</li>
 *   <li>跳过腿：{@code resourceName/entityCode/discovered/reason}（"无字段数据"）。</li>
 * </ul>
 * <p>条件键 NON_NULL 省略，保持旧 Map 两腿各自的键面与键序。</p>
 */
@JsonPropertyOrder({"resourceName", "entityCode", "entityName", "domainCode", "discovered",
        "reason", "fieldCount", "typeDistribution", "alreadyExists"})
public record AutoDiscoverEntityPreviewVO(
        String resourceName,
        String entityCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) String entityName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String domainCode,
        boolean discovered,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reason,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer fieldCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Integer> typeDistribution,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean alreadyExists) {

    public static AutoDiscoverEntityPreviewVO skipped(String resourceName, String entityCode) {
        return new AutoDiscoverEntityPreviewVO(resourceName, entityCode, null, null, false,
                "无字段数据", null, null, null);
    }

    public static AutoDiscoverEntityPreviewVO discovered(String resourceName, String entityCode,
                                                         String domainCode, int fieldCount,
                                                         Map<String, Integer> typeDistribution,
                                                         boolean alreadyExists) {
        return new AutoDiscoverEntityPreviewVO(resourceName, entityCode, resourceName, domainCode,
                true, null, fieldCount, typeDistribution, alreadyExists);
    }
}
