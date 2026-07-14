package com.chinacreator.gzcm.runtime.core.common.datasourcemgr.service;

import com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean.Tddxdatasource;
import com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean.WSDLDataSourceBean;

public interface IDataSourceMgrService {
    Tddxdatasource findDataSourceById(String dsId) throws Exception;
    
    /**
     * 根据ID查找数据源（别名方法，兼容旧代码）
     * @param dsId 数据源ID
     * @return 数据源对象
     * @throws Exception
     */
    default Tddxdatasource selectDataSourceById(String dsId) throws Exception {
        return findDataSourceById(dsId);
    }
    
    /**
     * 根据ID查找WSDL数据源
     * @param dsId 数据源ID
     * @return WSDL数据源对象
     * @throws Exception
     */
    default WSDLDataSourceBean findWSDataSourceById(String dsId) throws Exception {
        // Placeholder: delegate to findDataSourceById and convert if needed
        Tddxdatasource ds = findDataSourceById(dsId);
        if (ds == null) {
            return null;
        }
        // Convert Tddxdatasource to WSDLDataSourceBean if possible
        WSDLDataSourceBean wsdlDs = new WSDLDataSourceBean();
        wsdlDs.setDs_id(ds.getDs_id());
        wsdlDs.setDs_name(ds.getDs_name());
        return wsdlDs;
    }
    
    /**
     * 根据ID查找Tddxdatasource（别名方法，兼容旧代码）
     * @param dsId 数据源ID
     * @return 数据源对象
     * @throws Exception
     */
    default Tddxdatasource findTddxdatasourceById(String dsId) throws Exception {
        return findDataSourceById(dsId);
    }
}
