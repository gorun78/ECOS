/**
 * ECOS 场景工作台 — decision Tab（详细设计-07 F07-22 / F07-23 收口）。
 *
 * <p>原文件含「管理者零信任准入拦截沙箱」：内联硬编码审批人身份、前端伪造 {@code verdict}、
 * 固定角色与固定 clientIp 及固定耗时文案，属 P0 虚假验收族。该仿真分支
 * 整体删除：认知/准入结论只来自 {@code useCognition} 四端点（见 SimulationResultPanel /
 * CognitiveRightPanel），本 Tab 仅保留<b>真实 API</b> 的对账提案审批，补 §3.3 六态。</p>
 *
 * <p>F 组修复（检视报告 §4.6 行 264）：
 * ① 提案列表实为全局（非当前场景），副标题改为「全局列表」口径；
 * ② status 空串/缺省白屏兜底（toUpperCase 前判空）；
 * ③ payload 大 JSON 直排改为可折叠 <details>；
 * ④ 批准属不可逆写回 → 二次确认门（ConfirmDialog）+ 真实 traceId toast（由上层透出）；
 * ⑤ 驳回需理由 → 由上层 prompt-free 对话框（openRejectDialog）承接；
 * ⑥ catch 吞错 → 渲染错误态 + 重试。</p>
 */
import React, { useState } from 'react';
import {
  AlertTriangle,
  Archive,
  Check,
  Inbox,
  RefreshCw,
  User
} from 'lucide-react';
import ConfirmDialog from '../../../components/common/ConfirmDialog';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import type { BusinessScenario } from '../types';

interface Proposal {
  id: string;
  actionId?: string;
  actionName?: string;
  status?: string;
  payload?: unknown;
  proposedBy?: string;
  rejectReason?: string;
}

interface Props {
  activeScenario: BusinessScenario;
  proposals: unknown[];
  isLoadingProposals: boolean;
  /** 拉取失败时的错误信息（catch 吞错 → 错误态 + 重试） */
  proposalsError: string | null;
  fetchProposalsList: () => Promise<void>;
  handleApproveProposal: (id: string, actionId: string) => Promise<void>;
  /** 打开驳回理由对话框（替代原生 prompt()），由上层 ScenarioManagementView 持有模态 */
  openRejectDialog: (id: string, actionId: string) => void;
  resolvingProposalId: string | null;
}

