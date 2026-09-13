package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 工作流定义列表（分页 + 兼容）包装 VO — T16-2 强类型返回容器。
 *
 * <p>对齐 {@code OntologyWorkflowController.listDefinitions} 既有 API 输出结构：
 * {@code records}（当前页切片） + {@code data}（全量，兼容旧消费者） +
 * {@code total} + {@code pageNum} + {@code pageSize}。
 *
 * <p>规范分页结构（PMO-39 T2 §3）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyWorkflowPageVO {

    /** 当前页切片记录 */
    private List<OntologyWorkflowVO> records;

    /** 全量列表（兼容既有消费者；API 只增不改） */
    private List<OntologyWorkflowVO> data;

    /** 总记录数 */
    private Long total;

    /** 当前页码（1 起） */
    private Integer pageNum;

    /** 每页大小 */
    private Integer pageSize;
}
