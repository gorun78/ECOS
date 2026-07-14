/**
 * SQL Query Console — Query Toolbar
 * 数据源选择下拉 + 执行按钮 + 保存模板按钮 + 历史按钮
 * @license Apache-2.0
 */

import React from 'react';
import { useTheme } from '../../components/ThemeContext';
import * as Icons from 'lucide-react';
import type { DataSource } from './types';

interface QueryToolbarProps {
  dataSources: DataSource[];
  selectedDsId: string | null;
  onSelectDs: (dsId: string) => void;
  onExecute: () => void;
  onSaveTemplate: () => void;
  onToggleHistory: () => void;
  isExecuting: boolean;
  isHistoryOpen: boolean;
}

const Icon = ({ name, size = 16, className = '' }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

export default function QueryToolbar({
  dataSources,
  selectedDsId,
  onSelectDs,
  onExecute,
  onSaveTemplate,
  onToggleHistory,
  isExecuting,
  isHistoryOpen,
}: QueryToolbarProps) {
  const { styles } = useTheme();

  return (
    <div className={`h-10 px-3 flex items-center gap-2 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
      {/* 数据源选择下拉 */}
      <div className="flex items-center gap-1.5">
        <Icon name="Database" size={13} className="text-cyan-400 shrink-0" />
        <select
          value={selectedDsId || ''}
          onChange={e => onSelectDs(e.target.value)}
          className={`text-xs py-1 px-2 rounded ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder} outline-none focus:border-blue-500/50 transition-colors cursor-pointer min-w-[160px]`}
        >
          <option value="" disabled>
            选择数据源...
          </option>
          {dataSources.map(ds => (
            <option key={ds.datasourceId} value={ds.datasourceId}>
              {ds.datasourceName}
            </option>
          ))}
        </select>
      </div>

      {/* 分隔线 */}
      <div className="w-px h-5 bg-slate-700" />

      {/* 执行按钮 */}
      <button
        onClick={onExecute}
        disabled={!selectedDsId || isExecuting}
        className={`
          flex items-center gap-1.5 px-3 py-1 rounded text-xs font-medium transition-all
          ${selectedDsId && !isExecuting
            ? 'bg-emerald-600 hover:bg-emerald-500 text-white cursor-pointer active:scale-95'
            : 'bg-slate-700 text-slate-500 cursor-not-allowed'
          }
        `}
        title="执行 SQL (Ctrl+Enter)"
      >
        {isExecuting ? (
          <Icon name="Loader2" size={13} className="animate-spin" />
        ) : (
          <Icon name="Play" size={13} className="fill-current" />
        )}
        <span>执行</span>
      </button>

      {/* 保存模板按钮 */}
      <button
        onClick={onSaveTemplate}
        className="flex items-center gap-1.5 px-3 py-1 rounded text-xs font-medium bg-slate-700 hover:bg-slate-600 text-slate-200 cursor-pointer transition-all active:scale-95"
        title="保存为模板"
      >
        <Icon name="Save" size={13} />
        <span>保存模板</span>
      </button>

      {/* 右侧区域：历史按钮 + 导出等 */}
      <div className="flex-1" />

      <button
        onClick={onToggleHistory}
        className={`
          flex items-center gap-1.5 px-2.5 py-1 rounded text-xs font-medium transition-all cursor-pointer
          ${isHistoryOpen
            ? 'bg-blue-600/20 text-blue-400 border border-blue-500/30'
            : 'bg-slate-700 hover:bg-slate-600 text-slate-200'
          }
        `}
        title="查询历史"
      >
        <Icon name="History" size={13} />
        <span>历史</span>
      </button>
    </div>
  );
}
