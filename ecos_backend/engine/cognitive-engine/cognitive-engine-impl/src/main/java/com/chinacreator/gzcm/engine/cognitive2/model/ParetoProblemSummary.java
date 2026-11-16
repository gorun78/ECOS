package com.chinacreator.gzcm.engine.cognitive2.model;

/**
 * Pareto 历史列表行（D106；前端 {@code ParetoProblem} 契约四键）。
 *
 * @param timestamp ISO-8601 本地时间文本（前端直接渲染，不做二次格式化）
 */
public record ParetoProblemSummary(
        String problemId,
        String problemName,
        int frontSize,
        String timestamp) {
}
