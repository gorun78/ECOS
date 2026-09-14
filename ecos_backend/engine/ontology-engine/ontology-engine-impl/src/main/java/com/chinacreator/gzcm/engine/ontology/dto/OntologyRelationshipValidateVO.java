package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 关系校验结果 VO — {@code POST /relationships/validate} 返回单元。
 *
 * <p>字段对齐 {@code OntologyService.validateRelationship} 输出。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyRelationshipValidateVO {

    /** 新建边后是否会引入环路（true=会，业务应拒绝写入） */
    private boolean hasCycle;

    /** 提示消息 */
    private String message;
}
