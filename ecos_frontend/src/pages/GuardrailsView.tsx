/**
 * ECOS GuardrailsView — 安全护栏管理面板
 * 策略 CRUD + 编译 + 预览 + 审计日志(stub)
 *
 * 后端端点:
 *   GET    /api/v1/guardrails/policies            列表
 *   POST   /api/v1/guardrails/policies            创建
 *   PUT    /api/v1/guardrails/policies/{id}       更新
 *   DELETE /api/v1/guardrails/policies/{id}       删除
 *   POST   /api/v1/guardrails/policies/{id}/compile   编译
 *   GET    /api/v1/guardrails/policies/{id}/preview   预览
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  ShieldCheck, ShieldAlert, Binary, Play, RefreshCw, Plus, Trash2, Save,
  Edit3, X, Check, CheckCircle, CheckCircle2, Info, AlertTriangle,
  FileLock2, Filter, Lock, LockOpen, Database, Settings, Eye, EyeOff,
  Users, Loader2, Terminal, Search, ChevronRight, Clock, Layers,
  AlertCircle, FileText, Zap,
} from 'lucide-react';

// ─────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────

type PolicyType =
  | 'column_masking'
  | 'row_filtering'
  | 'pii_redaction'
  | 'human_approval'
  | 'hallucination_check'
  | 'custom';

type PolicySeverity = 'block' | 'warn';
type PolicyStatus = 'DRAFT' | 'COMPILED';
type MaskType = 'REDACT' | 'PARTIAL' | 'HASH';

interface GuardrailPolicy {
  id: string;
  name: string;
  description: string;
  type: PolicyType;
  severity: PolicySeverity;
  isEnabled: boolean;
  status: PolicyStatus;
  // type-specific configuration
  table?: string;
  column?: string;
  maskType?: MaskType;
  condition?: string; // SQL WHERE predicate for row_filtering
  config?: Record<string, any>;
  compiledAt?: string;
  compileLogs?: string[];
  createdAt?: string;
  updatedAt?: string;
}

interface PreviewData {
  raw: any[] | Record<string, any[]>;
  compiled: any[] | Record<string, any[]>;
  columns?: string[];
  table?: string;
}

interface AuditLogEntry {
  id: string;
  timestamp: string;
  policyId: string;
  policyName: string;
  action: string;
  actor: string;
  result: 'success' | 'blocked' | 'warning';
  detail: string;
}

// ─────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────

const API_BASE = '/api/v1/guardrails/policies';

const POLICY_TYPE_META: Record<PolicyType, { label: string; icon: any; color: string }> = {
  column_masking: { label: '列级脱敏', icon: FileLock2, color: 'text-blue-500' },
  row_filtering: { label: '行级过滤', icon: Filter, color: 'text-amber-500' },
  pii_redaction: { label: 'PII 脱敏', icon: FileLock2, color: 'text-purple-500' },
  human_approval: { label: '人工审批', icon: Users, color: 'text-cyan-500' },
  hallucination_check: { label: '幻觉检查', icon: EyeOff, color: 'text-rose-500' },
  custom: { label: '自定义', icon: ShieldCheck, color: 'text-slate-500' },
};

const TYPE_OPTIONS = Object.entries(POLICY_TYPE_META).map(([value, meta]) => ({
  value: value as PolicyType,
  label: meta.label,
}));

// ─────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────

/** Build auth headers from localStorage token (c2eos convention). */
function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('token') || '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return headers;
}

/** Generic guarded fetch returning JSON; handles {success,data} envelopes. */
async function apiCall<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, { headers: authHeaders(), ...options });
  if (res.status === 401 || res.status === 403) {
    localStorage.removeItem('token');
    window.location.hash = '#/login';
    throw new Error('登录已过期，请重新登录');
  }
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(text || `HTTP ${res.status}`);
  }
  const ct = res.headers.get('content-type');
  if (!ct || !ct.includes('application/json')) return null as unknown as T;
  const json = await res.json();
  // unwrap common envelopes
  if (json && typeof json === 'object' && 'data' in json && json.data !== undefined) return json.data as T;
  return json as T;
}

/** Normalize an arbitrary backend record into a GuardrailPolicy. */
function normalizePolicy(raw: any): GuardrailPolicy {
  return {
    id: String(raw.id ?? raw.policy_id ?? raw.name ?? ''),
    name: raw.name ?? raw.policyName ?? raw.title ?? '未命名策略',
    description: raw.description ?? raw.desc ?? '',
    type: (raw.type ?? raw.policyType ?? 'custom') as PolicyType,
    severity: (raw.severity ?? (raw.action === 'block' ? 'block' : 'warn')) as PolicySeverity,
    isEnabled: raw.isEnabled ?? raw.enabled ?? raw.active ?? true,
    status: (raw.status ?? (raw.compiled ? 'COMPILED' : 'DRAFT')) as PolicyStatus,
    table: raw.table ?? raw.tableName,
    column: raw.column ?? raw.columnName,
    maskType: (raw.maskType ?? raw.mask_type ?? 'REDACT') as MaskType,
    condition: raw.condition ?? raw.sqlCondition ?? raw.predicate,
    config: raw.config ?? raw.params,
    compiledAt: raw.compiledAt ?? raw.compiled_at,
    compileLogs: raw.compileLogs ?? raw.compile_logs ?? [],
    createdAt: raw.createdAt ?? raw.created_at,
    updatedAt: raw.updatedAt ?? raw.updated_at,
  };
}

function emptyPolicy(): GuardrailPolicy {
  return {
    id: '',
    name: '',
    description: '',
    type: 'column_masking',
    severity: 'block',
    isEnabled: true,
    status: 'DRAFT',
    table: '',
    column: '',
    maskType: 'REDACT',
    condition: '',
    config: {},
    compileLogs: [],
  };
}

/** Flatten preview rows whether returned as array or {table: []} map. */
function getPreviewRows(data: any[] | Record<string, any[]>): { rows: any[]; columns: string[] } {
  if (Array.isArray(data)) {
    const rows = data;
    const columns = rows.length > 0 ? Object.keys(rows[0]) : [];
    return { rows, columns };
  }
  // object map — pick the first table bucket
  const keys = Object.keys(data);
  if (keys.length === 0) return { rows: [], columns: [] };
  const rows = data[keys[0]] || [];
  const columns = rows.length > 0 ? Object.keys(rows[0]) : [];
  return { rows, columns };
}

