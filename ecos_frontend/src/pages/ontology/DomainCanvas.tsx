/**
 * DomainCanvas — 中间 SVG 画布 (M2 升级)
 *
 * M2 功能:
 *   1. 节点拖拽 — 原生事件 + localStorage 持久化位置
 *   2. 右键菜单 — CanvasContextMenu 浮层
 *   3. Shift 框选 — CanvasSelectionBox 矩形选区
 *   4. 连线 hover 高亮 — 加粗 + 标签 tooltip
 *   5. 点击选中 — 联动 PropertyEditor
 *
 * 不引入第三方库，全原生事件。
 *
 * @license Apache-2.0
 */

import React, { useMemo, useState, useCallback, useRef, useEffect } from 'react';
import { Network, Loader2, AlertCircle } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import type { Entity, Relationship } from '../../types/workbench';
import { ENTITY_TYPE_CONFIG } from './ontologyHelpers';
import CanvasContextMenu from './CanvasContextMenu';
import CanvasSelectionBox, { isEntityInSelection } from './CanvasSelectionBox';

// ── Props ──────────────────────────────────────────────────────

export interface DomainCanvasProps {
  entities: Entity[];
  relationships: Relationship[];
  selectedEntityId: string | null;
  onSelectEntity: (id: string | null) => void;
  onCreateRelation: (sourceId: string) => void;
  isLoading: boolean;
  error: string | null;
  onRetry: () => void;
  /** 域编码 — localStorage 位置持久化的 key */
  domainCode: string;
  /** 右键菜单：编辑实体 */
  onEditEntity: (id: string) => void;
  /** 右键菜单：删除实体 */
  onDeleteEntity: (id: string) => void;
  /** 右键菜单：添加属性 */
  onAddProperty: (id: string) => void;
}

// ── localStorage 辅助 ────────────────────────────────────────

const POS_STORAGE_PREFIX = 'ecos_ontology_positions_';

interface PosMap {
  [entityId: string]: { x: number; y: number };
}

