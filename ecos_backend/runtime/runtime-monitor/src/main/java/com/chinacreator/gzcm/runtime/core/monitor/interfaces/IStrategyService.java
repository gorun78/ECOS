package com.chinacreator.gzcm.runtime.core.monitor.interfaces;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.bean.MonitorObjectBean;
import com.chinacreator.gzcm.runtime.core.monitor.strategy.bean.StrategyBean;

/**
 * 策略服务接口
 */
public interface IStrategyService {
    
    void addStrategy(StrategyBean bean) throws Exception;
    
    long getCollectRepeatInterval(String pluginName, String itemPth) throws Exception;
    
    List<StrategyBean> getAllStrategyBean() throws Exception;
    
    StrategyBean getStrategyById(String strategyId) throws Exception;
    
    List<MonitorObjectBean> getMonitorObjectsByItem(String itemPath) throws Exception;
    
    StrategyBean getStrategyByPluginNameAndPath(String pluginName, String itemPth) throws Exception;
    
    void updateStrategy(StrategyBean item) throws Exception;
}
