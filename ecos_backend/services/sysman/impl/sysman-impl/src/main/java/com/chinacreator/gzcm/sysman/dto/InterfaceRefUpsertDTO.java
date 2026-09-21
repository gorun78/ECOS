package com.chinacreator.gzcm.sysman.dto;

/**
 * 接口引用上/改 DTO — name/interfaceType/endpoint/method/timeoutMs。
 * <p>替代 InterfaceRefController.create/update 原 Map&lt;String,Object&gt; 入参（P0-3 P1-2 集中修）。
 * 不使用 Lombok（sysman-impl 无 Lombok 依赖），手写 getter/setter（与 workspace-impl DTO 风格一致）。
 */
public class InterfaceRefUpsertDTO {

    /** 接口引用名称（create 必填） */
    private String name;

    /** 接口类型（HTTP/KAFKA/REST/MQ/AMQP，create 必填） */
    private String interfaceType;

    /** 端点地址（create 必填） */
    private String endpoint;

    /** HTTP 方法（可选） */
    private String method;

    /** 超时毫秒数（可选，默认 3000） */
    private Integer timeoutMs;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInterfaceType() { return interfaceType; }
    public void setInterfaceType(String interfaceType) { this.interfaceType = interfaceType; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }
}
