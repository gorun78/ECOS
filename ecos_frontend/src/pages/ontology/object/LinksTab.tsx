/**
 * LinksTab — 关联链接 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { ChevronRight, GitMerge } from 'lucide-react';
import type { LinksTabProps } from './types';

export default function LinksTab({
  objectType,
  relatedLinks,
  onNavigateToLink,
}: LinksTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="space-y-4">
      <div className={`text-xs ${styles.cardTextMuted}`}>
        {t('ow.tab.linksDesc').replace('{name}', objectType.displayName)}
      </div>
      {relatedLinks.length === 0 ? (
        <div className={`text-center py-8 border border-dashed ${styles.cardBorder} rounded-lg ${styles.muted} text-xs`}>
          {t('ow.empty.noLinksForObject')}
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4">
          {relatedLinks.map(link => {
            const isSource = link.sourceObjectType === objectType.id;
            return (
              <div
                key={link.id}
                onClick={() => onNavigateToLink(link.id)}
                className={`p-4 border ${styles.cardBorder} rounded-xl hover:border-blue-400 hover:shadow-xs transition-all cursor-pointer ${styles.cardBg} group flex items-start justify-between`}
              >
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <span className={`p-1 rounded-md ${styles.sidebarBg} ${styles.sidebarText} group-hover:bg-blue-50 group-hover:text-blue-600 transition-colors`}>
                      <GitMerge size={14} />
                    </span>
                    <div className={`font-semibold text-xs ${styles.cardText} group-hover:text-blue-600`}>
                      {link.displayName}
                    </div>
                    <span className={`text-[10px] font-mono ${styles.muted} ${styles.appBg} px-1 rounded`}>
                      {link.cardinality}
                    </span>
                  </div>
                  <p className={`text-[10px] ${styles.cardTextMuted}`}>{link.description}</p>
                  <div className={`flex items-center gap-1.5 text-[10px] ${styles.sidebarText} pt-1`}>
                    <span className={isSource ? `font-semibold ${styles.accentText}` : ''}>
                      {isSource ? t('ow.label.source') : t('ow.label.source') + '(' + link.sourceObjectType + ')'}
                    </span>
                    <span>→</span>
                    <span className={!isSource ? `font-semibold ${styles.accentText}` : ''}>
                      {!isSource ? t('ow.label.target') : t('ow.label.target') + '(' + link.targetObjectType + ')'}
                    </span>
                  </div>
                </div>
                <div className={`flex items-center gap-1 text-[10px] text-blue-500 group-hover:translate-x-0.5 transition-transform`}>
                  <span>{t('ow.btn.jumpToConfig')}</span>
                  <ChevronRight size={12} />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
