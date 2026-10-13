/**
 * 告警 / DLQ 控制台 · 主入口路由页
 * @license SPDX-License-Identifier: Apache-2.0
 *
 * 独立顶级路由 /alerts-center。两个 Tab：
 *   - alerts: GET /api/v1/monitor/alerts（severity/status 过滤 chip + 分页 + 15s 自动轮询，
 *     document.hidden 时暂停）；ack 按钮仅 L2+ 可见。
 *   - dlq:    GET /api/v1/monitor/dlq（replay 按钮，L3+）。
 *
 * 移动端 (<768px) 自动降级 MobileDataTable 卡片态；桌面走 DataTable。
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Bell,
  Inbox,
  RefreshCw,
  RotateCcw,
  ShieldAlert,
  Wallet,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import DataTable, { type ColumnConfig } from "../../components/common/DataTable";
import MobileDataTable from "../../components/common/MobileDataTable";
import { isNoAccessError, fetchUserSecurityProfile } from "../../api";
import {
  fetchMonitorAlerts,
  ackMonitorAlert,
  fetchDlq,
  replayDlq,
  type MonitorAlert,
  type DlqRecord,
} from "../../services/platformApi";
import AlertRow from "./AlertRow";
import AlertDetailDrawer from "./AlertDetailDrawer";

type Section = "alerts" | "dlq";

/** Read current user's clearance level (L2+ → can ack, L3+ → can replay). */
function useUserClearance(): number {
  const [level, setLevel] = useState<number>(() => {
    const ids = localStorage.getItem("ecos_user_id");
    const cached = localStorage.getItem("ecos_user_clearance");
    if (cached) return parseInt(cached, 10) || 0;
    return 0;
  });
  useEffect(() => {
    const userId = localStorage.getItem("ecos_user_id") || "";
    if (!userId) return;
    fetchUserSecurityProfile(userId)
      .then((p) => {
        const lvl = p?.clearanceLevel;
        if (typeof lvl === "number") {
          setLevel(lvl);
          try { localStorage.setItem("ecos_user_clearance", String(lvl)); } catch { /* ignore */ }
        }
      })
      .catch(() => {/* fallback to cached */});
  }, []);
  return level;
}

