/**
 * ActionTypeTab — 本体动作类型管理 (列表/编辑Modal/执行测试)
 * @license Apache-2.0
 */

import React, { useState, useEffect, useMemo } from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import Pagination from '../../../components/common/Pagination';
import ConfirmDialog from '../../../components/common/ConfirmDialog';
import LoadingSkeleton from '../../../components/common/LoadingSkeleton';
import EmptyState from '../../../components/common/EmptyState';
import type { AIPActionType, AIPPostAction, ExecuteActionResult } from '../../../types/aiworkbench';
import {
  fetchActionTypes,
  createActionType,
  updateActionType,
  deleteActionType,
  executeActionType,
} from '../../../pages/aiworkbench/api';
import * as Icons from 'lucide-react';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

// ── Props ──────────────────────────────────────────────────────

interface ActionTypeTabProps {
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

// ── Object type options (used in dropdown) ─────────────────────

const OBJECT_TYPE_OPTIONS = [
  'AviationFlight',
  'AviationPilot',
  'AviationAircraft',
  'AviationRoute',
  'AviationGate',
];

// ── Component ──────────────────────────────────────────────────

export default function ActionTypeTab({ showToast }: ActionTypeTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // ── List state ───────────────────────────────────────────────
  const [actionTypes, setActionTypes] = useState<AIPActionType[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // ── Modal state ──────────────────────────────────────────────
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AIPActionType | null>(null);
  const [formName, setFormName] = useState('');
  const [formObjectType, setFormObjectType] = useState(OBJECT_TYPE_OPTIONS[0]);
  const [formPreconditions, setFormPreconditions] = useState('');
  const [formPostActions, setFormPostActions] = useState<AIPPostAction[]>([
    { type: 'update_property', params: { field: '', value: '' } },
  ]);
  const [formAuditEnabled, setFormAuditEnabled] = useState(true);
  const [saving, setSaving] = useState(false);

  // ── Delete confirm ───────────────────────────────────────────
  const [deleteTarget, setDeleteTarget] = useState<AIPActionType | null>(null);

  // ── Execute test panel ───────────────────────────────────────
  const [selectedExecuteId, setSelectedExecuteId] = useState('');
  const [executeObjectId, setExecuteObjectId] = useState('');
  const [executing, setExecuting] = useState(false);
  const [executeResult, setExecuteResult] = useState<ExecuteActionResult | null>(null);

  // ── Data fetch ───────────────────────────────────────────────
  const loadData = async () => {
    setLoading(true);
    try {
      const data = await fetchActionTypes();
      setActionTypes(data);
    } catch (e) {
      showToast?.('error', t('aiworkbench.actionType.fetchError'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, []);

  // ── Pagination ───────────────────────────────────────────────
  const totalPages = Math.max(1, Math.ceil(actionTypes.length / pageSize));
  const pagedItems = useMemo(
    () => actionTypes.slice((page - 1) * pageSize, page * pageSize),
    [actionTypes, page, pageSize]
  );

  // ── Modal handlers ───────────────────────────────────────────
  const openCreateModal = () => {
    setEditing(null);
    setFormName('');
    setFormObjectType(OBJECT_TYPE_OPTIONS[0]);
    setFormPreconditions('');
    setFormPostActions([{ type: 'update_property', params: { field: '', value: '' } }]);
    setFormAuditEnabled(true);
    setModalOpen(true);
  };

  const openEditModal = (item: AIPActionType) => {
    setEditing(item);
    setFormName(item.name);
    setFormObjectType(item.objectTypeId || OBJECT_TYPE_OPTIONS[0]);
    setFormPreconditions(JSON.stringify(item.preconditions, null, 2));
    setFormPostActions(
      item.postActions.length > 0
        ? item.postActions.map(p => ({ ...p, params: { ...p.params } }))
        : [{ type: 'update_property', params: { field: '', value: '' } }]
    );
    setFormAuditEnabled(item.auditEnabled);
    setModalOpen(true);
  };

  const handleSave = async () => {
    if (!formName.trim()) {
      showToast?.('error', t('aiworkbench.actionType.nameRequired'));
      return;
    }
    setSaving(true);

    let preconditions: Record<string, unknown>[] = [];
    try {
      if (formPreconditions.trim()) {
        preconditions = JSON.parse(formPreconditions);
        if (!Array.isArray(preconditions)) preconditions = [];
      }
    } catch {
      showToast?.('error', t('aiworkbench.actionType.invalidJson'));
      setSaving(false);
      return;
    }

    const payload = {
      name: formName.trim(),
      objectTypeId: formObjectType,
      preconditions,
      postActions: formPostActions.filter(
        p => p.type.trim() && p.params && Object.keys(p.params).length > 0
      ),
      auditEnabled: formAuditEnabled,
    };

    try {
      if (editing) {
        await updateActionType(editing.id, payload);
        showToast?.('success', t('aiworkbench.actionType.updateSuccess'));
      } else {
        await createActionType(payload);
        showToast?.('success', t('aiworkbench.actionType.createSuccess'));
      }
      setModalOpen(false);
      await loadData();
    } catch (e: any) {
      showToast?.('error', e.message || t('aiworkbench.actionType.saveError'));
    } finally {
      setSaving(false);
    }
  };

  // ── Delete handler ───────────────────────────────────────────
  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteActionType(deleteTarget.id);
      showToast?.('success', t('aiworkbench.actionType.deleteSuccess'));
      setDeleteTarget(null);
      await loadData();
    } catch (e: any) {
      showToast?.('error', e.message || t('aiworkbench.actionType.deleteError'));
    }
  };

  // ── Execute handler ──────────────────────────────────────────
  const handleExecute = async () => {
    if (!selectedExecuteId || !executeObjectId.trim()) {
      showToast?.('error', t('aiworkbench.actionType.executeFieldsRequired'));
      return;
    }
    setExecuting(true);
    setExecuteResult(null);
    try {
      const result = await executeActionType(selectedExecuteId, executeObjectId.trim());
      setExecuteResult(result);
    } catch (e: any) {
      showToast?.('error', e.message || t('aiworkbench.actionType.executeError'));
    } finally {
      setExecuting(false);
    }
  };

  // ── Post-action form helpers ─────────────────────────────────
  const updatePostAction = (idx: number, field: string, value: string) => {
    setFormPostActions(prev => {
      const next = [...prev];
      if (field === 'type') {
        next[idx] = { ...next[idx], type: value };
      } else {
        next[idx] = {
          ...next[idx],
          params: { ...next[idx].params, [field]: value },
        };
      }
      return next;
    });
  };

  const addPostAction = () => {
    setFormPostActions(prev => [
      ...prev,
      { type: 'update_property', params: { field: '', value: '' } },
    ]);
  };

  const removePostAction = (idx: number) => {
    setFormPostActions(prev => prev.filter((_, i) => i !== idx));
  };

  // ── Render ───────────────────────────────────────────────────

  return (
    <div className="space-y-4">
      {/* ── Header bar ─────────────────────────────────────── */}
      <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
        <div className="space-y-0.5">
          <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
            <Icon name="Zap" size={16} className="text-amber-500" />
            <span>{t('aiworkbench.actionType.title')}</span>
          </h2>
          <p className={`text-xs ${styles.cardTextMuted}`}>
            {t('aiworkbench.actionType.subtitle')}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={loadData}
            disabled={loading}
            className={`px-3 py-1.5 text-xs font-bold rounded-lg border ${styles.cardBorder} ${styles.cardText} hover:${styles.inputBg} transition cursor-pointer`}
          >
            <Icon name="RefreshCw" size={12} className="inline mr-1" />
            {t('aiworkbench.actionType.refresh')}
          </button>
          <button
            onClick={openCreateModal}
            className={`px-3.5 py-1.5 ${styles.accentBg} text-white font-extrabold rounded-lg shadow-sm flex items-center gap-1.5 cursor-pointer text-xs transition hover:opacity-90`}
          >
            <Icon name="Plus" size={12} />
            <span>{t('aiworkbench.actionType.create')}</span>
          </button>
        </div>
      </div>

      {/* ── ActionType list table ──────────────────────────── */}
      {loading ? (
        <LoadingSkeleton rows={5} variant="table" />
      ) : actionTypes.length === 0 ? (
        <EmptyState
          icon={<Icon name="Zap" size={40} />}
          title={t('aiworkbench.actionType.emptyTitle')}
          description={t('aiworkbench.actionType.emptyDesc')}
          action={{ label: t('aiworkbench.actionType.create'), onClick: openCreateModal }}
        />
      ) : (
        <div className={`rounded-xl border ${styles.cardBorder} ${styles.cardBg} overflow-hidden`}>
          <table className="w-full text-xs">
            <thead>
              <tr className={`border-b ${styles.cardBorder}`}>
                <th className={`text-left px-4 py-2.5 font-extrabold text-[11px] ${styles.cardTextMuted}`}>
                  {t('aiworkbench.actionType.colName')}
                </th>
                <th className={`text-left px-4 py-2.5 font-extrabold text-[11px] ${styles.cardTextMuted}`}>
                  {t('aiworkbench.actionType.colObjectType')}
                </th>
                <th className={`text-left px-4 py-2.5 font-extrabold text-[11px] ${styles.cardTextMuted}`}>
                  {t('aiworkbench.actionType.colPreconditions')}
                </th>
                <th className={`text-left px-4 py-2.5 font-extrabold text-[11px] ${styles.cardTextMuted}`}>
                  {t('aiworkbench.actionType.colEnabled')}
                </th>
                <th className={`text-right px-4 py-2.5 font-extrabold text-[11px] ${styles.cardTextMuted}`}>
                  {t('aiworkbench.actionType.colActions')}
                </th>
              </tr>
            </thead>
            <tbody>
              {pagedItems.map(item => (
                <tr key={item.id} className={`border-b ${styles.cardBorder} hover:${styles.inputBg}`}>
                  <td className={`px-4 py-2.5 ${styles.cardText} font-bold`}>{item.name}</td>
                  <td className={`px-4 py-2.5 ${styles.cardTextMuted}`}>{item.objectTypeName}</td>
                  <td className={`px-4 py-2.5 ${styles.cardTextMuted}`}>{item.preconditions.length}</td>
                  <td className="px-4 py-2.5">
                    <span
                      className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-bold ${
                        item.enabled ? 'bg-emerald-500/10 text-emerald-600' : 'bg-slate-200 text-slate-500 dark:bg-slate-700 dark:text-slate-400'
                      }`}
                    >
                      {item.enabled ? t('aiworkbench.actionType.enabled') : t('aiworkbench.actionType.disabled')}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button
                        onClick={() => openEditModal(item)}
                        className="p-1.5 rounded hover:bg-black/5 dark:hover:bg-white/5 cursor-pointer transition"
                        title={t('aiworkbench.actionType.edit')}
                      >
                        <Icon name="Pencil" size={12} className={styles.cardTextMuted} />
                      </button>
                      <button
                        onClick={() => setDeleteTarget(item)}
                        className="p-1.5 rounded hover:bg-red-50 dark:hover:bg-red-900/10 cursor-pointer transition"
                        title={t('aiworkbench.actionType.delete')}
                      >
                        <Icon name="Trash2" size={12} className="text-red-500" />
                      </button>
                      <button
                        onClick={() => {
                          setSelectedExecuteId(item.id);
                          setExecuteObjectId('');
                          setExecuteResult(null);
                        }}
                        className="p-1.5 rounded hover:bg-blue-50 dark:hover:bg-blue-900/10 cursor-pointer transition"
                        title={t('aiworkbench.actionType.execute')}
                      >
                        <Icon name="Play" size={12} className="text-blue-500" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination
            page={page}
            pageSize={pageSize}
            total={actionTypes.length}
            onPageChange={setPage}
            onPageSizeChange={(sz) => { setPageSize(sz); setPage(1); }}
          />
        </div>
      )}

      {/* ── Create/Edit Modal ──────────────────────────────── */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setModalOpen(false)} />
          <div
            className={`relative z-50 w-full max-w-lg mx-4 rounded-xl shadow-2xl p-6 max-h-[90vh] overflow-y-auto ${styles.cardBg} border ${styles.cardBorder}`}
          >
            <h3 className={`text-sm font-black ${styles.cardText} mb-4`}>
              {editing ? t('aiworkbench.actionType.editTitle') : t('aiworkbench.actionType.createTitle')}
            </h3>

            {/* Name */}
            <div className="mb-3">
              <label className={`block text-[11px] font-bold ${styles.cardTextMuted} mb-1`}>
                {t('aiworkbench.actionType.formName')}
              </label>
              <input
                type="text"
                value={formName}
                onChange={e => setFormName(e.target.value)}
                className={`w-full px-3 py-1.5 text-xs rounded-lg border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                placeholder={t('aiworkbench.actionType.formNamePlaceholder')}
              />
            </div>

            {/* Object Type */}
            <div className="mb-3">
              <label className={`block text-[11px] font-bold ${styles.cardTextMuted} mb-1`}>
                {t('aiworkbench.actionType.formObjectType')}
              </label>
              <select
                value={formObjectType}
                onChange={e => setFormObjectType(e.target.value)}
                className={`w-full px-3 py-1.5 text-xs rounded-lg border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              >
                {OBJECT_TYPE_OPTIONS.map(opt => (
                  <option key={opt} value={opt}>{opt}</option>
                ))}
              </select>
            </div>

            {/* Preconditions JSON */}
            <div className="mb-3">
              <label className={`block text-[11px] font-bold ${styles.cardTextMuted} mb-1`}>
                {t('aiworkbench.actionType.formPreconditions')}
              </label>
              <textarea
                value={formPreconditions}
                onChange={e => setFormPreconditions(e.target.value)}
                rows={5}
                className={`w-full px-3 py-1.5 text-xs rounded-lg border font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                placeholder='[{"key": "status", "op": "eq", "value": "active"}]'
              />
            </div>

            {/* Post Actions */}
            <div className="mb-3">
              <div className="flex items-center justify-between mb-1">
                <label className={`text-[11px] font-bold ${styles.cardTextMuted}`}>
                  {t('aiworkbench.actionType.formPostActions')}
                </label>
                <button
                  onClick={addPostAction}
                  className={`text-[10px] font-bold ${styles.accentBg} text-white px-2 py-0.5 rounded cursor-pointer hover:opacity-90`}
                >
                  + {t('aiworkbench.actionType.addAction')}
                </button>
              </div>
              <div className="space-y-2">
                {formPostActions.map((pa, idx) => (
                  <div key={idx} className={`flex items-center gap-2 p-2 rounded-lg border ${styles.cardBorder}`}>
                    <select
                      value={pa.type}
                      onChange={e => updatePostAction(idx, 'type', e.target.value)}
                      className={`flex-1 px-2 py-1 text-[11px] rounded border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                    >
                      <option value="update_property">update_property</option>
                      <option value="send_notification">send_notification</option>
                      <option value="trigger_pipeline">trigger_pipeline</option>
                      <option value="invoke_function">invoke_function</option>
                    </select>
                    <input
                      type="text"
                      value={pa.params?.field || ''}
                      onChange={e => updatePostAction(idx, 'field', e.target.value)}
                      className={`w-24 px-2 py-1 text-[11px] rounded border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                      placeholder="field"
                    />
                    <input
                      type="text"
                      value={pa.params?.value || ''}
                      onChange={e => updatePostAction(idx, 'value', e.target.value)}
                      className={`w-28 px-2 py-1 text-[11px] rounded border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                      placeholder="value"
                    />
                    <button
                      onClick={() => removePostAction(idx)}
                      className="p-0.5 rounded hover:bg-red-50 dark:hover:bg-red-900/10 cursor-pointer"
                    >
                      <Icon name="X" size={12} className="text-red-400" />
                    </button>
                  </div>
                ))}
              </div>
            </div>

            {/* Audit toggle */}
            <div className="mb-4 flex items-center gap-2">
              <input
                type="checkbox"
                checked={formAuditEnabled}
                onChange={e => setFormAuditEnabled(e.target.checked)}
                className={`rounded ${styles.inputBorder} ${styles.accentBg}`}
              />
              <label className={`text-xs font-bold ${styles.cardText}`}>
                {t('aiworkbench.actionType.formAuditEnabled')}
              </label>
            </div>

            {/* Buttons */}
            <div className="flex gap-2 justify-end">
              <button
                onClick={() => setModalOpen(false)}
                className={`px-4 py-1.5 rounded text-xs border ${styles.cardBorder} ${styles.cardText} hover:${styles.inputBg} cursor-pointer`}
              >
                {t('aiworkbench.actionType.cancel')}
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className={`px-4 py-1.5 rounded text-xs font-bold text-white cursor-pointer transition ${styles.accentBg} hover:opacity-90 disabled:opacity-50`}
              >
                {saving ? t('aiworkbench.actionType.saving') : t('aiworkbench.actionType.save')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Execute test panel ─────────────────────────────── */}
      <div className={`rounded-xl border ${styles.cardBorder} ${styles.cardBg} p-4 space-y-3`}>
        <h3 className={`text-xs font-extrabold ${styles.cardText} flex items-center gap-2`}>
          <Icon name="PlayCircle" size={14} className="text-blue-500" />
          <span>{t('aiworkbench.actionType.executeTestTitle')}</span>
        </h3>

        <div className="flex items-center gap-3 flex-wrap">
          <select
            value={selectedExecuteId}
            onChange={e => { setSelectedExecuteId(e.target.value); setExecuteResult(null); }}
            className={`px-3 py-1.5 text-xs rounded-lg border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} min-w-[200px]`}
          >
            <option value="">{t('aiworkbench.actionType.selectActionType')}</option>
            {actionTypes.map(item => (
              <option key={item.id} value={item.id}>{item.name}</option>
            ))}
          </select>
          <input
            type="text"
            value={executeObjectId}
            onChange={e => setExecuteObjectId(e.target.value)}
            className={`px-3 py-1.5 text-xs rounded-lg border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} w-40`}
            placeholder={t('aiworkbench.actionType.objectIdPlaceholder')}
          />
          <button
            onClick={handleExecute}
            disabled={executing || !selectedExecuteId || !executeObjectId.trim()}
            className={`px-4 py-1.5 text-xs font-bold rounded-lg cursor-pointer transition bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-40`}
          >
            {executing ? (
              <span className="flex items-center gap-1.5">
                <Icon name="Loader" size={12} className="animate-spin" />
                {t('aiworkbench.actionType.executing')}
              </span>
            ) : (
              <span className="flex items-center gap-1.5">
                <Icon name="Play" size={12} />
                {t('aiworkbench.actionType.executeBtn')}
              </span>
            )}
          </button>
        </div>

        {/* Execute results card */}
        {executeResult && (
          <div className={`mt-3 p-4 rounded-xl border ${styles.cardBorder} space-y-3`}>
            {/* Success banner */}
            <div
              className={`flex items-center gap-2 px-3 py-2 rounded-lg text-xs font-bold ${
                executeResult.success
                  ? 'bg-emerald-500/10 text-emerald-600'
                  : 'bg-red-500/10 text-red-600'
              }`}
            >
              <Icon name={executeResult.success ? 'CheckCircle' : 'XCircle'} size={14} />
              <span>
                {executeResult.success
                  ? t('aiworkbench.actionType.executeSuccess')
                  : t('aiworkbench.actionType.executeFailed')}
              </span>
            </div>

            {/* Precondition results */}
            <div>
              <h4 className={`text-[11px] font-extrabold ${styles.cardTextMuted} uppercase mb-1.5`}>
                {t('aiworkbench.actionType.preconditionResults')}
              </h4>
              <div className="space-y-1">
                {executeResult.preconditionResults.map((pc, i) => (
                  <div key={i} className="flex items-center gap-2 text-xs">
                    <Icon
                      name={pc.passed ? 'Check' : 'X'}
                      size={12}
                      className={pc.passed ? 'text-emerald-500' : 'text-red-500'}
                    />
                    <span className={styles.cardText}>{pc.key}</span>
                    <span className={styles.cardTextMuted}>— {pc.message}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Changes */}
            {executeResult.changes.length > 0 && (
              <div>
                <h4 className={`text-[11px] font-extrabold ${styles.cardTextMuted} uppercase mb-1.5`}>
                  {t('aiworkbench.actionType.changes')}
                </h4>
                <div className="space-y-1">
                  {executeResult.changes.map((ch, i) => (
                    <div key={i} className="flex items-center gap-2 text-xs">
                      <span className={`font-mono font-bold ${styles.cardText}`}>{ch.field}</span>
                      <span className={styles.cardTextMuted}>
                        {ch.before} → {ch.after}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Post action statuses */}
            {executeResult.postActionStatuses.length > 0 && (
              <div>
                <h4 className={`text-[11px] font-extrabold ${styles.cardTextMuted} uppercase mb-1.5`}>
                  {t('aiworkbench.actionType.postActionStatuses')}
                </h4>
                <div className="space-y-1">
                  {executeResult.postActionStatuses.map((ps, i) => (
                    <div key={i} className="flex items-center gap-2 text-xs">
                      <Icon
                        name={ps.status === 'success' ? 'Check' : 'X'}
                        size={12}
                        className={ps.status === 'success' ? 'text-emerald-500' : 'text-red-500'}
                      />
                      <span className={`font-bold ${styles.cardText}`}>{ps.type}</span>
                      <span className={styles.cardTextMuted}>— {ps.message}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Audit ID */}
            {executeResult.auditId && (
              <div className={`pt-2 border-t ${styles.cardBorder}`}>
                <span className={`text-[10px] ${styles.cardTextMuted}`}>
                  {t('aiworkbench.actionType.auditId')}:{' '}
                </span>
                <code className={`text-[11px] ${styles.cardText} font-mono`}>{executeResult.auditId}</code>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Delete confirm dialog ──────────────────────────── */}
      <ConfirmDialog
        visible={deleteTarget !== null}
        title={t('aiworkbench.actionType.deleteConfirmTitle')}
        message={`${t('aiworkbench.actionType.deleteConfirmMsg')} "${deleteTarget?.name || ''}"?`}
        variant="danger"
        confirmText={t('aiworkbench.actionType.deleteConfirmBtn')}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
