/**
 * DataLake Page — SQL Console + Dataset Browser + Health Status
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { apiFetchData } from "../api";
import ErrorBoundary from "../components/common/ErrorBoundary";
import {
  Database, HardDrive, Play, Download, RefreshCw,
  Loader2, AlertCircle, CheckCircle2, XCircle,
  ChevronRight, Table, Terminal, FileText, Server
} from "lucide-react";

interface HealthStatus {
  duckdb: string;
  minio: string;
}

interface TableInfo {
  table: string;
  format: string;
  file_size_mb: number;
  path: string;
}

interface QueryResult {
  sql: string;
  row_count: number;
  data: Record<string, unknown>[];
}

export default function DataLake() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const tzh = useCallback((zh: string, en: string) => locale === "zh" ? zh : en, [locale]);

  // Health
  const [health, setHealth] = useState<HealthStatus | null>(null);
  const [healthLoading, setHealthLoading] = useState(false);
  const [healthError, setHealthError] = useState("");

  // Tables
  const [tables, setTables] = useState<TableInfo[]>([]);
  const [tablesLoading, setTablesLoading] = useState(false);
  const [selectedTable, setSelectedTable] = useState("");

  // Query
  const [sql, setSql] = useState("");
  const [queryResult, setQueryResult] = useState<QueryResult | null>(null);
  const [queryLoading, setQueryLoading] = useState(false);
  const [queryError, setQueryError] = useState("");

  // Export
  const [exportLoading, setExportLoading] = useState(false);
  const [exportMsg, setExportMsg] = useState("");

  // ── Fetch health ──
  const fetchHealth = useCallback(async () => {
    setHealthLoading(true);
    setHealthError("");
    try {
      const data = await apiFetchData<HealthStatus>("/api/datalake/health");
      setHealth(data);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      setHealthError(msg);
    } finally {
      setHealthLoading(false);
    }
  }, []);

  // ── Fetch tables ──
  const fetchTables = useCallback(async () => {
    setTablesLoading(true);
    try {
      const data = await apiFetchData<TableInfo[]>("/api/datalake/tables");
      setTables(data || []);
    } catch (e: unknown) {
      console.error("Failed to fetch tables", e);
    } finally {
      setTablesLoading(false);
    }
  }, []);

  // ── Execute SQL ──
  const executeQuery = useCallback(async () => {
    if (!sql.trim()) return;
    setQueryLoading(true);
    setQueryError("");
    setQueryResult(null);
    try {
      const data = await apiFetchData<QueryResult>("/api/datalake/query", {
        method: "POST",
        body: JSON.stringify({ sql: sql.trim() }),
      });
      setQueryResult(data);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      setQueryError(msg);
    } finally {
      setQueryLoading(false);
    }
  }, [sql]);

  // ── Export table ──
  const handleExport = useCallback(async () => {
    if (!selectedTable) return;
    setExportLoading(true);
    setExportMsg("");
    try {
      const data = await apiFetchData<{ table: string; row_count: number; status: string }>(
        "/api/datalake/export",
        {
          method: "POST",
          body: JSON.stringify({ table: selectedTable }),
        }
      );
      setExportMsg(tzh(
        `✅ 导出成功: ${data.table}, ${data.row_count} 行, 状态: ${data.status}`,
        `✅ Export OK: ${data.table}, ${data.row_count} rows, status: ${data.status}`
      ));
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      setExportMsg(tzh(`❌ 导出失败: ${msg}`, `❌ Export failed: ${msg}`));
    } finally {
      setExportLoading(false);
    }
  }, [selectedTable, tzh]);

  // ── Key binding: Ctrl+Enter to execute ──
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
        e.preventDefault();
        executeQuery();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [executeQuery]);

  // ── Initial load ──
  useEffect(() => {
    fetchHealth();
    fetchTables();
  }, [fetchHealth, fetchTables]);

  // ── Select table → prefill SQL ──
  const handleTableSelect = (tableName: string) => {
    setSelectedTable(tableName);
    setSql(`SELECT * FROM ${tableName} LIMIT 100`);
    setQueryResult(null);
    setQueryError("");
  };

  // ── Render health badge ──
  const renderHealthBadge = (label: string, status: string | undefined) => {
    if (!status) {
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400">
          <Loader2 className="w-3 h-3 animate-spin" />
          {label}
        </span>
      );
    }
    const isUp = status === "UP";
    return (
      <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${
        isUp
          ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-400"
          : "bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400"
      }`}>
        {isUp ? <CheckCircle2 className="w-3 h-3" /> : <XCircle className="w-3 h-3" />}
        {label}: {status}
      </span>
    );
  };

  // ── Columns from query result ──
  const columns = queryResult?.data && queryResult.data.length > 0
    ? Object.keys(queryResult.data[0])
    : [];

  return (
    <ErrorBoundary>
    <div className="flex flex-col h-full overflow-hidden">
      {/* ── Top: Health status bar ── */}
      <div className={`shrink-0 px-4 py-3 border-b ${styles.cardBorder} ${styles.cardBg}`}>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Server className="w-4 h-4 opacity-50" />
            <span className="text-xs font-semibold uppercase tracking-wider opacity-60">
              {tzh("数据湖服务状态", "DataLake Service Status")}
            </span>
            <div className="flex items-center gap-2">
              {renderHealthBadge("DuckDB", health?.duckdb)}
              {renderHealthBadge("MinIO", health?.minio)}
            </div>
            {healthError && (
              <span className="text-xs text-red-500 flex items-center gap-1">
                <AlertCircle className="w-3 h-3" />
                {healthError}
              </span>
            )}
          </div>
          <button
            onClick={() => { fetchHealth(); fetchTables(); }}
            disabled={healthLoading}
            className="p-1.5 rounded-md hover:bg-black/5 dark:hover:bg-white/10 transition-colors"
            title={tzh("刷新", "Refresh")}
          >
            <RefreshCw className={`w-4 h-4 opacity-60 ${healthLoading ? "animate-spin" : ""}`} />
          </button>
        </div>
      </div>

      {/* ── Main content: left panel + right panel ── */}
      <div className="flex-1 flex min-h-0 overflow-hidden">
        {/* ── Left: Dataset browser ── */}
        <div className={`w-72 shrink-0 border-r ${styles.cardBorder} flex flex-col ${styles.cardBg}`}>
          <div className={`px-3 py-2.5 border-b ${styles.cardBorder} flex items-center gap-2`}>
            <Database className="w-4 h-4 opacity-50" />
            <span className="text-xs font-semibold uppercase tracking-wider opacity-60">
              {tzh("数据集", "Datasets")}
            </span>
            <span className="ml-auto text-[10px] opacity-40">{tables.length}</span>
          </div>
          <div className="flex-1 overflow-y-auto">
            {tablesLoading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="w-5 h-5 animate-spin opacity-40" />
              </div>
            ) : tables.length === 0 ? (
              <div className="px-3 py-6 text-center text-xs opacity-50">
                {tzh("暂无数据集", "No datasets found")}
              </div>
            ) : (
              <div className="py-1">
                {tables.map((tbl) => (
                  <button
                    key={tbl.table}
                    onClick={() => handleTableSelect(tbl.table)}
                    className={`w-full text-left px-3 py-2.5 flex items-start gap-2.5 transition-colors duration-100 ${
                      selectedTable === tbl.table
                        ? "bg-indigo-50 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300"
                        : "hover:bg-black/5 dark:hover:bg-white/5"
                    }`}
                  >
                    <Table className="w-4 h-4 shrink-0 mt-0.5 opacity-60" />
                    <div className="flex-1 min-w-0">
                      <div className="text-[13px] font-medium truncate">{tbl.table}</div>
                      <div className="text-[10px] opacity-50 mt-0.5 flex items-center gap-2">
                        <span className="uppercase">{tbl.format}</span>
                        <span>{tbl.file_size_mb.toFixed(1)} MB</span>
                      </div>
                    </div>
                    <ChevronRight className={`w-3.5 h-3.5 shrink-0 mt-1 transition-transform ${
                      selectedTable === tbl.table ? "rotate-90" : ""
                    }`} />
                  </button>
                ))}
              </div>
            )}
          </div>
          {/* Export button */}
          <div className={`p-3 border-t ${styles.cardBorder}`}>
            <button
              onClick={handleExport}
              disabled={!selectedTable || exportLoading}
              className={`w-full py-2 px-3 rounded-md text-xs font-medium flex items-center justify-center gap-2 transition-colors ${
                !selectedTable || exportLoading
                  ? "bg-gray-100 text-gray-400 cursor-not-allowed dark:bg-gray-800 dark:text-gray-500"
                  : "bg-indigo-600 text-white hover:bg-indigo-700"
              }`}
            >
              {exportLoading ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <Download className="w-3.5 h-3.5" />
              )}
              {tzh("导出选中表", "Export Selected Table")}
            </button>
            {exportMsg && (
              <p className={`text-[10px] mt-1.5 text-center ${
                exportMsg.startsWith("✅") || exportMsg.startsWith("✅")
                  ? "text-emerald-600 dark:text-emerald-400"
                  : "text-red-500"
              }`}>{exportMsg}</p>
            )}
          </div>
        </div>

        {/* ── Right: SQL console + results ── */}
        <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
          {/* SQL Editor */}
          <div className={`shrink-0 px-4 pt-3 pb-2 border-b ${styles.cardBorder} ${styles.cardBg}`}>
            <div className="flex items-center gap-2 mb-2">
              <Terminal className="w-4 h-4 opacity-50" />
              <span className="text-xs font-semibold uppercase tracking-wider opacity-60">
                {tzh("SQL 控制台", "SQL Console")}
              </span>
              {selectedTable && (
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300">
                  {selectedTable}
                </span>
              )}
            </div>
            <div className="flex gap-2">
              <textarea
                value={sql}
                onChange={(e) => setSql(e.target.value)}
                placeholder={tzh(
                  "输入 SQL 查询语句... (Ctrl+Enter 执行)",
                  "Enter SQL query... (Ctrl+Enter to execute)"
                )}
                rows={4}
                className={`flex-1 px-3 py-2 rounded-md border text-sm font-mono resize-none outline-none transition-colors ${
                  styles.inputBg} ${styles.inputText} ${styles.inputBorder}
                  focus:border-indigo-400 focus:ring-1 focus:ring-indigo-400`}
                spellCheck={false}
              />
              <button
                onClick={executeQuery}
                disabled={queryLoading || !sql.trim()}
                className={`shrink-0 px-4 py-2 rounded-md text-sm font-medium flex items-center gap-2 transition-colors self-start ${
                  queryLoading || !sql.trim()
                    ? "bg-gray-100 text-gray-400 cursor-not-allowed dark:bg-gray-800 dark:text-gray-500"
                    : "bg-emerald-600 text-white hover:bg-emerald-700"
                }`}
              >
                {queryLoading ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Play className="w-4 h-4" />
                )}
                {tzh("执行", "Run")}
              </button>
            </div>
            <div className="text-[10px] opacity-40 mt-1">
              Ctrl + Enter {tzh("执行查询", "to execute")}
            </div>
          </div>

          {/* Results */}
          <div className="flex-1 overflow-auto">
            {queryError && (
              <div className="m-4 p-3 rounded-md bg-red-50 border border-red-200 dark:bg-red-900/20 dark:border-red-800">
                <div className="flex items-start gap-2">
                  <AlertCircle className="w-4 h-4 text-red-500 shrink-0 mt-0.5" />
                  <div>
                    <p className="text-sm font-medium text-red-700 dark:text-red-400">
                      {tzh("查询错误", "Query Error")}
                    </p>
                    <pre className="text-xs text-red-600 dark:text-red-300 mt-1 whitespace-pre-wrap font-mono">
                      {queryError}
                    </pre>
                  </div>
                </div>
              </div>
            )}

            {queryResult && (
              <div className="p-4">
                {/* Stats bar */}
                <div className="flex items-center gap-3 mb-3 text-xs">
                  <span className="opacity-60">
                    {tzh("返回行数", "Rows")}: <strong>{queryResult.row_count}</strong>
                  </span>
                  <span className="opacity-60">
                    {tzh("列数", "Cols")}: <strong>{columns.length}</strong>
                  </span>
                  {queryResult.sql && (
                    <span className="opacity-40 truncate max-w-md font-mono">
                      {queryResult.sql}
                    </span>
                  )}
                </div>

                {/* Table */}
                {columns.length > 0 ? (
                  <div className="overflow-auto rounded-lg border border-gray-200 dark:border-gray-700 max-h-[calc(100vh-420px)]">
                    <table className="w-full text-xs border-collapse">
                      <thead>
                        <tr className="bg-gray-50 dark:bg-gray-800/60">
                          <th className="sticky left-0 z-10 bg-gray-50 dark:bg-gray-800/60 px-3 py-2 text-left font-semibold text-gray-500 dark:text-gray-400 border-b border-gray-200 dark:border-gray-700 w-10">
                            #
                          </th>
                          {columns.map((col) => (
                            <th
                              key={col}
                              className="px-3 py-2 text-left font-semibold text-gray-600 dark:text-gray-300 border-b border-gray-200 dark:border-gray-700 whitespace-nowrap"
                            >
                              {col}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {queryResult.data.map((row, idx) => (
                          <tr
                            key={idx}
                            className={`border-b border-gray-100 dark:border-gray-800 ${
                              idx % 2 === 0 ? "bg-white dark:bg-gray-900" : "bg-gray-50/50 dark:bg-gray-800/30"
                            } hover:bg-indigo-50/50 dark:hover:bg-indigo-900/20`}
                          >
                            <td className="sticky left-0 z-10 px-3 py-1.5 text-gray-400 dark:text-gray-500 border-b border-gray-100 dark:border-gray-800 font-mono bg-inherit">
                              {idx + 1}
                            </td>
                            {columns.map((col) => {
                              const val = row[col];
                              const display = val === null
                                ? <span className="italic opacity-40">NULL</span>
                                : val === undefined
                                ? <span className="italic opacity-30">—</span>
                                : String(val);
                              return (
                                <td
                                  key={col}
                                  className="px-3 py-1.5 text-gray-700 dark:text-gray-300 max-w-xs truncate font-mono border-b border-gray-100 dark:border-gray-800"
                                  title={val !== null && val !== undefined ? String(val) : undefined}
                                >
                                  {display}
                                </td>
                              );
                            })}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div className="text-center py-8 text-xs opacity-40">
                    <FileText className="w-6 h-6 mx-auto mb-2 opacity-30" />
                    {tzh("查询成功但无返回数据", "Query succeeded but returned no rows")}
                  </div>
                )}
              </div>
            )}

            {!queryResult && !queryError && !queryLoading && (
              <div className="flex-1 flex items-center justify-center">
                <div className="text-center">
                  <HardDrive className="w-12 h-12 mx-auto mb-3 opacity-20" />
                  <p className="text-sm opacity-40">
                    {tzh(
                      "左侧选择数据集并输入 SQL 查询",
                      "Select a dataset on the left and enter a SQL query"
                    )}
                  </p>
                  <p className="text-xs opacity-25 mt-1">
                    {tzh("支持标准 DuckDB SQL 语法", "Supports standard DuckDB SQL syntax")}
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
      </div>
    </ErrorBoundary>
  );
}
