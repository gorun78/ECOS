package com.chinacreator.gzcm.engine.ai.agent.vo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 流水线节点执行轨迹 — 记录单个 pipeline 节点在一次执行中的完整轨迹。
 *
 * <h3>字段语义：</h3>
 * <ul>
 *   <li>{@link #nodeId} — 节点唯一标识（前端画布 node.id）</li>
 *   <li>{@link #nodeName} — 节点显示名称</li>
 *   <li>{@link #status} — 当前状态机枚举：idle / pending / running / success / failed / skipped</li>
 *   <li>{@link #startedAt} — 节点开始执行时间（ISO 字符串格式）</li>
 *   <li>{@link #completedAt} — 节点完成时间（ISO 字符串格式）</li>
 *   <li>{@link #latencyMs} — 节点执行耗时（毫秒）</li>
 *   <li>{@link #inputJson} — 节点输入参数（JSON 字符串，mock 时可为 null）</li>
 *   <li>{@link #outputJson} — 节点输出结果（JSON 字符串，mock 时可为 null）</li>
 *   <li>{@link #errorMessage} — 失败时的错误信息（success/skipped 时 null）</li>
 * </ul>
 *
 * <p>使用 Lombok {@code @Getter @Setter @NoArgsConstructor}，禁止 {@code @Data}
 * （遵循后端开发规范：防 JPA 懒加载/循环引用问题）。</p>
 *
 * <p>序列化：使用 {@link #toMap()} 代替直接 JSON 序列化，保证与
 * {@link com.chinacreator.gzcm.engine.ai.service.AgentStudioService.PipelineExecution#toMap()}
 * 输出契约一致（字段名 camelCase，null 值不输出）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class PipelineNodeTraceVO {

    /** 节点唯一标识（前端画布 node.id），如 "llm-1"、"trigger-0" */
    private String nodeId;

    /** 节点显示名称（前端画布 node.data.label） */
    private String nodeName;

    /**
     * 节点执行状态：
     * <ul>
     *   <li>idle — 初始状态（尚未被调度）</li>
     *   <li>pending — 已入队等待执行</li>
     *   <li>running — 正在执行</li>
     *   <li>success — 执行成功</li>
     *   <li>failed — 执行失败</li>
     *   <li>skipped — 被前序失败节点跳过</li>
     * </ul>
     */
    private String status;

    /** 节点开始执行时间（ISO-8601 字符串，如 "2026-09-12T10:30:00"） */
    private LocalDateTime startedAt;

    /** 节点完成时间（成功/失败时填入，执行中为 null） */
    private LocalDateTime completedAt;

    /** 节点执行耗时（毫秒），成功/失败时填入 */
    private Long latencyMs;

    /** 节点输入参数（JSON 字符串），mock 模式下为 null 或占位 JSON */
    private String inputJson;

    /** 节点输出结果（JSON 字符串），mock 模式下为 null 或占位 JSON */
    private String outputJson;

    /** 失败时的错误信息（success/skipped/pending/idle/running 时 null） */
    private String errorMessage;

    /**
     * 序列化为 Map（camelCase 键，null 值不输出）。
     * <p>与 {@link com.chinacreator.gzcm.engine.ai.service.AgentStudioService.PipelineExecution#toMap()}
     * 输出契约保持一致。</p>
     *
     * @return 序列化后的 Map，可直接放入 JSON 响应
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("nodeId", nodeId);
        m.put("nodeName", nodeName);
        m.put("status", status);
        if (startedAt != null) {
            m.put("startedAt", startedAt.toString());
        }
        if (completedAt != null) {
            m.put("completedAt", completedAt.toString());
        }
        if (latencyMs != null) {
            m.put("latencyMs", latencyMs);
        }
        if (inputJson != null) {
            m.put("inputJson", inputJson);
        }
        if (outputJson != null) {
            m.put("outputJson", outputJson);
        }
        if (errorMessage != null) {
            m.put("errorMessage", errorMessage);
        }
        return m;
    }
}
