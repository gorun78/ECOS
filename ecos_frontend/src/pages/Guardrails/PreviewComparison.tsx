import { useTheme } from '../../components/ThemeContext';
import { LockOpen, Lock } from 'lucide-react';
import type { PreviewData } from './types';
import { getPreviewRows } from './helpers';

// ─────────────────────────────────────────────────────────────
// Preview Comparison (raw vs compiled)
// ─────────────────────────────────────────────────────────────

export default function PreviewComparison({ data }: { data: PreviewData }) {
  const raw = getPreviewRows(data.raw);
  const compiled = getPreviewRows(data.compiled);
  const columns = data.columns && data.columns.length > 0 ? data.columns : (raw.columns.length > 0 ? raw.columns : compiled.columns);
  const { styles } = useTheme();

  return (
    <div className="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-4 min-h-0 overflow-hidden text-[10px]">
      {/* Raw */}
      <div className={`flex flex-col border ${styles.cardBorder} rounded-xl overflow-hidden min-h-0 ${styles.appBg}`}>
        <div className={`p-2 border-b ${styles.cardBorder} ${styles.appBg} flex items-center justify-between`}>
          <span className={`font-bold ${styles.cardText} flex items-center gap-1`}>
            <LockOpen size={10} className={styles.cardTextMuted} /> 原始明文视图 (Raw - Unsecured)
          </span>
          <span className={`px-1 py-0.5 rounded ${styles.badgeBg} ${styles.cardTextMuted} text-[8px] font-mono`}>PLAIN_TEXT</span>
        </div>
        <div className="flex-1 overflow-auto p-2">
          {raw.rows.length === 0 ? (
            <p className={`text-center ${styles.cardTextMuted} py-4 italic`}>无原始数据</p>
          ) : (
            <DataTable rows={raw.rows} columns={columns} />
          )}
        </div>
      </div>

      {/* Compiled */}
      <div className="flex flex-col border border-rose-200 bg-rose-50/5 rounded-xl overflow-hidden min-h-0">
        <div className="p-2 border-b border-rose-100 bg-rose-500/5 flex items-center justify-between">
          <span className="font-extrabold text-rose-800 flex items-center gap-1">
            <Lock size={10} className="text-rose-600" /> 合规安全视图 (Compiled - Secure)
          </span>
          <span className="px-1 py-0.5 rounded bg-rose-600 text-white text-[8px] font-mono">MASKED & SLICED</span>
        </div>
        <div className="flex-1 overflow-auto p-2">
          {compiled.rows.length === 0 ? (
            <p className={`text-center ${styles.cardTextMuted} py-4 italic`}>🚫 行级过滤生效：无符合安全条件的数据行</p>
          ) : (
            <DataTable rows={compiled.rows} columns={columns} rawRows={raw.rows} />
          )}
        </div>
      </div>
    </div>
  );
}

export function DataTable({ rows, columns, rawRows }: { rows: any[]; columns: string[]; rawRows?: any[] }) {
  if (columns.length === 0 && rows.length > 0) columns = Object.keys(rows[0]);
  const { styles } = useTheme();
  return (
    <table className="w-full text-left font-mono leading-relaxed">
      <thead className={`${styles.appBg} border-b ${styles.cardBorder} ${styles.cardTextMuted} font-extrabold sticky top-0`}>
        <tr>
          {columns.map(c => <th key={c} className="p-1 whitespace-nowrap">{c}</th>)}
        </tr>
      </thead>
      <tbody className={`divide-y ${styles.divider} ${styles.cardText}`}>
        {rows.map((r, i) => {
          const raw = rawRows?.find(rr => Object.values(rr)[0] === Object.values(r)[0]);
          return (
            <tr key={i} className={`${styles.sidebarHoverBg}`}>
              {columns.map(c => {
                const val = r[c];
                const rawVal = raw?.[c];
                const masked = raw && rawVal !== undefined && String(rawVal) !== String(val);
                return (
                  <td key={c} className={`p-1 ${masked ? 'text-amber-600 bg-amber-50 rounded-sm font-bold' : ''}`}>
                    {typeof val === 'number' ? val.toLocaleString() : String(val ?? '')}
                  </td>
                );
              })}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
