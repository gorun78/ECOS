/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */
import React, { useState, useMemo } from 'react';
import LucideIcon from '../../components/LucideIcon';
import type { ThemeStyles } from '../../components/ThemeContext';
import type { BusinessScenario } from '../project-workbench/types';

export interface ScenarioListProps {
  scenarios: BusinessScenario[];
  selectedScenarioId: string;
  onSelect: (id: string) => void;
  onCreateNew: () => void;
  styles: ThemeStyles;
  locale: string;
  tl: (zh: string, en: string) => string;
}

export default function ScenarioList({
  scenarios,
  selectedScenarioId,
  onSelect,
  onCreateNew,
  styles,
  locale,
  tl,
}: ScenarioListProps) {
  const [searchQuery, setSearchQuery] = useState('');

  const filteredScenarios = useMemo(() => {
    if (!searchQuery.trim()) return scenarios;
    const q = searchQuery.toLowerCase();
    return scenarios.filter(
      (s) =>
        s.name.toLowerCase().includes(q) ||
        s.description.toLowerCase().includes(q) ||
        s.department.toLowerCase().includes(q) ||
        s.priority.toLowerCase().includes(q)
    );
  }, [scenarios, searchQuery]);

  return (
    <div className={`w-80 border-r ${styles.cardBorder} flex flex-col ${styles.cardBg} select-none shrink-0`}>
      {/* Header */}
      <div className={`p-3 border-b ${styles.cardBorder} flex flex-col gap-2 shrink-0 ${styles.cardBg}`}>
        <div className="flex items-center justify-between">
          <span className={`text-xs font-bold ${styles.cardTextMuted} flex items-center gap-1.5`}>
            <LucideIcon name="ListCollapse" size={12} className="text-indigo-500" />
            {tl('业务场景库', 'Scenarios')}
          </span>
          <button
            onClick={onCreateNew}
            className="text-[10px] bg-indigo-600 hover:bg-indigo-700 text-white font-bold px-2 py-1 rounded flex items-center gap-1 transition-all cursor-pointer"
          >
            <LucideIcon name="Plus" size={10} />
            {tl('创建新场景', 'New Scenario')}
          </button>
        </div>

        {/* Search input */}
        <div className="relative">
          <LucideIcon
            name="Search"
            size={12}
            className={`absolute left-2 top-1/2 -translate-y-1/2 ${styles.cardTextMuted}`}
          />
          <input
            type="text"
            placeholder={tl('搜索场景名称、部门...', 'Search scenarios...')}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className={`w-full pl-7 pr-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
          />
        </div>
      </div>

      {/* Scenario Cards */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {filteredScenarios.length === 0 && (
          <div className={`text-center py-8 text-xs ${styles.cardTextMuted}`}>
            {searchQuery.trim()
              ? tl('未找到匹配的场景', 'No matching scenarios')
              : tl('暂无场景', 'No scenarios yet')}
          </div>
        )}
        {filteredScenarios.map((scen) => {
          const isSelected = scen.id === selectedScenarioId;
          const isCritical = scen.priority === 'CRITICAL';
          const isHigh = scen.priority === 'HIGH';
          const isDraft = scen.status === 'DRAFT';

          return (
            <div
              key={scen.id}
              onClick={() => onSelect(scen.id)}
              className={`p-3 rounded-lg border transition-all cursor-pointer ${
                isSelected
                  ? 'bg-slate-800 border-indigo-500 shadow-md shadow-indigo-950/30'
                  : `${styles.cardBg} ${styles.cardBorder} hover:border-slate-700 hover:bg-slate-800/50`
              }`}
            >
              <div className="flex items-start justify-between gap-1.5">
                <span className="text-xs font-extrabold tracking-tight text-white line-clamp-1 flex-1">
                  {scen.name}
                </span>
                <span
                  className={`text-[8px] font-bold px-1.5 py-0.5 rounded uppercase shrink-0 ${
                    isCritical
                      ? 'bg-red-950 text-red-400 border border-red-900/50'
                      : isHigh
                        ? 'bg-amber-950 text-amber-400 border border-amber-900/50'
                        : `bg-slate-800 ${styles.cardTextMuted} border border-slate-700`
                  }`}
                >
                  {scen.priority}
                </span>
              </div>

              <p className={`text-[11px] ${styles.cardTextMuted} line-clamp-2 mt-1.5 leading-relaxed`}>
                {scen.description}
              </p>

              <div
                className={`flex items-center justify-between mt-3 pt-2.5 border-t ${styles.cardBorder} text-[10px] ${styles.cardTextMuted} font-mono`}
              >
                <span className="flex items-center gap-1">
                  <LucideIcon name="Users" size={10} className={styles.cardTextMuted} />
                  {scen.department.slice(0, 8)}
                </span>
                <span
                  className={`flex items-center gap-1 font-bold ${
                    isDraft ? styles.cardTextMuted : 'text-emerald-400'
                  }`}
                >
                  <span
                    className={`w-1.5 h-1.5 rounded-full ${
                      isDraft ? 'bg-slate-600' : 'bg-emerald-500 animate-pulse'
                    }`}
                  />
                  {scen.status}
                </span>
              </div>
            </div>
          );
        })}
      </div>

      {/* Footer hint */}
      <div
        className={`p-3 ${styles.cardBg} border-t ${styles.cardBorder} text-[10px] ${styles.cardTextMuted} space-y-1 font-sans`}
      >
        <p className={`flex items-center gap-1 font-semibold ${styles.cardTextMuted}`}>
          <LucideIcon name="Info" size={11} className="text-indigo-400" />
          {tl('如何起作用？', 'How it works?')}
        </p>
        <p className="leading-relaxed">
          {tl(
            '管理者在此定义高维业务场景，绑定不同层级的系统底座（数据、实体、知识与安全规则），最终形成高合规的企业级智能流闭环。',
            'Define high-dimensional business scenarios, bind system layers (data, entities, knowledge, security rules), forming a compliant enterprise intelligence loop.'
          )}
        </p>
      </div>
    </div>
  );
}
