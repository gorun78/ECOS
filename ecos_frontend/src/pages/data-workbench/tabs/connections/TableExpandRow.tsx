/* Extracted from ConnectionsTab.tsx */
import React, { useState, useCallback, useRef } from 'react';
import LucideIcon from '../../LucideIcon';
import type { TableInfo } from '../../types';
import { useTheme } from "../../../../components/ThemeContext";
import { useLanguage } from "../../../../components/LanguageContext";
import { fetchFields, type DataFieldMeta } from '../../api';

// ── TableExpandRow ──────────────────────────────────────
// 数据表卡片 + 表名右侧 Chevron 图标：
//   首次点击「向下展开」，懒加载 fetchPreview 全量列，
//   面积以 max-height + opacity 过渡实现（340ms cubic-bezier）。
//   再次点击 ChevecDown 图标（轴线反转为 ChevronUp）→ 收起隐藏。
// 主题感知 (useTheme)，i18n (useLanguage)，禁 hardcoded 中文/颜色。
export default function TableExpandRow({ connId, table, selected, onToggle }: { connId: string; table: TableInfo; selected?: boolean; onToggle?: () => void }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [expanded, setExpanded] = useState(false);
  // 字段元数据（name/type/length/primaryKey）——优先窗口 4 列表格展示
  const [fieldRows, setFieldRows] = useState<{ name: string; type: string; length?: number | null; primaryKey?: boolean }[]>(
    (table.columns || []).map(c => ({ name: c.name, type: c.type, length: null as number | null, primaryKey: false }))
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const loadedRef = useRef(false);

  const toggle = useCallback(() => {
    setExpanded(prev => {
      const next = !prev;
      // 首次展开时懒加载字段元数据（名称/类型/长度/主键）
      if (next && !loadedRef.current && table.resourceId) {
        setLoading(true);
        setError(null);
        (async () => {
          try {
            const fs: DataFieldMeta[] = await fetchFields(table.resourceId!);
            if (Array.isArray(fs) && fs.length > 0) {
              setFieldRows(fs.map(f => ({
                name: f.fieldName,
                type: f.dataType,
                length: f.dataLength ?? null,
                primaryKey: f.primaryKey,
              })));
            } else if (table.columns && table.columns.length > 0) {
              // 兜底：字段元数据缺失时退回表目录缓存列（无长度/主键）
              setFieldRows(table.columns.map(c => ({ name: c.name, type: c.type, length: null as number | null, primaryKey: false })));
            }
            loadedRef.current = true;
          } catch (e) {
            setError(e instanceof Error ? e.message : String(e));
          } finally {
            setLoading(false);
          }
        })();
      }
      return next;
    });
  }, [table.resourceId, table.columns]);

  const isLoading = expanded && loading;
  const expandedCls = expanded
    ? 'max-h-[800px] opacity-100 translate-y-0'
    : 'max-h-0 opacity-0 -translate-y-1 pointer-events-none';
  const transition = 'max-height 0.34s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.25s ease, transform 0.3s cubic-bezier(0.16, 1, 0.3, 1)';

  return (
    <div className={`border ${styles.cardBorder} rounded-xl overflow-hidden ${styles.appBg}/50 transition-shadow hover:ring-1`}>
      {/* 表头行：表名 + 右侧展开/收起图标 */}
      <div
        className={`${styles.sidebarBg}/70 px-4 py-2 flex items-center justify-between gap-2 border-b ${styles.cardBorder}`}
      >
        <div className="flex items-center gap-2 text-xs flex-1 min-w-0">
          {onToggle && (
            <input
              type="checkbox"
              className="accent-indigo-500 shrink-0"
              checked={Boolean(selected)}
              onChange={onToggle}
              title={t('dw.ingest.selectTables') || '选择采集'}
            />
          )}
          <LucideIcon name="Table" size={13} className={styles.accentText} />
          <span className={`font-bold font-mono ${styles.cardText} truncate`}>{table.name}</span>
          {table.resourceId && (
            <span className="text-[9px] font-mono" style={{ color: styles.cardTextMuted }} title={table.resourceId}>
              {table.resourceId.slice(0, 12)}…
            </span>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <span className={`text-[10px] ${styles.cardTextMuted} ${styles.cardBg} border ${styles.cardBorder} px-2 py-0.5 rounded-full font-mono`}>
            {t('dw.physicalRows')} {table.rowCount != null && table.rowCount > 0 ? table.rowCount.toLocaleString() : t('dw.conn.rowsUnknown')}
            {table.rowCount != null && table.rowCount > 0 ? ' ' + t('dw.rowsUnit') : ''}
          </span>
          <button
            type="button"
            onClick={toggle}
            aria-expanded={expanded}
            aria-label={expanded ? t('dw.expandRow.collapse') : t('dw.expandRow.expand')}
            title={expanded ? t('dw.expandRow.collapse') : t('dw.expandRow.expand')}
            className={`p-1 rounded flex items-center justify-center transition-transform ${
              expanded ? 'rotate-180' : ''
            } ${styles.cardTextMuted} hover:${styles.accentText}`}
          >
            <LucideIcon name="ChevronDown" size={14} />
          </button>
        </div>
      </div>

      {/* 展开区域：全量列网格（懒加载） */}
      <div
        className={`overflow-hidden transition-all ${expandedCls}`}
        style={{ transition }}
        aria-hidden={!expanded}
      >
        <div className={`p-3 ${styles.cardBg} space-y-2`}>
          {isLoading ? (
            <div className={`flex items-center justify-center py-4 text-xs ${styles.cardTextMuted}`}>
              <LucideIcon name="RefreshCw" size={13} className="animate-spin mr-2" />
              {t('dw.loading') || 'Loading...'}
            </div>
          ) : error ? (
            <div className={`p-3 rounded border text-xs ${styles.dangerText}`} style={{ borderColor: styles.dangerText }}>
              {error}
            </div>
          ) : fieldRows.length === 0 ? (
            <div className={`p-3 rounded border text-center text-xs ${styles.cardTextMuted} ${styles.cardBorder}`}>
              {t('db.preview.empty') || 'No columns'}
            </div>
          ) : (
            <div className={`border rounded-lg overflow-hidden ${styles.cardBorder}`}>
              <table className="w-full text-xs" style={{ background: styles.cardBg }}>
                <thead>
                  <tr className={`border-b ${styles.cardBorder}`} style={{ background: styles.sidebarBg }}>
                    <th className="px-3 py-2 text-left font-bold text-[10px] uppercase tracking-wider" style={{ color: styles.cardTextMuted }}>#</th>
                    <th className="px-3 py-2 text-left font-bold text-[10px] uppercase tracking-wider" style={{ color: styles.cardTextMuted }}>{t('db.col.field') || '字段名称'}</th>
                    <th className="px-3 py-2 text-left font-bold text-[10px] uppercase tracking-wider" style={{ color: styles.cardTextMuted }}>{t('db.col.type') || '类型'}</th>
                    <th className="px-3 py-2 text-left font-bold text-[10px] uppercase tracking-wider" style={{ color: styles.cardTextMuted }}>{t('db.col.length') || '长度'}</th>
                    <th className="px-3 py-2 text-left font-bold text-[10px] uppercase tracking-wider w-10" style={{ color: styles.cardTextMuted }}>{t('db.col.primaryKey') || '主键'}</th>
                  </tr>
                </thead>
                <tbody>
                  {fieldRows.map((row, i) => (
                    <tr key={row.name + i} className={`border-b last:border-0 ${styles.cardBorder}`} style={{ background: styles.cardBg }}>
                      <td className="px-3 py-1.5 font-mono text-[10px]" style={{ color: styles.cardTextMuted }}>{i + 1}</td>
                      <td className="px-3 py-1.5 font-mono" style={{ color: styles.cardText }}>{row.name}</td>
                      <td className="px-3 py-1.5 font-mono text-[10px]" style={{ color: styles.cardTextMuted }}>{row.type}</td>
                      <td className="px-3 py-1.5 font-mono text-[10px]" style={{ color: styles.cardTextMuted }}>{row.length != null ? row.length : '-'}</td>
                      <td className="px-3 py-1.5">
                        {row.primaryKey
                          ? <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${styles.successBg} ${styles.successText}`}>{t('db.primaryYes') || 'PK'}</span>
                          : <span className="text-[10px] font-mono" style={{ color: styles.cardTextMuted }}>—</span>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className={`text-[10px] ${styles.cardTextMuted} font-mono pt-1`}>
            {t('dw.expandRow.colCount').replace('{n}', String(fieldRows.length))}
          </div>
        </div>
      </div>
    </div>
  );
}
