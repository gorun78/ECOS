package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 运行监控面（{@code /api/v1/ecos/monitoring}）：仪表盘与系统自检。
 *
 * <p><b>#147CL（DoD DI-2 第十六批）</b>：响应由自建 Map 改 VO——仪表盘
 * {@link DashboardVO}（4 键）、指标/曲线点/进程/告警各 5/4/5/4 键、自检
 * {@link DiagnosticsVO}（3 键）与条目 {@link DiagnosticItemVO}（3 键），键集与键序逐字不变。
 * 数据来源与取值口径零改动（含 {@code getDiskInfo} 失败回退 {@code "45%"} 的旧兜底）。</p>
 */
@RestController
@RequestMapping("/api/v1/ecos/monitoring")
public class MonitoringController {

    private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);

    @GetMapping("/dashboard")
    public ApiResponse<DashboardVO> dashboard() {
        try {
            return ApiResponse.success(new DashboardVO(buildSystemMetrics(), buildChartData(),
                    buildProcesses(), buildAlerts()));
        } catch (Throwable e) {
            log.error("获取监控仪表盘数据失败", e);
            return ApiResponse.internalError("获取仪表盘数据失败");
        }
    }

    @PostMapping("/diagnostics")
    public ApiResponse<DiagnosticsVO> diagnostics() {
        try {
            List<DiagnosticItemVO> items = new ArrayList<>();
            int passCount = 0;
            int failCount = 0;

            // 1. 数据库连接诊断
            DiagnosticItemVO dbCheck = diagnoseDatabase();
            items.add(dbCheck);
            if ("PASS".equals(dbCheck.status())) passCount++; else failCount++;

            // 2. JVM状态诊断
            DiagnosticItemVO jvmCheck = diagnoseJvm();
            items.add(jvmCheck);
            if ("PASS".equals(jvmCheck.status())) passCount++; else failCount++;

            // 3. 文件系统诊断
            DiagnosticItemVO fsCheck = diagnoseFileSystem();
            items.add(fsCheck);
            if ("PASS".equals(fsCheck.status())) passCount++; else failCount++;

            return ApiResponse.success(new DiagnosticsVO(
                    "系统诊断完成：" + passCount + "项通过，" + failCount + "项失败",
                    items,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        } catch (Throwable e) {
            log.error("系统诊断失败", e);
            return ApiResponse.internalError("诊断失败");
        }
    }

    // ─── Dashboard helper methods ──────────────────────

    private List<MetricVO> buildSystemMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long totalMem = runtime.totalMemory() / (1024 * 1024);
        long freeMem = runtime.freeMemory() / (1024 * 1024);
        long usedMem = totalMem - freeMem;
        long maxMem = runtime.maxMemory() / (1024 * 1024);
        int activeThreads = Thread.activeCount();
        int cpuCores = runtime.availableProcessors();

        List<MetricVO> metrics = new ArrayList<>();

        // CPU
        metrics.add(new MetricVO("CPU使用率", cpuCores + "核", cpuCores + "核", "Cpu", "#3B82F6"));

        // 内存使用（系统层面用JVM总内存估算）
        metrics.add(new MetricVO("内存使用", usedMem + "MB/" + maxMem + "MB", "JVM Max", "Zap", "#10B981"));

        // 磁盘使用（从根分区估算）
        metrics.add(new MetricVO("磁盘使用", getDiskInfo(), "/", "Database", "#F59E0B"));

        // JVM堆内存
        metrics.add(new MetricVO("JVM堆内存", usedMem + "MB/" + maxMem + "MB", "Young+Old", "Server", "#8B5CF6"));

        // 活跃线程
        int peakThreads = getPeakThreadCount();
        metrics.add(new MetricVO("活跃线程", String.valueOf(activeThreads), "峰值" + peakThreads, "Activity", "#EC4899"));

        // 数据库连接（静态示例）
        metrics.add(new MetricVO("数据库连接", "5/20", "HikariCP", "Radio", "#14B8A6"));

        return metrics;
    }

    private List<ChartPointVO> buildChartData() {
        List<ChartPointVO> chartData = new ArrayList<>();
        String[] times = {"00:00", "04:00", "08:00", "12:00", "16:00", "20:00"};
        int[] cpuVals = {15, 8, 35, 28, 32, 18};
        int[] memVals = {42, 38, 55, 50, 52, 44};
        int[] reqVals = {120, 45, 380, 290, 340, 160};

        for (int i = 0; i < times.length; i++) {
            chartData.add(new ChartPointVO(times[i], cpuVals[i], memVals[i], reqVals[i]));
        }
        return chartData;
    }

    private List<ProcessVO> buildProcesses() {
        List<ProcessVO> processes = new ArrayList<>();

        // SysMan进程
        processes.add(new ProcessVO("SysManApplication", "RUNNING",
                ProcessHandle.current().pid(), "12%",
                (Runtime.getRuntime().totalMemory() / (1024 * 1024)) + "MB"));

        // PostgreSQL（静态）
        processes.add(new ProcessVO("PostgreSQL", "RUNNING", 6789L, "3%", "256MB"));

        // Hermes Engine（静态）
        processes.add(new ProcessVO("Hermes Engine", "IDLE", 0L, "0%", "0MB"));

        return processes;
    }

    private List<AlertVO> buildAlerts() {
        List<AlertVO> alerts = new ArrayList<>();
        alerts.add(new AlertVO("alert-1", "数据库连接池使用率超过50%", "warning", "2026-06-19T01:30:00"));
        alerts.add(new AlertVO("alert-2", "所有服务运行正常", "info", "2026-06-19T01:25:00"));
        return alerts;
    }

    // ─── Diagnostics helper methods ────────────────────

    private DiagnosticItemVO diagnoseDatabase() {
        // 数据库连接可用性通过MonitoringQueryService检查，此处返回静态通过结果
        return new DiagnosticItemVO("数据库连接", "PASS", "PostgreSQL 14 响应正常 (15ms)");
    }

    private DiagnosticItemVO diagnoseJvm() {
        Runtime runtime = Runtime.getRuntime();
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMem = runtime.maxMemory() / (1024 * 1024);
        return new DiagnosticItemVO("JVM状态", "PASS", "堆内存使用: " + usedMem + "MB/" + maxMem + "MB");
    }

    private DiagnosticItemVO diagnoseFileSystem() {
        File root = new File("/");
        long usableSpace = root.getUsableSpace() / (1024 * 1024 * 1024);
        long totalSpace = root.getTotalSpace() / (1024 * 1024 * 1024);
        int usedPercent = (int) ((totalSpace - usableSpace) * 100 / Math.max(totalSpace, 1));
        return new DiagnosticItemVO("文件系统", usableSpace > 1 ? "PASS" : "WARN",
                "磁盘可用空间: " + (100 - usedPercent) + "% (" + usableSpace + "GB/" + totalSpace + "GB)");
    }

    // ─── Utility methods ───────────────────────────────

    private String getDiskInfo() {
        try {
            File root = new File("/");
            long total = root.getTotalSpace() / (1024 * 1024 * 1024);
            long usable = root.getUsableSpace() / (1024 * 1024 * 1024);
            int usedPercent = (int) ((total - usable) * 100 / Math.max(total, 1));
            return usedPercent + "%";
        } catch (Exception e) {
            return "45%";
        }
    }

    private int getPeakThreadCount() {
        try {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            return threadMXBean.getPeakThreadCount();
        } catch (Exception e) {
            return 42;
        }
    }

    // ── 响应 VO（#147CL：键集与键序＝旧 Map） ──────────────────

    /** 仪表盘（4 键）。 */
    public record DashboardVO(List<MetricVO> systemMetrics, List<ChartPointVO> chartData,
                              List<ProcessVO> processes, List<AlertVO> alerts) {
    }

    /** 指标卡片（5 键 label/value/desc/icon/color）。 */
    public record MetricVO(String label, String value, String desc, String icon, String color) {
    }

    /** 曲线点（4 键 time/cpu/memory/requests）。 */
    public record ChartPointVO(String time, int cpu, int memory, int requests) {
    }

    /** 进程（5 键 name/status/pid/cpu/memory）。 */
    public record ProcessVO(String name, String status, long pid, String cpu, String memory) {
    }

    /** 告警（4 键 id/message/type/time）。 */
    public record AlertVO(String id, String message, String type, String time) {
    }

    /** 自检结果（3 键 summary/items/timestamp）。 */
    public record DiagnosticsVO(String summary, List<DiagnosticItemVO> items, String timestamp) {
    }

    /** 自检条目（3 键 name/status/detail）。 */
    public record DiagnosticItemVO(String name, String status, String detail) {
    }
}
