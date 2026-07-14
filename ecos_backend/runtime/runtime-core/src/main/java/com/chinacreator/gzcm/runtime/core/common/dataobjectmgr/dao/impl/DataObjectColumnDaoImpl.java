package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.dao.impl;

import java.util.ArrayList;
import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObject;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObjectColumn;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.dao.DataObjectColumnDao;

public class DataObjectColumnDaoImpl implements DataObjectColumnDao {
    @Override
    public List<DataObjectColumn> getDataObjectColumnListByObjectId(String objectId) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public void updateDataObjectColumnLength(List<DataObjectColumn> columns) throws Exception {
        // 占位实现
    }

    @Override
    public DataObjectColumn add(DataObjectColumn srcColumn) throws Exception {
        // 占位实现：直接返回入参
        return srcColumn;
    }

    @Override
    public DataObjectColumn getDataObjectColumnByColumnId(String columnId) throws Exception {
        return null;
    }

    @Override
    public DataObjectColumn getDataObjectColumnByIdCode(String objectId, String columnCode) throws Exception {
        return null;
    }

    @Override
    public void updateDataObjectColumns(List<DataObjectColumn> columns) throws Exception {
        // 占位实现
    }

    @Override
    public void deleteColumns(List<DataObjectColumn> columns) throws Exception {
        // 占位实现
    }
    
    @Override
    public void addDataObjectColumns(DataObject dataObject, List<DataObjectColumn> columns) throws Exception {
        // 占位实现
        // TODO: 实现添加数据对象列的逻辑
    }
    
    @Override
    public List<DataObjectColumn> getDataObjectColumnListByPkFlagBean(DataObjectColumn condition) throws Exception {
        // 占位实现
        // TODO: 实现根据主键标志查询数据对象列的逻辑
        return new ArrayList<>();
    }
}
