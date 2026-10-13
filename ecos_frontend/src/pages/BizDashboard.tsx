/**
 * CEO经营仪表盘 — 项目/合同/产值/目标 + 经营诊断
 * Connected via api.ts → /api/v1/ecos/biz/dashboard
 *
 * 2026-10-13 前端检视修复（F 组）：
 * - 删除硬编码假告警文案（"供应商华强钢构准时率67%"并非真实数据）
 * - 端点 404/5xx 时错误卡不再直排原始 Spring JSON，按真实错误分类 + 重试
 * - 整页中文硬编码 → t()；结构性裸色板 → theme token
 * - KPI 缺字段显示「无数据」而非 0；缺数据不再误触发"偏差告警/脉冲按钮"
 */
import React, { useState, useEffect, useCallback } from "react";
import { Briefcase, FileText, TrendingUp, Building2, Users, RefreshCw, AlertCircle, Loader2, Stethoscope } from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { fetchBizDashboard, callDiagnosticAgent, isNoAccessError, NetworkError } from "../api";
import DiagnosticPanel from "../components/DiagnosticPanel";

interface BizData {
  departments?: { id: string; name: string; manager: string; parent_id?: string }[];
  projectStats?: { total: number; inProgress: number; completed: number; planning: number; totalAmount: number };
  contractStats?: { total: number; totalValue: string; incomeCount: number; expenseCount: number };
  metrics?: { id: string; dept_id: string; metric_type: string; metric_value: number; metric_month: string }[];
  targets?: { id: string; dept_id: string; target_type: string; target_value: number; target_year: number }[];
}

/** 结构化错误（标题 + 已分类描述 + HTTP 状态），不直排原始响应体 */
interface StructuredError {
  title: string;
  detail: string;
  status?: number;
}

const targetTypeNameKey: Record<string, string> = {
  revenue: "biz.targetType.revenue",
  profit: "biz.targetType.profit",
  collection_rate: "biz.targetType.collection",
};