export default function DecisionDeskTab({
  activeScenario, proposals, isLoadingProposals, proposalsError, fetchProposalsList,
  handleApproveProposal, openRejectDialog, resolvingProposalId,
}: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const rows = proposals as Proposal[];
  const pendingCount = rows.filter((p) => p.status === 'pending').length;
  // 批准二次确认门（危险/不可逆操作）
  const [approveTarget, setApproveTarget] = useState<Proposal | null>(null);

  const confirmApprove = () => {
    if (!approveTarget) return;
    void handleApproveProposal(approveTarget.id, approveTarget.actionId ?? approveTarget.id);
    setApproveTarget(null);
  };

  const badgeText = (status?: string) => {
    const s = (status ?? '').trim();
    return s ? s.toUpperCase() : t('scenario.decision.statusUnknown');
  };

  return (
    <div className="space-y-4">
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
        <div className="p-4 border-b border-[var(--border)] flex items-center justify-between gap-3">
          <div>
            <h3 className={`text-sm font-bold ${styles.cardText} flex items-center gap-2`}>
              {pendingCount > 0 && (
                <span className="flex h-2 w-2 relative">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[var(--danger)] opacity-75" />
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-[var(--danger)]" />
                </span>
              )}
              {t('scenario.decision.title')}
            </h3>
            {/* 提案列表来自全局 /api/v1/ontology/proposals（非按场景过滤），如实标注全局口径 */}
            <p className={`text-xs ${styles.cardTextMuted} mt-1`}>
              {t('scenario.decision.subtitleGlobal')}
            </p>
          </div>
          <button
            onClick={fetchProposalsList}
            disabled={isLoadingProposals}
            className={`shrink-0 px-3 py-1.5 border ${styles.cardBorder} ${styles.cardText} text-xs font-bold rounded flex items-center gap-1 transition-all cursor-pointer hover:opacity-80 disabled:opacity-50`}
          >
            <RefreshCw size={11} className={isLoadingProposals ? 'animate-spin' : ''} />
            {t('scenario.decision.refresh')}
          </button>
        </div>

        {/* 错误态（catch 吞错 → 渲染错误 + 重试，绝不伪装「暂无提案」） */}
        {proposalsError && (
          <div className={`p-6 text-center space-y-2`}>
            <AlertTriangle size={28} className={`mx-auto ${styles.dangerText}`} />
            <p className={`text-xs ${styles.dangerText}`}>{t('scenario.decision.loadError')}</p>
            <p className={`text-[10px] ${styles.cardTextMuted} font-mono break-all`}>{proposalsError}</p>
            <button
              onClick={fetchProposalsList}
              disabled={isLoadingProposals}
              className={`mt-1 px-3 py-1.5 border ${styles.cardBorder} ${styles.cardText} text-xs font-bold rounded flex items-center gap-1 mx-auto transition-all cursor-pointer hover:opacity-80 disabled:opacity-50`}
            >
              <RefreshCw size={11} className={isLoadingProposals ? 'animate-spin' : ''} />
              {t('scenario.decision.retry')}
            </button>
          </div>
        )}

        {/* 加载态 */}
        {!proposalsError && isLoadingProposals && rows.length === 0 && (
          <div className={`p-8 text-center ${styles.cardTextMuted}`}>{t('scenario.cognition.loading')}</div>
        )}

        {/* 空态 */}
        {!proposalsError && !isLoadingProposals && rows.length === 0 && (
          <div className={`p-8 text-center ${styles.cardTextMuted} space-y-2`}>
            <Inbox size={32} className={`mx-auto opacity-40`} />
            <p className="text-xs">{t('scenario.decision.empty')}</p>
          </div>
        )}

        {!proposalsError && rows.length > 0 && (
          <div className={`divide-y divide-[var(--border)] max-h-96 overflow-y-auto`}>
            {rows.map((prop) => {
              const isPending = prop.status === 'pending';
              return (
                <div key={prop.id} className="p-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
                  <div className="space-y-1.5 flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`text-xs font-extrabold ${styles.cardText}`}>{prop.actionName || prop.actionId || prop.id}</span>
                      <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded font-mono ${isPending ? `${styles.warningBg} ${styles.warningText}` : `${styles.cardBg} ${styles.cardTextMuted}`} border ${styles.cardBorder}`}>
                        {badgeText(prop.status)}
                      </span>
                    </div>
                    {/* payload 折叠：大 JSON 不再默认直排铺满，点击展开 */}
                    <details className="group">
                      <summary className={`text-[10px] cursor-pointer select-none ${styles.accentText} font-mono`}>
                        <span className="group-open:hidden">{t('scenario.decision.showPayload')}</span>
                        <span className="hidden group-open:inline">{t('scenario.decision.hidePayload')}</span>
                      </summary>
                      <pre className={`mt-1 text-[10px] ${styles.cardTextMuted} font-mono ${styles.inputBg} p-2.5 rounded border ${styles.inputBorder} overflow-x-auto max-h-56`}>
                        {JSON.stringify(prop.payload ?? {}, null, 2)}
                      </pre>
                    </details>
                    <div className={`flex items-center gap-4 text-[10px] ${styles.cardTextMuted} font-mono`}>
                      <span className="flex items-center gap-1">
                        <User size={10} />
                        {t('scenario.decision.source')}: {prop.proposedBy || t('scenario.decision.sourceAgent')}
                      </span>
                      {prop.rejectReason && (
                        <span className={styles.dangerText}>{t('scenario.decision.rejectReason')}: {prop.rejectReason}</span>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0 md:self-center">
                    {isPending ? (
                      <>
                        <button
                          onClick={() => openRejectDialog(prop.id, prop.actionId ?? prop.id)}
                          disabled={resolvingProposalId === prop.id}
                          className={`px-2.5 py-1.5 border ${styles.dangerBorder ?? styles.cardBorder} ${styles.dangerText} text-xs font-bold rounded cursor-pointer transition-all disabled:opacity-50`}
                        >
                          {t('scenario.decision.reject')}
                        </button>
                        <button
                          onClick={() => setApproveTarget(prop)}
                          disabled={resolvingProposalId === prop.id}
                          className={`px-3 py-1.5 ${styles.accentBg} border ${styles.accentBorder} ${styles.cardText} text-xs font-bold rounded flex items-center gap-1 cursor-pointer transition-all disabled:opacity-50`}
                        >
                          {resolvingProposalId === prop.id ? (
                            <><RefreshCw size={11} className="animate-spin" />{t('scenario.decision.writing')}</>
                          ) : (
                            <><Check size={12} />{t('scenario.decision.approve')}</>
                          )}
                        </button>
                      </>
                    ) : (
                      <span className={`text-[11px] ${styles.cardTextMuted} font-bold flex items-center gap-1`}>
                        <Archive size={12} />
                        {t('scenario.decision.archived')}
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 批准二次确认门（不可逆写回） */}
      <ConfirmDialog
        visible={approveTarget !== null}
        variant="warning"
        title={t('scenario.proposal.approveTitle')}
        message={`${t('scenario.proposal.approveMessage')}${approveTarget ? ` (${approveTarget.actionName || approveTarget.actionId || approveTarget.id})` : ''}`}
        confirmText={t('scenario.proposal.approveConfirm')}
        cancelText={t('common.cancel')}
        onConfirm={confirmApprove}
        onCancel={() => setApproveTarget(null)}
      />
    </div>
  );
}
