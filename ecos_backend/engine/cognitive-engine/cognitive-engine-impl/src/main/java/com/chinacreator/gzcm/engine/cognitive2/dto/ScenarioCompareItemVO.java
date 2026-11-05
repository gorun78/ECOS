package com.chinacreator.gzcm.engine.cognitive2.dto;

import java.util.Map;

/**
 * 情景对比条目（DI-2 #147EK）。
 *
 * <p>键序：name / confidence / summary / predictions；predictions 为推演服务原样预测结构。</p>
 */
public record ScenarioCompareItemVO(String name, double confidence, String summary,
                                    Map<String, Object> predictions) {
}
