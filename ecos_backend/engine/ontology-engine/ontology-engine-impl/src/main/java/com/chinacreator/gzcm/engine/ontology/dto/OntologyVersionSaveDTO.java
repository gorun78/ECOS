package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体版本（Ontology Version）新增 DTO — T16-2。
 *
 * <p>字段对应 {@code OntologyVersionService.createVersion} 实际消费的业务语义：
 * {@code changeLog} / {@code publisher} 均可选（缺省空串）。
 *
 * <p>T16-5 扩展：{@code ontologyId} 字段 — 仅简化端点
 * {@code POST /api/v1/ecos/versions}（OntologyVersionSimpleController）消费，
 * 缺省 "default"；T16-2 主端点（路径变量携带 ontologyId）忽略此字段，
 * 旧 T16-2 客户端不传该字段时行为不变（API 只增不改）。
 */
@Data
public class OntologyVersionSaveDTO {

    /** 目标本体 ID（简化端点可选，缺省 "default"；主端点由路径变量决定，此字段忽略） */
    private String ontologyId;

    /** 变更日志（可选） */
    private String changeLog;

    /** 发布人（可选） */
    private String publisher;
}
