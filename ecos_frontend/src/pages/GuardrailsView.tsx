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
  Info, AlertTriangle,
  Database, Eye, Loader2, ChevronRight, Zap,
} from 'lucide-react';
import { useTheme } from '../components/ThemeContext';
import { useLanguage } from '../components/LanguageContext';
import { showToastGlobal } from '../components/common/Toast';
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
  const { t } = useLanguage();
  // §4.5 P2（行230/249）：自建 toast（三套时长并存）已删除 → 统一走 common/Toast 全局通道。
  // 加载失败不再只靠 3.2s 短 toast（行245）：listError 错误态横幅 + 重试 + traceId 常驻展示。
  const showToast = useCallback((type: 'success' | 'info' | 'error', msg: string) => {
    showToastGlobal(type, msg);
  }, []);

  /** 从网关 ApiResponse 错误原文提取 traceId（后端实报才展示，不合成） */
  const extractTraceId = (raw: unknown): string | undefined => {
    const text = raw instanceof Error ? raw.message : String(raw ?? '');
    const m = text.match(/"traceId"\s*:\s*"([^"]+)"/);
    return m?.[1];
  };

  // ── State ──
  const [activeSubTab, setActiveSubTab] = useState<SubTab>('policies');
  const [policies, setPolicies] = useState<GuardrailPolicy[]>([]);
  const [loadingList, setLoadingList] = useState(false);
  const [listError, setListError] = useState<{ msg: string; traceId?: string } | null>(null);
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
    setListError(null);
    try {
      const raw = await apiCall<any>(API_BASE);
      const list: any[] = Array.isArray(raw) ? raw : (raw?.policies ?? raw?.items ?? raw?.list ?? []);
      const normalized = list.map(normalizePolicy);
      setPolicies(normalized);
      if (normalized.length > 0 && !selectedId) {
        setSelectedId(normalized[0].id);
      }
    } catch (e: any) {
      // §4.5 P1（行245）：失败 → 常驻错误态（message + traceId + 重试按钮），
      // 不再「置空 + 3.2s 短 toast」让错误与真空不可分
      setPolicies([]);
      setListError({ msg: e?.message || String(e), traceId: extractTraceId(e) });
    } finally {
      setLoadingList(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId, showToast, t]);

  useEffect(() => {
    loadPolicies();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── API: Create / Update ──
  const handleSave = async () => {
    if (!editing) return;
    if (!editing.name.trim()) {
      showToast('error', t('gwv.nameRequired'));
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
        showToast('success', t('gwv.created'));
        const np = normalizePolicy(created || { ...editing, id: created?.id ?? Date.now().toString() });
        setPolicies(prev => [...prev, np]);
        setSelectedId(np.id);
      } else if (formMode === 'edit' && editing.id) {
        await apiCall<any>(`${API_BASE}/${encodeURIComponent(editing.id)}`, {
          method: 'PUT',
          body: JSON.stringify(payload),
        });
        showToast('success', t('gwv.updated'));
        setPolicies(prev => prev.map(p => (p.id === editing.id ? { ...editing, status: 'DRAFT' } : p)));
      }
      setFormMode(null);
      setEditing(null);
    } catch (e: any) {
      showToast('error', t('gwv.saveFailed', { msg: e.message }));
    } finally {
      setSaving(false);
    }
  };

  // ── API: Delete ──
  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await apiCall<any>(`${API_BASE}/${encodeURIComponent(deleteTarget.id)}`, { method: 'DELETE' });
      showToast('success', t('gwv.deleted', { name: deleteTarget.name }));
      setPolicies(prev => prev.filter(p => p.id !== deleteTarget.id));
      if (selectedId === deleteTarget.id) setSelectedId(null);
    } catch (e: any) {
      showToast('error', t('gwv.deleteFailed', { msg: e.message }));
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
      showToast('success', t('gwv.stateUpdated'));
    } catch (e: any) {
      // rollback
      setPolicies(prev => prev.map(p => (p.id === policy.id ? policy : p)));
      showToast('error', t('gwv.updateFailed', { msg: e.message }));
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
      // §4.5 P0（行230）：引擎无日志时不再填 4 条罐头「编译日志」冒充引擎输出 ——
      // 显示诚实空态文案（真实日志条数 > 0 才逐条渲染）
      setCompileLogs(logs.length > 0 ? logs : [t('aiworkbench.gwv.compileNoEngineLogs')]);
      setPolicies(prev => prev.map(p => (p.id === policy.id ? { ...p, status, compiledAt, compileLogs: logs } : p)));
      showToast('success', t('gwv.compileSuccess'));
      // auto-refresh preview
      loadPreview({ ...policy, status, compiledAt });
    } catch (e: any) {
      setCompileLogs([t('gwv.compileLogFail', { msg: e.message })]);
      showToast('error', t('gwv.compileFail', { msg: e.message }));
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
      showToast('error', t('gwv.previewFailed', { msg: e.message }));
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
            <span className={`p-1 rounded ${styles.dangerBg} ${styles.dangerText}`}>
              <ShieldCheck size={14} />
            </span>
            <span>{t('gwv.title')}</span>
          </h2>
          <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('gwv.subtitle')}</p>
        </div>

        {/* Tab switcher */}
        <div className={`flex ${styles.appBg} p-0.5 rounded-lg border ${styles.cardBorder} shrink-0`}>
          <button
            type="button"
            onClick={() => setActiveSubTab('policies')}
            className={`px-3 py-1.5 rounded-md font-bold text-[11px] flex items-center gap-1.5 transition-all cursor-pointer ${activeSubTab === 'policies' ? `${styles.inputBg} ${styles.cardText} shadow-sm` : `${styles.cardTextMuted} ${styles.sidebarHoverBg} hover:${styles.accentText}`}`}
          >
            <ShieldAlert size={12} />
            <span>{t('gwv.tabPolicies')}</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveSubTab('compile')}
            className={`px-3 py-1.5 rounded-md font-bold text-[11px] flex items-center gap-1.5 transition-all cursor-pointer ${activeSubTab === 'compile' ? `${styles.inputBg} ${styles.cardText} shadow-sm` : `${styles.cardTextMuted} ${styles.sidebarHoverBg} hover:${styles.accentText}`}`}
          >
            <Binary size={12} />
            <span>{t('gwv.tabCompile')}</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveSubTab('audit')}
            className={`px-3 py-1.5 rounded-md font-bold text-[11px] flex items-center gap-1.5 transition-all cursor-pointer ${activeSubTab === 'audit' ? `${styles.inputBg} ${styles.cardText} shadow-sm` : `${styles.cardTextMuted} ${styles.sidebarHoverBg} hover:${styles.accentText}`}`}
          >
            <FileText size={12} />
            <span>{t('gwv.tabAudit')}</span>
          </button>
        </div>
      </div>

      {/* ═════════════ TAB: POLICIES (CRUD) ═════════════ */}
      {activeSubTab === 'policies' && (
        <div className="flex-1 flex flex-col min-h-0 gap-3">
          {/* §4.5 P1（行245）：加载失败 = 错误态横幅（message + traceId + 重试），与真空态可区分 */}
          {listError && (
            <div role="alert" className={`shrink-0 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 border ${styles.dangerBorder} ${styles.dangerBg} ${styles.dangerText} rounded-xl px-4 py-3 text-[11px]`}>
              <span className="min-w-0 font-semibold break-all">
                {t('gwv.loadFailed', { msg: listError.msg })}
                {listError.traceId && <span className="ml-2 font-mono opacity-70">traceId: {listError.traceId}</span>}
              </span>
              <button type="button" onClick={loadPolicies} className={`shrink-0 px-2 py-1 rounded border ${styles.dangerBorder} font-bold cursor-pointer`}>
                {t('network.retry')}
              </button>
            </div>
          )}
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
                <p className="font-bold">{t('gwv.emptyHint')}</p>
                <button type="button" onClick={startCreate} className={`${styles.accentText} font-bold text-[11px] hover:underline flex items-center gap-1`}>
                  <Plus size={11} /> {t('gwv.createNew')}
                </button>
              </div>
            )}
          </div>
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
              <h3 className={`font-black ${styles.cardText} text-sm`}>{t('gwv.deleteTitle')}</h3>
            </div>
            <p className={`text-[11px] ${styles.cardText} leading-relaxed mb-4`}>
              {t('gwv.deleteConfirm1')}「<span className={`font-bold ${styles.dangerText}`}>{deleteTarget.name}</span>」{t('gwv.deleteConfirm2')}
            </p>
            <div className="flex gap-2 justify-end">
              <button type="button" onClick={() => setDeleteTarget(null)} className={`px-3 py-1.5 border ${styles.cardBorder} ${styles.sidebarHoverBg} rounded-md text-[11px] font-bold ${styles.cardText} cursor-pointer`}>{t('common.cancel')}</button>
              <button type="button" onClick={handleDelete} className={`px-3 py-1.5 ${styles.dangerBg} ${styles.dangerText} rounded-md text-[11px] font-bold cursor-pointer flex items-center gap-1`}><Trash2 size={11} /> {t('common.delete')}</button>
            </div>
          </div>
        </div>
      )}

      {/* ── Toast：§4.5 P2 自建 toast 已删除，统一走 components/common/Toast 全局单源 ── */}
    </div>
  );
}
