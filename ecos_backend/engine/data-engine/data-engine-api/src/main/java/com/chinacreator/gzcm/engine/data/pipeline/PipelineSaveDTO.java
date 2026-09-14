package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Pipeline 定义保存请求体 — 创建/更新共用。
 * <p>遵循后端规范 XxxSaveDTO 命名（Lombok @Data）。
 *
 * @author DataBridge Datanet Team
 */
@Data
public class PipelineSaveDTO {

    /** Pipeline 名称（创建必填，更新可选） */
    private String name;

    /** 描述（选填） */
    private String description;

    /** 状态: DRAFT / ACTIVE / ARCHIVED（选填，默认 DRAFT） */
    private String status;

    /** 节点列表（选填；创建至少 1 个，更新为 null 表示不变更） */
    private List<NodeSpec> nodes;

    /** DAG 边列表（选填，from/to 指向 NodeSpec.nodeId） */
    private List<EdgeSpec> edges;

    /** 定时调度（选填，走 runtime-task） */
    private ScheduleSpec schedule;

    /** 扩展属性（选填，透传 definition JSONB，如 version/tags） */
    private Map<String, Object> extensions;

    /**
     * 节点规格 — DAG 中的执行单元。
     */
    @Data
    public static class NodeSpec {

        /** 前端节点标识（同一定义内唯一，DAG 拓扑依赖键） */
        private String nodeId;

        /** 兼容键：等价 nodeId，nodeId 为空时取 id */
        private String id;

        /** 节点类型: SOURCE_JDBC/SOURCE_CSV/SOURCE_REST/SOURCE_CDC/TRANSFORM_SQL/OUTPUT_OBJECT/TRANSFORM_UDF/JOIN/SINK */
        private String type;

        /** 节点配置（类型相关，见 P2-01 Schema §四） */
        private Map<String, Object> config;

        /** 画布 X 坐标（选填，默认 0） */
        private Integer positionX;

        /** 画布 Y 坐标（选填，默认 0） */
        private Integer positionY;
    }

    /**
     * DAG 边规格 — from 节点输出指向 to 节点输入。
     */
    @Data
    public static class EdgeSpec {

        /** 源节点 nodeId */
        private String from;

        /** 目标节点 nodeId（该节点依赖 from） */
        private String to;

        /** 边标签（选填） */
        private String label;
    }

    /**
     * 定时调度规格。
     */
    @Data
    public static class ScheduleSpec {

        /** cron 表达式（空表示调度取消，不注册） */
        private String cron;
    }
}