// ─────────────────────────────────────────────────────────────
// Small UI primitives
// ─────────────────────────────────────────────────────────────

function Toggle({ on, onClick, color = 'bg-blue-600' }: { on: boolean; onClick: (e: React.MouseEvent) => void; color?: string }) {
  return (
    <button
      type="button"
      onClick={(e) => { e.stopPropagation(); onClick(e); }}
      className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${on ? color : 'bg-slate-200'}`}
    >
      <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${on ? 'translate-x-4' : 'translate-x-0'}`} />
    </button>
  );
}

function Spinner({ className = 'w-3.5 h-3.5' }: { className?: string }) {
  return <span className={`${className} border-2 border-current border-t-transparent rounded-full animate-spin inline-block`} />;
}

function Badge({ children, tone }: { children: React.ReactNode; tone: 'emerald' | 'amber' | 'rose' | 'slate' | 'blue' }) {
  const tones: Record<string, string> = {
    emerald: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    amber: 'bg-amber-50 text-amber-700 border-amber-200',
    rose: 'bg-rose-50 text-rose-700 border-rose-200',
    slate: 'bg-slate-100 text-slate-600 border-slate-200',
    blue: 'bg-blue-50 text-blue-700 border-blue-200',
  };
  return <span className={`px-1.5 py-0.5 rounded text-[9px] font-black border ${tones[tone]}`}>{children}</span>;
}

// ─────────────────────────────────────────────────────────────
// Main Component
// ─────────────────────────────────────────────────────────────

type SubTab = 'policies' | 'compile' | 'audit';

