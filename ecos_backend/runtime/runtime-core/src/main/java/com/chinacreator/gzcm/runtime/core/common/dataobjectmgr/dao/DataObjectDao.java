package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.dao;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObject;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.RestfulRequestParameter;

/**
 * DataObjectDao - 数据对象DAO接口
 * 用于数据对象的数据库操作
 */
public interface DataObjectDao {
    
    /**
     * 根据ID查找数据对象
     */
    DataObject findById(String objectId) throws Exception;
    
    /**
     * 查找所有数据对象
     */
    List<DataObject> findAll() throws Exception;
    
    /**
     * 添加数据对象
     */
    boolean add(DataObject dataObject) throws Exception;
    
    /**
     * 更新数据对象
     */
    boolean update(DataObject dataObject) throws Exception;
    
    /**
     * 删除数据对象
     */
    boolean delete(String objectId) throws Exception;
    
    /**
     * 更新方案的源对象最大增量值
     * @param objectId 对象ID
     * @param scheduleId 方案ID
     * @param maxIncValue 最大增量值
     * @throws Exception
     */
    void updateScheduleSrcObjMaxInc(String objectId, String scheduleId, String maxIncValue) throws Exception;
    
    /**
     * 更新方案的源对象主键值
     * @param objectId 对象ID
     * @param scheduleId 方案ID
     * @param pkValues 主键值
     * @throws Exception
     */
    void updateScheduleSrcObjPkValus(String objectId, String scheduleId, String pkValues) throws Exception;

    // Added methods for ScheduleService compatibility
    public DataObject getDataObjectById(String objectId) throws Exception;
    public DataObject getDataObjectInfo(String objectId) throws Exception;
    public String getDsTypeByObjectId(String objectId) throws Exception;
    public List<RestfulRequestParameter> selectRestfulRequestParameters(Object condition) throws Exception;
    public void updateDataObject(DataObject dataObject) throws Exception;
    
    /**
     * 根据条件查询数据对象列表
     * @param condition 查询条件
     * @return 数据对象列表
     * @throws Exception
     */
    List<DataObject> getDataObjectList(Object condition) throws Exception;
    
    /**
     * 根据数据对象ID获取数据对象列列表
     * @param objectId 数据对象ID
     * @return 数据对象列列表
     * @throws Exception
     */
    List<com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObjectColumn> getDataObjectColumns(String objectId) throws Exception;
    
    /**
     * 检查简单数据源中是否存在同名数据对象
     * @param dataObject 数据对象
     * @return 如果存在返回true，否则返回false
     * @throws Exception
     */
    boolean isExistedObjectInSimpleDataSourceWithSameName(DataObject dataObject) throws Exception;
    
    /**
     * 选择RESTful请求头
     * @param header 请求头对象
     * @return 请求头列表
     * @throws Exception
     */
    List<com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.RestfulRequestHeader> selectRestfulRequestHeaders(com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.RestfulRequestHeader header) throws Exception;
    
    /**
     * 根据数据源ID获取数据源
     * @param dsId 数据源ID
     * @return 数据源对象
     * @throws Exception
     */
    public com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean.Tddxdatasource getTddxdatasourceByDsId(String dsId) throws Exception;
    
    /**
     * 根据ID查找数据对象（别名方法，兼容旧代码）
     * @param objectId 对象ID
     * @return 数据对象
     * @throws Exception
     */
    default DataObject selectDataObjectById(String objectId) throws Exception {
        return findById(objectId);
    }
    
    /**
     * 添加数据对象（别名方法，兼容旧代码）
     * @param dataObject 数据对象
     * @throws Exception
     */
    default void addDataObject(DataObject dataObject) throws Exception {
        add(dataObject);
    }
    
    /**
     * 检查数据对象是否被引用
     * @param objectId 对象ID
     * @return 如果被引用返回true，否则返回false
     * @throws Exception
     */
    default boolean checkDataObjectIsInvoked(String objectId) throws Exception {
        // TODO: 实现检查逻辑
        return false;
    }
    
    /**
     * 根据ID删除数据对象（别名方法，兼容旧代码）
     * @param objectId 对象ID
     * @throws Exception
     */
    default void deleteDataObjectById(String objectId) throws Exception {
        delete(objectId);
    }
}
