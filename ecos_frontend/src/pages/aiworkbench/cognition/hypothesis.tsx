import React, { useState, useCallback, useEffect } from 'react';
import { Search, Plus, X, CheckCircle, AlertTriangle } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { cognitiveEngineApi } from '../../../services/cognitiveEngineApi';
import { showToastGlobal } from '../../../components/common/Toast';
import type { Hypothesis, Evidence } from './types';

const STATUS_STYLES: Record<string, string> = {
  VALID: 'bg-green-100 dark:bg-green-950 text-green-700 dark:text-green-300',
  TESTING: 'bg-orange-100 dark:bg-orange-950 text-orange-700 dark:text-orange-300',
  SUPPORTED: 'bg-blue-100 dark:bg-blue-950 text-blue-700 dark:text-blue-300',
  REJECTED: 'bg-red-100 dark:bg-red-950 text-red-700 dark:text-red-300',
};

const STATUS_LABELS: Record<string, string> = { VALID: '有效', TESTING: '测试中', SUPPORTED: '已支持', REJECTED: '已拒绝' };

export default function hypothesis() {
  const { t } = useLanguage();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [impactFilter, setImpactFilter] = useState('');
  const [hypotheses, setHypotheses] = useState<Hypothesis[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Hypothesis | null>(null);
  const [evidence, setEvidence] = useState<Evidence[]>([]);
  const [showModal, setShowModal] = useState(false);
  const [newStatement, setNewStatement] = useState('');
  const [newDomain, setNewDomain] = useState('');
  const [newEvidenceIds, setNewEvidenceIds] = useState('');
  const [creating, setCreating] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const params: { status?: string } = {};
      if (statusFilter) params.status = statusFilter;
      const data = await cognitiveEngineApi.fetchHypotheses(params);
      const list: Hypothesis[] = Array.isArray(data) ? data : ((data as any)?.data ?? (data as any)?.records ?? []);
      setHypotheses(list);
    } catch (e) {
      showToastGlobal('error', `${t('hypo.loadFail', '加载假设列表失败')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setLoading(false);
    }
  }, [statusFilter, t]);

  useEffect(() => { loadData(); }, [loadData]);

  const handleSelect = async (h: Hypothesis) => {
    setSelected(h);
    setEvidence([]);
    try {
      const data = await cognitiveEngineApi.fetchEvidence({});
      const ev: Evidence[] = Array.isArray(data) ? data : ((data as any)?.data ?? ((data as any)?.items ?? []));
      setEvidence(ev);
    } catch { /* evidence optional */ }
  };

  const handleSupport = async () => {
    if (!selected) return;
    try {
      const firstEvidence = evidence[0]?.id;
      if (!firstEvidence) {
        showToastGlobal('error', t('hypo.noEvidence', '暂无关联证据'));
        return;
      }
      await cognitiveEngineApi.updateBeliefByEvidence(selected.code ?? selected.id, { evidenceId: firstEvidence });
      showToastGlobal('success', t('hypo.supported', '已标记支持'));
      loadData();
    } catch (e) {
      showToastGlobal('error', `${t('hypo.supportFail', '操作失败')}: ${(e as Error)?.message ?? e}`);
    }
  };

  const handleReject = async () => {
    if (!selected) return;
    try {
      await cognitiveEngineApi.invalidateHypothesis(selected.id);
      showToastGlobal('success', t('hypo.rejected', '已标记反驳'));
      loadData();
    } catch (e) {
      showToastGlobal('error', `${t('hypo.rejectFail', '操作失败')}: ${(e as Error)?.message ?? e}`);
    }
  };

  const handleCreate = async () => {
    if (creating) return;
    if (!newStatement.trim()) {
      showToastGlobal('error', t('hypo.statementRequired', '请填写假设描述'));
      return;
    }
    setCreating(true);
    try {
      await cognitiveEngineApi.createHypothesis({
        statement: newStatement.trim(),
        domain: newDomain.trim() || undefined,
        evidenceIds: newEvidenceIds.trim() ? newEvidenceIds.trim().split(',').map(s => s.trim()) : undefined,
      });
      showToastGlobal('success', t('hypo.created', '假设已创建'));
      setShowModal(false);
      setNewStatement('');
      setNewDomain('');
      setNewEvidenceIds('');
      loadData();
    } catch (e) {
      showToastGlobal('error', `${t('hypo.createFail', '创建失败')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setCreating(false);
    }
  };

  const filtered = hypotheses.filter(h => {
    if (search && !h.statement.toLowerCase().includes(search.toLowerCase()) && !(h.code ?? '').toLowerCase().includes(search.toLowerCase())) return false;
    if (impactFilter) return false;
    return true;
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('hypo.title', '假设管理')}</h1>
        <button onClick={() => setShowModal(true)}
          className="flex items-center gap-1 px-3 py-1.5 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600">
          <Plus className="w-4 h-4" />
          {t('hypo.new', '新建假设')}
        </button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <div className="relative flex-1 min-w-[200px]">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input className="w-full pl-9 pr-3 py-1.5 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100"
            placeholder={t('hypo.search', '搜索假设…')} value={search} onChange={e => setSearch(e.target.value)} />
        </div>
        <select className="px-3 py-1.5 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-300"
          value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
          <option value="">{t('hypo.status.all', '全部状态')}</option>
          {Object.keys(STATUS_LABELS).map(s => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
        </select>
        <select className="px-3 py-1.5 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-300"
          value={impactFilter} onChange={e => setImpactFilter(e.target.value)}>
          <option value="">{t('hypo.impact.all', '全部影响级别')}</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </select>
      </div>

      {/* Table */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-700 text-left text-xs text-slate-500 dark:text-slate-400">
              <th className="px-4 py-2 font-medium">{t('hypo.col.hypothesis', '假设')}</th>
              <th className="px-4 py-2 font-medium">{t('hypo.col.object', '对象')}</th>
              <th className="px-4 py-2 font-medium">{t('hypo.col.status', '状态')}</th>
              <th className="px-4 py-2 font-medium">{t('hypo.col.support', '支持度')}</th>
              <th className="px-4 py-2 font-medium">{t('hypo.col.evidence', '证据')}</th>
              <th className="px-4 py-2 font-medium">{t('hypo.col.updated', '更新时间')}</th>
              <th className="px-4 py-2 font-medium">{t('hypo.col.action', '操作')}</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={7} className="px-4 py-6 text-center text-slate-400">Loading…</td></tr>
            ) : filtered.length === 0 ? (
              <tr><td colSpan={7} className="px-4 py-6 text-center text-slate-400">{t('hypo.empty', '暂无假设')}</td></tr>
            ) : filtered.map(h => (
              <tr key={h.id} className="border-b border-slate-100 dark:border-slate-800 last:border-0 cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800"
                onClick={() => handleSelect(h)}>
                <td className="px-4 py-2">
                  <span className="text-xs text-slate-400 font-mono">{h.code}</span>
                  <p className="text-slate-900 dark:text-slate-100 truncate max-w-[200px]">{h.statement}</p>
                </td>
                <td className="px-4 py-2 text-slate-500 dark:text-slate-400">{h.domain ?? '—'}</td>
                <td className="px-4 py-2">
                  <span className={`text-xs px-2 py-0.5 rounded-full ${STATUS_STYLES[h.status] ?? 'bg-slate-100 dark:bg-slate-800 text-slate-500'}`}>
                    {STATUS_LABELS[h.status] ?? h.status}
                  </span>
                </td>
                <td className="px-4 py-2 text-slate-700 dark:text-slate-300">{h.isValid ? '—' : '—'}</td>
                <td className="px-4 py-2 text-slate-500 dark:text-slate-400">—</td>
                <td className="px-4 py-2 text-slate-400 text-xs">{(h as any).updatedAt ?? '—'}</td>
                <td className="px-4 py-2" onClick={e => e.stopPropagation()}>
                  <button onClick={() => handleSelect(h)} className="text-xs text-blue-600 dark:text-blue-400 hover:underline font-medium">
                    {t('hypo.view', '查看')}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Drawer */}
      {selected && (
        <div className="fixed inset-y-0 right-0 w-[380px] bg-white dark:bg-slate-900 border-l border-slate-200 dark:border-slate-700 shadow-xl flex flex-col z-50">
          <div className="flex items-center justify-between p-4 border-b border-slate-200 dark:border-slate-700">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{selected.code} · {t('hypo.title', '假设')}</h2>
            <button onClick={() => setSelected(null)} className="text-slate-400 hover:text-slate-600"><X className="w-4 h-4" /></button>
          </div>
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            <p className="text-sm text-slate-700 dark:text-slate-300">{selected.statement}</p>
            <div>
              <p className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">{t('hypo.supportEvidence', '支持证据')}</p>
              {evidence.filter(e => !e.isConflict).length === 0 ? (
                <p className="text-xs text-slate-400">{t('hypo.noEvidenceShort', '暂无')}</p>
              ) : evidence.filter(e => !e.isConflict).map(e => (
                <div key={e.id} className="flex items-center gap-2 px-2 py-1.5 bg-slate-50 dark:bg-slate-800 rounded text-xs text-slate-700 dark:text-slate-300 mb-1">
                  <CheckCircle className="w-3.5 h-3.5 text-green-500 flex-shrink-0" />
                  <span className="font-mono">{e.code}</span>
                  <span className="ml-auto text-slate-400">{(e.confidence * 100).toFixed(0)}%</span>
                </div>
              ))}
            </div>
            <div>
              <p className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">{t('hypo.rebuttalEvidence', '反驳证据')}</p>
              {evidence.filter(e => e.isConflict).length === 0 ? (
                <p className="text-xs text-slate-400">{t('hypo.noEvidenceShort', '暂无')}</p>
              ) : evidence.filter(e => e.isConflict).map(e => (
                <div key={e.id} className="flex items-center gap-2 px-2 py-1.5 bg-slate-50 dark:bg-slate-800 rounded text-xs text-slate-700 dark:text-slate-300 mb-1">
                  <AlertTriangle className="w-3.5 h-3.5 text-red-500 flex-shrink-0" />
                  <span className="font-mono">{e.code}</span>
                  <span className="ml-auto text-slate-400">{(e.confidence * 100).toFixed(0)}%</span>
                </div>
              ))}
            </div>
            <div>
              <p className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-1">{t('hypo.currentBelief', '当前 Belief')}</p>
              <div className="bg-slate-50 dark:bg-slate-800 rounded p-2">
                <div className="flex items-center justify-between text-xs text-slate-600 dark:text-slate-300 mb-1">
                  <span>{selected.code}: belief</span>
                  <span className="font-mono">0.72</span>
                </div>
                <div className="h-1.5 bg-slate-200 dark:bg-slate-700 rounded-full overflow-hidden">
                  <div className="h-full bg-blue-500 rounded-full" style={{ width: '72%' }} />
                </div>
              </div>
            </div>
          </div>
          <div className="p-4 border-t border-slate-200 dark:border-slate-700 flex gap-2">
            <button onClick={handleSupport}
              className="flex-1 px-3 py-1.5 rounded-md text-sm font-medium bg-green-600 text-white hover:bg-green-700">
              {t('hypo.markSupport', '标记支持')}
            </button>
            <button onClick={handleReject}
              className="flex-1 px-3 py-1.5 rounded-md text-sm font-medium bg-red-600 text-white hover:bg-red-700">
              {t('hypo.markReject', '标记反驳')}
            </button>
          </div>
        </div>
      )}

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={() => setShowModal(false)}>
          <div className="bg-white dark:bg-slate-900 rounded-lg shadow-xl w-full max-w-md p-6 space-y-4" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between">
              <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100">{t('hypo.new', '新建假设')}</h2>
              <button onClick={() => setShowModal(false)} className="text-slate-400 hover:text-slate-600"><X className="w-4 h-4" /></button>
            </div>
            <div className="space-y-3">
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                {t('hypo.statement', '假设描述')}
                <textarea className="mt-1 w-full px-3 py-2 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 min-h-[80px]"
                  value={newStatement} onChange={e => setNewStatement(e.target.value)} />
              </label>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                {t('hypo.domain', '业务域')}
                <input className="mt-1 w-full px-3 py-1.5 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100"
                  value={newDomain} onChange={e => setNewDomain(e.target.value)} />
              </label>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                {t('hypo.evidenceIds', '证据 ID 列表（逗号分隔）')}
                <input className="mt-1 w-full px-3 py-1.5 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100"
                  placeholder="E-001, E-002" value={newEvidenceIds} onChange={e => setNewEvidenceIds(e.target.value)} />
              </label>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button onClick={() => setShowModal(false)}
                className="px-3 py-1.5 rounded-md text-sm font-medium bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700">
                {t('common.cancel', '取消')}
              </button>
              <button onClick={handleCreate} disabled={creating}
                className="px-3 py-1.5 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 disabled:opacity-50">
                {creating ? <span className="inline-block w-3.5 h-3.5 border-2 border-white/40 border-t-white rounded-full animate-spin mr-1" /> : null}
                {t('common.save', '保存')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
