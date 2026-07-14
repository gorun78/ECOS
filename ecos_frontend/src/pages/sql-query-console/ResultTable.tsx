/**
 * SQL Query Console — Result Table
 * @license Apache-2.0
 */
import React from 'react';
import { useTheme } from '../../components/ThemeContext';
import type { QueryResult } from './types';

interface ResultTableProps {
  result: QueryResult | null;
  loading: boolean;
  error: string | null;
}

export default function ResultTable({ result, loading, error }: ResultTableProps) {
  const { styles } = useTheme();

  if (loading) {
    return (
      <div className={`flex-1 flex items-center justify-center ${styles.cardTextMuted} text-xs gap-2`}>
        <div className="w-4 h-4 border-2 border-slate-500 border-t-cyan-400 rounded-full animate-spin" />
        执行中...
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center text-rose-400 text-xs p-4">
        ⚠ {error}
      </div>
    );
  }

  if (!result) {
    return (
      <div className={`flex-1 flex items-center justify-center ${styles.cardTextMuted} text-xs`}>
        Ctrl+Enter 执行查询
      </div>
    );
  }

  const { columns, rows, rowCount, elapsedMs } = result;

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* 状态栏 */}
      <div className={`flex items-center gap-4 px-3 py-1.5 border-b ${styles.cardBorder} text-[10px] ${styles.cardTextMuted}`}>
        <span className="text-emerald-400 font-bold">{rowCount} 行</span>
        <span>{elapsedMs}ms</span>
        <span>{columns.length} 列</span>
        <div className="flex-1" />
        <button
          onClick={() => exportCSV(columns, rows)}
          className="text-blue-400 hover:text-blue-300 cursor-pointer"
        >
          导出 CSV
        </button>
      </div>

      {/* 表格 */}
      <div className="flex-1 overflow-auto">
        <table className="w-full text-[11px] border-collapse">
          <thead className="sticky top-0 z-10">
            <tr className={styles.cardBg}>
              {columns.map(col => (
                <th key={col} className={`px-3 py-1.5 text-left font-bold ${styles.cardText} border-b ${styles.cardBorder} whitespace-nowrap`}>
                  {col}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.slice(0, 500).map((row: any, i: number) => (
              <tr key={i} className={`hover:bg-white/5 ${i % 2 === 0 ? '' : 'bg-black/10'}`}>
                {columns.map(col => (
                  <td key={col} className={`px-3 py-1 ${styles.cardTextMuted} border-b border-slate-800/50 max-w-[300px] truncate`}>
                    {row[col] === null ? <span className="text-slate-600 italic">NULL</span> : String(row[col])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function exportCSV(columns: string[], rows: Record<string, any>[]) {
  const header = columns.join(',');
  const body = rows.map(row => columns.map(c => {
    const v = row[c];
    if (v === null || v === undefined) return '';
    const s = String(v);
    return s.includes(',') || s.includes('"') ? `"${s.replace(/"/g, '""')}"` : s;
  }).join(',')).join('\n');
  const blob = new Blob(['\uFEFF' + header + '\n' + body], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = `query-result-${Date.now()}.csv`;
  a.click(); URL.revokeObjectURL(url);
}
