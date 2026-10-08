/**
 * ECOS 场景工作台 — decision Tab
 * 从 ScenarioManagementView.tsx L1265-1504 拆分。
 * 引用的父级状态: ['activeScenario', 'fetchProposalsList', 'handleApproveProposal', 'handleRejectProposal', 'handleRunSandbox', 'isLoadingProposals', 'isSimulating', 'proposals', 'resolvingProposalId', 'setSimQuery', 'setSimRole', 'simQuery', 'simResult', 'simRole']
 */

import React from 'react';

import LucideIcon from '../../../components/LucideIcon';
import { useLanguage } from '../../../components/LanguageContext';
import type { BusinessScenario } from '../types';

interface Props {
  activeScenario: BusinessScenario;
  proposals: any[];
  isLoadingProposals: boolean;
  fetchProposalsList: () => Promise<void>;
  handleApproveProposal: (id: string, actionId: string) => Promise<void>;
  handleRejectProposal: (id: string, actionId: string) => Promise<void>;
  resolvingProposalId: string | null;
  simQuery: string;
  setSimQuery: (v: string) => void;
  simRole: 'AOC_DIRECTOR' | 'EXTERNAL_CONTRACTOR';
  setSimRole: (v: any) => void;
  simResult: any | null;
  isSimulating: boolean;
  handleRunSandbox: () => Promise<void>;
}

