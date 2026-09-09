package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Pipeline 断点调试 — 会话创建请求 DTO。
 * <p>
 * definitionId 与 definition 二选一：
 * <ul>
 *   <li>definitionId：引用库中已存在的 Pipeline 定义 ID（节点/依赖从 DB 加载）</li>
 *   <li>definition：内联 ad-hoc 定义（画布尚未保存时直接调试，不落库）</li>
 * </ul>
 *
 * @author DataBridge Datanet Team
 */
@Data
public class PipelineDebugStartDTO {

    /** 库中已存在的 Pipeline 定义 ID（与 definition 二选一） */
    private String definitionId;

    /** 内联 ad-hoc 定义（与 definitionId 二选一） */
    private PipelineDebugDefinitionDTO definition;

    /** 断点列表（nodeId + 可选触发条件表达式） */
    private List<BreakpointSpecDTO> breakpoints;

    /**
     * 内联 ad-hoc Pipeline 定义。
     */
    @Data
    public static class PipelineDebugDefinitionDTO {

        /** 调试会话显示名称（非必填） */
        private String name;

        /** 节点列表 */
        private List<PipelineDebugNodeDTO> nodes;
    }

    /**
     * 内联 ad-hoc 节点。
     */
    @Data
    public static class PipelineDebugNodeDTO {

        /** 节点唯一标识（前端画布节点 id） */
        private String nodeId;

        /** 节点类型: SOURCE_JDBC / SOURCE_CSV / SOURCE_REST / SOURCE_CDC / TRANSFORM_SQL / OUTPUT_OBJECT */
        private String type;

        /** 节点配置（JSON 对象，执行时序列化为 JSON 字符串，结构同 PipelineNode.config） */
        private Map<String, Object> config;

        /** 依赖的上游节点 ID 列表 */
        private List<String> dependsOn;
    }

    /**
     * 单个断点规格。
     */
    @Data
    public static class BreakpointSpecDTO {

        /** 断点所在节点 ID */
        private String nodeId;

        /** 触发条件表达式（可选，空 = 无条件命中） */
        private String condition;
    }
}
