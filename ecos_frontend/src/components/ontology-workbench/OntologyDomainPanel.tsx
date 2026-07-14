/**
 * OntologyDomainPanel — 本体选择器（API 驱动 + CRUD）
 *
 * 从后端 API 加载本体列表（GET /api/v1/ecos/ontologies），
 * 支持创建/编辑/删除本体。点击卡片切换当前本体。
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import { Globe, Box, Database, Layers, Plus, Pencil, Trash2, X, Save, Loader2 } from 'lucide-react';

// ── 本体 → 图标映射 ──
const ONTOLOGY_ICONS: Record<string, React.FC<{ className?: string }>> = {
  ont001: Box,
  ont002: Globe,
};

// ── API helpers ──
const ONTOLOGY_API = '/api/v1/ecos/ontologies';
const ENTITY_API = '/api/v1/ecos/ontologies'; // {ontologyId}/entities

async function apiFetch(path: string, options?: RequestInit): Promise<any> {
  const token = localStorage.getItem('token') || '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(path, { ...options, headers });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const json = await res.json();
  return json.data ?? json;
}

async function fetchOntologies(): Promise<any[]> {
  return apiFetch(ONTOLOGY_API);
}

async function fetchEntityCount(ontologyId: string): Promise<number> {
  try {
    const data = await apiFetch(`${ENTITY_API}/${ontologyId}/entities`);
    return Array.isArray(data) ? data.length : 0;
  } catch {
    return 0;
  }
}

interface OntologyItem {
  id: string;
  code: string;
  name: string;
  version?: string;
  status?: string;
  description?: string;
}

// ── Props ──
interface OntologyDomainPanelProps {
  ontologyId: string;
  onOntologyChange: (id: string) => void;
  onSelectEntity?: (entityId: string | null) => void;
  selectedEntityId?: string | null;
}

// ─── 组件 ────────────────────────────────────────────────────────

export default function OntologyDomainPanel({
  ontologyId,
  onOntologyChange,
  onSelectEntity,
  selectedEntityId,
}: OntologyDomainPanelProps) {
  const [ontologies, setOntologies] = useState<OntologyItem[]>([]);
  const [entityCounts, setEntityCounts] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // CRUD 状态
  const [showCreate, setShowCreate] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [formName, setFormName] = useState('');
  const [formCode, setFormCode] = useState('');
  const [formDescription, setFormDescription] = useState('');
  const [saving, setSaving] = useState(false);

  // 加载本体列表
  const loadOntologies = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const list = await fetchOntologies();
      setOntologies(list);
      // 加载各本体的实体数量
      const counts = await Promise.all(
        list.map(async (o: OntologyItem) => {
          const count = await fetchEntityCount(o.id);
          return [o.id, count] as const;
        }),
      );
      setEntityCounts(Object.fromEntries(counts));
    } catch (err: any) {
      setError(err?.message || '加载本体列表失败');
      // 回退到硬编码数据
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadOntologies();
  }, [loadOntologies]);

  // 创建本体
  const handleCreate = async () => {
    if (!formCode || !formName) return;
    setSaving(true);
    try {
      await apiFetch(ONTOLOGY_API, {
        method: 'POST',
        body: JSON.stringify({ code: formCode, name: formName, description: formDescription }),
      });
      setShowCreate(false);
      setFormCode('');
      setFormName('');
      setFormDescription('');
      await loadOntologies();
    } catch (err: any) {
      setError(err?.message || '创建失败');
    } finally {
      setSaving(false);
    }
  };

  // 编辑本体
  const handleEdit = async () => {
    if (!editingId || !formName) return;
    setSaving(true);
    try {
      await apiFetch(`${ONTOLOGY_API}/${editingId}`, {
        method: 'PUT',
        body: JSON.stringify({ name: formName, description: formDescription }),
      });
      setEditingId(null);
      await loadOntologies();
    } catch (err: any) {
      setError(err?.message || '更新失败');
    } finally {
      setSaving(false);
    }
  };

  // 删除本体
  const handleDelete = async () => {
    if (!deletingId) return;
    setSaving(true);
    try {
      await apiFetch(`${ONTOLOGY_API}/${deletingId}`, { method: 'DELETE' });
      setDeletingId(null);
      // 如果删除的是当前选中，切到第一个
      if (deletingId === ontologyId && ontologies.length > 1) {
        const next = ontologies.find(o => o.id !== deletingId);
        if (next) onOntologyChange(next.id);
      }
      await loadOntologies();
    } catch (err: any) {
      setError(err?.message || '删除失败');
    } finally {
      setSaving(false);
    }
  };

  // 打开编辑弹窗
  const openEdit = (o: OntologyItem) => {
    setEditingId(o.id);
    setFormCode(o.code);
    setFormName(o.name);
    setFormDescription(o.description || '');
  };

  const handleClick = (id: string) => {
    onOntologyChange(id);
    onSelectEntity?.(null);
  };

  // 回退数据（API 失败时）
  const displayOntologies = ontologies.length > 0 ? ontologies : [
    { id: 'ont001', code: 'supply_chain', name: '供应链本体' },
    { id: 'ont002', code: 'finance', name: '财务本体' },
  ];

  return (
    <div className="flex flex-col h-full bg-[#141924] text-slate-200">
      {/* 标题栏 */}
      <div className="px-3 py-3 border-b border-[#1E293B] flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Database className="w-4 h-4 text-indigo-400" />
          <div>
            <div className="text-xs font-semibold text-slate-200">本体 / 业务域</div>
            <div className="text-[9px] text-slate-500 font-mono mt-0.5">
              {displayOntologies.length} 个本体
            </div>
          </div>
        </div>
        <button
          onClick={() => {
            setFormCode('');
            setFormName('');
            setFormDescription('');
            setShowCreate(true);
          }}
          className="p-1.5 rounded-lg hover:bg-indigo-500/10 text-slate-400 hover:text-indigo-400 transition"
          title="创建本体"
        >
          <Plus size={14} />
        </button>
      </div>

      {/* 错误提示 */}
      {error && (
        <div className="px-3 py-2 bg-red-500/10 border-b border-red-500/20 text-[10px] text-red-400 flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError('')} className="hover:text-red-300"><X size={12} /></button>
        </div>
      )}

      {/* 卡片列表 */}
      <div className="flex-1 overflow-y-auto p-2 space-y-2">
        {loading && ontologies.length === 0 ? (
          <div className="flex items-center justify-center py-8 text-slate-500">
            <Loader2 size={16} className="animate-spin mr-2" />
            <span className="text-[11px]">加载中...</span>
          </div>
        ) : (
          displayOntologies.map((o) => {
            const Icon = ONTOLOGY_ICONS[o.id] || Layers;
            const isSelected = ontologyId === o.id;
            const count = entityCounts[o.id];

            return (
              <div key={o.id} className="relative group">
                <button
                  onClick={() => handleClick(o.id)}
                  className={`w-full text-left p-3 rounded-lg border transition-all duration-150
                    ${
                      isSelected
                        ? 'border-indigo-500 bg-indigo-500/10 shadow-[0_0_12px_rgba(99,102,241,0.15)]'
                        : 'border-[#1E293B] bg-[#1a2030] hover:border-[#334155] hover:bg-[#1e2635]'
                    }`}
                >
                  {/* 头部：图标 + 名称 */}
                  <div className="flex items-center gap-2.5 mb-2">
                    <div
                      className={`w-7 h-7 rounded-md flex items-center justify-center shrink-0 transition-colors
                        ${isSelected ? 'bg-indigo-500/20 text-indigo-400' : 'bg-[#2a3040] text-slate-400'}`}
                    >
                      <Icon className="w-3.5 h-3.5" />
                    </div>
                    <span
                      className={`text-xs font-semibold truncate flex-1 ${
                        isSelected ? 'text-indigo-300' : 'text-slate-200'
                      }`}
                    >
                      {o.name}
                    </span>
                  </div>

                  {/* 编码 */}
                  <div className="text-[9px] font-mono text-slate-500 mb-1">{o.code}</div>

                  {/* 实体数量 */}
                  <div className="flex items-center gap-1.5 text-[10px]">
                    <Layers className="w-3 h-3 text-slate-500" />
                    <span className="text-slate-400">
                      <span className="font-semibold text-slate-300">
                        {count ?? '—'}
                      </span>
                      <span className="text-slate-500 ml-0.5">个实体</span>
                    </span>
                  </div>

                  {/* 选中指示器 */}
                  {isSelected && (
                    <div className="mt-2 flex items-center gap-1 text-[9px] text-indigo-400">
                      <div className="w-1 h-1 rounded-full bg-indigo-400" />
                      当前选中
                    </div>
                  )}
                </button>

                {/* 操作按钮（hover 显示） */}
                <div className="absolute top-2 right-2 flex gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button
                    onClick={(e) => { e.stopPropagation(); openEdit(o); }}
                    className="p-1 rounded hover:bg-indigo-500/20 text-slate-500 hover:text-indigo-400 transition"
                    title="编辑"
                  >
                    <Pencil size={11} />
                  </button>
                  <button
                    onClick={(e) => { e.stopPropagation(); setDeletingId(o.id); }}
                    className="p-1 rounded hover:bg-red-500/20 text-slate-500 hover:text-red-400 transition"
                    title="删除"
                  >
                    <Trash2 size={11} />
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* ── 创建/编辑弹窗 ── */}
      {(showCreate || editingId) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => { setShowCreate(false); setEditingId(null); }}>
          <div className="bg-[#1a2030] border border-[#2a3040] rounded-xl p-5 w-[380px] shadow-2xl" onClick={e => e.stopPropagation()}>
            <h3 className="text-sm font-bold text-white mb-4">
              {editingId ? '编辑本体' : '创建本体'}
            </h3>
            <div className="space-y-3">
              {!editingId && (
                <input value={formCode} onChange={e => setFormCode(e.target.value)}
                  placeholder="编码 (英文, 必填)" className="w-full bg-[#0f1419] border border-[#2a3040] rounded px-3 py-2 text-xs text-slate-200 focus:border-indigo-500 outline-none" />
              )}
              {editingId && (
                <input value={formCode} readOnly
                  className="w-full bg-[#0b0e14] border border-[#1E293B] rounded px-3 py-2 text-xs text-slate-500 font-mono cursor-not-allowed" />
              )}
              <input value={formName} onChange={e => setFormName(e.target.value)}
                placeholder="名称 (必填)" className="w-full bg-[#0f1419] border border-[#2a3040] rounded px-3 py-2 text-xs text-slate-200 focus:border-indigo-500 outline-none" />
              <textarea value={formDescription} onChange={e => setFormDescription(e.target.value)}
                placeholder="描述 (可选)" rows={2} className="w-full bg-[#0f1419] border border-[#2a3040] rounded px-3 py-2 text-xs text-slate-200 resize-none focus:border-indigo-500 outline-none" />
            </div>
            <div className="flex gap-2 mt-4">
              <button onClick={editingId ? handleEdit : handleCreate} disabled={saving || !formName}
                className="flex-1 bg-indigo-600 text-white rounded-lg px-4 py-2 text-xs font-medium hover:bg-indigo-500 disabled:opacity-50 flex items-center justify-center gap-1.5">
                {saving ? <><Loader2 size={12} className="animate-spin" />保存中...</> : (editingId ? '保存修改' : '创建本体')}
              </button>
              <button onClick={() => { setShowCreate(false); setEditingId(null); }}
                className="px-4 py-2 bg-[#2a3040] text-slate-400 rounded-lg text-xs hover:bg-[#3a4050]">取消</button>
            </div>
          </div>
        </div>
      )}

      {/* ── 删除确认 ── */}
      {deletingId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setDeletingId(null)}>
          <div className="bg-[#1a2030] border border-[#2a3040] rounded-xl p-5 w-[360px] shadow-2xl" onClick={e => e.stopPropagation()}>
            <h3 className="text-sm font-bold text-white mb-2">删除本体</h3>
            <p className="text-xs text-slate-400 mb-4">
              确定要删除「{ontologies.find(o => o.id === deletingId)?.name || deletingId}」吗？删除后其下实体将无法访问。
            </p>
            <div className="flex gap-2">
              <button onClick={handleDelete} disabled={saving}
                className="flex-1 bg-red-600 text-white rounded-lg px-4 py-2 text-xs font-medium hover:bg-red-500 disabled:opacity-50 flex items-center justify-center gap-1.5">
                {saving ? <><Loader2 size={12} className="animate-spin" />删除中...</> : '确认删除'}
              </button>
              <button onClick={() => setDeletingId(null)}
                className="px-4 py-2 bg-[#2a3040] text-slate-400 rounded-lg text-xs hover:bg-[#3a4050]">取消</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
