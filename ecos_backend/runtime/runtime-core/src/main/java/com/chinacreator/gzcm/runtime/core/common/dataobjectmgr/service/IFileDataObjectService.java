package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.service;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.FileDataObject;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.RestfulRequestParameter;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.RestfulRequestHeader;

/**
 * 文件数据对象服务接口
 * 用于处理文件类型的数据对象
 */
public interface IFileDataObjectService {
    
    /**
     * 根据数据对象ID获取文件数据对象
     * 
     * @param dbName 数据库名称
     * @param dataObjectId 数据对象ID
     * @return 文件数据对象
     * @throws Exception 获取失败时抛出异常
     */
    FileDataObject getFileDataObjectByObjectId(String dbName, String dataObjectId) throws Exception;
    
    /**
     * 根据条件查询RESTful请求参数
     * 
     * @param condition 查询条件
     * @return RESTful请求参数列表
     * @throws Exception 查询失败时抛出异常
     */
    List<RestfulRequestParameter> selectRestfulRequestParameters(RestfulRequestParameter condition) throws Exception;
    
    /**
     * 添加RESTful请求参数
     * 
     * @param objectId 数据对象ID
     * @param parameters 请求参数列表
     * @throws Exception 添加失败时抛出异常
     */
    void addRestfulRequestParameters(String objectId, List<RestfulRequestParameter> parameters) throws Exception;
    
    /**
     * 根据条件查询RESTful请求头
     * 
     * @param condition 查询条件
     * @return RESTful请求头列表
     * @throws Exception 查询失败时抛出异常
     */
    List<RestfulRequestHeader> selectRestfulRequestHeaders(RestfulRequestHeader condition) throws Exception;
    
    /**
     * 添加RESTful请求头
     * 
     * @param objectId 数据对象ID
     * @param headers 请求头列表
     * @throws Exception 添加失败时抛出异常
     */
    void addRestfulRequestHeaders(String objectId, List<RestfulRequestHeader> headers) throws Exception;
    
    /**
     * 根据数据对象ID获取文件数据对象（单参数版本，兼容旧代码）
     * 
     * @param dataObjectId 数据对象ID
     * @return 文件数据对象
     * @throws Exception 获取失败时抛出异常
     */
    default FileDataObject getFileDataObjectByObjectId(String dataObjectId) throws Exception {
        return getFileDataObjectByObjectId("default", dataObjectId);
    }
    
    /**
     * 设置数据对象状态
     * 
     * @param objectIds 数据对象ID列表
     * @param status 状态
     * @throws Exception 设置失败时抛出异常
     */
    void setDataObjectStat(List<String> objectIds, String status) throws Exception;
    
    /**
     * 获取文件元数据
     * 
     * @param objectId 数据对象ID
     * @return 文件元数据Map
     * @throws Exception 获取失败时抛出异常
     */
    java.util.Map<String, java.util.Map<String, String>> getFileMetaData(String objectId) throws Exception;
    
    /**
     * 添加文件元数据
     * 
     * @param objectId 数据对象ID
     * @param metaData 文件元数据Map
     * @throws Exception 添加失败时抛出异常
     */
    void addFileMetaData(String objectId, java.util.Map<String, java.util.Map<String, String>> metaData) throws Exception;
}
