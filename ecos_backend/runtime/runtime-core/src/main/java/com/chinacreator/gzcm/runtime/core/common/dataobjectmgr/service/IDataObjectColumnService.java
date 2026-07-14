package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.service;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObjectColumn;

public interface IDataObjectColumnService {
    List<DataObjectColumn> getAllowFields(String scheduleId, String param2, String objectId) throws Exception;
    
    /**
     * 根据对象ID获取数据对象列列表
     * @param objectId 数据对象ID
     * @return 数据对象列列表
     * @throws Exception 获取失败时抛出异常
     */
    default List<DataObjectColumn> getDataObjectColumnListByObjectId(String objectId) throws Exception {
        // 默认实现，子类应该重写
        return new java.util.ArrayList<>();
    }
    
    /**
     * 根据数据源ID、数据源名称、表名和数据库Schema获取数据对象列列表
     * @param dsId 数据源ID
     * @param dsName 数据源名称
     * @param tableName 表名
     * @param dbSchema 数据库Schema
     * @return 数据对象列列表
     * @throws Exception 获取失败时抛出异常
     */
    default List<DataObjectColumn> getDataObjectColumnList(String dsId, String dsName, String tableName, String dbSchema) throws Exception {
        // 默认实现，子类应该重写
        return new java.util.ArrayList<>();
    }
    
    /**
     * 根据目标ID获取对象列列表
     * @param destId 目标ID
     * @return 数据对象列列表
     * @throws Exception 获取失败时抛出异常
     */
    default List<DataObjectColumn> getObjColumnsByDestId(String destId) throws Exception {
        // 默认实现，子类应该重写
        return new java.util.ArrayList<>();
    }
    
    /**
     * 批量添加数据对象列（部分）
     * @param dataObject 数据对象
     * @param columns 列列表
     * @throws Exception 添加失败时抛出异常
     */
    default void addDataObjectColumnsSome(com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObject dataObject, List<DataObjectColumn> columns) throws Exception {
        // 默认实现，子类应该重写
        throw new UnsupportedOperationException("addDataObjectColumnsSome is not implemented");
    }
    
    /**
     * 根据编码更新数据对象列
     * @param columns 列列表
     * @throws Exception 更新失败时抛出异常
     */
    default void updateDataObjectColumnsByCode(List<DataObjectColumn> columns) throws Exception {
        // 默认实现，子类应该重写
        throw new UnsupportedOperationException("updateDataObjectColumnsByCode is not implemented");
    }
    
    /**
     * 批量删除数据对象列（部分）
     * @param columns 列列表
     * @throws Exception 删除失败时抛出异常
     */
    default void deleteDataObjectColumnsSome(List<DataObjectColumn> columns) throws Exception {
        // 默认实现，子类应该重写
        throw new UnsupportedOperationException("deleteDataObjectColumnsSome is not implemented");
    }
    
    /**
     * 添加数据对象列
     * @param dataObject 数据对象
     * @param columns 列列表
     * @throws Exception 添加失败时抛出异常
     */
    default void addDataObjectColumns(com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObject dataObject, List<DataObjectColumn> columns) throws Exception {
        // 默认实现，子类应该重写
        throw new UnsupportedOperationException("addDataObjectColumns is not implemented");
    }
    
    /**
     * 更新数据对象列
     * @param columns 列列表
     * @throws Exception 更新失败时抛出异常
     */
    default void updateDataObjectColumns(List<DataObjectColumn> columns) throws Exception {
        // 默认实现，子类应该重写
        throw new UnsupportedOperationException("updateDataObjectColumns is not implemented");
    }
}
