package com.chinacreator.gzcm.engine.cognitive2.service;

import java.util.Map;

/**
 * What-if 场景推演请求体。
 * 输入场景名称 + 变量集，LLM 预测 → 对比基线 → 生成 Δ 值。
 */
public class SimRequest {

    /** 场景名称，如"原材料涨价10%场景" */
    private String name;

    /** 变量变更集，如 {"原材料价格": "+10%", "售价": "不变"} */
    private Map<String, String> variables;

    /** 业务域，用于知识库基线检索 */
    private String domain;

    public SimRequest() {}

    public SimRequest(String name, Map<String, String> variables, String domain) {
        this.name = name;
        this.variables = variables;
        this.domain = domain;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) { this.variables = variables; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
}
