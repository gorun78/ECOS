package com.chinacreator.gzcm.runtime.core.agent.tool;

import java.util.Map;

/**
 * 工具接口
 * 定义了 Agent 可调用的工具规范。
 * 各子系统通过实现此接口并注册到 ToolRegistry 来暴露自身能力给 AI Agent。
 *
 * 示例：
 * - Bus-Zhi 注册 "data_collect" 工具 → AI 可以触发数据采集
 * - Dc-Cheng 注册 "metadata_search" 工具 → AI 可以搜索元数据
 * - Sys-Man 注册 "audit_query" 工具 → AI 可以查询审计日志
 *
 * @author CDRC Design Team
 */
public interface Tool {

    /**
     * 工具唯一名称，如 "data_collect", "metadata_search"
     */
    String getName();

    /**
     * 工具描述，供 LLM 理解工具的用途和用法
     */
    String getDescription();

    /**
     * 工具参数 Schema（JSON Schema 格式），描述工具接受的参数
     * 返回 null 表示无需参数
     */
    Map<String, Object> getParametersSchema();

    /**
     * 执行工具调用
     *
     * @param arguments 工具参数（key-value 形式）
     * @return 工具执行结果
     * @throws Exception 工具执行异常
     */
    ToolResult execute(Map<String, Object> arguments) throws Exception;

    /**
     * 是否允许在无用户确认的情况下自动调用（安全敏感的工具应返回 false）
     */
    default boolean isAutoApprove() {
        return false;
    }

    /**
     * 工具所属子系统标识，如 "bus-zhi", "dc-cheng"
     */
    default String getSubsystem() {
        return "runtime";
    }
}
