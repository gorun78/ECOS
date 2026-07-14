/**
 * CodeWorkbook — Interactive Multi-Language Notebook (SQL + Python + R)
 * Real backend execution via Workbook API.
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  RotateCw, Plus, Database,
  Play, AlertCircle,
  Trash2, Terminal, FileCode, History
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { apiFetchData } from "../api";
import ErrorBoundary from "../components/common/ErrorBoundary";
import WorkbookHistory from "../components/WorkbookHistory";

// ── Types ──────────────────────────────────────────────

interface RuntimeStatus {
  sql: string;
  python: string;
  r: string;
}

interface ExecutionResult {
  output: string;
  result?: any;
  elapsed_ms: number;
  timed_out: boolean;
}

interface HistoryEntry {
  language: string;
  code: string;
  output: string;
  elapsed_ms: number;
  createdAt: string;
}

interface NotebookCell {
  id: string;
  language: Language;
  code: string;
  result?: ExecutionResult | null;
  loading: boolean;
  error?: string | null;
}

type Language = "sql" | "python" | "r";

const LANG_LABELS: Record<Language, string> = {
  sql: "SQL",
  python: "Python",
  r: "R",
};

const DEFAULT_CODE: Record<Language, string> = {
  sql: "SELECT 1 AS test;",
  python: `# Python Data Analysis
import sys
print("Hello from Python")
print(f"Python version: {sys.version}")`,
  r: `# R Statistical Computing
print("Hello from R")
print(R.version.string)`,
};

// ── Component ──────────────────────────────────────────

export default function CodeWorkbook() {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  // Runtime health
  const [runtimes, setRuntimes] = useState<RuntimeStatus | null>(null);
  const [healthLoading, setHealthLoading] = useState(true);
  const [healthError, setHealthError] = useState<string | null>(null);

  // Active language for new cells
  const [activeLanguage, setActiveLanguage] = useState<Language>("sql");

  // Notebook cells
  const [cells, setCells] = useState<NotebookCell[]>([]);

  // History panel
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [showHistory, setShowHistory] = useState(true);

  // ── Fetch health on mount ────────────────────────────
  const fetchHealth = useCallback(async () => {
    setHealthLoading(true);
    setHealthError(null);
    try {
      const data = await apiFetchData<{ runtimes: RuntimeStatus }>(
        "/api/workbook/health"
      );
      setRuntimes(data.runtimes);
    } catch (e: any) {
      console.warn("Health check failed:", e);
      setHealthError(e.message || "Health check failed");
    } finally {
      setHealthLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchHealth();
  }, [fetchHealth]);

  // ── Fetch history ────────────────────────────────────
  const fetchHistory = useCallback(async () => {
    setHistoryLoading(true);
    try {
      const data = await apiFetchData<HistoryEntry[]>(
        "/api/workbook/history"
      );
      setHistory(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn("History fetch failed:", e);
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  // ── Cell operations ──────────────────────────────────

  const addCell = (language?: Language) => {
    const lang = language || activeLanguage;
    const newCell: NotebookCell = {
      id: Date.now().toString() + Math.random().toString(36).slice(2, 6),
      language: lang,
      code: DEFAULT_CODE[lang],
      loading: false,
      result: null,
      error: null,
    };
    setCells((prev) => [...prev, newCell]);
  };

  const updateCellCode = (cellId: string, code: string) => {
    setCells((prev) =>
      prev.map((c) => (c.id === cellId ? { ...c, code } : c))
    );
  };

  const updateCellLanguage = (cellId: string, language: Language) => {
    setCells((prev) =>
      prev.map((c) =>
        c.id === cellId ? { ...c, language, result: null, error: null } : c
      )
    );
  };

  const deleteCell = (cellId: string) => {
    setCells((prev) => prev.filter((c) => c.id !== cellId));
  };

  // ── Execute cell via backend API ─────────────────────
  const executeCell = async (cellId: string) => {
    const cell = cells.find((c) => c.id === cellId);
    if (!cell || !cell.code.trim()) return;

    setCells((prev) =>
      prev.map((c) =>
        c.id === cellId ? { ...c, loading: true, error: null } : c
      )
    );

    try {
      const data = await apiFetchData<ExecutionResult>(
        "/api/workbook/execute",
        {
          method: "POST",
          body: JSON.stringify({
            language: cell.language,
            code: cell.code,
          }),
        }
      );

      setCells((prev) =>
        prev.map((c) =>
          c.id === cellId
            ? { ...c, loading: false, result: data, error: null }
            : c
        )
      );

      // Refresh history after execution
      fetchHistory();
    } catch (e: any) {
      const errorMsg: string = e.message || String(e);
      let displayError = errorMsg;

      // Handle 408 timeout specifically
      if (
        errorMsg.includes("408") ||
        errorMsg.toLowerCase().includes("timeout") ||
        errorMsg.toLowerCase().includes("timed_out")
      ) {
        displayError =
          locale === "zh"
            ? "执行超时，进程已终止"
            : "Execution timed out, process terminated";
      }

      setCells((prev) =>
        prev.map((c) =>
          c.id === cellId
            ? { ...c, loading: false, error: displayError }
            : c
        )
      );
    }
  };

  // ── Helpers ──────────────────────────────────────────

  const isRuntimeAvailable = (lang: Language): boolean => {
    if (!runtimes) return true; // Optimistic before health check loads
    const status = runtimes[lang];
    return status === "available" || status === "available=true";
  };

  // ── Result rendering ─────────────────────────────────

  const renderCellResult = (cell: NotebookCell) => {
    if (cell.loading) {
      return (
        <div className="flex items-center gap-2 text-xs text-slate-400 py-2 px-3">
          <RotateCw className="w-3 h-3 animate-spin" />
          <span>
            {locale === "zh" ? "执行中..." : "Executing..."}
          </span>
        </div>
      );
    }

    if (cell.error) {
      return (
        <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-3 flex items-start gap-2 text-red-700 dark:text-red-300 text-xs">
          <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
          <span>{cell.error}</span>
        </div>
      );
    }

    if (!cell.result) return null;

    const { output, result, elapsed_ms, timed_out } = cell.result;

    // ── SQL: attempt table rendering ──
    if (cell.language === "sql" && result) {
      let columns: string[] = [];
      let rows: Record<string, any>[] = [];

      if (Array.isArray(result)) {
        rows = result;
        if (rows.length > 0) {
          columns = Object.keys(rows[0]);
        }
      } else if (result.columns && Array.isArray(result.rows)) {
        columns = result.columns;
        rows = result.rows;
      } else if (typeof result === "object") {
        columns = Object.keys(result);
        rows = [result];
      }

      if (columns.length > 0) {
        return (
          <div className="border border-slate-200 dark:border-slate-700 rounded-lg overflow-hidden bg-white dark:bg-slate-900">
            <div className="overflow-x-auto max-h-[300px]">
              <table className="w-full text-xs">
                <thead>
                  <tr className="bg-slate-50 dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700 sticky top-0">
                    {columns.map((col) => (
                      <th
                        key={col}
                        className="text-left px-3 py-2 font-semibold text-slate-600 dark:text-slate-300 text-[11px] whitespace-nowrap"
                      >
                        {col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {rows.slice(0, 200).map((row, i) => (
                    <tr
                      key={i}
                      className="border-b border-slate-100 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800"
                    >
                      {columns.map((col) => (
                        <td
                          key={col}
                          className="px-3 py-1.5 font-mono text-[11px] text-slate-700 dark:text-slate-300 truncate max-w-[300px]"
                        >
                          {row[col] !== null && row[col] !== undefined
                            ? String(row[col])
                            : "—"}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="bg-slate-50 dark:bg-slate-800 border-t border-slate-200 dark:border-slate-700 px-3 py-1.5 text-[10px] text-slate-400 flex justify-between">
              <span>
                {rows.length}{" "}
                {locale === "zh" ? "行" : "rows"}
              </span>
              <span className="flex items-center gap-2">
                {elapsed_ms != null && <span>{elapsed_ms}ms</span>}
                {timed_out && (
                  <span className="text-amber-500">
                    ⚠ {locale === "zh" ? "超时" : "Timeout"}
                  </span>
                )}
              </span>
            </div>
          </div>
        );
      }
    }

    // ── Python / R / fallback: raw text output ──
    return (
      <div className="bg-slate-900 border border-slate-700 rounded-lg p-3 overflow-auto max-h-[400px]">
        <pre className="font-mono text-[12px] text-green-400 whitespace-pre-wrap break-all leading-relaxed">
          {output || (locale === "zh" ? "（无输出）" : "(no output)")}
        </pre>
        <div className="mt-2 pt-1.5 border-t border-slate-700 flex justify-between text-[10px] text-slate-500">
          <span>
            {elapsed_ms != null && `${elapsed_ms}ms`}
          </span>
          {timed_out && (
            <span className="text-amber-400">
              ⚠ {locale === "zh" ? "执行超时" : "Timed out"}
            </span>
          )}
        </div>
      </div>
    );
  };

  // ── Render ───────────────────────────────────────────

  return (
    <ErrorBoundary>
    <div className="flex-grow overflow-hidden flex flex-col md:flex-row h-full font-sans">
      {/* ── Main Workspace ─────────────────────────────── */}
      <div className="flex-1 flex flex-col min-w-0 h-full overflow-hidden">
        {/* Toolbar */}
        <div
          className={`h-11 border-b ${styles.cardBorder} ${styles.cardBg} px-4 flex items-center justify-between shrink-0`}
        >
          <div className="flex items-center gap-3">
            {/* Runtime health indicator */}
            <div className="flex items-center gap-1.5">
              {healthLoading ? (
                <RotateCw className="w-3 h-3 text-slate-400 animate-spin" />
              ) : healthError ? (
                <span className="flex items-center gap-1 text-[10px] text-red-500">
                  <AlertCircle className="w-3 h-3" />
                  {locale === "zh" ? "后端未连接" : "Backend offline"}
                </span>
              ) : (
                <span className="flex items-center gap-1 text-[10px] text-emerald-500 font-mono">
                  <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full"></span>
                  {locale === "zh" ? "运行时就绪" : "Runtimes ready"}
                </span>
              )}
            </div>
            <div className="h-4 w-px bg-slate-300 dark:bg-slate-600"></div>

            {/* Language tabs for new cells */}
            <div className="flex bg-slate-200 dark:bg-slate-700 rounded-md p-0.5">
              {(["sql", "python", "r"] as Language[]).map((lang) => {
                const available = isRuntimeAvailable(lang);
                return (
                  <button
                    key={lang}
                    onClick={() => setActiveLanguage(lang)}
                    title={
                      !available
                        ? locale === "zh"
                          ? `${LANG_LABELS[lang]} 未安装`
                          : `${LANG_LABELS[lang]} not installed`
                        : undefined
                    }
                    className={`px-3 py-1 text-[11px] font-semibold rounded transition flex items-center gap-1 ${
                      activeLanguage === lang
                        ? "bg-white dark:bg-slate-600 text-indigo-700 dark:text-indigo-300 shadow-sm"
                        : `text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 ${
                            !available ? "opacity-50" : ""
                          }`
                    }`}
                  >
                    {lang === "sql" ? (
                      <Database className="w-3 h-3" />
                    ) : (
                      <FileCode className="w-3 h-3" />
                    )}
                    {LANG_LABELS[lang]}
                    {!available && (
                      <span className="text-[9px] text-amber-500 ml-0.5">
                        {locale === "zh" ? "未安装" : "N/A"}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex items-center gap-2">
            {/* Add cell button */}
            <button
              onClick={() => addCell()}
              className="flex items-center gap-1.5 px-3 py-1 bg-indigo-600 hover:bg-indigo-700 text-white rounded-md text-xs font-bold h-7"
            >
              <Plus className="w-3 h-3" />
              <span>{locale === "zh" ? "新单元格" : "New Cell"}</span>
            </button>

            {/* Toggle history */}
            <button
              onClick={() => setShowHistory(!showHistory)}
              className={`p-1.5 border ${styles.cardBorder} hover:bg-black/5 dark:hover:bg-white/5 rounded-md ${styles.cardTextMuted}`}
              title={locale === "zh" ? "执行历史" : "Execution History"}
            >
              <History className="w-3.5 h-3.5" />
            </button>

            {/* Refresh health */}
            <button
              onClick={fetchHealth}
              className={`p-1.5 border ${styles.cardBorder} hover:bg-black/5 dark:hover:bg-white/5 rounded-md ${styles.cardTextMuted}`}
              title={locale === "zh" ? "刷新状态" : "Refresh Status"}
            >
              <RotateCw
                className={`w-3.5 h-3.5 ${healthLoading ? "animate-spin" : ""}`}
              />
            </button>
          </div>
        </div>

        {/* Cells area */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6 space-y-4">
          {cells.length === 0 ? (
            /* Empty state */
            <div className="flex-1 flex items-center justify-center py-16">
              <div className="max-w-lg text-center space-y-4">
                <div className="flex justify-center">
                  <div className="p-5 bg-gradient-to-br from-indigo-500/10 to-purple-500/10 rounded-2xl border border-indigo-500/20">
                    <Terminal className="w-14 h-14 text-indigo-400" />
                  </div>
                </div>
                <h2 className="text-base font-bold text-slate-700 dark:text-slate-200">
                  {locale === "zh"
                    ? "交互式多语言工作簿"
                    : "Interactive Multi-Language Workbook"}
                </h2>
                <p
                  className={`text-sm ${styles.cardTextMuted} max-w-md`}
                >
                  {locale === "zh"
                    ? "点击「新单元格」开始编写 SQL、Python 或 R 代码，通过后端运行时实时执行。"
                    : "Click 'New Cell' to start writing SQL, Python, or R code. Execute against real backend runtimes."}
                </p>
                <button
                  onClick={() => addCell()}
                  className="inline-flex items-center gap-1.5 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-bold"
                >
                  <Plus className="w-4 h-4" />
                  {locale === "zh" ? "创建第一个单元格" : "Create First Cell"}
                </button>

                {/* Runtime status cards */}
                {runtimes && (
                  <div className="grid grid-cols-3 gap-2 pt-4">
                    {(["sql", "python", "r"] as Language[]).map((lang) => {
                      const available = isRuntimeAvailable(lang);
                      return (
                        <div
                          key={lang}
                          className={`rounded-lg border p-3 text-center text-xs ${
                            available
                              ? "border-emerald-200 bg-emerald-50 dark:bg-emerald-900/20 dark:border-emerald-800"
                              : "border-amber-200 bg-amber-50 dark:bg-amber-900/20 dark:border-amber-800"
                          }`}
                        >
                          <div className="font-mono font-bold mb-0.5">
                            {LANG_LABELS[lang]}
                          </div>
                          <div
                            className={
                              available
                                ? "text-emerald-600 dark:text-emerald-400"
                                : "text-amber-600 dark:text-amber-400"
                            }
                          >
                            {available
                              ? locale === "zh"
                                ? "可用"
                                : "Available"
                              : locale === "zh"
                              ? "未安装"
                              : "Not installed"}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          ) : (
            /* Cells */
            cells.map((cell) => (
              <div
                key={cell.id}
                className="border border-slate-200 dark:border-slate-700 rounded-lg overflow-hidden bg-white dark:bg-slate-900 shadow-sm"
              >
                {/* Cell header */}
                <div className="bg-slate-50 dark:bg-slate-800 px-3 py-2 flex items-center justify-between border-b border-slate-200 dark:border-slate-700">
                  <div className="flex items-center gap-2">
                    {/* Language selector for this cell */}
                    <select
                      value={cell.language}
                      onChange={(e) =>
                        updateCellLanguage(cell.id, e.target.value as Language)
                      }
                      className="text-[11px] font-mono bg-white dark:bg-slate-700 border border-slate-200 dark:border-slate-600 rounded px-2 py-0.5 text-slate-600 dark:text-slate-300 outline-none"
                    >
                      {(["sql", "python", "r"] as Language[]).map((lang) => (
                        <option key={lang} value={lang}>
                          {LANG_LABELS[lang]}
                          {!isRuntimeAvailable(lang)
                            ? ` (${locale === "zh" ? "未安装" : "N/A"})`
                            : ""}
                        </option>
                      ))}
                    </select>
                    <span className="text-[10px] text-slate-400 font-mono">
                      #{cell.id.slice(-4)}
                    </span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    {/* Execute button */}
                    <button
                      onClick={() => executeCell(cell.id)}
                      disabled={!cell.code.trim() || cell.loading}
                      className="flex items-center gap-1 px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 disabled:bg-emerald-400 text-white rounded text-[11px] font-bold h-6"
                    >
                      {cell.loading ? (
                        <RotateCw className="w-3 h-3 animate-spin" />
                      ) : (
                        <Play className="w-3 h-3" />
                      )}
                      <span>{locale === "zh" ? "执行" : "Run"}</span>
                    </button>
                    {/* Delete button */}
                    <button
                      onClick={() => deleteCell(cell.id)}
                      className="p-1 hover:bg-red-50 dark:hover:bg-red-900/30 rounded text-slate-400 hover:text-red-500"
                      title={locale === "zh" ? "删除单元格" : "Delete cell"}
                    >
                      <Trash2 className="w-3 h-3" />
                    </button>
                  </div>
                </div>

                {/* Code editor */}
                <textarea
                  value={cell.code}
                  onChange={(e) => updateCellCode(cell.id, e.target.value)}
                  className={`w-full font-mono text-[13px] p-4 min-h-[100px] outline-none resize-y border-0 ${
                    cell.language === "python"
                      ? "bg-slate-900 text-emerald-400"
                      : cell.language === "r"
                      ? "bg-slate-900 text-sky-400"
                      : "bg-slate-800 text-green-400"
                  }`}
                  placeholder={
                    cell.language === "sql"
                      ? "SELECT * FROM ..."
                      : cell.language === "python"
                      ? "# Write Python code..."
                      : "# Write R code..."
                  }
                  spellCheck={false}
                  onKeyDown={(e: React.KeyboardEvent) => {
                    // Ctrl/Cmd+Enter to execute
                    if (
                      (e.ctrlKey || e.metaKey) &&
                      e.key === "Enter"
                    ) {
                      e.preventDefault();
                      executeCell(cell.id);
                    }
                  }}
                />

                {/* Result area */}
                {(cell.loading || cell.result || cell.error) && (
                  <div className="border-t border-slate-200 dark:border-slate-700 p-3 bg-slate-50/50 dark:bg-slate-900/50">
                    {renderCellResult(cell)}
                  </div>
                )}
              </div>
            ))
          )}

          {/* Add cell button at bottom (when cells already exist) */}
          {cells.length > 0 && (
            <div className="flex justify-center pt-2 pb-4">
              <button
                onClick={() => addCell()}
                className="flex items-center gap-1.5 px-4 py-2 border-2 border-dashed border-slate-300 dark:border-slate-600 hover:border-indigo-400 dark:hover:border-indigo-500 rounded-lg text-xs text-slate-400 hover:text-indigo-500 dark:hover:text-indigo-400 transition-colors"
              >
                <Plus className="w-3.5 h-3.5" />
                {locale === "zh" ? "添加单元格" : "Add Cell"}
              </button>
            </div>
          )}
        </div>

        {/* Footer with runtime info */}
        <div
          className={`h-7 border-t ${styles.cardBorder} ${styles.cardBg} px-4 flex items-center justify-between text-[10px] ${styles.cardTextMuted} shrink-0`}
        >
          <div className="flex items-center gap-2 font-mono">
            <span>
              {locale === "zh" ? "工作簿" : "Workbook"} ·{" "}
              {cells.length}{" "}
              {locale === "zh" ? "个单元格" : "cells"}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-slate-400">
              Ctrl+Enter{" "}
              {locale === "zh" ? "执行" : "to execute"}
            </span>
            {runtimes && (
              <span className="flex items-center gap-1">
                {(["sql", "python", "r"] as Language[]).map((lang) => (
                  <span
                    key={lang}
                    className={`w-1.5 h-1.5 rounded-full ${
                      isRuntimeAvailable(lang)
                        ? "bg-emerald-500"
                        : "bg-amber-500"
                    }`}
                    title={`${LANG_LABELS[lang]}: ${
                      isRuntimeAvailable(lang)
                        ? locale === "zh"
                          ? "可用"
                          : "available"
                        : locale === "zh"
                        ? "不可用"
                        : "unavailable"
                    }`}
                  ></span>
                ))}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* ── History Sidebar ─────────────────────────────── */}
      <WorkbookHistory
        history={history}
        loading={historyLoading}
        locale={locale}
        styles={styles}
        visible={showHistory}
        onRefresh={fetchHistory}
        onClose={() => setShowHistory(false)}
      />
    </div>
    </ErrorBoundary>
  );
}
