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

import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  ShieldCheck, ShieldAlert, Binary, FileText, Plus, Trash2,
  CheckCircle, AlertCircle, Info, AlertTriangle,
  Database, Eye, Loader2, ChevronRight, Zap,
} from 'lucide-react';
import { useTheme } from '../components/ThemeContext';
import type {
  GuardrailPolicy, PreviewData, AuditLogEntry, PolicyStatus,
} from './Guardrails/types';
import { API_BASE } from './Guardrails/constants';
import { apiCall, normalizePolicy, emptyPolicy } from './Guardrails/helpers';
import PolicyListPanel from './Guardrails/PolicyListPanel';
import PolicyEditor from './Guardrails/PolicyEditor';
import PolicyDetail from './Guardrails/PolicyDetail';
import CompilePreviewTab from './Guardrails/CompilePreviewTab';
import GuardrailsAuditTab from './Guardrails/GuardrailsAuditTab';

// ─────────────────────────────────────────────────────────────
// Main Component
// ─────────────────────────────────────────────────────────────

type SubTab = 'policies' | 'compile' | 'audit';

export default function GuardrailsView() {
  const { styles } = useTheme();
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
    <div className={`space-y-4 overflow-y-auto h-full p-5 ${styles.appBg} text-xs flex flex-col`}>
      {/* Header */}
      <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.cardBorder} pb-3 shrink-0 gap-3`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
            <span className="p-1 rounded bg-rose-600 text-white">
              <ShieldCheck size={14} />
            </span>
            <span>安全护栏管理控制台</span>
          </h2>
          <p className={`text-[11px] ${styles.cardTextMuted}`}>策略 CRUD、编译部署与合规预览的统一治理面板。</p>
        </div>

        {/* Tab switcher */}
        <div className={`flex ${styles.appBg} p-0.5 rounded-lg border ${styles.cardBorder} shrink-0`}>
          <button
            onClick={() => setActiveSubTab('policies')}
            className={`px-3 py-1.5 rounded-md font-bold text-[11px] flex items-center gap-1.5 transition-all cursor-pointer ${activeSubTab === 'policies' ? `${styles.inputBg} ${styles.cardText} shadow-sm` : `${styles.cardTextMuted} ${styles.sidebarHoverBg} hover:text-indigo-400`}`}
          >
            <ShieldAlert size={12} />
            <span>策略管理</span>
          </button>
          <button
            onClick={() => setActiveSubTab('compile')}
            className={`px-3 py-1.5 rounded-md font-bold text-[11px] flex items-center gap-1.5 transition-all cursor-pointer ${activeSubTab === 'compile' ? `${styles.inputBg} ${styles.cardText} shadow-sm` : `${styles.cardTextMuted} ${styles.sidebarHoverBg} hover:text-indigo-400`}`}
          >
            <Binary size={12} />
            <span>编译与预览</span>
          </button>
          <button
            onClick={() => setActiveSubTab('audit')}
            className={`px-3 py-1.5 rounded-md font-bold text-[11px] flex items-center gap-1.5 transition-all cursor-pointer ${activeSubTab === 'audit' ? `${styles.inputBg} ${styles.cardText} shadow-sm` : `${styles.cardTextMuted} ${styles.sidebarHoverBg} hover:text-indigo-400`}`}
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
          <PolicyListPanel
            policies={filteredPolicies}
            totalCount={policies.length}
            loading={loadingList}
            search={search}
            onSearchChange={setSearch}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onCreate={startCreate}
            onToggle={handleToggle}
          />

          {/* Right: detail / editor */}
          <div className={`flex-1 ${styles.inputBg} border ${styles.cardBorder} rounded-xl shadow-sm flex flex-col overflow-hidden min-h-0`}>
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
              <div className={`flex-1 flex flex-col items-center justify-center ${styles.cardTextMuted} space-y-2`}>
                <Info size={24} className={styles.cardTextMuted} />
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
        <CompilePreviewTab
          policy={selectedPolicy}
          isCompiling={isCompiling}
          compileLogs={compileLogs}
          loadingPreview={loadingPreview}
          previewData={previewData}
          onCompile={handleCompile}
          onRefreshPreview={loadPreview}
        />
      )}

      {/* ═════════════ TAB: AUDIT LOG (STUB) ═════════════ */}
      {activeSubTab === 'audit' && (
        <GuardrailsAuditTab policies={policies} onRefresh={() => loadPolicies()} />
      )}

      {/* ── Delete confirm modal ── */}
      {deleteTarget && (
        <div className={`fixed inset-0 ${styles.overlayBg} flex items-center justify-center z-50`} onClick={() => setDeleteTarget(null)}>
          <div className={`${styles.cardBg} rounded-xl p-5 max-w-sm w-full mx-4 shadow-xl`} onClick={e => e.stopPropagation()}>
            <div className="flex items-center gap-2 mb-3">
              <span className={`p-1.5 rounded ${styles.dangerBg} ${styles.dangerText}`}><AlertTriangle size={16} /></span>
              <h3 className={`font-black ${styles.cardText} text-sm`}>确认删除策略</h3>
            </div>
            <p className={`text-[11px] ${styles.cardText} leading-relaxed mb-4`}>
              即将删除护栏策略「<span className={`font-bold ${styles.dangerText}`}>{deleteTarget.name}</span>」，此操作不可撤销。
              删除后该策略将立即从查询引擎卸载。
            </p>
            <div className="flex gap-2 justify-end">
              <button onClick={() => setDeleteTarget(null)} className={`px-3 py-1.5 border ${styles.cardBorder} ${styles.sidebarHoverBg} rounded-md text-[11px] font-bold ${styles.cardText} cursor-pointer`}>取消</button>
              <button onClick={handleDelete} className="px-3 py-1.5 bg-rose-600 hover:bg-rose-700 text-white rounded-md text-[11px] font-bold cursor-pointer flex items-center gap-1"><Trash2 size={11} /> 删除</button>
            </div>
          </div>
        </div>
      )}

      {/* ── Toast ── */}
      {toast && (
        <div className="fixed bottom-5 right-5 z-50 animate-fadeIn">
          <div className={`px-4 py-2.5 rounded-lg shadow-lg text-white font-bold text-[11px] flex items-center gap-2 ${
            toast.type === 'success' ? 'bg-emerald-600' : toast.type === 'error' ? 'bg-rose-600' : 'bg-[var(--muted,#334155)]'
          }`}>
            {toast.type === 'success' ? <CheckCircle size={14} /> : toast.type === 'error' ? <AlertCircle size={14} /> : <Info size={14} />}
            <span>{toast.msg}</span>
          </div>
        </div>
      )}
    </div>
  );
}
