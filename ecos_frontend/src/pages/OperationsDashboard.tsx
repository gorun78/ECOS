/**
 * OperationsDashboard — 高速信科产值分配看板
 * 调用 /api/v1/gsxk/biz/dashboard 获取基础数据
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  TrendingUp, Briefcase, FileCheck, Wrench,
  RefreshCw, AlertCircle, Loader2, Building2,
  BarChart3, PieChart, Activity
} from "lucide-react";
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, PieChart as RePieChart, Pie, Cell, Legend,
  LineChart, Line
} from "recharts";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { fetchBizDashboard } from "../api";

// ── Types ──────────────────────────────────────────────
interface DashboardData {
  maintenanceOutput?: number | string;
  projectCount?: number;
  contractFulfillmentRate?: number | string;
  equipmentHealthRate?: number | string;
  departments?: { name: string; output?: number; value?: number; headcount?: number }[];
  monthlyTrend?: { month: string; value: number; projects: number }[];
  contractTrend?: { month: string; rate: number }[];
}

// ── Colors ─────────────────────────────────────────────
const PIE_COLORS = ["#6366F1", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#06B6D4", "#F97316", "#84CC16"];

const CHART_TICK_STYLE = { fontSize: 11, fill: "#94A3B8" };

// ── Helpers ────────────────────────────────────────────
function fmtMoney(v: number | string | undefined | null): string {
  if (v == null) return "—";
  const n = Number(v);
  if (isNaN(n)) return String(v);
  if (n >= 100000000) return `${(n / 100000000).toFixed(2)} 亿`;
  if (n >= 10000) return `${(n / 10000).toFixed(0)} 万`;
  return n.toLocaleString();
}

function fmtPercent(v: number | string | undefined | null): string {
  if (v == null) return "—";
  const n = Number(v);
  if (isNaN(n)) return String(v);
  return `${n.toFixed(1)}%`;
}

// ── Component ──────────────────────────────────────────
export default function OperationsDashboard() {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const raw = await fetchBizDashboard();

      setData({
        maintenanceOutput: raw.maintenanceOutput ?? raw.maintenance_output ?? 0,
        projectCount: raw.projectCount ?? raw.project_count ?? raw.projectStats?.total ?? 0,
        contractFulfillmentRate: raw.contractFulfillmentRate ?? raw.contract_fulfillment_rate ?? raw.contractStats?.fulfillmentRate ?? 0,
        equipmentHealthRate: raw.equipmentHealthRate ?? raw.equipment_health_rate ?? 0,
        departments: raw.departments || [],
        monthlyTrend: raw.monthlyTrend || raw.monthly_trend || [],
        contractTrend: raw.contractTrend || raw.contract_trend || [],
      });
    } catch (e: any) {
      setError(e.message || (locale === "zh" ? "加载看板数据失败" : "Failed to load dashboard"));
    } finally {
      setLoading(false);
    }
  }, [locale]);

  useEffect(() => { loadData(); }, [loadData]);

  // ── Loading ──────────────────────────────────────────
  if (loading) {
    return (
      <div className="h-full flex items-center justify-center">
        <div className="text-center space-y-3">
          <Loader2 className="w-8 h-8 text-indigo-500 animate-spin mx-auto" />
          <p className="text-sm text-slate-400">
            {locale === "zh" ? "加载看板数据..." : "Loading dashboard..."}
          </p>
        </div>
      </div>
    );
  }

  // ── Error ────────────────────────────────────────────
  if (error && !data) {
    return (
      <div className="h-full flex items-center justify-center p-6">
        <div className="text-center max-w-sm">
          <AlertCircle className="w-10 h-10 text-red-400 mx-auto mb-3" />
          <p className="text-sm font-semibold text-red-600 mb-1">
            {locale === "zh" ? "看板数据加载失败" : "Dashboard load failed"}
          </p>
          <p className="text-xs text-slate-500 mb-4">{error}</p>
          <button
            onClick={loadData}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold rounded-lg transition"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            {locale === "zh" ? "重试" : "Retry"}
          </button>
        </div>
      </div>
    );
  }

  // ── Derived data ─────────────────────────────────────
  const depts = data?.departments || [];
  const deptChartData = depts.map((d, i) => ({
    name: d.name,
    value: d.output ?? d.value ?? 0,
    headcount: d.headcount ?? 0,
    fill: PIE_COLORS[i % PIE_COLORS.length],
  }));

  const monthlyTrend = data?.monthlyTrend || [];
  const contractTrend = data?.contractTrend || [];

  // ── Render ───────────────────────────────────────────
  return (
    <div className="h-full overflow-auto p-4 sm:p-6 space-y-5">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold flex items-center gap-2">
            <BarChart3 className="w-5 h-5 text-indigo-500" />
            {locale === "zh" ? "产值分配看板" : "Operations Dashboard"}
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            {locale === "zh" ? "高速信科养护产值、工程项目与设备运营总览" : "GSXK maintenance output, projects & equipment overview"}
          </p>
        </div>
        <button
          onClick={loadData}
          disabled={loading}
          className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-lg border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition disabled:opacity-50"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {loading ? (locale === "zh" ? "刷新中..." : "Refreshing...") : (locale === "zh" ? "刷新" : "Refresh")}
        </button>
      </div>

      {/* KPI Cards (4 tiles) */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <KpiTile
          icon={TrendingUp}
          label={locale === "zh" ? "养护产值" : "Maint. Output"}
          value={fmtMoney(data?.maintenanceOutput)}
          color="text-emerald-500"
          bg="bg-emerald-50 dark:bg-emerald-900/20"
          border="border-emerald-200 dark:border-emerald-800"
        />
        <KpiTile
          icon={Briefcase}
          label={locale === "zh" ? "工程项目数" : "Projects"}
          value={data?.projectCount ?? 0}
          color="text-blue-500"
          bg="bg-blue-50 dark:bg-blue-900/20"
          border="border-blue-200 dark:border-blue-800"
        />
        <KpiTile
          icon={FileCheck}
          label={locale === "zh" ? "合同履约率" : "Fulfillment Rate"}
          value={fmtPercent(data?.contractFulfillmentRate)}
          color="text-violet-500"
          bg="bg-violet-50 dark:bg-violet-900/20"
          border="border-violet-200 dark:border-violet-800"
        />
        <KpiTile
          icon={Wrench}
          label={locale === "zh" ? "设备完好率" : "Equipment Health"}
          value={fmtPercent(data?.equipmentHealthRate)}
          color="text-amber-500"
          bg="bg-amber-50 dark:bg-amber-900/20"
          border="border-amber-200 dark:border-amber-800"
        />
      </div>

      {/* Empty state */}
      {!depts.length && !monthlyTrend.length && !contractTrend.length && (
        <div className={`text-center py-16 rounded-xl border border-dashed ${styles.cardBorder}`}>
          <Building2 className="w-10 h-10 text-slate-300 dark:text-slate-600 mx-auto mb-3" />
          <p className="text-sm font-semibold text-slate-500">
            {locale === "zh" ? "暂无业务数据" : "No business data"}
          </p>
          <p className="text-xs text-slate-400 mt-1">
            {locale === "zh" ? "请确认后端服务已启动，或稍后刷新重试" : "Please verify backend is running, or refresh later"}
          </p>
        </div>
      )}

      {/* Charts row */}
      {(monthlyTrend.length > 0 || deptChartData.length > 0) && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {/* Monthly trend chart */}
          {monthlyTrend.length > 0 && (
            <div className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} p-4`}>
              <h3 className="text-sm font-bold mb-3 flex items-center gap-2">
                <Activity className="w-4 h-4 text-indigo-500" />
                {locale === "zh" ? "月度产值趋势" : "Monthly Output Trend"}
              </h3>
              <div className="h-[220px]">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={monthlyTrend} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" strokeOpacity={0.3} />
                    <XAxis dataKey="month" tick={CHART_TICK_STYLE} />
                    <YAxis tick={CHART_TICK_STYLE} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: "rgba(255,255,255,0.95)",
                        border: "1px solid #E2E8F0",
                        borderRadius: "8px",
                        fontSize: "12px",
                      }}
                    />
                    <Line
                      type="monotone"
                      dataKey="value"
                      name={locale === "zh" ? "产值" : "Output"}
                      stroke="#6366F1"
                      strokeWidth={2}
                      dot={{ r: 3, fill: "#6366F1" }}
                      activeDot={{ r: 5 }}
                    />
                    {monthlyTrend[0]?.projects !== undefined && (
                      <Line
                        type="monotone"
                        dataKey="projects"
                        name={locale === "zh" ? "项目数" : "Projects"}
                        stroke="#10B981"
                        strokeWidth={2}
                        dot={{ r: 3, fill: "#10B981" }}
                      />
                    )}
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* Department distribution pie chart */}
          {deptChartData.length > 0 && deptChartData.some(d => d.value > 0) && (
            <div className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} p-4`}>
              <h3 className="text-sm font-bold mb-3 flex items-center gap-2">
                <Building2 className="w-4 h-4 text-indigo-500" />
                {locale === "zh" ? "部门产值分布" : "Department Output Distribution"}
              </h3>
              <div className="h-[220px]">
                <ResponsiveContainer width="100%" height="100%">
                  <RePieChart>
                    <Pie
                      data={deptChartData}
                      cx="50%"
                      cy="50%"
                      innerRadius={50}
                      outerRadius={80}
                      paddingAngle={3}
                      dataKey="value"
                      nameKey="name"
                    >
                      {deptChartData.map((entry, idx) => (
                        <Cell key={idx} fill={entry.fill} />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{
                        backgroundColor: "rgba(255,255,255,0.95)",
                        border: "1px solid #E2E8F0",
                        borderRadius: "8px",
                        fontSize: "12px",
                      }}
                      formatter={(value: any) => [fmtMoney(value), locale === "zh" ? "产值" : "Output"]}
                    />
                    <Legend
                      wrapperStyle={{ fontSize: "11px" }}
                    />
                  </RePieChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Contract fulfillment rate trend */}
      {contractTrend.length > 0 && (
        <div className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} p-4`}>
          <h3 className="text-sm font-bold mb-3 flex items-center gap-2">
            <FileCheck className="w-4 h-4 text-indigo-500" />
            {locale === "zh" ? "合同履约率趋势" : "Contract Fulfillment Rate Trend"}
          </h3>
          <div className="h-[180px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={contractTrend} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" strokeOpacity={0.3} />
                <XAxis dataKey="month" tick={CHART_TICK_STYLE} />
                <YAxis tick={CHART_TICK_STYLE} domain={[0, 100]} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: "rgba(255,255,255,0.95)",
                    border: "1px solid #E2E8F0",
                    borderRadius: "8px",
                    fontSize: "12px",
                  }}
                  formatter={(value: any) => [`${value}%`, locale === "zh" ? "履约率" : "Rate"]}
                />
                <Bar
                  dataKey="rate"
                  name={locale === "zh" ? "履约率" : "Fulfillment Rate"}
                  fill="#8B5CF6"
                  radius={[4, 4, 0, 0]}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Department table */}
      {depts.length > 0 && (
        <div className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} overflow-hidden`}>
          <div className="px-4 py-3 border-b border-gray-100 dark:border-gray-800">
            <h3 className="text-sm font-bold flex items-center gap-2">
              <PieChart className="w-4 h-4 text-indigo-500" />
              {locale === "zh" ? "部门产值明细" : "Department Output Details"}
            </h3>
          </div>
          <table className="w-full text-xs">
            <thead>
              <tr className={`border-b ${styles.cardBorder}`}>
                <th className="text-left px-4 py-2.5 font-bold uppercase text-[10px] tracking-wider text-slate-500">
                  {locale === "zh" ? "部门" : "Department"}
                </th>
                <th className="text-right px-4 py-2.5 font-bold uppercase text-[10px] tracking-wider text-slate-500">
                  {locale === "zh" ? "产值" : "Output"}
                </th>
                <th className="text-right px-4 py-2.5 font-bold uppercase text-[10px] tracking-wider text-slate-500">
                  {locale === "zh" ? "占比" : "Share"}
                </th>
              </tr>
            </thead>
            <tbody>
              {depts.map((d, i) => {
                const val = d.output ?? d.value ?? 0;
                const totalVal = depts.reduce((s, x) => s + (x.output ?? x.value ?? 0), 0);
                const pct = totalVal > 0 ? (Number(val) / totalVal * 100) : 0;
                return (
                  <tr key={i} className={`border-b ${styles.cardBorder} hover:bg-black/5 dark:hover:bg-white/5 transition`}>
                    <td className="px-4 py-2.5">
                      <span className="flex items-center gap-2">
                        <span
                          className="w-2.5 h-2.5 rounded-full shrink-0"
                          style={{ backgroundColor: PIE_COLORS[i % PIE_COLORS.length] }}
                        />
                        {d.name}
                      </span>
                    </td>
                    <td className="text-right px-4 py-2.5 font-mono">{fmtMoney(val)}</td>
                    <td className="text-right px-4 py-2.5">
                      <div className="flex items-center justify-end gap-2">
                        <div className="w-16 h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                          <div
                            className="h-full rounded-full"
                            style={{
                              width: `${Math.min(100, pct)}%`,
                              backgroundColor: PIE_COLORS[i % PIE_COLORS.length],
                            }}
                          />
                        </div>
                        <span className="w-10 text-right font-mono">{pct.toFixed(1)}%</span>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Error banner */}
      {error && data && (
        <div className="flex items-center gap-2 px-4 py-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-xs text-red-700 dark:text-red-400">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
          <button onClick={loadData} className="ml-auto font-semibold underline hover:no-underline">
            {locale === "zh" ? "重试" : "Retry"}
          </button>
        </div>
      )}
    </div>
  );
}

// ── KPI Tile ────────────────────────────────────────────
function KpiTile({ icon: Icon, label, value, color, bg, border }: {
  icon: any; label: string; value: string | number; color: string; bg: string; border: string;
}) {
  return (
    <div className={`flex items-center gap-3 px-4 py-4 rounded-lg border ${border} ${bg}`}>
      <div className={`p-2 rounded-lg ${bg}`}>
        <Icon className={`w-6 h-6 ${color}`} />
      </div>
      <div>
        <div className="text-xl font-bold tracking-tight">{value}</div>
        <div className="text-[10px] text-slate-500 dark:text-slate-400 font-medium uppercase tracking-wider">
          {label}
        </div>
      </div>
    </div>
  );
}
