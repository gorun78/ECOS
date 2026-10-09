/**
 * ECOS 场景工作台 — decision Tab（详细设计-07 F07-22 / F07-23 收口）。
 *
 * <p>原文件含「管理者零信任准入拦截沙箱」：内联硬编码审批人身份、前端伪造 {@code verdict}、
 * 固定角色与固定 clientIp 及固定耗时文案，属 P0 虚假验收族。该仿真分支
 * 整体删除：认知/准入结论只来自 {@code useCognition} 四端点（见 SimulationResultPanel /
 * CognitiveRightPanel），本 Tab 仅保留<b>真实 API</b> 的对账提案审批，补 §3.3 六态。</p>
 */
import React from 'react';
import LucideIcon from '../../../components/LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import type { BusinessScenario } from '../types';

interface Proposal {
  id: string;
  actionId?: string;
  actionName?: string;
  status: string;
  payload?: unknown;
  proposedBy?: string;
  rejectReason?: string;
}

interface Props {
  activeScenario: BusinessScenario;
  proposals: unknown[];
  isLoadingProposals: boolean;
  fetchProposalsList: () => Promise<void>;
  handleApproveProposal: (id: string, actionId: string) => Promise<void>;
  handleRejectProposal: (id: string, actionId: string) => Promise<void>;
  resolvingProposalId: string | null;
}

export default function DecisionDeskTab({
  activeScenario, proposals, isLoadingProposals, fetchProposalsList,
  handleApproveProposal, handleRejectProposal, resolvingProposalId,
}: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const rows = proposals as Proposal[];
  const pendingCount = rows.filter((p) => p.status === 'pending').length;

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
            <p className={`text-xs ${styles.cardTextMuted} mt-1`}>
              {t('scenario.decision.subtitle', { scenario: activeScenario.name })}
            </p>
          </div>
          <button
            onClick={fetchProposalsList}
            disabled={isLoadingProposals}
            className={`shrink-0 px-3 py-1.5 border ${styles.cardBorder} ${styles.cardText} text-xs font-bold rounded flex items-center gap-1 transition-all cursor-pointer hover:opacity-80 disabled:opacity-50`}
          >
            <LucideIcon name="RefreshCw" size={11} className={isLoadingProposals ? 'animate-spin' : ''} />
            {t('scenario.decision.refresh')}
          </button>
        </div>

        {/* 加载态 */}
        {isLoadingProposals && rows.length === 0 && (
          <div className={`p-8 text-center ${styles.cardTextMuted}`}>{t('scenario.cognition.loading')}</div>
        )}

        {/* 空态 */}
        {!isLoadingProposals && rows.length === 0 && (
          <div className={`p-8 text-center ${styles.cardTextMuted} space-y-2`}>
            <LucideIcon name="Inbox" size={32} className={`mx-auto opacity-40`} />
            <p className="text-xs">{t('scenario.decision.empty')}</p>
          </div>
        )}

        {rows.length > 0 && (
          <div className={`divide-y divide-[var(--border)] max-h-96 overflow-y-auto`}>
            {rows.map((prop) => {
              const isPending = prop.status === 'pending';
              return (
                <div key={prop.id} className="p-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
                  <div className="space-y-1.5 flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`text-xs font-extrabold ${styles.cardText}`}>{prop.actionName || prop.actionId || prop.id}</span>
                      <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded font-mono ${isPending ? `${styles.warningBg} ${styles.warningText}` : `${styles.cardBg} ${styles.cardTextMuted}`} border ${styles.cardBorder}`}>
                        {prop.status.toUpperCase()}
                      </span>
                    </div>
                    <pre className={`text-[10px] ${styles.cardTextMuted} font-mono ${styles.inputBg} p-2.5 rounded border ${styles.inputBorder} overflow-x-auto`}>
                      {JSON.stringify(prop.payload ?? {}, null, 2)}
                    </pre>
                    <div className={`flex items-center gap-4 text-[10px] ${styles.cardTextMuted} font-mono`}>
                      <span className="flex items-center gap-1">
                        <LucideIcon name="User" size={10} />
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
                          onClick={() => handleRejectProposal(prop.id, prop.actionId ?? prop.id)}
                          disabled={resolvingProposalId === prop.id}
                          className={`px-2.5 py-1.5 border ${styles.dangerBorder ?? styles.cardBorder} ${styles.dangerText} text-xs font-bold rounded cursor-pointer transition-all disabled:opacity-50`}
                        >
                          {t('scenario.decision.reject')}
                        </button>
                        <button
                          onClick={() => handleApproveProposal(prop.id, prop.actionId ?? prop.id)}
                          disabled={resolvingProposalId === prop.id}
                          className={`px-3 py-1.5 ${styles.accentBg} border ${styles.accentBorder} ${styles.cardText} text-xs font-bold rounded flex items-center gap-1 cursor-pointer transition-all disabled:opacity-50`}
                        >
                          {resolvingProposalId === prop.id ? (
                            <><LucideIcon name="RefreshCw" size={11} className="animate-spin" />{t('scenario.decision.writing')}</>
                          ) : (
                            <><LucideIcon name="Check" size={12} />{t('scenario.decision.approve')}</>
                          )}
                        </button>
                      </>
                    ) : (
                      <span className={`text-[11px] ${styles.cardTextMuted} font-bold flex items-center gap-1`}>
                        <LucideIcon name="Archive" size={12} />
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
    </div>
  );
}
