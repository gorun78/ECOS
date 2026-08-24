/**
 * DetectTab — 安全策略管理中心
 * 4个子Tab: ABAC策略评估 / RLS策略CRUD / CLS策略CRUD / 脱敏规则管理
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  Shield, Lock, Columns3, EyeOff, Plus, Trash2, Edit3, X,
  Search, AlertTriangle, Loader2, CheckCircle, XCircle,
} from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import {
  fetchRlsPolicies, createRlsPolicy, updateRlsPolicy, deleteRlsPolicy,
  fetchClsPolicies, createClsPolicy, updateClsPolicy, deleteClsPolicy,
  evaluateAbacPolicy, maskData,
  fetchAbacPolicies, createAbacPolicy, updateAbacPolicy, deleteAbacPolicy,
  fetchCatalogTables,
  type RlsPolicy, type ClsPolicy, type AbacPolicy, type CatalogTable,
} from '../../api';

// ── Sub-tab definitions ──────────────────────────────────────
type SubTabId = 'abac-crud' | 'abac' | 'rls' | 'cls' | 'masking';

interface SubTabDef {
  id: SubTabId;
  labelKey: string;
  icon: typeof Shield;
}

const SUB_TABS: SubTabDef[] = [
  { id: 'abac-crud', labelKey: 'sec.abac.crud.title', icon: Shield },
  { id: 'abac', labelKey: 'sec.abac.evaluate', icon: Shield },
  { id: 'rls', labelKey: 'sec.rls.title', icon: Lock },
  { id: 'cls', labelKey: 'sec.cls.title', icon: Columns3 },
  { id: 'masking', labelKey: 'sec.mask.title', icon: EyeOff },
];

// ── Shared styles ────────────────────────────────────────────
function inputClasses(styles: any) {
  return `w-full px-3 py-2 rounded-lg text-sm ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder} focus:outline-none focus:ring-2 focus:ring-blue-500/50`;
}

function btnPrimary(styles: any) {
  return `px-4 py-2 rounded-lg text-sm font-medium text-white ${styles.accentBg} ${styles.accentHover} transition-colors cursor-pointer disabled:opacity-50`;
}

function btnSecondary(styles: any) {
  return `px-4 py-2 rounded-lg text-sm font-medium ${styles.cardBg} ${styles.cardText} border ${styles.cardBorder} hover:${styles.sidebarHoverBg} transition-colors cursor-pointer`;
}

function badgeClasses(active: boolean, styles: any) {
  return active
    ? `px-2 py-0.5 rounded-full text-xs font-medium ${styles.badgeBg} ${styles.badgeText}`
    : `px-2 py-0.5 rounded-full text-xs font-medium bg-gray-200/50 text-gray-500 dark:bg-gray-700/50 dark:text-gray-400`;
}

// ── ABAC Policy Evaluator ────────────────────────────────────
function AbacEvaluator({ t, locale, styles }: { t: (k: string) => string; locale: string; styles: any }) {
  const [userId, setUserId] = useState('');
  const [roles, setRoles] = useState('');
  const [resourceType, setResourceType] = useState('');
  const [resourceId, setResourceId] = useState('');
  const [action, setAction] = useState('read');
  const [evaluating, setEvaluating] = useState(false);
  const [result, setResult] = useState<{ allowed: boolean; message?: string; details?: any } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleEvaluate = async () => {
    if (!userId || !resourceType || !resourceId) return;
    setEvaluating(true);
    setError(null);
    setResult(null);
    try {
      const roleList = roles.split(',').map(r => r.trim()).filter(Boolean);
      const res = await evaluateAbacPolicy({
        subject: { userId, roles: roleList },
        resource: { type: resourceType, id: resourceId },
        action,
      });
      setResult(res);
    } catch (e: any) {
      setError(e.message || 'Evaluation failed');
    } finally {
      setEvaluating(false);
    }
  };

  const actions = ['read', 'write', 'delete', 'execute'];

  return (
    <div className="p-6 space-y-6 overflow-auto h-full">
      <div>
        <h3 className={`text-lg font-bold mb-1 ${styles.cardText}`}>{t('sec.abac.evaluate')}</h3>
        <p className={`text-sm ${styles.muted}`}>{t('sec.abac.evaluate.desc')}</p>
      </div>

      {/* Subject section */}
      <div className={`p-5 rounded-xl ${styles.cardBg} border ${styles.cardBorder}`}>
        <h4 className={`text-sm font-semibold mb-3 ${styles.cardText}`}>{t('sec.abac.subject')}</h4>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.userId')}</label>
            <input
              type="text"
              value={userId}
              onChange={e => setUserId(e.target.value)}
              placeholder="user_001"
              className={inputClasses(styles)}
            />
          </div>
          <div>
            <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.roles')}</label>
            <input
              type="text"
              value={roles}
              onChange={e => setRoles(e.target.value)}
              placeholder="admin, analyst"
              className={inputClasses(styles)}
            />
          </div>
        </div>
      </div>

      {/* Resource section */}
      <div className={`p-5 rounded-xl ${styles.cardBg} border ${styles.cardBorder}`}>
        <h4 className={`text-sm font-semibold mb-3 ${styles.cardText}`}>{t('sec.abac.resource')}</h4>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.resourceType')}</label>
            <input
              type="text"
              value={resourceType}
              onChange={e => setResourceType(e.target.value)}
              placeholder="table"
              className={inputClasses(styles)}
            />
          </div>
          <div>
            <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.resourceId')}</label>
            <input
              type="text"
              value={resourceId}
              onChange={e => setResourceId(e.target.value)}
              placeholder="customer_data"
              className={inputClasses(styles)}
            />
          </div>
        </div>
      </div>

      {/* Action selector */}
      <div className={`p-5 rounded-xl ${styles.cardBg} border ${styles.cardBorder}`}>
        <h4 className={`text-sm font-semibold mb-3 ${styles.cardText}`}>{t('sec.abac.action')}</h4>
        <div className="flex gap-2">
          {actions.map(a => (
            <button
              key={a}
              onClick={() => setAction(a)}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors cursor-pointer ${
                action === a
                  ? `${styles.accentBg} text-white`
                  : `${styles.cardBg} ${styles.cardText} border ${styles.cardBorder} hover:${styles.sidebarHoverBg}`
              }`}
            >
              {t(`sec.abac.action.${a}`)}
            </button>
          ))}
        </div>
      </div>

      {/* Evaluate button */}
      <button
        onClick={handleEvaluate}
        disabled={evaluating || !userId || !resourceType || !resourceId}
        className={btnPrimary(styles)}
      >
        {evaluating ? (
          <span className="flex items-center gap-2"><Loader2 size={14} className="animate-spin" />{locale === 'zh' ? '评估中...' : 'Evaluating...'}</span>
        ) : (
          t('sec.abac.evalBtn')
        )}
      </button>

      {/* Error */}
      {error && (
        <div className={`p-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm dark:bg-red-950/30 dark:border-red-800 dark:text-red-400 flex items-start gap-2`}>
          <AlertTriangle size={16} className="shrink-0 mt-0.5" />
          <span>{error}</span>
        </div>
      )}

      {/* Result */}
      {result && (
        <div className={`p-5 rounded-xl ${styles.cardBg} border ${styles.cardBorder} space-y-4`}>
          <h4 className={`text-sm font-semibold ${styles.cardText}`}>{t('sec.abac.result')}</h4>
          <div className="flex items-center gap-3">
            {result.allowed ? (
              <CheckCircle size={24} className="text-green-500" />
            ) : (
              <XCircle size={24} className="text-red-500" />
            )}
            <span className={`text-lg font-bold ${result.allowed ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
              {result.allowed ? t('sec.abac.allow') : t('sec.abac.deny')}
            </span>
          </div>
          {result.message && (
            <p className={`text-sm ${styles.muted}`}>{result.message}</p>
          )}
          {result.details && (
            <div>
              <p className={`text-xs font-medium mb-2 ${styles.muted}`}>{t('sec.abac.details')}</p>
              <pre className={`p-3 rounded-lg text-xs font-mono overflow-auto max-h-40 ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder}`}>
                {JSON.stringify(result.details, null, 2)}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ── RLS Policy Manager ───────────────────────────────────────
function RlsPolicyManager({ t, locale, styles }: { t: (k: string) => string; locale: string; styles: any }) {
  const [policies, setPolicies] = useState<RlsPolicy[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState('');
  const [searchText, setSearchText] = useState('');

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<RlsPolicy | null>(null);
  const [form, setForm] = useState({ policyName: '', tableName: '', filterExpression: '', roles: '', status: 'ACTIVE', description: '' });
  const [saving, setSaving] = useState(false);

  // Delete confirmation
  const [deleteTarget, setDeleteTarget] = useState<RlsPolicy | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetchRlsPolicies(keyword ? { tableName: keyword } : {});
      setPolicies(res.data || []);
    } catch (e: any) {
      setError(e.message || 'Failed to load');
      setPolicies([]);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => { load(); }, [load]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setKeyword(searchText);
  };

  const openCreate = () => {
    setEditing(null);
    setForm({ policyName: '', tableName: '', filterExpression: '', roles: '', status: 'ACTIVE', description: '' });
    setModalOpen(true);
  };

  const openEdit = (p: RlsPolicy) => {
    setEditing(p);
    setForm({
      policyName: p.policyName || '',
      tableName: p.tableName || '',
      filterExpression: p.filterExpression || '',
      roles: (p.roles || []).join(', '),
      status: p.status || 'ACTIVE',
      description: p.description || '',
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const roleList = form.roles.split(',').map(r => r.trim()).filter(Boolean);
      const data = { ...form, roles: roleList };
      if (editing && editing.id) {
        await updateRlsPolicy(editing.id as number, data);
      } else {
        await createRlsPolicy(data);
      }
      setModalOpen(false);
      load();
    } catch (e: any) {
      setError(e.message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget?.id) return;
    try {
      await deleteRlsPolicy(deleteTarget.id as number);
      setDeleteTarget(null);
      load();
    } catch (e: any) {
      setError(e.message || 'Delete failed');
    }
  };

  return (
    <div className="p-6 space-y-4 overflow-auto h-full">
      <div className="flex items-center justify-between">
        <div>
          <h3 className={`text-lg font-bold ${styles.cardText}`}>{t('sec.rls.title')}</h3>
          <p className={`text-xs ${styles.muted}`}>{locale === 'zh' ? '管理行级安全过滤策略' : 'Manage row-level security filter policies'}</p>
        </div>
        <button onClick={openCreate} className={`flex items-center gap-1.5 ${btnPrimary(styles)}`}>
          <Plus size={14} />{t('sec.rls.create')}
        </button>
      </div>

      {/* Search bar */}
      <form onSubmit={handleSearch} className="flex gap-2">
        <div className="relative flex-1">
          <Search size={14} className={`absolute left-3 top-1/2 -translate-y-1/2 ${styles.muted}`} />
          <input
            type="text"
            value={searchText}
            onChange={e => setSearchText(e.target.value)}
            placeholder={locale === 'zh' ? '搜索表名...' : 'Search table name...'}
            className={`${inputClasses(styles)} pl-9`}
          />
        </div>
        <button type="submit" className={btnSecondary(styles)}>{t('common.search')}</button>
      </form>

      {/* Error */}
      {error && (
        <div className={`p-3 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm dark:bg-red-950/30 dark:border-red-800 dark:text-red-400 flex items-start gap-2`}>
          <AlertTriangle size={14} className="shrink-0 mt-0.5" />
          <span>{error}</span>
          <button onClick={() => setError(null)} className="ml-auto"><X size={14} /></button>
        </div>
      )}

      {/* Table */}
      {loading ? (
        <div className="flex items-center justify-center py-16">
          <Loader2 size={24} className={`animate-spin ${styles.muted}`} />
        </div>
      ) : policies.length === 0 ? (
        <div className={`text-center py-16 ${styles.muted}`}>
          <Shield size={40} className="mx-auto mb-3 opacity-30" />
          <p className="text-sm">{t('sec.rls.empty')}</p>
        </div>
      ) : (
        <div className={`rounded-xl border ${styles.cardBorder} overflow-hidden`}>
          <table className="w-full text-sm">
            <thead className={`${styles.cardBg} border-b ${styles.cardBorder}`}>
              <tr>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.rls.col.policyName')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.rls.col.tableName')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.rls.col.filter')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.rls.col.roles')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.rls.col.status')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.rls.col.action')}</th>
              </tr>
            </thead>
            <tbody>
              {policies.map(p => (
                <tr key={p.id} className={`border-t ${styles.cardBorder} hover:${styles.sidebarHoverBg}`}>
                  <td className={`px-4 py-3 font-medium ${styles.cardText}`}>{p.policyName}</td>
                  <td className={`px-4 py-3 ${styles.cardText}`}><code className={`text-xs px-1.5 py-0.5 rounded ${styles.inputBg}`}>{p.tableName}</code></td>
                  <td className={`px-4 py-3 ${styles.cardText}`}><code className={`text-xs px-1.5 py-0.5 rounded ${styles.inputBg}`}>{p.filterExpression}</code></td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {(p.roles || []).map(r => (
                        <span key={r} className={badgeClasses(true, styles)}>{r}</span>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={badgeClasses(p.status === 'ACTIVE', styles)}>
                      {p.status === 'ACTIVE' ? t('sec.rls.status.active') : t('sec.rls.status.inactive')}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-1.5">
                      <button onClick={() => openEdit(p)} className={`p-1.5 rounded hover:${styles.sidebarHoverBg} ${styles.muted} cursor-pointer`}>
                        <Edit3 size={14} />
                      </button>
                      <button onClick={() => setDeleteTarget(p)} className="p-1.5 rounded hover:bg-red-50 text-red-500 cursor-pointer">
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create/Edit Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setModalOpen(false)}>
          <div
            className={`w-full max-w-lg mx-4 rounded-xl ${styles.cardBg} border ${styles.cardBorder} shadow-xl`}
            onClick={e => e.stopPropagation()}
          >
            <div className={`flex items-center justify-between px-5 py-4 border-b ${styles.cardBorder}`}>
              <h3 className={`font-bold ${styles.cardText}`}>{editing ? t('sec.rls.edit') : t('sec.rls.create')}</h3>
              <button onClick={() => setModalOpen(false)} className={`${styles.muted} hover:${styles.cardText} cursor-pointer`}><X size={18} /></button>
            </div>
            <div className="p-5 space-y-4">
              <div>
                <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.rls.policyName')}</label>
                <input type="text" value={form.policyName} onChange={e => setForm(f => ({ ...f, policyName: e.target.value }))} className={inputClasses(styles)} />
              </div>
              <div>
                <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.rls.tableName')}</label>
                <TableSelect value={form.tableName} onChange={v => setForm(f => ({ ...f, tableName: v }))} styles={styles} />
              </div>
              <div>
                <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.rls.filterExpression')}</label>
                <textarea rows={3} value={form.filterExpression} onChange={e => setForm(f => ({ ...f, filterExpression: e.target.value }))} className={inputClasses(styles)} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.rls.roles')}</label>
                  <input type="text" value={form.roles} onChange={e => setForm(f => ({ ...f, roles: e.target.value }))} placeholder="admin, analyst" className={inputClasses(styles)} />
                </div>
                <div>
                  <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.rls.status')}</label>
                  <select value={form.status} onChange={e => setForm(f => ({ ...f, status: e.target.value }))} className={inputClasses(styles)}>
                    <option value="ACTIVE">{t('sec.rls.status.active')}</option>
                    <option value="INACTIVE">{t('sec.rls.status.inactive')}</option>
                  </select>
                </div>
              </div>
              <div>
                <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.rls.desc')}</label>
                <textarea rows={2} value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} className={inputClasses(styles)} />
              </div>
            </div>
            <div className={`flex justify-end gap-2 px-5 py-4 border-t ${styles.cardBorder}`}>
              <button onClick={() => setModalOpen(false)} className={btnSecondary(styles)}>{t('common.cancel')}</button>
              <button onClick={handleSave} disabled={saving || !form.policyName || !form.tableName} className={btnPrimary(styles)}>
                {saving ? <Loader2 size={14} className="animate-spin" /> : t('common.save')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setDeleteTarget(null)}>
          <div className={`w-full max-w-sm mx-4 rounded-xl ${styles.cardBg} border ${styles.cardBorder} shadow-xl p-6`} onClick={e => e.stopPropagation()}>
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 rounded-full bg-red-100 dark:bg-red-950/40"><AlertTriangle size={20} className="text-red-600" /></div>
              <div>
                <h4 className={`font-bold ${styles.cardText}`}>{t('common.delete')}</h4>
                <p className={`text-sm ${styles.muted}`}>{t('sec.rls.delete.confirm').replace('{name}', deleteTarget.policyName)}</p>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setDeleteTarget(null)} className={btnSecondary(styles)}>{t('common.cancel')}</button>
              <button onClick={handleDelete} className="px-4 py-2 rounded-lg text-sm font-medium text-white bg-red-600 hover:bg-red-700 transition-colors cursor-pointer">{t('common.delete')}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ── CLS Policy Manager ───────────────────────────────────────
function ClsPolicyManager({ t, locale, styles }: { t: (k: string) => string; locale: string; styles: any }) {
  const [policies, setPolicies] = useState<ClsPolicy[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState('');
  const [searchText, setSearchText] = useState('');

  // Modal
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ClsPolicy | null>(null);
  const [form, setForm] = useState({ policyName: '', tableName: '', visibleColumns: '', blockedColumns: '', roles: '', status: 'ACTIVE', description: '' });
  const [saving, setSaving] = useState(false);

  // Delete confirmation
  const [deleteTarget, setDeleteTarget] = useState<ClsPolicy | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetchClsPolicies(keyword ? { tableName: keyword } : {});
      setPolicies(res.data || []);
    } catch (e: any) {
      setError(e.message || 'Failed to load');
      setPolicies([]);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => { load(); }, [load]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setKeyword(searchText);
  };

  const openCreate = () => {
    setEditing(null);
    setForm({ policyName: '', tableName: '', visibleColumns: '', blockedColumns: '', roles: '', status: 'ACTIVE', description: '' });
    setModalOpen(true);
  };

  const openEdit = (p: ClsPolicy) => {
    setEditing(p);
    setForm({
      policyName: p.policyName || '',
      tableName: p.tableName || '',
      visibleColumns: (p.visibleColumns || []).join(', '),
      blockedColumns: (p.blockedColumns || []).join(', '),
      roles: (p.roles || []).join(', '),
      status: p.status || 'ACTIVE',
      description: p.description || '',
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const visibleList = form.visibleColumns.split(',').map(c => c.trim()).filter(Boolean);
      const blockedList = form.blockedColumns.split(',').map(c => c.trim()).filter(Boolean);
      const roleList = form.roles.split(',').map(r => r.trim()).filter(Boolean);
      const data = {
        policyName: form.policyName,
        tableName: form.tableName,
        visibleColumns: visibleList,
        blockedColumns: blockedList,
        roles: roleList,
        status: form.status,
        description: form.description,
      };
      if (editing && editing.id) {
        await updateClsPolicy(editing.id, data);
      } else {
        await createClsPolicy(data);
      }
      setModalOpen(false);
      load();
    } catch (e: any) {
      setError(e.message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget?.id) return;
    try {
      await deleteClsPolicy(deleteTarget.id);
      setDeleteTarget(null);
      load();
    } catch (e: any) {
      setError(e.message || 'Delete failed');
    }
  };

  return (
    <div className="p-6 space-y-4 overflow-auto h-full">
      <div className="flex items-center justify-between">
        <div>
          <h3 className={`text-lg font-bold ${styles.cardText}`}>{t('sec.cls.title')}</h3>
          <p className={`text-xs ${styles.muted}`}>{locale === 'zh' ? '管理列级安全配置策略' : 'Manage column-level security policies'}</p>
        </div>
        <button onClick={openCreate} className={`flex items-center gap-1.5 ${btnPrimary(styles)}`}>
          <Plus size={14} />{t('sec.cls.create')}
        </button>
      </div>

      {/* Search */}
      <form onSubmit={handleSearch} className="flex gap-2">
        <div className="relative flex-1">
          <Search size={14} className={`absolute left-3 top-1/2 -translate-y-1/2 ${styles.muted}`} />
          <input type="text" value={searchText} onChange={e => setSearchText(e.target.value)} placeholder={locale === 'zh' ? '搜索表名...' : 'Search table name...'} className={`${inputClasses(styles)} pl-9`} />
        </div>
        <button type="submit" className={btnSecondary(styles)}>{t('common.search')}</button>
      </form>

      {/* Error */}
      {error && (
        <div className={`p-3 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm dark:bg-red-950/30 dark:border-red-800 dark:text-red-400 flex items-start gap-2`}>
          <AlertTriangle size={14} className="shrink-0 mt-0.5" /><span>{error}</span>
          <button onClick={() => setError(null)} className="ml-auto"><X size={14} /></button>
        </div>
      )}

      {/* Table */}
      {loading ? (
        <div className="flex items-center justify-center py-16"><Loader2 size={24} className={`animate-spin ${styles.muted}`} /></div>
      ) : policies.length === 0 ? (
        <div className={`text-center py-16 ${styles.muted}`}>
          <Columns3 size={40} className="mx-auto mb-3 opacity-30" />
          <p className="text-sm">{t('sec.cls.empty')}</p>
        </div>
      ) : (
        <div className={`rounded-xl border ${styles.cardBorder} overflow-hidden`}>
          <table className="w-full text-sm">
            <thead className={`${styles.cardBg} border-b ${styles.cardBorder}`}>
              <tr>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.cls.col.policyName')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.cls.col.tableName')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.cls.col.visible')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.cls.col.blocked')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.cls.col.roles')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.cls.col.status')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.rls.col.action')}</th>
              </tr>
            </thead>
            <tbody>
              {policies.map(p => (
                <tr key={p.id} className={`border-t ${styles.cardBorder} hover:${styles.sidebarHoverBg}`}>
                  <td className={`px-4 py-3 font-medium ${styles.cardText}`}>{p.policyName}</td>
                  <td className={`px-4 py-3 ${styles.cardText}`}><code className={`text-xs px-1.5 py-0.5 rounded ${styles.inputBg}`}>{p.tableName}</code></td>
                  <td className={`px-4 py-3 ${styles.cardText}`}>
                    <div className="flex flex-wrap gap-1">{(p.visibleColumns || []).map(c => <span key={c} className={`text-xs px-1.5 py-0.5 rounded bg-green-50 text-green-700 dark:bg-green-950/30 dark:text-green-400`}>{c}</span>)}</div>
                  </td>
                  <td className={`px-4 py-3 ${styles.cardText}`}>
                    <div className="flex flex-wrap gap-1">{(p.blockedColumns || []).map(c => <span key={c} className={`text-xs px-1.5 py-0.5 rounded bg-red-50 text-red-700 dark:bg-red-950/30 dark:text-red-400`}>{c}</span>)}</div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">{(p.roles || []).map(r => <span key={r} className={badgeClasses(true, styles)}>{r}</span>)}</div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={badgeClasses(p.status === 'ACTIVE', styles)}>
                      {p.status === 'ACTIVE' ? t('sec.rls.status.active') : t('sec.rls.status.inactive')}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-1.5">
                      <button onClick={() => openEdit(p)} className={`p-1.5 rounded hover:${styles.sidebarHoverBg} ${styles.muted} cursor-pointer`}>
                        <Edit3 size={14} />
                      </button>
                      <button onClick={() => setDeleteTarget(p)} className="p-1.5 rounded hover:bg-red-50 text-red-500 cursor-pointer">
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create/Edit Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setModalOpen(false)}>
          <div className={`w-full max-w-lg mx-4 rounded-xl ${styles.cardBg} border ${styles.cardBorder} shadow-xl`} onClick={e => e.stopPropagation()}>
            <div className={`flex items-center justify-between px-5 py-4 border-b ${styles.cardBorder}`}>
              <h3 className={`font-bold ${styles.cardText}`}>{editing ? t('sec.cls.edit') : t('sec.cls.create')}</h3>
              <button onClick={() => setModalOpen(false)} className={`${styles.muted} hover:${styles.cardText} cursor-pointer`}><X size={18} /></button>
            </div>
            <div className="p-5 space-y-4">
              <div>
                <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.cls.policyName')}</label>
                <input type="text" value={form.policyName} onChange={e => setForm(f => ({ ...f, policyName: e.target.value }))} className={inputClasses(styles)} />
              </div>
              <div>
                <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.cls.tableName')}</label>
                <TableSelect value={form.tableName} onChange={v => setForm(f => ({ ...f, tableName: v }))} styles={styles} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.cls.visibleColumns')}</label>
                  <input type="text" value={form.visibleColumns} onChange={e => setForm(f => ({ ...f, visibleColumns: e.target.value }))} placeholder="id, name, email" className={inputClasses(styles)} />
                </div>
                <div>
                  <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.cls.blockedColumns')}</label>
                  <input type="text" value={form.blockedColumns} onChange={e => setForm(f => ({ ...f, blockedColumns: e.target.value }))} placeholder="salary, ssn" className={inputClasses(styles)} />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.cls.roles')}</label>
                  <input type="text" value={form.roles} onChange={e => setForm(f => ({ ...f, roles: e.target.value }))} placeholder="admin, analyst" className={inputClasses(styles)} />
                </div>
                <div>
                  <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.cls.status')}</label>
                  <select value={form.status} onChange={e => setForm(f => ({ ...f, status: e.target.value }))} className={inputClasses(styles)}>
                    <option value="ACTIVE">{t('sec.rls.status.active')}</option>
                    <option value="INACTIVE">{t('sec.rls.status.inactive')}</option>
                  </select>
                </div>
              </div>
              <div>
                <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.rls.desc')}</label>
                <textarea rows={2} value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} className={inputClasses(styles)} />
              </div>
            </div>
            <div className={`flex justify-end gap-2 px-5 py-4 border-t ${styles.cardBorder}`}>
              <button onClick={() => setModalOpen(false)} className={btnSecondary(styles)}>{t('common.cancel')}</button>
              <button onClick={handleSave} disabled={saving || !form.policyName || !form.tableName} className={btnPrimary(styles)}>
                {saving ? <Loader2 size={14} className="animate-spin" /> : t('common.save')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setDeleteTarget(null)}>
          <div className={`w-full max-w-sm mx-4 rounded-xl ${styles.cardBg} border ${styles.cardBorder} shadow-xl p-6`} onClick={e => e.stopPropagation()}>
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 rounded-full bg-red-100 dark:bg-red-950/40"><AlertTriangle size={20} className="text-red-600" /></div>
              <div>
                <h4 className={`font-bold ${styles.cardText}`}>{t('common.delete')}</h4>
                <p className={`text-sm ${styles.muted}`}>{t('sec.cls.delete.confirm').replace('{name}', deleteTarget.policyName)}</p>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setDeleteTarget(null)} className={btnSecondary(styles)}>{t('common.cancel')}</button>
              <button onClick={handleDelete} className="px-4 py-2 rounded-lg text-sm font-medium text-white bg-red-600 hover:bg-red-700 transition-colors cursor-pointer">{t('common.delete')}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Masking Rule Manager ─────────────────────────────────────
function MaskingRuleManager({ t, locale, styles }: { t: (k: string) => string; locale: string; styles: any }) {
  const [input, setInput] = useState('');
  const [maskType, setMaskType] = useState('SHA256');
  const [masking, setMasking] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const maskTypes = [
    { id: 'SHA256', key: 'sec.mask.type.sha256', color: 'from-blue-500 to-cyan-500', icon: '🔐' },
    { id: 'PHONE', key: 'sec.mask.type.phone', color: 'from-green-500 to-emerald-500', icon: '📱' },
    { id: 'EMAIL', key: 'sec.mask.type.email', color: 'from-purple-500 to-pink-500', icon: '📧' },
    { id: 'ID_CARD', key: 'sec.mask.type.idcard', color: 'from-orange-500 to-yellow-500', icon: '🪪' },
    { id: 'AMOUNT', key: 'sec.mask.type.amount', color: 'from-red-500 to-rose-500', icon: '💰' },
  ];

  const handleMask = async () => {
    if (!input.trim()) return;
    setMasking(true);
    setError(null);
    setResult(null);
    try {
      const res = await maskData({ input: input.trim(), maskType });
      setResult(res.masked);
    } catch (e: any) {
      setError(e.message || 'Masking failed');
    } finally {
      setMasking(false);
    }
  };

  // Demo fallback when backend is unavailable
  const demoMask = (text: string, type: string): string => {
    switch (type) {
      case 'SHA256': return text.length > 8 ? text.substring(0, 4) + '...' + text.substring(text.length - 4) : '****';
      case 'PHONE': return text.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
      case 'EMAIL': return text.replace(/(.{2}).*(@.*)/, '$1***$2');
      case 'ID_CARD': return text.replace(/(\d{6})\d{8}(\d{4})/, '$1********$2');
      case 'AMOUNT': return '¥***.**';
      default: return '****';
    }
  };

  const handleDemoMask = () => {
    if (!input.trim()) return;
    setResult(demoMask(input.trim(), maskType));
  };

  return (
    <div className="p-6 space-y-6 overflow-auto h-full">
      <div>
        <h3 className={`text-lg font-bold mb-1 ${styles.cardText}`}>{t('sec.mask.title')}</h3>
        <p className={`text-sm ${styles.muted}`}>{t('sec.mask.desc')}</p>
      </div>

      {/* Mask type cards */}
      <div className="grid grid-cols-5 gap-3">
        {maskTypes.map(mt => (
          <button
            key={mt.id}
            onClick={() => { setMaskType(mt.id); setResult(null); setError(null); }}
            className={`p-4 rounded-xl text-center transition-all cursor-pointer border-2 ${
              maskType === mt.id
                ? `${styles.accentBorder} ${styles.cardBg}`
                : `border-transparent ${styles.cardBg} opacity-70 hover:opacity-100`
            }`}
          >
            <div className="text-2xl mb-1">{mt.icon}</div>
            <div className={`text-xs font-medium ${styles.cardText}`}>{t(mt.key)}</div>
          </button>
        ))}
      </div>

      {/* Input */}
      <div className={`p-5 rounded-xl ${styles.cardBg} border ${styles.cardBorder}`}>
        <label className={`block text-sm font-medium mb-2 ${styles.cardText}`}>{t('sec.mask.input')}</label>
        <textarea
          rows={3}
          value={input}
          onChange={e => { setInput(e.target.value); setResult(null); }}
          placeholder={t('sec.mask.placeholder')}
          className={inputClasses(styles)}
        />
      </div>

      {/* Buttons */}
      <div className="flex gap-2">
        <button onClick={handleMask} disabled={masking || !input.trim()} className={btnPrimary(styles)}>
          {masking ? <Loader2 size={14} className="animate-spin" /> : t('sec.mask.btn')}
        </button>
        <button onClick={handleDemoMask} disabled={!input.trim()} className={btnSecondary(styles)}>
          {locale === 'zh' ? '本地演示' : 'Local Demo'}
        </button>
      </div>

      {/* Error */}
      {error && (
        <div className={`p-3 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm dark:bg-red-950/30 dark:border-red-800 dark:text-red-400 flex items-start gap-2`}>
          <AlertTriangle size={14} className="shrink-0 mt-0.5" /><span>{error}</span>
        </div>
      )}

      {/* Result */}
      {result !== null && (
        <div className={`p-5 rounded-xl ${styles.cardBg} border ${styles.cardBorder}`}>
          <h4 className={`text-sm font-medium mb-3 ${styles.cardText}`}>{t('sec.mask.result')}</h4>
          <div className={`p-4 rounded-lg ${styles.inputBg} border ${styles.inputBorder}`}>
            <p className={`font-mono text-sm ${styles.cardText}`}>{result}</p>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Table Select (from data catalog) ─────────────────────────
function TableSelect({ value, onChange, styles, placeholder }: {
  value: string; onChange: (v: string) => void; styles: any; placeholder?: string;
}) {
  const [tables, setTables] = useState<CatalogTable[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');

  useEffect(() => {
    setLoading(true);
    fetchCatalogTables().then(t => { setTables(t); setLoading(false); }).catch(() => setLoading(false));
  }, []);

  const filtered = searchText
    ? tables.filter(t => t.resourceName.toLowerCase().includes(searchText.toLowerCase()))
    : tables;

  return (
    <div className="relative">
      <input
        type="text"
        value={searchText || value}
        onChange={e => { setSearchText(e.target.value); if (!e.target.value) onChange(''); }}
        onFocus={() => setSearchText('')}
        placeholder={placeholder || (loading ? '加载表列表...' : '输入表名或搜索...')}
        className={inputClasses(styles)}
      />
      {searchText && filtered.length > 0 && (
        <div className={`absolute z-50 mt-1 w-full max-h-48 overflow-auto rounded-lg ${styles.cardBg} border ${styles.cardBorder} shadow-lg`}>
          {filtered.slice(0, 20).map(t => (
            <button
              key={t.catalogId}
              onClick={() => { onChange(t.resourceName); setSearchText(''); }}
              className={`w-full text-left px-3 py-2 text-sm hover:${styles.sidebarHoverBg} ${styles.cardText} cursor-pointer`}
            >
              <span className="font-medium">{t.resourceName}</span>
              <span className={`ml-2 text-xs ${styles.muted}`}>{t.resourceType}</span>
              {t.orgName && <span className={`ml-2 text-xs ${styles.muted}`}>{t.orgName}</span>}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

// ── ABAC Policy CRUD Manager ────────────────────────────────
function AbacPolicyManager({ t, locale, styles }: { t: (k: string) => string; locale: string; styles: any }) {
  const [policies, setPolicies] = useState<AbacPolicy[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState('');
  const [searchText, setSearchText] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AbacPolicy | null>(null);
  const [form, setForm] = useState({ name: '', resource: '', action: 'read', effect: 'allow', conditionExpression: '', priority: 100 });
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<AbacPolicy | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetchAbacPolicies(keyword, 1, 50);
      setPolicies(res.data || []);
    } catch (e: any) {
      setError(e.message || 'Failed to load');
      setPolicies([]);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => { load(); }, [load]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setKeyword(searchText);
  };

  const openCreate = () => {
    setEditing(null);
    setForm({ name: '', resource: '', action: 'read', effect: 'allow', conditionExpression: '', priority: 100 });
    setModalOpen(true);
  };

  const openEdit = (p: AbacPolicy) => {
    setEditing(p);
    setForm({
      name: p.name || '',
      resource: p.resource || '',
      action: p.action || 'read',
      effect: p.effect || 'allow',
      conditionExpression: p.conditionExpression || '',
      priority: p.priority ?? 100,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      if (editing && editing.id) {
        await updateAbacPolicy(editing.id, form);
      } else {
        await createAbacPolicy(form);
      }
      setModalOpen(false);
      load();
    } catch (e: any) {
      setError(e.message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget?.id) return;
    try {
      await deleteAbacPolicy(deleteTarget.id);
      setDeleteTarget(null);
      load();
    } catch (e: any) {
      setError(e.message || 'Delete failed');
    }
  };

  const effects = [
    { id: 'allow', key: 'sec.abac.effect.allow' },
    { id: 'deny', key: 'sec.abac.effect.deny' },
  ];
  const actions = ['read', 'write', 'delete', 'execute'];

  return (
    <div className="p-6 space-y-4 overflow-auto h-full">
      <div className="flex items-center justify-between">
        <div>
          <h3 className={`text-lg font-bold ${styles.cardText}`}>{t('sec.abac.crud.title')}</h3>
          <p className={`text-xs ${styles.muted}`}>{locale === 'zh' ? '管理ABAC属性访问控制策略' : 'Manage ABAC attribute-based access control policies'}</p>
        </div>
        <button onClick={openCreate} className={`flex items-center gap-1.5 ${btnPrimary(styles)}`}>
          <Plus size={14} />{t('sec.abac.crud.create')}
        </button>
      </div>

      <form onSubmit={handleSearch} className="flex gap-2">
        <div className="relative flex-1">
          <Search size={14} className={`absolute left-3 top-1/2 -translate-y-1/2 ${styles.muted}`} />
          <input type="text" value={searchText} onChange={e => setSearchText(e.target.value)} placeholder={locale === 'zh' ? '搜索策略名...' : 'Search policy name...'} className={`${inputClasses(styles)} pl-9`} />
        </div>
        <button type="submit" className={btnSecondary(styles)}>{t('common.search')}</button>
      </form>

      {error && (
        <div className={`p-3 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm dark:bg-red-950/30 dark:border-red-800 dark:text-red-400 flex items-start gap-2`}>
          <AlertTriangle size={14} className="shrink-0 mt-0.5" /><span>{error}</span>
          <button onClick={() => setError(null)} className="ml-auto"><X size={14} /></button>
        </div>
      )}

      {loading ? (
        <div className="flex items-center justify-center py-16"><Loader2 size={24} className={`animate-spin ${styles.muted}`} /></div>
      ) : policies.length === 0 ? (
        <div className={`text-center py-16 ${styles.muted}`}>
          <Shield size={40} className="mx-auto mb-3 opacity-30" />
          <p className="text-sm">{t('sec.abac.crud.empty')}</p>
        </div>
      ) : (
        <div className={`rounded-xl border ${styles.cardBorder} overflow-hidden`}>
          <table className="w-full text-sm">
            <thead className={`${styles.cardBg} border-b ${styles.cardBorder}`}>
              <tr>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.abac.crud.col.name')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.abac.crud.col.resource')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.abac.crud.col.action')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.abac.crud.col.effect')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.abac.crud.col.condition')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.abac.crud.col.priority')}</th>
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('sec.rls.col.action')}</th>
              </tr>
            </thead>
            <tbody>
              {policies.map(p => (
                <tr key={p.id} className={`border-t ${styles.cardBorder} hover:${styles.sidebarHoverBg}`}>
                  <td className={`px-4 py-3 font-medium ${styles.cardText}`}>{p.name}</td>
                  <td className={`px-4 py-3 ${styles.cardText}`}><code className={`text-xs px-1.5 py-0.5 rounded ${styles.inputBg}`}>{p.resource}</code></td>
                  <td className={`px-4 py-3 ${styles.cardText}`}>{p.action}</td>
                  <td className="px-4 py-3">
                    <span className={badgeClasses(p.effect === 'allow', styles)}>{p.effect === 'allow' ? t('sec.abac.effect.allow') : t('sec.abac.effect.deny')}</span>
                  </td>
                  <td className={`px-4 py-3 ${styles.cardText}`}><code className={`text-xs px-1.5 py-0.5 rounded ${styles.inputBg}`}>{p.conditionExpression || '-'}</code></td>
                  <td className={`px-4 py-3 ${styles.cardText}`}>{p.priority}</td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-1.5">
                      <button onClick={() => openEdit(p)} className={`p-1.5 rounded hover:${styles.sidebarHoverBg} ${styles.muted} cursor-pointer`}><Edit3 size={14} /></button>
                      <button onClick={() => setDeleteTarget(p)} className="p-1.5 rounded hover:bg-red-50 text-red-500 cursor-pointer"><Trash2 size={14} /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setModalOpen(false)}>
          <div className={`w-full max-w-lg mx-4 rounded-xl ${styles.cardBg} border ${styles.cardBorder} shadow-xl`} onClick={e => e.stopPropagation()}>
            <div className={`flex items-center justify-between px-5 py-4 border-b ${styles.cardBorder}`}>
              <h3 className={`font-bold ${styles.cardText}`}>{editing ? t('sec.abac.crud.edit') : t('sec.abac.crud.create')}</h3>
              <button onClick={() => setModalOpen(false)} className={`${styles.muted} hover:${styles.cardText} cursor-pointer`}><X size={18} /></button>
            </div>
            <div className="p-5 space-y-4">
              <div>
                <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.crud.col.name')}</label>
                <input type="text" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} className={inputClasses(styles)} />
              </div>
              <div>
                <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.crud.col.resource')}</label>
                <input type="text" value={form.resource} onChange={e => setForm(f => ({ ...f, resource: e.target.value }))} placeholder="table:td_user" className={inputClasses(styles)} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.crud.col.action')}</label>
                  <select value={form.action} onChange={e => setForm(f => ({ ...f, action: e.target.value }))} className={inputClasses(styles)}>
                    {actions.map(a => <option key={a} value={a}>{a}</option>)}
                  </select>
                </div>
                <div>
                  <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.crud.col.effect')}</label>
                  <select value={form.effect} onChange={e => setForm(f => ({ ...f, effect: e.target.value }))} className={inputClasses(styles)}>
                    {effects.map(e => <option key={e.id} value={e.id}>{t(e.key)}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.crud.col.condition')}</label>
                <input type="text" value={form.conditionExpression} onChange={e => setForm(f => ({ ...f, conditionExpression: e.target.value }))} placeholder="role:admin" className={inputClasses(styles)} />
              </div>
              <div>
                <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.crud.col.priority')}</label>
                <input type="number" value={form.priority} onChange={e => setForm(f => ({ ...f, priority: parseInt(e.target.value) || 0 }))} className={inputClasses(styles)} />
              </div>
            </div>
            <div className={`flex justify-end gap-2 px-5 py-4 border-t ${styles.cardBorder}`}>
              <button onClick={() => setModalOpen(false)} className={btnSecondary(styles)}>{t('common.cancel')}</button>
              <button onClick={handleSave} disabled={saving || !form.name} className={btnPrimary(styles)}>
                {saving ? <Loader2 size={14} className="animate-spin" /> : t('common.save')}
              </button>
            </div>
          </div>
        </div>
      )}

      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setDeleteTarget(null)}>
          <div className={`w-full max-w-sm mx-4 rounded-xl ${styles.cardBg} border ${styles.cardBorder} shadow-xl p-6`} onClick={e => e.stopPropagation()}>
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 rounded-full bg-red-100 dark:bg-red-950/40"><AlertTriangle size={20} className="text-red-600" /></div>
              <div>
                <h4 className={`font-bold ${styles.cardText}`}>{t('common.delete')}</h4>
                <p className={`text-sm ${styles.muted}`}>{t('sec.abac.crud.delete.confirm').replace('{name}', deleteTarget.name)}</p>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setDeleteTarget(null)} className={btnSecondary(styles)}>{t('common.cancel')}</button>
              <button onClick={handleDelete} className="px-4 py-2 rounded-lg text-sm font-medium text-white bg-red-600 hover:bg-red-700 transition-colors cursor-pointer">{t('common.delete')}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Main DetectTab ───────────────────────────────────────────
export default function DetectTab() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const [activeSubTab, setActiveSubTab] = useState<SubTabId>('abac-crud');

  return (
    <div className="h-full flex flex-col">
      {/* Sub-tab navigation */}
      <div className={`flex items-center border-b ${styles.appBorder} px-4 shrink-0`}>
        {SUB_TABS.map(st => {
          const Icon = st.icon;
          const isActive = activeSubTab === st.id;
          return (
            <button
              key={st.id}
              onClick={() => setActiveSubTab(st.id)}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-all duration-150 cursor-pointer
                ${isActive
                  ? `${styles.accentText} border-b-current`
                  : `${styles.muted} border-b-transparent hover:${styles.sidebarHoverBg}`
                }`}
            >
              <Icon size={16} />
              <span>{t(st.labelKey)}</span>
            </button>
          );
        })}
      </div>

      {/* Sub-tab content */}
      <div className="flex-1 overflow-hidden">
        {activeSubTab === 'abac-crud' && <AbacPolicyManager t={t} locale={locale} styles={styles} />}
        {activeSubTab === 'abac' && <AbacEvaluator t={t} locale={locale} styles={styles} />}
        {activeSubTab === 'rls' && <RlsPolicyManager t={t} locale={locale} styles={styles} />}
        {activeSubTab === 'cls' && <ClsPolicyManager t={t} locale={locale} styles={styles} />}
        {activeSubTab === 'masking' && <MaskingRuleManager t={t} locale={locale} styles={styles} />}
      </div>
    </div>
  );
}
