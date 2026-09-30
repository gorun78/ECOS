/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ObjectType, LinkType, OntologyDomain } from '../../../types/ontology';
import OntologyGraph from '../OntologyGraph';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { getDomainColorClasses } from './domainColorClasses';

interface GraphPanelProps {
  objectTypes: ObjectType[];
  domains: OntologyDomain[];
  selectedDomainFilter: string | null;
  onSelectDomainFilter: (id: string | null) => void;
  displayedObjects: ObjectType[];
  displayedLinks: LinkType[];
  onSelectNode: (nodeId: string) => void;
  onSelectEdge: (edgeId: string) => void;
}

export default function GraphPanel({
  objectTypes,
  domains,
  selectedDomainFilter,
  onSelectDomainFilter,
  displayedObjects,
  displayedLinks,
  onSelectNode,
  onSelectEdge
}: GraphPanelProps) {
  const { styles } = useTheme();

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs space-y-4`}>
      <div className={`flex flex-col md:flex-row md:items-center justify-between gap-3 border-b ${styles.divider} pb-3`}>
        <div className="space-y-0.5">
          <h3 className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1.5`}>
            <LucideIcon name="Network" size={15} className="text-blue-600" />
            关系拓扑图谱 (Ontology ER Diagram)
          </h3>
          <p className={`text-[11px] ${styles.cardTextMuted}`}>显示当前实体关联网络。可在上方切换不同业务域以进行视窗隔离和动态聚焦。</p>
        </div>

        {/* Dynamic Filter Controls */}
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <span className={`${styles.cardTextMuted} font-bold uppercase text-[9px] tracking-wider`}>视图按域过滤:</span>
          <button
            onClick={() => onSelectDomainFilter(null)}
            className={`px-2.5 py-1 rounded-full transition-all border font-medium cursor-pointer text-[11px] ${
              !selectedDomainFilter
                ? 'bg-[var(--card,#0F172A)] text-white border-[var(--card,#0F172A)] shadow-xs'
                : `${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder} hover:bg-blue-50/20`
            }`}
          >
            全局全景 ({objectTypes.length})
          </button>
          {domains.map(d => {
            const isSelected = selectedDomainFilter === d.id;
            const count = objectTypes.filter(ot => ot.domainId === d.id).length;
            const classes = getDomainColorClasses(d.color);
            return (
              <button
                key={d.id}
                onClick={() => onSelectDomainFilter(d.id)}
                className={`px-2.5 py-1 rounded-full transition-all border font-medium cursor-pointer flex items-center gap-1.5 text-[11px] ${
                  isSelected
                    ? `${classes.activeBg} text-white border-transparent shadow-xs`
                    : `${styles.cardBg} ${styles.cardText} ${styles.cardBorder} ${classes.hoverBg}`
                }`}
              >
                <span className={`w-1.5 h-1.5 rounded-full ${isSelected ? 'bg-white' : classes.dot}`} />
                <span>{d.displayName.split(' (')[0]}</span>
                <span className={`text-[9px] px-1 py-0.2 rounded-full font-mono ${isSelected ? 'bg-white/20' : `${styles.appBg} ${styles.cardTextMuted}`}`}>
                  {count}
                </span>
              </button>
            );
          })}
          {objectTypes.some(ot => !ot.domainId) && (
            <button
              onClick={() => onSelectDomainFilter('unassigned')}
              className={`px-2.5 py-1 rounded-full transition-all border font-medium cursor-pointer text-[11px] ${
                selectedDomainFilter === 'unassigned'
                  ? 'bg-[var(--card,#475569)] text-white border-[var(--card,#475569)] shadow-xs'
                  : `${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder} hover:bg-blue-50/20`
              }`}
            >
              未归域 ({objectTypes.filter(ot => !ot.domainId).length})
            </button>
          )}
        </div>
      </div>

      {/* Embedded SVG Graph Canvas with filtered nodes */}
      <div className={`relative border ${styles.cardBorder} rounded-lg overflow-hidden ${styles.appBg}`}>
        <OntologyGraph
          objectTypes={displayedObjects}
          linkTypes={displayedLinks}
          onSelectNode={onSelectNode}
          onSelectEdge={onSelectEdge}
        />
      </div>
    </div>
  );
}
