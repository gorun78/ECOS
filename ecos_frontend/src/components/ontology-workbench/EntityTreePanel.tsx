/**
 * EntityTreePanel — 左侧实体树面板
 *
 * 用于 DomainDesignerView 左侧面板，提供实体搜索和导航。
 * 功能:
 *   - 搜索框: 按编码/名称/描述过滤实体
 *   - 实体列表: 图标 + 名称 + 类型标签
 *   - 点击 → selectEntity
 *   - [+新建] 按钮 → 打开 CreateEntityModal
 *
 * 深色主题: bg-[#141924], border-[#1E293B]
 *
 * @license Apache-2.0
 */

import React, { useState, useMemo, useCallback } from 'react';
import {
  Search,
  Plus,
  Box,
  Database,
  List,
  Layers,
  Globe,
  Loader2,
  ChevronRight,
  Package,
} from 'lucide-react';
import { useWorkbenchStore } from '../../stores/useWorkbenchStore';
import type { Entity } from '../../types/workbench';

// ── 实体类型配置 ──────────────────────────────────────────────

const ENTITY_TYPE_CONFIG: Record<string, {
  icon: React.ReactNode;
  color: string;
  bgColor: string;
  label: string;
}> = {
  MASTER: {
    icon: <Database size={13} />,
    color: 'text-amber-400',
    bgColor: 'bg-amber-500/10',
    label: '主数据',
  },
  TRANSACTION: {
    icon: <List size={13} />,
    color: 'text-emerald-400',
    bgColor: 'bg-emerald-500/10',
    label: '事务',
  },
  EVENT: {
    icon: <Layers size={13} />,
    color: 'text-blue-400',
    bgColor: 'bg-blue-500/10',
    label: '事件',
  },
  REFERENCE: {
    icon: <Globe size={13} />,
    color: 'text-purple-400',
    bgColor: 'bg-purple-500/10',
    label: '引用',
  },
  default: {
    icon: <Box size={13} />,
    color: 'text-slate-400',
    bgColor: 'bg-slate-500/10',
    label: '未知',
  },
};

function getEntityTypeLabel(et: string): string {
  const map: Record<string, string> = {
    MASTER: '主数据',
    TRANSACTION: '事务',
    EVENT: '事件',
    REFERENCE: '引用',
  };
  return map[et] || et;
}

// ════════════════════════════════════════════════════════════════
// 实体列表项
// ════════════════════════════════════════════════════════════════

interface EntityListItemProps {
  entity: Entity;
  isSelected: boolean;
  onSelect: (id: string) => void;
  propertyCount?: number;
}

function EntityListItem({ entity, isSelected, onSelect, propertyCount }: EntityListItemProps) {
  const config = ENTITY_TYPE_CONFIG[entity.entityType] || ENTITY_TYPE_CONFIG.default;

  const handleClick = useCallback(() => {
    onSelect(entity.id);
  }, [entity.id, onSelect]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onSelect(entity.id);
    }
  }, [entity.id, onSelect]);

  return (
    <button
      onClick={handleClick}
      onKeyDown={handleKeyDown}
      className={`w-full text-left px-3 py-2.5 transition flex items-center gap-2.5 group border-l-2 ${
        isSelected
          ? 'bg-indigo-500/10 border-l-indigo-500'
          : 'border-l-transparent hover:bg-white/[0.03]'
      }`}
    >
      {/* 类型图标 */}
      <div className={`p-1 rounded-md ${config.bgColor} ${config.color} shrink-0`}>
        {config.icon}
      </div>

      {/* 实体信息 */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5">
          <span className="text-xs font-semibold text-white truncate">
            {entity.name || entity.code}
          </span>
          <span className={`text-[9px] font-normal ${config.color} opacity-70`}>
            {getEntityTypeLabel(entity.entityType)}
          </span>
        </div>
        <div className="text-[10px] text-slate-500 font-mono truncate mt-0.5">
          {entity.code}
          {propertyCount !== undefined && (
            <span className="text-slate-600 ml-1.5">
              · {propertyCount} 属性
            </span>
          )}
        </div>
      </div>

      {/* 选中指示器 */}
      <div className={`shrink-0 transition-opacity ${isSelected ? 'opacity-100' : 'opacity-0 group-hover:opacity-50'}`}>
        <ChevronRight size={13} className="text-indigo-400" />
      </div>
    </button>
  );
}

// ════════════════════════════════════════════════════════════════
// 主组件
// ════════════════════════════════════════════════════════════════

