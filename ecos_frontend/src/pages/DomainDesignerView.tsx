/**
 * DomainDesignerView — Step 3 本体设计器页
 *
 * 三栏布局:
 *   左侧 (280px)  — 域选择器 + 实体树 + 搜索
 *   中间 (flex-1) — 可编辑画布 (DomainGraphCanvas, Sprint 3 实现 ReactFlow)
 *   右侧 (320px) — 属性编辑器面板 (PropertyEditorPanel, Sprint 3 实现)
 *
 * 路由参数: domainCode (从 URL hash 提取, 如 #/ontology_workbench/domains/sales)
 *
 * 数据流:
 *   1. 解析 domainCode → store.setCurrentDomain(code)
 *   2. store.fetchDomainData() → 加载实体/关系列表
 *   3. store.syncCanvasFromData() → 生成画布节点/边
 *
 * @license Apache-2.0
 */

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  ArrowLeft, Building2, Box, GitBranch, Search, Plus,
  Loader2, AlertCircle, Network, Database, Layers,
  Maximize2, Minimize2, PanelLeftClose, PanelLeftOpen,
  PanelRightClose, PanelRightOpen, ChevronLeft, Filter,
  Globe, List, Edit3, Trash2, SlidersHorizontal, Eye,
  Sparkles,
} from 'lucide-react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { useWorkbenchStore } from '../stores/useWorkbenchStore';
import type { Entity, Relationship } from '../types/workbench';
import CreateEntityModal from '../components/ontology-workbench/modals/CreateEntityModal';
import CreateRelationshipModal from '../components/ontology-workbench/modals/CreateRelationshipModal';
import AutoDiscoverPanel from './ontology/AutoDiscoverPanel';

// ── 辅助函数：从 URL hash 中提取 domainCode ──────────────────

/**
 * 从当前 URL hash 中提取 domainCode 参数。
 * 期望格式: #/ontology_workbench/domains/:code
 * 也支持查询参数: #/ontology_workbench?domain=:code
 */
