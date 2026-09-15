package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.List;

/**
 * 抽取实体转候选本体（PMO-50 T5）请求体。
 *
 * <p>对应 {@code POST /api/v1/knowledge/extract/promote-to-candidate}：
 * 把知识抽取审核中选中的实体提交给 ontology-engine 生成「本体变更提案」（候选本体）。
 */
@Data
public class ExtractionPromoteRequest {

    /** 待转候选本体的实体列表（前端审核面板选中项） */
    private List<EntityRef> entities;

    /** 来源抽取任务 ID（写入提案 payload，供候选本体溯源） */
    private String sourceExtractionId;

    /** 单个实体引用（名称 + 类型）。 */
    @Data
    public static class EntityRef {

        /** 实体名称（提案 targetEntity） */
        private String name;

        /** 实体类型（写入提案 payload，供本体建模参考） */
        private String type;
    }
}