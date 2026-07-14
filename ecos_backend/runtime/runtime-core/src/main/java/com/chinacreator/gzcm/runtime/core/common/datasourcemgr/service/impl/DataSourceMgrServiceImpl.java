package com.chinacreator.gzcm.runtime.core.common.datasourcemgr.service.impl;

import com.chinacreator.gzcm.runtime.core.common.datasourcemgr.service.IDataSourceMgrService;
import com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean.Tddxdatasource;

public class DataSourceMgrServiceImpl implements IDataSourceMgrService {
    @Override
    public Tddxdatasource findDataSourceById(String dsId) throws Exception {
        return new Tddxdatasource();
    }
}
