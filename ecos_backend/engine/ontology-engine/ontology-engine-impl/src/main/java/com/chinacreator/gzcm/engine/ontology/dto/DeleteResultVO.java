package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 操作删除结果 VO — T16-2 强类型返回。
 *
 * <p>用于 DELETE {@code /api/v1/ontology/objects/{id}} /
 * {@code /api/v1/ontology/links/{id}}：替代原先返回的
 * {@code Map.of("deleted", true, "id", id)} Map 形态。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeleteResultVO {

    /** 是否删除成功 */
    private Boolean deleted;

    /** 被删除项的主键 ID */
    private String id;
}
