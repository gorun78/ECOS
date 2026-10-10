/**
 * AlertsPage — 告警中心 (#/platform/alerts)
 * 设计：docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §3.2。
 *
 * 15s 轮询；document.hidden 暂停 / 恢复；拉取失败保留上次数据 + 顶部错误条。
 * 状态 / 严重度过滤 / 分页 / ?alertId= 抽屉 / ack / close 全组件契约。
 */
import React, { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  RefreshCw,
  BellRing,
  ChevronLeft,
  ChevronRight,
  AlertTriangle,
  Inbox,
} from "lucide-react";
import { useLanguage } from "../../../components/LanguageContext";
import { useTheme } from "../../../components/ThemeContext";
import { useToast } from "../../../components/common/Toast";
import {
  type AlertRecord,
  type AlertSeverity,
  type AlertStatus,
  listAlerts,
  ackAlert,
  closeAlert,
  getAlertDetail,
  errorTraceId,
} from "../../../services/platform";
import AlertRow from "./components/AlertRow";
import AlertDetailDrawer from "./components/AlertDetailDrawer";

const POLL_MS = 15_000;
const PAGE_SIZE = 20;

type LoadingState = "loading" | "ready" | "error";

interface PollState {
  alerts: AlertRecord[];
  loading: LoadingState;
  total: number;
  lastError?: string;
  lastTraceId?: string;
}

const SEVERITY_OPTS: (AlertSeverity | "all")[] = ["all", "critical", "error", "warn", "info"];
const STATUS_OPTS: (AlertStatus | "all")[] = ["all", "open", "acked", "closed"];

