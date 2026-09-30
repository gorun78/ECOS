package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 实体链接结果 VO（PMO-74 H11-T4）。
 *
 * <p>承接 {@code EntityLinkerService#linkEntity} 的 Map 结果，键集合固定为
 * entityName / entityType / ontologyPath / confidence / message（message 仅未匹配时有值），
 * JSON 字段名与原 Map 完全一致（字段只增不删）。</p>
 */
@Data
public class EntityLinkResultVO {

    /** 输入的实体名称 */
    private String entityName;

    /** 输入的实体类型 */
    private String entityType;

    /** 匹配到的本体路径；未匹配时为"未匹配"或"未匹配(最高相似度=x.xx)" */
    private String ontologyPath;

    /** 匹配置信度（0~1 相似度） */
    private Double confidence;

    /** 可选说明（如 "no candidate ontology types found"），未命中时不出现该键（与原 Map 行为一致） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String message;
}
