import React, { useState, useCallback, useEffect } from 'react';
import { Search, Plus, X, CheckCircle, AlertTriangle } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { cognitiveEngineApi } from '../../../services/cognitiveEngineApi';
import { showToastGlobal } from '../../../components/common/Toast';
import Button from '../../../components/common/Button';
import Badge, { type BadgeVariant } from '../../../components/common/Badge';
import type { Hypothesis, Evidence } from './types';

// 主题合规（前端规范 §七.1 / design-05 B.9-4）：状态色一律走 Badge 语义变体，
// 具体明暗形态由 useTheme() token 层提供，页面不再出现 Tailwind 色字面量。
const STATUS_BADGE: Record<string, BadgeVariant> = {
  VALID: 'success',
  TESTING: 'warning',
  SUPPORTED: 'info',
  REJECTED: 'danger',
};

const STATUS_LABELS: Record<string, string> = { VALID: '有效', TESTING: '测试中', SUPPORTED: '已支持', REJECTED: '已拒绝' };

export default function hypothesis() {
  const { styles } = useTheme();
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
      showToastGlobal('error', `${t('hypo.loadFail')}: ${(e as Error)?.message ?? e}`);
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
        showToastGlobal('error', t('hypo.noEvidence'));
        return;
      }
      await cognitiveEngineApi.updateBeliefByEvidence(selected.code ?? selected.id, { evidenceId: firstEvidence });
      showToastGlobal('success', t('hypo.supported'));
      loadData();
    } catch (e) {
      showToastGlobal('error', `${t('hypo.supportFail')}: ${(e as Error)?.message ?? e}`);
    }
  };

  const handleReject = async () => {
    if (!selected) return;
    try {
      await cognitiveEngineApi.invalidateHypothesis(selected.id);
      showToastGlobal('success', t('hypo.rejected'));
      loadData();
    } catch (e) {
      showToastGlobal('error', `${t('hypo.rejectFail')}: ${(e as Error)?.message ?? e}`);
    }
  };

  const handleCreate = async () => {
    if (creating) return;
    if (!newStatement.trim()) {
      showToastGlobal('error', t('hypo.statementRequired'));
      return;
    }
    setCreating(true);
    try {
      await cognitiveEngineApi.createHypothesis({
        statement: newStatement.trim(),
        domain: newDomain.trim() || undefined,
        evidenceIds: newEvidenceIds.trim() ? newEvidenceIds.trim().split(',').map(s => s.trim()) : undefined,
      });
      showToastGlobal('success', t('hypo.created'));
      setShowModal(false);
      setNewStatement('');
      setNewDomain('');
      setNewEvidenceIds('');
      loadData();
    } catch (e) {
      showToastGlobal('error', `${t('hypo.createFail')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setCreating(false);
    }
  };

  const filtered = hypotheses.filter(h => {
    if (search && !h.statement.toLowerCase().includes(search.toLowerCase()) && !(h.code ?? '').toLowerCase().includes(search.toLowerCase())) return false;
    if (impactFilter) return false;
    return true;
  });

  const modalInput = `mt-1 w-full px-3 py-2 text-sm border ${styles.cardBorder} rounded-md ${styles.inputBg} ${styles.inputText}`;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className={`text-lg font-semibold ${styles.cardText}`}>{t('hypo.title')}</h1>
        <Button variant="primary" size="md" onClick={() => setShowModal(true)} aria-label={t('hypo.new')}>
          <Plus className="w-4 h-4" />
          {t('hypo.new')}
        </Button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <div className={`relative flex-1 min-w-[200px]`}>
          <Search className={`absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 ${styles.muted}`} aria-hidden />
          <input
            aria-label={t('hypo.search')}
            className={`w-full pl-9 pr-3 py-1.5 text-sm border ${styles.cardBorder} rounded-md ${styles.inputBg} ${styles.inputText}`}
            placeholder={t('hypo.search')} value={search} onChange={e => setSearch(e.target.value)} />
        </div>
        <select
          aria-label={t('hypo.status.all')}
          className={`px-3 py-1.5 text-sm border ${styles.cardBorder} rounded-md ${styles.inputBg} ${styles.inputText}`}
          value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
          <option value="">{t('hypo.status.all')}</option>
          {Object.keys(STATUS_LABELS).map(s => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
        </select>
        <select
          aria-label={t('hypo.impact.all')}
          className={`px-3 py-1.5 text-sm border ${styles.cardBorder} rounded-md ${styles.inputBg} ${styles.inputText}`}
          value={impactFilter} onChange={e => setImpactFilter(e.target.value)}>
          <option value="">{t('hypo.impact.all')}</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </select>
      </div>

      {/* Table */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg overflow-x-auto`}>
        <table className="w-full text-sm" aria-label={t('hypo.title')}>
          <thead>
            <tr className={`border-b ${styles.cardBorder} text-left text-xs ${styles.muted}`}>
              <th scope="col" className="px-4 py-2 font-medium">{t('hypo.col.hypothesis')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('hypo.col.object')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('hypo.col.status')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('hypo.col.support')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('hypo.col.evidence')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('hypo.col.updated')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('hypo.col.action')}</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={7} className={`px-4 py-6 text-center ${styles.muted}`} role="status">{t('common.loading')}</td></tr>
            ) : filtered.length === 0 ? (
              <tr><td colSpan={7} className={`px-4 py-6 text-center ${styles.muted}`}>{t('hypo.empty')}</td></tr>
            ) : filtered.map(h => (
              <tr key={h.id} className={`border-b ${styles.divider} last:border-0 cursor-pointer ${styles.sidebarHoverBg}`}
                onClick={() => handleSelect(h)}>
                <td className="px-4 py-2">
                  <span className={`text-xs ${styles.muted} font-mono`}>{h.code}</span>
                  <p className={`${styles.cardText} truncate max-w-[200px]`}>{h.statement}</p>
                </td>
                <td className={`px-4 py-2 ${styles.muted}`}>{h.domain ?? '—'}</td>
                <td className="px-4 py-2">
                  <Badge variant={STATUS_BADGE[h.status] ?? 'neutral'}>
                    {STATUS_LABELS[h.status] ?? h.status}
                  </Badge>
                </td>
                <td className={`px-4 py-2 ${styles.cardText}`}>{h.isValid ? '—' : '—'}</td>
                <td className={`px-4 py-2 ${styles.muted}`}>—</td>
                <td className={`px-4 py-2 ${styles.muted} text-xs`}>{(h as any).updatedAt ?? '—'}</td>
                <td className="px-4 py-2" onClick={e => e.stopPropagation()}>
                  <button type="button" onClick={() => handleSelect(h)}
                    aria-label={t('hypo.view')}
                    className={`text-xs ${styles.accentText} hover:underline font-medium`}>
                    {t('hypo.view')}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Drawer */}
      {selected && (
        <div className={`fixed inset-y-0 right-0 w-[380px] ${styles.cardBg} border-l ${styles.cardBorder} shadow-xl flex flex-col z-50`}
          role="dialog" aria-label={t('hypo.title')} aria-modal="true">
          <div className={`flex items-center justify-between p-4 border-b ${styles.cardBorder}`}>
            <h2 className={`text-sm font-semibold ${styles.cardText}`}>{selected.code} · {t('hypo.title')}</h2>
            <button type="button" onClick={() => setSelected(null)} aria-label={t('common.close')}
              className={`p-1 rounded ${styles.muted} hover:opacity-70 cursor-pointer`}><X className="w-4 h-4" /></button>
          </div>
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            <p className={`text-sm ${styles.cardText}`}>{selected.statement}</p>
            <div>
              <p className={`text-xs font-medium ${styles.muted} mb-2`}>{t('hypo.supportEvidence')}</p>
              {evidence.filter(e => !e.isConflict).length === 0 ? (
                <p className={`text-xs ${styles.muted}`}>{t('hypo.noEvidenceShort')}</p>
              ) : evidence.filter(e => !e.isConflict).map(e => (
                <div key={e.id} className={`flex items-center gap-2 px-2 py-1.5 ${styles.inputBg} rounded text-xs ${styles.cardText} mb-1`}>
                  <CheckCircle className={`w-3.5 h-3.5 ${styles.successText} flex-shrink-0`} aria-hidden />
                  <span className="font-mono">{e.code}</span>
                  <span className={`ml-auto ${styles.muted}`}>{(e.confidence * 100).toFixed(0)}%</span>
                </div>
              ))}
            </div>
            <div>
              <p className={`text-xs font-medium ${styles.muted} mb-2`}>{t('hypo.rebuttalEvidence')}</p>
              {evidence.filter(e => e.isConflict).length === 0 ? (
                <p className={`text-xs ${styles.muted}`}>{t('hypo.noEvidenceShort')}</p>
              ) : evidence.filter(e => e.isConflict).map(e => (
                <div key={e.id} className={`flex items-center gap-2 px-2 py-1.5 ${styles.inputBg} rounded text-xs ${styles.cardText} mb-1`}>
                  <AlertTriangle className={`w-3.5 h-3.5 ${styles.dangerText} flex-shrink-0`} aria-hidden />
                  <span className="font-mono">{e.code}</span>
                  <span className={`ml-auto ${styles.muted}`}>{(e.confidence * 100).toFixed(0)}%</span>
                </div>
              ))}
            </div>
            <div>
              <p className={`text-xs font-medium ${styles.muted} mb-1`}>{t('hypo.currentBelief')}</p>
              <div className={`${styles.inputBg} rounded p-2`}>
                <div className={`flex items-center justify-between text-xs ${styles.cardText} mb-1`}>
                  <span>{selected.code}: belief</span>
                  <span className="font-mono">0.72</span>
                </div>
                <div className={`h-1.5 ${styles.sidebarBg} rounded-full overflow-hidden`}>
                  <div className={`h-full ${styles.accentBg} rounded-full`} style={{ width: '72%' }} />
                </div>
              </div>
            </div>
          </div>
          <div className={`p-4 border-t ${styles.cardBorder} flex gap-2`}>
            <button type="button" onClick={handleSupport}
              aria-label={t('hypo.markSupport')}
              className={`flex-1 rounded-md border px-3 py-1.5 text-sm font-medium transition-opacity hover:opacity-90 cursor-pointer ${styles.successBg} ${styles.successText} ${styles.successBorder}`}>
              {t('hypo.markSupport')}
            </button>
            <Button variant="danger" onClick={handleReject} className="flex-1" aria-label={t('hypo.markReject')}>
              {t('hypo.markReject')}
            </Button>
          </div>
        </div>
      )}

      {/* Modal */}
      {showModal && (
        <div className={`fixed inset-0 ${styles.overlayBg} flex items-center justify-center z-50 p-4`} onClick={() => setShowModal(false)}>
          <div className={`${styles.cardBg} rounded-lg shadow-xl w-full max-w-md p-6 space-y-4`} onClick={e => e.stopPropagation()}
            role="dialog" aria-label={t('hypo.new')} aria-modal="true">
            <div className="flex items-center justify-between">
              <h2 className={`text-base font-semibold ${styles.cardText}`}>{t('hypo.new')}</h2>
              <button type="button" onClick={() => setShowModal(false)} aria-label={t('common.close')}
                className={`p-1 rounded ${styles.muted} hover:opacity-70 cursor-pointer`}><X className="w-4 h-4" /></button>
            </div>
            <div className="space-y-3">
              <label className={`block text-sm font-medium ${styles.cardText}`}>
                {t('hypo.statement')}
                <textarea className={modalInput}
                  value={newStatement} onChange={e => setNewStatement(e.target.value)} />
              </label>
              <label className={`block text-sm font-medium ${styles.cardText}`}>
                {t('hypo.domain')}
                <input className={modalInput}
                  value={newDomain} onChange={e => setNewDomain(e.target.value)} />
              </label>
              <label className={`block text-sm font-medium ${styles.cardText}`}>
                {t('hypo.evidenceIds')}
                <input className={modalInput}
                  placeholder="E-001, E-002" value={newEvidenceIds} onChange={e => setNewEvidenceIds(e.target.value)} />
              </label>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="secondary" onClick={() => setShowModal(false)} aria-label={t('common.cancel')}>
                {t('common.cancel')}
              </Button>
              <Button variant="primary" onClick={handleCreate} disabled={creating} aria-label={t('common.save')}>
                {creating ? <span className="inline-block w-3.5 h-3.5 border-2 border-current/30 border-t-current rounded-full animate-spin mr-1" aria-hidden /> : null}
                {t('common.save')}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
