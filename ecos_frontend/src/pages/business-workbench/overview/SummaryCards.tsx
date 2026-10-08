/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ObjectType, LinkType, InterfaceType, SharedProperty, OntologyDomain } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';

interface SummaryCardsProps {
  objectTypes: ObjectType[];
  linkTypes: LinkType[];
  interfaces: InterfaceType[];
  sharedProperties: SharedProperty[];
  domains: OntologyDomain[];
}

export default function SummaryCards({
  objectTypes,
  linkTypes,
  interfaces,
  sharedProperties,
  domains
}: SummaryCardsProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="grid grid-cols-4 gap-4">
      {/* 1. Object Types Card */}
      <div className={`${styles.cardBg} border ${styles.appBorder} rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow`}>
        <div className="space-y-1">
          <span className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>{t('ow.summary.objEntity')}</span>
          <div className={`text-xl font-bold ${styles.cardText} font-mono`}>{objectTypes.length}</div>
          <div className={`text-[10px] ${styles.cardTextMuted} flex items-center gap-1`}>
            <span>{t('ow.summary.objAssigned', { count: objectTypes.filter(ot => ot.domainId).length })}</span>
            <span className={styles.muted}>|</span>
            <span className="text-amber-600 font-medium">{t('ow.summary.objUnassigned', { count: objectTypes.filter(ot => !ot.domainId).length })}</span>
          </div>
        </div>
        <span className="p-2.5 rounded-xl bg-blue-50 text-blue-600 border border-blue-100">
          <LucideIcon name="Box" size={18} />
        </span>
      </div>

      {/* 2. Link Types Card */}
      <div className={`${styles.cardBg} border ${styles.appBorder} rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow`}>
        <div className="space-y-1">
          <span className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>{t('ow.summary.linkTitle')}</span>
          <div className={`text-xl font-bold ${styles.cardText} font-mono`}>{linkTypes.length}</div>
          <div className={`text-[10px] ${styles.cardTextMuted}`}>
            {t('ow.summary.linkSub')}
          </div>
        </div>
        <span className="p-2.5 rounded-xl bg-emerald-50 text-emerald-600 border border-emerald-100">
          <LucideIcon name="GitMerge" size={18} />
        </span>
      </div>

      {/* 3. Action Types Card */}
      <div className={`${styles.cardBg} border ${styles.appBorder} rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow`}>
        <div className="space-y-1">
          <span className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>{t('ow.summary.domainTitle')}</span>
          <div className={`text-xl font-bold ${styles.cardText} font-mono`}>{domains.length}</div>
          <div className={`text-[10px] ${styles.cardTextMuted}`}>
            {t('ow.summary.domainSub')}
          </div>
        </div>
        <span className="p-2.5 rounded-xl bg-purple-50 text-purple-600 border border-purple-100">
          <LucideIcon name="Layers" size={18} />
        </span>
      </div>

      {/* 4. Combined specs Card */}
      <div className={`${styles.cardBg} border ${styles.appBorder} rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow`}>
        <div className="space-y-1">
          <span className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>{t('ow.summary.combinedTitle')}</span>
          <div className={`text-xl font-bold ${styles.cardText} font-mono`}>{interfaces.length + sharedProperties.length}</div>
          <div className={`text-[10px] ${styles.cardTextMuted}`}>
            {t('ow.summary.combinedMeta', { i: interfaces.length, s: sharedProperties.length })}
          </div>
        </div>
        <span className="p-2.5 rounded-xl bg-amber-50 text-amber-600 border border-amber-100">
          <LucideIcon name="Tag" size={18} />
        </span>
      </div>
    </div>
  );
}
