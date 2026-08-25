/**
 * DataEngineConfigPanelGroupNav — 左侧分组导航子组件
 * 从 DataEngineConfigPanel 拆分而来。
 * PMO-3J-T6: 分组名走 i18n (labelKey → t)；颜色走 useTheme styles。
 * @license Apache-2.0
 */
import React from 'react';
import type { ConfigGroup } from './DataEngineConfigPanel';
import type { ThemeStyles } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

interface Props {
  groups: ConfigGroup[];
  activeGroup: string;
  onSelect: (id: string) => void;
  styles: ThemeStyles;
}

const DataEngineConfigPanelGroupNav: React.FC<Props> = ({
  groups,
  activeGroup,
  onSelect,
  styles,
}) => {
  const { t } = useLanguage();

  return (
    <div className={`w-44 border-r ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 overflow-y-auto`}>
      {groups.map(g => (
        <button
          key={g.id}
          onClick={() => onSelect(g.id)}
          className={`flex items-center gap-2 px-3 py-2.5 text-xs font-medium transition-all text-left border-l-2 ${
            activeGroup === g.id
              ? `${styles.infoBg} ${styles.accentText} ${styles.accentBorder}`
              : `${styles.cardTextMuted} hover:${styles.sidebarBg} border-l-transparent`
          }`}
        >
          <span className={activeGroup === g.id ? `${styles.accentText}` : `${styles.cardTextMuted}`}>
            {g.icon}
          </span>
          <span className="flex-1">{t(g.labelKey)}</span>
          {g.modified && (
            <span className={`${styles.warningText} text-[10px] font-bold`} title={t('dw.cfg.nav.modifiedTitle')}>●</span>
          )}
        </button>
      ))}
    </div>
  );
};

export default DataEngineConfigPanelGroupNav;
