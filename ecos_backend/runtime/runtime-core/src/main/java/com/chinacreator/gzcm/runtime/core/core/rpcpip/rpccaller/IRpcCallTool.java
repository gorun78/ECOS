package com.chinacreator.gzcm.runtime.core.core.rpcpip.rpccaller;

import java.util.Map;

/**
 * IRpcCallTool - RPC调用工具接口
 * 用于RPC调用的工具方法
 */
public interface IRpcCallTool {
    
    /**
     * 调用RPC服务
     */
    Object call(String serviceName, String methodName, Object... params) throws Exception;
    
    /**
     * 异步调用RPC服务
     */
    void callAsync(String serviceName, String methodName, Object... params) throws Exception;
    
    /**
     * 调用RPC控制台服务并返回结果
     * @param queueName 队列名称
     * @param receiverClass 接收器类名
     * @param methodName 方法名
     * @param params 参数Map
     * @param resultClass 返回结果类型
     * @return 调用结果
     * @throws Exception 调用异常
     */
    <T> T rpcCallConsoleWithResult(String queueName, String receiverClass, String methodName, 
                                   Map<String, Object> params, Class<T> resultClass) throws Exception;
    
    /**
     * 调用RPC控制台服务（无返回值）
     * @param queueName 队列名称
     * @param receiverClass 接收器类名
     * @param methodName 方法名
     * @param params 参数Map
     * @throws Exception 调用异常
     */
    void rpcCallConsole(String queueName, String receiverClass, String methodName, 
                       Map<String, Object> params) throws Exception;
    
    /**
     * 调用RPC服务并返回结果（兼容旧版本API）
     * @param nodeId 节点ID
     * @param receiverClass 接收器类名
     * @param methodName 方法名
     * @param params 参数Map
     * @param resultClass 返回结果类型
     * @return 调用结果
     * @throws Exception 调用异常
     */
    <T> T rpcCall(String nodeId, String receiverClass, String methodName, 
                  Map<String, Object> params, Class<T> resultClass) throws Exception;
}

