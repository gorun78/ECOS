package com.chinacreator.gzcm.runtime.core.agent.impl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.agent.tool.Tool;
import com.chinacreator.gzcm.runtime.core.agent.tool.ToolCall;
import com.chinacreator.gzcm.runtime.core.agent.tool.ToolRegistry;
import com.chinacreator.gzcm.runtime.core.agent.tool.ToolResult;

/**
 * 工具注册表实现（内存版）
 *
 * @author CDRC Design Team
 */
public class ToolRegistryImpl implements ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    @Override
    public void registerTool(Tool tool) {
        if (tool == null || tool.getName() == null) {
            throw new IllegalArgumentException("Tool and tool name must not be null");
        }
        tools.put(tool.getName(), tool);
    }

    @Override
    public void registerTools(List<Tool> tools) {
        if (tools != null) {
            tools.forEach(this::registerTool);
        }
    }

    @Override
    public void unregisterTool(String toolName) {
        tools.remove(toolName);
    }

    @Override
    public Tool getTool(String toolName) {
        return tools.get(toolName);
    }

    @Override
    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    @Override
    public List<Tool> getToolsBySubsystem(String subsystem) {
        return tools.values().stream()
                .filter(t -> subsystem.equals(t.getSubsystem()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getToolDefinitions() {
        return tools.values().stream().map(tool -> {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            Map<String, Object> params = tool.getParametersSchema();
            if (params != null) {
                function.put("parameters", params);
            }
            def.put("function", function);
            return def;
        }).collect(Collectors.toList());
    }

    @Override
    public ToolResult executeTool(ToolCall toolCall) {
        if (toolCall == null) {
            return ToolResult.error(null, "unknown", "ToolCall is null");
        }

        Tool tool = tools.get(toolCall.getToolName());
        if (tool == null) {
            return ToolResult.error(toolCall.getId(), toolCall.getToolName(),
                    "工具未注册: " + toolCall.getToolName());
        }

        long start = System.currentTimeMillis();
        try {
            ToolResult result = tool.execute(toolCall.getArguments());
            result.setToolCallId(toolCall.getId());
            result.setDurationMs(System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            return ToolResult.error(toolCall.getId(), toolCall.getToolName(),
                    "工具执行异常: " + e.getMessage());
        }
    }

    @Override
    public int getToolCount() {
        return tools.size();
    }
}
