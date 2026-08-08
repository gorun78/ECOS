/**
 * EntityTreePanel — 左侧实体树面板
 *
 * 包含：域标题 + 实体搜索 + 实体树列表 + 创建实体按钮
 * 从 DomainDesignerView.tsx 提取，纯结构重构，JSX 不变。
 *
 * @license Apache-2.0
 */

import React from 'react';
import {
  Building2, Box, Search, Plus, Loader2, ChevronLeft,
} from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import { useWorkbenchStore } from '../../stores/useWorkbenchStore';
import type { Entity, Relationship } from '../../types/workbench';
import { ENTITY_TYPE_CONFIG, getEntityTypeLabel } from './ontologyHelpers';

// ── Props ──────────────────────────────────────────────────────

export interface EntityTreePanelProps {
  entities: Entity[];
  entitiesLoading: boolean;
  selectedEntityId: string | null;
  entitySearch: string;
  onSearchChange: (value: string) => void;
  onSelectEntity: (id: string) => void;
  onBack: () => void;
  domainCode: string;
  relationships: Relationship[];
  onCreateEntity: () => void;
  leftPanelCollapsed: boolean;
}

// ── 过滤实体列表 ──────────────────────────────────────────────

function filterEntities(entities: Entity[], query: string): Entity[] {
  if (!query.trim()) return entities;
  const q = query.toLowerCase();
  return entities.filter(
    (e) =>
      e.code.toLowerCase().includes(q) ||
      e.name.toLowerCase().includes(q) ||
      (e.description && e.description.toLowerCase().includes(q)),
  );
}

// ── 组件 ──────────────────────────────────────────────────────

export default function EntityTreePanel({
  entities,
  entitiesLoading,
  selectedEntityId,
  entitySearch,
  onSearchChange,
  onSelectEntity,
  onBack,
  domainCode,
  relationships,
  onCreateEntity,
  leftPanelCollapsed,
}: EntityTreePanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const filteredEntities = React.useMemo(
    () => filterEntities(entities, entitySearch),
    [entities, entitySearch],
  );

  if (leftPanelCollapsed) return null;

  return (
    <div className={`w-[280px] min-w-[240px] border-r ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0`}>
      {/* 域标题 */}
      <div className={`px-4 py-3 border-b ${styles.cardBorder}`}>
        <button
          onClick={onBack}
          className="flex items-center gap-1.5 text-[10px] text-slate-500 hover:text-slate-400 mb-2.5 transition"
        >
          <ChevronLeft size={12} />
          {t("ontology.designer.backToDomainList")}
        </button>
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg bg-indigo-500/10">
            <Building2 size={14} className="text-indigo-400" />
          </div>
          <div className="min-w-0">
            <h3 className="text-sm font-semibold text-white truncate">
              {domainCode}
            </h3>
            <p className="text-[10px] text-slate-500">
              {t('ontology.designer.entityRelationshipCount', { entities: entities.length, relationships: relationships.length })}
            </p>
          </div>
        </div>
      </div>

      {/* 实体搜索 */}
      <div className={`px-4 py-2 border-b ${styles.cardBorder}`}>
        <div className="relative">
          <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            value={entitySearch}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder={t("ontology.designer.searchEntity")}
            className={`w-full ${styles.appBg} border ${styles.cardBorder} rounded-lg pl-7 pr-3 py-1.5
              text-xs text-white placeholder:text-slate-600
              focus:outline-none focus:border-indigo-500/40 transition`}
          />
        </div>
      </div>

      {/* 实体树列表 */}
      <div className="flex-1 overflow-y-auto">
        {entitiesLoading ? (
          <div className="flex items-center justify-center py-8 text-slate-500">
            <Loader2 size={16} className="animate-spin" />
          </div>
        ) : filteredEntities.length === 0 ? (
          <div className="flex items-center justify-center py-12 text-slate-500">
            <div className="text-center">
              <Box size={24} className="mx-auto mb-2 opacity-30" />
              <p className="text-xs">
                {entitySearch ? t('ontology.designer.noMatchEntity') : t('ontology.designer.noEntity')}
              </p>
            </div>
          </div>
        ) : (
          <div>
            {filteredEntities.map((entity) => {
              const isSelected = entity.id === selectedEntityId;
              const config =
                ENTITY_TYPE_CONFIG[entity.entityType] || ENTITY_TYPE_CONFIG.default;

              return (
                <button
                  key={entity.id}
                  onClick={() => onSelectEntity(entity.id)}
                  className={`w-full text-left px-4 py-2.5 border-b ${styles.cardBorder}/50 transition flex items-center gap-2.5 ${
                    isSelected
                      ? 'bg-indigo-500/10 border-l-2 border-l-indigo-500'
                      : `hover:${styles.cardBg}/[0.03] border-l-2 border-l-transparent`
                  }`}
                >
                  {config.icon}
                  <div className="flex-1 min-w-0">
                    <div className="text-xs font-semibold text-white truncate flex items-center gap-1.5">
                      {entity.name || entity.code}
                      <span className="text-[9px] font-normal text-slate-500">
                        ({getEntityTypeLabel(entity.entityType, t)})
                      </span>
                    </div>
                    <div className="text-[10px] text-slate-500 font-mono truncate">
                      {entity.code}
                    </div>
                  </div>
                  {isSelected && (
                    <ChevronLeft size={12} className="text-indigo-400 rotate-180 shrink-0" />
                  )}
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* 底部：创建实体按钮 */}
      <div className={`px-4 py-2.5 border-t ${styles.cardBorder}`}>
        <button
          onClick={onCreateEntity}
          className="w-full flex items-center justify-center gap-1.5 py-2 rounded-lg
            text-xs font-medium text-indigo-400 hover:text-indigo-300
            border border-indigo-500/20 hover:border-indigo-500/40
            bg-indigo-500/5 hover:bg-indigo-500/10 transition"
        >
          <Plus size={13} />
          {t("ontology.designer.newEntity")}
        </button>
      </div>
    </div>
  );
}
