package com.chinacreator.gzcm.engine.ontology.glossary;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 词条关系边持久化实体 — 对应 ecos_glossary_term_relation 表。
 *
 * <p>有向二元关系：{@code fromTermId --relationType--> toTermId}。
 * 边类型见 {@code glossary_relation_type} 字典
 * （ISA / SYNONYM / PART_OF / SEE_ALSO / CAUSAL / RELATED）。
 */
@Getter
@Setter
@NoArgsConstructor
public class GlossaryRelationEntity {

    /** 主键 */
    private Long id;

    /** 关系起点词条 id */
    private Long fromTermId;

    /** 关系终点词条 id */
    private Long toTermId;

    /** 边类型（glossary_relation_type 字典 code） */
    private String relationType;

    /** 关系权重（默认 100） */
    private Integer weight;

    /** 关系说明 */
    private String description;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}