/**
 * DataEngineConfigPanelActions — 底部操作栏子组件
 * 从 DataEngineConfigPanel 拆分而来，逻辑不变
 * @license Apache-2.0
 */
import React from 'react';
import { RotateCcw, Save, RefreshCw } from 'lucide-react';
import type { ConfigGroup } from './DataEngineConfigPanel';
import type { ThemeStyles } from '../../components/ThemeContext';

interface Props {
  groups: ConfigGroup[];
  saving: boolean;
  refreshing: boolean;
  onRestoreDefaults: () => void;
  onSaveAll: () => void;
  onRefreshCache: () => void;
  styles: ThemeStyles;
}

const DataEngineConfigPanelActions: React.FC<Props> = ({
  groups,
  saving,
  refreshing,
  onRestoreDefaults,
  onSaveAll,
  onRefreshCache,
  styles,
}) => {
  const modifiedCount = groups.filter(g => g.modified).length;

  return (
    <div className={`flex items-center justify-between px-4 py-2.5 border-t ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
      <div className={`text-[10px] ${styles.cardTextMuted}`}>
        {modifiedCount > 0 && (
          <span className={`${styles.warningText}`}>
            {modifiedCount} 个分组有未保存的修改
          </span>
        )}
      </div>
      <div className="flex items-center gap-2">
        <button
          onClick={onRestoreDefaults}
          disabled={saving || refreshing}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium ${styles.cardTextMuted} hover:${styles.sidebarBg} rounded-md transition-colors disabled:opacity-50`}
        >
          <RotateCcw size={13} />
          恢复默认
        </button>
        <button
          onClick={onSaveAll}
          disabled={saving || refreshing}
          className={`flex items-center gap-1.5 px-4 py-1.5 text-xs font-bold ${styles.cardText} ${styles.accentBg} hover:${styles.accentBg} rounded-md transition-colors disabled:opacity-50 shadow-sm`}
        >
          {saving ? (
            <>
              <RefreshCw size={13} className="animate-spin" />
              保存中...
            </>
          ) : (
            <>
              <Save size={13} />
              全部保存
            </>
          )}
        </button>
        <button
          onClick={onRefreshCache}
          disabled={saving || refreshing}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium ${styles.cardTextMuted} hover:${styles.sidebarBg} rounded-md transition-colors disabled:opacity-50`}
        >
          {refreshing ? (
            <>
              <RefreshCw size={13} className="animate-spin" />
              刷新中...
            </>
          ) : (
            <>
              <RefreshCw size={13} />
              刷新缓存
            </>
          )}
        </button>
      </div>
    </div>
  );
};

export default DataEngineConfigPanelActions;
