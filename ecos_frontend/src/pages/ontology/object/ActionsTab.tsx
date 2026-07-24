/**
 * ActionsTab — 应用操作 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { ChevronRight, Zap } from 'lucide-react';
import type { ActionsTabProps } from './types';

export default function ActionsTab({
  objectType,
  relatedActions,
  onNavigateToAction,
}: ActionsTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="space-y-4">
      <div className={`text-xs ${styles.cardTextMuted}`}>
        {t('ow.tab.actionsDesc').replace('{name}', objectType.displayName)}
      </div>
      {relatedActions.length === 0 ? (
        <div className={`text-center py-8 border border-dashed ${styles.cardBorder} rounded-lg ${styles.muted} text-xs`}>
          {t('ow.empty.noActionsForObject')}
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4">
          {relatedActions.map(action => (
            <div
              key={action.id}
              onClick={() => onNavigateToAction(action.id)}
              className={`p-4 border ${styles.cardBorder} rounded-xl hover:border-blue-400 hover:shadow-xs transition-all cursor-pointer ${styles.cardBg} group flex items-start justify-between`}
            >
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <span className="p-1.5 rounded-full bg-amber-50 text-amber-600 group-hover:bg-amber-100 transition-colors">
                    <Zap size={13} className="fill-amber-500" />
                  </span>
                  <div className={`font-semibold text-xs ${styles.cardText} group-hover:text-blue-600`}>
                    {action.displayName}
                  </div>
                </div>
                <p className={`text-[10px] ${styles.cardTextMuted}`}>{action.description}</p>
                <div className={`flex items-center gap-2 font-mono text-[9px] ${styles.muted} ${styles.appBg} p-1.5 rounded border border-slate-100`}>
                  <div>
                    <strong>{t('ow.label.params')}</strong> {action.parameters.length} | <strong>{t('ow.label.sideEffects')}</strong> {action.rules.length} {t('ow.label.countUnit')}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-1 text-[10px] text-blue-500 group-hover:translate-x-0.5 transition-transform">
                <span>{t('ow.btn.jumpToConfig')}</span>
                <ChevronRight size={12} />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