interface EntityTreePanelProps {
  /** 显示在顶部的标题文本（通常是域编码） */
  domainCode: string;
  /** 实体数量统计 */
  entityCount: number;
  /** 关系数量统计 */
  relationshipCount: number;
  /** 返回域列表的回调 */
  onBack?: () => void;
  /** 打开创建实体弹窗的回调 */
  onCreateEntity?: () => void;
  /** 实体列表加载中 */
  loading?: boolean;
  /** 底部自定义内容（可选） */
  footer?: React.ReactNode;
}

export default function EntityTreePanel({
  domainCode,
  entityCount,
  relationshipCount,
  onBack,
  onCreateEntity,
  loading = false,
  footer,
}: EntityTreePanelProps) {
  const store = useWorkbenchStore();
  const { entities, selectedEntityId, properties } = store;

  const [searchQuery, setSearchQuery] = useState('');

  // ── 搜索过滤 ────────────────────────────────────────────────
  const filteredEntities = useMemo(() => {
    if (!searchQuery.trim()) return entities;
    const q = searchQuery.toLowerCase();
    return entities.filter(
      (e) =>
        e.code.toLowerCase().includes(q) ||
        e.name.toLowerCase().includes(q) ||
        (e.description && e.description.toLowerCase().includes(q))
    );
  }, [entities, searchQuery]);

  // ── 选中实体 ─────────────────────────────────────────────────
  const handleSelectEntity = useCallback(
    (id: string) => {
      store.selectEntity(id);
    },
    [store]
  );

  return (
    <div className="flex flex-col h-full bg-[#141924]">
      {/* ── 域标题区域 ── */}
      <div className="px-4 py-3 border-b border-[#1E293B] shrink-0">
        {/* 返回按钮 */}
        {onBack && (
          <button
            onClick={onBack}
            className="flex items-center gap-1 text-[10px] text-slate-500 hover:text-slate-400 mb-2.5 transition"
          >
            <ChevronRight size={11} className="rotate-180" />
            返回域列表
          </button>
        )}

        <div className="flex items-center gap-2.5">
          <div className="p-1.5 rounded-lg bg-indigo-500/10">
            <Package size={15} className="text-indigo-400" />
          </div>
          <div className="min-w-0 flex-1">
            <h3 className="text-sm font-semibold text-white truncate">
              {domainCode}
            </h3>
            <p className="text-[10px] text-slate-500">
              {entityCount} 实体 · {relationshipCount} 关系
            </p>
          </div>
        </div>
      </div>

      {/* ── 搜索框 ── */}
      <div className="px-4 py-2.5 border-b border-[#1E293B] shrink-0">
        <div className="relative">
          <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="搜索实体..."
            className="w-full bg-[#0b0e14] border border-[#1E293B] rounded-lg pl-7 pr-3 py-1.5
              text-xs text-white placeholder:text-slate-600
              focus:outline-none focus:border-indigo-500/40 transition"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-600 hover:text-slate-400"
            >
              <span className="text-[11px] leading-none">✕</span>
            </button>
          )}
        </div>
      </div>

      {/* ── 实体列表 ── */}
      <div className="flex-1 overflow-y-auto scrollbar-thin">
        {loading ? (
          <div className="flex items-center justify-center py-12 text-slate-500">
            <Loader2 size={18} className="animate-spin" />
          </div>
        ) : filteredEntities.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-14 text-slate-500 px-4">
            <Box size={28} className="mb-3 opacity-20" />
            <p className="text-xs">
              {searchQuery ? '无匹配实体' : '暂无实体'}
            </p>
            {!searchQuery && (
              <p className="text-[10px] mt-1 opacity-50">
                点击下方按钮创建第一个实体
              </p>
            )}
          </div>
        ) : (
          <div>
            {filteredEntities.map((entity) => {
              const isSelected = entity.id === selectedEntityId;
              const propCount = (properties[entity.id] || []).length;

              return (
                <EntityListItem
                  key={entity.id}
                  entity={entity}
                  isSelected={isSelected}
                  onSelect={handleSelectEntity}
                  propertyCount={propCount}
                />
              );
            })}
          </div>
        )}
      </div>

      {/* ── 底部：创建按钮 + 自定义内容 ── */}
      <div className="shrink-0 border-t border-[#1E293B]">
        {/* 创建实体按钮 */}
        {onCreateEntity && (
          <div className="px-4 py-2.5">
            <button
              onClick={onCreateEntity}
              className="w-full flex items-center justify-center gap-1.5 py-2 rounded-lg
                text-xs font-medium text-indigo-400 hover:text-indigo-300
                border border-indigo-500/20 hover:border-indigo-500/40
                bg-indigo-500/5 hover:bg-indigo-500/10 transition"
            >
              <Plus size={13} />
              新建实体
            </button>
          </div>
        )}

        {/* 自定义底部 */}
        {footer}
      </div>
    </div>
  );
}
