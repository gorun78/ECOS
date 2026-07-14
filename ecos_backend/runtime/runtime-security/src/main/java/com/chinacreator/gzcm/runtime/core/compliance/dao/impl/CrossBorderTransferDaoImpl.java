package com.chinacreator.gzcm.runtime.core.compliance.dao.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.compliance.dao.CrossBorderTransferDao;
import com.chinacreator.gzcm.sysman.compliance.entity.CrossBorderTransfer;

/**
 * 跨境传输DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class CrossBorderTransferDaoImpl implements CrossBorderTransferDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/compliance/dao/impl/CrossBorderTransfer-sql.xml";

    @Autowired
    public CrossBorderTransferDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(CrossBorderTransfer transfer) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertCrossBorderTransfer", transfer);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入跨境传输记录失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CrossBorderTransfer> query(Map<String, Object> condition) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "queryTransfers", CrossBorderTransfer.class, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询跨境传输记录失败: " + e.getMessage(), e);
        }
    }
}

