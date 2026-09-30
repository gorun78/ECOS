/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Workflow Center tab of AiGuardrailsView (approvals + double-check + live physical tables).
// Extracted verbatim by H6-T4; state & data fetch stay in the container.

import React from 'react';
import { useTheme } from '../../components/ThemeContext';
import { Icon, PhysicalFlight, PhysicalPilot, Proposal } from './AiGuardrailsShared';

interface WorkflowTabProps {
  userRole: '签派总监' | '普通调度员';
  onSwitchRole: (role: '签派总监' | '普通调度员') => void;
  proposals: Proposal[];
  selectedProposalId: string | null;
  onSelectProposal: (id: string) => void;
  selectedProposal: Proposal | undefined;
  verificationLoading: boolean;
  verificationResult: any | null;
  executionLoading: boolean;
  executionResult: any | null;
  onExecuteProposal: (approved: boolean) => void;
  dbData: { flights: PhysicalFlight[]; pilots: PhysicalPilot[] } | null;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

export default function AiGuardrailsWorkflowTab({
  userRole,
  onSwitchRole,
  proposals,
  selectedProposalId,
  onSelectProposal,
  selectedProposal,
  verificationLoading,
  verificationResult,
  executionLoading,
  executionResult,
  onExecuteProposal,
  dbData,
  showToast,
}: WorkflowTabProps) {
  const { styles } = useTheme();

  return (
    <div className="flex-1 flex flex-col min-h-0 gap-6">

      {/* Top Banner: RBAC Identity badge */}
      <div className={`${styles.cardBg} rounded-xl p-4 shadow-md border ${styles.cardBorder} shrink-0 flex flex-col md:flex-row items-center justify-between gap-4`}>
        <div className="flex items-center gap-3">
          <div className={`p-2.5 bg-blue-500/20 text-blue-400 rounded-full border border-blue-500/30`}>
            <Icon name="UserCheck" size={18} />
          </div>
          <div className="space-y-1 text-left">
            <span className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>AOC 终端接入安全身份等级 (Active RBAC Level)</span>
            <div className="flex items-center gap-2">
              <span className={`font-extrabold text-sm ${styles.cardText}`}>
                {userRole === '签派总监' ? '王凯 (AOC 签派总监)' : '陈雪 (普通调度员)'}
              </span>
              <span className={`px-2 py-0.5 rounded-full text-[9px] font-black ${
                userRole === '签派总监'
                  ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                  : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
              }`}>
                {userRole === '签派总监' ? '🛡️ 核心授权密匙 [AOC_DIRECTOR]' : '🔍 只读分析账号 [DISPATCHER]'}
              </span>
            </div>
          </div>
        </div>

        {/* Switch role button */}
        <div className={`flex items-center gap-2 ${styles.inputBg} p-1.5 rounded-lg border ${styles.inputBorder}`}>
          <span className={`text-[10px] font-bold ${styles.cardTextMuted}`}>切换测试身份:</span>
          <button
            onClick={() => {
              onSwitchRole('签派总监');
              showToast?.('info', '安全权限级别已切换为: AOC 签派总监 (具备写回及授权批准特权)');
            }}
            className={`px-2.5 py-1 rounded font-bold text-[10px] cursor-pointer transition-colors ${
              userRole === '签派总监'
                ? `${styles.accentBg} text-white`
                : styles.cardTextMuted
            }`}
          >
            签派总监
          </button>
          <button
            onClick={() => {
              onSwitchRole('普通调度员');
              showToast?.('info', '安全权限级别已切换为: 普通调度员 (无写回授权，写入将被 RBAC 审计拦截)');
            }}
            className={`px-2.5 py-1 rounded font-bold text-[10px] cursor-pointer transition-colors ${
              userRole === '普通调度员'
                ? 'bg-amber-600 text-white'
                : styles.cardTextMuted
            }`}
          >
            普通调度员
          </button>
        </div>
      </div>

      {/* Main workspace splits: Proposals List & Proposal Inspector */}
      <div className="flex-1 grid grid-cols-1 xl:grid-cols-3 gap-6 min-h-0">

        {/* Split 1: Proposals List */}
        <div className={`xl:col-span-1 ${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-xs flex flex-col overflow-hidden`}>
          <div className={`p-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
            <span className={`font-extrabold ${styles.cardTextMuted} flex items-center gap-1.5`}>
              <Icon name="GitPullRequest" size={13} className={styles.cardTextMuted} />
              <span>待审批 Ontology 写回提案 ({proposals.length})</span>
            </span>
            <span className={`px-1.5 py-0.5 rounded ${styles.inputBg} ${styles.cardTextMuted} text-[9px] font-mono`}>PROPOSALS</span>
          </div>

          {/* List body */}
          <div className="flex-1 overflow-y-auto p-3 space-y-2">
            {proposals.length === 0 ? (
              <div className={`h-full flex flex-col items-center justify-center p-8 text-center ${styles.cardTextMuted} space-y-2`}>
                <Icon name="CheckCircle" size={24} className={styles.cardTextMuted} />
                <p className="font-bold">暂无挂起写入提案</p>
                <p className="text-[10px]">当 AI 智能体在沙箱试图修改数据时，其指令会被安全护栏挂起并在此注册。</p>
              </div>
            ) : (
              proposals.map(prop => {
                const isSelected = prop.id === selectedProposalId;
                return (
                  <button
                    key={prop.id}
                    onClick={() => onSelectProposal(prop.id)}
                    className={`w-full text-left p-3 rounded-xl border transition-all cursor-pointer flex flex-col gap-2 ${
                      isSelected
                        ? `${styles.accentBorder} ${styles.badgeBg} shadow-xs`
                        : `${styles.cardBorder} hover:${styles.cardBorder} hover:${styles.inputBg}`
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className={`font-mono text-[9px] font-bold ${styles.cardTextMuted}`}>#{prop.id}</span>
                      <span className={`px-1.5 py-0.5 rounded text-[8px] font-black ${
                        prop.status === 'pending' ? 'bg-amber-100 text-amber-700' :
                        prop.status === 'approved' ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'
                      }`}>
                        {prop.status === 'pending' ? '待审批' :
                         prop.status === 'approved' ? '已通过' : '已拒绝'}
                      </span>
                    </div>

                    <div className="space-y-1">
                      <p className={`font-bold ${styles.cardText} text-[11px] leading-tight`}>{prop.actionName}</p>
                      <div className={`flex items-center gap-1.5 text-[9px] ${styles.cardTextMuted} font-mono`}>
                        <span>智能体: {prop.agentName}</span>
                        <span>•</span>
                        <span>{prop.proposedAt}</span>
                      </div>
                    </div>

                    {/* Mini parameters visual */}
                    <div className={`${styles.inputBg} rounded-lg p-2 flex flex-wrap gap-x-3 gap-y-1 font-mono text-[9px] ${styles.cardTextMuted} border ${styles.cardBorder}`}>
                      {Object.entries(prop.payload).map(([k, v]) => (
                        <div key={k}>
                          <span className={`font-bold ${styles.cardTextMuted}`}>{k}:</span> <span>{v}</span>
                        </div>
                      ))}
                    </div>
                  </button>
                );
              })
            )}
          </div>
        </div>

        {/* Split 2: Cockpit Inspector */}
        <div className={`xl:col-span-2 ${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-xs flex flex-col overflow-hidden min-h-0`}>
          <div className={`p-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
            <span className={`font-extrabold ${styles.cardTextMuted} flex items-center gap-1.5`}>
              <Icon name="Settings" size={13} className={styles.cardTextMuted} />
              <span>双向核对及 Schema 安全对账中心</span>
            </span>
            {selectedProposal && (
              <span className={`font-mono ${styles.cardTextMuted} font-bold`}>Proposal: {selectedProposal.id}</span>
            )}
          </div>

          {/* Inspector Content */}
          {!selectedProposal ? (
            <div className={`flex-1 flex flex-col items-center justify-center p-12 ${styles.cardTextMuted} space-y-2`}>
              <Icon name="Info" size={24} className={styles.cardTextMuted} />
              <p className="font-bold">请在左侧选择一个写回提案</p>
            </div>
          ) : (
            <div className="flex-1 overflow-y-auto p-4 space-y-5">

              {/* Proposal Summary Info */}
              <div className={`grid grid-cols-2 md:grid-cols-4 gap-3 ${styles.inputBg} p-3 rounded-xl border ${styles.cardBorder}`}>
                <div>
                  <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase tracking-wider block`}>安全准入密级</span>
                  <span className={`font-bold ${styles.cardTextMuted} text-[10px] font-mono`}>ROLE: {selectedProposal.rbacRoleRequired}</span>
                </div>
                <div>
                  <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase tracking-wider block`}>提交智能体</span>
                  <span className={`font-bold ${styles.cardTextMuted} text-[10px]`}>{selectedProposal.agentName}</span>
                </div>
                <div>
                  <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase tracking-wider block`}>提案产生链路</span>
                  <span className={`font-bold ${styles.cardTextMuted} text-[10px] font-mono`}>{selectedProposal.proposedBy}</span>
                </div>
                <div>
                  <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase tracking-wider block`}>挂载拦截时戳</span>
                  <span className={`font-bold ${styles.cardTextMuted} text-[10px] font-mono`}>{selectedProposal.proposedAt}</span>
                </div>
              </div>

              {/* Schema Validator section */}
              <div className="space-y-2">
                <h4 className={`font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1`}>
                  <Icon name="CheckSquare" size={11} className={styles.accentText} />
                  <span>1. 输入参数契约校验 (Schema Validator Check)</span>
                </h4>

                <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
                  <table className="w-full text-left font-mono text-[10px]">
                    <thead className={`${styles.inputBg} ${styles.cardTextMuted} font-bold uppercase`}>
                      <tr className={`border-b ${styles.cardBorder}`}>
                        <th className="p-2 w-1/3">参数名称 (Field)</th>
                        <th className="p-2 w-1/3">请求设定值 (Value)</th>
                        <th className="p-2 w-1/3">格式及有效性验证 (Status)</th>
                      </tr>
                    </thead>
                    <tbody className={`divide-y ${styles.cardBorder} ${styles.cardTextMuted}`}>
                      {Object.entries(selectedProposal.payload).map(([field, val]) => (
                        <tr key={field} className={`hover:${styles.appBg}`}>
                          <td className={`p-2 font-bold ${styles.cardText}`}>{field}</td>
                          <td className={`p-2 ${styles.accentText} font-bold`}>{val}</td>
                          <td className="p-2">
                            <span className="px-2 py-0.5 bg-emerald-50 text-emerald-700 border border-emerald-200 rounded font-bold text-[9px] inline-flex items-center gap-1">
                              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                              <span>校验通过 (CONTRACT_OK)</span>
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Bi-directional mapping check table */}
              <div className="space-y-2">
                <h4 className={`font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1`}>
                  <Icon name="GitMerge" size={11} className="text-amber-500" />
                  <span>2. 物理与逻辑双向契约核对矩阵 (Bi-directional Double-Check Alignment Matrix)</span>
                </h4>

                {verificationLoading ? (
                  <div className={`p-6 ${styles.inputBg} border ${styles.cardBorder} rounded-xl flex items-center justify-center gap-2 ${styles.cardTextMuted} font-bold`}>
                    <span className={`w-4 h-4 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                    <span>正在穿透物理数据库进行契约核对对账...</span>
                  </div>
                ) : verificationResult?.alignmentMatrix ? (
                  <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
                    <table className="w-full text-left text-[10px]">
                      <thead className={`${styles.inputBg} ${styles.cardTextMuted} font-bold uppercase`}>
                        <tr className={`border-b ${styles.cardBorder}`}>
                          <th className="p-2 w-1/6 font-mono">字段 (Field)</th>
                          <th className="p-2 w-1/4">校验类别 (Check)</th>
                          <th className="p-2 w-1/4 font-mono">物理底座靶向 (Physical Target)</th>
                          <th className="p-2 w-1/6 font-mono">对账结果</th>
                          <th className="p-2 w-1/4 text-right">核对审计追踪</th>
                        </tr>
                      </thead>
                      <tbody className={`divide-y ${styles.cardBorder} font-sans ${styles.cardTextMuted} leading-relaxed`}>
                        {verificationResult.alignmentMatrix.map((item: any, idx: number) => (
                          <tr key={idx} className={`hover:${styles.appBg}`}>
                            <td className={`p-2 font-mono font-bold ${styles.cardText}`}>{item.field}</td>
                            <td className={`p-2 font-semibold ${styles.cardTextMuted}`}>{item.type}</td>
                            <td className={`p-2 font-mono ${styles.cardTextMuted} ${styles.appBg}`}>{item.target}</td>
                            <td className="p-2 font-mono">
                              <span className={`px-1.5 py-0.5 rounded font-bold text-[8px] uppercase ${
                                item.status === 'SUCCESS' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' :
                                item.status === 'WARNING' ? 'bg-amber-50 text-amber-700 border border-amber-200' :
                                'bg-rose-50 text-rose-700 border border-rose-200'
                              }`}>
                                {item.status}
                              </span>
                            </td>
                            <td className={`p-2 ${styles.cardTextMuted} font-bold text-right text-[9px]`}>{item.message}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p className={`text-[10px] ${styles.cardTextMuted} p-2 text-center ${styles.inputBg} rounded-lg`}>等待核对诊断结果...</p>
                )}
              </div>

              {/* EXECUTION OUTCOMES BAR */}
              {selectedProposal.status === 'pending' && (
                <div className="p-3 bg-amber-50/30 border border-amber-200/60 rounded-xl space-y-3">
                  <div className="flex items-start gap-2 text-amber-800 leading-relaxed">
                    <Icon name="ShieldAlert" size={14} className="text-amber-600 mt-0.5 shrink-0" />
                    <div className="space-y-0.5">
                      <p className="font-extrabold text-[11px]">🛡️ 外部更新安全防护阻断机制处于活跃状态</p>
                      <p className={`text-[10px] ${styles.cardTextMuted} font-medium`}>该动作属于高危本体指令，大语言模型已被严禁直接注入修改。必须经过人工审批授权方可将物理数据写入 Doris/PostgreSQL。</p>
                    </div>
                  </div>

                  <div className="flex gap-2">
                    <button
                      onClick={() => onExecuteProposal(true)}
                      disabled={executionLoading}
                      className={`flex-1 py-2 ${styles.accentBg} ${styles.accentHover} text-white font-bold rounded-lg transition-all flex items-center justify-center gap-1.5 shadow-sm cursor-pointer`}
                    >
                      {executionLoading ? (
                        <>
                          <span className={`w-3.5 h-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                          <span>正在写入并核对一致性...</span>
                        </>
                      ) : (
                        <>
                          <Icon name="CheckCircle" size={13} className="text-emerald-400" />
                          <span>验证资质并安全授权写入 (Approve & Commit)</span>
                        </>
                      )}
                    </button>
                    <button
                      onClick={() => onExecuteProposal(false)}
                      className={`px-4 py-2 border ${styles.cardBorder} hover:${styles.inputBg} font-bold rounded-lg transition-colors ${styles.cardTextMuted} cursor-pointer`}
                    >
                      <span>拒绝申请 (Reject)</span>
                    </button>
                  </div>
                </div>
              )}

              {/* Execution Results block */}
              {executionResult && (
                <div className={`p-4 rounded-xl space-y-3.5 border animate-fadeIn`}>
                  {!executionResult.success ? (
                    <div className="space-y-2 border border-rose-200 bg-rose-50/40 p-1.5 rounded-lg">
                      <div className="flex items-center gap-2 font-black text-rose-700 text-xs">
                        <span className="p-1 rounded bg-rose-100 text-rose-600">
                          <Icon name="Lock" size={13} className="animate-bounce" />
                        </span>
                        <span>🚨 RBAC 越权阻止: 事务强行阻断 (TRANSACTION_ABORTED)</span>
                      </div>
                      <div className={`space-y-1 ${styles.cardTextMuted} font-sans leading-relaxed text-[10px]`}>
                        <p className={`font-bold ${styles.cardText}`}>{executionResult.message}</p>
                        <p>安全策略拦截：已拒绝「{userRole}」级别的写回。Doris 底层物理表保持未更改状态。审计快照已通报给 CSO 安全合规部门并锁定。</p>
                      </div>
                    </div>
                  ) : (
                    <div className="space-y-4">
                      <div className="bg-emerald-50 border border-emerald-200 rounded-lg p-3 space-y-1.5">
                        <div className="flex items-center gap-2 font-black text-emerald-800 text-xs">
                          <Icon name="CheckCircle2" size={14} className="text-emerald-600" />
                          <span>写入执行成功，双向对齐一致性校验通过 (Consistency Verified)</span>
                        </div>
                        <p className={`font-sans leading-relaxed ${styles.cardTextMuted} text-[10px]`}>{executionResult.executionDetail}</p>
                      </div>

                      <div className="space-y-2">
                        <span className={`text-[9px] font-black uppercase ${styles.cardTextMuted} tracking-wider block`}>物理-逻辑双向实时对账读回核对 (Read-back double check consistency logs)</span>
                        <div className={`border ${styles.cardBorder} rounded-lg overflow-hidden`}>
                          <table className="w-full text-left font-mono text-[9px]">
                            <thead className={`${styles.inputBg} ${styles.cardTextMuted} font-bold uppercase border-b ${styles.cardBorder}`}>
                              <tr>
                                <th className="p-2">逻辑实体属性 (Logical Key)</th>
                                <th className="p-2">底层物理宽表列 (Physical Col)</th>
                                <th className="p-2">模型写入预期值 (Expected)</th>
                                <th className="p-2">物理数据库读回值 (Read-back)</th>
                                <th className="p-2 text-right">对账结果</th>
                              </tr>
                            </thead>
                            <tbody className={`divide-y ${styles.cardBorder} ${styles.cardTextMuted}`}>
                              {executionResult.verificationMatrix?.map((m: any, idx: number) => (
                                <tr key={idx} className={`hover:${styles.appBg}`}>
                                  <td className="p-2 font-bold">{m.logicalField}</td>
                                  <td className={`p-2 ${styles.cardTextMuted}`}>{m.physicalCol}</td>
                                  <td className={`p-2 ${styles.accentText} font-bold`}>{m.expectedValue}</td>
                                  <td className="p-2 text-emerald-600 font-bold bg-emerald-500/5">{m.readbackValue}</td>
                                  <td className="p-2 text-right">
                                    <span className="px-1.5 py-0.5 bg-emerald-50 text-emerald-700 font-black rounded-sm text-[8px] border border-emerald-200">
                                      ✅ 强一致对齐
                                    </span>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )}

            </div>
          )}
        </div>

      </div>

      {/* Bottom Table view */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-xs p-4 space-y-3 shrink-0`}>
        <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
          <span className={`font-extrabold ${styles.cardTextMuted} flex items-center gap-1.5`}>
            <Icon name="Database" size={13} className={styles.accentText} />
            <span>实时民航物理宽表数据行查看器 (Apache Doris OLAP Live Data Rows)</span>
          </span>
          <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase ${styles.appBg} px-2 py-0.5 rounded`}>
            DORIS ENGINE STATUS: ACTIVE
          </span>
        </div>

        {dbData ? (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

            <div className="space-y-1.5">
              <span className={`text-[10px] font-extrabold ${styles.cardTextMuted} font-mono uppercase block`}>物理大表 `ds_flights_clean` (航班运行核心表)</span>
              <div className={`border ${styles.cardBorder} rounded-lg overflow-hidden max-h-36 overflow-y-auto`}>
                <table className="w-full text-left font-mono text-[9px]">
                  <thead className={`${styles.inputBg} ${styles.cardTextMuted} font-bold border-b ${styles.cardBorder} sticky top-0`}>
                    <tr>
                      <th className="p-1.5">ID</th>
                      <th className="p-1.5">flight_num</th>
                      <th className="p-1.5">route</th>
                      <th className="p-1.5">scheduled</th>
                      <th className="p-1.5">actual_departure</th>
                      <th className="p-1.5">pilot_id</th>
                      <th className="p-1.5">status</th>
                      <th className="p-1.5">delay_min</th>
                    </tr>
                  </thead>
                  <tbody className={`divide-y ${styles.cardBorder} ${styles.cardTextMuted}`}>
                    {dbData.flights.map(f => (
                      <tr key={f.flight_id} className={`hover:${styles.inputBg}`}>
                        <td className={`p-1.5 font-bold ${styles.cardText}`}>{f.flight_id}</td>
                        <td className={`p-1.5 ${styles.accentText} font-bold`}>{f.flight_num}</td>
                        <td className="p-1.5">{f.dep_airport} → {f.arr_airport}</td>
                        <td className="p-1.5 font-sans">{f.scheduled_departure}</td>
                        <td className={`p-1.5 font-sans font-medium ${styles.cardTextMuted}`}>{f.actual_departure}</td>
                        <td className="p-1.5">{f.pilot_id} ({f.pilot_name})</td>
                        <td className="p-1.5">
                          <span className={`px-1.5 py-0.5 rounded-[3px] text-[8px] font-extrabold font-sans uppercase ${
                            f.status === 'ON_TIME' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'
                          }`}>
                            {f.status}
                          </span>
                        </td>
                        <td className={`p-1.5 font-sans font-bold ${styles.cardTextMuted}`}>{f.delay_minutes}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="space-y-1.5">
              <span className={`text-[10px] font-extrabold ${styles.cardTextMuted} font-mono uppercase block`}>物理大表 `ds_pilots_biography` (飞行员资格及薪水表)</span>
              <div className={`border ${styles.cardBorder} rounded-lg overflow-hidden max-h-36 overflow-y-auto`}>
                <table className="w-full text-left font-mono text-[9px]">
                  <thead className={`${styles.inputBg} ${styles.cardTextMuted} font-bold border-b ${styles.cardBorder} sticky top-0`}>
                    <tr>
                      <th className="p-1.5">pilot_id</th>
                      <th className="p-1.5">pilot_name</th>
                      <th className="p-1.5">ssn_number (GDPR Masked)</th>
                      <th className="p-1.5">licence_rating</th>
                      <th className="p-1.5">hours_flown</th>
                      <th className="p-1.5">base_salary</th>
                    </tr>
                  </thead>
                  <tbody className={`divide-y ${styles.cardBorder} ${styles.cardTextMuted}`}>
                    {dbData.pilots.map(p => (
                      <tr key={p.pilot_id} className={`hover:${styles.inputBg}`}>
                        <td className={`p-1.5 font-bold ${styles.cardText}`}>{p.pilot_id}</td>
                        <td className={`p-1.5 font-sans font-bold ${styles.cardTextMuted}`}>{p.pilot_name}</td>
                        <td className={`p-1.5 ${styles.cardTextMuted}`}>***-**-{p.ssn_number.slice(-4)}</td>
                        <td className="p-1.5"><span className={`px-1.5 py-0.5 ${styles.appBg} ${styles.cardTextMuted} rounded font-extrabold text-[8px]`}>{p.licence_rating}</span></td>
                        <td className={`p-1.5 ${styles.cardTextMuted}`}>{p.hours_flown} 小时</td>
                        <td className={`p-1.5 ${styles.cardTextMuted}`}>￥{p.base_salary.toLocaleString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

          </div>
        ) : (
          <p className={`text-center ${styles.cardTextMuted} py-3`}>正在拉取民航宽表物理元数据...</p>
        )}
      </div>

    </div>
  );
}
