package com.chinacreator.gzcm.gateway.service;

import com.chinacreator.gzcm.gateway.dao.MonitorDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 系统监控服务 — 从 MonitorController 下沉的 JdbcTemplate 访问层。
 * SQL 语义与原 Controller 保持一致。
 *
 * <p>B6b 数据访问收口（架构铁律 §2.5 / IR06）：裸 SQL 全量下沉至 {@link MonitorDao}；
 * 本 Service 减薄为编排层，公共方法签名维持不变。</p>
 */
@Service
public class MonitorService {

    private final MonitorDao dao;

    public MonitorService(MonitorDao dao) {
        this.dao = dao;
    }

    /** 活跃告警数（status=OPEN） */
    public Integer countActiveAlerts() {
        return dao.countActiveAlerts();
    }

    /** 今日告警总数 */
    public Integer countAlertsToday() {
        return dao.countAlertsToday();
    }

    /** 最近 10 条告警 */
    public List<Map<String, Object>> queryRecentAlerts() {
        return dao.queryRecentAlerts();
    }

    /** 打开的 DQ 问题数 */
    public Integer countOpenDqIssues() {
        return dao.countOpenDqIssues();
    }

    /** 数据库健康探测：SELECT 1 */
    public boolean isDbUp() {
        return dao.isDbUp();
    }

    /** 今日告警按级别统计 */
    public List<Map<String, Object>> queryAlertsTodayByLevel() {
        return dao.queryAlertsTodayByLevel();
    }

    /** 告警总数 */
    public Integer countTotalAlerts() {
        return dao.countTotalAlerts();
    }
}
