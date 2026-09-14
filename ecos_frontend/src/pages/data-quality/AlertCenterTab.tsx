/**
 * PMO-48-C T14 — 数据质量中心 · 告警中心 Tab
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 后端 T12 已就绪：
 *   GET  /api/v1/dq/alerts?{level}&{status}&{sort}
 *   POST /api/v1/dq/alerts/{id}/ack
 *   POST /api/v1/dq/alerts/{id}/resolve (body {note})
 *
 * 功能：
 * - 顶部 4 KPI 卡：P0 / P1 / P2 / P3 计数
 * - 告警时间倒序列表
 * - 级别/状态过滤下拉框
 * - 行内 [ack] / [resolve] 按钮
 * - 10s 轮询（卸载时 clear）
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Bell,
  BellRing,
  Check,
  CheckCircle2,
  Clock,
  Loader2,
  RefreshCw,
  Siren,
  TriangleAlert,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { showToastGlobal } from "../../components/common/Toast";
import {
  DqAlertVO,
  ackDqAlert,
  fetchDqAlerts,
  resolveDqAlert,
} from "./api";

/** KPI 级别数组 */
const KPI_LEVELS = ["P0", "P1", "P2", "P3"] as const;

/** 状态列表（过滤 dropdown 用） */
const ALERT_STATUSES = ["PENDING", "NOTIFIED", "ACKED", "RESOLVED"] as const;

/**
 * 告警级别徽章样式
 * P0=红 / P1=橙 / P2=黄 / P3=灰，全部用语义色 + absolute index 展开（不硬编码 Tailwind 色号）。
 * 使用 Tailwind 的任意透明度类（Tailwind v4 直接生成）保证主题感知。
 */
function levelBadgeClass(level: string): string {
  switch (level) {
    case "P0":
      return "bg-red-500/15 text-red-700 dark:text-red-400 border border-red-500/30";
    case "P1":
      return "bg-orange-500/15 text-orange-700 dark:text-orange-400 border border-orange-500/30";
    case "P2":
      return "bg-yellow-500/15 text-yellow-700 dark:text-yellow-400 border border-yellow-500/30";
    default:
      return "bg-slate-500/15 text-slate-600 dark:text-slate-400 border border-slate-400/30"; /* FIXME:theme-helper 留尾清理 */
  }
}

/** 状态徽章样式 */
function statusBadgeClass(status: string): string {
  switch (status) {
    case "PENDING":
      return "bg-red-500/10 text-red-600 dark:text-red-400";
    case "NOTIFIED":
      return "bg-yellow-500/10 text-yellow-600 dark:text-yellow-400";
    case "ACKED":
      return "bg-blue-500/10 text-blue-600 dark:text-blue-400";
    case "RESOLVED":
      return "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400";
    default:
      return "bg-slate-500/10 text-slate-500 dark:text-slate-400";
  }
}

/** 格式化时间（去掉时区） */
function fmtTime(iso?: string): string {
  if (!iso) return "—";
  return iso.replace("T", " ").slice(0, 19);
}

