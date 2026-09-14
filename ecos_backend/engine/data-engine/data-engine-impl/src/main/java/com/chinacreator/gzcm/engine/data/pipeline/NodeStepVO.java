package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Pipeline 节点执行步骤视图 VO — 调试会话步骤记录/前端命中概览共用。
 * <p>
 * 与 {@link PipelineStepStatusVO} 的区别：
 * <ul>
 *   <li>NodeStepVO：一次会话内的串行步骤记录（可含 QUEUED 占位），顺序 = 拓扑序</li>
 *   <li>PipelineStepStatusVO：实时状态（用于 SUCCEEDED/FAILED 等终端判定）</li>
 * </ul>
 *
 * @author DataBridge Datanet Team
 */
@Data
public class NodeStepVO {

    /** 节点唯一标识 */
    private String nodeId;

    /** 节点类型 (SOURCE_JDBC / TRANSFORM_SQL / ... ) */
    private String type;

    /** 执行状态: QUEUED / AWAITING / RUNNING / SUCCEEDED / FAILED */
    private String status;

    /** 处理行数（本节点） */
    private long rowsProcessed;

    /** 耗时（毫秒，未完成时 0） */
    private long elapsedMs;

    /** 完成时间（ISO-8601，未完成时 null） */
    private String finishedAt;

    /** 输入列名（调试会话留存，供数据预览） */
    private List<String> columnsIn;

    /** 输出列名（调试会话留存，供数据预览） */
    private List<String> columnsOut;

    /** 前 100 行数据样本（调试会话留存） */
    private List<Map<String, Object>> sampleRows;

    /** 变量快照（rowsProcessed/completedNodes/breakpointCondition...） */
    private Map<String, Object> snapshot;

    /** 错误信息（FAILED 时有值） */
    private String errorMsg;
}
