import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { CheckCircle, CheckCheck, Clock, AlertTriangle, Play, Plus, Trash2, Send, Shield, ShieldCheck, XCircle, Link2 } from 'lucide-react';
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
  fetchProperties,
  DEFAULT_ONTOLOGY_ID,
} from '../../services/ontologyApi';
import type { Proposal, ProposalStatus, CreateProposalDTO, VerifyProposalResult, ReviewProposalDTO, PropertyType } from '../../types/ontology';
import type { ObjectType } from '../../types/ontology';

interface ProposalPanelProps {
  /** 全部对象类型（提案目标下拉的候选集） */
  objectTypes: ObjectType[];
  /** 当前在详情页选中的对象类型 —— 有值时提案目标锁定为该本体 */
  selectedObjectType?: ObjectType | null;
  /** 自增信号：详情页点击「发起变更提案」时触发，>0 即自动展开表单 */
  openFormSignal?: number;
  /** 提案执行完成回调 —— 由工作台重拉实体/属性，使变更在界面上可见 */
  onProposalExecuted?: () => void;
}

type StatusBadgeStyle = { bg: string; text: string; icon: React.ReactNode };

/** 存量徽章样式（存量硬编码 Tailwind 色，本次不清理）；EXECUTED / VERIFIED 见 resolveStatusBadge */
const STATUS_BADGE: Record<Exclude<ProposalStatus, 'EXECUTED' | 'VERIFIED'>, StatusBadgeStyle> = {
  DRAFT: { bg: 'bg-slate-100', text: 'text-slate-700', icon: <Clock size={10} /> },
  PENDING: { bg: 'bg-amber-50', text: 'text-amber-700', icon: <Clock size={10} /> },
  APPROVED: { bg: 'bg-emerald-50', text: 'text-emerald-700', icon: <CheckCircle size={10} /> },
  REJECTED: { bg: 'bg-red-50', text: 'text-red-700', icon: <XCircle size={10} /> },
};

/** 提案类型 → 变更类别（写入 payload.changeType，兼容后端历史字段） */
const PROPOSAL_KINDS = [
  'UPDATE_ENTITY',
  'ADD_PROPERTY',
  'MODIFY_PROPERTY',
  'DELETE_PROPERTY',
  'CREATE_ENTITY',
  'ADD_RELATIONSHIP',
] as const;

type ProposalKind = (typeof PROPOSAL_KINDS)[number];

const KIND_CHANGE_TYPE: Record<ProposalKind, 'CREATE' | 'UPDATE' | 'DELETE'> = {
  UPDATE_ENTITY: 'UPDATE',
  ADD_PROPERTY: 'CREATE',
  MODIFY_PROPERTY: 'UPDATE',
  DELETE_PROPERTY: 'DELETE',
  CREATE_ENTITY: 'CREATE',
  ADD_RELATIONSHIP: 'CREATE',
};

/** 变更集：基于「后端现存属性」与「本地草稿属性」的差集 */
interface PropertyChangeSet {
  added: Record<string, any>[];
  updated: Record<string, any>[];
  removed: string[];
}

const EMPTY_CHANGE_SET: PropertyChangeSet = { added: [], updated: [], removed: [] };

/** 读取当前登录用户名（与 api.ts 的 localStorage 身份存储保持一致） */
const readCurrentUser = (): string => {
  try {
    return localStorage.getItem('username') || '';
  } catch {
    return '';
  }
};

