package com.chinacreator.gzcm.runtime.core.compliance.dao.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.compliance.dao.DataResidencyDao;
import com.chinacreator.gzcm.sysman.compliance.entity.DataResidency;

/**
 * 数据驻留DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class DataResidencyDaoImpl implements DataResidencyDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/compliance/dao/impl/DataResidency-sql.xml";

    @Autowired
    public DataResidencyDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(DataResidency residency) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertDataResidency", residency);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入数据驻留记录失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataResidency findByResource(String resourceId, String resourceType) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("resourceId", resourceId);
            params.put("resourceType", resourceType);
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findByResource", DataResidency.class, params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询数据驻留记录失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(DataResidency residency) throws Exception {
        try {
            databaseAccess.executeUpdateFromConfigWithEntity(SQL_CONFIG_PATH, "updateDataResidency", residency);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("更新数据驻留记录失败: " + e.getMessage(), e);
        }
    }
}

