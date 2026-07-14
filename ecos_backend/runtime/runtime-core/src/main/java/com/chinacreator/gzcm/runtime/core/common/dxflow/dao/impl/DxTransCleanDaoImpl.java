package com.chinacreator.gzcm.runtime.core.common.dxflow.dao.impl;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dxflow.bean.IDxTransCleanParam;
import com.chinacreator.gzcm.runtime.core.common.dxflow.dao.IDxTransCleanDao;

public class DxTransCleanDaoImpl implements IDxTransCleanDao {
    @Override public void add(IDxTransCleanParam stepParam) throws Exception {}
    @Override public void update(IDxTransCleanParam stepParam) throws Exception {}
    @Override public void delete(String id) throws Exception {}
    @Override public IDxTransCleanParam findById(String id) throws Exception { return null; }
    @Override public List<IDxTransCleanParam> findByCriteria(IDxTransCleanParam criteria) throws Exception { return null; }
    @Override public Integer getScheduleMaxSortSn(String scheduleId) throws Exception { return 0; }
    @Override public List<IDxTransCleanParam> getTransRules(String scheduleId) throws Exception { return null; }
    @Override public void addBatch(List<IDxTransCleanParam> transBeans) throws Exception {}
}
