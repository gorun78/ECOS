/**
 * WorkbookHistory — History sidebar for CodeWorkbook
 * @license Apache-2.0
 */

import React from "react";
import { RotateCw, Clock, History, X } from "lucide-react";

interface HistoryEntry {
  language: string;
  code: string;
  output: string;
  elapsed_ms: number;
  createdAt: string;
}

interface WorkbookHistoryProps {
  history: HistoryEntry[];
  loading: boolean;
  locale: string;
  styles: {
    cardBorder: string;
    cardBg: string;
    cardTextMuted: string;
    appBg: string;
    divider: string;
  };
  visible: boolean;
  onRefresh: () => void;
  onClose: () => void;
}

export default function WorkbookHistory({
  history,
  loading,
  locale,
  styles,
  visible,
  onRefresh,
  onClose,
}: WorkbookHistoryProps) {
  if (!visible) return null;

  return (
    <div
      className={`w-full md:w-[300px] border-t md:border-t-0 md:border-l ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 overflow-hidden`}
    >
      <div
        className={`p-4 border-b ${styles.cardBorder} flex items-center justify-between shrink-0`}
      >
        <div className="flex items-center gap-2">
          <Clock className="w-4 h-4 text-indigo-500" />
          <span className="text-xs font-bold uppercase tracking-wider">
            {locale === "zh" ? "执行历史" : "History"}
          </span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={onRefresh}
            className={`p-1 rounded hover:bg-black/10 dark:hover:bg-white/10 ${styles.cardTextMuted}`}
            title={locale === "zh" ? "刷新" : "Refresh"}
          >
            <RotateCw
              className={`w-3.5 h-3.5 ${
                loading ? "animate-spin" : ""
              }`}
            />
          </button>
          <button
            onClick={onClose}
            className={`p-1 rounded hover:bg-black/10 dark:hover:bg-white/10 ${styles.cardTextMuted}`}
            title={locale === "zh" ? "关闭" : "Close"}
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {loading ? (
          <div className="flex items-center justify-center py-12">
            <RotateCw className={`w-5 h-5 ${styles.cardTextMuted} animate-spin`} />
          </div>
        ) : history.length === 0 ? (
          <div className="flex flex-col items-center justify-center p-6 text-center">
            <div className={`p-3 ${styles.appBg} rounded-full mb-3`}>
              <History className={`w-6 h-6 ${styles.cardTextMuted}`} />
            </div>
            <p className={`text-xs ${styles.cardTextMuted} leading-relaxed`}>
              {locale === "zh"
                ? "暂无执行记录。执行代码后将在此显示。"
                : "No execution history yet. Run some code to see records here."}
            </p>
          </div>
        ) : (
          <div className="py-1">
            {history.map((entry, idx) => (
              <div
                key={idx}
                className={`px-4 py-3 border-b ${styles.divider} hover:bg-blue-50/20 transition-colors`}
              >
                <div className="flex items-center justify-between mb-1">
                  <span
                    className={`text-[10px] font-bold uppercase px-1.5 py-0.5 rounded ${
                      entry.language === "sql"
                        ? "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300"
                        : entry.language === "python"
                        ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300"
                        : "bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300"
                    }`}
                  >
                    {entry.language.toUpperCase()}
                  </span>
                  <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>
                    {entry.elapsed_ms != null
                      ? `${entry.elapsed_ms}ms`
                      : ""}
                  </span>
                </div>
                <div className={`font-mono text-[10px] truncate mb-1 ${styles.appBg} ${styles.cardTextMuted} rounded px-2 py-1`}>
                  {entry.code.length > 80
                    ? entry.code.slice(0, 80) + "..."
                    : entry.code}
                </div>
                {entry.output && (
                  <div className={`font-mono text-[10px] truncate max-h-8 overflow-hidden ${styles.cardTextMuted} opacity-80`}>
                    {entry.output.slice(0, 120)}
                  </div>
                )}
                {entry.createdAt && (
                  <div className={`text-[9px] mt-1 ${styles.cardTextMuted} opacity-80`}>
                    {new Date(entry.createdAt).toLocaleString()}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
