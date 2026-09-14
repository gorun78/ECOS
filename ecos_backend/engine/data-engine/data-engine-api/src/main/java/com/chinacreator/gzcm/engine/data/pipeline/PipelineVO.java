package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Pipeline 定义视图对象 — 列表/详情/创建/更新响应载荷。
 * <p>遵循后端规范 XxxVO 命名（Lombok @Data）。
 * 节点节点 config 中的敏感字段（password/token 等）在构造本对象前已脱敏。
 *
 * @author DataBridge Datanet Team
 */
@Data
public class PipelineVO {

    /** 定义唯一标识 */
    private String id;

    /** Pipeline 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 状态: DRAFT / ACTIVE / ARCHIVED */
    private String status;

    /** 节点列表（详情/创建/更新返回；列表视实现可为摘要，前端画布渲染需调详情） */
    private List<NodeVO> nodes;

    /** 扩展属性（含 scheduleId 等，来自 definition JSONB） */
    private Map<String, Object> extensions;

    /** 创建时间戳（毫秒） */
    private Long createdAt;

    /** 更新时间戳（毫秒） */
    private Long updatedAt;

    /**
     * 节点视图 — 序列化后供前端画布渲染。
     */
    @Data
    public static class NodeVO {

        /** 前端节点标识 */
        private String nodeId;

        /** 节点类型 */
        private String type;

        /** 节点配置（已脱敏敏感字段） */
        private Map<String, Object> config;

        /** 依赖的节点 ID 列表（拓扑） */
        private List<String> dependsOn;

        /** 画布 X 坐标 */
        private Integer positionX;

        /** 画布 Y 坐标 */
        private Integer positionY;
    }
}
