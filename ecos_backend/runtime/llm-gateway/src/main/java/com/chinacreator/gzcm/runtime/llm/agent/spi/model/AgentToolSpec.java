package com.chinacreator.gzcm.runtime.llm.agent.spi.model;

import java.util.HashMap;
import java.util.Map;

/**
 * SPI 契约：工具最小投影（接缝义务 1 · tool-router 能力，接缝义务 4 的前身）。
 *
 * <p>本类只承载既有路由所需的路径字段。接缝义务 4 的 Tool Contract 17 要素 +
 * {@code draft→…→retired} 生命周期落在 {@code ecos_ai.ecos_wagent_tool_contract}
 * （分册 10 属主），<b>不在本 SPI 内另立第二套契约模型</b>；届时以 contractId 关联，
 * 本类保持 additive 不变。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public class AgentToolSpec {

    private String code;
    private String name;
    private String endpoint;
    private AgentToolType type;
    private String status;
    private Map<String, Object> parameters = new HashMap<>();

    public AgentToolSpec() {
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public AgentToolType getType() { return type; }
    public void setType(AgentToolType type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters == null ? new HashMap<>() : parameters;
    }
}
