package com.chinacreator.gzcm.runtime.core.common;

/**
 * 初始化启动任务接口
 * 用于在系统启动时执行的任务
 * 
 * @author CDRC Runtime Team
 */
public interface InitStartTaskInterface {
    
    /**
     * 执行启动任务
     * 
     * @param dbname 数据库名称
     * @throws Exception 执行异常
     */
    void run(String dbname) throws Exception;
}
