package com.chinacreator.gzcm.runtime.core.monitor.interfaces;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.monitor.bean.MonitorObjectBean;
import com.chinacreator.gzcm.runtime.core.monitor.plugin.bean.PluginTargetBean;

/**
 * 插件目标服务接口
 */
public interface IPluginTargetService {
    
    List<PluginTargetBean> findFirstLevelPluginTargets(PluginTargetBean bean) throws Exception;
    
    List<PluginTargetBean> findPluginTargetsByCondition(PluginTargetBean condition) throws Exception;
    
    List<String> getTargetDataMap(String plugin, MonitorObjectBean obj, String targetname) throws Exception;
}
