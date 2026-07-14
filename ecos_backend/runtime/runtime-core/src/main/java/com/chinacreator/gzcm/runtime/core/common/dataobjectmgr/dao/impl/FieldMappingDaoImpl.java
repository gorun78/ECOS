package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.dao.impl;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.FieldMappingBean;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.dao.IFieldMappingDao;

public class FieldMappingDaoImpl implements IFieldMappingDao {
    @Override public List<FieldMappingBean> findByShareRefId(String shareRefId) throws Exception { return null; }
    @Override public void update(List<FieldMappingBean> list) throws Exception {}
    @Override public void addMappingRefs(String shareRefId, List<FieldMappingBean> list) throws Exception {}
    @Override public void removeMappingRefs(String shareRefId) throws Exception {
        // 占位实现
        // TODO: 实现删除映射引用的逻辑
    }
}