export default function BizDashboard() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [data, setData] = useState<BizData | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<StructuredError | null>(null);
  const [diagnosticOpen, setDiagnosticOpen] = useState(false);
  const [diagnosticResult, setDiagnosticResult] = useState<any>(null);
  const [diagnosing, setDiagnosing] = useState(false);

  const classifyError = useCallback((e: unknown): StructuredError => {
    const status = (e as { status?: number } | null)?.status;
    if (isNoAccessError(e)) {
      return { title: t("biz.error.title"), detail: t("biz.error.forbidden"), status: 403 };
    }
    if (e instanceof NetworkError) {
      return { title: t("biz.error.title"), detail: t("biz.error.network") };
    }
    if (typeof status === "number") {
      const detail = status === 404 ? t("biz.error.notFound") : t("biz.error.http", { status });
      return { title: t("biz.error.title"), detail, status };
    }
    return { title: t("biz.error.title"), detail: t("biz.error.unknown") };
  }, [t]);

  const loadData = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);
    try {
      const raw = await fetchBizDashboard();
      setData(raw);
    } catch (e) {
      setError(classifyError(e));
      setData(null);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [classifyError]);

  useEffect(() => { loadData(); }, [loadData]);

  const runDiagnostic = async () => {
    setDiagnosing(true);
    setDiagnosticOpen(true);
    try {
      const result = await callDiagnosticAgent(t("biz.diagnosticPrompt"));
      setDiagnosticResult(result);
    } catch (e) {
      // 不直排原始报文（可能是整段 Spring JSON），走同一错误分类
      setDiagnosticResult({ error: classifyError(e).detail });
    } finally {
      setDiagnosing(false);
    }
  };

  const noData = t("biz.noData");

  if (loading) return (
    <div className="h-full flex items-center justify-center">
      <div className="text-center space-y-3">
        <Loader2 className={`w-8 h-8 ${styles.accentText} animate-spin mx-auto`} />
        <p className={`text-sm ${styles.cardTextMuted}`}>{t("biz.loading")}</p>
      </div>
    </div>
  );
  if (error) return (
    <div className="h-full flex items-center justify-center p-6">
      <div className={`text-center max-w-sm p-6 rounded-xl border ${styles.cardBorder} ${styles.cardBg}`}>
        <AlertCircle className={`w-10 h-10 ${styles.dangerText} mx-auto mb-2`} />
        <p className={`text-sm font-semibold ${styles.dangerText}`}>{error.title}</p>
        <p className={`text-sm ${styles.cardTextMuted} mt-1`}>{error.detail}</p>
        {error.status !== undefined && (
          <p className={`text-[10px] font-mono uppercase tracking-wider ${styles.cardTextMuted} mt-2`}>HTTP {error.status}</p>
        )}
        <button type="button" onClick={() => loadData()} className={`mt-3 px-4 py-2 rounded ${styles.accentBg} text-white text-sm hover:opacity-90 transition`}>
          {t("biz.retry")}
        </button>
      </div>
    </div>
  );

  const deptCount = data?.departments?.length ?? null;
  const projects = data?.projectStats;
  const contracts = data?.contractStats;

  // CEO KPIs from metrics — 缺字段 = null（显示「无数据」），不以 0 冒充真实值
  const latestMetrics = data?.metrics || [];
  const revenueMetrics = latestMetrics.filter(m => m.metric_type === 'revenue');
  const latestRevenue: number | null = revenueMetrics.length > 0 ? revenueMetrics[revenueMetrics.length - 1].metric_value : null;
  const profitMetrics = latestMetrics.filter(m => m.metric_type === 'profit');
  const latestProfit: number | null = profitMetrics.length > 0 ? profitMetrics[profitMetrics.length - 1].metric_value : null;
  const collectionMetrics = latestMetrics.filter(m => m.metric_type === 'collection_rate');
  const latestCollection: number | null = collectionMetrics.length > 0 ? collectionMetrics[collectionMetrics.length - 1].metric_value : null;

  // Target comparison — 仅在指标与目标都存在时计算，缺数据不得判为"偏差"
  const targets = data?.targets || [];
  const revenueTarget = targets.find(t => t.target_type === 'revenue');
  const profitTarget = targets.find(t => t.target_type === 'profit');
  const collectionTarget = targets.find(t => t.target_type === 'collection_rate');

  const revenuePct: number | null =
    revenueTarget && revenueTarget.target_value > 0 && latestRevenue !== null
      ? Math.round((latestRevenue / (revenueTarget.target_value / 12)) * 100) : null;
  const profitPct: number | null =
    profitTarget && profitTarget.target_value > 0 && latestProfit !== null
      ? Math.round((latestProfit / (profitTarget.target_value / 12)) * 100) : null;

  // 偏差告警只在有真实指标时触发（旧逻辑缺数据时 0<80 恒告警 = 假告警）
  const hasCriticalDeviation = (revenuePct !== null && revenuePct < 80) || (profitPct !== null && profitPct < 80);

  const fmtYi = (v: number) => t("biz.unitYi", { v: (v / 100000000).toFixed(2) });
  const fmtWan = (v: number) => t("biz.unitWan", { v: (v / 10000).toFixed(0) });

  return (
    <div className="flex-1 overflow-auto p-6 lg:p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className={`text-2xl font-bold ${styles.appText}`}>{t("biz.title")}</h1>
          <p className={`text-sm ${styles.cardTextMuted} mt-1`}>{t("biz.subtitle")}</p>
        </div>
        <div className="flex items-center gap-3">
          <button type="button"
            onClick={runDiagnostic}
            disabled={diagnosing}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition ${
              hasCriticalDeviation
                ? `${styles.dangerBg} border ${styles.dangerBorder} ${styles.dangerText} animate-pulse`
                : `${styles.inputBg} border ${styles.cardBorder} ${styles.cardText} ${styles.sidebarHoverBg}`
            } disabled:opacity-50`}
          >
            <Stethoscope className={`w-4 h-4 ${diagnosing ? "animate-spin" : ""}`} />
            {t("biz.diagnostic")}
          </button>
          <button type="button"
            onClick={() => loadData(true)}
            disabled={refreshing}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg ${styles.inputBg} border ${styles.cardBorder} text-sm ${styles.cardText} ${styles.sidebarHoverBg} transition disabled:opacity-50`}
          >
            <RefreshCw className={`w-4 h-4 ${refreshing ? "animate-spin" : ""}`} />
            {t("biz.refresh")}
          </button>
        </div>
      </div>

      {/* KPI Cards — 缺字段显示「无数据」，不以 0 呈现 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <KpiCard icon={Building2} label={t("biz.kpi.deptCount")} value={deptCount === null ? noData : deptCount} color="blue" />
        <KpiCard
          icon={Briefcase} label={t("biz.kpi.projects")} value={projects?.total ?? noData}
          sub={projects
            ? `${t("biz.kpi.inProgress")} ${projects.inProgress ?? noData} · ${t("biz.label.contractTotal")} ${fmtYi(projects.totalAmount)}`
            : undefined}
          color="indigo"
        />
        <KpiCard
          icon={TrendingUp} label={t("biz.kpi.monthRevenueRate")}
          value={revenuePct === null ? noData : `${revenuePct}%`}
          sub={latestRevenue !== null ? `${t("biz.kpi.revenueMonth")} ${fmtWan(latestRevenue)}` : undefined}
          color={revenuePct !== null && revenuePct < 80 ? "red" : "emerald"}
        />
        <KpiCard
          icon={FileText} label={t("biz.kpi.collectionRate")}
          value={latestCollection === null ? noData : `${latestCollection.toFixed(0)}%`}
          sub={collectionTarget ? `${t("biz.label.target")} ${collectionTarget.target_value}%` : undefined}
          color={latestCollection !== null && latestCollection < (collectionTarget?.target_value ?? 85) ? "red" : "emerald"}
        />
      </div>

      {/* Deviation Alert — 仅呈现真实指标偏差；原"供应商华强钢构准时率67%"系硬编码假文案，已删除 */}
      {hasCriticalDeviation && (
        <div className={`mb-6 p-4 ${styles.dangerBg} border ${styles.dangerBorder} rounded-xl flex items-start gap-3`}>
          <AlertCircle className={`w-5 h-5 ${styles.dangerText} mt-0.5 shrink-0`} />
          <div>
            <p className={`text-sm font-semibold ${styles.dangerText}`}>{t("biz.alert.title")}</p>
            <p className={`text-xs ${styles.dangerText} mt-1`}>
              {[
                revenuePct !== null ? t("biz.alert.revenue", { v: `${revenuePct}%` }) : null,
                profitPct !== null ? t("biz.alert.profit", { v: `${profitPct}%` }) : null,
              ].filter(Boolean).join(" · ")}
            </p>
            <button type="button" onClick={runDiagnostic} className={`mt-2 text-xs font-medium ${styles.dangerText} underline hover:opacity-80`}>
              {t("biz.alert.diagnoseCta")}
            </button>
          </div>
        </div>
      )}

      {/* Department + Project Overview */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        {/* Departments */}
        {deptCount !== null && deptCount > 0 && (
          <div className={`${styles.cardBg} rounded-xl border ${styles.cardBorder} p-5`}>
            <h2 className={`text-lg font-semibold ${styles.appText} mb-4 flex items-center gap-2`}>
              <Users className={`w-5 h-5 ${styles.accentText}`} />
              {t("biz.section.org", { n: deptCount })}
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {data?.departments?.slice(0, 6).map((dept) => (
                <div key={dept.id} className={`p-3 rounded-lg border ${styles.appBorder} ${styles.appBg}`}>
                  <p className={`text-sm font-medium ${styles.cardText}`}>{dept.name}</p>
                  <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{dept.manager || "—"}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Project Stats Card */}
        {projects && (
          <div className={`${styles.cardBg} rounded-xl border ${styles.cardBorder} p-5`}>
            <h2 className={`text-lg font-semibold ${styles.appText} mb-4 flex items-center gap-2`}>
              <Briefcase className={`w-5 h-5 ${styles.accentText}`} />
              {t("biz.section.projects")}
            </h2>
            <div className="space-y-3">
              <StatRow label={t("biz.stat.total")} value={projects.total ?? noData} />
              <StatRow label={t("biz.stat.inProgress")} value={projects.inProgress ?? noData} highlight />
              <StatRow label={t("biz.stat.completed")} value={projects.completed ?? noData} />
              <StatRow label={t("biz.stat.planning")} value={projects.planning ?? noData} />
              <StatRow label={t("biz.stat.contractTotal")} value={projects.totalAmount != null ? fmtYi(projects.totalAmount) : noData} />
            </div>
          </div>
        )}
      </div>

      {/* Target vs Actual */}
      {targets.length > 0 && (
        <div className={`${styles.cardBg} rounded-xl border ${styles.cardBorder} p-5 mb-6`}>
          <h2 className={`text-lg font-semibold ${styles.appText} mb-4 flex items-center gap-2`}>
            <TrendingUp className={`w-5 h-5 ${styles.warningText}`} />
            {t("biz.section.targets")}
          </h2>
          <div className="space-y-3">
            {targets.map((item) => {
              const actual = item.target_type === 'revenue' ? latestRevenue
                : item.target_type === 'profit' ? latestProfit
                : item.target_type === 'collection_rate' ? latestCollection : null;
              const pct: number | null = actual !== null && item.target_value > 0
                ? Math.round((actual / item.target_value) * 100) : null;
              const atRisk = pct !== null && pct < 80;
              return (
                <div key={item.id} className="flex items-center gap-4">
                  <span className={`text-sm ${styles.cardText} w-32`}>
                    {targetTypeNameKey[item.target_type] ? t(targetTypeNameKey[item.target_type]) : item.target_type}
                  </span>
                  <div className={`flex-1 h-3 ${styles.inputBg} rounded-full overflow-hidden`}>
                    {pct !== null && (
                      <div
                        className={`h-full rounded-full transition-all ${atRisk ? styles.dangerText : styles.successText}`}
                        style={{ width: `${Math.min(pct, 100)}%`, backgroundColor: "currentColor" }}
                      />
                    )}
                  </div>
                  <span className={`text-sm font-mono ${pct === null ? styles.cardTextMuted : atRisk ? styles.dangerText : styles.successText}`}>
                    {pct === null ? noData : `${pct}%`}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Contract summary（原 contractStats 从未呈现，保留口径注释以便后续接线时补充） */}
      {contracts && contracts.total != null && (
        <div className={`${styles.cardBg} rounded-xl border ${styles.cardBorder} p-5`}>
          <h2 className={`text-lg font-semibold ${styles.appText} mb-4 flex items-center gap-2`}>
            <FileText className={`w-5 h-5 ${styles.accentText}`} />
            {t("biz.section.contracts")}
          </h2>
          <div className="space-y-3">
            <StatRow label={t("biz.stat.contractTotalCount")} value={contracts.total ?? noData} />
            <StatRow label={t("biz.stat.contractTotalValue")} value={contracts.totalValue || noData} />
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
  const { styles } = useTheme();
  return (
    <div className="flex items-center justify-between py-1.5">
      <span className={`text-sm ${styles.cardTextMuted}`}>{label}</span>
      <span className={`text-sm font-semibold ${highlight ? styles.accentText : styles.cardText}`}>
        {value}
      </span>
    </div>
  );
}

function KpiCard({ icon: Icon, label, value, sub, color }: {
  icon: React.ComponentType<{ className?: string }>; label: string; value: number | string; sub?: string; color: string;
}) {
  const { styles } = useTheme();
  // KPI 图标语义色映射（blue/indigo/emerald/amber/red）— 属状态语义枚举，保留
  const colors: Record<string, string> = {
    blue: "bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400",
    indigo: "bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400",
    emerald: "bg-emerald-50 dark:bg-emerald-900/20 text-emerald-600 dark:text-emerald-400",
    amber: "bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400",
    red: "bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400",
  };
  return (
    <div className={`${styles.cardBg} rounded-xl border ${styles.cardBorder} p-5`}>
      <div className="flex items-center gap-3">
        <div className={`p-2.5 rounded-lg ${colors[color] || colors.blue}`}>
          <Icon className="w-5 h-5" />
        </div>
        <div>
          <p className={`text-xs ${styles.cardTextMuted}`}>{label}</p>
          <p className={`text-2xl font-bold ${styles.appText}`}>{value}</p>
          {sub && <p className={`text-xs ${styles.cardTextMuted} mt-0.5`}>{sub}</p>}
        </div>
      </div>
    </div>
  );
}
