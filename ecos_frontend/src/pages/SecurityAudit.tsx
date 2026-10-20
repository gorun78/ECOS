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
import { fetchAuditLogs, fetchUsers, apiFetchData, extractErrorTraceId, isNoAccessError, AUDIT_STATS_BASE } from "../api";
import type { IamUser } from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import ErrorPanel from "../components/common/status/ErrorPanel";
import DeniedPanel from "../components/common/status/DeniedPanel";

const ACTION_OPTIONS = ["LOGIN", "QUERY", "MODIFY", "DELETE", "EXPORT"] as const;
const PAGE_SIZE_OPTIONS = [20, 50, 100] as const;

/** GET /api/v1/audit/stats — 直连传输原语（报告行283/305：现用 fetchAuditStats
 *  在 transport 层把失败吞成零值 → 「今日 0 条审计」假安静。此处不经吞错门面，
 *  失败上抛给页面渲染真错误态。基址自 #145/B6-13 起取 api 层族单源常量，
 *  本文件不再书写族根体（属主唯一），绕开门面的语义保持不变。） */
async function loadAuditStats(): Promise<AuditStats> {
  return apiFetchData<AuditStats>(AUDIT_STATS_BASE);
}

/** 本地错误描述（无假真理：失败必须带原因 + traceId，绝不回落空列表/零值） */
interface LoadFailure {
  message: string;
  traceId?: string;
  denied: boolean;
  offline: boolean;
}

function toFailure(e: unknown, fallbackMessage: string): LoadFailure {
  const denied = isNoAccessError(e);
  const message = e instanceof Error && e.message ? e.message : fallbackMessage;
  return {
    message: denied ? fallbackMessage : message,
    traceId: extractErrorTraceId(e),
    denied,
    offline: !denied && message === fallbackMessage,
  };
}

