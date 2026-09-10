/**
 * PMO-48-D T15 — 数据质量中心 · 报告中心 Tab
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 后端端点 (Phase 4 T15 就绪):
 *   POST   /api/v1/dq/reports/generate?scope=&type=daily|weekly|monthly
 *   GET    /api/v1/dq/reports?reportType=&scope=&pageNum=&pageSize=
 *   GET    /api/v1/dq/reports/{id}/html    (浏览器直接查看)
 *   GET    /api/v1/dq/reports/{id}/pdf     (Phase 5 补, 当前 501)
 *
 * 视图层:
 *   - 顶部: 类型 (DAILY/WEEKLY/MONTHLY) + scope 选择 + 生成按钮 + 刷新按钮
 *   - 中: 报告列表（类型/范围/时段/整体分/行数/告警数/AI摘要/操作(查看)）
 *   - 点"查看" → window.open(dqReportHtmlUrl(id))  在新 tab 打开 HTML
 */
import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Bell,
  BellRing,
  Briefcase,
  CalendarClock,
  Eye,
  FileText,
  Loader2,
  PlusCircle,
  RefreshCw,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { showToastGlobal } from "../../components/common/Toast";
import {
  DqReportType,
  DqReportVO,
  dqReportHtmlUrl,
  fetchDqReports,
  generateDqReport,
} from "./api";

/** 可用的报告类型（下拉框选项） */
const REPORT_TYPES: DqReportType[] = ["daily", "weekly", "monthly"];

/**
 * 分数阶段 → 颜色语义色 (与 DqDimension#gradeOf 阈值对齐 A≥0.95 B≥0.85 C≥0.70 D≥0.50 F<0.50)
 */
function scoreTone(score?: number): "good" | "warn" | "bad" | undefined {
  if (score == null) return undefined;
  if (score >= 0.70) return "good";
  if (score >= 0.50) return "warn";
  return "bad";
}

/** 分数→等级 (A/B/C/D/F) */
function scoreGrade(score?: number): string {
  if (score == null) return "N/A";
  if (score >= 0.95) return "A";
  if (score >= 0.85) return "B";
  if (score >= 0.70) return "C";
  if (score >= 0.50) return "D";
  return "F";
}

/** 紧凑日期格式 (yyyy-MM-dd) */
function fmtDate(iso?: string): string {
  if (!iso) return "";
  // 兼容 "2026-09-10" 与 "2026-09-10T12:00:00" 两种后端格式
  return iso.slice(0, 10);
}

