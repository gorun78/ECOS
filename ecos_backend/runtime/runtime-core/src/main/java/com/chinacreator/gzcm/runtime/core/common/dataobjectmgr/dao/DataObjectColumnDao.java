package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.dao;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObject;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObjectColumn;

public interface DataObjectColumnDao {
    List<DataObjectColumn> getDataObjectColumnListByObjectId(String objectId) throws Exception;
    void updateDataObjectColumnLength(List<DataObjectColumn> columns) throws Exception;
    DataObjectColumn add(DataObjectColumn srcColumn) throws Exception;
    DataObjectColumn getDataObjectColumnByColumnId(String columnId) throws Exception;
    DataObjectColumn getDataObjectColumnByIdCode(String objectId, String columnCode) throws Exception;
    
    /**
     * 批量更新数据对象列
     * @param columns 列列表
     * @throws Exception
     */
    void updateDataObjectColumns(List<DataObjectColumn> columns) throws Exception;
    
    /**
     * 批量删除数据对象列
     * @param columns 列列表
     * @throws Exception
     */
    void deleteColumns(List<DataObjectColumn> columns) throws Exception;
    
    /**
     * 批量添加数据对象列
     * @param dataObject 数据对象
     * @param columns 列列表
     * @throws Exception
     */
    void addDataObjectColumns(DataObject dataObject, List<DataObjectColumn> columns) throws Exception;
    
    /**
     * 根据主键标志查询数据对象列列表（使用Bean作为参数）
     * @param condition 查询条件（包含object_id和pk_flag）
     * @return 数据对象列列表
     * @throws Exception
     */
    default List<DataObjectColumn> getDataObjectColumnListByPkFlagBean(DataObjectColumn condition) throws Exception {
        // Placeholder implementation
        return new java.util.ArrayList<>();
    }
}
