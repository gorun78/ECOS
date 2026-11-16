package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.Map;

/**
 * 前沿上的一枚解（D106；wire 形态 {@code {variables:{x1..xn}, objectives:{f1..fm}}}）。
 *
 * <p><b>为什么用 {@code Map} 承载内部键而不展开成字段</b>：变量维数与目标数由请求决定
 * （2~5 变量 × 2~3 目标），字段名集合在编译期未知。本 record 的职责只是给「解」这层
 * 结构一个强类型外壳，避免载荷整体退化成 {@code Map<String,Object>} ——
 * 键值面仍由 {@code Nsga2Optimizer} 的维度决定。</p>
 */
public record ParetoSolution(
        Map<String, Double> variables,
        Map<String, Double> objectives) {
}
