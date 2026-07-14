package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.monitor.sigarplugin.IRmiService;

/**
 * 监控RMI服务实现
 * 
 * @author CDRC Runtime Team
 */
public class RmiServiceImpl implements IRmiService {
    
    @Override
    public Map<String, String> statHostInfo() throws RemoteException, Exception {
        Map<String, String> info = new HashMap<>();
        info.put("hostname", "localhost");
        info.put("os", System.getProperty("os.name"));
        info.put("arch", System.getProperty("os.arch"));
        info.put("cpu_count", String.valueOf(Runtime.getRuntime().availableProcessors()));
        return info;
    }
    
    @Override
    public Map<String, String> statCpuUserInfo() throws RemoteException {
        Map<String, String> info = new HashMap<>();
        info.put("cpu_usage", "50.0");
        info.put("cpu_user", "30.0");
        info.put("cpu_sys", "20.0");
        info.put("cpu_idle", "50.0");
        return info;
    }
    
    @Override
    public Map<String, String> statDiskInfo(String childnew) throws RemoteException {
        Map<String, String> info = new HashMap<>();
        info.put("disk_name", childnew);
        info.put("total", "1000000");
        info.put("used", "500000");
        info.put("free", "500000");
        info.put("usage_percent", "50.0");
        return info;
    }
    
    @Override
    public List<String> getAllDiskNames() throws RemoteException, Exception {
        List<String> disks = new ArrayList<>();
        disks.add("C:");
        disks.add("D:");
        return disks;
    }
}

