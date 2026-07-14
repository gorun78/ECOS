package com.chinacreator.gzcm.sysman.compliance.dao;

import com.chinacreator.gzcm.sysman.compliance.entity.DataResidency;

/**
 * 数据驻留DAO接口
 */
public interface DataResidencyDao {
    void insert(DataResidency residency) throws Exception;
    DataResidency findByResource(String resourceId, String resourceType) throws Exception;
    void update(DataResidency residency) throws Exception;
}