function extractDomainCode(): string | null {
  const hash = window.location.hash.replace(/^#/, '');

  // 匹配路径格式 /ontology_workbench/domains/:code
  const pathMatch = hash.match(/\/ontology_workbench\/domains\/([^/?]+)/);
  if (pathMatch) return decodeURIComponent(pathMatch[1]);

  // 匹配查询参数格式 ?domain=:code
  const params = new URLSearchParams(hash.split('?')[1] || '');
  const domainParam = params.get('domain');
  if (domainParam) return decodeURIComponent(domainParam);

  return null;
}

// ── 实体类型图标 & 颜色映射 ──────────────────────────────────

const ENTITY_TYPE_CONFIG: Record<string, { icon: React.ReactNode; color: string }> = {
  MASTER: {
    icon: <Database size={12} className="text-amber-400" />,
    color: 'text-amber-400',
  },
  TRANSACTION: {
    icon: <List size={12} className="text-emerald-400" />,
    color: 'text-emerald-400',
  },
  EVENT: {
    icon: <Layers size={12} className="text-blue-400" />,
    color: 'text-blue-400',
  },
  REFERENCE: {
    icon: <Globe size={12} className="text-purple-400" />,
    color: 'text-purple-400',
  },
  default: {
    icon: <Box size={12} className="text-slate-400" />,
    color: 'text-slate-400',
  },
};

function getEntityTypeLabel(et: string, t: (key: string) => string): string {
  const map: Record<string, string> = {
    MASTER: t('ontology.designer.entityType.master'),
    TRANSACTION: t('ontology.designer.entityType.transaction'),
    EVENT: t('ontology.designer.entityType.event'),
    REFERENCE: t('ontology.designer.entityType.reference'),
  };
  return map[et] || et;
}

// ── 主组件 ──────────────────────────────────────────────────

export default function DomainDesignerView() {
  // 从 URL 提取 domainCode
  const domainCode = useMemo(() => extractDomainCode(), []);

  const { styles } = useTheme();
  const store = useWorkbenchStore();
  const { t } = useLanguage();
  const {
    entities,
    relationships,
    selectedEntityId,
    entitiesLoading,
    relationshipsLoading,
    savingEntity,
    error,
    leftPanelCollapsed,
    rightPanelCollapsed,
  } = store;

  // 本地 UI 状态
  const [showCreateEntity, setShowCreateEntity] = useState(false);
  const [showCreateRelation, setShowCreateRelation] = useState(false);
  const [createRelationSourceId, setCreateRelationSourceId] = useState<string>('');
  const [entitySearch, setEntitySearch] = useState('');
  const [showAutoDiscover, setShowAutoDiscover] = useState(false);

  // ── 初始化：设置域并加载数据 ──
  useEffect(() => {
    if (domainCode) {
      store.setCurrentDomain(domainCode);
      store.fetchDomainData();
    }
  }, [domainCode]);

  // ── 过滤实体列表 ──
  const filteredEntities = useMemo(() => {
    if (!entitySearch.trim()) return entities;
    const q = entitySearch.toLowerCase();
    return entities.filter(
      (e) =>
        e.code.toLowerCase().includes(q) ||
        e.name.toLowerCase().includes(q) ||
        (e.description && e.description.toLowerCase().includes(q))
    );
  }, [entities, entitySearch]);

  // ── 选中的实体 ──
  const selectedEntity = useMemo(
    () => entities.find((e) => e.id === selectedEntityId),
    [entities, selectedEntityId]
  );

  // ── 关联关系 ──
  const entityRelationships = useMemo(() => {
    if (!selectedEntityId) return [];
    return relationships.filter(
      (r) =>
        r.sourceEntityId === selectedEntityId ||
        r.targetEntityId === selectedEntityId
    );
  }, [relationships, selectedEntityId]);

  // ── 返回域列表 ──
  const handleBack = useCallback(() => {
    window.location.hash = '#/ontology_workbench/domains';
  }, []);

  // ── 打开创建关系弹窗 ──
  const handleOpenCreateRelation = useCallback((sourceId?: string) => {
    setCreateRelationSourceId(sourceId || selectedEntityId || '');
    setShowCreateRelation(true);
  }, [selectedEntityId]);

  // ── 加载态 ──
  const isLoading = entitiesLoading || relationshipsLoading;

  if (!domainCode) {
    return (
      <div className={`flex-1 flex items-center justify-center ${styles.appBg}`}>
        <div className="text-center">
          <AlertCircle size={40} className="text-red-400 mx-auto mb-3" />
          <p className="text-sm text-red-400">{t("ontology.designer.missingDomainParam")}</p>
          <p className="text-xs text-slate-500 mt-1">{t("ontology.designer.enterFromDomainList")}</p>
          <button
            onClick={handleBack}
            className="mt-4 px-4 py-1.5 rounded-lg text-xs bg-slate-700 text-slate-300 hover:bg-slate-600"
          >
            {t("ontology.designer.backToDomainList")}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={`flex-1 flex h-full overflow-hidden ${styles.appBg} font-sans`}>
      {/* ═══════ 左侧面板 (280px) — 域选择器 + 实体树 ═══════ */}
      {!leftPanelCollapsed && (
        <div className={`w-[280px] min-w-[240px] border-r ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0`}>
          {/* 域标题 */}
          <div className={`px-4 py-3 border-b ${styles.cardBorder}`}>
            <button
              onClick={handleBack}
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
                onChange={(e) => setEntitySearch(e.target.value)}
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
                      onClick={() => store.selectEntity(entity.id)}
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
              onClick={() => setShowCreateEntity(true)}
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
      )}

      {/* ═══════ 中间画布 (flex: 1) ═══════ */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* 工具栏 */}
        <div className={`flex items-center justify-between px-4 py-2 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
          <div className="flex items-center gap-1.5">
            {/* 左侧面板折叠按钮 */}
            <button
              onClick={() => store.toggleLeftPanel()}
              className={`p-1.5 rounded hover:${styles.cardBg}/5 text-slate-400 hover:text-slate-300 transition`}
              title={leftPanelCollapsed ? t('ontology.designer.expandEntityTree') : t('ontology.designer.collapseEntityTree')}
            >
              {leftPanelCollapsed ? <PanelLeftOpen size={14} /> : <PanelLeftClose size={14} />}
            </button>

            <div className={`w-px h-5 border-l ${styles.cardBorder} mx-1`} />

            <Network size={14} className="text-indigo-400" />
            <span className="text-xs font-medium text-slate-300">{t("ontology.designer.ontologyDesigner")}</span>
          </div>

          <div className="flex items-center gap-1.5">
            {/* 创建实体 */}
            <button
              onClick={() => setShowCreateEntity(true)}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[10px]
                bg-indigo-600/20 text-indigo-300 border border-indigo-500/30
                hover:bg-indigo-600/30 transition"
            >
              <Plus size={11} />
              {t("ontology.designer.entity")}
            </button>

            {/* 创建关系 */}
            <button
              disabled={entities.length < 2}
              onClick={() => handleOpenCreateRelation()}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[10px]
                bg-emerald-600/20 text-emerald-300 border border-emerald-500/30
                hover:bg-emerald-600/30
                disabled:opacity-30 disabled:cursor-not-allowed transition"
            >
              <GitBranch size={11} />
              {t("ontology.designer.relationship")}
            </button>

            {/* 自动发现 */}
            <button
              onClick={() => setShowAutoDiscover(true)}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[10px]
                bg-purple-600/20 text-purple-300 border border-purple-500/30
                hover:bg-purple-600/30 transition"
            >
              <Sparkles size={11} />
              {t("ontology.autoDiscover.title")}
            </button>

            <div className={`w-px h-5 border-l ${styles.cardBorder} mx-1`} />

            {/* 布局按钮（Sprint 3 接入） */}
            <button
              className={`p-1.5 rounded hover:${styles.cardBg}/5 text-slate-400 hover:text-slate-300 transition`}
              title={t("ontology.designer.autoLayout")}
            >
              <Maximize2 size={13} />
            </button>

            {/* 右侧面板折叠按钮 */}
            <button
              onClick={() => store.toggleRightPanel()}
              className={`p-1.5 rounded hover:${styles.cardBg}/5 text-slate-400 hover:text-slate-300 transition`}
              title={rightPanelCollapsed ? t('ontology.designer.expandPropertyPanel') : t('ontology.designer.collapsePropertyPanel')}
            >
              {rightPanelCollapsed ? <PanelRightOpen size={14} /> : <PanelRightClose size={14} />}
            </button>
          </div>
        </div>

        {/* 画布区域 */}
        <div className="flex-1 relative overflow-hidden">
          {isLoading ? (
            <div className={`absolute inset-0 flex items-center justify-center ${styles.appBg}`}>
              <div className="text-center">
                <Loader2 size={28} className="animate-spin text-indigo-400 mx-auto mb-3" />
                <p className="text-xs text-slate-500">{t("ontology.designer.loadingDomainData")}</p>
              </div>
            </div>
          ) : error ? (
            <div className={`absolute inset-0 flex items-center justify-center ${styles.appBg}`}>
              <div className="text-center p-8">
                <AlertCircle size={32} className="text-red-400 mx-auto mb-3" />
                <p className="text-xs text-red-400 mb-3">{error}</p>
                <button
                  onClick={() => store.fetchDomainData()}
                  className="px-4 py-1.5 rounded-lg text-xs bg-slate-700 text-slate-300 hover:bg-slate-600"
                >
                  {t("ontology.designer.retry")}
                </button>
              </div>
            </div>
          ) : (
            <DomainGraphCanvas
              entities={entities}
              relationships={relationships}
              selectedEntityId={selectedEntityId}
              onSelectEntity={(id) => store.selectEntity(id)}
              onCreateRelation={handleOpenCreateRelation}
            />
          )}
        </div>
      </div>

      {/* ═══════ 右侧面板 (320px) — 属性编辑器 ═══════ */}
      {!rightPanelCollapsed && (
        <div className={`w-[320px] min-w-[260px] border-l ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 overflow-y-auto`}>
          <PropertyEditorPanel
            entity={selectedEntity || null}
            relationships={entityRelationships}
            allEntities={entities}
            onCreateRelation={handleOpenCreateRelation}
            saving={savingEntity}
          />
        </div>
      )}

      {/* ═══════ 模态框 ═══════ */}

      {/* 创建实体弹窗 */}
      <CreateEntityModal
        open={showCreateEntity}
        onClose={() => setShowCreateEntity(false)}
        domainCode={domainCode}
      />

      {/* 创建关系弹窗 */}
      <CreateRelationshipModal
        open={showCreateRelation}
        onClose={() => setShowCreateRelation(false)}
        sourceEntityId={createRelationSourceId}
        entities={entities}
      />

      {/* 自动发现面板 */}
      {showAutoDiscover && (
        <AutoDiscoverPanel
          domainCode={domainCode}
          onClose={() => setShowAutoDiscover(false)}
        />
      )}
    </div>
  );
}

// ================================================================
// 子组件: DomainGraphCanvas (画布区域, Sprint 3 接入 ReactFlow)
// ================================================================

interface DomainGraphCanvasProps {
  entities: Entity[];
  relationships: Relationship[];
  selectedEntityId: string | null;
  onSelectEntity: (id: string | null) => void;
  onCreateRelation: (sourceId: string) => void;
}

function DomainGraphCanvas({
  entities,
  relationships,
  selectedEntityId,
  onSelectEntity,
  onCreateRelation,
}: DomainGraphCanvasProps) {
  // ── 简化网格布局 ──
  const cols = Math.max(Math.ceil(Math.sqrt(entities.length)), 2);
  const cellW = 160;
  const cellH = 100;
  const padding = 40;

  // 节点布局位置
  const nodePositions = useMemo(() => {
    const map = new Map<string, { x: number; y: number }>();
    entities.forEach((entity, i) => {
      const col = i % cols;
      const row = Math.floor(i / cols);
      map.set(entity.id, {
        x: padding + col * cellW + cellW / 2,
        y: padding + row * cellH + cellH / 2,
      });
    });
    return map;
  }, [entities, cols]);

  // SVG 画布尺寸
  const svgWidth = cols * cellW + padding * 2;
  const svgHeight = Math.ceil(entities.length / cols) * cellH + padding * 2;

  if (entities.length === 0) {
    return (
      <div className={`absolute inset-0 flex items-center justify-center ${styles.appBg}`}>
        <div className="text-center">
          <Network size={40} className="mx-auto mb-3 opacity-20 text-slate-500" />
          <p className="text-sm text-slate-500 mb-2">{t("ontology.designer.emptyDomain")}</p>
          <p className="text-[11px] text-slate-600">
            {t("ontology.designer.clickEntityToCreate")}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className={`absolute inset-0 overflow-auto ${styles.appBg}`}>
      <svg
        width={Math.max(svgWidth, 600)}
        height={Math.max(svgHeight, 400)}
        className="min-w-full min-h-full"
      >
        <defs>
          <marker
            id="arrow"
            viewBox="0 0 10 10"
            refX="16"
            refY="5"
            markerWidth="5"
            markerHeight="5"
            orient="auto-start-reverse"
          >
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#475569" />
          </marker>
        </defs>

        {/* 网格背景 */}
        <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
          <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#1E293B" strokeWidth="0.3" />
        </pattern>
        <rect width="100%" height="100%" fill="url(#grid)" />

        {/* 边/关系 */}
        {relationships.map((rel) => {
          const src = nodePositions.get(rel.sourceEntityId);
          const tgt = nodePositions.get(rel.targetEntityId);
          if (!src || !tgt) return null;

          const isSelected =
            selectedEntityId === rel.sourceEntityId ||
            selectedEntityId === rel.targetEntityId;

          // 从源节点右边缘到目标节点左边缘
          const x1 = src.x + 36;
          const y1 = src.y;
          const x2 = tgt.x - 36;
          const y2 = tgt.y;

          return (
            <g key={rel.id}>
              <line
                x1={x1} y1={y1} x2={x2} y2={y2}
                stroke={isSelected ? '#6366f1' : '#334155'}
                strokeWidth={isSelected ? 2 : 1.2}
                markerEnd="url(#arrow)"
                opacity={isSelected ? 0.8 : 0.4}
                className="transition-colors"
              />
              {/* 关系标签 */}
              {rel.name && (
                <text
                  x={(x1 + x2) / 2}
                  y={(y1 + y2) / 2 - 6}
                  textAnchor="middle"
                  fill={isSelected ? '#818cf8' : '#64748b'}
                  fontSize="8"
                  fontFamily="sans-serif"
                  fontWeight="500"
                >
                  {rel.name.length > 12 ? rel.name.slice(0, 11) + '…' : rel.name}
                </text>
              )}
            </g>
          );
        })}

        {/* 节点 */}
        {entities.map((entity) => {
          const pos = nodePositions.get(entity.id);
          if (!pos) return null;

          const isSelected = entity.id === selectedEntityId;
          const config =
            ENTITY_TYPE_CONFIG[entity.entityType] || ENTITY_TYPE_CONFIG.default;
          const etColor =
            entity.entityType === 'MASTER'
              ? '#f59e0b'
              : entity.entityType === 'TRANSACTION'
              ? '#10b981'
              : entity.entityType === 'EVENT'
              ? '#3b82f6'
              : entity.entityType === 'REFERENCE'
              ? '#8b5cf6'
              : '#64748b';

          return (
            <g
              key={entity.id}
              className="cursor-pointer"
              onClick={() => onSelectEntity(entity.id)}
            >
              {/* 节点矩形背景 */}
              <rect
                x={pos.x - 36}
                y={pos.y - 22}
                width={72}
                height={44}
                rx={8}
                fill={isSelected ? '#1e1b4b' : '#1a1f2e'}
                stroke={isSelected ? '#6366f1' : '#2a3040'}
                strokeWidth={isSelected ? 2 : 1}
                className="transition-colors"
              />

              {/* 类型色条 */}
              <rect
                x={pos.x - 36}
                y={pos.y - 22}
                width={4}
                height={44}
                rx={2}
                fill={etColor}
              />

              {/* 实体编码 */}
              <text
                x={pos.x - 26}
                y={pos.y - 6}
                fill="#e2e8f0"
                fontSize="10"
                fontWeight="600"
                fontFamily="monospace"
              >
                {entity.code.length > 10 ? entity.code.slice(0, 9) + '…' : entity.code}
              </text>

              {/* 实体名称 */}
              {entity.name && (
                <text
                  x={pos.x - 26}
                  y={pos.y + 10}
                  fill="#94a3b8"
                  fontSize="8"
                  fontFamily="sans-serif"
                >
                  {entity.name.length > 10 ? entity.name.slice(0, 9) + '…' : entity.name}
                </text>
              )}

              {/* 创建关系手柄（选中时显示） */}
              {isSelected && (
                <circle
                  cx={pos.x + 36}
                  cy={pos.y}
                  r={5}
                  fill="#6366f1"
                  stroke="#1a1f2e"
                  strokeWidth={2}
                  className="cursor-crosshair hover:fill-indigo-300"
                  onClick={(e) => {
                    e.stopPropagation();
                    onCreateRelation(entity.id);
                  }}
                />
              )}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

// ================================================================
// 子组件: PropertyEditorPanel (右侧属性编辑器, Sprint 3 完善)
// ================================================================

interface PropertyEditorPanelProps {
  entity: Entity | null;
  relationships: Relationship[];
  allEntities: Entity[];
  onCreateRelation: (sourceId: string) => void;
  saving: boolean;
}

function PropertyEditorPanel({
  entity,
  relationships,
  allEntities,
  onCreateRelation,
  saving,
}: PropertyEditorPanelProps) {
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
