package com.chinacreator.gzcm.engine.cognitive2.dto;

import java.util.Map;

/**
 * 情景演示模板条目（DI-2 #147EK）。
 *
 * <p>键序：name / domain / variables / description / demo（demo 恒 true，标识内置演示素材）。</p>
 */
public record ScenarioTemplateVO(String name, String domain, Map<String, String> variables,
                                 String description, boolean demo) {
}
