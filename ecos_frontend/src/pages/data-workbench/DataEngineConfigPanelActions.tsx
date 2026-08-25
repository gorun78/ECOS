/**
 * DataEngineConfigPanelActions — 底部操作栏子组件
 * 从 DataEngineConfigPanel 拆分而来。
 * PMO-3J-T6: 文案走 i18n (t)。
 * @license Apache-2.0
 */
import React from 'react';
import { RotateCcw, Save, RefreshCw } from 'lucide-react';
import type { ConfigGroup } from './DataEngineConfigPanel';
import type { ThemeStyles } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

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
  const { t } = useLanguage();
  const modifiedCount = groups.filter(g => g.modified).length;

  return (
    <div className={`flex items-center justify-between px-4 py-2.5 border-t ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
      <div className={`text-[10px] ${styles.cardTextMuted}`}>
        {modifiedCount > 0 && (
          <span className={`${styles.warningText}`}>
            {t('dw.cfg.actions.modifiedGroups', { count: modifiedCount })}
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
          {t('dw.cfg.actions.restoreDefaults')}
        </button>
        <button
          onClick={onSaveAll}
          disabled={saving || refreshing}
          className={`flex items-center gap-1.5 px-4 py-1.5 text-xs font-bold ${styles.cardText} ${styles.accentBg} hover:${styles.accentBg} rounded-md transition-colors disabled:opacity-50 shadow-sm`}
        >
          {saving ? (
            <>
              <RefreshCw size={13} className="animate-spin" />
              {t('dw.cfg.actions.saving')}
            </>
          ) : (
            <>
              <Save size={13} />
              {t('dw.cfg.actions.saveAll')}
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
              {t('dw.cfg.actions.refreshing')}
            </>
          ) : (
            <>
              <RefreshCw size={13} />
              {t('dw.cfg.actions.refreshCache')}
            </>
          )}
        </button>
      </div>
    </div>
  );
};

export default DataEngineConfigPanelActions;
