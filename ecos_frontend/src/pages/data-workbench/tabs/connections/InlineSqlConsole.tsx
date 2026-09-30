/* Extracted from ConnectionsTab.tsx */
import React from 'react';
import LucideIcon from '../../LucideIcon';
import { getErrorMessage } from '../../helpers';
import { useTheme } from "../../../../components/ThemeContext";
import { useLanguage } from "../../../../components/LanguageContext";

// Inline SQL Query Console (embedded, reuses datasource ID)
/** 后端 QueryExecutionServiceImpl.execute 返回 Map：columns[{name,label,type}] / rows(以列 label 为键) / rowCount / elapsedMs */
interface QueryExecuteColumn { name?: string; label?: string; type?: string }
interface QueryExecuteRawResult {
  columns?: (string | QueryExecuteColumn)[];
  rows?: Record<string, unknown>[];
  rowCount?: number;
  elapsedMs?: number;
}
interface SqlResult {
  columns: string[];
  rows: Record<string, unknown>[];
  rowCount: number;
  elapsedMs: number;
}
export default function InlineSqlConsole({ datasourceId }: { datasourceId: string }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [sql, setSql] = React.useState('SELECT * FROM orders LIMIT 10');
  const [result, setResult] = React.useState<SqlResult | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [collapsed, setCollapsed] = React.useState(false);

  const execute = async () => {
    setLoading(true); setError(null);
    try {
      const token = localStorage.getItem('token') || '';
      const res = await fetch('/api/v1/engine/data/query/execute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ datasource_id: datasourceId, sql: sql.trim(), max_rows: 500, timeout_seconds: 30 })
      });
      if (!res.ok) throw new Error(await res.text());
      const data = await res.json();
      const d: QueryExecuteRawResult = data.data || data;
      // 后端返回 columns 为对象数组 [{name, label, type}]，rows 以 columnLabel 为键
      // 提取 label 作为列名，用于渲染和行数据取值
      const rawCols = d.columns || [];
      const colLabels: string[] = rawCols.map((c) =>
        typeof c === 'string' ? c : (c.label || c.name || '')
      );
      setResult({ columns: colLabels, rows: d.rows || [], rowCount: d.rowCount || 0, elapsedMs: d.elapsedMs || 0 });
    } catch (e) {
      setError(getErrorMessage(e) || t("dw.execFailed"));
      setResult(null);
    } finally { setLoading(false); }
  };

  return (
    <div className={`border ${styles.cardBorder} rounded-xl overflow-hidden`}>
      <div className={`${styles.sidebarBg} px-4 py-2 flex items-center justify-between cursor-pointer select-none`}
           onClick={() => setCollapsed(!collapsed)}>
        <div className={`flex items-center gap-2 text-xs font-bold ${styles.cardText}`}>
          <LucideIcon name="Terminal" size={14} className={`${styles.accentText}`} />
          <span>{t("dw.sqlConsole")}</span>
        </div>
        <LucideIcon name={collapsed ? 'ChevronDown' : 'ChevronUp'} size={14} className={`${styles.cardTextMuted}`} />
      </div>
      {!collapsed && (
        <div className={`${styles.cardBg} p-3 space-y-3`}>
          {/* SQL editor + run button */}
          <div className="flex gap-2">
            <textarea value={sql} onChange={e => setSql(e.target.value)}
              className={`flex-1 p-2 border ${styles.inputBorder} rounded text-xs font-mono resize-none outline-none focus:${styles.accentBorder} h-16 ${styles.inputBg} ${styles.inputText}`}
              placeholder="SELECT * FROM ..." spellCheck={false} />
            <button onClick={execute} disabled={loading}
              className={`px-4 py-1 ${styles.accentBg} ${styles.accentHover} ${styles.cardText} text-xs font-semibold rounded cursor-pointer disabled:opacity-50 shrink-0`}>
              {loading ? t("dw.executing") : t("dw.runExec")}
            </button>
          </div>
          {/* Result */}
          {error && <div className={`${styles.dangerText} text-xs ${styles.appBg} p-2 rounded`}>⚠ {error}</div>}
          {result && !error && (
            <div>
              <div className={`flex items-center gap-3 text-[10px] ${styles.cardTextMuted} mb-2`}>
                <span className={`font-bold ${styles.accentText}`}>{result.rowCount} {t("dw.rowsUnit")}</span>
                <span>{result.elapsedMs}ms</span>
                <span>{result.columns.length} {t("dw.colsUnit")}</span>
              </div>
              <div className={`max-h-64 overflow-auto border ${styles.cardBorder} rounded`}>
                <table className="w-full text-[11px]">
                  <thead><tr className={`${styles.appBg}`}>
                    {result.columns.map((c: string) => (
                      <th key={c} className={`px-2 py-1 text-left font-bold ${styles.cardText} whitespace-nowrap border-b`}>{c}</th>
                    ))}
                  </tr></thead>
                  <tbody>
                    {result.rows.slice(0, 50).map((row, i: number) => (
                      <tr key={i} className={i % 2 ? `${styles.appBg}/50` : ''}>
                        {result.columns.map((c: string) => (
                          <td key={c} className={`px-2 py-0.5 ${styles.cardTextMuted} border-b ${styles.cardBorder} max-w-[200px] truncate`}>
                            {row[c] === null ? <span className={`${styles.cardTextMuted} italic`}>NULL</span> : String(row[c])}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
