/**
 * CEO经营仪表盘 — 项目/合同/产值/目标 + 经营诊断
 * Connected via api.ts → /api/v1/ecos/biz/dashboard
 */
import React, { useState, useEffect, useCallback } from "react";
import { Briefcase, FileText, TrendingUp, Building2, Users, RefreshCw, AlertCircle, Loader2, Stethoscope } from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { fetchBizDashboard, callDiagnosticAgent } from "../api";
import DiagnosticPanel from "../components/DiagnosticPanel";

interface BizData {
  departments?: { id: string; name: string; manager: string; parent_id?: string }[];
  projectStats?: { total: number; inProgress: number; completed: number; planning: number; totalAmount: number };
  contractStats?: { total: number; totalValue: string; incomeCount: number; expenseCount: number };
  metrics?: { id: string; dept_id: string; metric_type: string; metric_value: number; metric_month: string }[];
  targets?: { id: string; dept_id: string; target_type: string; target_value: number; target_year: number }[];
}

export default function BizDashboard() {
  const { t } = useLanguage();
  const [data, setData] = useState<BizData | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [diagnosticOpen, setDiagnosticOpen] = useState(false);
  const [diagnosticResult, setDiagnosticResult] = useState<any>(null);
  const [diagnosing, setDiagnosing] = useState(false);

  const loadData = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError("");
    try {
      const raw = await fetchBizDashboard();
      setData(raw);
    } catch (e: any) {
      setError(e.message || "加载失败");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  const runDiagnostic = async () => {
    setDiagnosing(true);
    setDiagnosticOpen(true);
    try {
      const result = await callDiagnosticAgent("浙北路桥项目进度滞后的根因是什么？");
      setDiagnosticResult(result);
    } catch (e: any) {
      setDiagnosticResult({ error: e.message || "诊断失败" });
    } finally {
      setDiagnosing(false);
    }
  };

  if (loading) return (
    <div className="h-full flex items-center justify-center">
      <div className="text-center space-y-3">
        <Loader2 className="w-8 h-8 text-[#3B82F6] animate-spin mx-auto" />
        <p className="text-sm text-slate-400">加载仪表盘数据...</p>
      </div>
    </div>
  );
  if (error) return (
    <div className="h-full flex items-center justify-center">
      <div className="text-center max-w-sm">
        <AlertCircle className="w-10 h-10 text-red-400 mx-auto mb-2" />
        <p className="text-sm text-slate-400">{error}</p>
        <button onClick={() => loadData()} className="mt-3 px-4 py-2 rounded bg-blue-500 text-white text-sm hover:bg-blue-600 transition">重试</button>
      </div>
    </div>
  );

  const deptCount = data?.departments?.length || 0;
  const projects = data?.projectStats;
  const contracts = data?.contractStats;

  // Calculate CEO KPIs from metrics
  const latestMetrics = data?.metrics || [];
  const revenueMetrics = latestMetrics.filter(m => m.metric_type === 'revenue');
  const latestRevenue = revenueMetrics.length > 0 ? revenueMetrics[revenueMetrics.length - 1].metric_value : 0;
  const profitMetrics = latestMetrics.filter(m => m.metric_type === 'profit');
  const latestProfit = profitMetrics.length > 0 ? profitMetrics[profitMetrics.length - 1].metric_value : 0;
  const collectionMetrics = latestMetrics.filter(m => m.metric_type === 'collection_rate');
  const latestCollection = collectionMetrics.length > 0 ? collectionMetrics[collectionMetrics.length - 1].metric_value : 0;

  // Target comparison
  const targets = data?.targets || [];
  const revenueTarget = targets.find(t => t.target_type === 'revenue');
  const profitTarget = targets.find(t => t.target_type === 'profit');
  const collectionTarget = targets.find(t => t.target_type === 'collection_rate');

  const revenuePct = revenueTarget && revenueTarget.target_value > 0
    ? Math.round((latestRevenue / (revenueTarget.target_value / 12)) * 100) : 0;
  const profitPct = profitTarget && profitTarget.target_value > 0
    ? Math.round((latestProfit / (profitTarget.target_value / 12)) * 100) : 0;

  // Check for critical deviations (red highlights)
  const hasCriticalDeviation = (revenuePct < 80 || profitPct < 80);

  return (
    <div className="flex-1 overflow-auto p-6 lg:p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 dark:text-slate-100">经营仪表盘</h1>
          <p className="text-sm text-slate-400 mt-1">CEO晨会 · 项目型企业全景</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={runDiagnostic}
            disabled={diagnosing}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition ${
              hasCriticalDeviation
                ? "bg-red-500 text-white hover:bg-red-600 animate-pulse"
                : "bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700"
            } disabled:opacity-50`}
          >
            <Stethoscope className={`w-4 h-4 ${diagnosing ? "animate-spin" : ""}`} />
            {hasCriticalDeviation ? "⚠️ 经营诊断" : "经营诊断"}
          </button>
          <button
            onClick={() => loadData(true)}
            disabled={refreshing}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-sm text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 transition disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${refreshing ? "animate-spin" : ""}`} />
            刷新
          </button>
        </div>
      </div>

      {/* KPI Cards — CEO关注的4个指标 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <KpiCard icon={Building2} label="部门数" value={deptCount} color="blue" />
        <KpiCard
          icon={Briefcase} label="项目总数" value={projects?.total || 0}
          sub={`在建 ${projects?.inProgress || 0} · 合同总额 ¥${((projects?.totalAmount || 0) / 100000000).toFixed(2)}亿`}
          color="indigo"
        />
        <KpiCard
          icon={TrendingUp} label="月营收达标率"
          value={`${revenuePct}%`}
          sub={`月营收 ¥${(latestRevenue / 10000).toFixed(0)}万`}
          color={revenuePct < 80 ? "red" : "emerald"}
        />
        <KpiCard
          icon={FileText} label="回款率"
          value={`${latestCollection.toFixed(0)}%`}
          sub={collectionTarget ? `目标 ${collectionTarget.target_value}%` : ""}
          color={latestCollection < (collectionTarget?.target_value || 85) ? "red" : "emerald"}
        />
      </div>

      {/* Deviation Alert */}
      {hasCriticalDeviation && (
        <div className="mb-6 p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl flex items-start gap-3">
          <AlertCircle className="w-5 h-5 text-red-500 mt-0.5 shrink-0" />
          <div>
            <p className="text-sm font-semibold text-red-700 dark:text-red-400">经营偏差告警</p>
            <p className="text-xs text-red-600 dark:text-red-400 mt-1">
              营收月达标率{revenuePct}%（低于80%）· 利润月达标率{profitPct}% · 供应商华强钢构准时率67%
            </p>
            <button onClick={runDiagnostic} className="mt-2 text-xs font-medium text-red-600 dark:text-red-400 underline hover:text-red-800">
              点击进行AI诊断 →
            </button>
          </div>
        </div>
      )}

      {/* Department + Project Overview */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        {/* Departments */}
        {deptCount > 0 && (
          <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 p-5">
            <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100 mb-4 flex items-center gap-2">
              <Users className="w-5 h-5 text-blue-500" />
              组织机构 ({deptCount})
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {data?.departments?.slice(0, 6).map((dept) => (
                <div key={dept.id} className="p-3 rounded-lg border border-slate-100 dark:border-slate-700 bg-slate-50 dark:bg-slate-900/50">
                  <p className="text-sm font-medium text-slate-800 dark:text-slate-200">{dept.name}</p>
                  <p className="text-xs text-slate-400 mt-1">{dept.manager || "—"}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Project Stats Card */}
        {projects && (
          <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 p-5">
            <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100 mb-4 flex items-center gap-2">
              <Briefcase className="w-5 h-5 text-indigo-500" />
              项目概览
            </h2>
            <div className="space-y-3">
              <StatRow label="项目总数" value={projects.total} />
              <StatRow label="在建项目" value={projects.inProgress} highlight />
              <StatRow label="已完成" value={projects.completed} />
              <StatRow label="规划中" value={projects.planning} />
              <StatRow label="合同总额" value={`¥${(projects.totalAmount / 100000000).toFixed(2)}亿`} />
            </div>
          </div>
        )}
      </div>

      {/* Target vs Actual */}
      {targets.length > 0 && (
        <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 p-5 mb-6">
          <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100 mb-4 flex items-center gap-2">
            <TrendingUp className="w-5 h-5 text-amber-500" />
            年度目标追踪
          </h2>
          <div className="space-y-3">
            {targets.map((t) => {
              const actual = t.target_type === 'revenue' ? latestRevenue
                : t.target_type === 'profit' ? latestProfit
                : t.target_type === 'collection_rate' ? latestCollection : 0;
              const pct = t.target_value > 0 ? Math.round((actual / t.target_value) * 100) : 0;
              const atRisk = pct < 80;
              return (
                <div key={t.id} className="flex items-center gap-4">
                  <span className="text-sm text-slate-600 dark:text-slate-400 w-32">{t.target_type === 'revenue' ? '营收' : t.target_type === 'profit' ? '利润' : '回款率'}</span>
                  <div className="flex-1 h-3 bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all ${atRisk ? 'bg-red-500' : 'bg-emerald-500'}`}
                      style={{ width: `${Math.min(pct, 100)}%` }}
                    />
                  </div>
                  <span className={`text-sm font-mono ${atRisk ? 'text-red-500' : 'text-emerald-600'}`}>
                    {pct}%
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Diagnostic Panel Modal */}
      {diagnosticOpen && (
        <DiagnosticPanel
          result={diagnosticResult}
          loading={diagnosing}
          onClose={() => setDiagnosticOpen(false)}
          onRetry={runDiagnostic}
        />
      )}
    </div>
  );
}

function StatRow({ label, value, highlight }: { label: string; value: number | string; highlight?: boolean }) {
  return (
    <div className="flex items-center justify-between py-1.5">
      <span className="text-sm text-slate-500 dark:text-slate-400">{label}</span>
      <span className={`text-sm font-semibold ${highlight ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-700 dark:text-slate-300'}`}>
        {value}
      </span>
    </div>
  );
}

function KpiCard({ icon: Icon, label, value, sub, color }: {
  icon: any; label: string; value: number | string; sub?: string; color: string;
}) {
  const colors: Record<string, string> = {
    blue: "bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400",
    indigo: "bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400",
    emerald: "bg-emerald-50 dark:bg-emerald-900/20 text-emerald-600 dark:text-emerald-400",
    amber: "bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400",
    red: "bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400",
  };
  return (
    <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 p-5">
      <div className="flex items-center gap-3">
        <div className={`p-2.5 rounded-lg ${colors[color] || colors.blue}`}>
          <Icon className="w-5 h-5" />
        </div>
        <div>
          <p className="text-xs text-slate-400">{label}</p>
          <p className="text-2xl font-bold text-slate-800 dark:text-slate-100">{value}</p>
          {sub && <p className="text-xs text-slate-400 mt-0.5">{sub}</p>}
        </div>
      </div>
    </div>
  );
}
