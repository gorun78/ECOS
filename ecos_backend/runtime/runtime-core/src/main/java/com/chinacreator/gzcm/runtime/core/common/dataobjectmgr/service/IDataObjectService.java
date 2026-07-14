package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.service;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObject;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObjectColumn;
import com.chinacreator.gzcm.runtime.core.common.util.PageInfo;

/**
 * 数据对象服务接口
 */
public interface IDataObjectService {
    
    /**
     * 根据ID获取数据对象
     * @param objectId 数据对象ID
     * @return 数据对象
     * @throws Exception 获取失败时抛出异常
     */
    DataObject getDataObjectById(String objectId) throws Exception;
    
    /**
     * 根据对象ID获取数据对象（别名方法，兼容旧代码）
     * @param objectId 数据对象ID
     * @return 数据对象
     * @throws Exception 获取失败时抛出异常
     */
    default DataObject getDataObjectByObjectId(String objectId) throws Exception {
        return getDataObjectById(objectId);
    }
    
    /**
     * 获取数据对象列表
     * @param condition 查询条件
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 分页数据对象列表
     * @throws Exception 查询失败时抛出异常
     */
    PageInfo<DataObject> getDataObjectList(Object condition, Integer offset, Integer pageSize) throws Exception;
    
    /**
     * 根据数据对象ID获取数据对象列列表
     * @param objectId 数据对象ID
     * @return 数据对象列列表
     * @throws Exception 获取失败时抛出异常
     */
    List<DataObjectColumn> getDataObjectColumns(String objectId) throws Exception;
    
    /**
     * 更新数据对象
     * @param dataObject 数据对象
     * @throws Exception 更新失败时抛出异常
     */
    void updateDataObject(DataObject dataObject) throws Exception;
    
    /**
     * 添加数据对象
     * @param dataObject 数据对象
     * @return 添加后的数据对象（包含生成的ID）
     * @throws Exception 添加失败时抛出异常
     */
    default DataObject addDataObject(DataObject dataObject) throws Exception {
        // 默认实现，子类应该重写
        throw new UnsupportedOperationException("addDataObject is not implemented");
    }
    
    /**
     * 检查参数表名是否存在
     * @param dsId 数据源ID
     * @param tableName 表名
     * @return 如果表名存在返回true，否则返回false
     * @throws Exception 检查失败时抛出异常
     */
    default boolean checkParamTableName(String dsId, String tableName) throws Exception {
        // 默认实现：占位方法，子类应该重写
        return false;
    }
}