export default function AlertCenterTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [alerts, setAlerts] = useState<DqAlertVO[]>([]);
  const [levelFilter, setLevelFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const load = useCallback(async () => {
    try {
      const q: { level?: string; status?: string; sort?: string } = { sort: "desc" };
      if (levelFilter) q.level = levelFilter;
      if (statusFilter) q.status = statusFilter;
      const r = await fetchDqAlerts(q);
      setAlerts(r ?? []);
    } finally {
      setLoading(false);
    }
  }, [levelFilter, statusFilter]);

  // 首次加载 + 10s 轮询
  useEffect(() => {
    setLoading(true);
    void load();
    timerRef.current = setInterval(() => void load(), 10_000);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
      timerRef.current = null;
    };
  }, [load]);

  /** KPI 计数（按 alertLevel 统计，不局限于当前过滤） */
  const kpiCounts = useMemo(() => {
    const counts: Record<string, number> = { P0: 0, P1: 0, P2: 0, P3: 0 };
    for (const a of alerts) {
      const lvl = a.alertLevel;
      if (lvl in counts) (counts as Record<string, number>)[lvl] += 1;
    }
    return counts;
  }, [alerts]);

  const handleAck = useCallback(
    async (id: string) => {
      setBusyId(id);
      try {
        const r = await ackDqAlert(id);
        if (r.success) {
          showToastGlobal("success", t("dw.dqRule.alerts.ackSuccess"));
          await load();
        } else {
          showToastGlobal("error", r.error ?? t("dw.dqRule.alerts.ackFailed"));
        }
      } finally {
        setBusyId(null);
      }
    },
    [load, t],
  );

  const handleResolve = useCallback(
    async (id: string) => {
      setBusyId(id);
      try {
        const note = t("dw.dqRule.alerts.resolveNoteHint");
        const r = await resolveDqAlert(id, note);
        if (r.success) {
          showToastGlobal("success", t("dw.dqRule.alerts.resolveSuccess"));
          await load();
        } else {
          showToastGlobal("error", r.error ?? t("dw.dqRule.alerts.resolveFailed"));
        }
      } finally {
        setBusyId(null);
      }
    },
    [load, t],
  );

  return (
    <div className="flex-1 min-h-0 flex flex-col space-y-4">
      {/* KPI 卡片行 */}
      <div className="grid grid-cols-4 gap-4">
        {KPI_LEVELS.map((lvl) => {
          const Icon =
            lvl === "P0" ? Siren : lvl === "P1" ? BellRing : lvl === "P2" ? Bell : TriangleAlert;
          const count = kpiCounts[lvl] ?? 0;
          return (
            <button
              key={lvl}
              onClick={() => setLevelFilter(levelFilter === lvl ? "" : lvl)}
              className={`rounded-md p-4 text-left cursor-pointer transition border ${
                levelFilter === lvl ? styles.cardBorder : "transparent"
              } ${styles.cardBg} hover:${styles.sidebarBg}`}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <Icon
                    className={
                      lvl === "P0"
                        ? "w-5 h-5 text-red-500"
                        : lvl === "P1"
                          ? "w-5 h-5 text-orange-500"
                          : lvl === "P2"
                            ? "w-5 h-5 text-yellow-500"
                            : `w-5 h-5 ${styles.cardTextMuted}`
                    }
                  />
                  <span className={`text-xs font-folded font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>
                    {t(`dw.dqRule.alerts.level.${lvl}`)}
                  </span>
                </div>
                <span className={`text-2xl font-bold ${styles.cardText}`}>
                  {count}
                </span>
              </div>
            </button>
          );
        })}
      </div>

      {/* 过滤区 + 刷新 */}
      <div className="flex items-center gap-2 flex-wrap">
        <select
          value={levelFilter}
          onChange={(e) => setLevelFilter(e.target.value)}
          className={`h-8 rounded-md px-2.5 text-xs border ${styles.cardBorder} ${styles.inputBg} ${styles.cardText}`}
        >
          <option value="">{t("dw.dqRule.alerts.filterAll")}</option>
          {KPI_LEVELS.map((lvl) => (
            <option key={lvl} value={lvl}>
              {t(`dw.dqRule.alerts.level.${lvl}`)}
            </option>
          ))}
        </select>

        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className={`h-8 rounded-md px-2.5 text-xs border ${styles.cardBorder} ${styles.inputBg} ${styles.cardText}`}
        >
          <option value="">{t("dw.dqRule.alerts.filterAll")}</option>
          {ALERT_STATUSES.map((s) => (
            <option key={s} value={s}>
              {t(`dw.dqRule.alerts.status.${s}`)}
            </option>
          ))}
        </select>

        <button
          onClick={() => {
            setLoading(true);
            void load();
          }}
          className="h-8 px-3 rounded-md flex items-center gap-1.5 text-xs border cursor-pointer transition hover:opacity-80"
          style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {t("dw.dqRule.refresh")}
        </button>
      </div>

      {/* 告警列表 */}
      <div className={`flex-1 min-h-0 overflow-y-auto rounded-md border ${styles.cardBorder} ${styles.cardBg}`}>
        {loading && alerts.length === 0 ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin opacity-50" />
          </div>
        ) : alerts.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-2">
            <Bell className="w-8 h-8 opacity-30" />
            <span className={`text-xs ${styles.cardTextMuted}`}>{t("dw.dqRule.alerts.noAlerts")}</span>
          </div>
        ) : (
          <table className="w-full text-xs">
            <thead>
              <tr className={`border-b ${styles.cardBorder} text-left`}>
                <th className="px-3 py-2.5 font-semibold w-14">{t("dw.dqRule.alerts.colLevel")}</th>
                <th className="px-3 py-2.5 font-semibold">{t("dw.dqRule.alerts.colRule")}</th>
                <th className="px-3 py-2.5 font-semibold">{t("dw.dqRule.alerts.colAsset")}</th>
                <th className="px-3 py-2.5 font-semibold hidden md:table-cell">{t("dw.dqRule.alerts.colMsg")}</th>
                <th className="px-3 py-2.5 font-semibold hidden lg:table-cell">{t("dw.dqRule.alerts.colTime")}</th>
                <th className="px-3 py-2.5 font-semibold w-20">{t("dw.dqRule.alerts.colStatus")}</th>
                <th className="px-3 py-2.5 font-semibold w-32 text-right">{t("dw.dqRule.colActions")}</th>
              </tr>
            </thead>
            <tbody>
              {alerts.map((a) => {
                const isBusy = busyId === a.id;
                const canAck = a.status === "PENDING" || a.status === "NOTIFIED";
                const canResolve = a.status !== "RESOLVED";
                return (
                  <tr
                    key={a.id}
                    className={`border-b last:border-0 ${styles.cardBorder} ${styles.sidebarHoverBg} transition`}
                  >
                    {/* 级别 */}
                    <td className="px-3 py-2.5">
                      <span
                        className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold tracking-wide ${levelBadgeClass(a.alertLevel)}`}
                      >
                        {a.alertLevel}
                      </span>
                      {a.escalatedTo && (
                        <span className="ml-1 text-[9px] font-mono opacity-50">→{a.escalatedTo}</span>
                      )}
                    </td>

                    {/* 规则 */}
                    <td className="px-3 py-2.5 max-w-48 truncate" title={a.ruleName}>
                      <span className={`font-medium ${styles.cardText}`}>{a.ruleName}</span>
                      <div className="text-[10px] font-mono opacity-50 mt-0.5">{a.alertType}</div>
                    </td>

                    {/* 资产 */}
                    <td className="px-3 py-2.5 max-w-40 truncate text-[11px] font-mono" title={a.assetName ?? a.assetId}>
                      {a.assetName ?? a.assetId ?? "—"}
                    </td>

                    {/* 消息 */}
                    <td className="px-3 py-2.5 max-w-64 truncate hidden md:table-cell" title={a.message}>
                      <span className={`opacity-70 ${styles.cardTextMuted}`}>{a.message}</span>
                    </td>

                    {/* 时间 */}
                    <td className="px-3 py-2.5 hidden lg:table-cell font-mono text-[11px] whitespace-nowrap">
                      {fmtTime(a.createdAt)}
                    </td>

                    {/* 状态 */}
                    <td className="px-3 py-2.5">
                      <span
                        className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium ${statusBadgeClass(a.status)}`}
                      >
                        {t(`dw.dqRule.alerts.status.${a.status}`)}
                      </span>
                      {a.notifyCount > 1 && (
                        <span className="ml-1 text-[10px] font-mono opacity-40">×{a.notifyCount}</span>
                      )}
                    </td>

                    {/* 操作 */}
                    <td className="px-3 py-2.5 text-right whitespace-nowrap">
                      {canAck && (
                        <button
                          disabled={isBusy}
                          onClick={() => handleAck(a.id)}
                          className="h-6 px-2 rounded text-[10px] font-medium border cursor-pointer transition
                            border-emerald-500/40 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/10
                            disabled:opacity-50"
                        >
                          {isBusy ? (
                            <Loader2 className="w-3 h-3 inline animate-spin" />
                          ) : (
                            <Check className="w-3 h-3 inline" />
                          )}
                          {" "}{t("dw.dqRule.alerts.ack")}
                        </button>
                      )}
                      {canResolve && (
                        <button
                          disabled={isBusy}
                          onClick={() => handleResolve(a.id)}
                          className="h-6 px-2 rounded text-[10px] font-medium border ml-1.5 cursor-pointer transition
                            border-blue-500/40 text-blue-600 dark:text-blue-400 hover:bg-blue-500/10
                            disabled:opacity-50"
                        >
                          {isBusy ? (
                            <Loader2 className="w-3 h-3 inline animate-spin" />
                          ) : (
                            <CheckCircle2 className="w-3 h-3 inline" />
                          )}
                          {" "}{t("dw.dqRule.alerts.resolve")}
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
