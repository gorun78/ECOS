/**
 * Pagination — 通用分页器组件
 * @license Apache-2.0
 */

import React from "react";
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";
import { useTheme } from "../ThemeContext";

export interface PaginationProps {
  page: number;
  pageSize: number;
  total: number;
  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
}

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

export default function Pagination({
  page,
  pageSize,
  total,
  onPageChange,
  onPageSizeChange,
}: PaginationProps) {
  const { styles } = useTheme();
  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  const setPage = (p: number) => {
    const clamped = Math.max(1, Math.min(p, totalPages));
    if (clamped !== page) {
      onPageChange(clamped);
    }
  };

  // Generate visible page numbers
  const getVisiblePages = (): (number | "...")[] => {
    const pages: (number | "...")[] = [];
    const delta = 2;
    const left = Math.max(2, page - delta);
    const right = Math.min(totalPages - 1, page + delta);

    pages.push(1);
    if (left > 2) pages.push("...");
    for (let i = left; i <= right; i++) pages.push(i);
    if (right < totalPages - 1) pages.push("...");
    if (totalPages > 1) pages.push(totalPages);

    return pages;
  };

  const visiblePages = getVisiblePages();

  if (total === 0) return null;

  return (
    <div
      className={`flex items-center justify-between px-3 py-2.5 border-t ${styles.cardBorder} text-xs select-none`}
    >
      <span className={`${styles.cardTextMuted} text-[11px]`}>
        {total} 条记录，第 {page}/{totalPages} 页
      </span>

      <div className="flex items-center gap-1">
        {/* First page */}
        <button
          className={`p-1 rounded transition cursor-pointer ${
            page <= 1
              ? "opacity-30 cursor-not-allowed"
              : `${styles.cardTextMuted} hover:bg-black/10 dark:hover:bg-white/10`
          }`}
          disabled={page <= 1}
          onClick={() => setPage(1)}
        >
          <ChevronsLeft className="w-3.5 h-3.5" />
        </button>

        {/* Previous */}
        <button
          className={`p-1 rounded transition cursor-pointer ${
            page <= 1
              ? "opacity-30 cursor-not-allowed"
              : `${styles.cardTextMuted} hover:bg-black/10 dark:hover:bg-white/10`
          }`}
          disabled={page <= 1}
          onClick={() => setPage(page - 1)}
        >
          <ChevronLeft className="w-3.5 h-3.5" />
        </button>

        {/* Page numbers */}
        {visiblePages.map((p, i) =>
          p === "..." ? (
            <span key={`ellipsis-${i}`} className={`px-1 ${styles.cardTextMuted}`}>
              ...
            </span>
          ) : (
            <button
              key={p}
              className={`min-w-[24px] h-6 px-1 rounded text-xs font-bold transition cursor-pointer ${
                p === page
                  ? `${styles.accentBg} text-white`
                  : `${styles.cardTextMuted} hover:bg-black/10 dark:hover:bg-white/10`
              }`}
              onClick={() => setPage(p)}
            >
              {p}
            </button>
          )
        )}

        {/* Next */}
        <button
          className={`p-1 rounded transition cursor-pointer ${
            page >= totalPages
              ? "opacity-30 cursor-not-allowed"
              : `${styles.cardTextMuted} hover:bg-black/10 dark:hover:bg-white/10`
          }`}
          disabled={page >= totalPages}
          onClick={() => setPage(page + 1)}
        >
          <ChevronRight className="w-3.5 h-3.5" />
        </button>

        {/* Last page */}
        <button
          className={`p-1 rounded transition cursor-pointer ${
            page >= totalPages
              ? "opacity-30 cursor-not-allowed"
              : `${styles.cardTextMuted} hover:bg-black/10 dark:hover:bg-white/10`
          }`}
          disabled={page >= totalPages}
          onClick={() => setPage(totalPages)}
        >
          <ChevronsRight className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Page size selector */}
      {onPageSizeChange && (
        <div className="flex items-center gap-1.5 ml-3">
          <span className={`${styles.cardTextMuted} text-[10px]`}>每页</span>
          <select
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
            className={`px-1.5 py-0.5 text-[11px] rounded border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} cursor-pointer`}
          >
            {PAGE_SIZE_OPTIONS.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
          <span className={`${styles.cardTextMuted} text-[10px]`}>条</span>
        </div>
      )}
    </div>
  );
}
