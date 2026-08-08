/**
 * PropertyEditor — 右侧属性编辑器面板
 *
 * 显示选中实体的详情：基本信息、关联关系、属性列表占位、删除操作。
 * 从 DomainDesignerView.tsx 的 PropertyEditorPanel 提取，纯结构重构，JSX 不变。
 *
 * 修正：添加 useTheme() 调用（预存 bug，原函数引用了未在作用域内的 styles）。
 *
 * @license Apache-2.0
 */

import React from 'react';
import {
  Eye, Edit3, GitBranch, Plus, List, SlidersHorizontal, Trash2,
} from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import type { Entity, Relationship } from '../../types/workbench';
import { ENTITY_TYPE_CONFIG, getEntityTypeLabel } from './ontologyHelpers';

// ── Props ──────────────────────────────────────────────────────

export interface PropertyEditorProps {
  entity: Entity | null;
  relationships: Relationship[];
  allEntities: Entity[];
  onCreateRelation: (sourceId: string) => void;
  saving: boolean;
}

// ── 组件 ──────────────────────────────────────────────────────

export default function PropertyEditor({
  entity,
  relationships,
  allEntities,
  onCreateRelation,
  saving,
}: PropertyEditorProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  if (!entity) {
    return (
      <div className="flex-1 flex items-center justify-center text-slate-500">
        <div className="text-center p-6">
          <Eye size={32} className="mx-auto mb-2 opacity-20" />
          <p className="text-xs">{t("ontology.designer.selectEntityDetail")}</p>
          <p className="text-[10px] mt-1 opacity-60">
            {t("ontology.designer.clickCanvasOrTree")}
          </p>
        </div>
      </div>
    );
  }

  const config = ENTITY_TYPE_CONFIG[entity.entityType] || ENTITY_TYPE_CONFIG.default;

  return (
    <div className="flex-1 flex flex-col">
      {/* 标题 */}
      <div className={`px-4 py-3 border-b ${styles.cardBorder}`}>
        <div className="flex items-center gap-2 mb-2">
          {config.icon}
          <h3 className="text-sm font-semibold text-white">
            {entity.name || entity.code}
          </h3>
        </div>
        <p className="text-[10px] font-mono text-slate-500">{entity.code}</p>
        <div className="flex items-center gap-2 mt-2">
          <span className="text-[10px] px-2 py-0.5 rounded bg-slate-700/50 text-slate-300">
            {getEntityTypeLabel(entity.entityType, t)}
          </span>
          {entity.entityType && (
            <span className="text-[10px] px-2 py-0.5 rounded bg-slate-700/50 text-slate-300">
              {entity.entityType}
            </span>
          )}
        </div>
      </div>

      {/* 基本信息 */}
      <div className={`px-4 py-3 border-b ${styles.cardBorder}`}>
        <h4 className="text-[11px] font-semibold text-slate-300 mb-2 flex items-center gap-1.5">
          <Edit3 size={11} className="text-slate-500" />
          {t("ontology.designer.basicInfo")}
        </h4>
        <div className="space-y-1.5 text-[11px]">
          <div className="flex justify-between">
            <span className="text-slate-500">{t("ontology.designer.code")}</span>
            <span className="text-white font-mono">{entity.code}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">{t("ontology.designer.name")}</span>
            <span className="text-white">{entity.name}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">{t("ontology.designer.type")}</span>
            <span className="text-white">
              {getEntityTypeLabel(entity.entityType, t)} ({entity.entityType})
            </span>
          </div>
          {entity.description && (
            <div className="pt-1">
              <span className="text-slate-500">{t("ontology.designer.description")}</span>
              <p className="text-white mt-0.5 text-[10px] leading-relaxed">
                {entity.description}
              </p>
            </div>
          )}
          {entity.createdAt && (
            <div className="flex justify-between">
              <span className="text-slate-500">{t("ontology.designer.createdAt")}</span>
              <span className="text-white text-[10px]">{entity.createdAt}</span>
            </div>
          )}
        </div>
      </div>

      {/* 关联关系 */}
      <div className={`px-4 py-3 border-b ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-2">
          <h4 className="text-[11px] font-semibold text-slate-300 flex items-center gap-1.5">
            <GitBranch size={11} className="text-slate-500" />
            {t('ontology.designer.relationshipsCount', { count: relationships.length })}
          </h4>
          {allEntities.length > 1 && (
            <button
              onClick={() => onCreateRelation(entity.id)}
              className="text-[10px] text-indigo-400 hover:text-indigo-300 flex items-center gap-1"
            >
              <Plus size={10} /> {t("ontology.designer.add")}
            </button>
          )}
        </div>

        {relationships.length === 0 ? (
          <p className="text-[10px] text-slate-600 text-center py-3">{t("ontology.designer.noRelationships")}</p>
        ) : (
          <div className="space-y-1">
            {relationships.map((rel) => {
              const isSource = rel.sourceEntityId === entity.id;
              const otherId = isSource ? rel.targetEntityId : rel.sourceEntityId;
              const otherEntity = allEntities.find((e) => e.id === otherId);

              return (
                <div
                  key={rel.id}
                  className={`flex items-center gap-2 px-2 py-1.5 rounded hover:${styles.cardBg}/[0.03] text-[10px]`}
                >
                  <span className={isSource ? 'text-emerald-400' : 'text-blue-400'}>
                    {isSource ? '→' : '←'}
                  </span>
                  <span className="text-slate-300 flex-1">{rel.code}</span>
                  <span className="text-slate-500 font-mono">
                    {otherEntity?.code || otherId?.slice(0, 8)}
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 属性列表 (Sprint 3 占位) */}
      <div className={`px-4 py-3 border-b ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-2">
          <h4 className="text-[11px] font-semibold text-slate-300 flex items-center gap-1.5">
            <List size={11} className="text-slate-500" />
            {t("ontology.designer.propertyList")}
          </h4>
          <span className="text-[9px] text-slate-600">{t("ontology.designer.sprint3Impl")}</span>
        </div>
        <div className="text-center py-4">
          <SlidersHorizontal size={20} className="mx-auto mb-1 opacity-20 text-slate-500" />
          <p className="text-[10px] text-slate-600">{t("ontology.designer.propertyEditHere")}</p>
        </div>
      </div>

      {/* 底部操作 */}
      <div className={`px-4 py-3 mt-auto border-t ${styles.cardBorder}`}>
        <div className="flex gap-2">
          <button
            className="flex-1 py-1.5 rounded-lg text-[10px] font-medium
              bg-red-500/10 text-red-400 border border-red-500/20
              hover:bg-red-500/20 transition flex items-center justify-center gap-1"
          >
            <Trash2 size={10} />
            {t("ontology.designer.deleteEntity")}
          </button>
        </div>
      </div>
    </div>
  );
}
