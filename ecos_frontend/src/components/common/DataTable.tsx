/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from "react";
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";
import { useTheme } from "../ThemeContext";
import LoadingSkeleton from "./LoadingSkeleton";
import EmptyState from "./EmptyState";

export interface ColumnConfig<T> {
  key: string;
  label: string;
  render?: (value: any, record: T, index: number) => React.ReactNode;
  sortable?: boolean;
  width?: string;
  align?: "left" | "center" | "right";
}

export interface DataTableProps<T> {
  columns: ColumnConfig<T>[];
  data: T[];
  rowKey: keyof T | ((record: T) => string);
  loading?: boolean;
  emptyTitle?: string;
  emptyDescription?: string;
  emptyIcon?: React.ReactNode;
  emptyAction?: { label: string; onClick: () => void };
  pageSize?: number;
  currentPage?: number;
  total?: number;
  onPageChange?: (page: number) => void;
  onRowClick?: (record: T) => void;
  className?: string;
  /** If true, hides pagination entirely */
  hidePagination?: boolean;
}

export default function DataTable<T extends Record<string, any>>({
  columns,
  data,
  rowKey,
  loading = false,
  emptyTitle,
  emptyDescription,
  emptyIcon,
  emptyAction,
  pageSize = 10,
  currentPage: controlledPage,
  total: controlledTotal,
  onPageChange,
  onRowClick,
  className = "",
  hidePagination = false,
}: DataTableProps<T>) {
  const { styles } = useTheme();
  const [internalPage, setInternalPage] = useState(1);

  const isControlled = controlledPage !== undefined;
  const page = isControlled ? controlledPage : internalPage;
  const total = controlledTotal ?? data.length;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  // If uncontrolled, slice data by current page
  const displayData = isControlled
    ? data
    : data.slice((page - 1) * pageSize, page * pageSize);

  const setPage = (p: number) => {
    const clamped = Math.max(1, Math.min(p, totalPages));
    if (isControlled) {
      onPageChange?.(clamped);
    } else {
      setInternalPage(clamped);
    }
  };

  const getRowKey = (record: T, index: number): string => {
    if (typeof rowKey === "function") return rowKey(record);
    return String(record[rowKey]);
  };

  const alignClass = (align?: "left" | "center" | "right") => {
    switch (align) {
      case "center": return "text-center";
      case "right": return "text-right";
      default: return "text-left";
    }
  };

  if (loading) {
    return (
      <div className={className}>
        <LoadingSkeleton variant="table" rows={pageSize} />
      </div>
    );
  }

  if (!displayData.length) {
    return (
      <div className={className}>
        <EmptyState
          icon={emptyIcon}
          title={emptyTitle}
          description={emptyDescription}
          action={emptyAction}
        />
      </div>
    );
  }

  return (
    <div className={`${className} flex flex-col`}>
      {/* Table */}
      <div className="overflow-x-auto scrollbar-thin">
        <table className={`w-full text-xs border-collapse ${styles.cardText}`}>
          <thead>
            <tr className={`border-b ${styles.cardBorder}`}>
              {columns.map((col) => (
                <th
                  key={col.key}
                  className={`px-3 py-2.5 font-bold uppercase tracking-wider text-[10px] ${styles.cardTextMuted} ${alignClass(col.align)} select-none`}
                  style={col.width ? { width: col.width } : undefined}
                >
                  {col.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {displayData.map((record, rowIdx) => (
              <tr
                key={getRowKey(record, rowIdx)}
                className={`border-b ${styles.cardBorder} transition-colors duration-100 ${
                  onRowClick
                    ? "cursor-pointer hover:bg-black/5 dark:hover:bg-white/5"
                    : ""
                }`}
                onClick={() => onRowClick?.(record)}
              >
                {columns.map((col) => (
                  <td
                    key={col.key}
                    className={`px-3 py-2.5 ${alignClass(col.align)} ${styles.cardText}`}
                    style={col.width ? { width: col.width } : undefined}
                  >
                    {col.render
                      ? col.render(record[col.key], record, rowIdx)
                      : record[col.key] ?? "—"}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {!hidePagination && totalPages > 1 && (
        <div
          className={`flex items-center justify-between px-3 py-2.5 border-t ${styles.cardBorder} text-xs select-none`}
        >
          <span className={`${styles.cardTextMuted} text-[11px]`}>
            {total} 条记录，第 {page}/{totalPages} 页
          </span>

          <div className="flex items-center gap-1">
            <button
              className={`p-1 rounded transition cursor-pointer ${
                page <= 1 ? "opacity-30 cursor-not-allowed" : `${styles.cardTextMuted} hover:bg-black/10 dark:hover:bg-white/10`
              }`}
              disabled={page <= 1}
              onClick={() => setPage(1)}
            >
              <ChevronsLeft className="w-3.5 h-3.5" />
            </button>
            <button
              className={`p-1 rounded transition cursor-pointer ${
                page <= 1 ? "opacity-30 cursor-not-allowed" : `${styles.cardTextMuted} hover:bg-black/10 dark:hover:bg-white/10`
              }`}
              disabled={page <= 1}
              onClick={() => setPage(page - 1)}
            >
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>

            <span className={`px-2 py-0.5 font-bold ${styles.cardText}`}>
              {page}
            </span>

            <button
              className={`p-1 rounded transition cursor-pointer ${
                page >= totalPages ? "opacity-30 cursor-not-allowed" : `${styles.cardTextMuted} hover:bg-black/10 dark:hover:bg-white/10`
              }`}
              disabled={page >= totalPages}
              onClick={() => setPage(page + 1)}
            >
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
            <button
              className={`p-1 rounded transition cursor-pointer ${
                page >= totalPages ? "opacity-30 cursor-not-allowed" : `${styles.cardTextMuted} hover:bg-black/10 dark:hover:bg-white/10`
              }`}
              disabled={page >= totalPages}
              onClick={() => setPage(totalPages)}
            >
              <ChevronsRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