export default function SecurityAudit() {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // ── Data state ────────────────────────────────────
  const [auditLogs, setAuditLogs] = useState<AuditEvent[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState<number>(20);
  const [loading, setLoading] = useState(true);
  const [users, setUsers] = useState<IamUser[]>([]);
  // stats: null = 未到达/失败，0 值只在后端真实返回后才呈现（报告行305 假安静）
  const [stats, setStats] = useState<AuditStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [statsFailure, setStatsFailure] = useState<LoadFailure | null>(null);
  const [failure, setFailure] = useState<LoadFailure | null>(null);

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
    query?: { userId?: string; action?: string; dateFrom?: string; dateTo?: string },
  ) => {
    setLoading(true);
    setFailure(null);
    setStatsLoading(true);
    setStatsFailure(null);
    // 日期真正入请求（报告行281：筛选从未随 fetch 发出 = 假过滤）
    const startTime = query?.dateFrom ? `${query.dateFrom}T00:00:00` : undefined;
    const endTime = query?.dateTo ? `${query.dateTo}T23:59:59` : undefined;
    const [logsRes, statsRes] = await Promise.allSettled([
      fetchAuditLogs(
        query?.userId || undefined,
        query?.action || undefined,
        undefined,
        p,
        ps,
        startTime,
        endTime,
      ),
      loadAuditStats(),
    ]);
    if (logsRes.status === "fulfilled") {
      setAuditLogs(logsRes.value.data || []);
      setTotal(logsRes.value.total || 0);
    } else {
      // 失败不再 console.warn 后落「时间轴为空」（报告行282）：置错误态，保留重试
      setAuditLogs([]);
      setTotal(0);
      setFailure(toFailure(logsRes.reason, t("common.loadFailed")));
    }
    if (statsRes.status === "fulfilled") {
      setStats(statsRes.value);
    } else {
      setStats(null);
      setStatsFailure(toFailure(statsRes.reason, t("sec.stats.failed")));
    }
    setLoading(false);
    setStatsLoading(false);
  }, [page, pageSize, t]);

  // Load users on mount（用户列表失败仅降级下拉框，不阻断主面）
  useEffect(() => {
    fetchUsers(undefined, 1, 200).then(res => {
      setUsers(res.data || []);
    }).catch(() => setUsers([]));
  }, []);

  // Load audit logs + stats on mount and when filters/page change
  useEffect(() => {
    loadData(page, pageSize, {
      userId: filterUserId,
      action: filterAction,
      dateFrom: filterDateFrom,
      dateTo: filterDateTo,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize]);

  const currentQuery = () => ({
    userId: filterUserId,
    action: filterAction,
    dateFrom: filterDateFrom,
    dateTo: filterDateTo,
  });

  // Re-fetch when filters change (reset page to 1)
  const handleSearch = () => {
    setPage(1);
    loadData(1, pageSize, currentQuery());
  };

  const handleReset = () => {
    setFilterUserId("");
    setFilterAction("");
    setFilterDateFrom("");
    setFilterDateTo("");
    setFilterIp("");
    setPage(1);
    loadData(1, pageSize, {});
  };

  const retry = () => loadData(page, pageSize, currentQuery());

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

    // IP 不在后端 /audit/logs 参数面内（实测：userId/action/resourceType/startTime/
    // endTime/page/pageSize），故只作当前页客户端过滤，并在 UI 明示作用域，禁伪装成全量筛选。
    const ipNeedle = filterIp.trim();
    const scoped = ipNeedle
      ? auditLogs.filter(log => (log.ipAddress || "").includes(ipNeedle))
      : auditLogs;

    scoped.forEach(log => {
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
  }, [auditLogs, filterIp, t]);

  // ── CSV export ────────────────────────────────────
  // 数据源 = 当前页 auditLogs（未全量拉取），按钮 title 明示范围（报告行307）。
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

  /**
   * 结果徽章（报告行283：FAILURE 曾渲染 `sec.tb.status.pending`「审计合规待批」
   * —— 安全事件语义颠倒）。口径：SUCCESS=已提交 / FAILURE=失败（danger）/
   * PENDING=待签批（warning）/ 其余原值呈现，禁兜底成成功或 pending。
   */
  const getResultBadge = (result: string) => {
    const normalized = (result || "").toUpperCase();
    if (normalized === "SUCCESS") {
      return (
        <span className={`px-2 py-0.5 rounded text-[10px] font-bold border ${styles.successBg} ${styles.successText} ${styles.successBorder}`}>
          {t("sec.ledger.committed")}
        </span>
      );
    }
    if (normalized === "PENDING") {
      return (
        <span className={`px-2 py-0.5 rounded text-[10px] font-bold border ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
          {t("sec.ledger.pending")}
        </span>
      );
    }
    return (
      <span className={`px-2 py-0.5 rounded text-[10px] font-bold border ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
        {normalized === "FAILURE" ? t("sec.tb.status.failure") : (result || t("common.na"))}
      </span>
    );
  };

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  /**
   * 详情面板的「响应说明」文案：与徽章同一口径，非成功态不得一律渲染成
   * 「操作被拦截或待审批」（sec.audit.blocked），避免把失败伪装成拦截。
   */
  const getResultSentence = (result?: string) => {
    const normalized = (result || "").toUpperCase();
    if (normalized === "SUCCESS") return t("sec.audit.successVoucher");
    if (normalized === "BLOCKED" || normalized === "DENIED" || normalized === "PENDING") {
      return t("sec.audit.blocked");
    }
    return `${t("sec.tb.status.failure")}（${result || t("common.na")}）`;
  };

  const isResultSuccess = (result?: string) => (result || "").toUpperCase() === "SUCCESS";

  // ── Detail panel data ─────────────────────────────
  const expandedEvent = auditLogs.find(e => e.eventId === expandedEventId);

  // ── Render ────────────────────────────────────────
  return (
    <div className={`flex-1 overflow-y-auto p-5 flex flex-col h-full font-sans ${styles.appBg} ${styles.appText}`}>
      {/* ── Header ────────────────────────────────── */}
      <div className="flex justify-between items-center mb-4 shrink-0">
        <div>
          <h1 className={`text-xl font-bold tracking-tight flex items-center gap-2 ${styles.cardText}`}>
            <Shield className={`${styles.accentText} w-5 h-5 shrink-0`} />
            {t("sec.title")}
          </h1>
          <p className={`text-xs mt-0.5 ${styles.cardTextMuted}`}>{t("sec.desc")}</p>
        </div>
        <button type="button"
          onClick={exportCsv}
          disabled={auditLogs.length === 0}
          title={t("sec.export.csv_scope_note")}
          className={`px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer border
            ${styles.infoBg} ${styles.infoText} ${styles.infoBorder} ${styles.accentHover}
            ${auditLogs.length === 0 ? "opacity-50 cursor-not-allowed" : ""}`}
        >
          <Download size={14} />
          {t("sec.export.csv")}
        </button>
      </div>

      {/* ── Stats cards ────────────────────────────── */}
      {statsFailure ? (
        <div className="mb-4 shrink-0">
          <ErrorPanel
            reason={statsFailure.message}
            traceId={statsFailure.traceId}
            onRetry={retry}
          />
        </div>
      ) : (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-4 shrink-0">
          {([
            { icon: <Activity size={16} />, label: t("sec.stats.today"), value: stats?.todayCount, accent: styles.accentText },
            { icon: <AlertTriangle size={16} />, label: t("sec.stats.failures"), value: stats?.failureCount, accent: styles.dangerText },
            { icon: <Users size={16} />, label: t("sec.stats.active_users"), value: stats?.activeUsers, accent: styles.successText },
            { icon: <Globe size={16} />, label: t("sec.stats.anomaly_ips"), value: stats?.anomalyIps, accent: styles.warningText },
          ]).map((card, i) => (
            <div key={i} className={`rounded-lg border p-3 flex items-center gap-3 ${styles.cardBg} ${styles.cardBorder}`}>
              <div className={`shrink-0 ${card.accent}`}>{card.icon}</div>
              <div className="min-w-0">
                <p className={`text-[10px] font-mono uppercase tracking-wider ${styles.cardTextMuted}`}>{card.label}</p>
                {statsLoading || stats === null ? (
                  // 加载/未到达态：不渲染 0（报告行305：后端不可用时「今日 0 条审计」= 假安静）
                  <p className={`text-lg font-bold ${styles.cardTextMuted}`} aria-busy="true">—</p>
                ) : (
                  <p className={`text-lg font-bold tabular-nums font-mono ${styles.cardText}`}>{card.value?.toLocaleString() ?? t("common.na")}</p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ── Filter bar ─────────────────────────────── */}
      <div className={`rounded-lg border p-3 mb-4 shrink-0 ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center gap-2 mb-2">
          <button type="button"
            onClick={() => setShowFilters(!showFilters)}
            className={`flex items-center gap-1 text-xs font-bold transition-colors cursor-pointer ${styles.cardTextMuted} ${styles.sidebarHoverBg}`}
          >
            <Filter size={13} />
            {t("sec.audit.filters")}
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
                className={`text-xs rounded-md border px-2 py-1.5 min-w-[140px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} outline-none focus:ring-1 ${styles.accentRing}`}
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
                className={`text-xs rounded-md border px-2 py-1.5 min-w-[120px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} outline-none focus:ring-1 ${styles.accentRing}`}
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
                className={`text-xs rounded-md border px-2 py-1.5 ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} outline-none focus:ring-1 ${styles.accentRing}`}
              />
            </div>

            {/* Date to */}
            <div className="flex flex-col gap-1">
              <label className={`text-[10px] font-mono uppercase ${styles.cardTextMuted}`}>{t("sec.filter.date_to")}</label>
              <input
                type="date"
                value={filterDateTo}
                onChange={e => setFilterDateTo(e.target.value)}
                className={`text-xs rounded-md border px-2 py-1.5 ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} outline-none focus:ring-1 ${styles.accentRing}`}
              />
            </div>

            {/* IP input（后端 /audit/logs 无 IP 参数面 → 当前页客户端过滤，作用域已标注） */}
            <div className="flex flex-col gap-1">
              <label className={`text-[10px] font-mono uppercase ${styles.cardTextMuted}`}>{t("sec.filter.ip")}</label>
              <input
                type="text"
                value={filterIp}
                onChange={e => setFilterIp(e.target.value)}
                placeholder={t("sec.filter.ip_placeholder")}
                className={`text-xs rounded-md border px-2 py-1.5 w-[140px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} outline-none focus:ring-1 ${styles.accentRing}`}
              />
              {filterIp.trim() !== "" && (
                <span className={`text-[9px] ${styles.warningText}`}>{t("sec.filter.ip_scope_note")}</span>
              )}
            </div>

            {/* Buttons */}
            <div className="flex gap-2">
              <button type="button"
                onClick={handleSearch}
                className={`px-3 py-1.5 rounded-md text-xs font-bold transition-all flex items-center gap-1 cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentHover}`}
              >
                <Search size={13} />
                {t("sec.filter.search")}
              </button>
              <button type="button"
                onClick={handleReset}
                className={`px-3 py-1.5 rounded-md text-xs font-bold border transition-all flex items-center gap-1 cursor-pointer ${styles.cardTextMuted} ${styles.cardBorder} ${styles.sidebarHoverBg}`}
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
          ) : failure ? (
            /* 失败态（报告行282：错误不得冒充「时间轴为空」）：原因 + traceId + 重试 */
            failure.denied ? (
              <DeniedPanel missingPermissions={[failure.message]} traceId={failure.traceId} />
            ) : (
              <ErrorPanel reason={failure.message} traceId={failure.traceId} onRetry={retry} />
            )
          ) : timelineGroups.length === 0 ? (
            <div className="flex items-center justify-center h-32">
              <span className={`text-xs font-mono ${styles.cardTextMuted}`}>
                {filterIp.trim() !== "" ? t("sec.filter.no_match_current_page") : t("sec.timeline.empty")}
              </span>
            </div>
          ) : (
            <div className="relative pl-6">
              {/* Vertical line */}
              <div className={`absolute left-[11px] top-0 bottom-0 w-0.5 ${styles.cardBorder}`} />

              {timelineGroups.map((group, gi) => (
                <div key={group.labelKey} className="mb-6 last:mb-0">
                  {/* Group header */}
                  <div className="flex items-center gap-2 mb-3 -ml-[26px]">
                    <div className={`w-3 h-3 rounded-full ${styles.accentBg} ${styles.accentBorder} border-2`} />
                    <span className={`text-[11px] font-bold uppercase tracking-wider ${styles.cardText}`}>
                      {group.label}
                    </span>
                    <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
                      {group.events.length} {t("sec.audit.unit")}
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
                        <div className={`absolute left-[-26px] top-3 w-2 h-2 rounded-full ${styles.accentBg} ${styles.accentBorder} border`} />

                        {/* Event card：button + aria-expanded（报告行307：div onClick 无键盘通路） */}
                        <button
                          type="button"
                          aria-expanded={isExpanded}
                          aria-controls={`audit-detail-${log.eventId}`}
                          onClick={() => setExpandedEventId(isExpanded ? null : log.eventId)}
                          className={`w-full text-left rounded-md border p-3 cursor-pointer transition-all ${
                            isExpanded ? `${styles.accentBorder} ${styles.badgeBg}` : `${styles.cardBorder} ${styles.sidebarHoverBg}`
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
                            <span>{t("sec.audit.actor")}
                              <span className={`${styles.accentText} font-semibold`}>{log.userId}</span>
                            </span>
                            {log.ipAddress && (
                              <span>IP: <span className={styles.cardText}>{log.ipAddress}</span></span>
                            )}
                            {log.duration !== undefined && (
                              <span className="flex items-center gap-1">
                                <Timer size={10} /> {log.duration}{t("sec.detail.duration_unit")}
                              </span>
                            )}
                            <span className={`ml-auto ${styles.cardTextMuted}`}>{log.eventId}</span>
                          </div>

                          {/* Expand indicator */}
                          <div className={`text-[10px] mt-1.5 flex items-center gap-1 ${styles.cardTextMuted}`}>
                            {isExpanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                            <span className="opacity-60">{isExpanded ? t("sec.detail.close") : t("sec.audit.details")}</span>
                          </div>

                          {/* Expanded detail content */}
                          {isExpanded && (
                            <div id={`audit-detail-${log.eventId}`} className={`mt-3 pt-3 border-t space-y-2 ${styles.cardBorder}`}>
                              <p className={`text-[10px] font-bold uppercase font-mono flex items-center gap-1.5 ${styles.cardText}`}>
                                <FileJson size={12} className={styles.warningText} />
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
                                <p className={`text-[10px] font-mono mt-0.5 ${isResultSuccess(log.result) ? styles.successText : styles.dangerText}`}>
                                  {getResultSentence(log.result)}
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
                        </button>
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
                <FileJson size={13} className={styles.warningText} />
                {t("sec.detail.title")}
              </h3>
              <button type="button"
                onClick={() => setExpandedEventId(null)}
                className={`p-1 rounded cursor-pointer ${styles.sidebarHoverBg} ${styles.cardTextMuted}`}
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
                <p className={`text-[10px] font-mono mt-0.5 ${isResultSuccess(expandedEvent.result) ? styles.successText : styles.dangerText}`}>
                  {getResultSentence(expandedEvent.result)}
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
            <button type="button"
              onClick={() => setPage(p => Math.max(1, p - 1))}
              disabled={page <= 1}
              className={`p-1 rounded transition-all cursor-pointer ${page <= 1 ? "opacity-30 cursor-not-allowed" : `${styles.sidebarHoverBg} ${styles.cardText}`}`}
            >
              <ChevronLeft size={14} />
            </button>
            <span className={`text-[10px] font-mono px-2 tabular-nums ${styles.cardText}`}>
              {page} <span className={styles.cardTextMuted}>/ {totalPages}</span>
            </span>
            <button type="button"
              onClick={() => setPage(p => Math.min(totalPages, p + 1))}
              disabled={page >= totalPages}
              className={`p-1 rounded transition-all cursor-pointer ${page >= totalPages ? "opacity-30 cursor-not-allowed" : `${styles.sidebarHoverBg} ${styles.cardText}`}`}
            >
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
