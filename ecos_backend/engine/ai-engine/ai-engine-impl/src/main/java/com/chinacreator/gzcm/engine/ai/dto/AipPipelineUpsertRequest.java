package com.chinacreator.gzcm.engine.ai.dto;

/**
 * 逻辑编排创建/更新请求体（DI-2 强类型收口：禁 {@code Map<String,Object>} 出入参）。
 *
 * <p>{@code steps} 保持自由结构（画布 nodes/edges 结构随前端演进，后端只做透传与合并），
 * 其余字段全部声明型。更新时 {@code null} 字段表示「未出现、保持原值」。</p>
 */
public record AipPipelineUpsertRequest(
        String name,
        String description,
        String status,
        Object steps,
        Object nodes,
        Object edges,
        String templateRef) {
}
