package com.chinacreator.gzcm.runtime.core.common.exsharemgr.dao;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.exsharemgr.bean.RequestParameterMapping;
import com.chinacreator.gzcm.runtime.core.common.exsharemgr.bean.RequestParameterMappingDefinition;

/**
 * 请求参数映射DAO接口
 */
public interface IRequestParameterMappingDao {
    
    /**
     * 根据条件查询请求参数映射定义列表
     * @param condition 查询条件
     * @return 映射定义列表
     * @throws Exception
     */
    List<RequestParameterMappingDefinition> selectRequestParameterMappingDefinitions(RequestParameterMappingDefinition condition) throws Exception;
    
    /**
     * 插入请求参数映射定义
     * @param definition 映射定义
     * @throws Exception
     */
    void insertRequestParameterMappingDefinition(RequestParameterMappingDefinition definition) throws Exception;
    
    /**
     * 根据方案ID查询请求参数映射列表
     * @param scheduleId 方案ID
     * @return 映射列表
     * @throws Exception
     */
    List<RequestParameterMapping> selectRequestParameterMappings(String scheduleId) throws Exception;
    
    /**
     * 批量插入请求参数映射
     * @param mappings 映射列表
     * @throws Exception
     */
    void insertRequestParameterMappings(List<RequestParameterMapping> mappings) throws Exception;
}