export default function AlertsPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const clearance = useUserClearance();
  const canAck = clearance >= 2;
  const canReplay = clearance >= 3;

  const [section, setSection] = useState<Section>("alerts");

  // ── Alerts state ────────────────────────────────────────────────
  const [alerts, setAlerts] = useState<MonitorAlert[]>([]);
  const [alertTotal, setAlertTotal] = useState(0);
  const [severity, setSeverity] = useState("");
  const [status, setStatus] = useState("");
  const [alertPage, setAlertPage] = useState(1);
  const [alertLoading, setAlertLoading] = useState(true);
  const [noAccess, setNoAccess] = useState(false);
  const [alertError, setAlertError] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [selected, setSelected] = useState<MonitorAlert | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // loadAlerts reads state directly (severity/status/alertPage) so page/filter
  // changes always trigger a fresh fetch — no render-time refs (refs updated during
  // render never re-run the effect, and a stale ref caused loads with the wrong page).
  const loadAlerts = useCallback(async (silent = false) => {
    if (!silent) setAlertLoading(true);
    setAlertError("");
    setNoAccess(false);
    try {
      const data = await fetchMonitorAlerts({
        severity: severity || undefined,
        status: status || undefined,
        page: alertPage,
        size: 10,
      });
      setAlerts(data.rows);
      setAlertTotal(data.total);
    } catch (e) {
      if (isNoAccessError(e)) setNoAccess(true);
      else if (!silent) setAlertError(e instanceof Error ? e.message : "error");
    } finally {
      if (!silent) setAlertLoading(false);
    }
  }, [severity, status, alertPage]);

  // Runs whenever section / filters / page change (loadAlerts identity flips),
  // plus a 15s silent poll that pauses while the tab is backgrounded.
  useEffect(() => {
    if (section !== "alerts") return;
    void loadAlerts(false);
    const interval = setInterval(() => {
      // PAUSE when document.hidden — skip a poll tick while the tab is background
      if (document.hidden) return;
      void loadAlerts(true);
    }, 15000);
    timerRef.current = interval;
    return () => clearInterval(interval);
  }, [section, loadAlerts]);

  const handleAck = useCallback(
    async (id: string) => {
      setBusyId(id);
      try {
        await ackMonitorAlert(id);
        setAlerts((prev) =>
          prev.map((a) => (a.id === id ? { ...a, status: "ACKED", ack_time: new Date().toISOString() } : a)),
        );
        setSelected((prev) => (prev?.id === id ? { ...prev, status: "ACKED" } : prev));
      } catch (e) {
        if (!isNoAccessError(e)) setAlertError(e instanceof Error ? e.message : "error");
      } finally {
        setBusyId(null);
      }
    },
    [],
  );

  // ── DLQ state ───────────────────────────────────────────────────
  const [dlq, setDlq] = useState<DlqRecord[]>([]);
  const [dlqTotal, setDlqTotal] = useState(0);
  const [dlqStatus, setDlqStatus] = useState("");
  const [dlqPage, setDlqPage] = useState(1);
  const [dlqLoading, setDlqLoading] = useState(false);
  const [dlqError, setDlqError] = useState("");
  const [dlqBusyId, setDlqBusyId] = useState<string | null>(null);

  // Reads state directly (dlqStatus/dlqPage); see loadAlerts above for rationale.
  const loadDlq = useCallback(async (silent = false) => {
    if (!silent) setDlqLoading(true);
    setDlqError("");
    try {
      const data = await fetchDlq({
        status: dlqStatus || undefined,
        page: dlqPage,
        size: 10,
      });
      setDlq(data.rows);
      setDlqTotal(data.total);
    } catch (e) {
      if (isNoAccessError(e)) setNoAccess(true);
      else if (!silent) setDlqError(e instanceof Error ? e.message : "error");
    } finally {
      if (!silent) setDlqLoading(false);
    }
  }, [dlqStatus, dlqPage]);

  useEffect(() => {
    if (section !== "dlq") return;
    void loadDlq(false);
    const interval = setInterval(() => {
      if (document.hidden) return;
      void loadDlq(true);
    }, 15000);
    timerRef.current = interval;
    return () => {
      clearInterval(interval);
      if (timerRef.current === interval) timerRef.current = null;
    };
  }, [section, loadDlq]);

  const handleReplay = useCallback(
    async (id: string) => {
      setDlqBusyId(id);
      try {
        await replayDlq(id);
        setDlq((prev) =>
          prev.map((r) =>
            r.id === id ? { ...r, status: "REPLAYED", replayed_at: new Date().toISOString() } : r,
          ),
        );
      } catch (e) {
        if (!isNoAccessError(e)) setDlqError(e instanceof Error ? e.message : "error");
      } finally {
        setDlqBusyId(null);
      }
    },
    [],
  );

  // ── Column configs for DataTable / MobileDataTable ─────────────
  const alertColumns: ColumnConfig<Record<string, any>>[] = useMemo(
    () => [
      {
        key: "severity",
        label: t("platform.alerts.col.severity"),
        render: (v: string) => (
          <span
            className={[
              "inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold uppercase border",
              v?.toUpperCase() === "CRITICAL" || v?.toUpperCase() === "ERROR"
                ? `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`
                : v?.toUpperCase() === "HIGH" || v?.toUpperCase() === "WARN"
                  ? `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`
                  : `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`,
            ].join(" ")}
          >
            {v}
          </span>
        ),
      },
      { key: "title", label: t("platform.alerts.col.title"), render: (v: string, r: MonitorAlert) => v || r.rule_code },
      {
        key: "status",
        label: t("platform.alerts.col.status"),
        render: (v: string) => (
          <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] ${styles.badgeBg} ${styles.badgeText} border ${styles.appBorder}`}>
            {t("platform.alerts.status." + (v || "").toLowerCase()) || v}
          </span>
        ),
      },
      { key: "occurred_at", label: t("platform.alerts.col.time"), render: (v: string) => v?.replace("T", " ").slice(0, 19) },
      {
        key: "ops",
        label: t("platform.alerts.col.actions"),
        align: "right",
        render: (_v, r: MonitorAlert) => (
          <button
            type="button"
            disabled={!canAck || !!busyId || r.status === "CLOSED" || r.status === "ACKED"}
            onClick={() => void handleAck(r.id)}
            className={`inline-flex items-center gap-1 rounded px-2 py-0.5 text-[10px] font-medium border transition ${
              canAck && r.status !== "CLOSED" && r.status !== "ACKED"
                ? `${styles.successBorder} ${styles.successText} hover:opacity-80 cursor-pointer`
                : `${styles.appBorder} ${styles.cardTextMuted} opacity-40 cursor-not-allowed`
            }`}
          >
            <RefreshCw className={`w-3 h-3 ${busyId === r.id ? "animate-spin" : ""}`} />
            {t("platform.alerts.ack")}
          </button>
        ),
      },
    ],
    [t, styles, canAck, busyId, handleAck],
  );

  const dlqColumns: ColumnConfig<Record<string, any>>[] = useMemo(
    () => [
      { key: "topic", label: t("platform.alerts.dlq.col.topic") },
      { key: "dlt_topic", label: t("platform.alerts.dlq.col.dltTopic") },
      {
        key: "attempts",
        label: t("platform.alerts.dlq.col.attempts"),
        align: "right",
        render: (v: number) => String(v ?? 0),
      },
      {
        key: "status",
        label: t("platform.alerts.dlq.col.status"),
        render: (v: string) => (
          <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] ${styles.badgeBg} ${styles.badgeText} border ${styles.appBorder}`}>
            {v || "—"}
          </span>
        ),
      },
      {
        key: "replay",
        label: t("platform.alerts.col.actions"),
        align: "right",
        render: (_v: unknown, r: DlqRecord) => (
          <button
            type="button"
            disabled={!canReplay || !!dlqBusyId || r.status === "REPLAYED"}
            onClick={() => void handleReplay(r.id)}
            className={`inline-flex items-center gap-1 rounded px-2 py-0.5 text-[10px] font-medium border transition ${
              canReplay && r.status !== "REPLAYED"
                ? `${styles.infoBorder} ${styles.infoText} hover:opacity-80 cursor-pointer`
                : `${styles.appBorder} ${styles.cardTextMuted} opacity-40 cursor-not-allowed`
            }`}
          >
            <RotateCcw className={`w-3 h-3 ${dlqBusyId === r.id ? "animate-spin" : ""}`} />
            {t("platform.alerts.dlq.replay")}
          </button>
        ),
      },
    ],
    [t, styles, canReplay, dlqBusyId, handleReplay],
  );

  return (
    <div className={`flex-1 min-h-0 flex flex-col overflow-hidden ${styles.appBg}`}>
      {/* header */}
      <div className="shrink-0 px-6 pt-4 pb-3 flex items-start justify-between gap-4">
        <div className="flex items-center gap-2">
          <Bell className={`w-5 h-5 ${styles.accentText}`} />
          <h1 className="font-bold text-xl tracking-tight">{t("platform.alerts.title")}</h1>
        </div>
        {!canAck && (
          <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] border ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
            <ShieldAlert className="w-3 h-3" />
            {t("platform.alerts.requiresL2")}
          </span>
        )}
      </div>

      {/* section tabs */}
      <div className={`shrink-0 border-b ${styles.cardBorder} ${styles.cardBg}`}>
        <div className="px-6 flex items-center gap-0.5">
          {(["alerts", "dlq"] as Section[]).map((s) => {
            const Icon = s === "alerts" ? Bell : Wallet;
            const active = section === s;
            return (
              <button
                key={s}
                type="button"
                onClick={() => setSection(s)}
                className={`h-10 px-3 inline-flex items-center gap-2 text-xs font-medium transition cursor-pointer ${
                  active
                    ? `${styles.cardText} font-bold border-b-2 border-current ${styles.accentText}`
                    : `${styles.cardTextMuted} border-b-2 border-transparent hover:opacity-100 opacity-70`
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                {t("platform.alerts.tab." + s)}
              </button>
            );
          })}
        </div>
      </div>

      {/* alert pills (severity + status) */}
      {section === "alerts" && (
        <div className="shrink-0 px-6 py-3 flex flex-wrap items-center gap-2">
          <div className="flex items-center gap-1 flex-wrap">
            {["", "CRITICAL", "HIGH", "MEDIUM", "LOW"].map((s) => {
              const active = severity === s;
              return (
                <button
                  key={s || "all"}
                  type="button"
                  onClick={() => {
                    setSeverity(s);
                    setAlertPage(1);
                  }}
                  className={`h-6 px-2.5 rounded-full border text-[10px] font-medium transition cursor-pointer ${
                    active
                      ? `${styles.accentBg} ${styles.accentBorder} text-white`
                      : `${styles.appBorder} ${styles.cardTextMuted} hover:opacity-80`
                  }`}
                >
                  {t(s ? `platform.alerts.severity.${s.toLowerCase()}` : "platform.alerts.filter.all")}
                </button>
              );
            })}
          </div>
          <span className={`h-4 w-px ${styles.appBorder}`} />
          <div className="flex items-center gap-1 flex-wrap">
            {["", "PENDING", "NOTIFIED", "ACKED", "CLOSED"].map((s) => {
              const active = status === s;
              return (
                <button
                  key={s || "all"}
                  type="button"
                  onClick={() => {
                    setStatus(s);
                    setAlertPage(1);
                  }}
                  className={`h-6 px-2.5 rounded-full border text-[10px] font-medium transition cursor-pointer ${
                    active
                      ? `${styles.accentBg} ${styles.accentBorder} text-white`
                      : `${styles.appBorder} ${styles.cardTextMuted} hover:opacity-80`
                  }`}
                >
                  {t(s ? `platform.alerts.status.${s.toLowerCase()}` : "platform.alerts.filter.all")}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* dlq status filter */}
      {section === "dlq" && (
        <div className="shrink-0 px-6 py-3 flex flex-wrap items-center gap-2">
          {["", "PENDING", "REPLAYED", "FAILED"].map((s) => {
            const active = dlqStatus === s;
            return (
              <button
                key={s || "all"}
                type="button"
                onClick={() => {
                  setDlqStatus(s);
                  setDlqPage(1);
                }}
                className={`h-6 px-2.5 rounded-full border text-[10px] font-medium transition cursor-pointer ${
                  active
                    ? `${styles.accentBg} ${styles.accentBorder} text-white`
                    : `${styles.appBorder} ${styles.cardTextMuted} hover:opacity-80`
                }`}
              >
                {t(s ? `platform.alerts.dlq.status.${s.toLowerCase()}` : "platform.alerts.filter.all")}
              </button>
            );
          })}
        </div>
      )}

      {/* body */}
      <div className="flex-1 min-h-0 overflow-hidden px-6 pb-4">
        <div className="max-w-7xl mx-auto h-full flex flex-col">
          {noAccess ? (
            <div className="flex-1 flex flex-col items-center justify-center gap-3 text-center">
              <ShieldAlert className={`w-9 h-9 ${styles.warningText}`} />
              <span className={`text-sm font-bold ${styles.cardText}`}>{t("platform.alerts.noAccess.title")}</span>
              <span className={`text-xs max-w-sm ${styles.cardTextMuted}`}>{t("platform.alerts.noAccess.desc")}</span>
            </div>
          ) : section === "alerts" ? (
            <AlertsBody
              alerts={alerts}
              total={alertTotal}
              page={alertPage}
              pageSize={10}
              loading={alertLoading}
              error={alertError}
              canAck={canAck}
              busyId={busyId}
              t={t}
              onAck={handleAck}
              onOpen={setSelected}
              onPageChange={setAlertPage}
              onRetry={() => void loadAlerts(false)}
              alertColumns={alertColumns}
            />
          ) : (
            <DlqBody
              rows={dlq as unknown as Record<string, any>[]}
              total={dlqTotal}
              page={dlqPage}
              pageSize={10}
              loading={dlqLoading}
              error={dlqError}
              canReplay={canReplay}
              busyId={dlqBusyId}
              t={t}
              onPageChange={setDlqPage}
              onRetry={() => void loadDlq(false)}
              columns={dlqColumns}
            />
          )}
        </div>
      </div>

      {/* Detail drawer */}
      <AlertDetailDrawer
        alert={selected}
        canAck={canAck}
        busy={busyId === selected?.id}
        onAck={handleAck}
        onClose={() => setSelected(null)}
      />
    </div>
  );
}

// ── Alerts body: mobile cards + AlertRow + pagination ──────────────

function AlertsBody({
  alerts,
  total,
  page,
  pageSize,
  loading,
  error,
  canAck,
  busyId,
  t,
  onAck,
  onOpen,
  onPageChange,
  onRetry,
  alertColumns,
}: {
  alerts: MonitorAlert[];
  total: number;
  page: number;
  pageSize: number;
  loading: boolean;
  error: string;
  canAck: boolean;
  busyId: string | null;
  t: (k: string) => string;
  onAck: (id: string) => void;
  onOpen: (a: MonitorAlert) => void;
  onPageChange: (p: number) => void;
  onRetry: () => void;
  alertColumns: ColumnConfig<Record<string, any>>[];
}) {
  const { styles } = useTheme();
  const { isMobile } = useMobileBreakpoint();

  // Server-paged: `alerts` is already the current page of rows; use total for page math.
  const totalPages = Math.max(1, Math.ceil((total || 0) / pageSize));
  const pageRows = alerts;

  if (loading && alerts.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-3">
        <RefreshCw className={`w-6 h-6 animate-spin ${styles.accentText}`} />
        <span className={`text-xs ${styles.cardTextMuted}`}>{t("common.loading")}</span>
      </div>
    );
  }
  if (error && alerts.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-3 text-center">
        <ShieldAlert className={`w-8 h-8 ${styles.dangerText}`} />
        <span className={`text-xs max-w-md ${styles.cardTextMuted}`}>{error}</span>
        <button
          type="button"
          onClick={onRetry}
          className={`mt-2 inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold ${styles.accentBg} ${styles.accentHover} text-white rounded-lg cursor-pointer`}
        >
          <RefreshCw className="w-3.5 h-3.5" />
          {t("platform.alerts.retry")}
        </button>
      </div>
    );
  }
  if (!alerts.length) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-3">
        <Bell className={`w-10 h-10 ${styles.cardTextMuted} opacity-40`} />
        <span className={`text-xs ${styles.cardTextMuted}`}>{t("platform.alerts.empty")}</span>
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0 flex flex-col">
      {/*
        Two row renderers by design (low-risk unification, kept intentionally):
          - desktop: AlertRow (rich custom layout, own ack button + severity/status styling)
          - mobile:  MobileDataTable driven by `alertColumns`
        Both paths use the SAME theme tokens (danger/warning/info badge classes) and the
        SAME ack gating (canAck && status∉{CLOSED,ACKED} && !busy), so behaviour and look
        stay consistent across breakpoints without a risky shared-row refactor.
      */}
      <div className={`flex-1 min-h-0 overflow-y-auto rounded-lg border ${styles.cardBorder} ${styles.cardBg}`}>
        {isMobile ? (
          <MobileDataTable<Record<string, any>>
            columns={alertColumns}
            data={pageRows as unknown as Record<string, any>[]}
            rowKey={(r) => r.id as string}
            currentPage={page}
            total={total}
            pageSize={pageSize}
            hidePagination
            onRowClick={(r) => onOpen(r as unknown as MonitorAlert)}
            mobileConfig={{
              headerKeys: ["severity", "title"],
              detailKeys: ["status", "occurred_at"],
              actionColumnKey: "ops",
            }}
          />
        ) : (
          <div>
            {pageRows.map((a) => (
              <AlertRow
                key={a.id}
                alert={a}
                canAck={canAck}
                busy={busyId === a.id}
                onAck={onAck}
                onOpen={onOpen}
              />
            ))}
          </div>
        )}
      </div>
      {/* custom pagination (MobileDataTable/AlertRow paths manage their own row display) */}
      <div className="shrink-0 mt-3">
        <MobilePagination page={page} total={totalPages} onPageChange={onPageChange} t={t} />
      </div>
    </div>
  );
}

// ── DLQ body ───────────────────────────────────────────────────────

function DlqBody({
  rows,
  total,
  page,
  pageSize,
  loading,
  error,
  canReplay,
  busyId,
  t,
  onPageChange,
  onRetry,
  columns,
}: {
  rows: Record<string, any>[];
  total: number;
  page: number;
  pageSize: number;
  loading: boolean;
  error: string;
  canReplay: boolean;
  busyId: string | null;
  t: (k: string) => string;
  onPageChange: (p: number) => void;
  onRetry: () => void;
  columns: ColumnConfig<Record<string, any>>[];
}) {
  const { styles } = useTheme();
  const { isMobile } = useMobileBreakpoint();

  // Server-paged: `rows` is already the current page; use total for page math.
  const totalPages = Math.max(1, Math.ceil((total || 0) / pageSize));
  const pageRows = rows;

  if (loading && rows.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-3">
        <RefreshCw className={`w-6 h-6 animate-spin ${styles.accentText}`} />
        <span className={`text-xs ${styles.cardTextMuted}`}>{t("common.loading")}</span>
      </div>
    );
  }
  if (error && rows.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-3 text-center">
        <ShieldAlert className={`w-8 h-8 ${styles.dangerText}`} />
        <span className={`text-xs max-w-md ${styles.cardTextMuted}`}>{error}</span>
        <button
          type="button"
          onClick={onRetry}
          className={`mt-2 inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold ${styles.accentBg} ${styles.accentHover} text-white rounded-lg cursor-pointer`}
        >
          <RefreshCw className="w-3.5 h-3.5" />
          {t("platform.alerts.retry")}
        </button>
      </div>
    );
  }
  if (!rows.length) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-3">
        <Inbox className={`w-10 h-10 ${styles.cardTextMuted} opacity-40`} />
        <span className={`text-xs ${styles.cardTextMuted}`}>{t("platform.alerts.dlq.empty")}</span>
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0 flex flex-col">
      <div className={`flex-1 min-h-0 overflow-y-auto rounded-lg border ${styles.cardBorder} ${styles.cardBg}`}>
        {isMobile ? (
          <MobileDataTable<Record<string, any>>
            columns={columns}
            data={pageRows}
            rowKey={(r) => r.id as string}
            currentPage={page}
            total={total}
            pageSize={pageSize}
            hidePagination
            mobileConfig={{
              headerKeys: ["topic", "status"],
              detailKeys: ["dlt_topic", "error_message"],
              actionColumnKey: "replay",
            }}
          />
        ) : (
          <DataTable<Record<string, any>>
            columns={columns}
            data={pageRows}
            rowKey={(r) => r.id as string}
            currentPage={page}
            total={total}
            pageSize={pageSize}
            hidePagination
          />
        )}
      </div>
      <div className={`mt-3 shrink-0`}>
        <MobilePagination page={page} total={totalPages} onPageChange={onPageChange} t={t} />
      </div>
    </div>
  );
}

// ── Shared pagination (reuses DataTable's own but hides if we pass hidePagination) ──

function MobilePagination({
  page,
  total,
  onPageChange,
  t,
}: {
  page: number;
  total: number;
  onPageChange: (p: number) => void;
  t: (k: string) => string;
}) {
  const { styles } = useTheme();
  if (total <= 1) return null;
  return (
    <div className="flex items-center justify-end gap-2">
      <button
        type="button"
        disabled={page <= 1}
        onClick={() => onPageChange(page - 1)}
        className={`h-7 px-2.5 rounded-md border text-[11px] font-medium cursor-pointer transition disabled:opacity-40 disabled:cursor-not-allowed ${styles.appBorder} ${styles.cardTextMuted} hover:opacity-80`}
      >
        &lt; {t("platform.alerts.pagination.prev")}
      </button>
      <span className={`text-[11px] ${styles.cardTextMuted}`}>
        {t("common.pagination.page").replace("{p}", String(page)).replace("{m}", String(total))}
      </span>
      <button
        type="button"
        disabled={page >= total}
        onClick={() => onPageChange(page + 1)}
        className={`h-7 px-2.5 rounded-md border text-[11px] font-medium cursor-pointer transition disabled:opacity-40 disabled:cursor-not-allowed ${styles.appBorder} ${styles.cardTextMuted} hover:opacity-80`}
      >
        {t("platform.alerts.pagination.next")} &gt;
      </button>
    </div>
  );
}

// ── Small hook to avoid importing useMediaQuery directly ───────────

function useMobileBreakpoint() {
  const [isMobile, setIsMobile] = useState<boolean>(() => {
    if (typeof window === "undefined") return false;
    return window.matchMedia("(max-width: 767px)").matches;
  });
  useEffect(() => {
    if (typeof window === "undefined") return;
    const mql = window.matchMedia("(max-width: 767px)");
    const on = (e: MediaQueryListEvent) => setIsMobile(e.matches);
    mql.addEventListener("change", on);
    setIsMobile(mql.matches);
    return () => mql.removeEventListener("change", on);
  }, []);
  return { isMobile };
}
