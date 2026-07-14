/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from "react";
import {
  Gauge,
  Settings,
  Cpu,
  Database,
  Activity,
  RefreshCw,
  AlertTriangle,
  CheckCircle2,
  LineChart,
  Terminal,
  Layers,
  FileCheck,
  Server,
  Zap,
  Radio,
  BarChart3,
  Circle,
  Send,
} from "lucide-react";
import { useLanguage } from "../../../components/LanguageContext";
import { useTheme } from "../../../components/ThemeContext";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import {
  fetchMonitoringDashboard,
  runSystemDiagnostics,
  MonitoringKpi,
  MonitoringChartPoint,
  MonitoringProcess,
  MonitoringAlert,
} from "../../../api";
import { ICON_MAP } from "../helpers";

// ── Basic Monitoring Tab ────────────────────────────────────
export default function BasicMonitoringTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  const tl = (zh: string, en: string) => locale === "zh" ? zh : en;

  const [isRefreshing, setIsRefreshing] = useState(false);
  const [diagnosticResult, setDiagnosticResult] = useState<string | null>(null);
  const [diagnosticError, setDiagnosticError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const [systemMetrics, setSystemMetrics] = useState<MonitoringKpi[]>([]);
  const [chartData, setChartData] = useState<MonitoringChartPoint[]>([]);
  const [processes, setProcesses] = useState<MonitoringProcess[]>([]);
  const [alerts, setAlerts] = useState<MonitoringAlert[]>([]);

  const loadData = async () => {
    setLoading(true);
    try {
      const dash = await fetchMonitoringDashboard();
      setSystemMetrics(Array.isArray(dash.systemMetrics) ? dash.systemMetrics : []);
      setChartData(Array.isArray(dash.chartData) ? dash.chartData : []);
      setProcesses(Array.isArray(dash.processes) ? dash.processes : []);
      setAlerts(Array.isArray(dash.alerts) ? dash.alerts : []);
    } catch {
      // Graceful empty state
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const runDiagnostics = async () => {
    setIsRefreshing(true);
    setDiagnosticResult(null);
    setDiagnosticError(null);
    try {
      const result = await runSystemDiagnostics();
      const formatted = [
        `${tl("状态", "Status")}: ${result.status}`,
        `${tl("数据库", "DB")}: ${result.database.status}`,
        `${tl("运行时间", "Uptime")}: ${Math.round(result.uptime_ms / 1000)}s`,
        `${tl("版本", "Version")}: ${result.version}`,
      ].join(" | ");
      setDiagnosticResult(formatted);
    } catch (e: any) {
      setDiagnosticError(e.message || tl("诊断服务不可用。", "Diagnostics service unavailable."));
    } finally {
      setIsRefreshing(false);
    }
  };

  const chartLines = chartData.length > 0
    ? Object.keys(chartData[0]).filter(k => k !== "time")
    : [];

  const isEmpty = !loading && systemMetrics.length === 0 && chartData.length === 0 && processes.length === 0 && alerts.length === 0;

  return (
    <div className="flex-grow overflow-y-auto p-6 font-sans">
      <div className="max-w-7xl mx-auto space-y-6">
        
        {/* Title and Controls */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h1 className="text-xl font-bold tracking-tight">{tl("系统监控", "System Monitoring")}</h1>
            <p className={`text-xs ${styles.cardTextMuted} mt-1`}>
              {tl("实时系统指标、进程与告警概览", "Real-time system metrics, processes & alerts overview")}
            </p>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <button 
              onClick={loadData}
              disabled={loading}
              className={`flex items-center gap-1.5 px-3 py-1.5 border border-slate-300 dark:border-slate-600 text-slate-500 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer disabled:opacity-50 text-xs font-bold rounded-lg transition h-8`}
              title={tl("刷新", "Refresh")}
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
              <span>{tl("刷新", "Refresh")}</span>
            </button>
            <button 
              onClick={runDiagnostics}
              disabled={isRefreshing}
              className={`flex items-center gap-1.5 px-3 py-1.5 border border-indigo-500 text-indigo-500 hover:bg-indigo-500/10 cursor-pointer disabled:opacity-50 text-xs font-bold rounded-lg transition h-8`}
            >
              <FileCheck className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />
              <span>{tl("运行诊断", "Run Diagnostics")}</span>
            </button>
          </div>
        </div>

        {/* Loading state */}
        {loading && (
          <div className="flex items-center justify-center py-16">
            <div className="flex flex-col items-center gap-3">
              <RefreshCw className="w-8 h-8 text-indigo-500 animate-spin" />
              <p className="text-sm text-slate-400">{tl("加载监控数据...", "Loading monitoring data...")}</p>
            </div>
          </div>
        )}

        {/* Diagnosis Toast — error */}
        {diagnosticError && (
          <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-lg flex items-start gap-3 animate-fade-in text-red-600 dark:text-red-400">
            <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
            <div className="text-xs font-mono font-medium">{diagnosticError}</div>
          </div>
        )}

        {/* Diagnosis Toast — success */}
        {diagnosticResult && (
          <div className="p-4 bg-indigo-500/10 border border-indigo-500/20 rounded-lg flex items-start gap-3 animate-fade-in text-indigo-600 dark:text-indigo-400">
            <FileCheck className="w-5 h-5 shrink-0 mt-0.5" />
            <div className="text-xs font-mono font-medium">
              <span className="font-extrabold text-indigo-500 block mb-1">C2EOS INTERNAL COGNITIVE TELEMETRY:</span> 
              {diagnosticResult}
            </div>
          </div>
        )}

        {/* Empty state */}
        {isEmpty && !loading && (
          <div className="border border-dashed border-slate-300 dark:border-slate-700 rounded-xl p-12 flex flex-col items-center justify-center text-center">
            <div className="p-4 bg-slate-100 dark:bg-slate-800 rounded-full mb-4">
              <BarChart3 className="w-10 h-10 text-slate-400" />
            </div>
            <h3 className="font-bold text-sm text-slate-500 dark:text-slate-400 mb-2">
              {tl("实时监控数据尚未就绪", "Real-time monitoring data not ready")}
            </h3>
            <p className="text-xs text-slate-400 max-w-md leading-relaxed">
              {tl("实时监控数据尚未就绪。后台服务连接后，系统指标、图表和告警将在此展示。点击刷新按钮重试。", "Real-time monitoring data is not yet available. Once the backend service is connected, system metrics, charts, and alerts will appear here. Click the refresh button to retry.")}
            </p>
          </div>
        )}

        {/* Core KPI cards */}
        {systemMetrics.length > 0 && (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {systemMetrics.map((met) => {
              const Icon = ICON_MAP[met.icon] || Gauge;
              return (
                <div key={met.label} className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-4 shadow-3xs flex items-start gap-3`}>
                  <div className={`p-2 bg-black/5 dark:bg-white/5 rounded-lg shrink-0 ${met.color || "text-indigo-500"}`}>
                    <Icon className="w-5 h-5" />
                  </div>
                  <div className="flex-grow min-w-0">
                    <div className="text-[10px] font-mono uppercase tracking-wider text-slate-400 font-bold select-none truncate">
                      {met.label}
                    </div>
                    <div className="text-lg font-extrabold mt-1 truncate">{met.value}</div>
                    <div className="text-[9.5px] text-slate-400 truncate mt-0.5">{met.desc}</div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Real-time Chart & Alerts */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          
          <div className="lg:col-span-8 space-y-6">
            {chartData.length > 0 && (
              <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-6 shadow-2xs`}>
                <div className="flex items-center gap-2 mb-4">
                  <Radio className="w-4 h-4 text-indigo-500 shrink-0" />
                  <h3 className="font-bold text-sm">{tl("系统指标", "System Metrics")}</h3>
                </div>
                <div className="h-64">
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={chartData}>
                      <defs>
                        {chartLines.map((key, i) => (
                          <linearGradient key={key} id={`color${key}`} x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor={i === 0 ? "#818CF8" : "#34D399"} stopOpacity={0.4} />
                            <stop offset="95%" stopColor={i === 0 ? "#818CF8" : "#34D399"} stopOpacity={0.0} />
                          </linearGradient>
                        ))}
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" opacity={0.1} />
                      <XAxis dataKey="time" tick={{ fontSize: 10 }} stroke="#888888" />
                      <YAxis tick={{ fontSize: 10 }} stroke="#888888" />
                      <Tooltip />
                      {chartLines.map((key, i) => (
                        <Area
                          key={key}
                          type="monotone"
                          dataKey={key}
                          stroke={i === 0 ? "#818CF8" : "#34D399"}
                          fillOpacity={1}
                          fill={`url(#color${key})`}
                          strokeWidth={2}
                        />
                      ))}
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              </div>
            )}

            {processes.length > 0 && (
              <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-6 shadow-2xs`}>
                <div className="flex items-center gap-2 mb-4">
                  <Activity className="w-4 h-4 text-indigo-500 shrink-0" />
                  <h3 className="font-bold text-sm">{tl("进程监控", "Process Monitor")}</h3>
                </div>
                <div className="overflow-x-auto rounded border border-slate-200 dark:border-slate-800 text-xs">
                  <table className="w-full border-collapse">
                    <thead className="bg-slate-800 text-slate-200 font-mono text-[9px] uppercase tracking-wider">
                      <tr>
                        <th className="p-2 text-left">{tl("进程", "Job")}</th>
                        <th className="p-2 text-left">{tl("类型", "Type")}</th>
                        <th className="p-2 text-left">{tl("运行时间", "Uptime")}</th>
                        <th className="p-2 text-right">{tl("计数", "Count")}</th>
                      </tr>
                    </thead>
                    <tbody className="font-mono">
                      {processes.map((ap) => (
                        <tr key={ap.name} className="border-b hover:bg-indigo-500/5 odd:bg-black/[0.01] dark:even:bg-[#111827]">
                          <td className="p-2 font-bold flex items-center gap-1.5">
                            <span className={`w-1.5 h-1.5 rounded-full animate-pulse shrink-0 ${ap.status === "Running" ? "bg-emerald-500" : "bg-amber-500"}`}></span>
                            <span className="truncate max-w-[240px]">{ap.name}</span>
                          </td>
                          <td className="p-2 truncate">{ap.type}</td>
                          <td className="p-2">{ap.uptime}</td>
                          <td className="p-2 text-right text-indigo-500 font-bold">{ap.items}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>

          <div className="lg:col-span-4 space-y-6">
            {alerts.length > 0 && (
              <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-4 shadow-3xs space-y-4`}>
                <h4 className="text-xs font-extrabold uppercase font-mono tracking-wider text-slate-400 flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-amber-500 shrink-0" /> {tl("最近告警", "Recent Alerts")}
                </h4>
                <div className="space-y-3">
                  {alerts.map((al, idx) => (
                    <div key={idx} className="p-3 bg-black/5 dark:bg-white/5 rounded text-[11px] leading-normal font-mono border-l-2 border-l-amber-500 border border-slate-200 dark:border-slate-800">
                      <div className="flex items-center justify-between font-bold text-slate-400">
                        <span>[{al.level}]</span>
                        <span>{al.time}</span>
                      </div>
                      <div className="font-semibold text-slate-700 dark:text-slate-300 mt-1">{al.module}</div>
                      <p className="text-slate-500 dark:text-slate-400 mt-0.5">{al.message}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

        </div>

      </div>
    </div>
  );
}
