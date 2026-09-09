package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

/**
 * Pipeline 调试日志行视图 VO — SSE / 拉取日志的响应载荷。
 *
 * @author DataBridge Datanet Team
 */
@Data
public class PipelineDebugLogLineVO {

    /** 自增序号（frontend 增量回放索引） */
    private long seq;

    /** 节点 ID（全局日志为 null） */
    private String nodeId;

    /** 日志级别: INFO / WARNING / ERROR */
    private String level;

    /** 日志消息（含异常栈） */
    private String message;

    /** 时间戳（毫秒） */
    private long atMs;
}
