package com.chinacreator.gzcm.runtime.core.common.flowmonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * NodeFlowDataPool - 节点流程数据池（占位实现）
 * 
 * 用于管理节点和调度的时间信息
 * 
 * @deprecated 建议使用 Spring 管理的缓存或状态管理服务
 */
@Deprecated
public class NodeFlowDataPool {
    
    private static final ConcurrentMap<String, Long> scheduleTimeMap = new ConcurrentHashMap<>();
    private static final List<String> runningNodes = new ArrayList<>();
    
    /**
     * 获取运行中的节点列表
     * 
     * @return 运行中的节点ID列表
     */
    public static List<String> getRunningNode() {
        return new ArrayList<>(runningNodes);
    }
    
    /**
     * 移除调度时间
     * 
     * @param scheduleId 调度ID
     */
    public static void removeScheduleTime(String scheduleId) {
        scheduleTimeMap.remove(scheduleId);
    }
    
    /**
     * 设置调度时间
     * 
     * @param scheduleId 调度ID
     * @param time 时间戳
     */
    public static void setScheduleTime(String scheduleId, Long time) {
        if (time != null) {
            scheduleTimeMap.put(scheduleId, time);
        } else {
            scheduleTimeMap.remove(scheduleId);
        }
    }
    
    /**
     * 获取调度时间
     * 
     * @param scheduleId 调度ID
     * @return 时间戳，如果不存在则返回null
     */
    public static Long getScheduleTime(String scheduleId) {
        return scheduleTimeMap.get(scheduleId);
    }
}
