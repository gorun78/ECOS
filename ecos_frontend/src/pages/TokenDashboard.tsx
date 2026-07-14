/**
 * TokenDashboard — Token 用量审计面板
 * Shows grand total, by-model, and by-operation token usage.
 * @license Apache-2.0
 */

import React, { useState, useEffect } from "react";
import {
  Coins,
  BarChart3,
  Cpu,
  Layers,
  RefreshCw,
  TrendingUp,
  Hash,
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { apiFetchData } from "../api";
import ErrorBoundary from "../components/common/ErrorBoundary";

// ── Types ──────────────────────────────────────────────────────

interface ModelTokens {
  model: string;
  tokens: number;
}

interface OperationTokens {
  operation: string;
  tokens: number;
}

interface TokenSummary {
  totals: {
    grand_total: number;
  };
  by_model: ModelTokens[];
  by_operation: OperationTokens[];
}

// ── Helpers ────────────────────────────────────────────────────

function formatTokens(n: number): string {
  if (n >= 1_000_000_000) return `${(n / 1_000_000_000).toFixed(2)} B`;
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(2)} M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)} K`;
  return n.toLocaleString();
}

// ── Main Component ─────────────────────────────────────────────

export default function TokenDashboard() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  const [data, setData] = useState<TokenSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [range, setRange] = useState("7d");

  const fetchData = async () => {
    setLoading(true);
    setError("");
    try {
      const result = await apiFetchData<TokenSummary>(
        `/api/telemetry/tokens/summary?range=${range}`
      );
      setData(result);
    } catch (e: any) {
      setError(e.message || "Failed to load token data");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [range]);

  const grandTotal = data?.totals?.grand_total ?? 0;
  const byModel = data?.by_model ?? [];
  const byOperation = data?.by_operation ?? [];

  return (
    <ErrorBoundary>
    <div className="h-full overflow-y-auto p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className={`text-xl font-bold ${styles.cardText}`}>
            {locale === "zh" ? "Token 审计" : "Token Dashboard"}
          </h1>
          <p className={`text-xs mt-1 ${styles.cardTextMuted}`}>
            {locale === "zh"
              ? "监控 LLM Token 用量，按模型与操作维度统计"
              : "Monitor LLM token usage by model and operation"}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {/* Range selector */}
          <select
            value={range}
            onChange={(e) => setRange(e.target.value)}
            className={`px-3 py-1.5 rounded text-xs border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
          >
            <option value="1d">{locale === "zh" ? "最近 1 天" : "Last 1 day"}</option>
            <option value="7d">{locale === "zh" ? "最近 7 天" : "Last 7 days"}</option>
            <option value="30d">{locale === "zh" ? "最近 30 天" : "Last 30 days"}</option>
          </select>
          <button
            onClick={fetchData}
            disabled={loading}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-medium transition-colors ${styles.accentBg} text-white ${styles.accentHover}`}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
            {locale === "zh" ? "刷新" : "Refresh"}
          </button>
        </div>
      </div>

      {error && (
        <div className="p-3 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
          {error}
        </div>
      )}

      {/* Grand Total Card */}
      <div className={`rounded-lg border p-6 ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center gap-2 mb-1">
          <Coins className={`w-5 h-5 ${styles.accentText}`} />
          <span className={`text-sm font-semibold ${styles.cardText}`}>
            {locale === "zh" ? "Token 总量" : "Grand Total"}
          </span>
        </div>
        <div className="mt-2">
          {loading && data === null ? (
            <div className="flex items-center gap-2">
              <RefreshCw className="w-4 h-4 animate-spin text-slate-400" />
              <span className={`text-xs ${styles.cardTextMuted}`}>
                {locale === "zh" ? "加载中…" : "Loading…"}
              </span>
            </div>
          ) : (
            <>
              <span className={`text-4xl font-extrabold tracking-tight ${styles.accentText}`}>
                {formatTokens(grandTotal)}
              </span>
              <span className={`text-sm ml-2 ${styles.cardTextMuted}`}>tokens</span>
            </>
          )}
        </div>
      </div>

      {/* Two-column layout: By Model | By Operation */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* By Model */}
        <div className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
          <div className={`px-4 py-3 border-b flex items-center gap-2 ${styles.cardBorder}`}>
            <Cpu className={`w-4 h-4 ${styles.cardTextMuted}`} />
            <span className={`text-sm font-semibold ${styles.cardText}`}>
              {locale === "zh" ? "按模型" : "By Model"}
            </span>
          </div>
          {byModel.length === 0 ? (
            <div className="p-6 text-center">
              <BarChart3 className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
              <p className={`text-xs ${styles.cardTextMuted}`}>
                {locale === "zh" ? "暂无数据" : "No data"}
              </p>
            </div>
          ) : (
            <div className="divide-y divide-white/5">
              {byModel.map((m) => {
                const pct = grandTotal > 0 ? (m.tokens / grandTotal) * 100 : 0;
                return (
                  <div key={m.model} className="px-4 py-3">
                    <div className="flex items-center justify-between mb-1.5">
                      <span className={`text-xs font-medium ${styles.cardText}`}>
                        {m.model || "unknown"}
                      </span>
                      <span className="text-xs font-mono text-slate-400">
                        {formatTokens(m.tokens)}
                      </span>
                    </div>
                    <div className="w-full h-1.5 rounded-full bg-white/5 overflow-hidden">
                      <div
                        className={`h-full rounded-full ${styles.accentBg}`}
                        style={{ width: `${Math.min(pct, 100)}%` }}
                      />
                    </div>
                    <div className="text-[10px] text-right mt-0.5 text-slate-500">
                      {pct.toFixed(1)}%
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* By Operation */}
        <div className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
          <div className={`px-4 py-3 border-b flex items-center gap-2 ${styles.cardBorder}`}>
            <Layers className={`w-4 h-4 ${styles.cardTextMuted}`} />
            <span className={`text-sm font-semibold ${styles.cardText}`}>
              {locale === "zh" ? "按操作" : "By Operation"}
            </span>
          </div>
          {byOperation.length === 0 ? (
            <div className="p-6 text-center">
              <TrendingUp className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
              <p className={`text-xs ${styles.cardTextMuted}`}>
                {locale === "zh" ? "暂无数据" : "No data"}
              </p>
            </div>
          ) : (
            <div className="divide-y divide-white/5">
              {byOperation.map((op) => {
                const pct = grandTotal > 0 ? (op.tokens / grandTotal) * 100 : 0;
                return (
                  <div key={op.operation} className="px-4 py-3">
                    <div className="flex items-center justify-between mb-1.5">
                      <span className={`text-xs font-medium ${styles.cardText}`}>
                        {op.operation || "unknown"}
                      </span>
                      <span className="text-xs font-mono text-slate-400">
                        {formatTokens(op.tokens)}
                      </span>
                    </div>
                    <div className="w-full h-1.5 rounded-full bg-white/5 overflow-hidden">
                      <div
                        className="h-full rounded-full bg-emerald-500/60"
                        style={{ width: `${Math.min(pct, 100)}%` }}
                      />
                    </div>
                    <div className="text-[10px] text-right mt-0.5 text-slate-500">
                      {pct.toFixed(1)}%
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
    </ErrorBoundary>
  );
}
