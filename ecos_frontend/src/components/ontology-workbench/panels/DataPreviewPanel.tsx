/**
 * DataPreviewPanel — 数据预览面板
 *
 * 显示实体映射物理表的数据预览。
 * 从 useWorkbenchStore.dataPreview 获取预览数据并渲染为表格。
 *
 * Props:
 *   - entityId: 本体实体 ID
 *   - resourceId?: 指定预览的物理资源 ID（可选，默认取第一个映射资源）
 *
 * 深色主题，中文界面。
 *
 * @license Apache-2.0
 */

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  Loader2,
  AlertTriangle,
  Database,
  Download,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { useWorkbenchStore } from '../../../stores/useWorkbenchStore';
import { useTheme } from '../../ThemeContext';
import { useLanguage } from '../../LanguageContext';
import { DATA_CATALOG_UNAVAILABLE } from '../../../services/dataCatalogClient';

// ── 组件接口 ────────────────────────────────────────────────

interface DataPreviewPanelProps {
  entityId: string;
  resourceId?: string;
}

/** 每页行数 */
const PAGE_SIZE = 20;

// ── 主组件 ──────────────────────────────────────────────────

export default function DataPreviewPanel({
  entityId,
  resourceId,
}: DataPreviewPanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const store = useWorkbenchStore();
  const {
    dataPreview,
    entityTableMappings,
    fetchDataPreview,
  } = store;

  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [retrying, setRetrying] = useState(false);

  const { rows, loading } = dataPreview;

  // 首次加载预览
  useEffect(() => {
    if (!entityId) return;
    setError(null);
    setPage(0);

    fetchDataPreview(entityId).catch((err: unknown) => {
      const msg = (err as { message?: string } | undefined)?.message || DATA_CATALOG_UNAVAILABLE;
      setError(msg);
    });
  }, [entityId, fetchDataPreview]);

  // 计算列名
  const columns = useMemo(() => {
    if (!rows || rows.length === 0) return [];
    // 从第一行提取所有 key
    const keys = new Set<string>();
    rows.forEach((row) => {
      Object.keys(row).forEach((k) => keys.add(k));
    });
    return Array.from(keys);
  }, [rows]);

  // 分页数据
  const totalPages = Math.max(1, Math.ceil((rows?.length || 0) / PAGE_SIZE));
  const pageRows = useMemo(() => {
    if (!rows) return [];
    const start = page * PAGE_SIZE;
    return rows.slice(start, start + PAGE_SIZE);
  }, [rows, page]);

  // 重试
  const handleRetry = useCallback(async () => {
    setRetrying(true);
    setError(null);
    try {
      await fetchDataPreview(entityId);
    } catch (err: unknown) {
      const msg = (err as { message?: string } | undefined)?.message;
      setError(msg || DATA_CATALOG_UNAVAILABLE);
    } finally {
      setRetrying(false);
    }
  }, [entityId, fetchDataPreview]);

  // ── 加载态 ──
  if (loading && retrying === false) {
    return (
      <div className="flex flex-col items-center justify-center py-16">
        <Loader2 size={22} className="animate-spin text-indigo-400 mb-3" />
        <p className={`text-xs ${styles.cardTextMuted}`}>{t("ow.preview.loading")}</p>
      </div>
    );
  }

  // ── 错误态 ──
  if (error && (!rows || rows.length === 0)) {
    return (
      <div className="flex flex-col items-center justify-center py-12 space-y-4">
        <div className="flex items-start gap-2.5 px-4 py-3 rounded-lg bg-amber-500/10 border border-amber-500/20 max-w-sm">
          <AlertTriangle size={15} className="text-amber-400 mt-0.5 shrink-0" />
          <div>
            <p className="text-xs text-amber-300 font-medium">{t("ow.preview.error.title")}</p>
            <p className="text-[10px] text-amber-500/70 mt-1">{error}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={handleRetry}
          disabled={retrying}
          className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs text-indigo-400
            border border-indigo-500/20 hover:bg-indigo-500/10
            disabled:opacity-50 transition"
        >
          {retrying ? (
            <Loader2 size={12} className="animate-spin" />
          ) : (
            <RefreshCw size={12} />
          )}
          {t("ow.preview.error.retry")}
        </button>
      </div>
    );
  }

  // ── 空状态 ──
  if (!rows || rows.length === 0) {
    const mappedIds = entityTableMappings[entityId] || [];
    const hasMappings = mappedIds.length > 0;

    return (
      <div className={`flex flex-col items-center justify-center py-12 ${styles.muted}`}>
        <Database size={32} className="mb-3 opacity-20" />
        <p className="text-xs">
          {hasMappings ? t("ow.preview.empty.nodata") : t("ow.preview.empty.unmapped")}
        </p>
        <p className="text-[10px] mt-1.5 opacity-60 max-w-[200px] text-center">
          {hasMappings
            ? t("ow.preview.empty.nodataHint")
            : t("ow.preview.empty.unmappedHint")}
        </p>
        {hasMappings && (
          <button
            type="button"
            onClick={handleRetry}
            disabled={retrying}
            className="mt-4 flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[10px] text-indigo-400
              border border-indigo-500/20 hover:bg-indigo-500/10 transition"
          >
            <RefreshCw size={11} />
            {t("ow.preview.empty.reload")}
          </button>
        )}
      </div>
    );
  }

  // ── 错误提示 + 数据仍可展示 ──
  const errorBanner = error ? (
    <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-amber-500/10 border border-amber-500/20 mb-3">
      <AlertTriangle size={12} className="text-amber-400 shrink-0" />
      <p className="text-[10px] text-amber-400/80 flex-1">{error}</p>
      <button
        type="button"
        onClick={() => setError(null)}
        className="text-[10px] text-amber-400 hover:text-amber-300 underline shrink-0"
      >
        {t("ow.preview.error.dismiss")}
      </button>
    </div>
  ) : null;

  // ── 数据表格 ──
  return (
    <div className="space-y-3">
      {/* 头部信息 */}
      <div className="flex items-center justify-between">
        <span className={`text-[10px] ${styles.muted}`}>
          {t("ow.preview.summary", { rows: rows.length, cols: columns.length })}
        </span>
        <button
          type="button"
          onClick={handleRetry}
          disabled={retrying}
          className={`flex items-center gap-1 px-2 py-1 rounded text-[10px] ${styles.muted}
            hover:${styles.cardText} hover:bg-white/5 transition`}
          title={t("ow.preview.refreshTitle")}
        >
          <RefreshCw size={11} className={retrying ? 'animate-spin' : ''} />
          {t("ow.preview.refresh")}
        </button>
      </div>

      {errorBanner}

      {/* 表格容器 */}
      <div className="overflow-x-auto rounded-lg border border-[#1E293B]">
        <table className="w-full text-left border-collapse">
          {/* 表头 */}
          <thead>
            <tr className="bg-[#0f131a] border-b border-[#1E293B]">
              <th className={`sticky left-0 bg-[#0f131a] px-3 py-2 text-[10px] font-medium ${styles.cardTextMuted}
                border-r border-[#1E293B] min-w-[40px] text-center`}>
                #
              </th>
              {columns.map((col) => (
                <th
                  key={col}
                  className={`px-3 py-2 text-[10px] font-medium ${styles.cardTextMuted} whitespace-nowrap
                    border-r border-[#1E293B] last:border-r-0 min-w-[100px]`}
                >
                  <div className="flex items-center gap-1.5">
                    <span className="font-mono">{col}</span>
                  </div>
                </th>
              ))}
            </tr>
          </thead>

          {/* 表体 */}
          <tbody>
            {pageRows.map((row, rowIdx) => {
              const globalIdx = page * PAGE_SIZE + rowIdx;
              return (
                <tr
                  key={globalIdx}
                  className={`border-b border-[#1a1f2e] hover:bg-white/[0.02] transition ${
                    rowIdx % 2 === 0 ? 'bg-transparent' : 'bg-[#0b0e14]/30'
                  }`}
                >
                  <td className={`sticky left-0 px-3 py-1.5 text-[10px] ${styles.muted} font-mono
                    border-r border-[#1E293B] text-center bg-inherit`}>
                    {globalIdx + 1}
                  </td>
                  {columns.map((col) => {
                    const val = row[col];
                    const display =
                      val === null || val === undefined
                        ? 'NULL'
                        : typeof val === 'object'
                        ? JSON.stringify(val)
                        : String(val);

                    const isNull = val === null || val === undefined;
                    return (
                      <td
                        key={col}
                        className={`px-3 py-1.5 text-[11px] whitespace-nowrap max-w-[200px] truncate
                          border-r border-[#1E293B] last:border-r-0 ${
                            isNull
                              ? `${styles.muted} italic`
                              : styles.cardText
                          }`}
                        title={display}
                      >
                        {display.length > 50
                          ? display.slice(0, 50) + '...'
                          : display}
                      </td>
                    );
                  })}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* 分页 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <span className={`text-[10px] ${styles.muted}`}>
            {t("ow.preview.page", { cur: page + 1, total: totalPages })}
          </span>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className={`p-1.5 rounded ${styles.cardTextMuted} hover:${styles.cardText} hover:bg-white/5
                disabled:opacity-30 disabled:cursor-not-allowed transition`}
            >
              <ChevronLeft size={13} />
            </button>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className={`p-1.5 rounded ${styles.cardTextMuted} hover:${styles.cardText} hover:bg-white/5
                disabled:opacity-30 disabled:cursor-not-allowed transition`}
            >
              <ChevronRight size={13} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
