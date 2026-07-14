package com.chinacreator.gzcm.runtime.core.monitor.strategy.dao;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.strategy.bean.StrategyBean;

public interface IStrategyDao {

	public void insertStrategy(StrategyBean bean, String dbname)throws Exception;
	
	public StrategyBean getStrategyBean(String pluginName,String itemPath)throws Exception;
	
	public List<StrategyBean> findByCondition(StrategyBean condition)throws Exception;
	
	public StrategyBean getStrategyById(String strategyId)throws Exception;
	
	public void update(StrategyBean bean)throws Exception;
	
	public void delete(String pluginName, String dbname)throws Exception;
	
}
