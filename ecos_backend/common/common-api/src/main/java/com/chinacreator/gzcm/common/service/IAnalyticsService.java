package com.chinacreator.gzcm.common.service;

import java.util.List;
import java.util.Map;

/**
 * 分析引擎抽象接口
 * 
 * @author ECOS Sprint 5.3
 */
public interface IAnalyticsService {

    /**
     * 执行分析 SQL 查询
     *
     * @param analyticsSql 分析 SQL 语句
     * @return 查询结果行列表，每行为 Map 格式
     */
    List<Map<String, Object>> executeQuery(String analyticsSql);

    /**
     * 健康检查
     *
     * @return 连接状态信息，包含 analyticsProvider、status 等字段
     */
    Map<String, Object> health();
}
