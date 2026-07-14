package com.chinacreator.gzcm.runtime.core.agent.tool;

import java.util.List;
import java.util.Map;

/**
 * 工具注册表接口
 * 
 * 管理所有可被 AI Agent 调用的工具。子系统通过 registerTools() 注册自己的工具，
 * AgentRuntime 通过此接口发现和调用工具。
 *
 * 设计原则：
 * - 工具是子系统能力的抽象封装
 * - 每个工具需提供 LLM 可理解的描述和参数 Schema
 * - 支持按子系统隔离和按名称检索
 *
 * @author CDRC Design Team
 */
public interface ToolRegistry {

    /**
     * 注册单个工具
     */
    void registerTool(Tool tool);

    /**
     * 批量注册工具
     */
    void registerTools(List<Tool> tools);

    /**
     * 注销工具
     */
    void unregisterTool(String toolName);

    /**
     * 获取工具
     */
    Tool getTool(String toolName);

    /**
     * 获取所有已注册工具
     */
    List<Tool> getAllTools();

    /**
     * 获取指定子系统的所有工具
     */
    List<Tool> getToolsBySubsystem(String subsystem);

    /**
     * 获取工具定义列表（供 LLM function calling 使用）
     * 返回包含 name, description, parameters 的 Map 列表
     */
    List<Map<String, Object>> getToolDefinitions();

    /**
     * 执行工具调用
     */
    ToolResult executeTool(ToolCall toolCall);

    /**
     * 已注册工具数量
     */
    int getToolCount();
}
