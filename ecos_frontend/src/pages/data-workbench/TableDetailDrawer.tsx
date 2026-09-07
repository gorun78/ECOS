/* 数据表字段详情抽屉 — 右侧滑入式
 * 点击数据表目录后异步加载字段明细 (via METADATA_PREVIEW endpoint)
 * 平滑展开/收起: CSS transform + opacity transition 350ms
 * 主题感知 (useTheme), i18n (db.* 现有 key + dw.tableDrawer.* 新增)
 * 2026-09-07
 */
import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { fetchPreview } from '../../api';
import type { DataPreview } from '../../api';
import type { TableInfo } from './types';

interface Props {
  table: TableInfo | null;      // null 时收起 (抽屉不渲染内容)
  connId: string;
  onClose: () => void;
}

interface FieldColumn {
  name: string;
  type: string;
  isNullable?: boolean;
}

export default function TableDetailDrawer({ table, connId, onClose }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [preview, setPreview] = useState<DataPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 切换表时清空旧数据
  const [lastTableKey, setLastTableKey] = useState<string | null>(null);
  const currentKey = table ? `${table.name}-${table.resourceId ?? ''}` : null;
  if (currentKey !== lastTableKey) {
    setLastTableKey(currentKey);
    if (table) {
      setLoading(true);
      setPreview(null);
      setError(null);
    }
  }

  // ESC 关闭
  useEffect(() => {
    if (!table) return;
    const h = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [table, onClose]);

  // 异步加载字段 (limit=1)
  useEffect(() => {
    if (!table || !table.resourceId) {
      if (table) setError('No resourceId - cannot load field details');
      setLoading(false);
      return;
    }
    let cancelled = false;
    const run = async () => {
      setLoading(true);
      setError(null);
      try {
        const p = await fetchPreview(table.resourceId!, 1);
        if (!cancelled) setPreview(p);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    run();
    return () => { cancelled = true; };
  }, [table?.name, table?.resourceId]);

  const open = !!table;
  const label = table?.name ?? '';
  const columns: FieldColumn[] = (preview?.columns ?? []).map(c => ({
    name: c.label || c.name,
    type: c.type,
  }));

  // 平滑展开/收起: 用 CSS transition
  const drawerStyle: React.CSSProperties = {
    transform: open ? 'translateX(0)' : 'translateX(100%)',
    opacity: open ? 1 : 0,
    transition: 'transform 0.35s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.3s ease',
    pointerEvents: open ? 'auto' : 'none',
  };

  // 通过 portal 渲染, 脱离表格 td 层级
  return createPortal(
    <div className="fixed inset-0 z-[80]">
      {/* 遮罩 */}
      <div
        onClick={onClose}
        className="absolute inset-0"
        style={{
          background: 'rgba(0,0,0,0.4)',
          opacity: open ? 1 : 0,
          transition: 'opacity 0.3s ease',
          pointerEvents: open ? 'auto' : 'none',
        }}
      />
      {/* 抽屉主体 */}
      <aside
        className="absolute inset-y-0 right-0 w-[560px] max-w-[92vw] shadow-2xl flex flex-col"
        style={{ background: styles.appBg, ...drawerStyle, borderLeft: `1px solid ${styles.cardBorder}` }}
        role="dialog"
        aria-label={label}
      >
        {/* 头部 */}
        <div className={`px-4 py-3 border-b flex items-center justify-between gap-3`} style={{ borderColor: styles.cardBorder, background: (styles.sidebarBg as string) + '60' }}>
          <div className="min-w-0 flex items-center gap-2">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={styles.accentText as string} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <rect x="3" y="3" width="18" height="18" rx="2" />
              <path d="M3 9h18M9 3v18" />
            </svg>
            <div className="min-w-0">
              <h3 className={`text-sm font-bold font-mono truncate ${styles.cardText}`} title={label}>{label}</h3>
              {table?.sourcePath && (
                <p className={`text-[10px] font-mono truncate ${styles.cardTextMuted}`}>{table.sourcePath}</p>
              )}
            </div>
          </div>
          <button onClick={onClose} className={`text-[10px] px-2 py-1 rounded border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.cardText} cursor-pointer transition-colors`}>
            {t('common.close') || '关闭'} (Esc)
          </button>
        </div>

        {/* 内容区 */}
        <div className="flex-1 overflow-y-auto p-4 space-y-5">
          {/* 元信息 */}
          <div className="grid grid-cols-3 gap-3">
            <MetaTile label={t('dw.rowsUnit') || '行数'} value={table?.rowCount != null && table.rowCount >= 0 ? table.rowCount.toLocaleString() : (t('dw.conn.rowsUnknown') || '未知')} />
            <MetaTile label={t('dw.colsUnit') || '列数'} value={String(columns.length || table?.fieldCount || 0)} />
            <MetaTile label={t('dw.conn.rowsUnknown')?.includes('未知') ? '状态' : '详情'} value={table?.description || '-'} multiline />
          </div>

          {/* 字段列表 */}
          <section>
            <h4 className={`text-xs font-semibold flex items-center gap-1.5 mb-2 ${styles.cardText}`}>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke={styles.accentText as string} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" /><path d="M2 12h20M12 2a15 15 0 0 1 0 20" />
              </svg>
              {t('db.fields.title') || '数据字段'} ({columns.length})
            </h4>

            {loading ? (
              <div className={`text-xs p-4 text-center rounded border ${styles.cardBorder}`} style={{ color: styles.cardTextMuted }}>
                <span className="inline-block animate-spin mr-2">↻</span> 加载中...
              </div>
            ) : error ? (
              <div className={`text-xs p-3 rounded border ${styles.dangerText} border`} style={{ borderColor: styles.dangerText, color: styles.dangerText }}>
                {error}
              </div>
            ) : columns.length === 0 ? (
              <div className={`text-xs p-4 text-center rounded border ${styles.cardBorder}`} style={{ color: styles.cardTextMuted }}>
                {t('db.preview.empty') || '暂无字段'}
              </div>
            ) : (
              <div className={`border rounded-lg overflow-hidden ${styles.cardBorder}`}>
                <table className="w-full text-xs" style={{ background: styles.cardBg }}>
                  <thead>
                    <tr className={`border-b ${styles.cardBorder}`} style={{ background: (styles.sidebarBg as string) + '40' }}>
                      <th className="px-3 py-2 text-left font-semibold w-8" style={{ color: styles.cardTextMuted }}>#</th>
                      <th className="px-3 py-2 text-left font-semibold" style={{ color: styles.cardTextMuted }}>{t('db.col.field') || '字段名'}</th>
                      <th className="px-3 py-2 text-left font-semibold" style={{ color: styles.cardTextMuted }}>{t('db.col.type') || '类型'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {columns.map((c, i) => (
                      <tr key={c.name + i} className={`border-b last:border-0 ${styles.cardBorder} hover:bg-white/2 transition-colors`} style={{ background: styles.cardBg }}>
                        <td className="px-3 py-1.5 font-mono text-[10px]" style={{ color: styles.cardTextMuted }}>{i + 1}</td>
                        <td className="px-3 py-1.5 font-mono" style={{ color: styles.cardText }}>{c.name}</td>
                        <td className="px-3 py-1.5 font-mono text-[10px]" style={{ color: styles.cardTextMuted }}>{c.type}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {/* 数据预览 (1 行抽样) */}
          {preview && preview.rows.length > 0 && (
            <section>
              <h4 className={`text-xs font-semibold mb-2 ${styles.cardText}`}>
                {t('db.preview.title').replace('{LIMIT}', '1') || '数据预览 (1 行)'}
              </h4>
              <div className={`border rounded-lg overflow-hidden ${styles.cardBorder} overflow-x-auto`}>
                <table className="w-full text-[10px] font-mono" style={{ background: styles.cardBg }}>
                  <thead>
                    <tr className={`border-b ${styles.cardBorder}`} style={{ background: (styles.sidebarBg as string) + '40' }}>
                      {preview.columns.map(c => (
                        <th key={c.name} className="px-2 py-1.5 text-left align-bottom" style={{ color: styles.cardTextMuted }}>
                          {c.label || c.name}
                          <div className="text-[9px] font-normal opacity-70">{c.type}</div>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {preview.rows.slice(0, 1).map((row, i) => (
                      <tr key={i} className={`border-b ${styles.cardBorder}`}>
                        {preview.columns.map(c => (
                          <td key={c.name} className="px-2 py-1.5 max-w-[180px] truncate" title={String((row as Record<string, unknown>)[c.name] ?? '')} style={{ color: styles.cardText }}>
                            {String((row as Record<string, unknown>)[c.name] ?? '-')}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </div>
      </aside>
    </div>,
    document.body,
  );
}

function MetaTile({ label, value, multiline }: { label: string; value: string | number; multiline?: boolean }) {
  const { styles } = useTheme();
  return (
    <div className={`px-3 py-2 rounded border ${styles.cardBorder}`} style={{ background: styles.cardBg }}>
      <div className="text-[10px] mb-1" style={{ color: styles.cardTextMuted }}>{label}</div>
      <div className={`text-xs font-semibold ${multiline ? 'line-clamp-1' : ''}`} style={{ color: styles.cardText }} title={String(value)}>
        {String(value)}
      </div>
    </div>
  );
}
