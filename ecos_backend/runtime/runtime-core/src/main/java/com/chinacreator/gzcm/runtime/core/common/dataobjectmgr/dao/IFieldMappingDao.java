package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.dao;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.FieldMappingBean;

public interface IFieldMappingDao {
    List<FieldMappingBean> findByShareRefId(String shareRefId) throws Exception;
    void update(List<FieldMappingBean> list) throws Exception;
    void addMappingRefs(String shareRefId, List<FieldMappingBean> list) throws Exception;
    
    /**
     * 添加映射引用（重载方法，从列表中提取shareRefId）
     * @param list 映射列表（每个元素应包含share_ref_id）
     * @throws Exception
     */
    default void addMappingRefs(List<FieldMappingBean> list) throws Exception {
        if (list == null || list.isEmpty()) {
            return;
        }
        // 按share_ref_id分组处理
        java.util.Map<String, java.util.List<FieldMappingBean>> grouped = new java.util.HashMap<>();
        for (FieldMappingBean bean : list) {
            String shareRefId = bean.getShare_ref_id();
            if (shareRefId != null) {
                grouped.computeIfAbsent(shareRefId, k -> new java.util.ArrayList<>()).add(bean);
            }
        }
        // 对每个shareRefId调用addMappingRefs
        for (java.util.Map.Entry<String, java.util.List<FieldMappingBean>> entry : grouped.entrySet()) {
            addMappingRefs(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * 删除映射引用
     * @param shareRefId 共享引用ID
     * @throws Exception
     */
    void removeMappingRefs(String shareRefId) throws Exception;
}
