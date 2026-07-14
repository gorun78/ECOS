package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.service.impl;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObject;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObjectColumn;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.dao.DataObjectDao;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.service.IDataObjectService;
import com.chinacreator.gzcm.runtime.core.common.util.PageInfo;

/**
 * 数据对象服务实现类
 */
public class DataObjectService implements IDataObjectService {
    
    private DataObjectDao dataObjectDao;
    
    public DataObjectService() {
        // 默认构造函数
    }
    
    public DataObjectService(DataObjectDao dataObjectDao) {
        this.dataObjectDao = dataObjectDao;
    }
    
    @Override
    public DataObject getDataObjectById(String objectId) throws Exception {
        if (dataObjectDao == null) {
            throw new IllegalStateException("DataObjectDao is not initialized");
        }
        return dataObjectDao.getDataObjectById(objectId);
    }
    
    @Override
    public PageInfo<DataObject> getDataObjectList(Object condition, Integer offset, Integer pageSize) throws Exception {
        if (dataObjectDao == null) {
            throw new IllegalStateException("DataObjectDao is not initialized");
        }
        List<DataObject> list = dataObjectDao.getDataObjectList(condition);
        PageInfo<DataObject> pageInfo = new PageInfo<>();
        if (list != null && offset != null && pageSize != null && offset >= 0 && pageSize > 0) {
            int total = list.size();
            int start = Math.min(offset, total);
            int end = Math.min(offset + pageSize, total);
            pageInfo.setList(list.subList(start, end));
            pageInfo.setTotal(total);
        } else {
            pageInfo.setList(list);
            pageInfo.setTotal(list != null ? list.size() : 0);
        }
        return pageInfo;
    }
    
    @Override
    public List<DataObjectColumn> getDataObjectColumns(String objectId) throws Exception {
        if (dataObjectDao == null) {
            throw new IllegalStateException("DataObjectDao is not initialized");
        }
        return dataObjectDao.getDataObjectColumns(objectId);
    }
    
    @Override
    public void updateDataObject(DataObject dataObject) throws Exception {
        if (dataObjectDao == null) {
            throw new IllegalStateException("DataObjectDao is not initialized");
        }
        dataObjectDao.updateDataObject(dataObject);
    }
}
