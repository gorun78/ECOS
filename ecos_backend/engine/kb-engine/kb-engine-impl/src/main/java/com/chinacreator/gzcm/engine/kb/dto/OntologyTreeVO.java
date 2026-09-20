package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.List;

/**
 * 本体树 VO — 按 domain 分组，列出本体（{@code ontology_id/code}）及其实体 codes。
 *
 * <p>用途：知识工作台「实例化目标选择器」（T7 端点
 * {@code GET /api/v1/knowledge/extract/ontology-tree}），
 * 出参结构见 Controller javadoc；当 {@code /api/v1/ecos/versions} 或
 * {@code /api/v1/ecos/ontologies} 不可用（或无数据）时返回空列表，
 * 不抛 500（前端可渲染空树）。
 */
@Data
public class OntologyTreeVO {

    /** 业务域（来自本体 engine 的 {@code sourceType} / {@code domainCode}）；未分组时为 "default"。 */
    private String domain;

    /** 当前域下的本体列表 */
    private List<OntologyItem> ontologies;

    /** 单个本体条目（id / code / name / entityCodes[]）。 */
    @Data
    public static class OntologyItem {

        /** 本体业务 ID */
        private String id;

        /** 本体编码（code） */
        private String code;

        /** 本体名称 */
        private String name;

        /** 本体下的实体 code 数组（如 "customer" / "contract" / "order"） */
        private List<String> entityCodes;
    }
}
