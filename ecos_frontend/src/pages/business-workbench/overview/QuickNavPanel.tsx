/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ObjectType, OntologyDomain } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { getDomainColorClasses } from './domainColorClasses';

interface QuickNavPanelProps {
  domains: OntologyDomain[];
  objectTypes: ObjectType[];
  onQuickNavigate: (category: any, id: string) => void;
}

export default function QuickNavPanel({
  domains,
  objectTypes,
  onQuickNavigate
}: QuickNavPanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs space-y-3`}>
      <h3 className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1.5`}>
        <LucideIcon name="Compass" size={14} className={styles.cardTextMuted} />
        {t('ow.qnav.title')}
      </h3>

      <div className="space-y-4 max-h-[260px] overflow-y-auto pr-1">
        {/* 1. Show Domains */}
        {domains.map(d => {
          const domObjs = objectTypes.filter(ot => ot.domainId === d.id);
          if (domObjs.length === 0) return null;
          const classes = getDomainColorClasses(d.color);

          return (
            <div key={d.id} className="space-y-1.5">
              <div className={`flex items-center gap-1.5 text-[10px] font-extrabold ${styles.cardTextMuted} tracking-wider uppercase border-b ${styles.divider} pb-1`}>
                <span className={`w-2 h-2 rounded-full ${classes.dot}`} />
                <span>{d.displayName}</span>
              </div>
              <div className="space-y-1 pl-1">
                {domObjs.map(ot => (
                  <div
                    key={ot.id}
                    onClick={() => onQuickNavigate('object', ot.id)}
                    className={`flex items-center justify-between p-1.5 rounded-lg border ${styles.divider} hover:border-blue-300 hover:bg-blue-50/20 cursor-pointer transition-all group`}
                  >
                    <div className="flex items-center gap-2 truncate">
                      <span className={`p-0.5 rounded border ${ot.color}`}>
                        <LucideIcon name={ot.icon} size={11} />
                      </span>
                      <span className={`text-xs font-medium ${styles.cardText}`}>{ot.displayName}</span>
                    </div>
                    <LucideIcon name="ChevronRight" size={11} className={`${styles.muted} group-hover:translate-x-0.5 transition-transform`} />
                  </div>
                ))}
              </div>
            </div>
          );
        })}

        {/* 2. Show Unassigned if any */}
        {objectTypes.some(ot => !ot.domainId) && (
          <div className="space-y-1.5">
            <div className={`flex items-center gap-1.5 text-[10px] font-extrabold ${styles.cardTextMuted} tracking-wider uppercase border-b ${styles.divider} pb-1`}>
              <span className="w-2 h-2 rounded-full bg-[var(--card,#94A3B8)]" />
              <span>{t('ow.qnav.unassigned')}</span>
            </div>
            <div className="space-y-1 pl-1">
              {objectTypes.filter(ot => !ot.domainId).map(ot => (
                <div
                  key={ot.id}
                  onClick={() => onQuickNavigate('object', ot.id)}
                  className={`flex items-center justify-between p-1.5 rounded-lg border ${styles.divider} hover:border-blue-300 hover:bg-blue-50/20 cursor-pointer transition-all group`}
                >
                  <div className="flex items-center gap-2 truncate">
                    <span className={`p-0.5 rounded border ${ot.color}`}>
                      <LucideIcon name={ot.icon} size={11} />
                    </span>
                    <span className={`text-xs font-medium ${styles.cardText}`}>{ot.displayName}</span>
                  </div>
                  <LucideIcon name="ChevronRight" size={11} className={`${styles.muted} group-hover:translate-x-0.5 transition-transform`} />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
