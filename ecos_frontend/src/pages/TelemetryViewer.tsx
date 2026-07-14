/**
 * TelemetryViewer — 链路追踪查看器
 * Shows health status, recent traces, and span drill-down.
 * @license Apache-2.0
 */

import React, { useState, useEffect } from "react";
import {
  Activity,
  Radio,
  Clock,
  Layers,
  CheckCircle2,
  AlertTriangle,
  ChevronRight,
  ChevronDown,
  RefreshCw,
  Search,
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { apiFetchData } from "../api";
import ErrorBoundary from "../components/common/ErrorBoundary";

// ── Types ──────────────────────────────────────────────────────

interface HealthData {
  status: string;
  spans_stored: number;
  token_records: number;
  db: string;
}

interface TraceSummary {
  traceId: string;
  rootSpan: string;
  spanCount: number;
  totalDuration_ms: number;
}

interface SpanDetail {
  spanId: string;
  parentSpanId: string | null;
  operation: string;
  duration_ms: number;
  status: string;
}

interface TraceDetail {
  traceId: string;
  spans: SpanDetail[];
}

// ── Helpers ────────────────────────────────────────────────────

function formatDuration(ms: number): string {
  if (ms >= 1000) return `${(ms / 1000).toFixed(2)} s`;
  if (ms >= 1) return `${ms.toFixed(1)} ms`;
  return `${(ms * 1000).toFixed(0)} µs`;
}

function statusBadge(status: string): { icon: React.ReactNode; cls: string } {
  const s = status?.toLowerCase() || "";
  if (s === "ok" || s === "healthy" || s === "connected") {
    return { icon: <CheckCircle2 className="w-3.5 h-3.5" />, cls: "bg-emerald-500/15 text-emerald-500" };
  }
  if (s === "error" || s === "failed") {
    return { icon: <AlertTriangle className="w-3.5 h-3.5" />, cls: "bg-red-500/15 text-red-500" };
  }
  return { icon: <Activity className="w-3.5 h-3.5" />, cls: "bg-amber-500/15 text-amber-500" };
}

// ── Span Tree (stateful wrapper) ───────────────────────────────

function SpanTree({ rootSpans, allSpans }: { rootSpans: SpanDetail[]; allSpans: SpanDetail[] }) {
  const [openMap, setOpenMap] = React.useState<Record<string, boolean>>({});

  const renderWithState = (span: SpanDetail, depth: number, all: SpanDetail[]): React.ReactNode => {
    const children = all.filter((s) => s.parentSpanId === span.spanId);
    const hasChildren = children.length > 0;
    const open = openMap[span.spanId] ?? (depth < 2);
    const badge = statusBadge(span.status);

    return (
      <React.Fragment key={span.spanId}>
        <tr className="border-b border-white/5 hover:bg-white/[0.03] transition-colors">
          <td className="py-2 px-3 text-xs font-mono text-slate-400">
            <span style={{ paddingLeft: depth * 20 }} className="inline-flex items-center gap-1">
              {hasChildren ? (
                <button
                  onClick={() => setOpenMap((prev) => ({ ...prev, [span.spanId]: !(prev[span.spanId] ?? depth < 2) }))}
                  className="p-0.5 hover:bg-white/10 rounded"
                >
                  {open ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
                </button>
              ) : (
                <span className="w-4 inline-block" />
              )}
              {span.spanId.slice(0, 12)}…
            </span>
          </td>
          <td className="py-2 px-3 text-sm font-medium">{span.operation}</td>
          <td className="py-2 px-3 text-xs font-mono text-right">
            {formatDuration(span.duration_ms)}
          </td>
          <td className="py-2 px-3 text-xs text-right">
            <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium ${badge.cls}`}>
              {badge.icon}
              {span.status || "ok"}
            </span>
          </td>
        </tr>
        {open && hasChildren && children.map((child) => renderWithState(child, depth + 1, all))}
      </React.Fragment>
    );
  };

  return <>{rootSpans.map((rs) => renderWithState(rs, 0, allSpans))}</>;
}

// ── Main Component ─────────────────────────────────────────────

export default function TelemetryViewer() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  const [health, setHealth] = useState<HealthData | null>(null);
  const [traces, setTraces] = useState<TraceSummary[]>([]);
  const [selectedTraceId, setSelectedTraceId] = useState<string | null>(null);
  const [traceDetail, setTraceDetail] = useState<TraceDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState("");

  // Fetch health + traces on mount
  const fetchData = async () => {
    setLoading(true);
    setError("");
    try {
      const [healthData, tracesData] = await Promise.all([
        apiFetchData<HealthData>("/api/telemetry/health"),
        apiFetchData<TraceSummary[]>("/api/telemetry/traces?limit=20"),
      ]);
      setHealth(healthData);
      setTraces(Array.isArray(tracesData) ? tracesData : []);
    } catch (e: any) {
      setError(e.message || "Failed to load telemetry data");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  // Fetch trace detail
  const selectTrace = async (traceId: string) => {
    if (selectedTraceId === traceId) {
      setSelectedTraceId(null);
      setTraceDetail(null);
      return;
    }
    setSelectedTraceId(traceId);
    setDetailLoading(true);
    try {
      const detail = await apiFetchData<TraceDetail>(`/api/telemetry/traces/${traceId}`);
      setTraceDetail(detail);
    } catch (e: any) {
      setTraceDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  // If we have a raw object instead of array for traces, handle it
  const traceList = Array.isArray(traces) ? traces : [];

  return (
    <ErrorBoundary>
    <div className="h-full overflow-y-auto p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className={`text-xl font-bold ${styles.cardText}`}>
            {locale === "zh" ? "链路追踪" : "Telemetry Viewer"}
          </h1>
          <p className={`text-xs mt-1 ${styles.cardTextMuted}`}>
            {locale === "zh" ? "查看分布式链路追踪与 Span 详情" : "Distributed tracing and span inspection"}
          </p>
        </div>
        <button
          onClick={fetchData}
          disabled={loading}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-medium transition-colors ${styles.accentBg} text-white ${styles.accentHover}`}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {locale === "zh" ? "刷新" : "Refresh"}
        </button>
      </div>

      {error && (
        <div className="p-3 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
          {error}
        </div>
      )}

      {/* Health Card */}
      {health && (
        <div className={`rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="flex items-center gap-2 mb-3">
            <Radio className="w-4 h-4 text-emerald-500" />
            <span className={`text-sm font-semibold ${styles.cardText}`}>
              {locale === "zh" ? "系统状态" : "System Health"}
            </span>
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium bg-emerald-500/15 text-emerald-500">
              <CheckCircle2 className="w-3 h-3" />
              {health.status || "ok"}
            </span>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <div className="text-center">
              <div className={`text-2xl font-bold ${styles.accentText}`}>
                {health.spans_stored?.toLocaleString() ?? 0}
              </div>
              <div className={`text-[10px] uppercase tracking-wider ${styles.cardTextMuted}`}>
                Spans
              </div>
            </div>
            <div className="text-center">
              <div className={`text-2xl font-bold ${styles.accentText}`}>
                {health.token_records?.toLocaleString() ?? 0}
              </div>
              <div className={`text-[10px] uppercase tracking-wider ${styles.cardTextMuted}`}>
                Token Records
              </div>
            </div>
            <div className="text-center">
              <div className={`text-2xl font-bold ${styles.accentText}`}>
                {health.db || "—"}
              </div>
              <div className={`text-[10px] uppercase tracking-wider ${styles.cardTextMuted}`}>
                Database
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Traces Table */}
      <div className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
        <div className={`px-4 py-3 border-b ${styles.cardBorder} flex items-center gap-2`}>
          <Layers className={`w-4 h-4 ${styles.cardTextMuted}`} />
          <span className={`text-sm font-semibold ${styles.cardText}`}>
            {locale === "zh" ? "最近链路" : "Recent Traces"}
          </span>
          <span className={`text-[10px] ${styles.cardTextMuted}`}>
            ({traceList.length})
          </span>
        </div>

        {loading && traceList.length === 0 ? (
          <div className="p-8 text-center">
            <Activity className={`w-6 h-6 mx-auto mb-2 animate-spin ${styles.accentText}`} />
            <p className={`text-xs ${styles.cardTextMuted}`}>
              {locale === "zh" ? "加载中…" : "Loading…"}
            </p>
          </div>
        ) : traceList.length === 0 ? (
          <div className="p-8 text-center">
            <Search className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
            <p className={`text-xs ${styles.cardTextMuted}`}>
              {locale === "zh" ? "暂无链路数据" : "No traces found"}
            </p>
          </div>
        ) : (
          <table className="w-full text-left">
            <thead>
              <tr className={`border-b text-[11px] uppercase tracking-wider ${styles.cardBorder} ${styles.cardTextMuted}`}>
                <th className="py-2.5 px-4 font-medium">Trace ID</th>
                <th className="py-2.5 px-4 font-medium">
                  {locale === "zh" ? "根操作" : "Root Operation"}
                </th>
                <th className="py-2.5 px-4 font-medium text-right">Spans</th>
                <th className="py-2.5 px-4 font-medium text-right">
                  {locale === "zh" ? "耗时" : "Duration"}
                </th>
              </tr>
            </thead>
            <tbody>
              {traceList.map((trace) => {
                const isSelected = selectedTraceId === trace.traceId;
                const shortId = trace.traceId.length > 16
                  ? trace.traceId.slice(0, 16) + "…"
                  : trace.traceId;
                return (
                  <React.Fragment key={trace.traceId}>
                    <tr
                      onClick={() => selectTrace(trace.traceId)}
                      className={`border-b cursor-pointer transition-colors ${styles.cardBorder} ${
                        isSelected
                          ? "bg-indigo-500/10"
                          : "hover:bg-white/[0.03]"
                      }`}
                    >
                      <td className="py-2.5 px-4 font-mono text-xs text-slate-300">
                        {shortId}
                      </td>
                      <td className={`py-2.5 px-4 text-sm ${styles.cardText}`}>
                        {trace.rootSpan || "—"}
                      </td>
                      <td className="py-2.5 px-4 text-xs text-right font-mono">
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-indigo-500/15 text-indigo-400 text-[10px]">
                          {trace.spanCount}
                        </span>
                      </td>
                      <td className="py-2.5 px-4 text-xs text-right font-mono text-slate-400">
                        {formatDuration(trace.totalDuration_ms)}
                      </td>
                    </tr>
                    {/* Expanded trace detail */}
                    {isSelected && (
                      <tr>
                        <td colSpan={4} className="p-0">
                          <div className="bg-black/10 px-4 py-3 border-b border-white/5">
                            {detailLoading ? (
                              <div className="flex items-center gap-2 text-xs text-slate-400 py-2">
                                <Activity className="w-3.5 h-3.5 animate-spin" />
                                {locale === "zh" ? "加载 Span 详情…" : "Loading spans…"}
                              </div>
                            ) : traceDetail && traceDetail.spans ? (
                              <table className="w-full text-left">
                                <thead>
                                  <tr className="text-[10px] uppercase tracking-wider text-slate-500">
                                    <th className="py-1.5 px-3 font-medium">Span ID</th>
                                    <th className="py-1.5 px-3 font-medium">Operation</th>
                                    <th className="py-1.5 px-3 font-medium text-right">Duration</th>
                                    <th className="py-1.5 px-3 font-medium text-right">Status</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  <SpanTree rootSpans={traceDetail.spans.filter((s) => !s.parentSpanId)} allSpans={traceDetail.spans} />
                                </tbody>
                              </table>
                            ) : (
                              <p className="text-xs text-slate-500 py-2">
                                {locale === "zh" ? "无 Span 数据" : "No span data"}
                              </p>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
    </ErrorBoundary>
  );
}
