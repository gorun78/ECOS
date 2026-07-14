/**
 * WorkbenchRightPanel — 右侧面板容器（标签页式详情面板）
 *
 * 当选中实体时，提供三个标签页:
 *   - 基本信息: EntityBasicInfoForm（编码只读/名称/类型/描述可编辑）
 *   - 属性列表: PropertyTableEditor（可编辑属性表格）
 *   - 关联关系: RelationshipListPanel（当前实体关系列表）
 *
 * 无选中实体时显示空状态提示。
 *
 * @license Apache-2.0
 */

import React, { useState, useCallback } from 'react';
import {
  Edit3,
  List,
  GitBranch,
  Eye,
  Database,
  BookOpen,
  Layers,
  Globe,
  Box,
  Save,
  Loader2,
} from 'lucide-react';
import { useWorkbenchStore } from '../../stores/useWorkbenchStore';
import type { Entity } from '../../types/workbench';
import PropertyTableEditor from './PropertyTableEditor';
import RelationshipListPanel from './RelationshipListPanel';
import DataSourcePanel from './panels/DataSourcePanel';
import GlossaryBindingPanel from './panels/GlossaryBindingPanel';

// ── 实体类型标签映射 ──────────────────────────────────────────

const ENTITY_TYPE_CONFIG: Record<string, { icon: React.ReactNode; color: string; label: string }> = {
  MASTER:   { icon: <Database size={12} className="text-amber-400" />,  color: 'text-amber-400',  label: '主数据' },
  TRANSACTION: { icon: <List size={12} className="text-emerald-400" />, color: 'text-emerald-400', label: '事务' },
  EVENT:    { icon: <Layers size={12} className="text-blue-400" />,   color: 'text-blue-400',   label: '事件' },
  REFERENCE:{ icon: <Globe size={12} className="text-purple-400" />,  color: 'text-purple-400',  label: '引用' },
  default:  { icon: <Box size={12} className="text-slate-400" />,     color: 'text-slate-400',   label: '未知' },
};

function getEntityTypeLabel(et: string): string {
  return ENTITY_TYPE_CONFIG[et]?.label || et;
}

// ── 标签页定义 ────────────────────────────────────────────────

type TabKey = 'basic' | 'properties' | 'relationships' | 'glossary' | 'datasource';

interface TabDef {
  key: TabKey;
  label: string;
  icon: React.ReactNode;
}

const TABS: TabDef[] = [
  { key: 'basic', label: '基本信息', icon: <Edit3 size={11} /> },
  { key: 'properties', label: '属性列表', icon: <List size={11} /> },
  { key: 'relationships', label: '关联关系', icon: <GitBranch size={11} /> },
  { key: 'glossary', label: '术语关联', icon: <BookOpen size={11} /> },
  { key: 'datasource', label: '数据映射', icon: <Database size={11} /> },
];

// ── 实体类型选项 ──────────────────────────────────────────────

const ENTITY_TYPES: string[] = ['MASTER', 'TRANSACTION', 'EVENT', 'REFERENCE'];

// ════════════════════════════════════════════════════════════════
// 基本信息编辑表单（内部子组件）
// ════════════════════════════════════════════════════════════════

interface EntityBasicInfoFormProps {
  entity: Entity;
}

