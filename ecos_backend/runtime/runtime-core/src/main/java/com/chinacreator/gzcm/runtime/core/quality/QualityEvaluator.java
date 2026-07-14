package com.chinacreator.gzcm.runtime.core.quality;

import java.util.List;

/**
 * 数据质量评估器 — 内核质量门禁。
 *
 * <p>在任何数据落盘操作（INSERT / UPDATE / 批量导入）前调用此接口，
 * 根据上层 BUS-ZHI 模块通过 {@link com.chinacreator.gzcm.runtime.core.quality.spi.QualityRuleProvider}
 * 注册的规则集，对数据执行校验。
 *
 * <p>使用方式：
 * <pre>{@code
 * List<QualityResult> results = qualityEvaluator.evaluate("dataset.person_info", rows);
 * boolean allPassed = results.stream().allMatch(QualityResult::isPassed);
 * if (!allPassed) {
 *     // 记录质量事件，ERROR 级别规则失败的拒绝入库
 *     throw new DataQualityException(results);
 * }
 * }</pre>
 *
 * <p>上层 BUS-ZHI 模块负责规则定义、评分体系和质量报告。
 *
 * @see com.chinacreator.gzcm.runtime.core.quality.spi.QualityRuleProvider
 */
public interface QualityEvaluator {

    /**
     * 对目标数据集执行全部适用规则的质量评估。
     *
     * @param datasetId 数据集标识，如 "person_info"
     * @param rows      待校验的数据行（每行 Map 表示）
     * @return 各规则的评估结果列表
     */
    List<QualityResult> evaluate(String datasetId, List<java.util.Map<String, Object>> rows);

    /**
     * 对单条数据执行指定规则的快速评估。
     *
     * @param rule  质量规则
     * @param row   单行数据
     * @return 评估结果
     */
    QualityResult evaluateSingle(QualityRule rule, java.util.Map<String, Object> row);
}
