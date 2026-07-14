package com.chinacreator.gzcm.runtime.core.common.datasourcemgr.dao;

import com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean.Tddxdatasource;

public interface ITddxdatasourceDao {
    Tddxdatasource getTddxdatasourceById(String id) throws Exception;
    
    /**
     * 根据节点ID获取错误数据源
     * @param nodeId 节点ID
     * @return 错误数据源
     * @throws Exception 查询异常
     */
    default Tddxdatasource getErrorDataSourceByNodeId(String nodeId) throws Exception {
        // Placeholder implementation
        return null;
    }
    
    /**
     * 根据ID查找数据源（兼容方法）
     * @param id 数据源ID
     * @return 数据源对象
     * @throws Exception
     */
    default Tddxdatasource findDataSourceById(String id) throws Exception {
        return getTddxdatasourceById(id);
    }
    
    /**
     * 根据对象ID查找数据源
     * @param objectId 对象ID
     * @return 数据源对象
     * @throws Exception
     */
    Tddxdatasource findDataSourceByObjectId(String objectId) throws Exception;
}
