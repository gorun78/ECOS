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

const Icon = ({ name, size = 14 }: { name: string; size?: number }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} />;
};

export default function HistoryPanel({ show, onLoadSql, onClose }: HistoryPanelProps) {
  const { styles } = useTheme();
  const [items, setItems] = useState<QueryHistoryItem[]>([]);
  const [loading, setLoading] = useState(false);

  const load = () => {
    setLoading(true);
    apiFetchData<any>('/api/v1/engine/data/query/history?page=1&pageSize=50')
      .then((d: any) => setItems(Array.isArray(d?.data) ? d.data : (Array.isArray(d) ? d : [])))
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => { if (show) load(); }, [show]);

  if (!show) return null;

  return (
    <div className={`w-64 border-l ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 overflow-hidden`}>
      <div className={`px-2.5 py-2 border-b ${styles.cardBorder} flex items-center justify-between`}>
        <span className="text-[10px] font-bold uppercase text-slate-400 flex items-center gap-1.5">
          <Icon name="History" size={13} />查询历史
        </span>
        <button onClick={onClose} className="text-slate-500 hover:text-slate-300">
          <Icon name="X" size={13} />
        </button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {loading ? (
          <div className="flex items-center justify-center py-8 text-slate-500 text-[11px]">
            <Icon name="Loader2" /> 加载中...
          </div>
        ) : items.length === 0 ? (
          <div className="text-center py-8 text-slate-500 text-[10px]">暂无历史</div>
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
