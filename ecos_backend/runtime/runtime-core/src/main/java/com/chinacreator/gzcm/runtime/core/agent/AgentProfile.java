package com.chinacreator.gzcm.runtime.core.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 配置文件基类
 * 
 * 定义 Agent 在执行特定子系统任务时的行为配置。
 * 各子系统通过继承此类创建自己的 AgentProfile，
 * 配置系统提示词、允许的工具集、运行参数等。
 *
 * @author CDRC Design Team
 */
public class AgentProfile {

    /** 配置名称 */
    private String name;

    /** 配置描述 */
    private String description;

    /** 系统提示词 — 定义 Agent 的角色和职责 */
    private String systemPrompt;

    /** 允许使用的工具名称列表（空列表表示可以使用所有工具） */
    private List<String> allowedTools = new ArrayList<>();

    /** 最大迭代次数（ReAct 循环次数上限） */
    private int maxIterations = 10;

    /** LLM 温度参数（0-2，越低越确定，越高越有创造性） */
    private double temperature = 0.7;

    /** 最大 Token 数 */
    private int maxTokens = 4096;

    /** 会话超时时间（秒），0 表示不超时 */
    private int sessionTimeoutSeconds = 600;

    /** 是否启用工具调用 */
    private boolean toolsEnabled = true;

    /** 是否自动批准工具调用（安全敏感场景设为 false） */
    private boolean autoApprove = false;

    /** 所属子系统标识 */
    private String subsystem;

    public AgentProfile() {
    }

    public AgentProfile(String name, String systemPrompt, String subsystem) {
        this.name = name;
        this.systemPrompt = systemPrompt;
        this.subsystem = subsystem;
    }

    // ── Getters & Setters ──────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public List<String> getAllowedTools() { return allowedTools; }
    public void setAllowedTools(List<String> allowedTools) { this.allowedTools = allowedTools; }

    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public int getSessionTimeoutSeconds() { return sessionTimeoutSeconds; }
    public void setSessionTimeoutSeconds(int sessionTimeoutSeconds) { this.sessionTimeoutSeconds = sessionTimeoutSeconds; }

    public boolean isToolsEnabled() { return toolsEnabled; }
    public void setToolsEnabled(boolean toolsEnabled) { this.toolsEnabled = toolsEnabled; }

    public boolean isAutoApprove() { return autoApprove; }
    public void setAutoApprove(boolean autoApprove) { this.autoApprove = autoApprove; }

    public String getSubsystem() { return subsystem; }
    public void setSubsystem(String subsystem) { this.subsystem = subsystem; }
}
