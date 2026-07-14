package com.chinacreator.gzcm.runtime.core.core;

/**
 * NodePropties - 节点属性类（单例模式）
 */
public class NodePropties {
    private static NodePropties instance;
    
    private String nodeID;
    private int scheduleTaskStartThreadPoolSize;
    private String enableIPAccessControl;
    private String resultFormat;
    private String returnUnFilter;
    
    private NodePropties() {
        // 初始化默认值
        this.nodeID = "000";
        this.scheduleTaskStartThreadPoolSize = 10;
        this.enableIPAccessControl = "0";
        this.resultFormat = "XML";
        this.returnUnFilter = "0";
    }
    
    /**
     * 获取单例实例
     * @return NodePropties实例
     */
    public static NodePropties getInstance() {
        if (instance == null) {
            synchronized (NodePropties.class) {
                if (instance == null) {
                    instance = new NodePropties();
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取节点ID
     * @return 节点ID
     */
    public String getNodeID() {
        return nodeID;
    }
    
    /**
     * 设置节点ID
     * @param nodeID 节点ID
     */
    public void setNodeID(String nodeID) {
        this.nodeID = nodeID;
    }
    
    /**
     * 获取调度任务启动线程池大小
     * @return 线程池大小
     */
    public int getScheduleTaskStartThreadPoolSize() {
        return scheduleTaskStartThreadPoolSize;
    }
    
    /**
     * 设置调度任务启动线程池大小
     * @param scheduleTaskStartThreadPoolSize 线程池大小
     */
    public void setScheduleTaskStartThreadPoolSize(int scheduleTaskStartThreadPoolSize) {
        this.scheduleTaskStartThreadPoolSize = scheduleTaskStartThreadPoolSize;
    }
    
    /**
     * 获取是否启用IP访问控制
     * @return "1"表示启用，"0"表示禁用
     */
    public String getEnableIPAccessControl() {
        return enableIPAccessControl;
    }
    
    /**
     * 设置是否启用IP访问控制
     * @param enableIPAccessControl "1"表示启用，"0"表示禁用
     */
    public void setEnableIPAccessControl(String enableIPAccessControl) {
        this.enableIPAccessControl = enableIPAccessControl;
    }
    
    /**
     * 获取结果格式
     * @return 结果格式（如"XML"、"JSON"等）
     */
    public String getResultFormat() {
        return resultFormat;
    }
    
    /**
     * 设置结果格式
     * @param resultFormat 结果格式
     */
    public void setResultFormat(String resultFormat) {
        this.resultFormat = resultFormat;
    }
    
    /**
     * 获取是否返回未过滤数据
     * @return "1"表示返回未过滤数据，"0"表示返回过滤后数据
     */
    public String getReturnUnFilter() {
        return returnUnFilter;
    }
    
    /**
     * 设置是否返回未过滤数据
     * @param returnUnFilter "1"表示返回未过滤数据，"0"表示返回过滤后数据
     */
    public void setReturnUnFilter(String returnUnFilter) {
        this.returnUnFilter = returnUnFilter;
    }
    
    /**
     * 获取集群节点ID（别名方法，兼容旧代码）
     * @return 节点ID
     */
    public String getClusterNodeId() {
        return nodeID;
    }
    
    /**
     * 获取中心节点ID（别名方法，兼容旧代码）
     * @return 节点ID
     */
    public String getCenterNodeId() {
        return nodeID;
    }
    
    /**
     * 设置集群节点ID（别名方法，兼容旧代码）
     * @param clusterNodeId 节点ID
     */
    public void setClusterNodeId(String clusterNodeId) {
        this.nodeID = clusterNodeId;
    }
    
    // Additional field for pool size
    private int poolSize = 10;
    
    /**
     * 获取连接池大小
     * @return 连接池大小
     */
    public int getPoolSize() {
        return poolSize;
    }
    
    /**
     * 设置连接池大小
     * @param poolSize 连接池大小
     */
    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }
    
    // Additional fields for schedule monitoring
    private long waitDataPoolCollectTime = 30000; // 默认30秒
    private long scanInterval = 60000; // 默认60秒
    
    public long getWaitDataPoolCollectTime() {
        return waitDataPoolCollectTime;
    }
    
    public void setWaitDataPoolCollectTime(long waitDataPoolCollectTime) {
        this.waitDataPoolCollectTime = waitDataPoolCollectTime;
    }
    
    public long getScanInterval() {
        return scanInterval;
    }
    
    public void setScanInterval(long scanInterval) {
        this.scanInterval = scanInterval;
    }
}