function EntityBasicInfoForm({ entity }: EntityBasicInfoFormProps) {
  const store = useWorkbenchStore();
  const { savingEntity } = store;

  const [name, setName] = useState(entity.name || '');
  const [entityType, setEntityType] = useState(entity.entityType || 'MASTER');
  const [description, setDescription] = useState(entity.description || '');
  const [saving, setSaving] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');

  // 检测是否有修改
  const isDirty =
    name !== (entity.name || '') ||
    entityType !== (entity.entityType || 'MASTER') ||
    description !== (entity.description || '');

  const handleSave = useCallback(async () => {
    if (!isDirty || saving || savingEntity) return;
    setSaving(true);
    try {
      await store.updateEntity(entity.id, {
        name: name.trim() || undefined,
        entityType,
        description: description.trim() || undefined,
      });
      setSuccessMsg('保存成功');
      setTimeout(() => setSuccessMsg(''), 2000);
    } catch {
      // error handled by store
    } finally {
      setSaving(false);
    }
  }, [isDirty, saving, savingEntity, entity.id, name, entityType, description, store]);

  return (
    <div className="space-y-3">
      {/* 编码（只读） */}
      <div>
        <label className="block text-[10px] font-medium text-slate-500 mb-1">编码</label>
        <input
          value={entity.code}
          readOnly
          className="w-full bg-[#0b0e14] border border-[#1E293B] rounded-lg px-3 py-2
            text-xs text-slate-500 font-mono cursor-not-allowed"
        />
      </div>

      {/* 名称 */}
      <div>
        <label className="block text-[10px] font-medium text-slate-500 mb-1">名称</label>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={100}
          className="w-full bg-[#0b0e14] border border-[#1E293B] rounded-lg px-3 py-2
            text-xs text-white placeholder:text-slate-600
            focus:outline-none focus:border-indigo-500/50 transition"
        />
      </div>

      {/* 实体类型 */}
      <div>
        <label className="block text-[10px] font-medium text-slate-500 mb-1.5">实体类型</label>
        <div className="grid grid-cols-2 gap-1.5">
          {ENTITY_TYPES.map((et) => {
            const cfg = ENTITY_TYPE_CONFIG[et] || ENTITY_TYPE_CONFIG.default;
            return (
              <button
                key={et}
                onClick={() => setEntityType(et)}
                className={`flex items-center gap-1.5 px-2.5 py-2 rounded-lg text-[10px] font-medium transition border ${
                  entityType === et
                    ? 'border-indigo-500/40 bg-indigo-500/10 text-indigo-300'
                    : 'border-[#1E293B] bg-[#0b0e14] text-slate-400 hover:border-[#2a3040]'
                }`}
              >
                {cfg.icon}
                <span>{getEntityTypeLabel(et)}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* 描述 */}
      <div>
        <label className="block text-[10px] font-medium text-slate-500 mb-1">描述</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
          maxLength={500}
          placeholder="实体描述（可选）"
          className="w-full bg-[#0b0e14] border border-[#1E293B] rounded-lg px-3 py-2
            text-xs text-white placeholder:text-slate-600 resize-none
            focus:outline-none focus:border-indigo-500/50 transition"
        />
      </div>

      {/* 元信息（只读） */}
      <div className="pt-2 border-t border-[#1E293B] space-y-1.5 text-[10px]">
        {entity.createdAt && (
          <div className="flex justify-between">
            <span className="text-slate-500">创建时间</span>
            <span className="text-slate-400">{entity.createdAt}</span>
          </div>
        )}
        {entity.updatedAt && (
          <div className="flex justify-between">
            <span className="text-slate-500">更新时间</span>
            <span className="text-slate-400">{entity.updatedAt}</span>
          </div>
        )}
        <div className="flex justify-between">
          <span className="text-slate-500">ID</span>
          <span className="text-slate-500 font-mono text-[9px]">{entity.id}</span>
        </div>
      </div>

      {/* 保存按钮 */}
      <div className="flex items-center gap-2 pt-1">
        <button
          onClick={handleSave}
          disabled={!isDirty || saving || savingEntity}
          className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-[11px] font-semibold
            bg-indigo-600 hover:bg-indigo-500 text-white
            disabled:opacity-40 disabled:cursor-not-allowed transition"
        >
          {saving ? <Loader2 size={12} className="animate-spin" /> : <Save size={12} />}
          {saving ? '保存中...' : '保存修改'}
        </button>
        {successMsg && (
          <span className="text-[10px] text-emerald-400">{successMsg}</span>
        )}
      </div>
    </div>
  );
}

// ════════════════════════════════════════════════════════════════
// 主组件
// ════════════════════════════════════════════════════════════════

export default function WorkbenchRightPanel({
  selectedEntityId: externalSelectedId,
  ontologyId: externalOntologyId,
}: {
  selectedEntityId?: string | null;
  ontologyId?: string;
} = {}) {
  const store = useWorkbenchStore();
  const selectedEntityId = externalSelectedId ?? store.selectedEntityId;
  const ontologyId = externalOntologyId ?? 'ont001';
  const { entities, rightPanelCollapsed } = store;

  const selectedEntity = entities.find((e) => e.id === selectedEntityId) || null;

  const [activeTab, setActiveTab] = useState<TabKey>('basic');

  // ── 无选中实体 ──────────────────────────────────────────────
  if (!selectedEntity) {
    return (
      <div className="flex-1 flex items-center justify-center text-slate-500">
        <div className="text-center p-6">
          <Eye size={32} className="mx-auto mb-2 opacity-20" />
          <p className="text-xs">选择实体查看详情</p>
          <p className="text-[10px] mt-1 opacity-60">
            点击画布节点或左侧实体树
          </p>
        </div>
      </div>
    );
  }

  const config = ENTITY_TYPE_CONFIG[selectedEntity.entityType] || ENTITY_TYPE_CONFIG.default;

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* ── 头部：实体信息摘要 ── */}
      <div className="px-4 py-3 border-b border-[#1E293B] shrink-0">
        <div className="flex items-center gap-2 mb-1.5">
          <div className="p-1 rounded bg-indigo-500/10">
            {config.icon}
          </div>
          <h3 className="text-sm font-semibold text-white truncate">
            {selectedEntity.name || selectedEntity.code}
          </h3>
        </div>
        <p className="text-[10px] font-mono text-slate-500 truncate">{selectedEntity.code}</p>
        <div className="flex items-center gap-1.5 mt-2">
          <span className={`text-[10px] px-2 py-0.5 rounded bg-slate-700/50 text-slate-300`}>
            {getEntityTypeLabel(selectedEntity.entityType)}
          </span>
        </div>
      </div>

      {/* ── 标签页导航 ── */}
      <div className="flex border-b border-[#1E293B] shrink-0">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex items-center gap-1.5 px-4 py-2.5 text-[11px] font-medium transition border-b-2 -mb-[1px] ${
              activeTab === tab.key
                ? 'border-indigo-500 text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-400'
            }`}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </div>

      {/* ── 标签页内容 ── */}
      <div className="flex-1 overflow-y-auto p-4">
        {activeTab === 'basic' && (
          <EntityBasicInfoForm entity={selectedEntity} />
        )}

        {activeTab === 'properties' && (
          <PropertyTableEditor entityId={selectedEntity.id} />
        )}

        {activeTab === 'relationships' && (
          <RelationshipListPanel entityId={selectedEntity.id} />
        )}

        {activeTab === 'glossary' && (
          <GlossaryBindingPanel entityId={selectedEntity.id} />
        )}

        {activeTab === 'datasource' && (
          <DataSourcePanel entityId={selectedEntity.id} />
        )}
      </div>
    </div>
  );
}
