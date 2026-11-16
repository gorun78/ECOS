package com.chinacreator.gzcm.engine.cognitive2.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 一次优化的响应体（D106；前端 {@code ParetoOptimizeResult} 契约四键）。
 *
 * <p>{@code elapsed_ms} 是下划线键 —— 前端类型就是这么声明的，与仓库其它面
 * 的 camelCase 不一致属既有契约，不为「好看」而改。</p>
 */
public record ParetoOptimizeResult(
        String problemId,
        int frontSize,
        List<ParetoSolution> solutions,
        @JsonProperty("elapsed_ms") long elapsedMs) {
}
