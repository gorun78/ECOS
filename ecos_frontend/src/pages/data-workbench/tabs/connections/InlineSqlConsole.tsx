/* Extracted from ConnectionsTab.tsx */
import React from 'react';
import {
  ChevronDown,
  ChevronUp,
  Terminal
} from 'lucide-react';
import { getErrorMessage } from '../../helpers';
import { useTheme } from "../../../../components/ThemeContext";
import { useLanguage } from "../../../../components/LanguageContext";
import ConfirmDialog from "../../../../components/common/ConfirmDialog";
import { API_BASE, rawAuthedUrlFetch } from "../../../../services/httpClient";

/** 结果表最多渲染 50 行（后端 max_rows=500，超出仅展示截断） */
const MAX_RENDER_ROWS = 50;

/** 写操作 / DDL 关键字检测：命中 ⇒ 执行前二次确认（就地执行任意 SQL 的确认门） */
function isRiskySql(sql: string): boolean {
  return /\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|MERGE|REPLACE)\b/i.test(sql);
}

/**
 * 提取本次 SQL 的<b>行级安全作用域</b>声明（#142/B4-3e-19，差距 D-B4-63）。
 *
 * 属主端点现在要求调用方显式声明表集：不声明＝403 `ECOS-SEC-520`（默认 DENY）。
 * 本函数只负责「声明」，不负责「判定」——裁决永远在 security-engine 一侧。
 * 提取不到表名时调用方必须停下来问用户，禁退化成"不带作用域照样发"，
 * 那正是收口前的绕过路径（页面通道行过滤覆盖面恒为零）。
 */
function extractTableScope(sql: string): string[] {
  const stripped = sql.replace(/--[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');
  const found = new Set<string>();
  const pattern = /\b(?:from|join)\s+([a-zA-Z_][\w$]*(?:\.[a-zA-Z_][\w$]*){0,2})/gi;
  let match: RegExpExecArray | null = pattern.exec(stripped);
  while (match !== null) {
    found.add(match[1]);
    match = pattern.exec(stripped);
  }
  return [...found];
}

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
  const [sql, setSql] = React.useState('');
  const [result, setResult] = React.useState<SqlResult | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [collapsed, setCollapsed] = React.useState(false);
  const [confirmRisky, setConfirmRisky] = React.useState(false);

  const execute = async () => {
    // 行级安全作用域声明门（D-B4-63）：提取不到表名就不发请求——属主侧未声明即 403，
    // 在这里拦掉既避免一次无谓往返，也把「这条 SQL 没有可裁决的作用域」讲给用户，
    // 禁静默降级成"不带作用域照发"（那正是收口前的绕过路径）。
    const scope = extractTableScope(sql);
    if (scope.length === 0) {
      setError(t("dw.rlsScopeUndeclared"));
      setResult(null);
      return;
    }
    setLoading(true); setError(null);
    try {
      const res = await rawAuthedUrlFetch(`${API_BASE}/v1/engine/data/query/execute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ datasource_id: datasourceId, sql: sql.trim(), table_names: scope, max_rows: 500, timeout_seconds: 30 })
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

  const handleRunClick = () => {
    if (!sql.trim()) return;
    if (isRiskySql(sql)) { setConfirmRisky(true); return; }
    void execute();
  };

  return (
    <div className={`border ${styles.cardBorder} rounded-xl overflow-hidden`}>
      <div className={`${styles.sidebarBg} px-4 py-2 flex items-center justify-between cursor-pointer select-none`}
           onClick={() => setCollapsed(!collapsed)}>
        <div className={`flex items-center gap-2 text-xs font-bold ${styles.cardText}`}>
          <Terminal size={14} className={`${styles.accentText}`} />
          <span>{t("dw.sqlConsole")}</span>
        </div>
        {collapsed ? <ChevronDown size={14} className={`${styles.cardTextMuted}`} /> : <ChevronUp size={14} className={`${styles.cardTextMuted}`} />}
      </div>
      {!collapsed && (
        <div className={`${styles.cardBg} p-3 space-y-3`}>
          {/* 只读约束警示：本控制台直连数据源，写/DDL 语句将真实生效 */}
          <div className={`text-[10px] px-2 py-1.5 rounded border ${styles.warningBg} ${styles.warningText} border-current`}>
            {t("dw.sqlConsole.readonlyWarning")}
          </div>
          {/* SQL editor + run button */}
          <div className="flex gap-2">
            <textarea value={sql} onChange={e => setSql(e.target.value)}
              className={`flex-1 p-2 border ${styles.inputBorder} rounded text-xs font-mono resize-none outline-none focus:${styles.accentBorder} h-16 ${styles.inputBg} ${styles.inputText}`}
              placeholder="SELECT * FROM ..." spellCheck={false} />
            <button type="button" onClick={handleRunClick} disabled={loading || !sql.trim()}
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
                {result.rows.length > MAX_RENDER_ROWS && (
                  <span className={styles.warningText}>{t("dw.sqlConsole.truncatedHint", { shown: MAX_RENDER_ROWS, total: result.rows.length })}</span>
                )}
              </div>
              <div className={`max-h-64 overflow-auto border ${styles.cardBorder} rounded`}>
                <table className="w-full text-[11px]">
                  <thead><tr className={`${styles.appBg}`}>
                    {result.columns.map((c: string) => (
                      <th key={c} className={`px-2 py-1 text-left font-bold ${styles.cardText} whitespace-nowrap border-b`}>{c}</th>
                    ))}
                  </tr></thead>
                  <tbody>
                    {result.rows.slice(0, MAX_RENDER_ROWS).map((row, i: number) => (
                      <tr key={i} className={i % 2 ? styles.appBg : ''}>
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
      {/* 写/DDL 语句执行确认门（危险操作禁直发） */}
      <ConfirmDialog
        open={confirmRisky}
        variant="danger"
        title={t("dw.sqlConsole.confirmTitle")}
        message={t("dw.sqlConsole.confirmMessage", { sql: sql.trim().slice(0, 200) })}
        confirmText={t("dw.runExec")}
        onConfirm={() => { setConfirmRisky(false); void execute(); }}
        onCancel={() => setConfirmRisky(false)}
      />
    </div>
  );
};
