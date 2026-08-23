package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
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
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> d = new LinkedHashMap<>();

        // 系统资源
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("cpu_cores", os.getAvailableProcessors());
        system.put("cpu_load", String.format("%.2f%%", os.getSystemLoadAverage()));
        system.put("heap_used_mb", mem.getHeapMemoryUsage().getUsed() / 1024 / 1024);
        system.put("heap_max_mb", mem.getHeapMemoryUsage().getMax() / 1024 / 1024);
        d.put("system", system);

        // 告警统计
        Integer activeAlerts = monitorService.countActiveAlerts();
        Integer totalToday = monitorService.countAlertsToday();
        d.put("active_alerts", activeAlerts != null ? activeAlerts : 0);
        d.put("alerts_today", totalToday != null ? totalToday : 0);

        // 最近告警
        List<Map<String, Object>> recentAlerts = monitorService.queryRecentAlerts();
        d.put("recent_alerts", recentAlerts);

        // DQ 问题趋势
        Integer openDqIssues = monitorService.countOpenDqIssues();
        d.put("open_dq_issues", openDqIssues != null ? openDqIssues : 0);

        // chartData: 时间序列 mock 数据 (最近12个5分钟间隔点)
        List<Map<String, Object>> chartData = new ArrayList<>();
        long now = System.currentTimeMillis();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm");
        for (int i = 11; i >= 0; i--) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", sdf.format(new java.util.Date(now - i * 5 * 60 * 1000)));
            point.put("cpu", Math.round((20 + Math.random() * 60) * 10.0) / 10.0);
            point.put("memory", Math.round((40 + Math.random() * 30) * 10.0) / 10.0);
            chartData.add(point);
        }
        d.put("chartData", chartData);

        // processes: JVM 线程 + fallback
        List<Map<String, Object>> processes = new ArrayList<>();
        for (java.lang.Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().contains("http") || t.getName().contains("pool") || t.getName().contains("scheduler")) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", t.getName());
                p.put("type", t.isDaemon() ? "daemon" : "user");
                p.put("uptime", t.getState().toString());
                p.put("items", 1);
                p.put("status", t.isAlive() ? "Running" : "Stopped");
                processes.add(p);
                if (processes.size() >= 10) break;
            }
        }
        if (processes.isEmpty()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", "ECOS-Gateway-Main");
            p.put("type", "service");
            p.put("uptime", "active");
            p.put("items", 1);
            p.put("status", "Running");
            processes.add(p);
        }
        d.put("processes", processes);

        return ApiResponse.success(d);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> h = new LinkedHashMap<>();
        Map<String, Object> db = new LinkedHashMap<>();

        try {
            if (monitorService.isDbUp()) {
                db.put("status", "UP");
            } else {
                db.put("status", "DOWN");
            }
        } catch (Exception e) {
            db.put("status", "DOWN");
            db.put("error", e.getMessage());
        }

        h.put("database", db);
        h.put("uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
        h.put("version", "Phase4-M1");

        String overallStatus = "UP".equals(db.get("status")) ? "UP" : "DEGRADED";
        h.put("status", overallStatus);

        return ApiResponse.success(h);
    }

    @GetMapping("/alerts")
    public ApiResponse<Map<String, Object>> alertStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 按级别统计
        List<Map<String, Object>> byLevel = monitorService.queryAlertsTodayByLevel();
        stats.put("today_by_level", byLevel);

        // 总览
        Integer total = monitorService.countTotalAlerts();
        Integer open = monitorService.countActiveAlerts();
        stats.put("total", total != null ? total : 0);
        stats.put("open", open != null ? open : 0);

        return ApiResponse.success(stats);
    }
}
