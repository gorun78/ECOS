package com.chinacreator.gzcm.workspace.scenario;

/**
 * 场景沙盘布局保存 DTO — React Flow 画布序列化入参（PMO-60 v2.0 P1）。
 */
public class SandboxSaveDTO {

    /** 节点列表（React Flow nodes 数组） */
    private Object nodes;

    /** 边列表（React Flow edges 数组） */
    private Object edges;

    /** 视口状态（{x, y, zoom}） */
    private Object viewport;

    /** 乐观锁：客户端当前持有的版本号（0 = 首次保存） */
    private Integer expectedVersion;

    public Object getNodes() { return nodes; }
    public void setNodes(Object nodes) { this.nodes = nodes; }
    public Object getEdges() { return edges; }
    public void setEdges(Object edges) { this.edges = edges; }
    public Object getViewport() { return viewport; }
    public void setViewport(Object viewport) { this.viewport = viewport; }
    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
}
