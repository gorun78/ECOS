/**
 * PropertyTableEditor — 可编辑属性表格
 *
 * 用于 WorkbenchRightPanel 属性列表标签页。
 * 展示当前选中实体的属性列表，支持:
 *   - 内联编辑（失焦保存）
 *   - 添加新属性
 *   - 删除属性（确认后删除）
 *
 * 数据源: useWorkbenchStore.properties, createProperty, deleteProperty, updateProperty
 *
 * @license Apache-2.0
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import {
  Plus,
  Trash2,
  Loader2,
  SlidersHorizontal,
  Check,
  X,
} from 'lucide-react';
import { useWorkbenchStore } from '../../stores/useWorkbenchStore';
import type { Property } from '../../types/workbench';

// ── 属性类型选项 ──────────────────────────────────────────────

const PROPERTY_TYPES: { value: string; label: string }[] = [
  { value: 'STRING',   label: '字符串' },
  { value: 'NUMBER',   label: '数字' },
  { value: 'BOOLEAN',  label: '布尔' },
  { value: 'DATETIME', label: '日期时间' },
  { value: 'JSON',     label: 'JSON' },
];

// ── 属性类型颜色映射 ──────────────────────────────────────────

const TYPE_COLORS: Record<string, string> = {
  STRING:   'bg-blue-500/15 text-blue-400 border-blue-500/20',
  NUMBER:   'bg-amber-500/15 text-amber-400 border-amber-500/20',
  BOOLEAN:  'bg-emerald-500/15 text-emerald-400 border-emerald-500/20',
  DATETIME: 'bg-purple-500/15 text-purple-400 border-purple-500/20',
  JSON:     'bg-slate-500/15 text-slate-400 border-slate-500/20',
};

// ════════════════════════════════════════════════════════════════
// 新建行组件
// ════════════════════════════════════════════════════════════════

interface NewRowFormProps {
  onSave: (code: string, name: string, propertyType: string) => Promise<void>;
  onCancel: () => void;
}

function NewRowForm({ onSave, onCancel }: NewRowFormProps) {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [propertyType, setPropertyType] = useState('STRING');
  const [saving, setSaving] = useState(false);
  const codeInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    codeInputRef.current?.focus();
  }, []);

  const isValid = code.trim() !== '' && name.trim() !== '';

  const handleSave = async () => {
    if (!isValid || saving) return;
    setSaving(true);
    try {
      await onSave(code.trim(), name.trim(), propertyType);
    } catch {
      // error handled by store
    } finally {
      setSaving(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && isValid) handleSave();
    if (e.key === 'Escape') onCancel();
  };

  return (
    <tr className="bg-indigo-500/5" onKeyDown={handleKeyDown}>
      <td className="px-2 py-1.5">
        <input
          ref={codeInputRef}
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="编码"
          maxLength={64}
          className="w-full bg-[#0b0e14] border border-indigo-500/30 rounded px-2 py-1
            text-[11px] text-white placeholder:text-slate-600 font-mono
            focus:outline-none focus:border-indigo-500/60 transition"
        />
      </td>
      <td className="px-2 py-1.5">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="名称"
          maxLength={100}
          className="w-full bg-[#0b0e14] border border-indigo-500/30 rounded px-2 py-1
            text-[11px] text-white placeholder:text-slate-600
            focus:outline-none focus:border-indigo-500/60 transition"
        />
      </td>
      <td className="px-2 py-1.5">
        <select
          value={propertyType}
          onChange={(e) => setPropertyType(e.target.value)}
          className="w-full bg-[#0b0e14] border border-indigo-500/30 rounded px-1.5 py-1
            text-[11px] text-white focus:outline-none focus:border-indigo-500/60 transition"
        >
          {PROPERTY_TYPES.map((pt) => (
            <option key={pt.value} value={pt.value}>{pt.label}</option>
          ))}
        </select>
      </td>
      <td className="px-2 py-1.5 text-center">
        <span className="text-[10px] text-slate-600">—</span>
      </td>
      <td className="px-2 py-1.5">
        <div className="flex items-center gap-1">
          <button
            onClick={handleSave}
            disabled={!isValid || saving}
            className="p-1 rounded text-emerald-400 hover:bg-emerald-500/10 transition
              disabled:opacity-30 disabled:cursor-not-allowed"
            title="保存"
          >
            {saving ? <Loader2 size={12} className="animate-spin" /> : <Check size={12} />}
          </button>
          <button
            onClick={onCancel}
            disabled={saving}
            className="p-1 rounded text-slate-500 hover:text-slate-400 hover:bg-white/5 transition"
            title="取消"
          >
            <X size={12} />
          </button>
        </div>
      </td>
    </tr>
  );
}

// ════════════════════════════════════════════════════════════════
// 行内编辑组件
// ════════════════════════════════════════════════════════════════

interface EditableRowProps {
  prop: Property;
  onSave: (propId: string, name: string, propertyType: string) => Promise<void>;
  onCancel: () => void;
  onDelete: () => void;
}

function EditableRow({ prop, onSave, onCancel, onDelete }: EditableRowProps) {
  const [name, setName] = useState(prop.name || '');
  const [propertyType, setPropertyType] = useState(prop.propertyType || 'STRING');
  const [saving, setSaving] = useState(false);

  const isDirty = name !== (prop.name || '') || propertyType !== (prop.propertyType || 'STRING');

  const handleSave = async () => {
    if (!isDirty || saving) return;
    setSaving(true);
    try {
      await onSave(prop.id, name.trim(), propertyType);
      onCancel(); // exit edit mode
    } catch {
      // error handled by store
    } finally {
      setSaving(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSave();
    if (e.key === 'Escape') onCancel();
  };

  return (
    <tr className="bg-indigo-500/5" onKeyDown={handleKeyDown}>
      <td className="px-2 py-1.5">
        <span className="text-[11px] font-mono text-slate-500">{prop.code}</span>
      </td>
      <td className="px-2 py-1.5">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={100}
          className="w-full bg-[#0b0e14] border border-indigo-500/30 rounded px-2 py-1
            text-[11px] text-white focus:outline-none focus:border-indigo-500/60 transition"
        />
      </td>
      <td className="px-2 py-1.5">
        <select
          value={propertyType}
          onChange={(e) => setPropertyType(e.target.value)}
          className="w-full bg-[#0b0e14] border border-indigo-500/30 rounded px-1.5 py-1
            text-[11px] text-white focus:outline-none focus:border-indigo-500/60 transition"
        >
          {PROPERTY_TYPES.map((pt) => (
            <option key={pt.value} value={pt.value}>{pt.label}</option>
          ))}
        </select>
      </td>
      <td className="px-2 py-1.5 text-center">
        {prop.requiredFlag === 1 ? (
          <span className="text-[10px] text-red-400">是</span>
        ) : (
          <span className="text-[10px] text-slate-600">否</span>
        )}
      </td>
      <td className="px-2 py-1.5">
        <div className="flex items-center gap-1">
          <button
            onClick={handleSave}
            disabled={!isDirty || saving}
            className="p-1 rounded text-emerald-400 hover:bg-emerald-500/10 transition
              disabled:opacity-30 disabled:cursor-not-allowed"
            title="保存"
          >
            {saving ? <Loader2 size={12} className="animate-spin" /> : <Check size={12} />}
          </button>
          <button
            onClick={onCancel}
            className="p-1 rounded text-slate-500 hover:text-slate-400 hover:bg-white/5 transition"
            title="取消编辑"
          >
            <X size={12} />
          </button>
        </div>
      </td>
    </tr>
  );
}

// ════════════════════════════════════════════════════════════════
// 主组件
// ════════════════════════════════════════════════════════════════

interface PropertyTableEditorProps {
  entityId: string;
}

export default function PropertyTableEditor({ entityId }: PropertyTableEditorProps) {
  const store = useWorkbenchStore();

  // 从 store 中读取属性列表
  const properties: Property[] = store.properties[entityId] || [];

  const [showNewRow, setShowNewRow] = useState(false);
  const [editingPropId, setEditingPropId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<string | null>(null);

  // ── 创建属性 ────────────────────────────────────────────────
  const handleCreate = useCallback(async (code: string, name: string, propertyType: string) => {
    await store.createProperty(entityId, {
      code,
      name,
      propertyType,
      requiredFlag: 0,
      searchableFlag: 1,
    });
    setShowNewRow(false);
  }, [entityId, store]);

  // ── 更新属性（失焦保存） ────────────────────────────────────
  const handleUpdate = useCallback(async (propId: string, name: string, propertyType: string) => {
    await store.updateProperty(entityId, propId, {
      name,
      propertyType,
    });
  }, [entityId, store]);

  // ── 删除属性 ────────────────────────────────────────────────
  const handleDelete = useCallback(async (propId: string) => {
    setDeleting(propId);
    try {
      await store.deleteProperty(entityId, propId);
    } catch {
      // error handled by store
    } finally {
      setDeleting(null);
    }
  }, [entityId, store]);

  // ── 双击行进入编辑 ──────────────────────────────────────────
  const handleDoubleClick = useCallback((propId: string) => {
    setShowNewRow(false);
    setEditingPropId(propId);
  }, []);

  return (
    <div>
      {/* 头部操作栏 */}
      <div className="flex items-center justify-between mb-3">
        <h4 className="text-[11px] font-semibold text-slate-300 flex items-center gap-1.5">
          <SlidersHorizontal size={11} className="text-slate-500" />
          属性列表
          <span className="text-[10px] font-normal text-slate-500 ml-1">
            ({properties.length})
          </span>
        </h4>
        {!showNewRow && (
          <button
            onClick={() => { setShowNewRow(true); setEditingPropId(null); }}
            className="flex items-center gap-1 px-2.5 py-1 rounded-lg text-[10px]
              font-medium text-indigo-400 hover:text-indigo-300
              border border-indigo-500/20 hover:border-indigo-500/40
              bg-indigo-500/5 hover:bg-indigo-500/10 transition"
          >
            <Plus size={10} />
            添加属性
          </button>
        )}
      </div>

      {/* 表格 */}
      <div className="overflow-x-auto">
        <table className="w-full text-[11px]">
          <thead>
            <tr className="border-b border-[#1E293B]">
              <th className="text-left px-2 py-2 text-[10px] font-medium text-slate-500 w-[30%]">编码</th>
              <th className="text-left px-2 py-2 text-[10px] font-medium text-slate-500 w-[30%]">名称</th>
              <th className="text-left px-2 py-2 text-[10px] font-medium text-slate-500 w-[20%]">类型</th>
              <th className="text-center px-2 py-2 text-[10px] font-medium text-slate-500 w-[10%]">必填</th>
              <th className="text-center px-2 py-2 text-[10px] font-medium text-slate-500 w-[10%]">操作</th>
            </tr>
          </thead>
          <tbody>
            {/* 新增行 */}
            {showNewRow && (
              <NewRowForm
                onSave={handleCreate}
                onCancel={() => setShowNewRow(false)}
              />
            )}

            {/* 属性数据行 */}
            {properties.length === 0 && !showNewRow ? (
              <tr>
                <td colSpan={5} className="text-center py-8">
                  <SlidersHorizontal size={20} className="mx-auto mb-1 opacity-20 text-slate-500" />
                  <p className="text-[10px] text-slate-600">暂无属性，点击「添加属性」开始</p>
                </td>
              </tr>
            ) : (
              properties.map((prop) => {
                if (editingPropId === prop.id) {
                  return (
                    <EditableRow
                      key={prop.id}
                      prop={prop}
                      onSave={handleUpdate}
                      onCancel={() => setEditingPropId(null)}
                      onDelete={() => handleDelete(prop.id)}
                    />
                  );
                }

                const typeColor = TYPE_COLORS[prop.propertyType] || TYPE_COLORS.STRING;
                const isDeleting = deleting === prop.id;

                return (
                  <tr
                    key={prop.id}
                    onDoubleClick={() => handleDoubleClick(prop.id)}
                    className="border-b border-[#1E293B]/30 hover:bg-white/[0.02] transition cursor-pointer"
                  >
                    <td className="px-2 py-1.5">
                      <span className="text-[11px] font-mono text-slate-300">{prop.code}</span>
                    </td>
                    <td className="px-2 py-1.5">
                      <span className="text-[11px] text-white">{prop.name}</span>
                    </td>
                    <td className="px-2 py-1.5">
                      <span className={`text-[10px] px-1.5 py-0.5 rounded border ${typeColor}`}>
                        {prop.propertyType}
                      </span>
                    </td>
                    <td className="px-2 py-1.5 text-center">
                      {prop.requiredFlag === 1 ? (
                        <span className="text-[10px] text-red-400">是</span>
                      ) : (
                        <span className="text-[10px] text-slate-600">否</span>
                      )}
                    </td>
                    <td className="px-2 py-1.5">
                      <div className="flex items-center justify-center gap-1">
                        <button
                          onClick={(e) => { e.stopPropagation(); handleDelete(prop.id); }}
                          disabled={isDeleting}
                          className="p-1 rounded hover:bg-red-500/10 text-slate-600 hover:text-red-400 transition
                            disabled:opacity-30"
                          title="删除属性"
                        >
                          {isDeleting ? (
                            <Loader2 size={11} className="animate-spin" />
                          ) : (
                            <Trash2 size={11} />
                          )}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* 底部提示 */}
      <p className="text-[9px] text-slate-600 mt-2 px-2">
        双击行可编辑 · 输入完成后按 Enter 或点击 ✓ 保存
      </p>
    </div>
  );
}
