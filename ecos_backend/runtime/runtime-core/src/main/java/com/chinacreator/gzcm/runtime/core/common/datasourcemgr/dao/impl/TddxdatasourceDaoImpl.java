package com.chinacreator.gzcm.runtime.core.common.datasourcemgr.dao.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean.Tddxdatasource;
import com.chinacreator.gzcm.runtime.core.common.datasourcemgr.dao.ITddxdatasourceDao;
import com.chinacreator.gzcm.runtime.core.common.datasourcemgr.mapper.TddxdatasourceMapper;

@Repository
public class TddxdatasourceDaoImpl implements ITddxdatasourceDao {
    
    @Autowired
    private TddxdatasourceMapper mapper;
    
    @Override
    public Tddxdatasource getTddxdatasourceById(String id) throws Exception {
        return mapper.getTddxdatasourceById(id);
    }
    
    @Override
    public Tddxdatasource findDataSourceByObjectId(String objectId) throws Exception {
        return mapper.findDataSourceByObjectId(objectId);
    }
    
    @Override
    public Tddxdatasource getErrorDataSourceByNodeId(String nodeId) throws Exception {
        return mapper.getErrorDataSourceByNodeId(nodeId);
    }
}
