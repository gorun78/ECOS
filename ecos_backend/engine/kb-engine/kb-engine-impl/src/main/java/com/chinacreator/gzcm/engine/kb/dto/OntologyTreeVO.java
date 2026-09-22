package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

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

    /** 业务域（来自本体 engine 的 {@code sourceType} / {@code domainCode}）；未分组时为 "default"。
     *  <b>保留兼容</b>：前端新消费路径读 {@code domainBreakdown}，此字段仅保留兼容，恒 "default"。 */
    private String domain;

    /** 当前域下的本体列表 */
    private List<OntologyItem> ontologies;

    /** 单个本体条目（id / code / name / entityCodes[] + entities[] + domainBreakdown）。 */
    @Data
    public static class OntologyItem {

        /** 本体业务 ID */
        private String id;

        /** 本体编码（code） */
        private String code;

        /** 本体名称 */
        private String name;

        /** 本体下的实体 code 数组（如 "customer" / "contract" / "order"）— 仅 code，向后兼容。 */
        private List<String> entityCodes;

        /** 本体下的完整实体简报（含 code/name/entityType/domainId/sortOrder）。
         *  <b>新增</b>：前端可按 {@code domainId} 分桶、按 {@code entityType} 区分 MASTER/TRANSACTION 徽章。 */
        private List<EntityBrief> entities;

        /** 按 {@code domainId} 分桶（LinkedHashMap 保插入序；空 domainId → "__unassigned__"）。
         *  <b>新增</b>：key = domainId，value = 该域下实体列表；前端「对象类型行」直接消费此字段。 */
        private Map<String, List<EntityBrief>> domainBreakdown;

        /** 实体简报（code/name/entityType/domainId/sortOrder）。 */
        @Data
        public static class EntityBrief {

            /** 对象类型 code（Department / Employee / ...） */
            private String code;

            /** 对象类型显示名（中文 部门 / 员工 / ...） */
            private String name;

            /** 实体类型（MASTER / TRANSACTION / CHILD / REFERENCE / ...） */
            private String entityType;

            /** 实体所属域 ID（dom_org / dom_hr / ...；可空 → 未分组，前端展示"未分组"） */
            private String domainId;

            /** 排序权重 */
            private Integer sortOrder;
        }
    }
}