export default function ReportListTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [type, setType] = useState<DqReportType>("daily");
  const [scope, setScope] = useState<string>("ALL");
  const [reports, setReports] = useState<DqReportVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [total, setTotal] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetchDqReports({ reportType: type.toUpperCase(), scope });
      if (res) {
        setReports(res.data);
        setTotal(res.total);
      } else {
        setReports([]);
        setTotal(0);
      }
    } catch (e) {
      setReports([]);
      setTotal(0);
      if (e instanceof Error) {
        showToastGlobal("error", e.message);
      }
    } finally {
      setLoading(false);
    }
  }, [type, scope]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleGenerate = useCallback(async () => {
    setGenerating(true);
    const r = await generateDqReport(type, scope);
    setGenerating(false);
    if (r.success) {
      showToastGlobal("success", t("dw.dqRule.report.generateSuccess"));
      void load();
    } else {
      showToastGlobal("error", r.error ?? t("dw.dqRule.report.generateFailed"));
    }
  }, [type, scope, t, load]);

  const handleView = useCallback((id: string) => {
    window.open(dqReportHtmlUrl(id), "_blank", "noopener");
  }, []);

  // 级联 badge: [类型图标]
  const typeIcons = useMemo(() => ({
    daily: BellRing,
    weekly: CalendarClock,
    monthly: Bell,
  }), []);

  /** 类型→ badge 颜色 class (用主题时 theme-safe class 若没则用 opacity) */
  const typeToneMap: Record<string, string> = {
    DAILY: "opacity-100",
    WEEKLY: "opacity-85",
    MONTHLY: "opacity-70",
  };

  return (
    <div className="h-full overflow-y-auto">
      {/* 顶部选项卡 */}
      <div className={`flex flex-wrap items-center gap-3 mb-4 p-3.5 rounded-xl border ${styles.cardBorder} ${styles.cardBg}`}>
        <span className="text-xs font-semibold">{t("dw.dqRule.report.type." + "DAILY")}</span>
        <div className="flex items-center gap-1">
          {REPORT_TYPES.map((tst) => {
            const active = type === tst;
            return (
              <button
                key={tst}
                onClick={() => setType(tst)}
                className={`px-3 py-1.5 text-xs rounded-lg border transition cursor-pointer ${
                  active
                    ? `border ${styles.accentBorder} ${styles.accentBg} ${styles.accentText} font-bold`
                    : `border ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/5`
                }`}
              >
                {t(`dw.dqRule.report.type.${tst.toUpperCase()}`)}
              </button>
            );
          })}
        </div>

        {/* scope 输入 */}
        <input
          value={scope}
          onChange={(e) => setScope(e.target.value)}
          placeholder={t("dw.dqRule.report.scope.hint")}
          className={`flex-1 min-w-[180px] max-w-[260px] px-2.5 py-1.5 text-xs rounded-lg border ${styles.cardBorder} ${styles.cardBg} ${styles.cardText} focus:outline-none`}
        />

        <button
          onClick={() => void handleGenerate()}
          disabled={generating}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold rounded-lg transition cursor-pointer disabled:opacity-50 ${styles.accentBg} ${styles.accentHover} ${styles.cardText} border ${styles.accentBorder}`}
        >
          {generating ? (
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
          ) : (
            <PlusCircle className="w-3.5 h-3.5" />
          )}
          {generating ? t("dw.dqRule.report.generating") : t("dw.dqRule.report.generate")}
        </button>

        <button
          onClick={() => void load()}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border ${styles.cardBorder} ${styles.cardTextMuted} rounded-lg transition cursor-pointer hover:bg-black/5 dark:hover:bg-white/5`}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {t("dw.dqRule.report.refresh")}
        </button>

        <span className={`ml-auto text-[11px] font-mono ${styles.cardTextMuted}`}>
          {t("dw.dqRule.report.refresh")}: {total}
        </span>
      </div>

      {/* 报告列表 */}
      <div className={`border ${styles.cardBorder} rounded-xl ${styles.cardBg} overflow-hidden`}>
        <div className={`px-3.5 py-2.5 border-b ${styles.cardBorder} flex items-center gap-2`}>
          <FileText className={`w-3.5 h-3.5 ${styles.cardTextMuted}`} />
          <span className="text-xs font-bold">{t("dw.dqRule.report.tab.name")}</span>
          <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>/api/v1/dq/reports</span>
        </div>

        {reports.length === 0 && !loading ? (
          <div className="px-3.5 py-12 text-center">
            <FileText className={`w-12 h-12 mx-auto ${styles.cardTextMuted} opacity-40`} />
            <div className={`mt-3 text-xs ${styles.cardTextMuted}`}>{t("dw.dqRule.report.noReports")}</div>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className={`border-b ${styles.cardBorder}`}>
                  <th className="px-3 py-2.5 font-semibold text-left w-20">{t("dw.dqRule.report.colType")}</th>
                  <th className="px-3 py-2.5 font-semibold text-left w-28">{t("dw.dqRule.report.colScope")}</th>
                  <th className="px-3 py-2.5 font-semibold text-left w-48">{t("dw.dqRule.report.colDates")}</th>
                  <th className="px-3 py-2.5 font-semibold text-right w-16">{t("dw.dqRule.report.colScore")}</th>
                  <th className="px-3 py-2.5 font-semibold text-right w-16">{t("dw.dqRule.report.colRows")}</th>
                  <th className="px-3 py-2.5 font-semibold text-right w-20">{t("dw.dqRule.report.colAlerts")}</th>
                  <th className="px-3 py-2.5 font-semibold text-left hidden lg:table-cell">{t("dw.dqRule.report.colSummary")}</th>
                  <th className="px-3 py-2.5 font-semibold text-right w-24">{t("dw.dqRule.report.colActions")}</th>
                </tr>
              </thead>
              <tbody>
                {reports.map((r) => {
                  const score = r.avgScore;
                  const grade = scoreGrade(score);
                  const tone = scoreTone(score);
                  const typeKey = r.reportType?.toUpperCase() ?? "DAILY";
                  const scopeKey = r.scope ?? "ALL";
                  const scopeI18n = scopeKey === "ALL" || scopeKey === "NATIVE" || scopeKey === "OFI"
                    ? `dw.dqRule.report.scope.${scopeKey}`
                    : null;
                  return (
                    <tr key={r.id} className={`border-b ${styles.cardBorder} hover:bg-black/[0.02] dark:hover:bg-white/[0.02] transition`}>
                      <td className="px-3 py-2.5">
                        <div className="flex items-center gap-1.5">
                          {(() => {
                            const Icon = typeIcons[r.reportType?.toLowerCase() as DqReportType] ?? Bell;
                            return <Icon className="w-3.5 h-3.5 shrink-0" />;
                          })()}
                          <span className="font-mono font-semibold" style={{ opacity: Number((typeToneMap[typeKey] ?? "").replace("opacity-", "") || 100) / 100 }}>
                            {t(`dw.dqRule.report.type.${typeKey}`)}
                          </span>
                        </div>
                      </td>
                      <td className="px-3 py-2.5">
                        <span className={`font-mono ${styles.cardText}`}>
                          {scopeI18n ? t(scopeI18n) : scopeKey}
                        </span>
                      </td>
                      <td className="px-3 py-2.5 font-mono">
                        {fmtDate(r.startDate)} ~ {fmtDate(r.endDate)}
                      </td>
                      <td className="px-3 py-2.5 text-right">
                        <span
                          className={`font-mono font-bold tabular-nums ${
                            tone === "good" ? styles.successText :
                            tone === "warn" ? styles.warningText :
                            tone === "bad" ? styles.dangerText : styles.cardText
                          }`}
                        >
                          {score != null ? (score * 100).toFixed(0) + "%" : "N/A"}
                        </span>
                        <span className={`ml-1 text-[10px] font-mono text ${styles.cardTextMuted}`}>[{grade}]</span>
                      </td>
                      <td className="px-3 py-2.5 text-right font-mono tabular-nums">{r.rowCount ?? 0}</td>
                      <td className="px-3 py-2.5 text-right font-mono tabular-nums">
                        <span className={r.alertCount && r.alertCount > 0 ? styles.dangerText : styles.cardText}>
                          {r.alertCount ?? 0}
                        </span>
                      </td>
                      <td className="px-3 py-2.5 hidden lg:table-cell max-w-[280px]">
                        <span className={`text-[10px] ${styles.cardTextMuted} line-clamp-2`} title={r.llmSummary ?? ""}>
                          {r.llmSummary || "-"}
                        </span>
                      </td>
                      <td className="px-3 py-2.5 text-right">
                        <button
                          onClick={() => handleView(r.id)}
                          className={`inline-flex items-center gap-1 px-2.5 py-1 text-[10px] font-semibold rounded-md border ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/5 transition cursor-pointer`}
                          title={t("dw.dqRule.report.viewHint")}
                        >
                          <Eye className="w-3 h-3" />
                          {t("dw.dqRule.report.view")}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
