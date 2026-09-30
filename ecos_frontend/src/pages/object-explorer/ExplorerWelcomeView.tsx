/**
 * ExplorerWelcomeView — 未选择对象类型时的欢迎/快捷入口视图
 * Extracted verbatim from ObjectExplorerView.tsx (pure structural refactor)
 * @license Apache-2.0
 */
import React from 'react';
import { Compass } from 'lucide-react';
import DynamicIcon from '../../components/ontology/DynamicIcon';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import type { DataRecord, ObjectType } from '../../types/ontology';

interface Props {
  objectTypes: ObjectType[];
  activeObjectTypeId: string | null;
  instanceData: DataRecord[];
  dataTotal: number;
  relatedInstanceCache: Record<string, DataRecord[]>;
  setActiveObjectTypeId: (id: string) => void;
}

export default function ExplorerWelcomeView({
  objectTypes,
  activeObjectTypeId,
  instanceData,
  dataTotal,
  relatedInstanceCache,
  setActiveObjectTypeId
}: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`flex flex-col items-center justify-center h-full p-8 text-center ${styles.appBg}`}>
      <div className={`w-16 h-16 rounded-2xl ${styles.sidebarActiveBg} border ${styles.accentBorder} flex items-center justify-center ${styles.accentText} mb-4 animate-pulse`}>
        <Compass size={32} />
      </div>
      <h2 className={`text-sm font-semibold ${styles.cardText}`}>{t('ow.explore.welcomeTitle')}</h2>
      <p className={`text-xs ${styles.cardTextMuted} max-w-lg leading-relaxed mt-2`}>
        {t('ow.explore.welcomeDesc1')}
        {t('ow.explore.welcomeDesc2')}
      </p>
      
      {/* Grid of quick choices */}
      <div className="grid grid-cols-2 gap-4 w-full max-w-xl mt-8">
        {objectTypes.map(ot => {
          const count = (ot.id === activeObjectTypeId && instanceData.length > 0) ? dataTotal : (relatedInstanceCache[ot.id]?.length ?? 0);
          return (
            <div
              key={ot.id}
              onClick={() => setActiveObjectTypeId(ot.id)}
              className={`${styles.cardBg} border ${styles.cardBorder} hover:border-blue-500 p-4 rounded-xl shadow-3xs hover:shadow-xs transition-all cursor-pointer flex items-start gap-3 group text-left`}
            >
              <span className={`p-2.5 rounded-lg border ${ot.color} shrink-0`}>
                <DynamicIcon name={ot.icon} size={16} />
              </span>
              <div className="space-y-0.5">
                <div className={`text-xs font-semibold ${styles.cardText} group-hover:text-blue-600`}>{ot.displayName}</div>
                <p className={`text-[10px] ${styles.muted} line-clamp-1`}>{ot.description}</p>
                <div className={`text-[10px] font-mono ${styles.cardTextMuted} mt-1`}>
                  <strong>{count}</strong> {t('ow.explore.runningInstances')}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
