import React, { useState, useEffect, useCallback } from 'react';
import {
  Shield, Plus, Trash2, Edit3, X, Search, AlertTriangle, Loader2,
} from 'lucide-react';
import {
  fetchAbacPolicies, createAbacPolicy, updateAbacPolicy, deleteAbacPolicy,
  type AbacPolicy,
} from '../../../api';
import { inputClasses, btnPrimary, btnSecondary, badgeClasses } from './helpers';

// ── ABAC Policy CRUD Manager ────────────────────────────────
export default function AbacPolicyManager({ t, locale, styles }: { t: (k: string) => string; locale: string; styles: any }) {
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