export default function DecisionDeskTab({
  activeScenario, proposals, isLoadingProposals, fetchProposalsList,
  handleApproveProposal, handleRejectProposal, resolvingProposalId,
  simQuery, setSimQuery, simRole, setSimRole, simResult, isSimulating, handleRunSandbox
}: Props) {
  const { t } = useLanguage();
  return (
<div className="space-y-4">

  {/* 1. Interactive Pending Action Proposals */}
  <div className="bg-[var(--card,#0F172A)] border border-[var(--card,#1E293B)] rounded-xl overflow-hidden">
    <div className="p-4 bg-[var(--card,#020617)] border-b border-[var(--card,#1E293B)] flex items-center justify-between">
      <div>
        <h3 className="text-sm font-bold text-white flex items-center gap-2">
          <span className="flex h-2 w-2 relative">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2 w-2 bg-rose-500"></span>
          </span>
          {t('scenario.decision.proposals.title')}
        </h3>
        <p className="text-xs text-[var(--card,#94A3B8)] mt-1">{t('scenario.decision.proposals.subtitle')}</p>
      </div>

      <button
        type="button"
        onClick={fetchProposalsList}
        disabled={isLoadingProposals}
        className="px-3 py-1.5 bg-[var(--card,#1E293B)] hover:bg-[var(--card,#334155)] disabled:opacity-50 text-[var(--card,#E2E8F0)] text-xs font-bold rounded flex items-center gap-1 transition-all cursor-pointer"
      >
        <LucideIcon name="RefreshCw" size={11} className={isLoadingProposals ? 'animate-spin' : ''} />
        {t('scenario.decision.proposals.refresh')}
      </button>
    </div>

    {proposals.length === 0 ? (
      <div className="p-8 text-center text-[var(--card,#64748B)] space-y-2">
        <LucideIcon name="Inbox" size={32} className="mx-auto opacity-30 text-[var(--card,#94A3B8)]" />
        <p className="text-xs">{t('scenario.decision.proposals.empty')}</p>
        <p className="text-[10px] text-[var(--card,#64748B)]">{t('scenario.decision.proposals.emptyHint')}</p>
      </div>
    ) : (
      <div className="divide-y divide-[var(--card,#1E293B)]/80 max-h-96 overflow-y-auto">
        {proposals.map((prop: any) => {
          const isPending = prop.status === 'pending';
          const isApproved = prop.status === 'approved';
          const isRejected = prop.status === 'rejected';

          return (
            <div key={prop.id} className="p-4 flex flex-col md:flex-row md:items-center justify-between gap-4 hover:bg-[var(--card,#1E293B)]/50 transition-all">
              <div className="space-y-1.5 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-extrabold text-white">{prop.actionName || prop.actionId}</span>
                  <span className={`text-[9px] font-bold px-1.5 py-0.2 rounded font-mono ${
                    isPending ? 'bg-amber-950 text-amber-400 border border-amber-900/40 animate-pulse' :
                    isApproved ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/40' : 'bg-red-950 text-red-400 border border-red-900/40'
                  }`}>
                    {prop.status.toUpperCase()}
                  </span>
                  <span className="text-[10px] text-[var(--card,#94A3B8)] font-mono">ID: {prop.id}</span>
                </div>

                <div className="text-xs text-[var(--card,#CBD5E1)] font-mono bg-[var(--card,#020617)]/80 p-2.5 rounded border border-[var(--card,#1E293B)]/60 overflow-x-auto">
                  <span className="text-[10px] text-indigo-400 block font-bold mb-1">// Proposed Parameter Values</span>
                  {JSON.stringify(prop.payload, null, 2)}
                </div>

                <div className="flex items-center gap-4 text-[10px] text-[var(--card,#94A3B8)] font-mono">
                  <span className="flex items-center gap-1">
                    <LucideIcon name="User" size={10} className="text-[var(--card,#64748B)]" />
                    {t('scenario.decision.proposals.proposedBy', { by: prop.proposedBy || t('scenario.decision.proposals.sandboxAgent') })}
                  </span>
                  {prop.rejectReason && (
                    <span className="text-rose-400 font-sans">
                      🔴 {t('scenario.decision.proposals.rejectReason', { reason: prop.rejectReason })}
                    </span>
                  )}
                </div>
              </div>

              <div className="flex items-center gap-2 shrink-0 md:self-center">
                {isPending ? (
                  <>
                    <button
                      type="button"
                      onClick={() => handleRejectProposal(prop.id, prop.actionId)}
                      disabled={resolvingProposalId === prop.id}
                      className="px-2.5 py-1.5 bg-red-950/40 hover:bg-red-900/50 border border-red-900/60 text-red-400 text-xs font-bold rounded cursor-pointer transition-all disabled:opacity-50"
                    >
                      {t('scenario.decision.proposals.reject')}
                    </button>
                    <button
                      type="button"
                      onClick={() => handleApproveProposal(prop.id, prop.actionId)}
                      disabled={resolvingProposalId === prop.id}
                      className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold rounded flex items-center gap-1 cursor-pointer transition-all disabled:opacity-50 shadow-sm shadow-indigo-900/30"
                    >
                      {resolvingProposalId === prop.id ? (
                        <>
                          <LucideIcon name="RefreshCw" size={11} className="animate-spin" />
                          <span>{t('scenario.decision.proposals.writing')}</span>
                        </>
                      ) : (
                        <>
                          <LucideIcon name="Check" size={12} />
                          <span>{t('scenario.decision.proposals.approve')}</span>
                        </>
                      )}
                    </button>
                  </>
                ) : (
                  <span className="text-[11px] text-[var(--card,#64748B)] font-bold flex items-center gap-1">
                    <LucideIcon name="Archive" size={12} />
                    {t('scenario.decision.proposals.archived')}
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    )}
  </div>

  {/* 2. Enterprise Real-time Decision Sandbox Simulation */}
  <div className="bg-[var(--card,#0F172A)] border border-[var(--card,#1E293B)] rounded-xl overflow-hidden p-4 space-y-4">
    <div>
      <h3 className="text-sm font-bold text-white flex items-center gap-1.5">
        <LucideIcon name="Laptop" size={14} className="text-indigo-400" />
        {t('scenario.decision.sandbox.title')}
      </h3>
      <p className="text-xs text-[var(--card,#94A3B8)] mt-1">{t('scenario.decision.sandbox.subtitle')}</p>
    </div>

    <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
      {/* Simulator Inputs */}
      <div className="space-y-3 p-3 bg-[var(--card,#020617)]/40 border border-[var(--card,#1E293B)] rounded-lg">
        <span className="text-[11px] font-bold text-[var(--card,#CBD5E1)] block border-b border-[var(--card,#1E293B)] pb-1.5">{t('scenario.decision.sandbox.params')}</span>

        <div className="space-y-1">
          <label className="text-[10px] text-[var(--card,#94A3B8)] font-semibold block">{t('scenario.decision.sandbox.roleLabel')}</label>
          <select
            value={simRole}
            onChange={(e) => setSimRole(e.target.value as any)}
            className="w-full p-2 bg-[var(--card,#0F172A)] border border-[var(--card,#1E293B)] rounded text-xs font-bold text-white outline-none"
          >
            <option value="AOC_DIRECTOR">{t('scenario.decision.sandbox.roleAoc')}</option>
            <option value="EXTERNAL_CONTRACTOR">{t('scenario.decision.sandbox.roleContractor')}</option>
          </select>
        </div>

        <div className="space-y-1">
          <label className="text-[10px] text-[var(--card,#94A3B8)] font-semibold block">{t('scenario.decision.sandbox.queryLabel')}</label>
          <input
            type="text"
            value={simQuery}
            onChange={(e) => setSimQuery(e.target.value)}
            className="w-full p-2 bg-[var(--card,#0F172A)] border border-[var(--card,#1E293B)] rounded text-xs text-white outline-none font-sans"
          />
        </div>

        <div className="grid grid-cols-2 gap-2 text-[10px] text-[var(--card,#94A3B8)] font-mono bg-[var(--card,#0F172A)]/60 p-2 rounded border border-[var(--card,#1E293B)]/60 leading-normal">
          <div>
            <span className="block text-[8px] text-[var(--card,#64748B)]">Simulated IP:</span>
            <span className={simRole === 'AOC_DIRECTOR' ? 'text-emerald-400 font-bold' : 'text-rose-400 font-bold'}>
              {simRole === 'AOC_DIRECTOR' ? '10.120.5.23' : '222.22.22.22'}
            </span>
          </div>
          <div>
            <span className="block text-[8px] text-[var(--card,#64748B)]">Security Project:</span>
            <span className="text-[var(--card,#CBD5E1)]">{activeScenario.bindings.securityPolicies[1] || 'proj_aviation_core'}</span>
          </div>
        </div>

        <button
          type="button"
          onClick={handleRunSandbox}
          disabled={isSimulating}
          className="w-full py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white font-bold text-xs rounded transition-all cursor-pointer flex items-center justify-center gap-1.5"
        >
          {isSimulating ? (
            <>
              <LucideIcon name="RefreshCw" size={12} className="animate-spin" />
              <span>{t('scenario.decision.sandbox.simulating')}</span>
            </>
          ) : (
            <>
              <LucideIcon name="Play" size={12} />
              <span>{t('scenario.decision.sandbox.run')}</span>
            </>
          )}
        </button>
      </div>

      {/* Simulator Results & Log Traces */}
      <div className="lg:col-span-2 flex flex-col bg-[var(--card,#020617)] p-3 rounded-lg border border-[var(--card,#1E293B)] font-mono">
        <div className="flex items-center justify-between border-b border-[var(--card,#1E293B)] pb-2 mb-2">
          <span className="text-[11px] font-bold text-[var(--card,#CBD5E1)] flex items-center gap-1">
            <LucideIcon name="Terminal" size={11} className="text-emerald-500" />
            {t('scenario.decision.sandbox.console')}
          </span>
          {simResult && (
            <span className={`text-[9px] font-extrabold px-1.5 py-0.2 rounded ${
              simResult.verdict === 'GRANTED' ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/40' : 'bg-rose-950 text-rose-400 border border-rose-900/40'
            }`}>
              VERDICT: {simResult.verdict}
            </span>
          )}
        </div>

        <div className="flex-1 overflow-y-auto space-y-3 min-h-40 max-h-56 p-1 text-[11px] leading-relaxed">
          {simResult ? (
            <>
              <div className="space-y-1">
                <span className="text-[10px] text-indigo-400 block font-bold">{t('scenario.decision.sandbox.ragTitle')}</span>
                <div className="bg-[var(--card,#0F172A)] p-2.5 rounded border border-[var(--card,#1E293B)] text-[var(--card,#CBD5E1)] font-sans whitespace-pre-wrap">
                  {simResult.answer}
                </div>
              </div>

              {simResult.groundedDocs && simResult.groundedDocs.length > 0 && (
                <div className="space-y-1">
                  <span className="text-[10px] text-amber-400 block font-bold">{t('scenario.decision.sandbox.groundedTitle')}</span>
                  <div className="space-y-1">
                    {simResult.groundedDocs.map((doc: any, i: number) => (
                      <div key={i} className="bg-[var(--card,#0F172A)]/50 p-1.5 rounded border border-[var(--card,#1E293B)] flex items-center justify-between text-[var(--card,#94A3B8)]">
                        <span>[{i+1}] {doc.title}</span>
                        <span className="text-[10px] text-emerald-400 font-bold">Similarity Score: {(doc.score * 100).toFixed(1)}%</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div className="space-y-1 text-[var(--card,#64748B)] text-[9px]">
                <span>[System Event] Decision flow processed in 18ms by Rust-backed Databridge Crate.</span>
                <span>[System Event] Security audit log recorded in server.ts under {simRole === 'AOC_DIRECTOR' ? 'GRANTED' : 'DENIED'} category.</span>
              </div>
            </>
          ) : (
            <div className="h-full flex items-center justify-center text-[var(--card,#64748B)] text-xs">
              {t('scenario.decision.sandbox.empty')}
            </div>
          )}
        </div>
      </div>
    </div>
  </div>

</div>
  );
}
