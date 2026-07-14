package com.chinacreator.gzcm.runtime.core.agent;

import java.util.Arrays;

/**
 * Sys-Man 子系统 Agent 配置
 * 
 * 为系统管理场景定制的 Agent 配置，包含：
 * - IAM 用户/角色/权限管理
 * - 安全审计与合规检查
 * - 系统配置管理
 * - 策略管理与查询
 * - 日志分析与故障排查
 *
 * @author CDRC Design Team
 */
public class SysManAgentProfile extends AgentProfile {

    /** 子系统标识 */
    public static final String SUBSYSTEM = "sysman";

    /** 默认系统提示词 */
    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是一个数据桥接平台（DataBridge）的系统管理助手。\n" +
            "你的职责包括：\n" +
            "1. 帮助管理员进行用户、角色、权限的查询和管理\n" +
            "2. 分析安全审计日志，识别异常行为\n" +
            "3. 辅助系统配置的查询和修改\n" +
            "4. 回答关于系统状态、数据策略和合规性的问题\n" +
            "5. 提供故障排查建议和最佳实践指导\n" +
            "\n" +
            "原则：\n" +
            "- 敏感操作需要用户明确确认\n" +
            "- 提供准确、简洁的回答\n" +
            "- 优先使用工具查询实时数据，而非猜测\n" +
            "- 对于配置修改类操作，先说明影响再执行";

    /** Sys-Man 允许的工具列表 */
    public static final java.util.List<String> SYS_MAN_TOOLS = Arrays.asList(
            "user_query",           // 用户查询
            "role_query",           // 角色查询
            "permission_query",     // 权限查询
            "audit_log_query",      // 审计日志查询
            "config_query",         // 配置查询
            "policy_query",         // 策略查询
            "system_status",        // 系统状态
            "log_analysis"          // 日志分析
    );

    public SysManAgentProfile() {
        super("SysMan Agent", DEFAULT_SYSTEM_PROMPT, SUBSYSTEM);
        this.setAllowedTools(SYS_MAN_TOOLS);
        this.setDescription("系统管理 AI 助手 - 用于 IAM、审计、配置等系统管理任务");
        // 系统管理操作敏感，需要更谨慎
        this.setMaxIterations(8);
        this.setTemperature(0.3);   // 更确定性的输出
        this.setAutoApprove(false); // 不自动批准工具调用
        this.setSessionTimeoutSeconds(900); // 15 分钟超时
    }

    /**
     * 创建带自定义系统提示词的 SysMan Agent 配置
     */
    public SysManAgentProfile(String customSystemPrompt) {
        this();
        if (customSystemPrompt != null && !customSystemPrompt.trim().isEmpty()) {
            this.setSystemPrompt(customSystemPrompt);
        }
    }
}
