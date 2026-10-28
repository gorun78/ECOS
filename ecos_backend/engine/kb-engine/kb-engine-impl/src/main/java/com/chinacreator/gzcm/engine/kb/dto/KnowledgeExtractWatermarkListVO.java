package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽取水位线列表 — 详设-07 §7.3.2 第 13 行（{@code {items[]}}）＋ §7.3.1 分页约定
 * （{@code {items,total,page,size}}，默认 50／上限 500）。
 *
 * <p>{@code sourceType} 为入参回显：当前水位表无 {@code source_type} 列（V179），
 * 全部行均属唯一在位的 {@code ONTOLOGY_MAPPING} 来源，故该值只做口径回显、
 * <b>不</b>作为额外 SQL 谓词（声明的过滤条件与实际谓词必须一致，禁「假过滤」）。</p>
 */
@Data
public class KnowledgeExtractWatermarkListVO {

    /** 水位线活行（按 {@code update_time} 倒序）。 */
    private List<KnowledgeExtractWatermarkVO> items = new ArrayList<>();

    /** 活行总数。 */
    private long total;

    /** 当前页码（1-based）。 */
    private int page;

    /** 每页条数（≤ 500）。 */
    private int size;

    /** 来源类型回显。 */
    private String sourceType;
}
