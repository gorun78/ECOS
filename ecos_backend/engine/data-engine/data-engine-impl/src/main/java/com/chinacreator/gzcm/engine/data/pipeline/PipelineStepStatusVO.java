package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Pipeline 节点执行状态视图 VO — 监视面板概览/数据预览共用。
 * <p>
 * 字段约定：
 * <ul>
 *   <li>columnsIn / columnsOut 拆分输入/输出列名（Data Preview Tab 用 schema 对比）</li>
 *   <li>sampleRows 仅 debug 会话下保留（前 100 行）</li>
 * </ul>
 *
 * @author DataBridge Datanet Team
 */
@Data
public class PipelineStepStatusVO {

    /** 节点 ID */
    private String nodeId;

    /** 节点类型（SOURCE_JDBC / TRANSFORM_SQL / ...） */
    private String type;

    /** 节点显示名称（前端画布标签，后端留 null） */
    private String name;

    /** 执行状态: IDLE / QUEUED / RUNNING / SUCCEEDED / FAILED / BROKEN */
    private String status;

    /** 输入行数（Transform/Output 用，Source 固定 0） */
    private long rowsInput;

    /** 输出行数 */
    private long rowsOutput;

    /** 耗时（毫秒，未完成时 0） */
    private long elapsedMs;

    /** 完成时间（ISO-8601，未完成时 null） */
    private String finishedAt;

    /** 错误信息（FAILED 时有值） */
    private String errorMsg;

    /** 命中的断点条件（BROKEN 时有值） */
    private String condition;

    /** 输入列名（调试会话留存） */
    private List<String> columnsIn;

    /** 输出列名（调试会话留存） */
    private List<String> columnsOut;

    /** 前 100 行数据样本（调试会话留存） */
    private List<Map<String, Object>> sampleRows;

    /** 变量快照（BROKEN/RUNNING 时有值，含 rowsProcessed/lastRows/condition 等） */
    private Map<String, Object> snapshot;
}
