/**
 * ECOS 项目工作台 — decision Tab
 * 从 ScenarioManagementView.tsx L1265-1504 拆分。
 * 引用的父级状态: ['activeScenario', 'fetchProposalsList', 'handleApproveProposal', 'handleRejectProposal', 'handleRunSandbox', 'isLoadingProposals', 'isSimulating', 'proposals', 'resolvingProposalId', 'setSimQuery', 'setSimRole', 'simQuery', 'simResult', 'simRole']
 */

import React from 'react';

import LucideIcon from '../../../components/LucideIcon';
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
  return (
<div className="space-y-4">

  {/* 1. Interactive Pending Action Proposals */}
  <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
    <div className="p-4 bg-slate-950 border-b border-slate-800 flex items-center justify-between">
      <div>
        <h3 className="text-sm font-bold text-white flex items-center gap-2">
          <span className="flex h-2 w-2 relative">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2 w-2 bg-rose-500"></span>
          </span>
          AOC 核心签派动作待审批提案 (Pending Write-back Proposals)
        </h3>
        <p className="text-xs text-slate-400 mt-1">此处汇总了由 AI 智能体/分析员在沙箱中触发的写回事务，需 AOC 签派总监（王凯）二次签名授权即可物理同步落库物理表。</p>
      </div>

      <button
        onClick={fetchProposalsList}
        disabled={isLoadingProposals}
        className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-slate-200 text-xs font-bold rounded flex items-center gap-1 transition-all cursor-pointer"
      >
        <LucideIcon name="RefreshCw" size={11} className={isLoadingProposals ? 'animate-spin' : ''} />
        刷新列表
      </button>
    </div>

    {proposals.length === 0 ? (
      <div className="p-8 text-center text-slate-500 space-y-2">
        <LucideIcon name="Inbox" size={32} className="mx-auto opacity-30 text-slate-400" />
        <p className="text-xs">暂无待审批或已归档的 Ontology Action 提案。</p>
        <p className="text-[10px] text-slate-600">您可以在「AI工作台」运行 Chatbot 智能对话并输入“延误”以发起全新修改提案！</p>
      </div>
    ) : (
      <div className="divide-y divide-slate-800/80 max-h-96 overflow-y-auto">
        {proposals.map((prop: any) => {
          const isPending = prop.status === 'pending';
          const isApproved = prop.status === 'approved';
          const isRejected = prop.status === 'rejected';

          return (
            <div key={prop.id} className="p-4 flex flex-col md:flex-row md:items-center justify-between gap-4 hover:bg-slate-850/50 transition-all">
              <div className="space-y-1.5 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-extrabold text-white">{prop.actionName || prop.actionId}</span>
                  <span className={`text-[9px] font-bold px-1.5 py-0.2 rounded font-mono ${
                    isPending ? 'bg-amber-950 text-amber-400 border border-amber-900/40 animate-pulse' :
                    isApproved ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/40' : 'bg-red-950 text-red-400 border border-red-900/40'
                  }`}>
                    {prop.status.toUpperCase()}
                  </span>
                  <span className="text-[10px] text-slate-400 font-mono">ID: {prop.id}</span>
                </div>

                <div className="text-xs text-slate-300 font-mono bg-slate-950/80 p-2.5 rounded border border-slate-800/60 overflow-x-auto">
                  <span className="text-[10px] text-indigo-400 block font-bold mb-1">// Proposed Parameter Values</span>
                  {JSON.stringify(prop.payload, null, 2)}
                </div>

                <div className="flex items-center gap-4 text-[10px] text-slate-400 font-mono">
                  <span className="flex items-center gap-1">
                    <LucideIcon name="User" size={10} className="text-slate-500" />
                    提议源: {prop.proposedBy || '智能体沙箱'}
                  </span>
                  {prop.rejectReason && (
                    <span className="text-rose-400 font-sans">
                      🔴 拒绝理由: {prop.rejectReason}
                    </span>
                  )}
                </div>
              </div>

              <div className="flex items-center gap-2 shrink-0 md:self-center">
                {isPending ? (
                  <>
                    <button
                      onClick={() => handleRejectProposal(prop.id, prop.actionId)}
                      disabled={resolvingProposalId === prop.id}
                      className="px-2.5 py-1.5 bg-red-950/40 hover:bg-red-900/50 border border-red-900/60 text-red-400 text-xs font-bold rounded cursor-pointer transition-all disabled:opacity-50"
                    >
                      安全拒绝
                    </button>
                    <button
                      onClick={() => handleApproveProposal(prop.id, prop.actionId)}
                      disabled={resolvingProposalId === prop.id}
                      className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold rounded flex items-center gap-1 cursor-pointer transition-all disabled:opacity-50 shadow-sm shadow-indigo-900/30"
                    >
                      {resolvingProposalId === prop.id ? (
                        <>
                          <LucideIcon name="RefreshCw" size={11} className="animate-spin" />
                          <span>正在物理写入...</span>
                        </>
                      ) : (
                        <>
                          <LucideIcon name="Check" size={12} />
                          <span>总监审核放行</span>
                        </>
                      )}
                    </button>
                  </>
                ) : (
                  <span className="text-[11px] text-slate-500 font-bold flex items-center gap-1">
                    <LucideIcon name="Archive" size={12} />
                    该对账已归档
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
  <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden p-4 space-y-4">
    <div>
      <h3 className="text-sm font-bold text-white flex items-center gap-1.5">
        <LucideIcon name="Laptop" size={14} className="text-indigo-400" />
        管理者零信任准入拦截沙箱 (Executive Zero-Trust Access Tester)
      </h3>
      <p className="text-xs text-slate-400 mt-1">模拟不同职能角色从特定网络终端访问本场景数据的真实准入结果，测试 PBAC/DAC 和 PII 脱敏逻辑的阻断能力。</p>
    </div>

    <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
      {/* Simulator Inputs */}
      <div className="space-y-3 p-3 bg-slate-950/40 border border-slate-800 rounded-lg">
        <span className="text-[11px] font-bold text-slate-300 block border-b border-slate-800 pb-1.5">参数装配 (Simulation Profile)</span>

        <div className="space-y-1">
          <label className="text-[10px] text-slate-400 font-semibold block">模拟访问主体角色 (Subject Role)</label>
          <select
            value={simRole}
            onChange={(e) => setSimRole(e.target.value as any)}
            className="w-full p-2 bg-slate-900 border border-slate-800 rounded text-xs font-bold text-white outline-none"
          >
            <option value="AOC_DIRECTOR">王凯 (AOC 签派总监 - 白名单 IP 段)</option>
            <option value="EXTERNAL_CONTRACTOR">张杰 (外部承包商 - 阻断非白名单 IP / 涉敏感 PII)</option>
          </select>
        </div>

        <div className="space-y-1">
          <label className="text-[10px] text-slate-400 font-semibold block">检索提问 (Retrieval Query Target)</label>
          <input
            type="text"
            value={simQuery}
            onChange={(e) => setSimQuery(e.target.value)}
            className="w-full p-2 bg-slate-900 border border-slate-800 rounded text-xs text-white outline-none font-sans"
          />
        </div>

        <div className="grid grid-cols-2 gap-2 text-[10px] text-slate-400 font-mono bg-slate-900/60 p-2 rounded border border-slate-800/60 leading-normal">
          <div>
            <span className="block text-[8px] text-slate-500">Simulated IP:</span>
            <span className={simRole === 'AOC_DIRECTOR' ? 'text-emerald-400 font-bold' : 'text-rose-400 font-bold'}>
              {simRole === 'AOC_DIRECTOR' ? '10.120.5.23' : '222.22.22.22'}
            </span>
          </div>
          <div>
            <span className="block text-[8px] text-slate-500">Security Project:</span>
            <span className="text-slate-300">{activeScenario.bindings.securityPolicies[1] || 'proj_aviation_core'}</span>
          </div>
        </div>

        <button
          onClick={handleRunSandbox}
          disabled={isSimulating}
          className="w-full py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white font-bold text-xs rounded transition-all cursor-pointer flex items-center justify-center gap-1.5"
        >
          {isSimulating ? (
            <>
              <LucideIcon name="RefreshCw" size={12} className="animate-spin" />
              <span>正在模拟计算...</span>
            </>
          ) : (
            <>
              <LucideIcon name="Play" size={12} />
              <span>运行零信任鉴权验证</span>
            </>
          )}
        </button>
      </div>

      {/* Simulator Results & Log Traces */}
      <div className="lg:col-span-2 flex flex-col bg-slate-950 p-3 rounded-lg border border-slate-800 font-mono">
        <div className="flex items-center justify-between border-b border-slate-850 pb-2 mb-2">
          <span className="text-[11px] font-bold text-slate-300 flex items-center gap-1">
            <LucideIcon name="Terminal" size={11} className="text-emerald-500" />
            沙箱零信任控制流输出 (Console Logs)
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
                <span className="text-[10px] text-indigo-400 block font-bold">// 1. Cognitive RAG Output (大模型零信任答复)</span>
                <div className="bg-slate-900 p-2.5 rounded border border-slate-850 text-slate-300 font-sans whitespace-pre-wrap">
                  {simResult.answer}
                </div>
              </div>

              {simResult.groundedDocs && simResult.groundedDocs.length > 0 && (
                <div className="space-y-1">
                  <span className="text-[10px] text-amber-400 block font-bold">// 2. Grounded Prior Knowledge Chunks (向量相似度召回)</span>
                  <div className="space-y-1">
                    {simResult.groundedDocs.map((doc: any, i: number) => (
                      <div key={i} className="bg-slate-900/50 p-1.5 rounded border border-slate-850 flex items-center justify-between text-slate-400">
                        <span>[{i+1}] {doc.title}</span>
                        <span className="text-[10px] text-emerald-400 font-bold">Similarity Score: {(doc.score * 100).toFixed(1)}%</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div className="space-y-1 text-slate-500 text-[9px]">
                <span>[System Event] Decision flow processed in 18ms by Rust-backed Databridge Crate.</span>
                <span>[System Event] Security audit log recorded in server.ts under {simRole === 'AOC_DIRECTOR' ? 'GRANTED' : 'DENIED'} category.</span>
              </div>
            </>
          ) : (
            <div className="h-full flex items-center justify-center text-slate-600 text-xs">
              点击左侧「运行零信任鉴权验证」查看仿真结果。
            </div>
          )}
        </div>
      </div>
    </div>
  </div>

</div>
  );
}
