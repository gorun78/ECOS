/**
 * SQL Query Console — History Panel
 * @license Apache-2.0
 */
import React, { useState, useEffect } from 'react';
import * as Icons from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { apiFetchData } from '../../api';
import type { QueryHistoryItem } from './types';

interface HistoryPanelProps {
  show: boolean;
  onLoadSql: (sql: string) => void;
  onClose: () => void;
}

type IconComponent = React.ComponentType<{ size?: number; className?: string }>;

/** 后端历史记录原始行（snake_case / camelCase 双形态，仅本文件消费） */
interface RawQueryHistoryRow {
  id?: string | number;
  dataSourceId?: string | number;
  datasource_id?: string | number;
  sqlContent?: string;
  sql_content?: string;
  sql?: string;
  status?: string;
  rowsReturned?: number | string;
  rows_returned?: number | string;
  elapsedMs?: number | string;
  elapsed_ms?: number | string;
  startedAt?: string;
  started_at?: string;
  errorMessage?: string;
  error_msg?: string;
}

type HistoryResponse = RawQueryHistoryRow[] | { data?: RawQueryHistoryRow[] } | null;

const Icon = ({ name, size = 14 }: { name: string; size?: number }) => {
  const iconMap = Icons as unknown as Record<string, IconComponent>;
  const Comp = iconMap[name] || iconMap.HelpCircle;
  return <Comp size={size} />;
};

export default function HistoryPanel({ show, onLoadSql, onClose }: HistoryPanelProps) {
  const { styles } = useTheme();
  const [items, setItems] = useState<QueryHistoryItem[]>([]);
  const [loading, setLoading] = useState(false);

  const load = () => {
    setLoading(true);
    apiFetchData<HistoryResponse>('/api/v1/engine/data/query/history?page=1&pageSize=50')
      .then((d) => {
        const list = Array.isArray(d) ? d : d?.data;
        const raw: RawQueryHistoryRow[] = Array.isArray(list) ? list : [];
        const normalized: QueryHistoryItem[] = raw.map((r) => ({
          id: String(r.id ?? ''),
          datasourceId: String(r.dataSourceId ?? r.datasource_id ?? ''),
          sqlContent: String(r.sqlContent ?? r.sql_content ?? r.sql ?? ''),
          status: String(r.status ?? 'UNKNOWN'),
          rowsReturned: Number(r.rowsReturned ?? r.rows_returned ?? 0),
          elapsedMs: Number(r.elapsedMs ?? r.elapsed_ms ?? 0),
          startedAt: String(r.startedAt ?? r.started_at ?? ''),
          errorMessage: r.errorMessage ?? r.error_msg ?? null,
        }));
        setItems(normalized);
      })
      .catch((e: unknown) => {
        setItems([]);
        console.error('[HistoryPanel] 加载历史失败:', e);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => { if (show) load(); }, [show]);

  if (!show) return null;

  return (
    <div className={`w-64 border-l ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 overflow-hidden`}>
      <div className={`px-2.5 py-2 border-b ${styles.cardBorder} flex items-center justify-between`}>
        <span className={`text-[10px] font-bold uppercase ${styles.cardTextMuted} flex items-center gap-1.5`}>
          <Icon name="History" size={13} />查询历史
        </span>
        <button type="button" onClick={onClose} className={`${styles.cardTextMuted} hover:opacity-70`}>
          <Icon name="X" size={13} />
        </button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {loading ? (
          <div className={`flex items-center justify-center py-8 ${styles.cardTextMuted} text-[11px]`}>
            <Icon name="Loader2" /> 加载中...
          </div>
        ) : items.length === 0 ? (
          <div className={`text-center py-8 ${styles.cardTextMuted} text-[10px]`}>暂无历史</div>
        ) : (
          items.map(item => (
            <div key={item.id} className={`px-2.5 py-2 border-b ${styles.cardBorder} hover:bg-white/5 cursor-pointer`}
              onClick={() => onLoadSql(item.sqlContent)}>
              <div className={`flex items-center gap-1.5 text-[10px] ${styles.cardTextMuted}`}>
                <span className={`w-1.5 h-1.5 rounded-full ${item.status === 'SUCCEEDED' ? 'bg-emerald-400' : 'bg-rose-400'}`} />
                <span>{item.rowsReturned} 行</span>
                <span>{item.elapsedMs}ms</span>
                <span className="flex-1 text-right">{formatTime(item.startedAt)}</span>
              </div>
              <div className={`text-[10px] ${styles.cardTextMuted} mt-0.5 line-clamp-2 font-mono`}>
                {item.sqlContent.substring(0, 100)}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function formatTime(iso: string): string {
  try { return new Date(iso).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }); } catch { return ''; }
}
