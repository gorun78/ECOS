package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.service;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObjectIndex;

/**
 * 数据对象索引服务接口
 */
public interface IDataObjectIndexService {
    
    /**
     * 根据对象ID获取索引列表
     * @param objectId 对象ID
     * @return 索引列表
     * @throws Exception 查询失败时抛出异常
     */
    List<DataObjectIndex> getIndexListByObjectId(String objectId) throws Exception;
    
    /**
     * 根据索引ID获取索引
     * @param indexId 索引ID
     * @return 索引对象
     * @throws Exception 查询失败时抛出异常
     */
    DataObjectIndex getIndexById(String indexId) throws Exception;
    
    /**
     * 添加索引
     * @param index 索引对象
     * @throws Exception 添加失败时抛出异常
     */
    void addIndex(DataObjectIndex index) throws Exception;
    
    /**
     * 更新索引
     * @param index 索引对象
     * @throws Exception 更新失败时抛出异常
     */
    void updateIndex(DataObjectIndex index) throws Exception;
    
    /**
     * 删除索引
     * @param indexId 索引ID
     * @throws Exception 删除失败时抛出异常
     */
    void deleteIndex(String indexId) throws Exception;
    
    /**
     * 根据对象ID删除所有索引
     * @param objectId 对象ID
     * @throws Exception 删除失败时抛出异常
     */
    void deleteIndexByObjectId(String objectId) throws Exception;
    
    /**
     * 从数据库同步索引信息
     * @param objectId 对象ID
     * @throws Exception 同步失败时抛出异常
     */
    default void syncIndexFromDB(String objectId) throws Exception {
        // 默认实现，子类应该重写
        throw new UnsupportedOperationException("syncIndexFromDB is not implemented");
    }
}
