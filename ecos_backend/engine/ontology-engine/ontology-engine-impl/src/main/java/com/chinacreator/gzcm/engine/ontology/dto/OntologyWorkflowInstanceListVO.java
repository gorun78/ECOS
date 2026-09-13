package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 工作流实例列表包装 VO — T16-2 强类型返回容器。
 *
 * <p>对齐 {@code OntologyWorkflowController.listInstances} 既有 API 输出结构：
 * {@code data}（实例列表） + {@code total}（条数）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyWorkflowInstanceListVO {

    /** 实例列表 */
    private List<OntologyWorkflowInstanceVO> data;

    /** 实例条数（= data.size()，兼容既有消费者） */
    private Integer total;
}