function loadPositions(domainCode: string): PosMap {
  try {
    const raw = localStorage.getItem(POS_STORAGE_PREFIX + domainCode);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function savePositions(domainCode: string, map: PosMap) {
  try {
    localStorage.setItem(POS_STORAGE_PREFIX + domainCode, JSON.stringify(map));
  } catch { /* quota exceeded, silently ignore */ }
}

// ── 实体类型颜色映射 ──────────────────────────────────────────

const ETYPE_COLORS: Record<string, string> = {
  MASTER: '#f59e0b',
  TRANSACTION: '#10b981',
  EVENT: '#3b82f6',
  REFERENCE: '#8b5cf6',
};

function getEntityColor(et: string): string {
  return ETYPE_COLORS[et] || '#64748b';
}

// ── 网格布局默认值 ────────────────────────────────────────────

const CELL_W = 160;
const CELL_H = 100;
const PADDING = 40;

function computeGridPositions(entities: Entity[]): PosMap {
  const cols = Math.max(Math.ceil(Math.sqrt(entities.length)), 2);
  const map: PosMap = {};
  entities.forEach((entity, i) => {
    const col = i % cols;
    const row = Math.floor(i / cols);
    map[entity.id] = {
      x: PADDING + col * CELL_W + CELL_W / 2,
      y: PADDING + row * CELL_H + CELL_H / 2,
    };
  });
  return map;
}

// ── 组件 ──────────────────────────────────────────────────────

export default function DomainCanvas({
  entities,
  relationships,
  selectedEntityId,
  onSelectEntity,
  onCreateRelation,
  isLoading,
  error,
  onRetry,
  domainCode,
  onEditEntity,
  onDeleteEntity,
  onAddProperty,
}: DomainCanvasProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const svgRef = useRef<SVGSVGElement>(null);

  // ── 节点位置 (grid 默认 + localStorage 覆盖) ──
  const [nodePositions, setNodePositions] = useState<PosMap>(() => {
    const grid = computeGridPositions(entities);
    const saved = loadPositions(domainCode);
    // 合并：saved 优先，但只保留在 entities 中存在的
    const merged: PosMap = { ...grid };
    for (const [id, pos] of Object.entries(saved)) {
      if (merged[id]) merged[id] = pos;
    }
    return merged;
  });

  // entities 变化时重新计算网格（新实体获取默认位置）
  useEffect(() => {
    setNodePositions((prev) => {
      const grid = computeGridPositions(entities);
      const next: PosMap = {};
      for (const entity of entities) {
        next[entity.id] = prev[entity.id] || grid[entity.id];
      }
      return next;
    });
  }, [entities]);

  // ── 持久化 ──
  const persistPositions = useCallback(
    (pos: PosMap) => savePositions(domainCode, pos),
    [domainCode],
  );

  // ── 拖拽状态 ──
  const [dragState, setDragState] = useState<{
    entityId: string;
    startMouseX: number;
    startMouseY: number;
    startNodeX: number;
    startNodeY: number;
  } | null>(null);

  // ── 右键菜单状态 ──
  const [contextMenu, setContextMenu] = useState<{
    x: number;
    y: number;
    entityId: string;
    entityCode: string;
  } | null>(null);

  // ── 框选状态 ──
  const [selectionBox, setSelectionBox] = useState<{
    x1: number;
    y1: number;
    x2: number;
    y2: number;
  } | null>(null);

  // ── 框选选中集合 ──
  const [boxSelectedIds, setBoxSelectedIds] = useState<Set<string>>(new Set());

  // ── 连线 hover 状态 ──
  const [hoveredRelationId, setHoveredRelationId] = useState<string | null>(null);

  // ── tooltip 位置 ──
  const [tooltipPos, setTooltipPos] = useState<{ x: number; y: number } | null>(null);

  // ── Shift 键追踪 ──
  const shiftRef = useRef(false);

  useEffect(() => {
    const down = (e: KeyboardEvent) => { if (e.key === 'Shift') shiftRef.current = true; };
    const up = (e: KeyboardEvent) => { if (e.key === 'Shift') shiftRef.current = false; };
    window.addEventListener('keydown', down);
    window.addEventListener('keyup', up);
    return () => {
      window.removeEventListener('keydown', down);
      window.removeEventListener('keyup', up);
    };
  }, []);

  // ── SVG 坐标转换 ──
  const svgPoint = useCallback(
    (clientX: number, clientY: number): { x: number; y: number } => {
      const svg = svgRef.current;
      if (!svg) return { x: clientX, y: clientY };
      const pt = svg.createSVGPoint();
      pt.x = clientX;
      pt.y = clientY;
      const ctm = svg.getScreenCTM();
      if (!ctm) return { x: clientX, y: clientY };
      const svgPt = pt.matrixTransform(ctm.inverse());
      return { x: svgPt.x, y: svgPt.y };
    },
    [],
  );

  // ── 节点拖拽事件 ──
  const handleNodeMouseDown = useCallback(
    (e: React.MouseEvent, entityId: string) => {
      if (e.button !== 0) return; // only respond to left button
      e.stopPropagation();
      const pos = nodePositions[entityId];
      if (!pos) return;

      setDragState({
        entityId,
        startMouseX: e.clientX,
        startMouseY: e.clientY,
        startNodeX: pos.x,
        startNodeY: pos.y,
      });
    },
    [nodePositions],
  );

  const handleSVGMouseMove = useCallback(
    (e: React.MouseEvent) => {
      // ── 拖拽处理 ──
      if (dragState) {
        const dx = e.clientX - dragState.startMouseX;
        const dy = e.clientY - dragState.startMouseY;
        setNodePositions((prev) => ({
          ...prev,
          [dragState.entityId]: {
            x: dragState.startNodeX + dx,
            y: dragState.startNodeY + dy,
          },
        }));
        return;
      }

      // ── 框选处理 ──
      if (selectionBox) {
        const pt = svgPoint(e.clientX, e.clientY);
        setSelectionBox((prev) => prev ? { ...prev, x2: pt.x, y2: pt.y } : null);
        return;
      }
    },
    [dragState, selectionBox, svgPoint],
  );

  const handleSVGMouseUp = useCallback(
    (e: React.MouseEvent) => {
      // ── 结束拖拽 ──
      if (dragState) {
        setNodePositions((prev) => {
          persistPositions(prev);
          return prev;
        });
        setDragState(null);
        return;
      }

      // ── 结束框选 ──
      if (selectionBox) {
        const { x1, y1, x2, y2 } = selectionBox;
        const ids = new Set<string>();
        entities.forEach((entity) => {
          const pos = nodePositions[entity.id];
          if (pos && isEntityInSelection(pos.x, pos.y, x1, y1, x2, y2)) {
            ids.add(entity.id);
          }
        });
        setBoxSelectedIds(ids);
        setSelectionBox(null);
        return;
      }
    },
    [dragState, selectionBox, entities, nodePositions, persistPositions],
  );

  // ── 画布空白区域 mousedown → 可能开始框选 ──
  const handleSVGMouseDown = useCallback(
    (e: React.MouseEvent) => {
      // 只有 Shift+左键在空白区域才框选
      if (shiftRef.current && e.button === 0) {
        const pt = svgPoint(e.clientX, e.clientY);
        setSelectionBox({ x1: pt.x, y1: pt.y, x2: pt.x, y2: pt.y });
        setBoxSelectedIds(new Set());
        e.preventDefault();
      } else {
        // 点击空白取消选中
        onSelectEntity(null);
        setBoxSelectedIds(new Set());
      }
    },
    [onSelectEntity, svgPoint],
  );

  // ── 节点点击 ──
  const handleNodeClick = useCallback(
    (e: React.MouseEvent, entityId: string) => {
      e.stopPropagation();
      onSelectEntity(entityId);
    },
    [onSelectEntity],
  );

  // ── 右键菜单 ──
  const handleNodeContextMenu = useCallback(
    (e: React.MouseEvent, entityId: string, entityCode: string) => {
      e.preventDefault();
      e.stopPropagation();
      setContextMenu({ x: e.clientX, y: e.clientY, entityId, entityCode });
    },
    [],
  );

  // ── 连线 hover ──
  const handleEdgeMouseEnter = useCallback(
    (e: React.MouseEvent, relId: string) => {
      setHoveredRelationId(relId);
      setTooltipPos({ x: e.clientX, y: e.clientY });
    },
    [],
  );

  const handleEdgeMouseMove = useCallback(
    (e: React.MouseEvent) => {
      if (hoveredRelationId) {
        setTooltipPos({ x: e.clientX, y: e.clientY });
      }
    },
    [hoveredRelationId],
  );

  const handleEdgeMouseLeave = useCallback(() => {
    setHoveredRelationId(null);
    setTooltipPos(null);
  }, []);

  // ── 全局 mouseup (处理在 SVG 外释放的情况) ──
  useEffect(() => {
    const up = () => {
      if (dragState) {
        setNodePositions((prev) => {
          persistPositions(prev);
          return prev;
        });
        setDragState(null);
      }
      if (selectionBox) {
        const { x1, y1, x2, y2 } = selectionBox;
        const ids = new Set<string>();
        entities.forEach((entity) => {
          const pos = nodePositions[entity.id];
          if (pos && isEntityInSelection(pos.x, pos.y, x1, y1, x2, y2)) {
            ids.add(entity.id);
          }
        });
        setBoxSelectedIds(ids);
        setSelectionBox(null);
      }
    };
    window.addEventListener('mouseup', up);
    return () => window.removeEventListener('mouseup', up);
  }, [dragState, selectionBox, entities, nodePositions, persistPositions]);

  // ── SVG 尺寸 ──
  const cols = Math.max(Math.ceil(Math.sqrt(entities.length)), 2);
  const svgWidth = Math.max(cols * CELL_W + PADDING * 2, 600);
  const svgHeight = Math.max(Math.ceil(entities.length / cols) * CELL_H + PADDING * 2, 400);

  // ── 加载态 ──
  if (isLoading) {
    return (
      <div className={`absolute inset-0 flex items-center justify-center ${styles.appBg}`}>
        <div className="text-center">
          <Loader2 size={28} className="animate-spin text-indigo-400 mx-auto mb-3" />
          <p className="text-xs text-slate-500">{t("ontology.designer.loadingDomainData")}</p>
        </div>
      </div>
    );
  }

  // ── 错误态 ──
  if (error) {
    return (
      <div className={`absolute inset-0 flex items-center justify-center ${styles.appBg}`}>
        <div className="text-center p-8">
          <AlertCircle size={32} className="text-red-400 mx-auto mb-3" />
          <p className="text-xs text-red-400 mb-3">{error}</p>
          <button
            onClick={onRetry}
            className="px-4 py-1.5 rounded-lg text-xs bg-slate-700 text-slate-300 hover:bg-slate-600"
          >
            {t("ontology.designer.retry")}
          </button>
        </div>
      </div>
    );
  }

  // ── 空态 ──
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

  // ── 绘制 ──
  return (
    <div className={`absolute inset-0 overflow-auto ${styles.appBg}`}>
      <svg
        ref={svgRef}
        width={Math.max(svgWidth, 600)}
        height={Math.max(svgHeight, 400)}
        className="min-w-full min-h-full"
        onMouseDown={handleSVGMouseDown}
        onMouseMove={handleSVGMouseMove}
        onMouseUp={handleSVGMouseUp}
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
          <marker
            id="arrow-hover"
            viewBox="0 0 10 10"
            refX="16"
            refY="5"
            markerWidth="5"
            markerHeight="5"
            orient="auto-start-reverse"
          >
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#818cf8" />
          </marker>
          <marker
            id="arrow-selected"
            viewBox="0 0 10 10"
            refX="16"
            refY="5"
            markerWidth="5"
            markerHeight="5"
            orient="auto-start-reverse"
          >
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#6366f1" />
          </marker>
        </defs>

        {/* 网格背景 */}
        <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
          <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#1E293B" strokeWidth="0.3" />
        </pattern>
        <rect width="100%" height="100%" fill="url(#grid)" />

        {/* 边/关系 */}
        {relationships.map((rel) => {
          const src = nodePositions[rel.sourceEntityId];
          const tgt = nodePositions[rel.targetEntityId];
          if (!src || !tgt) return null;

          const isSelected =
            selectedEntityId === rel.sourceEntityId ||
            selectedEntityId === rel.targetEntityId;
          const isHovered = hoveredRelationId === rel.id;
          const highlight = isSelected || isHovered;

          // 从源节点右边缘到目标节点左边缘
          const x1 = src.x + 36;
          const y1 = src.y;
          const x2 = tgt.x - 36;
          const y2 = tgt.y;

          return (
            <g
              key={rel.id}
              onMouseEnter={(e) => handleEdgeMouseEnter(e, rel.id)}
              onMouseMove={handleEdgeMouseMove}
              onMouseLeave={handleEdgeMouseLeave}
              className="cursor-pointer"
            >
              {/* 不可见宽区域便于 hover 命中 */}
              <line
                x1={x1} y1={y1} x2={x2} y2={y2}
                stroke="transparent"
                strokeWidth={12}
              />
              <line
                x1={x1} y1={y1} x2={x2} y2={y2}
                stroke={highlight ? '#6366f1' : '#334155'}
                strokeWidth={highlight ? 2.5 : 1.2}
                markerEnd={isHovered ? 'url(#arrow-hover)' : isSelected ? 'url(#arrow-selected)' : 'url(#arrow)'}
                opacity={highlight ? 0.9 : 0.4}
                className="transition-all duration-150"
              />
              {/* 关系标签 */}
              {rel.name && (
                <text
                  x={(x1 + x2) / 2}
                  y={(y1 + y2) / 2 - 6}
                  textAnchor="middle"
                  fill={highlight ? '#818cf8' : '#64748b'}
                  fontSize={highlight ? '9' : '8'}
                  fontFamily="sans-serif"
                  fontWeight={highlight ? '600' : '500'}
                >
                  {rel.name.length > 12 ? rel.name.slice(0, 11) + '\u2026' : rel.name}
                </text>
              )}
            </g>
          );
        })}

        {/* Shift 框选矩形 */}
        {selectionBox && (
          <CanvasSelectionBox
            x1={selectionBox.x1}
            y1={selectionBox.y1}
            x2={selectionBox.x2}
            y2={selectionBox.y2}
          />
        )}

        {/* 节点 */}
        {entities.map((entity) => {
          const pos = nodePositions[entity.id];
          if (!pos) return null;

          const isSelected = entity.id === selectedEntityId;
          const isBoxSelected = boxSelectedIds.has(entity.id);
          const isHighlighted = isSelected || isBoxSelected;
          const etColor = getEntityColor(entity.entityType);

          return (
            <g
              key={entity.id}
              className="cursor-pointer select-none"
              onClick={(e) => handleNodeClick(e, entity.id)}
              onMouseDown={(e) => handleNodeMouseDown(e, entity.id)}
              onContextMenu={(e) => handleNodeContextMenu(e, entity.id, entity.code)}
            >
              {/* 框选高亮外发光 */}
              {isBoxSelected && !isSelected && (
                <rect
                  x={pos.x - 40}
                  y={pos.y - 26}
                  width={80}
                  height={52}
                  rx={10}
                  fill="none"
                  stroke="#6366f1"
                  strokeWidth={1.5}
                  strokeDasharray="4 2"
                  opacity={0.6}
                />
              )}

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
                {entity.code.length > 10 ? entity.code.slice(0, 9) + '\u2026' : entity.code}
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
                  {entity.name.length > 10 ? entity.name.slice(0, 9) + '\u2026' : entity.name}
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
                  onMouseDown={(e) => e.stopPropagation()}
                />
              )}
            </g>
          );
        })}
      </svg>

      {/* 右键菜单 */}
      {contextMenu && (
        <CanvasContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          entityId={contextMenu.entityId}
          entityCode={contextMenu.entityCode}
          onEdit={onEditEntity}
          onDelete={onDeleteEntity}
          onCreateRelation={onCreateRelation}
          onAddProperty={onAddProperty}
          onClose={() => setContextMenu(null)}
        />
      )}

      {/* 连线 hover tooltip */}
      {hoveredRelationId && tooltipPos && (() => {
        const rel = relationships.find((r) => r.id === hoveredRelationId);
        if (!rel) return null;
        return (
          <div
            className="fixed z-[9998] pointer-events-none px-2.5 py-1.5 rounded-lg
              bg-slate-800 border border-slate-600 shadow-xl text-[11px] text-slate-200
              whitespace-nowrap"
            style={{ left: tooltipPos.x + 12, top: tooltipPos.y - 10 }}
          >
            <span className="font-mono text-indigo-300">{rel.code}</span>
            {rel.name && <span className="text-slate-400 ml-1.5">{rel.name}</span>}
            <span className="text-slate-600 ml-1.5">({rel.relationshipType})</span>
          </div>
        );
      })()}
    </div>
  );
}
