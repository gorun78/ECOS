package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IStrategyService;
import com.chinacreator.gzcm.runtime.core.monitor.bean.MonitorObjectBean;
import com.chinacreator.gzcm.runtime.core.monitor.strategy.bean.StrategyBean;

/**
 * 监控策略服务实现
 * 
 * @author CDRC Runtime Team
 */
public class StrategyServiceImpl implements IStrategyService {
    
    // 内存存储：strategyId -> StrategyBean
    private final Map<String, StrategyBean> strategyStore = new ConcurrentHashMap<>();
    
    // 内存存储：pluginName_itemPath -> StrategyBean
    private final Map<String, StrategyBean> strategyByPluginMap = new ConcurrentHashMap<>();
    
    @Override
    public void addStrategy(StrategyBean bean) throws Exception {
        if (bean != null) {
            if (bean.getCollect_strategy_id() == null) {
                bean.setCollect_strategy_id("strategy_" + System.currentTimeMillis());
            }
            strategyStore.put(bean.getCollect_strategy_id(), bean);
            
            // 更新插件映射
            if (bean.getPlugin_name() != null && bean.getTarget_path() != null) {
                String key = bean.getPlugin_name() + "_" + bean.getTarget_path();
                strategyByPluginMap.put(key, bean);
            }
        }
    }
    
    @Override
    public long getCollectRepeatInterval(String pluginName, String itemPth) throws Exception {
        String key = pluginName + "_" + itemPth;
        StrategyBean strategy = strategyByPluginMap.get(key);
        if (strategy != null) {
            // 根据time_interval_sec和time_interval_min计算间隔（毫秒）
            long interval = 0;
            if (strategy.getTime_interval_sec() > 0) {
                interval = strategy.getTime_interval_sec() * 1000L;
            } else if (strategy.getTime_interval_min() > 0) {
                interval = strategy.getTime_interval_min() * 60L * 1000L;
            }
            if (interval > 0) {
                return interval;
            }
        }
        // 默认间隔：60秒
        return 60000L;
    }
    
    @Override
    public List<StrategyBean> getAllStrategyBean() throws Exception {
        return new ArrayList<>(strategyStore.values());
    }
    
    @Override
    public StrategyBean getStrategyById(String strategyId) throws Exception {
        return strategyStore.get(strategyId);
    }
    
    @Override
    public List<MonitorObjectBean> getMonitorObjectsByItem(String itemPath) throws Exception {
        // 占位实现：返回空列表
        return new ArrayList<>();
    }
    
    @Override
    public StrategyBean getStrategyByPluginNameAndPath(String pluginName, String itemPth) throws Exception {
        String key = pluginName + "_" + itemPth;
        return strategyByPluginMap.get(key);
    }
    
    @Override
    public void updateStrategy(StrategyBean item) throws Exception {
        addStrategy(item);
    }
}

