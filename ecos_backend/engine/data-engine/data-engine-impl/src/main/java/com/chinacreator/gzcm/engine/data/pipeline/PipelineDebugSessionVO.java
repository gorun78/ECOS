package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Pipeline 断点调试 — 会话状态视图 VO。
 * <p>
 * GET /api/v1/pipeline/debug/sessions/{sessionId} 的响应载荷，
 * 描述一次调试会话的当前进度、执行步骤、变量快照与命中记录。
 *
 * @author DataBridge Datanet Team
 */
@Data
public class PipelineDebugSessionVO {

    /** 会话 ID */
    private String sessionId;

    /** 会话状态（小写）: created / queued / running / awaiting / broken / paused / completed / failed / stopped */
    private String state;

    /** 关联的 Pipeline 定义 ID（ad-hoc 定义时为空） */
    private String definitionId;

    /** 关联的 executionId（执行落库 ID，用于 SSE 日志端点） */
    private String executionId;

    /** Pipeline 显示名称 */
    private String pipelineName;

    /** 创建时间（ISO-8601） */
    private String createdAt;

    /** 会话开始时间（ISO-8601，RUNNING 后才有值） */
    private String startedAt;

    /** 会话结束时间（ISO-8601，终态才有值） */
    private String finishedAt;

    /** 已完成节点数 */
    private int completedNodes;

    /** 节点总数 */
    private int totalNodes;

    /** 当前正在执行/刚命中的节点 ID */
    private String currentNodeId;

    /** 当前步骤状态（succ/failed/awaiting/...，前端映射是否使用） */
    private String currentStepStatus;

    /** 累计处理行数 */
    private long rowsProcessed;

    /** 变量快照（rowsProcessed/completedNodes/breakpointCondition...，断点命中时刷新） */
    private Map<String, Object> variableSnapshot;

    /** 断点规格列表（nodeId + condition + enabled） */
    private List<BreakpointVO> breakpoints;

    /** 节点执行步骤记录（含 QUEUED 占位，按拓扑序） */
    private List<NodeStepVO> steps;

    /** 断点命中记录（最近一次在前） */
    private List<HitRecordVO> hitRecords;

    /** 失败信息（state=FAILED 时有值） */
    private String error;

    /**
     * 断点规格 VO。
     */
    @Data
    public static class BreakpointVO {

        /** 断点所在节点 ID */
        private String nodeId;

        /** 触发条件表达式（无条件命中时为空） */
        private String condition;

        /** 是否启用 */
        private boolean enabled;
    }

    /**
     * 断点命中记录。
     */
    @Data
    public static class HitRecordVO {

        /** 命中的节点 ID */
        private String nodeId;

        /** 命中的断点条件表达式（无条件命中时为空） */
        private String condition;

        /** 命中时间（ISO-8601） */
        private String at;

        /** 命中时变量快照 */
        private Map<String, Object> snapshot;
    }
}
