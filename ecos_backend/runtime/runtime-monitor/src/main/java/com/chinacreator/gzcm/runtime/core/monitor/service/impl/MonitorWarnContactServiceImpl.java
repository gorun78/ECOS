package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IMonitorWarnContactService;
import com.chinacreator.gzcm.runtime.core.monitor.warn.bean.MonitorWarnContact;
import com.chinacreator.gzcm.runtime.core.monitor.warn.common.bean.WarnTypeBean;
import com.chinacreator.gzcm.runtime.core.common.util.PageInfo;

/**
 * 监控告警联系人服务实现
 * 
 * @author CDRC Runtime Team
 */
public class MonitorWarnContactServiceImpl implements IMonitorWarnContactService {
    
    // 内存存储：contact_id -> MonitorWarnContact
    private final Map<String, MonitorWarnContact> contactStore = new ConcurrentHashMap<>();
    
    // 内存存储：dbname -> List<WarnTypeBean>
    private final Map<String, List<WarnTypeBean>> warnTypeStore = new ConcurrentHashMap<>();
    
    @Override
    public List<MonitorWarnContact> find(MonitorWarnContact condition) throws Exception {
        List<MonitorWarnContact> all = new ArrayList<>(contactStore.values());
        if (condition == null) {
            return all;
        }
        
        return all.stream()
            .filter(contact -> {
                if (condition.getContact_name() != null && 
                    !contact.getContact_name().contains(condition.getContact_name())) {
                    return false;
                }
                if (condition.getContact_email() != null && 
                    !contact.getContact_email().equals(condition.getContact_email())) {
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public List<WarnTypeBean> findWarnTypes(int type, String dbname) throws Exception {
        List<WarnTypeBean> types = warnTypeStore.get(dbname);
        return types != null ? new ArrayList<>(types) : new ArrayList<>();
    }
    
    @Override
    public PageInfo<MonitorWarnContact> findByPage(Integer offset, Integer pageSize, MonitorWarnContact condition) throws Exception {
        List<MonitorWarnContact> filtered = find(condition);

        int start = offset != null ? offset : 0;
        int size = pageSize != null ? pageSize : 10;
        int end = Math.min(start + size, filtered.size());
        List<MonitorWarnContact> datas = new ArrayList<>();
        for (int i = start; i < end; i++) {
            datas.add(filtered.get(i));
        }

        // 计算页码 (offset从0开始)
        int pageNum = (start / size) + 1;
        PageInfo<MonitorWarnContact> pageInfo = new PageInfo<>(datas, filtered.size(), pageNum, size);

        return pageInfo;
    }
    
    @Override
    public boolean add(MonitorWarnContact item) throws Exception {
        if (item.getContact_id() == null) {
            item.setContact_id("contact_" + System.currentTimeMillis());
        }
        contactStore.put(item.getContact_id(), item);
        return true;
    }
    
    @Override
    public boolean delete(MonitorWarnContact item) throws Exception {
        if (item.getContact_id() != null) {
            contactStore.remove(item.getContact_id());
            return true;
        }
        return false;
    }
    
    @Override
    public boolean update(MonitorWarnContact item) throws Exception {
        if (item.getContact_id() != null) {
            contactStore.put(item.getContact_id(), item);
            return true;
        }
        return false;
    }
    
    @Override
    public void insertWarnTypes(List<WarnTypeBean> list, String dbname) throws Exception {
        if (list != null) {
            warnTypeStore.computeIfAbsent(dbname, k -> new ArrayList<>()).addAll(list);
        }
    }
    
    @Override
    public void updateWarnTypes(List<WarnTypeBean> list, String dbname) throws Exception {
        if (list != null) {
            warnTypeStore.put(dbname, new ArrayList<>(list));
        }
    }
}

