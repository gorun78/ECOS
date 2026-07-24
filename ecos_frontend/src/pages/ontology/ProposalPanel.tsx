import React, { useState, useEffect, useCallback } from 'react';
import { CheckCircle, Clock, AlertTriangle, Play, Plus, Trash2, Send, Shield, XCircle } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import {
  fetchProposals,
  createProposal,
  submitProposal,
  verifyProposal,
  approveProposal,
  rejectProposal,
  executeProposal,
  deleteProposal,
} from '../../services/ontologyApi';
import type { Proposal, ProposalStatus, CreateProposalDTO, VerifyProposalResult, ReviewProposalDTO } from '../../types/ontology';
import type { ObjectType } from '../../types/ontology';

interface ProposalPanelProps {
  objectTypes: ObjectType[];
}

const STATUS_BADGE: Record<ProposalStatus, { bg: string; text: string; icon: React.ReactNode }> = {
  DRAFT: { bg: 'bg-slate-100', text: 'text-slate-700', icon: <Clock size={10} /> },
  PENDING: { bg: 'bg-amber-50', text: 'text-amber-700', icon: <Clock size={10} /> },
  APPROVED: { bg: 'bg-emerald-50', text: 'text-emerald-700', icon: <CheckCircle size={10} /> },
  REJECTED: { bg: 'bg-red-50', text: 'text-red-700', icon: <XCircle size={10} /> },
};

const CHANGE_TYPES = ['CREATE', 'UPDATE', 'DELETE'] as const;

