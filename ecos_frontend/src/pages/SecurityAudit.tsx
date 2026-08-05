/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 * SecurityAudit — Enhanced with filters, stats, timeline, pagination, CSV export, detail expand
 */

import React, { useMemo, useState, useEffect, useCallback } from "react";
import {
  Shield, Search, RotateCcw, Download, ChevronLeft, ChevronRight,
  Activity, AlertTriangle, Users, Globe, X, Clock, Server,
  FileJson, Monitor, Timer, ChevronDown, ChevronUp, Filter,
} from "lucide-react";
import { AuditEvent, AuditStats } from "../types";
import { fetchAuditLogs, fetchAuditStats, fetchUsers } from "../api";
import type { IamUser } from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

const ACTION_OPTIONS = ["LOGIN", "QUERY", "MODIFY", "DELETE", "EXPORT"] as const;
const PAGE_SIZE_OPTIONS = [20, 50, 100] as const;

export default function SecurityAudit() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  // ── Data state ────────────────────────────────────
  const [auditLogs, setAuditLogs] = useState<AuditEvent[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState<number>(20);
  const [loading, setLoading] = useState(true);
  const [users, setUsers] = useState<IamUser[]>([]);
  const [stats, setStats] = useState<AuditStats>({
    todayCount: 0, failureCount: 0, activeUsers: 0, anomalyIps: 0,
  });

  // ── Filter state ──────────────────────────────────
  const [filterUserId, setFilterUserId] = useState("");
  const [filterAction, setFilterAction] = useState("");
  const [filterDateFrom, setFilterDateFrom] = useState("");
  const [filterDateTo, setFilterDateTo] = useState("");
  const [filterIp, setFilterIp] = useState("");

  // ── UI state ──────────────────────────────────────
  const [expandedEventId, setExpandedEventId] = useState<string | null>(null);
  const [showFilters, setShowFilters] = useState(false);

  // ── Data fetching ─────────────────────────────────
  const loadData = useCallback(async (
    p: number = page,
    ps: number = pageSize,
    userId?: string,
    action?: string,
  ) => {
    setLoading(true);
    try {
      const [logsRes, statsRes] = await Promise.all([
        fetchAuditLogs(userId || undefined, action || undefined, undefined, p, ps),
        fetchAuditStats(),
      ]);
      setAuditLogs(logsRes.data || []);
      setTotal(logsRes.total || 0);
      setStats(statsRes);
    } catch (e) {
      console.warn("SecurityAudit: load failed", e);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  // Load users on mount
  useEffect(() => {
    fetchUsers(undefined, 1, 200).then(res => {
      setUsers(res.data || []);
    }).catch(() => {});
  }, []);

  // Load audit logs + stats on mount and when filters/page change
  useEffect(() => {
    loadData(page, pageSize, filterUserId, filterAction);
  }, [page, pageSize]); // eslint-disable-line react-hooks/exhaustive-deps

  // Re-fetch when filters change (reset page to 1)
  const handleSearch = () => {
    setPage(1);
    loadData(1, pageSize, filterUserId, filterAction);
  };

  const handleReset = () => {
    setFilterUserId("");
    setFilterAction("");
    setFilterDateFrom("");
    setFilterDateTo("");
    setFilterIp("");
    setPage(1);
    loadData(1, pageSize, "", "");
  };

  // ── Timeline grouping ─────────────────────────────
  const timelineGroups = useMemo(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    const groups: { label: string; labelKey: string; events: AuditEvent[] }[] = [];
    const todayEvents: AuditEvent[] = [];
    const yesterdayEvents: AuditEvent[] = [];
    const olderEvents: AuditEvent[] = [];

    auditLogs.forEach(log => {
      const logDate = new Date(log.timestamp);
      logDate.setHours(0, 0, 0, 0);
      if (logDate.getTime() === today.getTime()) {
        todayEvents.push(log);
      } else if (logDate.getTime() === yesterday.getTime()) {
        yesterdayEvents.push(log);
      } else {
        olderEvents.push(log);
      }
    });

    if (todayEvents.length > 0) groups.push({ label: t("sec.timeline.today"), labelKey: "today", events: todayEvents });
    if (yesterdayEvents.length > 0) groups.push({ label: t("sec.timeline.yesterday"), labelKey: "yesterday", events: yesterdayEvents });
    if (olderEvents.length > 0) groups.push({ label: t("sec.timeline.older"), labelKey: "older", events: olderEvents });

    return groups;
  }, [auditLogs, t]);

  // ── CSV export ────────────────────────────────────
  const exportCsv = () => {
    const headers = ["EventID", "Timestamp", "UserID", "Action", "Resource", "Result", "IP", "UserAgent", "Duration(ms)"];
    const rows = auditLogs.map(log => [
      log.eventId,
      log.timestamp,
      log.userId,
      log.action,
      log.resource,
      log.result,
      log.ipAddress || "",
      log.userAgent || "",
      log.duration !== undefined ? String(log.duration) : "",
    ]);

    const BOM = "\uFEFF";
    const csv = BOM + [headers, ...rows].map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `audit-export-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // ── Helpers ───────────────────────────────────────
  const getActionLabel = (action: string) => {
    const key = `sec.action.${action.toLowerCase()}`;
    const translated = t(key);
    return translated !== key ? translated : action;
  };

  const getResultBadge = (result: string) => {
    const success = result === "SUCCESS";
    return (
      <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
        success ? "bg-green-500/20 text-green-400 border border-green-500/30" :
          "bg-red-500/20 text-red-400 border border-red-500/30"
      }`}>
        {success ? t("sec.ledger.committed") : (result === "FAILURE" ? t("sec.tb.status.pending") : result)}
      </span>
    );
  };

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  // ── Detail panel data ─────────────────────────────
  const expandedEvent = auditLogs.find(e => e.eventId === expandedEventId);

  // ── Render ────────────────────────────────────────
  return (
    <div className={`flex-1 overflow-y-auto p-5 flex flex-col h-full font-sans ${styles.appBg} ${styles.appText}`}>
      {/* ── Header ────────────────────────────────── */}
      <div className="flex justify-between items-center mb-4 shrink-0">
        <div>
          <h1 className={`text-xl font-bold tracking-tight flex items-center gap-2 ${styles.cardText}`}>
            <Shield className="text-blue-500 w-5 h-5 shrink-0" />
            {t("sec.title")}
          </h1>
          <p className={`text-xs mt-0.5 ${styles.cardTextMuted}`}>{t("sec.desc")}</p>
        </div>
        <button
          onClick={exportCsv}
          disabled={auditLogs.length === 0}
          className={`px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer border
            ${auditLogs.length === 0 ? "opacity-50 cursor-not-allowed" : "hover:bg-blue-600/30"}
            bg-blue-600/20 text-blue-400 border-blue-500/30`}
        >
          <Download size={14} />
          {t("sec.export.csv")}
        </button>
      </div>

      {/* ── Stats cards ────────────────────────────── */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-4 shrink-0">
        {([
          { icon: <Activity size={16} className="text-blue-400" />, label: t("sec.stats.today"), value: stats.todayCount, bg: "bg-blue-500/10", border: "border-blue-500/20" },
          { icon: <AlertTriangle size={16} className="text-red-400" />, label: t("sec.stats.failures"), value: stats.failureCount, bg: "bg-red-500/10", border: "border-red-500/20" },
          { icon: <Users size={16} className="text-emerald-400" />, label: t("sec.stats.active_users"), value: stats.activeUsers, bg: "bg-emerald-500/10", border: "border-emerald-500/20" },
          { icon: <Globe size={16} className="text-amber-400" />, label: t("sec.stats.anomaly_ips"), value: stats.anomalyIps, bg: "bg-amber-500/10", border: "border-amber-500/20" },
        ]).map((card, i) => (
          <div key={i} className={`rounded-lg border p-3 flex items-center gap-3 ${card.bg} ${card.border}`}>
            <div className="shrink-0">{card.icon}</div>
            <div className="min-w-0">
              <p className={`text-[10px] font-mono uppercase tracking-wider ${styles.cardTextMuted}`}>{card.label}</p>
              <p className={`text-lg font-bold tabular-nums font-mono ${styles.cardText}`}>{card.value.toLocaleString()}</p>
            </div>
          </div>
        ))}
      </div>

      {/* ── Filter bar ─────────────────────────────── */}
      <div className={`rounded-lg border p-3 mb-4 shrink-0 ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center gap-2 mb-2">
          <button
            onClick={() => setShowFilters(!showFilters)}
            className={`flex items-center gap-1 text-xs font-bold transition-colors cursor-pointer ${styles.cardTextMuted} hover:${styles.cardText}`}
          >
            <Filter size={13} />
            {locale === "zh" ? "筛选条件" : "Filters"}
            {showFilters ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
          </button>
        </div>

        {showFilters && (
          <div className="flex flex-wrap items-end gap-3">
            {/* User dropdown */}
            <div className="flex flex-col gap-1">
              <label className={`text-[10px] font-mono uppercase ${styles.cardTextMuted}`}>{t("sec.filter.user")}</label>
              <select
                value={filterUserId}
                onChange={e => setFilterUserId(e.target.value)}
                className={`text-xs rounded-md border px-2 py-1.5 min-w-[140px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} outline-none focus:ring-1 focus:ring-blue-500`}
              >
                <option value="">{t("sec.filter.user_all")}</option>
                {users.map(u => (
                  <option key={u.userId} value={u.userId}>
                    {u.realName || u.username} ({u.userId})
                  </option>
                ))}
              </select>
            </div>

            {/* Action dropdown */}
            <div className="flex flex-col gap-1">
              <label className={`text-[10px] font-mono uppercase ${styles.cardTextMuted}`}>{t("sec.filter.action")}</label>
              <select
                value={filterAction}
                onChange={e => setFilterAction(e.target.value)}
                className={`text-xs rounded-md border px-2 py-1.5 min-w-[120px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} outline-none focus:ring-1 focus:ring-blue-500`}
              >
                <option value="">{t("sec.filter.action_all")}</option>
                {ACTION_OPTIONS.map(a => (
                  <option key={a} value={a}>{getActionLabel(a)}</option>
                ))}
              </select>
            </div>

            {/* Date from */}
            <div className="flex flex-col gap-1">
              <label className={`text-[10px] font-mono uppercase ${styles.cardTextMuted}`}>{t("sec.filter.date_from")}</label>
              <input
                type="date"
                value={filterDateFrom}
                onChange={e => setFilterDateFrom(e.target.value)}
                className={`text-xs rounded-md border px-2 py-1.5 ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} outline-none focus:ring-1 focus:ring-blue-500`}
              />
            </div>

            {/* Date to */}
            <div className="flex flex-col gap-1">
              <label className={`text-[10px] font-mono uppercase ${styles.cardTextMuted}`}>{t("sec.filter.date_to")}</label>
              <input
                type="date"
                value={filterDateTo}
                onChange={e => setFilterDateTo(e.target.value)}
                className={`text-xs rounded-md border px-2 py-1.5 ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} outline-none focus:ring-1 focus:ring-blue-500`}
              />
            </div>

            {/* IP input */}
            <div className="flex flex-col gap-1">
              <label className={`text-[10px] font-mono uppercase ${styles.cardTextMuted}`}>{t("sec.filter.ip")}</label>
              <input
                type="text"
                value={filterIp}
                onChange={e => setFilterIp(e.target.value)}
                placeholder={t("sec.filter.ip_placeholder")}
                className={`text-xs rounded-md border px-2 py-1.5 w-[140px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} outline-none focus:ring-1 focus:ring-blue-500`}
              />
            </div>

            {/* Buttons */}
            <div className="flex gap-2">
              <button
                onClick={handleSearch}
                className="px-3 py-1.5 rounded-md text-xs font-bold bg-blue-600 text-white hover:bg-blue-500 transition-all flex items-center gap-1 cursor-pointer"
              >
                <Search size={13} />
                {t("sec.filter.search")}
              </button>
              <button
                onClick={handleReset}
                className={`px-3 py-1.5 rounded-md text-xs font-bold border transition-all flex items-center gap-1 cursor-pointer ${styles.cardTextMuted} ${styles.cardBorder} hover:${styles.cardText}`}
              >
                <RotateCcw size={13} />
                {t("sec.filter.reset")}
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ── Main content: Timeline + Detail panel ───── */}
      <div className="flex-1 min-h-0 flex gap-4">
        {/* Timeline */}
        <div className={`flex-1 rounded-lg border p-4 overflow-y-auto scrollbar-thin ${styles.cardBg} ${styles.cardBorder}`}>
          {loading ? (
            <div className="flex items-center justify-center h-32">
              <span className={`text-xs font-mono animate-pulse ${styles.cardTextMuted}`}>
                {t("sec.timeline.loading")}
              </span>
            </div>
          ) : timelineGroups.length === 0 ? (
            <div className="flex items-center justify-center h-32">
              <span className={`text-xs font-mono ${styles.cardTextMuted}`}>{t("sec.timeline.empty")}</span>
            </div>
          ) : (
            <div className="relative pl-6">
              {/* Vertical line */}
              <div className="absolute left-[11px] top-0 bottom-0 w-0.5 bg-blue-500/20" />

              {timelineGroups.map((group, gi) => (
                <div key={group.labelKey} className="mb-6 last:mb-0">
                  {/* Group header */}
                  <div className="flex items-center gap-2 mb-3 -ml-[26px]">
                    <div className="w-3 h-3 rounded-full bg-blue-500 border-2 border-blue-500/40 ring-2 ring-blue-500/20" />
                    <span className={`text-[11px] font-bold uppercase tracking-wider ${styles.cardText}`}>
                      {group.label}
                    </span>
                    <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
                      {group.events.length} {locale === "zh" ? "条" : "items"}
                    </span>
                  </div>

                  {group.events.map((log, ei) => {
                    const isExpanded = expandedEventId === log.eventId;
                    const detailsStr = typeof log.details === "string" ? log.details :
                      (log.details ? JSON.stringify(log.details, null, 2) : "");
                    const timeStr = log.timestamp ? log.timestamp.split("T")[1]?.substring(0, 8) || log.timestamp : "";

                    return (
                      <div key={log.eventId} className="mb-2 last:mb-0 relative">
                        {/* Time node */}
                        <div className="absolute left-[-26px] top-3 w-2 h-2 rounded-full bg-blue-500/50 border border-blue-500/30" />

                        {/* Event card */}
                        <div
                          onClick={() => setExpandedEventId(isExpanded ? null : log.eventId)}
                          className={`rounded-md border p-3 cursor-pointer transition-all ${
                            isExpanded ? "border-blue-500/50 bg-blue-500/5" : `${styles.cardBorder} bg-black/10 hover:bg-black/20`
                          }`}
                        >
                          <div className="flex items-center justify-between gap-3 flex-wrap mb-1.5">
                            <div className="flex items-center gap-2 min-w-0">
                              <span className={`text-[11px] font-bold uppercase font-mono ${styles.cardText}`}>
                                {getActionLabel(log.action)}
                              </span>
                              <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
                                {log.resource}
                              </span>
                            </div>
                            <div className="flex items-center gap-2 shrink-0">
                              {getResultBadge(log.result)}
                              <span className={`text-[10px] font-mono ${styles.cardTextMuted} flex items-center gap-1`}>
                                <Clock size={11} />
                                {timeStr}
                              </span>
                            </div>
                          </div>

                          <div className={`flex flex-wrap items-center gap-x-3 gap-y-1 text-[10px] font-mono ${styles.cardTextMuted}`}>
                            <span>{locale === "zh" ? "操作者: " : "Actor: "}
                              <span className="text-blue-400 font-semibold">{log.userId}</span>
                            </span>
                            {log.ipAddress && (
                              <span>IP: <span className={styles.cardText}>{log.ipAddress}</span></span>
                            )}
                            {log.duration !== undefined && (
                              <span className="flex items-center gap-1">
                                <Timer size={10} /> {log.duration}{t("sec.detail.duration_unit")}
                              </span>
                            )}
                            <span className="ml-auto text-blue-400/70">{log.eventId}</span>
                          </div>

                          {/* Expand indicator */}
                          <div className={`text-[10px] mt-1.5 flex items-center gap-1 ${styles.cardTextMuted}`}>
                            {isExpanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                            <span className="opacity-60">{isExpanded ? t("sec.detail.close") : (locale === "zh" ? "展开详情" : "Details")}</span>
                          </div>

                          {/* Expanded detail content */}
                          {isExpanded && (
                            <div className={`mt-3 pt-3 border-t space-y-2 ${styles.cardBorder}`}>
                              <p className={`text-[10px] font-bold uppercase font-mono flex items-center gap-1.5 ${styles.cardText}`}>
                                <FileJson size={12} className="text-amber-400" />
                                {t("sec.detail.title")}
                              </p>

                              {/* Request params */}
                              <div>
                                <span className={`text-[9px] font-mono uppercase ${styles.cardTextMuted}`}>{t("sec.detail.params")}</span>
                                <pre className={`mt-1 text-[10px] rounded-md p-2 overflow-x-auto font-mono max-h-[200px] overflow-y-auto ${styles.inputBg} ${styles.cardText}`}>
                                  {detailsStr || t("sec.detail.no_details")}
                                </pre>
                              </div>

                              {/* Response summary */}
                              <div>
                                <span className={`text-[9px] font-mono uppercase ${styles.cardTextMuted}`}>{t("sec.detail.response")}</span>
                                <p className={`text-[10px] font-mono mt-0.5 ${styles.cardText}`}>
                                  {log.result === "SUCCESS"
                                    ? (locale === "zh" ? "操作执行成功，凭证已写入不可篡改审计区块。" : "Operation completed successfully. Voucher committed to immutable audit ledger.")
                                    : (locale === "zh" ? "操作被拦截或待审批。" : "Operation blocked or pending approval.")}
                                </p>
                              </div>

                              {/* Meta row */}
                              <div className={`grid grid-cols-2 gap-x-4 gap-y-1 text-[10px] font-mono pt-1 border-t ${styles.cardBorder}`}>
                                <div className="flex items-center gap-1.5">
                                  <Globe size={10} className={styles.cardTextMuted} />
                                  <span className={styles.cardTextMuted}>{t("sec.detail.ip")}:</span>
                                  <span className={styles.cardText}>{log.ipAddress || "—"}</span>
                                </div>
                                <div className="flex items-center gap-1.5">
                                  <Timer size={10} className={styles.cardTextMuted} />
                                  <span className={styles.cardTextMuted}>{t("sec.detail.duration")}:</span>
                                  <span className={styles.cardText}>{log.duration !== undefined ? `${log.duration}${t("sec.detail.duration_unit")}` : "—"}</span>
                                </div>
                                <div className="col-span-2 flex items-start gap-1.5">
                                  <Monitor size={10} className={`shrink-0 mt-0.5 ${styles.cardTextMuted}`} />
                                  <span className={styles.cardTextMuted}>{t("sec.detail.ua")}:</span>
                                  <span className={`break-all ${styles.cardText}`}>{log.userAgent || "—"}</span>
                                </div>
                              </div>
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Side detail panel (shown when expandedEvent exists, larger screens) */}
        {expandedEvent && (
          <div className={`hidden xl:flex w-[360px] shrink-0 rounded-lg border p-4 flex-col overflow-y-auto scrollbar-thin ${styles.cardBg} ${styles.cardBorder}`}>
            <div className="flex items-center justify-between mb-3">
              <h3 className={`text-xs font-bold uppercase flex items-center gap-1.5 ${styles.cardText}`}>
                <FileJson size={13} className="text-amber-400" />
                {t("sec.detail.title")}
              </h3>
              <button
                onClick={() => setExpandedEventId(null)}
                className={`p-1 rounded hover:bg-white/10 cursor-pointer ${styles.cardTextMuted}`}
              >
                <X size={14} />
              </button>
            </div>

            <div className="space-y-3">
              <div>
                <span className={`text-[9px] font-mono uppercase ${styles.cardTextMuted}`}>Event ID</span>
                <p className={`text-[11px] font-mono mt-0.5 ${styles.cardText}`}>{expandedEvent.eventId}</p>
              </div>

              <div>
                <span className={`text-[9px] font-mono uppercase ${styles.cardTextMuted}`}>{t("sec.filter.action")}</span>
                <p className={`text-[11px] font-bold font-mono mt-0.5 ${styles.cardText}`}>{getActionLabel(expandedEvent.action)}</p>
              </div>

              <div>
                <span className={`text-[9px] font-mono uppercase ${styles.cardTextMuted}`}>{t("sec.detail.params")}</span>
                <pre className={`mt-1 text-[10px] rounded-md p-2 overflow-x-auto font-mono max-h-[300px] overflow-y-auto ${styles.inputBg} ${styles.cardText}`}>
                  {typeof expandedEvent.details === "string" ? expandedEvent.details :
                    (expandedEvent.details ? JSON.stringify(expandedEvent.details, null, 2) : t("sec.detail.no_details"))}
                </pre>
              </div>

              <div>
                <span className={`text-[9px] font-mono uppercase ${styles.cardTextMuted}`}>{t("sec.detail.response")}</span>
                <p className={`text-[10px] font-mono mt-0.5 ${styles.cardText}`}>
                  {expandedEvent.result === "SUCCESS"
                    ? (locale === "zh" ? "操作执行成功。" : "Operation completed successfully.")
                    : (locale === "zh" ? "操作被拦截或待审批。" : "Operation blocked or pending approval.")}
                </p>
              </div>

              <div className={`pt-3 border-t ${styles.cardBorder} space-y-2`}>
                <div>
                  <span className={`text-[9px] font-mono uppercase flex items-center gap-1 ${styles.cardTextMuted}`}>
                    <Globe size={10} /> {t("sec.detail.ip")}
                  </span>
                  <p className={`text-[11px] font-mono mt-0.5 ${styles.cardText}`}>{expandedEvent.ipAddress || "—"}</p>
                </div>
                <div>
                  <span className={`text-[9px] font-mono uppercase flex items-center gap-1 ${styles.cardTextMuted}`}>
                    <Monitor size={10} /> {t("sec.detail.ua")}
                  </span>
                  <p className={`text-[10px] font-mono mt-0.5 break-all ${styles.cardText}`}>{expandedEvent.userAgent || "—"}</p>
                </div>
                <div>
                  <span className={`text-[9px] font-mono uppercase flex items-center gap-1 ${styles.cardTextMuted}`}>
                    <Timer size={10} /> {t("sec.detail.duration")}
                  </span>
                  <p className={`text-[11px] font-mono mt-0.5 ${styles.cardText}`}>
                    {expandedEvent.duration !== undefined ? `${expandedEvent.duration}${t("sec.detail.duration_unit")}` : "—"}
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* ── Pagination ──────────────────────────────── */}
      <div className={`mt-4 pt-3 border-t flex items-center justify-between shrink-0 ${styles.cardBorder}`}>
        {/* Total info */}
        <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
          {t("sec.pagination.total")} <span className={styles.cardText}>{total}</span> {t("sec.pagination.items")}
        </span>

        <div className="flex items-center gap-3">
          {/* Page size selector */}
          <div className="flex items-center gap-1.5">
            <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>{t("sec.pagination.per_page")}</span>
            <select
              value={pageSize}
              onChange={e => { setPageSize(Number(e.target.value)); setPage(1); }}
              className={`text-[10px] rounded border px-1.5 py-0.5 font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} outline-none`}
            >
              {PAGE_SIZE_OPTIONS.map(ps => (
                <option key={ps} value={ps}>{ps}</option>
              ))}
            </select>
          </div>

          {/* Page nav */}
          <div className="flex items-center gap-1">
            <button
              onClick={() => setPage(p => Math.max(1, p - 1))}
              disabled={page <= 1}
              className={`p-1 rounded transition-all cursor-pointer ${page <= 1 ? "opacity-30 cursor-not-allowed" : `hover:bg-white/10 ${styles.cardText}`}`}
            >
              <ChevronLeft size={14} />
            </button>
            <span className={`text-[10px] font-mono px-2 tabular-nums ${styles.cardText}`}>
              {page} <span className={styles.cardTextMuted}>/ {totalPages}</span>
            </span>
            <button
              onClick={() => setPage(p => Math.min(totalPages, p + 1))}
              disabled={page >= totalPages}
              className={`p-1 rounded transition-all cursor-pointer ${page >= totalPages ? "opacity-30 cursor-not-allowed" : `hover:bg-white/10 ${styles.cardText}`}`}
            >
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
