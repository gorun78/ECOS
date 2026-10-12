import React, { useState, useEffect, useCallback } from 'react';
import {
  Shield, Plus, Trash2, Edit3, X, Search, AlertTriangle, Loader2,
} from 'lucide-react';
import {
  fetchRlsPolicies, createRlsPolicy, updateRlsPolicy, deleteRlsPolicy,
  type RlsPolicy,
} from '../../../api';
import { inputClasses, btnPrimary, btnSecondary, badgeClasses } from './helpers';
import TableSelect from './TableSelect';

// ── RLS Policy Manager ───────────────────────────────────────
export default function RlsPolicyManager({ t, locale, styles }: { t: (k: string) => string; locale: string; styles: any }) {
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
          <p className={`text-xs ${styles.muted}`}>{t('sec.rls.subtitle')}</p>
        </div>
        <button type="button" onClick={openCreate} className={`flex items-center gap-1.5 ${btnPrimary(styles)}`}>
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
            placeholder={t('sec.rls.searchTablePh')}
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
          <button type="button" onClick={() => setError(null)} className="ml-auto"><X size={14} /></button>
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
                      <button type="button" onClick={() => openEdit(p)} className={`p-1.5 rounded hover:${styles.sidebarHoverBg} ${styles.muted} cursor-pointer`}>
                        <Edit3 size={14} />
                      </button>
                      <button type="button" onClick={() => setDeleteTarget(p)} className="p-1.5 rounded hover:bg-red-50 text-red-500 cursor-pointer">
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
              <button type="button" onClick={() => setModalOpen(false)} className={`${styles.muted} hover:${styles.cardText} cursor-pointer`}><X size={18} /></button>
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
              <button type="button" onClick={() => setModalOpen(false)} className={btnSecondary(styles)}>{t('common.cancel')}</button>
              <button type="button" onClick={handleSave} disabled={saving || !form.policyName || !form.tableName} className={btnPrimary(styles)}>
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
              <button type="button" onClick={() => setDeleteTarget(null)} className={btnSecondary(styles)}>{t('common.cancel')}</button>
              <button type="button" onClick={handleDelete} className="px-4 py-2 rounded-lg text-sm font-medium text-white bg-red-600 hover:bg-red-700 transition-colors cursor-pointer">{t('common.delete')}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