export default function AlertsPage() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const { showToast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  // 过滤参数（默认 open）— 走 query，可分享
  const severity = (searchParams.get("severity") ?? "all") as AlertSeverity | "all";
  const status = (searchParams.get("status") ?? "open") as AlertStatus | "all";
  const page = Math.max(1, parseInt(searchParams.get("page") ?? "1", 10) || 1);
  const alertId = searchParams.get("alertId");

  const [state, setState] = useState<PollState>({
    alerts: [],
    loading: "loading",
    total: 0,
  });
  const [fetching, setFetching] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [liveText, setLiveText] = useState("");
  const [detailAlert, setDetailAlert] = useState<AlertRecord | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const pollRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inFlightRef = useRef(false);

  // 写 ?severity=&status=&page= 参数（保持 ?alertId= 不变）
  const setQuery = useCallback(
    (patch: Record<string, string | number>) => {
      setSearchParams((prev) => {
        const sp = new URLSearchParams(prev);
        for (const [k, v] of Object.entries(patch)) sp.set(k, String(v));
        return sp;
      });
    },
    [setSearchParams]
  );

  const closeDrawer = useCallback(() => {
    setDetailAlert(null);
    setSearchParams((prev) => {
      const sp = new URLSearchParams(prev);
      sp.delete("alertId");
      return sp;
    });
  }, [setSearchParams]);

  const openAlert = useCallback(
    (alert: AlertRecord) => {
      setDetailAlert({ ...alert });
      setDetailLoading(true);
      setSearchParams((prev) => {
        const sp = new URLSearchParams(prev);
        sp.set("alertId", alert.id);
        return sp;
      });
      getAlertDetail(alert.id)
        .then((fresh) => setDetailAlert(fresh))
        .catch(() => {/* 详情拉取失败：保留列表里的旧数据 */})
        .finally(() => setDetailLoading(false));
    },
    [setSearchParams]
  );

  const fetchOnce = useCallback(async () => {
    if (inFlightRef.current) return;
    inFlightRef.current = true;
    setFetching(true);
    try {
      const pageRes = await listAlerts({
        page,
        size: PAGE_SIZE,
        status: status === "all" ? undefined : status,
        severity: severity === "all" ? undefined : severity,
      });
      setState({
        alerts: pageRes.items,
        loading: "ready",
        total: pageRes.total,
      });
    } catch (e) {
      setState((prev) => ({
        ...prev,
        loading: "error",
        lastError: String((e as Error)?.message ?? "fetch failed"),
        lastTraceId: errorTraceId(e),
      }));
    } finally {
      inFlightRef.current = false;
      setFetching(false);
    }
  }, [page, status, severity]);

  // 15s 轮询；document.hidden 暂停；恢复时立即拉一次
  useEffect(() => {
    void fetchOnce();
    const tick = () => {
      if (pollRef.current) clearTimeout(pollRef.current);
      pollRef.current = setTimeout(() => {
        if (!document.hidden) {
          void fetchOnce();
        }
        tick();
      }, POLL_MS);
    };
    tick();
    return () => {
      if (pollRef.current) clearTimeout(pollRef.current);
    };
  }, [fetchOnce]);

  // visibilitychange: 切回前台手动补一次
  useEffect(() => {
    const onVis = () => {
      if (!document.hidden) void fetchOnce();
    };
    document.addEventListener("visibilitychange", onVis);
    return () => document.removeEventListener("visibilitychange", onVis);
  }, [fetchOnce]);

  const doAck = useCallback(async (alert: AlertRecord) => {
    if (!alert || busyId) return;
    setBusyId(alert.id);
    try {
      await ackAlert(alert.id, {
        note: "ack from alert-center",
      });
      setLiveText(t("platform.alerts.message.ackSuccess"));
      showToast("info", t("platform.alerts.message.ackSuccess"));
      await fetchOnce();
    } catch (e) {
      setLiveText(t("platform.alerts.message.ackFailed"));
      showToast("error", `${t("platform.alerts.message.ackFailed")}: ${String((e as Error)?.message ?? "")}`);
    } finally {
      setBusyId(null);
    }
  }, [busyId, fetchOnce, showToast, t]);

  const doClose = useCallback(async (alert: AlertRecord) => {
    if (!alert || busyId) return;
    setBusyId(alert.id);
    try {
      await closeAlert(alert.id);
      setLiveText(t("platform.alerts.message.closeSuccess"));
      showToast("info", t("platform.alerts.message.closeSuccess"));
      await fetchOnce();
    } catch (e) {
      setLiveText(t("platform.alerts.message.closeFailed"));
      showToast("error", `${t("platform.alerts.message.closeFailed")}: ${String((e as Error)?.message ?? "")}`);
    } finally {
      setBusyId(null);
    }
  }, [busyId, fetchOnce, showToast, t]);

  const totalPages = Math.max(1, Math.ceil((state.total || 0) / PAGE_SIZE));

  return (
    <div className={`h-full flex flex-col ${styles.appBg} ${styles.appText}`}>
      {/* 顶栏 */}
      <div className={`flex items-center justify-between px-4 pt-4 pb-3 border-b ${styles.appBorder}`}>
        <div className="flex items-center gap-2">
          <BellRing className={`w-4 h-4 ${styles.accentText}`} />
          <h1 className={`text-base font-semibold ${styles.cardText}`} data-testid="alerts-page-title">
            {t("platform.alerts.title")}
          </h1>
          <span className={`text-[10px] font-mono ${styles.muted}`} data-testid="alerts-polling">
            {t("platform.alerts.state.polling")}
          </span>
        </div>
        <button
          type="button"
          data-testid="alerts-refresh"
          onClick={() => void fetchOnce()}
          aria-busy={fetching}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium cursor-pointer border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} hover:opacity-80 transition disabled:opacity-50`}
          disabled={fetching}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${fetching ? "animate-spin" : ""}`} />
          {t("platform.gateway.action.refresh")}
        </button>
      </div>

      {/* 过滤条 */}
      <div className={`flex items-center gap-2 px-4 py-2 border-b ${styles.appBorder} flex-wrap`}>
        <label className={`flex items-center gap-1.5 text-[10px] font-mono ${styles.muted}`}>
          {t("platform.alerts.filter.severity")}
          <select
            data-testid="alerts-severity-filter"
            value={severity}
            onChange={(e) => setQuery({ severity: e.target.value, page: 1 })}
            className={`px-2 py-1 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} text-xs cursor-pointer`}
          >
            {SEVERITY_OPTS.map((v) => (
              <option key={v} value={v}>
                {v === "all" ? t("platform.alerts.filter.all") : t(`platform.alerts.severity.${v}`)}
              </option>
            ))}
          </select>
        </label>

        <label className={`flex items-center gap-1.5 text-[10px] font-mono ${styles.muted}`}>
          {t("platform.alerts.filter.status")}
          <select
            data-testid="alerts-status-filter"
            value={status}
            onChange={(e) => setQuery({ status: e.target.value, page: 1 })}
            className={`px-2 py-1 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} text-xs cursor-pointer`}
          >
            {STATUS_OPTS.map((v) => (
              <option key={v} value={v}>
                {v === "all" ? t("platform.alerts.filter.all") : t(`platform.alerts.status.${v}`)}
              </option>
            ))}
          </select>
        </label>

        <div className="flex-1" />
        {/* 分页 */}
        {totalPages > 1 && (
          <nav aria-label="pagination" className={`flex items-center gap-1 text-[10px] font-mono ${styles.muted}`}>
            <button
              type="button"
              data-testid="alerts-prev-page"
              disabled={page <= 1}
              onClick={() => setQuery({ page: page - 1 })}
              className={`px-2 py-0.5 border rounded ${styles.inputBorder} ${styles.inputText} disabled:opacity-40 cursor-pointer inline-flex items-center`}
            >
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>
            <span className="px-2 tabular-nums">
              {page}/{totalPages}
            </span>
            <button
              type="button"
              data-testid="alerts-next-page"
              disabled={page >= totalPages}
              onClick={() => setQuery({ page: page + 1 })}
              className={`px-2 py-0.5 border rounded ${styles.inputBorder} ${styles.inputText} disabled:opacity-40 cursor-pointer inline-flex items-center`}
            >
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </nav>
        )}
      </div>

      {/* 拉取失败错误条（保留上次数据） */}
      {state.loading === "error" && state.lastError && (
        <div
          role="alert"
          data-testid="alerts-error-banner"
          className={`px-4 py-2 border-b ${styles.dangerBorder} ${styles.dangerBg} flex items-center gap-2 text-xs ${styles.dangerText}`}
        >
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span className="flex-1">{t("platform.alerts.error.message")}</span>
          {state.lastTraceId && <code className="font-mono text-[10px]">{state.lastTraceId}</code>}
        </div>
      )}

      {/* 列表 */}
      <div className="flex-1 overflow-y-auto p-4 space-y-2 scrollbar-thin" data-testid="alerts-list">
        {state.loading === "loading" ? (
          <AlertSkeletons />
        ) : state.alerts.length === 0 ? (
          <div
            data-testid="alerts-empty"
            className={`${styles.cardBg} border ${styles.cardBorder} rounded-md p-8 flex flex-col items-center gap-3`}
          >
            <Inbox className={`w-8 h-8 ${styles.muted}`} />
            <div className={`text-sm font-semibold ${styles.cardText}`}>{t("platform.alerts.state.empty")}</div>
            <div className={`text-xs ${styles.muted}`}>{t("platform.alerts.state.empty.desc")}</div>
          </div>
        ) : (
          state.alerts.map((a) => (
            <AlertRow
              key={a.id}
              alert={a}
              onOpen={openAlert}
              onAck={doAck}
              onClose={doClose}
              canOperate
              busy={busyId === a.id}
            />
          ))
        )}
      </div>

      {/* ack/close 状态变化 aria-live 区（屏幕阅读器可读） */}
      <div aria-live="polite" aria-atomic="true" data-testid="alerts-live" className="sr-only">
        {liveText}
      </div>

      {/* 详情抽屉（?alertId= 驱动） */}
      <AlertDetailDrawer
        alert={detailAlert}
        loading={detailLoading}
        onClose={closeDrawer}
      />
    </div>
  );
}

// 行骨架
function AlertSkeletons() {
  const { styles } = useTheme();
  return (
    <div className="space-y-2" aria-busy="true">
      {[1, 2, 3, 4].map((i) => (
        <div key={i} className={`h-14 rounded-md border ${styles.appBorder} animate-pulse`} />
      ))}
    </div>
  );
}
