/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ObjectType, LinkType, InterfaceType, SharedProperty, OntologyDomain } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';

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

  return (
    <div className="grid grid-cols-4 gap-4">
      {/* 1. Object Types Card */}
      <div className={`${styles.cardBg} border ${styles.appBorder} rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow`}>
        <div className="space-y-1">
          <span className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>业务对象实体</span>
          <div className={`text-xl font-bold ${styles.cardText} font-mono`}>{objectTypes.length}</div>
          <div className={`text-[10px] ${styles.cardTextMuted} flex items-center gap-1`}>
            <span>{objectTypes.filter(ot => ot.domainId).length} 个已归域</span>
            <span className={styles.muted}>|</span>
            <span className="text-amber-600 font-medium">{objectTypes.filter(ot => !ot.domainId).length} 个未归类</span>
          </div>
        </div>
        <span className="p-2.5 rounded-xl bg-blue-50 text-blue-600 border border-blue-100">
          <LucideIcon name="Box" size={18} />
        </span>
      </div>

      {/* 2. Link Types Card */}
      <div className={`${styles.cardBg} border ${styles.appBorder} rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow`}>
        <div className="space-y-1">
          <span className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>关系链接数量</span>
          <div className={`text-xl font-bold ${styles.cardText} font-mono`}>{linkTypes.length}</div>
          <div className={`text-[10px] ${styles.cardTextMuted}`}>
            包含域内关联与跨域多维关联
          </div>
        </div>
        <span className="p-2.5 rounded-xl bg-emerald-50 text-emerald-600 border border-emerald-100">
          <LucideIcon name="GitMerge" size={18} />
        </span>
      </div>

      {/* 3. Action Types Card */}
      <div className={`${styles.cardBg} border ${styles.appBorder} rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow`}>
        <div className="space-y-1">
          <span className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>系统业务域</span>
          <div className={`text-xl font-bold ${styles.cardText} font-mono`}>{domains.length}</div>
          <div className={`text-[10px] ${styles.cardTextMuted}`}>
            支持按域进行对象隔离与维护
          </div>
        </div>
        <span className="p-2.5 rounded-xl bg-purple-50 text-purple-600 border border-purple-100">
          <LucideIcon name="Layers" size={18} />
        </span>
      </div>

      {/* 4. Combined specs Card */}
      <div className={`${styles.cardBg} border ${styles.appBorder} rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow`}>
        <div className="space-y-1">
          <span className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>接口与属性指标</span>
          <div className={`text-xl font-bold ${styles.cardText} font-mono`}>{interfaces.length + sharedProperties.length}</div>
          <div className={`text-[10px] ${styles.cardTextMuted}`}>
            {interfaces.length} 契约规范 · {sharedProperties.length} 共享属性
          </div>
        </div>
        <span className="p-2.5 rounded-xl bg-amber-50 text-amber-600 border border-amber-100">
          <LucideIcon name="Tag" size={18} />
        </span>
      </div>
    </div>
  );
}
