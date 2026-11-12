package com.chinacreator.gzcm.engine.ai.dto;

import java.util.List;

/**
 * 模板实例化请求体。
 *
 * <p>{@code ref} 走请求体而非路径段：模板引用天然含 {@code /}（git ref {@code repo/pb1}、
 * 内置 {@code builtin/intent/FORECAST}），路径编码 {@code %2F} 会被 Tomcat 拒为 400。</p>
 *
 * @param ref   模板引用键（目录项的 {@code ref}，原样传入）
 * @param name  新编排名称（空则由服务端按模板兜底）
 * @param slots 槽位参数（键 = Playbook 占位符名；内置模板无占位，可缺省）
 */
public record AipTemplateInstantiateRequest(String ref, String name, java.util.Map<String, String> slots) {

    /** 实例化回执：新 pipeline id + 转换报告。 */
    public record Result(String pipelineId, Report conversionReport) {
    }

    /** 转换报告：成功映射节点数 + 未映射的 {@code stepKey:stepType} 清单。 */
    public record Report(int mapped, List<String> unmapped) {
    }
}
