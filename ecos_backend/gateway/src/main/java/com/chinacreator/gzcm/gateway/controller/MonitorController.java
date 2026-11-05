package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.gateway.dto.MonitorAlertStatsVO;
import com.chinacreator.gzcm.gateway.dto.MonitorDashboardVO;
import com.chinacreator.gzcm.gateway.dto.MonitorHealthVO;
import com.chinacreator.gzcm.gateway.service.MonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.*;

/**
 * Monitoring Dashboard API — 系统监控 + 告警概览。
 *
 * <pre>
 * GET  /api/monitor          — 监控仪表盘汇总
 * GET  /api/monitor/alerts   — 告警统计
 * GET  /api/monitor/health   — 系统健康状态
 * </pre>
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private static final Logger log = LoggerFactory.getLogger(MonitorController.class);
    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping
    public ApiResponse<MonitorDashboardVO> dashboard() {
        // 系统资源
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MonitorDashboardVO.SystemStats system = new MonitorDashboardVO.SystemStats(
                os.getAvailableProcessors(),
                String.format("%.2f%%", os.getSystemLoadAverage()),
                mem.getHeapMemoryUsage().getUsed() / 1024 / 1024,
                mem.getHeapMemoryUsage().getMax() / 1024 / 1024);

        // 告警统计
        Integer activeAlerts = monitorService.countActiveAlerts();
        Integer totalToday = monitorService.countAlertsToday();

        // 最近告警（告警表动态列，属主行原样透传）
        var recentAlerts = monitorService.queryRecentAlerts();

        // DQ 问题趋势
        Integer openDqIssues = monitorService.countOpenDqIssues();

        // chartData: 时间序列 mock 数据 (最近12个5分钟间隔点)
        List<MonitorDashboardVO.ChartPoint> chartData = new ArrayList<>();
        long now = System.currentTimeMillis();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm");
        for (int i = 11; i >= 0; i--) {
            chartData.add(new MonitorDashboardVO.ChartPoint(
                    sdf.format(new java.util.Date(now - i * 5 * 60 * 1000)),
                    Math.round((20 + Math.random() * 60) * 10.0) / 10.0,
                    Math.round((40 + Math.random() * 30) * 10.0) / 10.0));
        }

        // processes: JVM 线程 + fallback
        List<MonitorDashboardVO.ProcessRow> processes = new ArrayList<>();
        for (java.lang.Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().contains("http") || t.getName().contains("pool") || t.getName().contains("scheduler")) {
                processes.add(new MonitorDashboardVO.ProcessRow(
                        t.getName(),
                        t.isDaemon() ? "daemon" : "user",
                        t.getState().toString(),
                        1,
                        t.isAlive() ? "Running" : "Stopped"));
                if (processes.size() >= 10) break;
            }
        }
        if (processes.isEmpty()) {
            processes.add(new MonitorDashboardVO.ProcessRow(
                    "ECOS-Gateway-Main", "service", "active", 1, "Running"));
        }

        return ApiResponse.success(new MonitorDashboardVO(
                system,
                activeAlerts != null ? activeAlerts : 0,
                totalToday != null ? totalToday : 0,
                recentAlerts,
                openDqIssues != null ? openDqIssues : 0,
                chartData,
                processes));
    }

    @GetMapping("/health")
    public ApiResponse<MonitorHealthVO> health() {
        MonitorHealthVO.DatabaseStatus db;
        try {
            db = monitorService.isDbUp()
                    ? MonitorHealthVO.DatabaseStatus.up()
                    : MonitorHealthVO.DatabaseStatus.down(null);
        } catch (Exception e) {
            db = MonitorHealthVO.DatabaseStatus.down(e.getMessage());
        }

        return ApiResponse.success(new MonitorHealthVO(
                db,
                ManagementFactory.getRuntimeMXBean().getUptime(),
                "Phase4-M1",
                "UP".equals(db.status()) ? "UP" : "DEGRADED"));
    }

    @GetMapping("/alerts")
    public ApiResponse<MonitorAlertStatsVO> alertStats() {
        // 按级别统计（告警表动态列，属主行原样透传）
        var byLevel = monitorService.queryAlertsTodayByLevel();

        // 总览
        Integer total = monitorService.countTotalAlerts();
        Integer open = monitorService.countActiveAlerts();
        return ApiResponse.success(new MonitorAlertStatsVO(
                byLevel, total != null ? total : 0, open != null ? open : 0));
    }
}
