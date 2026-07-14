package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.service.impl;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObjectColumn;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.service.IDataObjectColumnService;

public class DataObjectColumnService implements IDataObjectColumnService {
    @Override 
    public List<DataObjectColumn> getAllowFields(String scheduleId, String param2, String objectId) throws Exception { 
        return null; 
    }
    
    @Override
    public List<DataObjectColumn> getDataObjectColumnListByObjectId(String objectId) throws Exception {
        // 默认实现，返回空列表
        return new java.util.ArrayList<>();
    }
}
