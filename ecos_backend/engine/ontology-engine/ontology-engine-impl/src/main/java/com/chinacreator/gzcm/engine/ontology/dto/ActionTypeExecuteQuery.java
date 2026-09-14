package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 动作类型执行入参 Query — T16-5 强类型入参。
 *
 * <p>对应 {@code POST /api/v1/ontology/action-types/{id}/execute} 的 body：
 * {@code objectId}（对象 ID，可选，缺省空串）+ {@code context}（动态执行上下文，
 * 元素结构随业务演进，保持 Object 豁免）。
 *
 * <p>未知字段由 Spring MVC 全局 ObjectMapper（FAIL_ON_UNKNOWN_PROPERTIES=false）
 * 忽略，与既有 Map 入参行为一致。
 */
@Data
public class ActionTypeExecuteQuery {

    /** 对象 ID（可选，缺省空串） */
    private String objectId;

    /** 执行上下文（可选，动态 KV，缺省空 Map） */
    private Object context;
}
