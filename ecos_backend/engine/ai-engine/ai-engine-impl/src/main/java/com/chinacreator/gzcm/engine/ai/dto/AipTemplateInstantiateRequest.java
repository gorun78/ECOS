package com.chinacreator.gzcm.engine.ai.dto;

import java.util.List;

/** 模板实例化请求体（{@code slots} 键 = Playbook 占位符名）。 */
public record AipTemplateInstantiateRequest(String name, java.util.Map<String, String> slots) {

    /** 实例化回执：新 pipeline id + 转换报告。 */
    public record Result(String pipelineId, Report conversionReport) {
    }

    /** 转换报告：成功映射节点数 + 未映射的 {@code stepKey:stepType} 清单。 */
    public record Report(int mapped, List<String> unmapped) {
    }
}
