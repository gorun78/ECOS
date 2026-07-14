package com.chinacreator.gzcm.runtime.core.common.datasourcemgr.mapper;

import com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean.Tddxdatasource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TddxdatasourceMapper {
    
    Tddxdatasource getTddxdatasourceById(@Param("dsId") String dsId);
    
    Tddxdatasource findDataSourceByObjectId(@Param("objectId") String objectId);
    
    Tddxdatasource getErrorDataSourceByNodeId(@Param("nodeId") String nodeId);
}
