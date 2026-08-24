/**
 * DataEngineConfigPanelGroupNav — 左侧分组导航子组件
 * 从 DataEngineConfigPanel 拆分而来，逻辑不变
 * @license Apache-2.0
 */
import React from 'react';
import type { ConfigGroup } from './DataEngineConfigPanel';

interface Props {
  groups: ConfigGroup[];
  activeGroup: string;
  onSelect: (id: string) => void;
  styles: { cardBorder: string; cardTextMuted: string; cardBg: string };
}

const DataEngineConfigPanelGroupNav: React.FC<Props> = ({
  groups,
  activeGroup,
  onSelect,
  styles,
}) => {
  return (
    <div className={`w-44 border-r ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 overflow-y-auto`}>
      {groups.map(g => (
        <button
          key={g.id}
          onClick={() => onSelect(g.id)}
          className={`flex items-center gap-2 px-3 py-2.5 text-xs font-medium transition-all text-left border-l-2 ${
            activeGroup === g.id
              ? 'bg-blue-50 text-blue-700 border-l-blue-600'
              : `${styles.cardTextMuted} hover:bg-slate-100 border-l-transparent`
          }`}
        >
          <span className={activeGroup === g.id ? 'text-blue-600' : 'text-slate-400'}>
            {g.icon}
          </span>
          <span className="flex-1">{g.label}</span>
          {g.modified && (
            <span className="text-amber-500 text-[10px] font-bold" title="有未保存的修改">●</span>
          )}
        </button>
      ))}
    </div>
  );
};

export default DataEngineConfigPanelGroupNav;
