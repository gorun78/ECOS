package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.Map;

/**
 * 推理请求 — 规则推理 / 因果分析统一入口。
 */
public class ReasonRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 推理模式：rule / causal */
    private String mode;
    /** 事实数据（键值对） */
    private Map<String, Object> facts;
    /** 上下文约束（数据源、时间窗口等） */
    private Map<String, Object> context;
    /** 推理选项（最大深度、阈值等） */
    private Map<String, Object> options;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Map<String, Object> getFacts() { return facts; }
    public void setFacts(Map<String, Object> facts) { this.facts = facts; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }
}