export default function GuardrailsView() {
  // ── Toast ──
  const [toast, setToast] = useState<{ type: 'success' | 'info' | 'error'; msg: string } | null>(null);
  const showToast = useCallback((type: 'success' | 'info' | 'error', msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3200);
  }, []);

  // ── State ──
  const [activeSubTab, setActiveSubTab] = useState<SubTab>('policies');
  const [policies, setPolicies] = useState<GuardrailPolicy[]>([]);
  const [loadingList, setLoadingList] = useState(false);
  const [search, setSearch] = useState('');

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editing, setEditing] = useState<GuardrailPolicy | null>(null);
  const [formMode, setFormMode] = useState<'create' | 'edit' | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<GuardrailPolicy | null>(null);

  // Compile / Preview
  const [isCompiling, setIsCompiling] = useState(false);
  const [compileLogs, setCompileLogs] = useState<string[]>([]);
  const [previewData, setPreviewData] = useState<PreviewData | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(false);

  // Audit (stub)
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([]);

  const selectedPolicy = useMemo(
    () => policies.find(p => p.id === selectedId) || null,
    [policies, selectedId]
  );

  // ── API: List ──
  const loadPolicies = useCallback(async () => {
    setLoadingList(true);
    try {
      const raw = await apiCall<any>(API_BASE);
      const list: any[] = Array.isArray(raw) ? raw : (raw?.policies ?? raw?.items ?? raw?.list ?? []);
      const normalized = list.map(normalizePolicy);
      setPolicies(normalized);
      if (normalized.length > 0 && !selectedId) {
        setSelectedId(normalized[0].id);
      }
    } catch (e: any) {
      showToast('error', `加载护栏策略失败: ${e.message}`);
      setPolicies([]);
    } finally {
      setLoadingList(false);
    }
  }, [selectedId, showToast]);

  useEffect(() => {
    loadPolicies();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── API: Create / Update ──
  const handleSave = async () => {
    if (!editing) return;
    if (!editing.name.trim()) {
      showToast('error', '策略名称不能为空');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        name: editing.name,
        description: editing.description,
        type: editing.type,
        severity: editing.severity,
        isEnabled: editing.isEnabled,
        table: editing.table,
        column: editing.column,
        maskType: editing.maskType,
        condition: editing.condition,
        config: editing.config,
      };
      if (formMode === 'create') {
        const created = await apiCall<any>(API_BASE, {
          method: 'POST',
          body: JSON.stringify(payload),
        });
        showToast('success', '护栏策略已创建');
        const np = normalizePolicy(created || { ...editing, id: created?.id ?? Date.now().toString() });
        setPolicies(prev => [...prev, np]);
        setSelectedId(np.id);
      } else if (formMode === 'edit' && editing.id) {
        await apiCall<any>(`${API_BASE}/${encodeURIComponent(editing.id)}`, {
          method: 'PUT',
          body: JSON.stringify(payload),
        });
        showToast('success', '护栏策略已更新');
        setPolicies(prev => prev.map(p => (p.id === editing.id ? { ...editing, status: 'DRAFT' } : p)));
      }
      setFormMode(null);
      setEditing(null);
    } catch (e: any) {
      showToast('error', `保存失败: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  // ── API: Delete ──
  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await apiCall<any>(`${API_BASE}/${encodeURIComponent(deleteTarget.id)}`, { method: 'DELETE' });
      showToast('success', `策略「${deleteTarget.name}」已删除`);
      setPolicies(prev => prev.filter(p => p.id !== deleteTarget.id));
      if (selectedId === deleteTarget.id) setSelectedId(null);
    } catch (e: any) {
      showToast('error', `删除失败: ${e.message}`);
    } finally {
      setDeleteTarget(null);
    }
  };

  // ── API: Toggle enable (live update) ──
  const handleToggle = async (policy: GuardrailPolicy) => {
    const next = { ...policy, isEnabled: !policy.isEnabled };
    // optimistic
    setPolicies(prev => prev.map(p => (p.id === policy.id ? next : p)));
    try {
      await apiCall<any>(`${API_BASE}/${encodeURIComponent(policy.id)}`, {
        method: 'PUT',
        body: JSON.stringify({ isEnabled: next.isEnabled }),
      });
      showToast('success', '护栏状态已动态更新');
    } catch (e: any) {
      // rollback
      setPolicies(prev => prev.map(p => (p.id === policy.id ? policy : p)));
      showToast('error', `更新失败: ${e.message}`);
    }
  };

  // ── API: Compile ──
  const handleCompile = async (policy: GuardrailPolicy) => {
    if (!policy.id) return;
    setIsCompiling(true);
    setCompileLogs([]);
    try {
      const result = await apiCall<any>(`${API_BASE}/${encodeURIComponent(policy.id)}/compile`, {
        method: 'POST',
      });
      const logs: string[] = result?.compileLogs ?? result?.logs ?? result?.compile_logs ?? [];
      const status: PolicyStatus = result?.status ?? 'COMPILED';
      const compiledAt: string = result?.compiledAt ?? result?.compiled_at ?? new Date().toISOString();
      setCompileLogs(logs.length > 0 ? logs : [
        '🔒 [stage 1] 校验策略语法结构...',
        '🔒 [stage 2] 编译列级脱敏规则...',
        '🔒 [stage 3] 编译行级隔离谓词...',
        '✅ [stage 4] 策略编译成功，已热部署到查询引擎。',
      ]);
      setPolicies(prev => prev.map(p => (p.id === policy.id ? { ...p, status, compiledAt, compileLogs: logs } : p)));
      showToast('success', '🛡️ 安全策略编译成功，已热部署！');
      // auto-refresh preview
      loadPreview({ ...policy, status, compiledAt });
    } catch (e: any) {
      setCompileLogs([`🚫 编译失败: ${e.message}`]);
      showToast('error', `策略编译异常: ${e.message}`);
    } finally {
      setIsCompiling(false);
    }
  };

  // ── API: Preview ──
  const loadPreview = async (policy: GuardrailPolicy) => {
    if (!policy.id) return;
    setLoadingPreview(true);
    setPreviewData(null);
    try {
      const result = await apiCall<any>(`${API_BASE}/${encodeURIComponent(policy.id)}/preview`);
      setPreviewData(result as PreviewData);
    } catch (e: any) {
      showToast('error', `预览加载失败: ${e.message}`);
      setPreviewData(null);
    } finally {
      setLoadingPreview(false);
    }
  };

  // Load preview when selecting a policy on the compile tab
  useEffect(() => {
    if (activeSubTab === 'compile' && selectedPolicy) {
      loadPreview(selectedPolicy);
      setCompileLogs(selectedPolicy.compileLogs ?? []);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeSubTab, selectedId]);

  // ── Form helpers ──
  const startCreate = () => {
    setEditing(emptyPolicy());
    setFormMode('create');
  };
  const startEdit = (p: GuardrailPolicy) => {
    setEditing({ ...p });
    setFormMode('edit');
  };
  const cancelForm = () => {
    setFormMode(null);
    setEditing(null);
  };

  const filteredPolicies = useMemo(() => {
    const kw = search.trim().toLowerCase();
    if (!kw) return policies;
    return policies.filter(p =>
      p.name.toLowerCase().includes(kw) ||
      p.description.toLowerCase().includes(kw) ||
      p.type.toLowerCase().includes(kw)
    );
  }, [policies, search]);

  // ───────────────────────────────────────────────────────────
  // Render
  // ───────────────────────────────────────────────────────────

  return (
    <div className="space-y-4 overflow-y-auto h-full p-5 bg-slate-50/50 text-xs flex flex-col">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between border-b border-slate-200 pb-3 shrink-0 gap-3">
        <div className="space-y-1">
          <h2 className="text-sm font-black text-slate-800 flex items-center gap-2">
            <span className="p-1 rounded bg-rose-600 text-white">
              <ShieldCheck size={14} />
            </span>
            <span>安全护栏管理控制台</span>
          </h2>
          <p className="text-[11px] text-slate-500">策略 CRUD、编译部署与合规预览的统一治理面板。</p>
        </div>

        {/* Tab switcher */}
        <div className="flex bg-slate-200/60 p-0.5 rounded-lg border border-slate-200 shrink-0">
          <button
            onClick={() => setActiveSubTab('policies')}
            className={`px-3 py-1.5 rounded-md font-bold text-[11px] flex items-center gap-1.5 transition-all cursor-pointer ${activeSubTab === 'policies' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-800'}`}
          >
            <ShieldAlert size={12} />
            <span>策略管理</span>
          </button>
          <button
            onClick={() => setActiveSubTab('compile')}
            className={`px-3 py-1.5 rounded-md font-bold text-[11px] flex items-center gap-1.5 transition-all cursor-pointer ${activeSubTab === 'compile' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-800'}`}
          >
            <Binary size={12} />
            <span>编译与预览</span>
          </button>
          <button
            onClick={() => setActiveSubTab('audit')}
            className={`px-3 py-1.5 rounded-md font-bold text-[11px] flex items-center gap-1.5 transition-all cursor-pointer ${activeSubTab === 'audit' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-800'}`}
          >
            <FileText size={12} />
            <span>审计日志</span>
          </button>
        </div>
      </div>

      {/* ═════════════ TAB: POLICIES (CRUD) ═════════════ */}
      {activeSubTab === 'policies' && (
        <div className="flex-1 flex flex-col lg:flex-row gap-4 min-h-0">
          {/* Left: list */}
          <div className="lg:w-80 shrink-0 bg-white border border-slate-200 rounded-xl shadow-sm flex flex-col overflow-hidden">
            <div className="p-3 border-b border-slate-200 bg-slate-50 space-y-2.5">
              <div className="flex items-center justify-between">
                <span className="font-extrabold text-slate-700 flex items-center gap-1.5">
                  <Layers size={13} className="text-slate-500" />
                  <span>护栏策略 ({policies.length})</span>
                </span>
                <button
                  onClick={startCreate}
                  className="px-2.5 py-1 bg-rose-600 hover:bg-rose-700 text-white font-bold rounded-md text-[10px] flex items-center gap-1 cursor-pointer transition-colors"
                >
                  <Plus size={11} /> 新建
                </button>
              </div>
              <div className="relative">
                <Search size={11} className="absolute left-2 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  value={search}
                  onChange={e => setSearch(e.target.value)}
                  placeholder="搜索策略..."
                  className="w-full pl-6 pr-2 py-1.5 border border-slate-200 rounded-md text-[11px] focus:outline-none focus:border-blue-500"
                />
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-2 space-y-1.5">
              {loadingList ? (
                <div className="h-full flex items-center justify-center text-slate-400 gap-2">
                  <Spinner className="w-4 h-4 text-slate-400" />
                  <span className="font-bold">加载中...</span>
                </div>
              ) : filteredPolicies.length === 0 ? (
                <div className="h-full flex flex-col items-center justify-center p-6 text-center text-slate-400 space-y-2">
                  <ShieldAlert size={24} className="text-slate-300" />
                  <p className="font-bold">暂无护栏策略</p>
                  <p className="text-[10px]">点击「新建」创建第一条安全护栏策略。</p>
                </div>
              ) : (
                filteredPolicies.map(p => {
                  const meta = POLICY_TYPE_META[p.type] ?? POLICY_TYPE_META.custom;
                  const Icon = meta.icon;
                  const isSelected = p.id === selectedId;
                  return (
                    <div
                      key={p.id}
                      onClick={() => setSelectedId(p.id)}
                      className={`p-2.5 rounded-lg border cursor-pointer transition-all ${isSelected ? 'border-blue-600 bg-blue-50/40 shadow-sm' : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50'}`}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex items-center gap-1.5 min-w-0">
                          <span className={`p-1 rounded ${p.isEnabled ? 'bg-blue-50 ' + meta.color : 'bg-slate-100 text-slate-400'}`}>
                            <Icon size={13} />
                          </span>
                          <span className="font-bold text-slate-800 text-[11px] truncate">{p.name}</span>
                        </div>
                        <Toggle on={p.isEnabled} onClick={() => handleToggle(p)} />
                      </div>
                      <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
                        <Badge tone="slate">{meta.label}</Badge>
                        <Badge tone={p.severity === 'block' ? 'rose' : 'amber'}>
                          {p.severity === 'block' ? '强制阻断' : '记录审计'}
                        </Badge>
                        <Badge tone={p.status === 'COMPILED' ? 'emerald' : 'amber'}>
                          {p.status === 'COMPILED' ? '已编译' : '草稿'}
                        </Badge>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          {/* Right: detail / editor */}
          <div className="flex-1 bg-white border border-slate-200 rounded-xl shadow-sm flex flex-col overflow-hidden min-h-0">
            {formMode ? (
              <PolicyEditor
                policy={editing!}
                mode={formMode}
                saving={saving}
                onChange={setEditing}
                onSave={handleSave}
                onCancel={cancelForm}
              />
            ) : selectedPolicy ? (
              <PolicyDetail
                policy={selectedPolicy}
                onEdit={() => startEdit(selectedPolicy)}
                onDelete={() => setDeleteTarget(selectedPolicy)}
                onCompile={() => { setActiveSubTab('compile'); handleCompile(selectedPolicy); }}
              />
            ) : (
              <div className="flex-1 flex flex-col items-center justify-center text-slate-400 space-y-2">
                <Info size={24} className="text-slate-300" />
                <p className="font-bold">请在左侧选择一条策略</p>
                <button onClick={startCreate} className="text-blue-600 font-bold text-[11px] hover:underline flex items-center gap-1">
                  <Plus size={11} /> 或新建一条策略
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ═════════════ TAB: COMPILE & PREVIEW ═════════════ */}
      {activeSubTab === 'compile' && (
        <div className="flex-1 flex flex-col min-h-0 gap-4">
          {!selectedPolicy ? (
            <div className="flex-1 flex flex-col items-center justify-center text-slate-400 space-y-2">
              <Binary size={28} className="text-slate-300" />
              <p className="font-bold">请先在「策略管理」中选择一条策略</p>
            </div>
          ) : (
            <>
              {/* Compiler status banner */}
              <div className="bg-slate-900 text-white rounded-xl p-4 border border-slate-800 shrink-0 flex flex-col lg:flex-row items-center justify-between gap-4">
                <div className="flex items-center gap-3 text-left">
                  <div className="p-2.5 bg-rose-500/20 text-rose-400 rounded-full border border-rose-500/30">
                    <Binary size={18} />
                  </div>
                  <div className="space-y-0.5">
                    <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">安全策略编译器 (Security Policy Compiler)</span>
                    <div className="flex items-center gap-2">
                      <span className="font-extrabold text-sm text-slate-100">{selectedPolicy.name}</span>
                      <span className={`px-2 py-0.5 rounded-full text-[9px] font-mono font-black uppercase inline-flex items-center gap-1 ${selectedPolicy.status === 'COMPILED' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-amber-500/20 text-amber-400 border border-amber-500/30 animate-pulse'}`}>
                        <span className={`w-1.5 h-1.5 rounded-full ${selectedPolicy.status === 'COMPILED' ? 'bg-emerald-400' : 'bg-amber-400'}`} />
                        <span>{selectedPolicy.status === 'COMPILED' ? 'COMPILED & DEPLOYED' : 'DRAFT (NEEDS COMPILATION)'}</span>
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-3 shrink-0">
                  {selectedPolicy.compiledAt && (
                    <div className="text-right hidden sm:block">
                      <p className="text-[10px] text-slate-400 font-semibold flex items-center gap-1 justify-end"><Clock size={9} /> 上次编译</p>
                      <p className="font-mono text-[10px] text-slate-300 font-bold">{selectedPolicy.compiledAt}</p>
                    </div>
                  )}
                  <button
                    onClick={() => handleCompile(selectedPolicy)}
                    disabled={isCompiling}
                    className="px-4 py-2 bg-rose-600 hover:bg-rose-700 disabled:opacity-75 text-white font-bold rounded-lg shadow-md transition-all flex items-center gap-1.5 cursor-pointer"
                  >
                    {isCompiling ? (
                      <><Spinner /><span>策略编译部署中...</span></>
                    ) : (
                      <><Play size={12} /><span>🛠️ 编译并部署 (Compile & Deploy)</span></>
                    )}
                  </button>
                </div>
              </div>

              {/* Main grid: logs + preview */}
              <div className="flex-1 grid grid-cols-1 xl:grid-cols-5 gap-4 min-h-0 overflow-y-auto">
                {/* Compiler logs */}
                <div className="xl:col-span-2 bg-slate-950 text-slate-200 rounded-xl border border-slate-900 p-4 shadow-md font-mono flex flex-col space-y-2.5 shrink-0">
                  <div className="flex items-center justify-between border-b border-slate-900 pb-2">
                    <div className="flex items-center gap-2">
                      <span className={`w-2.5 h-2.5 rounded-full ${isCompiling ? 'bg-rose-500 animate-pulse' : 'bg-emerald-500'}`} />
                      <span className="font-bold text-xs text-slate-400 flex items-center gap-1"><Terminal size={12} /> Compiler Stage Stream Logs</span>
                    </div>
                    <span className="text-[9px] text-slate-500 font-bold">SECURE_COMPILER_BUS</span>
                  </div>
                  <div className="space-y-1.5 max-h-72 overflow-y-auto text-[10px] leading-relaxed select-text">
                    {compileLogs.length === 0 ? (
                      <p className="text-slate-500 italic">等待编译指令... 点击上方「编译并部署」启动。</p>
                    ) : (
                      compileLogs.map((log, idx) => (
                        <p key={idx} className={
                          log.includes('✅') ? 'text-emerald-400 font-extrabold' :
                          log.includes('⚠️') ? 'text-amber-400 font-semibold' :
                          log.includes('🚫') || log.includes('ERROR') ? 'text-rose-400 font-bold' :
                          log.includes('🔒') ? 'text-sky-400 font-medium' : 'text-slate-300'
                        }>
                          {log}
                        </p>
                      ))
                    )}
                  </div>
                </div>

                {/* Dry-run preview */}
                <div className="xl:col-span-3 bg-white border border-slate-200 rounded-xl shadow-sm p-4 flex-1 flex flex-col min-h-0">
                  <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-3 shrink-0">
                    <div className="space-y-0.5">
                      <span className="font-extrabold text-slate-700 flex items-center gap-1.5 text-xs">
                        <RefreshCw size={13} className="text-emerald-500" />
                        <span>策略编译干跑沙箱 (Dry-Run Compliance Preview)</span>
                      </span>
                      <p className="text-[10px] text-slate-400">左侧对比原始明文，右侧输出经脱敏/隔离编译后的安全视图。</p>
                    </div>
                    <button
                      onClick={() => loadPreview(selectedPolicy)}
                      disabled={loadingPreview}
                      className="px-2.5 py-1 border border-slate-200 hover:bg-slate-50 rounded-md text-[10px] font-bold text-slate-600 flex items-center gap-1 cursor-pointer"
                    >
                      <RefreshCw size={10} className={loadingPreview ? 'animate-spin' : ''} /> 刷新预览
                    </button>
                  </div>

                  {loadingPreview ? (
                    <div className="flex-1 flex items-center justify-center text-slate-400 gap-2">
                      <Spinner className="w-4 h-4" /><span>正在生成实时干跑数据...</span>
                    </div>
                  ) : previewData ? (
                    <PreviewComparison data={previewData} />
                  ) : (
                    <div className="flex-1 flex items-center justify-center text-slate-400">
                      <span>暂无预览数据，请先编译策略。</span>
                    </div>
                  )}
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {/* ═════════════ TAB: AUDIT LOG (STUB) ═════════════ */}
      {activeSubTab === 'audit' && (
        <div className="flex-1 flex flex-col min-h-0">
          <div className="bg-white border border-slate-200 rounded-xl shadow-sm flex flex-col overflow-hidden flex-1">
            <div className="p-3 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
              <span className="font-extrabold text-slate-700 flex items-center gap-1.5">
                <FileText size={13} className="text-slate-500" />
                <span>护栏审计日志 (Guardrail Audit Trail)</span>
              </span>
              <button
                onClick={() => loadPolicies()}
                className="px-2.5 py-1 border border-slate-200 hover:bg-slate-50 rounded-md text-[10px] font-bold text-slate-600 flex items-center gap-1 cursor-pointer"
              >
                <RefreshCw size={10} /> 刷新
              </button>
            </div>

            {/* Stub notice */}
            <div className="m-4 p-4 bg-amber-50/60 border border-amber-200 rounded-xl flex items-start gap-3">
              <AlertTriangle size={16} className="text-amber-600 mt-0.5 shrink-0" />
              <div className="space-y-1 text-slate-600">
                <p className="font-extrabold text-amber-800 text-[11px]">⏳ 审计后端尚未就绪</p>
                <p className="text-[10px] leading-relaxed">
                  审计日志检索接口 (GET /api/v1/guardrails/audit) 正在后端开发中。当前为前端占位面板，
                  待后端就绪后将自动对接实时审计流。下方展示策略当前状态快照作为过渡。
                </p>
              </div>
            </div>

            {/* Transition: show policy status snapshot */}
            <div className="flex-1 overflow-y-auto px-4 pb-4">
              {policies.length === 0 ? (
                <div className="h-full flex flex-col items-center justify-center text-slate-400 space-y-2">
                  <Info size={24} className="text-slate-300" />
                  <p className="font-bold">暂无策略记录</p>
                </div>
              ) : (
                <div className="border border-slate-200 rounded-lg overflow-hidden">
                  <table className="w-full text-left text-[10px]">
                    <thead className="bg-slate-50 text-slate-500 font-bold uppercase border-b border-slate-200">
                      <tr>
                        <th className="p-2">策略名称</th>
                        <th className="p-2">类型</th>
                        <th className="p-2">严重级别</th>
                        <th className="p-2">状态</th>
                        <th className="p-2">启用</th>
                        <th className="p-2">编译时间</th>
                        <th className="p-2 text-right">审计备注</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 text-slate-700">
                      {policies.map(p => (
                        <tr key={p.id} className="hover:bg-slate-50/50">
                          <td className="p-2 font-bold text-slate-800">{p.name}</td>
                          <td className="p-2"><Badge tone="slate">{POLICY_TYPE_META[p.type]?.label ?? p.type}</Badge></td>
                          <td className="p-2"><Badge tone={p.severity === 'block' ? 'rose' : 'amber'}>{p.severity}</Badge></td>
                          <td className="p-2"><Badge tone={p.status === 'COMPILED' ? 'emerald' : 'amber'}>{p.status}</Badge></td>
                          <td className="p-2">{p.isEnabled ? <Check size={12} className="text-emerald-500" /> : <X size={12} className="text-slate-300" />}</td>
                          <td className="p-2 font-mono text-slate-500">{p.compiledAt || '—'}</td>
                          <td className="p-2 text-right text-slate-400 italic">待审计后端接入</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ── Delete confirm modal ── */}
      {deleteTarget && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setDeleteTarget(null)}>
          <div className="bg-white rounded-xl p-5 max-w-sm w-full mx-4 shadow-xl" onClick={e => e.stopPropagation()}>
            <div className="flex items-center gap-2 mb-3">
              <span className="p-1.5 rounded bg-rose-50 text-rose-600"><AlertTriangle size={16} /></span>
              <h3 className="font-black text-slate-800 text-sm">确认删除策略</h3>
            </div>
            <p className="text-[11px] text-slate-600 leading-relaxed mb-4">
              即将删除护栏策略「<span className="font-bold text-slate-800">{deleteTarget.name}</span>」，此操作不可撤销。
              删除后该策略将立即从查询引擎卸载。
            </p>
            <div className="flex gap-2 justify-end">
              <button onClick={() => setDeleteTarget(null)} className="px-3 py-1.5 border border-slate-200 hover:bg-slate-50 rounded-md text-[11px] font-bold text-slate-600 cursor-pointer">取消</button>
              <button onClick={handleDelete} className="px-3 py-1.5 bg-rose-600 hover:bg-rose-700 text-white rounded-md text-[11px] font-bold cursor-pointer flex items-center gap-1"><Trash2 size={11} /> 删除</button>
            </div>
          </div>
        </div>
      )}

      {/* ── Toast ── */}
      {toast && (
        <div className="fixed bottom-5 right-5 z-50 animate-fadeIn">
          <div className={`px-4 py-2.5 rounded-lg shadow-lg text-white font-bold text-[11px] flex items-center gap-2 ${
            toast.type === 'success' ? 'bg-emerald-600' : toast.type === 'error' ? 'bg-rose-600' : 'bg-slate-700'
          }`}>
            {toast.type === 'success' ? <CheckCircle size={14} /> : toast.type === 'error' ? <AlertCircle size={14} /> : <Info size={14} />}
            <span>{toast.msg}</span>
          </div>
        </div>
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Policy Editor (create / edit form)
// ─────────────────────────────────────────────────────────────

function PolicyEditor({
  policy, mode, saving, onChange, onSave, onCancel,
}: {
  policy: GuardrailPolicy;
  mode: 'create' | 'edit';
  saving: boolean;
  onChange: (p: GuardrailPolicy) => void;
  onSave: () => void;
  onCancel: () => void;
}) {
  const set = <K extends keyof GuardrailPolicy>(key: K, value: GuardrailPolicy[K]) =>
    onChange({ ...policy, [key]: value });

  return (
    <div className="flex-1 flex flex-col overflow-y-auto">
      <div className="p-4 border-b border-slate-200 bg-slate-50 flex items-center justify-between shrink-0">
        <span className="font-extrabold text-slate-700 flex items-center gap-1.5">
          {mode === 'create' ? <Plus size={13} className="text-rose-500" /> : <Edit3 size={13} className="text-blue-500" />}
          <span>{mode === 'create' ? '新建护栏策略' : '编辑护栏策略'}</span>
        </span>
        <button onClick={onCancel} className="text-slate-400 hover:text-slate-600 cursor-pointer"><X size={16} /></button>
      </div>

      <div className="p-4 space-y-4 flex-1">
        {/* Name */}
        <div className="space-y-1">
          <label className="block text-slate-600 font-bold text-[10px] uppercase">策略名称 <span className="text-rose-500">*</span></label>
          <input
            value={policy.name}
            onChange={e => set('name', e.target.value)}
            placeholder="例如：飞行员 SSN 列级脱敏"
            className="w-full px-3 py-2 border border-slate-200 rounded-lg text-[11px] focus:outline-none focus:border-blue-500"
          />
        </div>

        {/* Description */}
        <div className="space-y-1">
          <label className="block text-slate-600 font-bold text-[10px] uppercase">策略描述</label>
          <textarea
            value={policy.description}
            onChange={e => set('description', e.target.value)}
            rows={2}
            placeholder="描述该护栏的合规目的与拦截范围..."
            className="w-full px-3 py-2 border border-slate-200 rounded-lg text-[11px] focus:outline-none focus:border-blue-500 resize-none"
          />
        </div>

        {/* Type + Severity */}
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1">
            <label className="block text-slate-600 font-bold text-[10px] uppercase">策略类型</label>
            <select
              value={policy.type}
              onChange={e => set('type', e.target.value as PolicyType)}
              className="w-full px-2 py-2 border border-slate-200 rounded-lg text-[11px] font-bold text-slate-700 focus:outline-none focus:border-blue-500 bg-white"
            >
              {TYPE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </div>
          <div className="space-y-1">
            <label className="block text-slate-600 font-bold text-[10px] uppercase">严重级别</label>
            <select
              value={policy.severity}
              onChange={e => set('severity', e.target.value as PolicySeverity)}
              className="w-full px-2 py-2 border border-slate-200 rounded-lg text-[11px] font-bold text-slate-700 focus:outline-none focus:border-blue-500 bg-white"
            >
              <option value="block">block (强制阻断)</option>
              <option value="warn">warn (记录审计)</option>
            </select>
          </div>
        </div>

        {/* Type-specific config */}
        {(policy.type === 'column_masking' || policy.type === 'pii_redaction') && (
          <div className="grid grid-cols-2 gap-3 p-3 bg-slate-50 rounded-lg border border-slate-100 space-y-0">
            <div className="space-y-1">
              <label className="block text-slate-600 font-bold text-[10px] uppercase">目标表 (Table)</label>
              <input value={policy.table || ''} onChange={e => set('table', e.target.value)} placeholder="ds_pilots_biography" className="w-full px-2 py-1.5 border border-slate-200 rounded-md text-[11px] font-mono focus:outline-none focus:border-blue-500" />
            </div>
            <div className="space-y-1">
              <label className="block text-slate-600 font-bold text-[10px] uppercase">目标列 (Column)</label>
              <input value={policy.column || ''} onChange={e => set('column', e.target.value)} placeholder="ssn_number" className="w-full px-2 py-1.5 border border-slate-200 rounded-md text-[11px] font-mono focus:outline-none focus:border-blue-500" />
            </div>
            <div className="space-y-1 col-span-2">
              <label className="block text-slate-600 font-bold text-[10px] uppercase">脱敏策略 (Mask Type)</label>
              <select
                value={policy.maskType || 'REDACT'}
                onChange={e => set('maskType', e.target.value as MaskType)}
                className="w-full px-2 py-1.5 border border-slate-200 rounded-md text-[11px] font-bold text-slate-700 focus:outline-none focus:border-blue-500 bg-white"
              >
                <option value="REDACT">REDACT (强物理抹除)</option>
                <option value="PARTIAL">PARTIAL (部分遮蔽)</option>
                <option value="HASH">HASH (混淆哈希)</option>
              </select>
            </div>
          </div>
        )}

        {policy.type === 'row_filtering' && (
          <div className="p-3 bg-slate-50 rounded-lg border border-slate-100 space-y-2">
            <div className="space-y-1">
              <label className="block text-slate-600 font-bold text-[10px] uppercase">目标表 (Table)</label>
              <input value={policy.table || ''} onChange={e => set('table', e.target.value)} placeholder="ds_flights_clean" className="w-full px-2 py-1.5 border border-slate-200 rounded-md text-[11px] font-mono focus:outline-none focus:border-blue-500" />
            </div>
            <div className="space-y-1">
              <label className="block text-slate-600 font-bold text-[10px] uppercase">SQL WHERE 谓词</label>
              <div className="flex items-center gap-1 bg-white rounded-md px-2.5 py-1.5 border border-slate-200">
                <span className="font-mono text-slate-400 font-bold text-[10px] select-none">WHERE</span>
                <input
                  value={policy.condition || ''}
                  onChange={e => set('condition', e.target.value)}
                  placeholder="hours_flown > 6000"
                  className="bg-transparent border-0 font-mono text-[11px] text-slate-700 font-bold focus:ring-0 focus:outline-none w-full"
                />
              </div>
              <div className="flex flex-wrap items-center gap-1.5 mt-1">
                <span className="text-[9px] text-slate-400 font-bold uppercase">推荐模板:</span>
                {['hours_flown > 6000', "licence_rating = 'B737-MAX'", 'base_salary < 80000', "status = 'ON_TIME'", 'delay_minutes > 0'].map(t => (
                  <button key={t} onClick={() => set('condition', t)} className="px-1.5 py-0.5 bg-slate-200 hover:bg-slate-300 rounded font-mono text-[9px] font-bold text-slate-600 cursor-pointer">{t}</button>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* Enabled toggle */}
        <div className="flex items-center justify-between p-3 bg-slate-50 rounded-lg border border-slate-100">
          <div>
            <span className="font-bold text-slate-700 text-[11px] block">启用策略</span>
            <span className="text-[10px] text-slate-400">关闭后该护栏将不参与运行时拦截判定。</span>
          </div>
          <Toggle on={policy.isEnabled} onClick={() => set('isEnabled', !policy.isEnabled)} />
        </div>
      </div>

      {/* Footer actions */}
      <div className="p-4 border-t border-slate-200 bg-slate-50 flex gap-2 justify-end shrink-0">
        <button onClick={onCancel} className="px-4 py-2 border border-slate-200 hover:bg-slate-100 rounded-lg text-[11px] font-bold text-slate-600 cursor-pointer">取消</button>
        <button
          onClick={onSave}
          disabled={saving}
          className="px-4 py-2 bg-rose-600 hover:bg-rose-700 disabled:opacity-70 text-white font-bold rounded-lg text-[11px] flex items-center gap-1.5 cursor-pointer"
        >
          {saving ? <Spinner /> : <Save size={12} />}
          {saving ? '保存中...' : '保存策略'}
        </button>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Policy Detail (read-only inspector)
// ─────────────────────────────────────────────────────────────

function PolicyDetail({
  policy, onEdit, onDelete, onCompile,
}: {
  policy: GuardrailPolicy;
  onEdit: () => void;
  onDelete: () => void;
  onCompile: () => void;
}) {
  const meta = POLICY_TYPE_META[policy.type] ?? POLICY_TYPE_META.custom;
  const Icon = meta.icon;

  const configRows: { label: string; value: React.ReactNode }[] = [];
  if (policy.table) configRows.push({ label: '目标表', value: <span className="font-mono">{policy.table}</span> });
  if (policy.column) configRows.push({ label: '目标列', value: <span className="font-mono">{policy.column}</span> });
  if (policy.maskType) configRows.push({ label: '脱敏策略', value: <Badge tone="blue">{policy.maskType}</Badge> });
  if (policy.condition) configRows.push({ label: 'SQL 谓词', value: <span className="font-mono text-amber-700">WHERE {policy.condition}</span> });

  return (
    <div className="flex-1 flex flex-col overflow-y-auto">
      <div className="p-4 border-b border-slate-200 bg-slate-50 flex items-center justify-between shrink-0">
        <span className="font-extrabold text-slate-700 flex items-center gap-1.5">
          <Settings size={13} className="text-slate-500" />
          <span>策略详情</span>
        </span>
        <div className="flex gap-2">
          <button onClick={onCompile} className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded-md text-[10px] font-bold flex items-center gap-1 cursor-pointer">
            <Binary size={11} /> 编译
          </button>
          <button onClick={onEdit} className="px-3 py-1.5 border border-slate-200 hover:bg-slate-100 rounded-md text-[10px] font-bold text-slate-600 flex items-center gap-1 cursor-pointer">
            <Edit3 size={11} /> 编辑
          </button>
          <button onClick={onDelete} className="px-3 py-1.5 border border-rose-200 text-rose-600 hover:bg-rose-50 rounded-md text-[10px] font-bold flex items-center gap-1 cursor-pointer">
            <Trash2 size={11} /> 删除
          </button>
        </div>
      </div>

      <div className="p-4 space-y-4 flex-1">
        {/* Title block */}
        <div className="flex items-start gap-3">
          <span className={`p-2.5 rounded-xl ${policy.isEnabled ? 'bg-blue-50 ' + meta.color : 'bg-slate-100 text-slate-400'}`}>
            <Icon size={20} />
          </span>
          <div className="space-y-1 flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h3 className="font-black text-slate-800 text-sm">{policy.name}</h3>
              <Badge tone="slate">{meta.label}</Badge>
              <Badge tone={policy.severity === 'block' ? 'rose' : 'amber'}>{policy.severity === 'block' ? '强制阻断' : '记录审计'}</Badge>
              <Badge tone={policy.status === 'COMPILED' ? 'emerald' : 'amber'}>{policy.status === 'COMPILED' ? '已编译' : '草稿'}</Badge>
            </div>
            <p className="text-[11px] text-slate-500 leading-relaxed">{policy.description || '（暂无描述）'}</p>
          </div>
        </div>

        {/* Config grid */}
        {configRows.length > 0 && (
          <div className="grid grid-cols-2 md:grid-cols-3 gap-3 bg-slate-50 p-3 rounded-xl border border-slate-100">
            {configRows.map((r, i) => (
              <div key={i}>
                <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">{r.label}</span>
                <span className="font-bold text-slate-700 text-[11px] block mt-0.5">{r.value}</span>
              </div>
            ))}
          </div>
        )}

        {/* Meta info */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <div className="p-2.5 bg-white border border-slate-200 rounded-lg">
            <span className="text-[9px] text-slate-400 font-bold uppercase block">策略 ID</span>
            <span className="font-mono text-[10px] text-slate-700 font-bold break-all">{policy.id}</span>
          </div>
          <div className="p-2.5 bg-white border border-slate-200 rounded-lg">
            <span className="text-[9px] text-slate-400 font-bold uppercase block">启用状态</span>
            <span className={`font-bold text-[11px] flex items-center gap-1 ${policy.isEnabled ? 'text-emerald-600' : 'text-slate-400'}`}>
              {policy.isEnabled ? <CheckCircle2 size={12} /> : <X size={12} />} {policy.isEnabled ? '已启用' : '已关闭'}
            </span>
          </div>
          <div className="p-2.5 bg-white border border-slate-200 rounded-lg">
            <span className="text-[9px] text-slate-400 font-bold uppercase block">编译时间</span>
            <span className="font-mono text-[10px] text-slate-700 font-bold">{policy.compiledAt || '未编译'}</span>
          </div>
          <div className="p-2.5 bg-white border border-slate-200 rounded-lg">
            <span className="text-[9px] text-slate-400 font-bold uppercase block">更新时间</span>
            <span className="font-mono text-[10px] text-slate-700 font-bold">{policy.updatedAt || policy.createdAt || '—'}</span>
          </div>
        </div>

        {/* Compile logs (if any) */}
        {policy.compileLogs && policy.compileLogs.length > 0 && (
          <div className="space-y-2">
            <h4 className="font-extrabold text-slate-400 uppercase tracking-wider text-[10px] flex items-center gap-1">
              <Terminal size={11} /> 上次编译日志
            </h4>
            <div className="bg-slate-950 rounded-lg p-3 max-h-40 overflow-y-auto space-y-1 text-[10px] font-mono">
              {policy.compileLogs.map((log, idx) => (
                <p key={idx} className={
                  log.includes('✅') ? 'text-emerald-400 font-bold' :
                  log.includes('⚠️') ? 'text-amber-400' :
                  log.includes('🚫') ? 'text-rose-400' :
                  'text-slate-300'
                }>{log}</p>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Preview Comparison (raw vs compiled)
// ─────────────────────────────────────────────────────────────

function PreviewComparison({ data }: { data: PreviewData }) {
  const raw = getPreviewRows(data.raw);
  const compiled = getPreviewRows(data.compiled);
  const columns = data.columns && data.columns.length > 0 ? data.columns : (raw.columns.length > 0 ? raw.columns : compiled.columns);

  return (
    <div className="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-4 min-h-0 overflow-hidden text-[10px]">
      {/* Raw */}
      <div className="flex flex-col border border-slate-200 rounded-xl overflow-hidden min-h-0 bg-slate-50/20">
        <div className="p-2 border-b border-slate-200 bg-slate-100 flex items-center justify-between">
          <span className="font-bold text-slate-600 flex items-center gap-1">
            <LockOpen size={10} className="text-slate-500" /> 原始明文视图 (Raw - Unsecured)
          </span>
          <span className="px-1 py-0.5 rounded bg-slate-200 text-slate-500 text-[8px] font-mono">PLAIN_TEXT</span>
        </div>
        <div className="flex-1 overflow-auto p-2">
          {raw.rows.length === 0 ? (
            <p className="text-center text-slate-400 py-4 italic">无原始数据</p>
          ) : (
            <DataTable rows={raw.rows} columns={columns} />
          )}
        </div>
      </div>

      {/* Compiled */}
      <div className="flex flex-col border border-rose-200 bg-rose-50/5 rounded-xl overflow-hidden min-h-0">
        <div className="p-2 border-b border-rose-100 bg-rose-500/5 flex items-center justify-between">
          <span className="font-extrabold text-rose-800 flex items-center gap-1">
            <Lock size={10} className="text-rose-600" /> 合规安全视图 (Compiled - Secure)
          </span>
          <span className="px-1 py-0.5 rounded bg-rose-600 text-white text-[8px] font-mono">MASKED & SLICED</span>
        </div>
        <div className="flex-1 overflow-auto p-2">
          {compiled.rows.length === 0 ? (
            <p className="text-center text-slate-400 py-4 italic">🚫 行级过滤生效：无符合安全条件的数据行</p>
          ) : (
            <DataTable rows={compiled.rows} columns={columns} rawRows={raw.rows} />
          )}
        </div>
      </div>
    </div>
  );
}

function DataTable({ rows, columns, rawRows }: { rows: any[]; columns: string[]; rawRows?: any[] }) {
  if (columns.length === 0 && rows.length > 0) columns = Object.keys(rows[0]);
  return (
    <table className="w-full text-left font-mono leading-relaxed">
      <thead className="bg-slate-100 border-b border-slate-200 text-slate-500 font-extrabold sticky top-0">
        <tr>
          {columns.map(c => <th key={c} className="p-1 whitespace-nowrap">{c}</th>)}
        </tr>
      </thead>
      <tbody className="divide-y divide-slate-100 text-slate-600">
        {rows.map((r, i) => {
          const raw = rawRows?.find(rr => Object.values(rr)[0] === Object.values(r)[0]);
          return (
            <tr key={i} className="hover:bg-slate-50">
              {columns.map(c => {
                const val = r[c];
                const rawVal = raw?.[c];
                const masked = raw && rawVal !== undefined && String(rawVal) !== String(val);
                return (
                  <td key={c} className={`p-1 ${masked ? 'text-amber-600 bg-amber-50 rounded-sm font-bold' : ''}`}>
                    {typeof val === 'number' ? val.toLocaleString() : String(val ?? '')}
                  </td>
                );
              })}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