export default function ProposalPanel({
  objectTypes,
  selectedObjectType = null,
  openFormSignal = 0,
  onProposalExecuted,
}: ProposalPanelProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [proposals, setProposals] = useState<Proposal[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [formTitle, setFormTitle] = useState('');
  const [formTargetType, setFormTargetType] = useState('');
  const [formKind, setFormKind] = useState<ProposalKind>('UPDATE_ENTITY');
  const [formDescription, setFormDescription] = useState('');
  const [verificationResults, setVerificationResults] = useState<Record<string, VerifyProposalResult>>({});
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [reviewModal, setReviewModal] = useState<{ id: string; action: 'approve' | 'reject' } | null>(null);
  const [reviewComment, setReviewComment] = useState('');
  /** 目标本体的后端现存属性 ID 集合（表单展开时抓取，作为差集基线） */
  const [originalPropertyIds, setOriginalPropertyIds] = useState<Set<string> | null>(null);
  const [currentUser, setCurrentUser] = useState<string>(() => readCurrentUser());

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

  // 登录态可能在会话内变化（切换账号/重新登录），列表刷新时同步
  useEffect(() => { setCurrentUser(readCurrentUser()); }, [proposals]);

  // 详情页「发起变更提案」信号 → 自动展开表单
  useEffect(() => {
    if (openFormSignal > 0) {
      setShowForm(true);
    }
  }, [openFormSignal]);

  // 表单展开时抓取目标本体后端现存属性，作为「新增/修改/删除」判定基线
  useEffect(() => {
    if (!showForm || !selectedObjectType) {
      setOriginalPropertyIds(null);
      return;
    }
    let cancelled = false;
    fetchProperties(selectedObjectType.id)
      .then(list => {
        if (!cancelled) {
          setOriginalPropertyIds(new Set((list || []).map(p => String(p.id))));
        }
      })
      .catch(() => {
        if (!cancelled) {
          setOriginalPropertyIds(new Set());
        }
      });
    return () => { cancelled = true; };
  }, [showForm, selectedObjectType?.id]);

  /** 提案目标：优先取详情页选中的本体（锁定），否则取表单下拉选择 */
  const targetObjectType = selectedObjectType ?? objectTypes.find(ot => ot.id === formTargetType) ?? null;

  /** 本地草稿属性 → 后端属性请求体 */
  const toPropertyBody = (p: PropertyType, withId = false): Record<string, any> => {
    const body: Record<string, any> = {
      code: p.apiName || p.displayName,
      name: p.displayName,
      description: p.description || '',
      propertyType: String(p.dataType || 'string').toUpperCase(),
      requiredFlag: p.required ? 1 : 0,
      uniqueFlag: p.isPrimaryKey ? 1 : 0,
      searchableFlag: p.searchable ? 1 : 0,
    };
    if (withId) {
      body.id = p.id;
      body.propertyId = p.id;
    }
    return body;
  };

  /** 变更集：本地草稿属性与后端现存属性的差集（提交后由后端执行落库） */
  const changeSet: PropertyChangeSet = useMemo(() => {
    if (!targetObjectType || originalPropertyIds === null) {
      return EMPTY_CHANGE_SET;
    }
    const localIds = new Set(targetObjectType.properties.map(p => String(p.id)));
    return {
      added: targetObjectType.properties
        .filter(p => !originalPropertyIds.has(String(p.id)))
        .map(p => toPropertyBody(p)),
      updated: targetObjectType.properties
        .filter(p => originalPropertyIds.has(String(p.id)))
        .map(p => toPropertyBody(p, true)),
      removed: [...originalPropertyIds].filter(id => !localIds.has(id)),
    };
  }, [targetObjectType, originalPropertyIds]);

  const resetForm = () => {
    setShowForm(false);
    setFormTitle('');
    setFormTargetType('');
    setFormKind('UPDATE_ENTITY');
    setFormDescription('');
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formTitle.trim() || !targetObjectType) {
      if (!targetObjectType) {
        showToast('error', t('ow.msg.selectObjectFirst'));
      }
      return;
    }
    // 身份校验：未登录不得发起提案 —— 否则提交人只能落为 system 兜底，自审拦截将失效
    if (!currentUser) {
      showToast('error', t('ow.msg.loginRequired'));
      return;
    }
    try {
      const dto: CreateProposalDTO = {
        title: formTitle.trim(),
        targetType: targetObjectType.id,
        proposalType: formKind,
        targetEntity: targetObjectType.id,
        ontologyId: DEFAULT_ONTOLOGY_ID,
        domainCode: DEFAULT_ONTOLOGY_ID,
        changeType: KIND_CHANGE_TYPE[formKind],
        description: formDescription.trim(),
        payload: {
          ontologyId: DEFAULT_ONTOLOGY_ID,
          entityId: targetObjectType.id,
          entity: {
            code: targetObjectType.apiName,
            name: targetObjectType.displayName,
            description: targetObjectType.description,
          },
          propertiesAdded: changeSet.added,
          propertiesUpdated: changeSet.updated,
          propertiesRemoved: changeSet.removed,
        },
        proposedBy: currentUser || undefined,
      };
      await createProposal(dto);
      showToast('success', t('ow.msg.proposalCreated'));
      resetForm();
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

  /** 提交审批：前置身份校验 —— 未登录不得提交（后端以登录态为准记录提交人） */
  const handleSubmit = (id: string) => {
    if (!currentUser) {
      showToast('error', t('ow.msg.loginRequired'));
      return;
    }
    handleAction(id, () => submitProposal(id), 'ow.msg.proposalSubmitted');
  };

  const handleExecute = async (id: string) => {
    setActionLoading(id);
    try {
      await executeProposal(id);
      showToast('success', t('ow.msg.proposalExecuted'));
      loadProposals();
      // 闭环：执行完成后通知工作台重拉实体/属性，使变更立即可见
      onProposalExecuted?.();
    } catch {
      showToast('error', t('ow.msg.proposalFailed'));
    } finally {
      setActionLoading(null);
    }
  };

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
      // 写后刷新：验证会变更提案状态（verified/rejected），重拉列表以驱动「执行」按钮显隐
      loadProposals();
    } catch {
      showToast('error', t('ow.msg.proposalFailed'));
    } finally {
      setActionLoading(null);
    }
  };

  /** 审批人 = 提交人时禁止自审自批（前端拦截，后端 ONT-007 二次兜底） */
  const isSelfReview = (p: Proposal) =>
    !!currentUser && !!p.proposedBy && p.proposedBy.toLowerCase() === currentUser.toLowerCase();

  const handleReviewSubmit = async () => {
    if (!reviewModal) return;
    if (!currentUser) {
      showToast('error', t('ow.msg.loginRequired'));
      return;
    }
    const target = proposals.find(p => p.id === reviewModal.id);
    if (target && isSelfReview(target)) {
      showToast('error', t('ow.msg.reviewerMustDiffer'));
      setReviewModal(null);
      return;
    }
    const dto: ReviewProposalDTO = {
      reviewer: currentUser,
      reviewComment: reviewComment.trim() || undefined,
    };
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
  const kindLabel = (kind: string) => t(`ow.proposal.kind.${kind}`);
  /** 状态徽章：EXECUTED / VERIFIED 走主题令牌语义色；未知状态兜底 DRAFT，避免后端新增状态导致渲染崩溃 */
  const resolveStatusBadge = (s: ProposalStatus): StatusBadgeStyle => {
    if (s === 'EXECUTED') {
      return { bg: styles.successBg, text: styles.successText, icon: <CheckCheck size={10} /> };
    }
    if (s === 'VERIFIED') {
      return { bg: styles.infoBg, text: styles.infoText, icon: <ShieldCheck size={10} /> };
    }
    return STATUS_BADGE[s] ?? STATUS_BADGE.DRAFT;
  };

  /** 提案列表项的目标本体展示名（后端 target_entity 即对象类型 id） */
  const targetName = (p: Proposal) =>
    objectTypes.find(ot => ot.id === p.targetType || ot.id === p.targetId)?.displayName || p.targetType || '-';

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
            onClick={() => {
              if (!selectedObjectType && objectTypes.length === 0) {
                showToast('error', t('ow.msg.selectObjectFirst'));
                return;
              }
              setShowForm(true);
            }}
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
              onClick={resetForm}
              className={`${styles.muted} hover:opacity-80 p-1 rounded`}
            >
              <XCircle size={16} />
            </button>
          </div>

          {/* 目标本体：详情页选中时锁定展示，否则提供下拉选择 */}
          <div className="space-y-1">
            <label className={`text-[11px] font-bold ${styles.muted} uppercase tracking-wider block`}>
              {t('ow.label.proposalTarget')}
            </label>
            {selectedObjectType ? (
              <div className={`flex items-center gap-2 px-3 py-1.5 text-xs border ${styles.cardBorder} rounded ${styles.cardBg}`}>
                <Link2 size={13} className={styles.accentText} />
                <span className={`font-semibold ${styles.cardText}`}>{selectedObjectType.displayName}</span>
                <span className={`font-mono ${styles.muted}`}>{selectedObjectType.apiName}</span>
                <span className={`ml-auto text-[10px] ${styles.muted}`}>{t('ow.label.basedOnObject')}</span>
              </div>
            ) : (
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
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
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
                {t('ow.label.proposalType')}
              </label>
              <select
                value={formKind}
                onChange={e => setFormKind(e.target.value as ProposalKind)}
                className={`w-full px-3 py-1.5 text-xs border ${styles.cardBorder} rounded ${styles.cardBg}`}
              >
                {PROPOSAL_KINDS.map(kind => (
                  <option key={kind} value={kind}>{kindLabel(kind)}</option>
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

          {/* 变更明细：本地草稿 vs 后端现存属性的差集，作为提案执行内容 */}
          {selectedObjectType && (
            <div className={`text-[11px] ${styles.cardTextMuted} border ${styles.cardBorder} rounded-lg p-3 space-y-1`}>
              <div className="font-semibold">{t('ow.label.proposalChangeSet')}</div>
              <div className="flex items-center gap-3">
                <span className="text-emerald-600">{t('ow.label.changeSetAdded')}: {changeSet.added.length}</span>
                <span className="text-blue-600">{t('ow.label.changeSetUpdated')}: {changeSet.updated.length}</span>
                <span className="text-red-600">{t('ow.label.changeSetRemoved')}: {changeSet.removed.length}</span>
              </div>
            </div>
          )}

          <div className={`flex justify-end gap-2 border-t ${styles.cardBorder} pt-3`}>
            <button
              type="button"
              onClick={resetForm}
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
            const badge = resolveStatusBadge(p.status);
            const verification = verificationResults[p.id];
            const isActing = actionLoading === p.id;
            const selfReview = isSelfReview(p);

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
                        {kindLabel(p.changeType)}
                      </span>
                    </div>
                    <p className={`text-[11px] ${styles.muted} mt-0.5 truncate`}>{p.description}</p>
                    <div className={`flex items-center gap-3 mt-1 text-[10px] ${styles.muted}`}>
                      <span>{t('ow.label.proposalAuthor')}: {p.proposedBy || '-'}</span>
                      <span>{t('ow.label.proposalTarget')}: {targetName(p)}</span>
                      {p.reviewer ? <span>{t('ow.label.proposalReviewer')}: {p.reviewer}</span> : null}
                    </div>
                    {selfReview && p.status === 'PENDING' && (
                      <p className="text-[10px] text-amber-600 mt-1">{t('ow.msg.reviewerMustDiffer')}</p>
                    )}
                  </div>
                </div>

                {verification && (
                  <div className={`text-[11px] p-2 rounded-lg border ${verification.valid ? 'bg-emerald-50 border-emerald-200 text-emerald-700' : 'bg-amber-50 border-amber-200 text-amber-700'}`}>
                    <div className="flex items-center gap-1 font-semibold">
                      {verification.valid ? <CheckCircle size={12} /> : <AlertTriangle size={12} />}
                      {t('ow.label.proposalVerified')}: {verification.valid ? '✓' : ''}
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
                        disabled={isActing || selfReview}
                        title={selfReview ? t('ow.msg.reviewerMustDiffer') : undefined}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 hover:bg-emerald-100 disabled:opacity-50"
                      >
                        <CheckCircle size={10} />
                        {t('ow.btn.approveProposal')}
                      </button>
                      <button
                        onClick={() => setReviewModal({ id: p.id, action: 'reject' })}
                        disabled={isActing || selfReview}
                        title={selfReview ? t('ow.msg.reviewerMustDiffer') : undefined}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[10px] font-semibold bg-red-50 text-red-600 border border-red-200 hover:bg-red-100 disabled:opacity-50"
                      >
                        <XCircle size={10} />
                        {t('ow.btn.rejectProposal')}
                      </button>
                    </>
                  )}
                  {/* 已验证(VERIFIED) 或已审批(APPROVED) 均可执行 —— 与后端 execute 端点受理状态对齐 */}
                  {(p.status === 'APPROVED' || p.status === 'VERIFIED') && (
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