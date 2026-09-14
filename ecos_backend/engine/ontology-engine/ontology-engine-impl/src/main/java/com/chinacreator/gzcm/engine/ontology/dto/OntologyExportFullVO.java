package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 本体全量导出 VO — T16-4 强类型返回容器。
 *
 * <p>对齐 {@code OntologyExportController.exportFull}（GET /api/v1/ontology/export）
 * 既有 Map 输出契约：按 {@code entityType} 分类的实体列表 + mappings 空占位。
 *
 * <p>对象/动作/函数类型均复用 T16-1 {@link OntologyEntityVO}（同一
 * {@code OntologyService.entityToMap} 输出结构）；
 * 关系（link_type）行结构动态且本端点当前置空列表，
 * T16-4: 导出 blob 载荷动态结构豁免 Map — 保持 {@code List<Object>}。
 * {@code mappings} 为历史空占位（空列表），保持 List 形态输出契约不变。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class OntologyExportFullVO {

    /** 对象类型（MASTER / OBJECT / object_type 类实体） */
    private List<OntologyEntityVO> objectTypes;

    /** 关系类型（LINK / link_type 类实体）— 动态行结构豁免（T16-4: 导出 blob 载荷动态结构豁免 Map） */
    private List<Object> linkTypes;

    /** 动作类型（ACTION / action_type 类实体） */
    private List<OntologyEntityVO> actionTypes;

    /** 函数类型（FUNCTION / function_type 类实体） */
    private List<OntologyEntityVO> functionTypes;

    /** 映射占位（历史契约空列表，保持输出） */
    private List<Object> mappings;
}
