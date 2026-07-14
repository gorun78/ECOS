package com.chinacreator.gzcm.runtime.core.common.rpccaller.trans;

import java.util.Map;
import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObject;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObjectColumn;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.ScheduleBean;
import com.chinacreator.gzcm.runtime.core.core.rpcpip.rpccaller.bean.RpcCallResult;

/**
 * 传输管理调用器接口（Runtime版本）
 * 这是一个占位接口，实际实现应该委托给 Bus-Zhi 模块的 ITransManageCaller
 */
public interface ITransManageCaller {
    
    /**
     * 执行数据传输
     * @param scheduleBean 调度Bean
     * @param excuteBatchId 执行批次ID
     * @param dataObject 数据对象
     * @param destNodeIds 目标节点ID列表
     * @param shareRefIds 共享引用ID映射
     * @param param 参数
     * @return 执行结果
     * @throws Exception 执行异常
     */
    Map<String, Object> executeDataTransAtNode(ScheduleBean scheduleBean,
            String excuteBatchId, DataObject dataObject,
            List<String> destNodeIds, Map<String, List<String>> shareRefIds,
            Map<String, Object> param) throws Exception;
    
    /**
     * 执行增量数据传输
     * @param scheduleBean 调度Bean
     * @param excuteBatchId 执行批次ID
     * @param dataObject 数据对象
     * @param destNodeIds 目标节点ID列表
     * @param shareRefIds 共享引用ID映射
     * @param incMaxVal 增量最大值
     * @param incMaxPkVal 增量主键最大值
     * @param param 参数
     * @return 执行结果
     * @throws Exception 执行异常
     */
    Map<String, Object> executeIncDataTransAtNode(ScheduleBean scheduleBean,
            String excuteBatchId, DataObject dataObject,
            List<String> destNodeIds, Map<String, List<String>> shareRefIds,
            String incMaxVal, String incMaxPkVal, Map<String, Object> param) throws Exception;
    
    /**
     * 创建表
     * @param dataObject 数据对象
     * @param columns 列列表
     * @param createIndex 是否创建索引
     * @param isUnique 是否唯一
     * @return 表名
     * @throws Exception 创建异常
     */
    String createTable(DataObject dataObject, List<DataObjectColumn> columns, 
            boolean createIndex, boolean isUnique) throws Exception;
    
    /**
     * 停止输出传输
     * @param sourceNodeId 源节点ID
     * @param scheduleBean 调度Bean
     * @return RPC调用结果
     * @throws Exception 停止异常
     */
    RpcCallResult stopOutputTrans(String sourceNodeId, ScheduleBean scheduleBean) throws Exception;
    
    /**
     * 停止输入传输
     * @param destNodeId 目标节点ID
     * @param scheduleBean 调度Bean
     * @return RPC调用结果
     * @throws Exception 停止异常
     */
    RpcCallResult stopInputTrans(String destNodeId, ScheduleBean scheduleBean) throws Exception;
}
