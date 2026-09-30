/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ObjectType, OntologyDomain } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { getDomainColorClasses } from './domainColorClasses';

interface DomainCardsGridProps {
  domains: OntologyDomain[];
  objectTypes: ObjectType[];
  handleStartEdit: (domain: OntologyDomain) => void;
  handleDeleteDomain: (domainId: string) => void;
  onQuickNavigate: (category: any, id: string) => void;
}

export default function DomainCardsGrid({
  domains,
  objectTypes,
  handleStartEdit,
  handleDeleteDomain,
  onQuickNavigate
}: DomainCardsGridProps) {
  const { styles } = useTheme();

  return (
    /* Domains Cards Grid */
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      {domains.map(d => {
        const classes = getDomainColorClasses(d.color);
        const domainObjects = objectTypes.filter(ot => ot.domainId === d.id);

        return (
          <div
            key={d.id}
            className={`${styles.cardBg} border ${styles.cardBorder} hover:border-blue-300 rounded-xl p-4 flex flex-col justify-between hover:shadow-2xs transition-all relative overflow-hidden ${classes.leftBorder}`}
          >
            <div className="space-y-2">
              <div className="flex justify-between items-start">
                <div>
                  <h4 className={`text-xs font-semibold ${styles.cardText}`}>{d.displayName}</h4>
                  <span className={`text-[10px] ${styles.cardTextMuted} font-mono lowercase tracking-tight`}>域识别ID: {d.id}</span>
                </div>

                <div className="flex items-center gap-1.5">
                  <button
                    onClick={() => handleStartEdit(d)}
                    className={`p-1 ${styles.cardTextMuted} opacity-80 hover:opacity-100 rounded ${styles.sidebarHoverBg}`}
                    title="编辑此业务域"
                  >
                    <LucideIcon name="Edit" size={12} />
                  </button>
                  <button
                    onClick={() => handleDeleteDomain(d.id)}
                    className={`p-1 ${styles.cardTextMuted} hover:text-red-600 rounded hover:bg-red-50`}
                    title="删除此业务域"
                  >
                    <LucideIcon name="Trash2" size={12} />
                  </button>
                </div>
              </div>

              <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed min-h-[36px] line-clamp-2`}>
                {d.description || '暂无业务描述。'}
              </p>
            </div>

            {/* Associated object lists */}
            <div className={`border-t ${styles.divider} pt-3 mt-3`}>
              <div className={`flex items-center justify-between text-[10px] ${styles.cardTextMuted} font-semibold uppercase mb-1.5`}>
                <span>已关联对象 ({domainObjects.length})</span>
                <span className="font-mono">{d.id} domain</span>
              </div>
              {domainObjects.length > 0 ? (
                <div className="flex flex-wrap gap-1.5">
                  {domainObjects.map(ot => (
                    <div
                      key={ot.id}
                      onClick={() => onQuickNavigate('object', ot.id)}
                      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full border ${styles.cardBorder} ${styles.appBg} ${styles.cardTextMuted} text-[10px] font-medium hover:border-blue-400 hover:bg-blue-50/20 cursor-pointer transition-colors`}
                    >
                      <LucideIcon name={ot.icon} size={10} className={styles.cardTextMuted} />
                      <span>{ot.displayName.split(' (')[0]}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className={`text-[10px] ${styles.cardTextMuted} italic`}>暂无绑定的业务实体</div>
              )}
            </div>
          </div>
        );
      })}

      {/* Unassigned Quick Stats Card */}
      {objectTypes.some(ot => !ot.domainId) && (
        <div className={`${styles.appBg} border ${styles.sidebarBorder} rounded-xl p-4 flex flex-col justify-between border-dashed`}>
          <div className="space-y-1.5">
            <div className="flex justify-between items-center">
              <h4 className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1`}>
                <LucideIcon name="AlertCircle" size={13} className="text-amber-500" />
                <span>未分类实体池</span>
              </h4>
              <span className="text-[10px] px-1.5 py-0.2 rounded-full bg-amber-100 text-amber-800 font-semibold font-mono">
                {objectTypes.filter(ot => !ot.domainId).length} 对象
              </span>
            </div>
            <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed`}>
              当前声明的某些本体实体还未绑定到具体的业务划分域中。未分类对象在全景关系拓扑中可以继续存在，但在业务域治理上未形成职责分级。
            </p>
          </div>

          <div className={`border-t ${styles.divider} pt-3 mt-3 flex flex-wrap gap-1.5`}>
            {objectTypes.filter(ot => !ot.domainId).map(ot => (
              <div
                key={ot.id}
                onClick={() => onQuickNavigate('object', ot.id)}
                className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full border ${styles.cardBorder} ${styles.cardBg} ${styles.cardTextMuted} text-[10px] hover:border-amber-400 hover:bg-amber-50/20 cursor-pointer transition-colors`}
              >
                <LucideIcon name={ot.icon} size={10} />
                <span>{ot.displayName.split(' (')[0]}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
