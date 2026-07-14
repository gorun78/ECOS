package com.chinacreator.gzcm.runtime.core.common.dxflow.dao;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dxflow.bean.IDxTransCleanParam;

public interface IDxTransCleanDao {
    void add(IDxTransCleanParam stepParam) throws Exception;
    void update(IDxTransCleanParam stepParam) throws Exception;
    void delete(String id) throws Exception;
    IDxTransCleanParam findById(String id) throws Exception;
    List<IDxTransCleanParam> findByCriteria(IDxTransCleanParam criteria) throws Exception;
    Integer getScheduleMaxSortSn(String scheduleId) throws Exception;
    List<IDxTransCleanParam> getTransRules(String scheduleId) throws Exception;
    void addBatch(List<IDxTransCleanParam> transBeans) throws Exception;
}
