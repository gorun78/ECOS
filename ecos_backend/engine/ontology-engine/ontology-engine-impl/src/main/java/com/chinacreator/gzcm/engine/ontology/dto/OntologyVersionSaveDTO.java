package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体版本（Ontology Version）新增 DTO — T16-2。
 *
 * <p>字段对应 {@code OntologyVersionService.createVersion} 实际消费的业务语义：
 * {@code changeLog} / {@code publisher} 均可选（缺省空串）。
 */
@Data
public class OntologyVersionSaveDTO {

    /** 变更日志（可选） */
    private String changeLog;

    /** 发布人（可选） */
    private String publisher;
}
