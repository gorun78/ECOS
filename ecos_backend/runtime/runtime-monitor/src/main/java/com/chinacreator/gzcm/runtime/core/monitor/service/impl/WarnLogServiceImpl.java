package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IWarnLogService;
import com.chinacreator.gzcm.runtime.core.monitor.warn.bean.WarnLogBean;
import com.chinacreator.gzcm.runtime.core.common.util.PageInfo;

/**
 * 告警日志服务实现
 * 
 * @author CDRC Runtime Team
 */
public class WarnLogServiceImpl implements IWarnLogService {
    
    // 内存存储：logid -> WarnLogBean
    private final Map<String, WarnLogBean> logStore = new ConcurrentHashMap<>();
    
    @Override
    public PageInfo<WarnLogBean> findByPage(Integer offset, Integer pageSize, WarnLogBean condition) throws Exception {
        List<WarnLogBean> all = new ArrayList<>(logStore.values());
        List<WarnLogBean> filtered = all;

        if (condition != null) {
            filtered = all.stream()
                .filter(log -> {
                    if (condition.getWarn_type() != null && !log.getWarn_type().equals(condition.getWarn_type())) {
                        return false;
                    }
                    if (condition.getWarn_objid() != null && !log.getWarn_objid().equals(condition.getWarn_objid())) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
        }

        int start = offset != null ? offset : 0;
        int size = pageSize != null ? pageSize : 10;
        int end = Math.min(start + size, filtered.size());
        List<WarnLogBean> datas = new ArrayList<>();
        for (int i = start; i < end; i++) {
            datas.add(filtered.get(i));
        }

        // 计算页码 (offset从0开始)
        int pageNum = (start / size) + 1;
        PageInfo<WarnLogBean> pageInfo = new PageInfo<>(datas, filtered.size(), pageNum, size);

        return pageInfo;
    }
    
    @Override
    public void insertWarnLog(WarnLogBean log) throws Exception {
        if (log.getLog_id() == null) {
            log.setLog_id("log_" + System.currentTimeMillis());
        }
        logStore.put(log.getLog_id(), log);
    }
    
    @Override
    public void updateLogResult(String logid, String msg) throws Exception {
        WarnLogBean log = logStore.get(logid);
        if (log != null) {
            log.setWarn_result(msg);
        }
    }
    
    @Override
    public void setLogHanded(String ids) throws Exception {
        if (ids != null) {
            String[] idArray = ids.split(",");
            for (String id : idArray) {
                WarnLogBean log = logStore.get(id.trim());
                if (log != null) {
                    log.setIshanded("1");
                }
            }
        }
    }
    
    @Override
    public void warn(String type, String objid, String objname, String err, String typeHander) throws Exception {
        WarnLogBean log = new WarnLogBean();
        log.setLog_id("log_" + System.currentTimeMillis());
        log.setWarn_type(type);
        log.setWarn_objid(objid);
        log.setWarn_objname(objname);
        log.setWarn_message(err);
        log.setWarn_hand(typeHander);
        log.setIshanded("0");
        log.setWarn_time(new java.sql.Timestamp(System.currentTimeMillis()));
        insertWarnLog(log);
    }
}

