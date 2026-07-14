/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { LayoutDashboard, Cpu, Bot, Layers, ShieldCheck, TrendingUp, Activity, ChevronRight } from 'lucide-react';
import { AIPLogicPipeline, AIPAgent, AIPModel, AIPAuditLog } from '../../types/aiworkbench';
import { useTheme } from '../../components/ThemeContext';

interface DashboardViewProps {
  pipelines: AIPLogicPipeline[];
  agents: AIPAgent[];
  models: AIPModel[];
  auditLogs: AIPAuditLog[];
  onNavigateToView: (view: 'logic' | 'agent' | 'model' | 'guardrails') => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

export default function DashboardView({
  pipelines,
  agents,
  models,
  auditLogs,
  onNavigateToView
}: DashboardViewProps) {
  const { styles } = useTheme();
  // Compute dashboard metrics
  const activePipelinesCount = pipelines.filter(p => p.status === 'active').length;
  const activeAgentsCount = agents.filter(a => a.status === 'active').length;
  
  // Simulated chart data
  const weeklyUsage = [120, 185, 240, 310, 290, 420, 385]; // requests
  const maxUsage = Math.max(...weeklyUsage);

  return (
    <div className={`space-y-6 overflow-y-auto h-full p-6 ${styles.appBg}`}>
      
      {/* Welcome Banner */}
      <div className={`${styles.appBg} text-white rounded-xl p-6 shadow-sm relative overflow-hidden`}>
        <div className="absolute top-0 right-0 w-96 h-96 bg-gradient-to-br from-blue-600/20 to-indigo-600/10 rounded-full blur-3xl pointer-events-none" />
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-1.5">
            <div className="flex items-center gap-2 text-blue-400 text-xs font-semibold tracking-wider uppercase">
              <span className="w-2 h-2 rounded-full bg-blue-500 animate-pulse" />
              <span>AIP | ECOS 智能平台</span>
            </div>
            <h2 className="text-xl font-extrabold text-white">欢迎来到 AI工作台 (AI Workbench)</h2>
            <p className={`text-xs ${styles.cardTextMuted} max-w-2xl leading-relaxed`}>
              将大规模语言模型(LLM)无缝绑定于航空核心本体(Aviation Core)。在此配置决策逻辑流(AIP Logic)、部署会话交互智能体(AIP Agents)，并受到企业级私有边界与数据安全护栏(Guardrails)的完全实时审计和守护。
            </p>
          </div>
          <div className="flex gap-2.5 shrink-0">
            <button
              onClick={() => onNavigateToView('agent')}
              className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-lg text-xs shadow-sm transition-all cursor-pointer flex items-center gap-1.5"
            >
              <Bot size={13} />
              <span>定制智能体</span>
            </button>
            <button
              onClick={() => onNavigateToView('logic')}
              className={`px-3.5 py-1.5 bg-slate-800 hover:bg-slate-700 ${styles.cardText} border ${styles.inputBorder} font-semibold rounded-lg text-xs transition-all cursor-pointer flex items-center gap-1.5`}
            >
              <Cpu size={13} />
              <span>编排逻辑流</span>
            </button>
          </div>
        </div>
      </div>

      {/* Key Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {([
          {
            title: '激活逻辑编排 (Pipelines)',
            value: `${activePipelinesCount} / ${pipelines.length}`,
            subText: '已绑定为 Ontology 函数',
            icon: Cpu,
            color: 'text-blue-600 bg-blue-50'
          },
          {
            title: '在线智能体 (AIP Agents)',
            value: `${activeAgentsCount} / ${agents.length}`,
            subText: '已授权触发动作规则',
            icon: Bot,
            color: 'text-indigo-600 bg-indigo-50'
          },
          {
            title: '可调配 LLM 资产数',
            value: `${models.filter(m => m.status === 'connected').length} 台`,
            subText: '安全合规托管边界内',
            icon: Layers,
            color: 'text-emerald-600 bg-emerald-50'
          },
          {
            title: '安全护栏拦截率 (PII/Hallu)',
            value: '100.00%',
            subText: '近 24H 自动防御审计 142 次',
            icon: ShieldCheck,
            color: 'text-rose-600 bg-rose-50'
          }
        ] as const).map((m, i) => (
          <div key={i} className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 flex items-center gap-4 hover:shadow-md transition-shadow`}>
            <div className={`p-3 rounded-xl shrink-0 ${m.color}`}>
              <m.icon size={18} />
            </div>
            <div className="space-y-0.5">
              <p className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>{m.title}</p>
              <p className={`text-lg font-bold ${styles.cardText}`}>{m.value}</p>
              <p className={`text-[10px] ${styles.cardTextMuted}`}>{m.subText}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Visual Analytics Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left Chart: Token Requests Trend */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 lg:col-span-2 flex flex-col justify-between space-y-4`}>
          <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
            <div className="flex items-center gap-2">
              <span className="p-1 rounded-md bg-blue-50 text-blue-600">
                <TrendingUp size={13} />
              </span>
              <h3 className={`text-xs font-bold ${styles.cardText}`}>AIP API 运行负载趋势 (24小时批次)</h3>
            </div>
            <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>2026-07-03 最新汇总</span>
          </div>

          {/* SVG Line Chart */}
          <div className="h-40 w-full relative pt-2">
            <div className={`absolute left-0 bottom-0 top-0 w-8 flex flex-col justify-between text-[9px] ${styles.cardTextMuted} font-mono pr-2 border-r ${styles.cardBorder}`}>
              <span>500</span>
              <span>250</span>
              <span>0</span>
            </div>
            <div className="ml-10 h-full relative flex items-end justify-between">
              {/* Grid Lines */}
              <div className={`absolute left-0 right-0 top-0 border-t ${styles.cardBorder} border-dashed`} />
              <div className={`absolute left-0 right-0 top-1/2 border-t ${styles.cardBorder} border-dashed`} />
              <div className={`absolute left-0 right-0 bottom-0 border-b ${styles.cardBorder}`} />

              {/* Weekly bar graph */}
              {weeklyUsage.map((val, idx) => {
                const heightPercent = (val / maxUsage) * 100;
                return (
                  <div key={idx} className="flex-1 flex flex-col items-center justify-end h-full px-1.5 group relative">
                    {/* Tooltip */}
                    <span className="absolute -top-6 bg-slate-800 text-white text-[9px] font-semibold px-1.5 py-0.5 rounded-sm opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-10 whitespace-nowrap">
                      {val} 次调用
                    </span>
                    {/* Bar */}
                    <div 
                      className="w-full bg-blue-500 hover:bg-blue-600 rounded-t-sm transition-all"
                      style={{ height: `${heightPercent * 0.8}%` }}
                    />
                    <span className={`text-[9px] ${styles.cardTextMuted} font-mono mt-1.5`}>
                      {['周一', '周二', '周三', '周四', '周五', '周六', '周日'][idx]}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Right Chart: Model Distribution */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 flex flex-col justify-between`}>
          <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
            <div className="flex items-center gap-2">
              <span className="p-1 rounded-md bg-indigo-50 text-indigo-600">
                <Layers size={13} />
              </span>
              <h3 className={`text-xs font-bold ${styles.cardText}`}>大模型(LLM)算力消耗占比</h3>
            </div>
            <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>按 Token 计算</span>
          </div>

          <div className="flex items-center justify-around py-4">
            {/* Simple Visual Pie representation */}
            <div className="relative w-24 h-24 flex items-center justify-center">
              {/* Interactive SVG circle layout */}
              <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#f1f5f9" strokeWidth="3" />
                {/* Google 50% */}
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#3b82f6" strokeWidth="3" strokeDasharray="50 50" strokeDashoffset="0" />
                {/* Anthropic 30% */}
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#8b5cf6" strokeWidth="3" strokeDasharray="30 70" strokeDashoffset="-50" />
                {/* OpenAI 20% */}
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#10b981" strokeWidth="3" strokeDasharray="20 80" strokeDashoffset="-80" />
              </svg>
              <div className="absolute text-center">
                <p className={`text-xs font-black ${styles.cardText}`}>14.2M</p>
                <p className={`text-[8px] ${styles.cardTextMuted} uppercase font-bold`}>总 Token</p>
              </div>
            </div>

            {/* Legend indicators */}
            <div className="space-y-2 text-[10px]">
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-blue-500" />
                <span className={`font-semibold ${styles.cardTextMuted}`}>Gemini (50%)</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-purple-500" />
                <span className={`font-semibold ${styles.cardTextMuted}`}>Claude (30%)</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-emerald-500" />
                <span className={`font-semibold ${styles.cardTextMuted}`}>GPT-4o (20%)</span>
              </div>
            </div>
          </div>
        </div>

      </div>

      {/* AIP Security Guardrail Alerts / Recent Logs */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden shadow-xs`}>
        <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
          <div className="flex items-center gap-2">
            <span className="p-1 rounded bg-rose-50 text-rose-600">
              <Activity size={13} />
            </span>
            <h3 className={`text-xs font-bold ${styles.cardText}`}>AIP 平台运行审计与安全阻断日志</h3>
          </div>
          <button 
            onClick={() => onNavigateToView('guardrails')}
            className="text-[10px] text-blue-600 hover:text-blue-700 font-bold flex items-center gap-0.5 cursor-pointer"
          >
            <span>管理安全护栏</span>
            <ChevronRight size={11} />
          </button>
        </div>

        <div className={`divide-y ${styles.cardBorder} max-h-60 overflow-y-auto`}>
          {auditLogs.map((log) => {
            const isBlocked = log.status === 'blocked';
            const isFlagged = log.status === 'flagged';
            const isPending = log.status === 'pending_approval';

            return (
              <div key={log.id} className={`p-3 hover:${styles.appBg} flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs`}>
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className={`font-mono text-[10px] ${styles.cardTextMuted} ${styles.appBg} px-1 py-0.5 rounded`}>{log.timestamp}</span>
                    <span className={`font-bold ${styles.cardTextMuted}`}>{log.assetName}</span>
                    <span className={`text-[10px] ${styles.cardTextMuted} font-semibold ${styles.inputBg} px-1.5 py-0.5 border ${styles.cardBorder} rounded`}>{log.source}</span>
                    <span className={`${styles.cardTextMuted} font-medium`}>| 操作人: {log.user}</span>
                  </div>
                  <p className={`${styles.cardTextMuted} text-[11px] leading-relaxed`}>{log.details}</p>
                </div>

                <div className="flex flex-col items-end gap-1.5 shrink-0 self-end sm:self-center">
                  {isBlocked ? (
                    <span className="px-2 py-0.5 rounded-full bg-red-100 text-red-700 font-bold text-[9px] flex items-center gap-1 border border-red-200">
                      <span className="w-1 h-1 rounded-full bg-red-600" />
                      已安全阻断 (Blocked)
                    </span>
                  ) : isFlagged ? (
                    <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 font-bold text-[9px] flex items-center gap-1 border border-amber-200">
                      <span className="w-1 h-1 rounded-full bg-amber-600 animate-pulse" />
                      待人工校对 (Flagged)
                    </span>
                  ) : isPending ? (
                    <span className="px-2 py-0.5 rounded-full bg-indigo-100 text-indigo-700 font-bold text-[9px] flex items-center gap-1 border border-indigo-200">
                      <span className="w-1.5 h-1.5 rounded-full bg-indigo-600 animate-ping" />
                      待签授权 (Pending)
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700 font-bold text-[9px] flex items-center gap-1 border border-emerald-200">
                      <span className="w-1 h-1 rounded-full bg-emerald-600" />
                      安全放行 (Passed)
                    </span>
                  )}
                  {log.actionTaken && (
                    <span className="text-[9px] text-rose-500 font-mono font-bold">{log.actionTaken}</span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

    </div>
  );
}
