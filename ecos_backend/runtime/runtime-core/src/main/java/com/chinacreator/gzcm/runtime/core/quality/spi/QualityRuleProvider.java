package com.chinacreator.gzcm.runtime.core.quality.spi;

import com.chinacreator.gzcm.runtime.core.quality.QualityRule;

import java.util.List;

/**
 * 质量规则提供者 SPI — 由上层 BUS-ZHI 模块实现。
 *
 * <p>内核 {@link com.chinacreator.gzcm.runtime.core.quality.QualityEvaluator}
 * 通过此 SPI 获取适用于指定数据集的规则列表。
 *
 * <p>BUS-ZHI 模块负责：规则定义、规则生命周期管理、规则版本控制、
 * 评分体系（数据集质量得分、趋势报告）。
 */
public interface QualityRuleProvider {

    /**
     * 获取适用于指定数据集的所有质量规则。
     *
     * @param datasetId 数据集标识
     * @return 规则列表
     */
    List<QualityRule> getRulesFor(String datasetId);

    /**
     * 质量评估完成后的回调，用于 BUS-ZHI 记录评估历史、更新评分。
     *
     * @param datasetId 数据集标识
     * @param results   评估结果
     */
    void onEvaluationComplete(String datasetId,
                               List<com.chinacreator.gzcm.runtime.core.quality.QualityResult> results);
}