export default function ProposalPanel({ objectTypes }: ProposalPanelProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [proposals, setProposals] = useState<Proposal[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [formTitle, setFormTitle] = useState('');
  const [formTargetType, setFormTargetType] = useState('');
  const [formChangeType, setFormChangeType] = useState<string>('CREATE');
  const [formDescription, setFormDescription] = useState('');
  const [verificationResults, setVerificationResults] = useState<Record<string, VerifyProposalResult>>({});
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [reviewModal, setReviewModal] = useState<{ id: string; action: 'approve' | 'reject' } | null>(null);
  const [reviewComment, setReviewComment] = useState('');

  const [toast, setToast] = useState<{ type: 'success' | 'info' | 'error'; message: string } | null>(null);
  const showToast = useCallback((type: 'success' | 'info' | 'error', message: string) => {
    setToast({ type, message });
    setTimeout(() => setToast(null), 4000);
  }, []);

  const loadProposals = useCallback(async () => {
    try {
      const data = await fetchProposals();
      setProposals(data);
    } catch {
      showToast('error', t('ow.msg.proposalFailed'));
    } finally {
      setLoading(false);
    }
  }, [showToast, t]);

  useEffect(() => { loadProposals(); }, [loadProposals]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formTitle.trim() || !formTargetType) return;
    try {
      const dto: CreateProposalDTO = {
        title: formTitle.trim(),
        targetType: formTargetType,
        changeType: formChangeType as 'CREATE' | 'UPDATE' | 'DELETE',
        description: formDescription.trim(),
      };
      await createProposal(dto);
      showToast('success', t('ow.msg.proposalCreated'));
      setShowForm(false);
      setFormTitle('');
      setFormTargetType('');
      setFormChangeType('CREATE');
      setFormDescription('');
      loadProposals();
    } catch {
      showToast('error', t('ow.msg.proposalFailed'));
    }
  };

  const handleAction = async (id: string, action: () => Promise<any>, msgKey: string) => {
    setActionLoading(id);
    try {
      await action();
      showToast('success', t(msgKey));
      loadProposals();
    } catch {
      showToast('error', t('ow.msg.proposalFailed'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleSubmit = (id: string) => handleAction(id, () => submitProposal(id), 'ow.msg.proposalSubmitted');
  const handleExecute = (id: string) => handleAction(id, () => executeProposal(id), 'ow.msg.proposalExecuted');
  const handleDelete = async (id: string) => {
    try {
      await deleteProposal(id);
      showToast('success', t('ow.msg.proposalDeleted'));
      loadProposals();
    } catch {
      showToast('error', t('ow.msg.proposalFailed'));
    }
  };

  const handleVerify = async (id: string) => {
    setActionLoading(id);
    try {
      const result = await verifyProposal(id);
      setVerificationResults(prev => ({ ...prev, [id]: result }));
      showToast('info', t('ow.msg.proposalVerified'));
    } catch {
      showToast('error', t('ow.msg.proposalFailed'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleReviewSubmit = async () => {
    if (!reviewModal) return;
    const dto: ReviewProposalDTO = { reviewComment: reviewComment.trim() || undefined };
    const action = reviewModal.action === 'approve'
      ? () => approveProposal(reviewModal.id, dto)
      : () => rejectProposal(reviewModal.id, dto);
    const msgKey = reviewModal.action === 'approve' ? 'ow.msg.proposalApproved' : 'ow.msg.proposalRejected';
    setActionLoading(reviewModal.id);
    try {
      await action();
      showToast('success', t(msgKey));
      loadProposals();
    } catch {
      showToast('error', t('ow.msg.proposalFailed'));
    } finally {
      setActionLoading(null);
      setReviewModal(null);
      setReviewComment('');
    }
  };

  const statusLabel = (s: ProposalStatus) => t(`ow.proposal.status.${s.toLowerCase()}`);
  const changeTypeLabel = (ct: string) => t(`ow.proposal.type.${ct}`);

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-4`}>
      <div className={`flex justify-between items-center border-b ${styles.cardBorder} pb-3`}>
        <div>
          <h3 className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1.5`}>
            <Shield size={15} className={styles.muted} />
            {t('ow.section.proposals')}
          </h3>
          <p className={`text-[11px] ${styles.muted} mt-0.5`}>
            {t('ow.label.proposalStatus')}: {proposals.length}
          </p>
        </div>
        {!showForm && (
          <button
            onClick={() => setShowForm(true)}
            className={`${styles.accentBg} hover:opacity-90 text-white text-xs px-3 py-1.5 rounded-lg font-medium flex items-center gap-1 shadow-xs`}
          >
            <Plus size={14} />
            {t('ow.btn.createProposal')}
          </button>
        )}
      </div>

      {showForm && (
        <form onSubmit={handleCreate} className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
          <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
            <h4 className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1.5`}>
              <Plus size={14} className={styles.accentText} />
              {t('ow.btn.createProposal')}
            </h4>
            <button
              type="button"
              onClick={() => setShowForm(false)}
              className={`${styles.muted} hover:opacity-80 p-1 rounded`}
            >
              <XCircle size={16} />
            </button>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div className="space-y-1">
              <label className={`text-[11px] font-bold ${styles.muted} uppercase tracking-wider block`}>
                {t('ow.label.displayName')}
              </label>
              <input
                type="text"
                value={formTitle}
                onChange={e => setFormTitle(e.target.value)}
                placeholder={t('ow.placeholder.proposalTitle')}
                className={`w-full px-3 py-1.5 text-xs border ${styles.cardBorder} rounded ${styles.cardBg}`}
                required
              />
            </div>
            <div className="space-y-1">
              <label className={`text-[11px] font-bold ${styles.muted} uppercase tracking-wider block`}>
                {t('ow.label.proposalTarget')}
              </label>
              <select
                value={formTargetType}
                onChange={e => setFormTargetType(e.target.value)}
                className={`w-full px-3 py-1.5 text-xs border ${styles.cardBorder} rounded ${styles.cardBg}`}
                required
              >
                <option value="">--</option>
                {objectTypes.map(ot => (
                  <option key={ot.id} value={ot.id}>{ot.displayName}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <label className={`text-[11px] font-bold ${styles.muted} uppercase tracking-wider block`}>
                {t('ow.label.proposalType')}
              </label>
              <select
                value={formChangeType}
                onChange={e => setFormChangeType(e.target.value)}
                className={`w-full px-3 py-1.5 text-xs border ${styles.cardBorder} rounded ${styles.cardBg}`}
              >
                {CHANGE_TYPES.map(ct => (
                  <option key={ct} value={ct}>{changeTypeLabel(ct)}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="space-y-1">
            <label className={`text-[11px] font-bold ${styles.muted} uppercase tracking-wider block`}>
              {t('ow.label.description')}
            </label>
            <textarea
              value={formDescription}
              onChange={e => setFormDescription(e.target.value)}
              placeholder={t('ow.placeholder.proposalDescription')}
              className={`w-full h-16 px-3 py-1.5 text-xs border ${styles.cardBorder} rounded ${styles.cardBg}`}
            />
          </div>
          <div className={`flex justify-end gap-2 border-t ${styles.cardBorder} pt-3`}>
            <button
              type="button"
              onClick={() => setShowForm(false)}
              className={`px-3.5 py-1.5 rounded-lg border ${styles.cardBorder} ${styles.cardTextMuted} text-xs font-semibold ${styles.cardBg}`}
            >
              {t('ow.btn.cancel')}
            </button>
            <button
              type="submit"
              className={`px-4 py-1.5 rounded-lg text-white text-xs font-semibold ${styles.accentBg} hover:opacity-90 shadow-sm`}
            >
              {t('ow.btn.createProposal')}
            </button>
          </div>
        </form>
      )}

      {loading ? (
        <div className={`text-xs ${styles.muted} text-center py-6`}>{t('ow.empty.noProposals')}</div>
      ) : proposals.length === 0 ? (
        <div className={`text-xs ${styles.muted} text-center py-6 border border-dashed ${styles.cardBorder} rounded-lg`}>
          {t('ow.empty.noProposals')}
        </div>
      ) : (
        <div className="space-y-2">
          {proposals.map(p => {
            const badge = STATUS_BADGE[p.status];
            const verification = verificationResults[p.id];
            const isActing = actionLoading === p.id;

            return (
              <div key={p.id} className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-3 space-y-2`}>
                <div className="flex items-start justify-between">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className={`text-xs font-semibold ${styles.cardText} truncate`}>{p.title}</span>
                      <span className={`inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded-full text-[10px] font-bold ${badge.bg} ${badge.text}`}>
                        {badge.icon}
                        {statusLabel(p.status)}
                      </span>
                      <span className={`text-[10px] ${styles.muted} font-mono`}>
                        {changeTypeLabel(p.changeType)}
                      </span>
                    </div>
                    <p className={`text-[11px] ${styles.muted} mt-0.5 truncate`}>{p.description}</p>
                    <div className={`flex items-center gap-3 mt-1 text-[10px] ${styles.muted}`}>
                      <span>{t('ow.label.proposalAuthor')}: {p.proposedBy || '-'}</span>
                      <span>{t('ow.label.proposalTarget')}: {objectTypes.find(ot => ot.id === p.targetType)?.displayName || p.targetType}</span>
                    </div>
                  </div>
                </div>

                {verification && (
                  <div className={`text-[11px] p-2 rounded-lg border ${verification.valid ? 'bg-emerald-50 border-emerald-200 text-emerald-700' : 'bg-amber-50 border-amber-200 text-amber-700'}`}>
                    <div className="flex items-center gap-1 font-semibold">
                      {verification.valid ? <CheckCircle size={12} /> : <AlertTriangle size={12} />}
                      {t('ow.label.proposalVerified')}: {verification.valid ? '✓' : '✗'}
                    </div>
                    {verification.issues.length > 0 && (
                      <ul className="mt-1 pl-4 list-disc space-y-0.5">
                        {verification.issues.map((issue, i) => (
                          <li key={i}>{issue}</li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}

                <div className={`flex items-center gap-1.5 pt-1 border-t ${styles.cardBorder}`}>
                  {p.status === 'DRAFT' && (
                    <>
                      <button
                        onClick={() => handleSubmit(p.id)}
                        disabled={isActing}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[10px] font-semibold bg-amber-50 text-amber-700 border border-amber-200 hover:bg-amber-100 disabled:opacity-50"
                      >
                        <Send size={10} />
                        {t('ow.btn.submitProposal')}
                      </button>
                      <button
                        onClick={() => handleDelete(p.id)}
                        disabled={isActing}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[10px] font-semibold bg-red-50 text-red-600 border border-red-200 hover:bg-red-100 disabled:opacity-50"
                      >
                        <Trash2 size={10} />
                      </button>
                    </>
                  )}
                  {p.status === 'PENDING' && (
                    <>
                      <button
                        onClick={() => handleVerify(p.id)}
                        disabled={isActing}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[10px] font-semibold bg-blue-50 text-blue-700 border border-blue-200 hover:bg-blue-100 disabled:opacity-50"
                      >
                        <Shield size={10} />
                        {t('ow.btn.verifyProposal')}
                      </button>
                      <button
                        onClick={() => setReviewModal({ id: p.id, action: 'approve' })}
                        disabled={isActing}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 hover:bg-emerald-100 disabled:opacity-50"
                      >
                        <CheckCircle size={10} />
                        {t('ow.btn.approveProposal')}
                      </button>
                      <button
                        onClick={() => setReviewModal({ id: p.id, action: 'reject' })}
                        disabled={isActing}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[10px] font-semibold bg-red-50 text-red-600 border border-red-200 hover:bg-red-100 disabled:opacity-50"
                      >
                        <XCircle size={10} />
                        {t('ow.btn.rejectProposal')}
                      </button>
                    </>
                  )}
                  {p.status === 'APPROVED' && (
                    <button
                      onClick={() => handleExecute(p.id)}
                      disabled={isActing}
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[10px] font-semibold bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50"
                    >
                      <Play size={10} />
                      {t('ow.btn.executeProposal')}
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {reviewModal && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center" onClick={() => { setReviewModal(null); setReviewComment(''); }}>
          <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} p-5 w-96`} onClick={e => e.stopPropagation()}>
            <h3 className={`text-sm font-bold mb-3 ${styles.cardText}`}>
              {reviewModal.action === 'approve' ? t('ow.btn.approveProposal') : t('ow.btn.rejectProposal')}
            </h3>
            <div className="space-y-3">
              <div>
                <label className={`block text-[10px] font-semibold mb-1 ${styles.muted}`}>{t('ow.label.proposalComment')}</label>
                <textarea
                  value={reviewComment}
                  onChange={e => setReviewComment(e.target.value)}
                  placeholder={t('ow.placeholder.proposalComment')}
                  className={`w-full h-20 px-3 py-2 text-xs border ${styles.cardBorder} rounded ${styles.cardBg}`}
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button onClick={() => { setReviewModal(null); setReviewComment(''); }}
                  className={`px-3 py-1.5 rounded text-[10px] font-semibold border ${styles.cardBorder} ${styles.cardTextMuted} ${styles.cardBg}`}>
                  {t('ow.btn.cancel')}
                </button>
                <button onClick={handleReviewSubmit}
                  className={`px-4 py-1.5 rounded text-[10px] font-semibold text-white ${reviewModal.action === 'approve' ? 'bg-emerald-600 hover:bg-emerald-700' : 'bg-red-600 hover:bg-red-700'}`}>
                  {reviewModal.action === 'approve' ? t('ow.btn.approveProposal') : t('ow.btn.rejectProposal')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {toast && (
        <div className={`fixed bottom-6 right-6 flex items-center gap-2 px-4 py-3 rounded-lg shadow-xl text-xs font-semibold text-white ${styles.accentBg} border ${styles.accentBorder} z-50`}>
          <span className={toast.type === 'success' ? 'text-emerald-400' : toast.type === 'error' ? 'text-red-400' : 'text-blue-400'}>
            {toast.type === 'success' ? '✓' : toast.type === 'error' ? '✗' : 'ℹ'}
          </span>
          <span>{toast.message}</span>
        </div>
      )}
    </div>
  );
}
