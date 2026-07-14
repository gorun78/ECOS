package com.chinacreator.gzcm.runtime.core.common.dxflow.dao;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dxflow.bean.DxTransRuleOutputField;

/**
 * IDxTransOutputFieldDao - 数据转换输出字段DAO接口
 * 用于数据转换输出字段的数据库操作
 */
public interface IDxTransOutputFieldDao {
    
    /**
     * 根据转换ID查找输出字段列表
     */
    List<?> findByTransId(String transId) throws Exception;
    
    /**
     * 添加输出字段
     */
    boolean add(Object outputField) throws Exception;
    
    /**
     * 更新输出字段
     */
    boolean update(Object outputField) throws Exception;
    
    /**
     * 删除输出字段
     */
    boolean delete(String fieldId) throws Exception;
    
    /**
     * 根据清理规则ID删除输出字段
     */
    void deleteByCleanRuleId(String cleanRuleId) throws Exception;
    
    /**
     * 根据转换清理ID获取输出字段列表
     */
    List<DxTransRuleOutputField> getDxOutputFieldsByTransCleanId(String transCleanId) throws Exception;
    
    /**
     * 删除XML数据源数据对象项映射
     */
    void deleteXmlDsDoItemMapping(String mappingId) throws Exception;
    
    /**
     * 批量添加输出字段
     */
    void addBatch(List<DxTransRuleOutputField> outputFields) throws Exception;
}

