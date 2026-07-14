/**
 * OverviewTab — 安全中心 "overview" 标签页
 * @license Apache-2.0
 */
import React from 'react';
import { ResponsiveContainer, LineChart, Line, XAxis, YAxis, Tooltip as ChartTooltip, Legend, PieChart, Pie, Cell, BarChart, Bar } from 'recharts';
import { Globe, FolderGit, Tag, Shield, Activity, TrendingUp, RefreshCw, ArrowRight, ShieldAlert, Play, EyeOff, FileText, Plus, X, ChevronRight, Zap, ClipboardList, Info } from 'lucide-react';
import { PieChart as PieChartIcon } from 'lucide-react';
import { LucideIcon, CHART_COLORS, pieData, evaluationTrend } from '../helpers';
import type { TabProps } from './types';

export default function OverviewTab(props: TabProps) {
  const {
    orgs, setOrgs, projects, setProjects, markings, setMarkings,
    purposes, setPurposes, rowColPolicies, setRowColPolicies,
    auditLogs, setAuditLogs, activeTab, setActiveTab,
    selectedProjectId, setSelectedProjectId,
    selectedMarkingId, setSelectedMarkingId,
    selectedRowColDs, setSelectedRowColDs,
    simUser, setSimUser, simDataset, setSimDataset,
    simPurpose, setSimPurpose, simResult, simIp, setSimIp,
    handleRunSimulation, showAddOrgModal, setShowAddOrgModal,
    showAddMemberModal, setShowAddMemberModal,
    showAddMarkingModal, setShowAddMarkingModal,
    showAddPurposeModal, setShowAddPurposeModal,
    newOrgName, setNewOrgName, newOrgId, setNewOrgId,
    newOrgIp, setNewOrgIp, newOrgIsolation, setNewOrgIsolation,
    newMemberName, setNewMemberName, newMemberRole, setNewMemberRole,
    newMarkId, setNewMarkId, newMarkName, setNewMarkName,
    newMarkLevel, setNewMarkLevel, newMarkDesc, setNewMarkDesc,
    newPurpId, setNewPurpId, newPurpName, setNewPurpName,
    newPurpDesc, setNewPurpDesc, newPurpDs, setNewPurpDs,
    newPurpRules, setNewPurpRules,
    handleAddOrg, handleAddMember, handleAddMarking, handleAddPurpose,
    handleToggleColumnMask, handleToggleRowFilter,
    handleExecutePlaybookStep, openDiagnosticReport, showToast,
  } = props;

  return (
<div className="space-y-6">
  
  {/* Summary Metrics */}
  <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
    <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
      <div className="space-y-1">
        <div className="text-[11px] text-slate-400 font-semibold uppercase">安全隔离域 (Orgs)</div>
        <div className="text-xl font-mono font-black text-white">{orgs.length}</div>
      </div>
      <span className="p-2 rounded-lg bg-indigo-500/10 text-indigo-400">
        <Globe size={18} />
      </span>
    </div>
    <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
      <div className="space-y-1">
        <div className="text-[11px] text-slate-400 font-semibold uppercase">受控工程项目 (DAC)</div>
        <div className="text-xl font-mono font-black text-white">{projects.length}</div>
      </div>
      <span className="p-2 rounded-lg bg-blue-500/10 text-blue-400">
        <FolderGit size={18} />
      </span>
    </div>
    <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
      <div className="space-y-1">
        <div className="text-[11px] text-slate-400 font-semibold uppercase">强制密级标记 (MAC)</div>
        <div className="text-xl font-mono font-black text-white">{markings.length}</div>
      </div>
      <span className="p-2 rounded-lg bg-pink-500/10 text-pink-400">
        <Tag size={18} />
      </span>
    </div>
    <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
      <div className="space-y-1">
        <div className="text-[11px] text-slate-400 font-semibold uppercase">合规目的管控 (PBAC)</div>
        <div className="text-xl font-mono font-black text-white">
          {purposes.filter(p => p.status === 'ACTIVE').length} <span className="text-xs text-slate-500">/ {purposes.length}</span>
        </div>
      </div>
      <span className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400">
        <ClipboardList size={18} />
      </span>
    </div>
  </div>

  {/* Charts Row */}
  <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
    
    {/* Recharts Evaluation Trend */}
    <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 lg:col-span-2 space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
          <TrendingUp size={13} className="text-indigo-400" />
          安全策略鉴权评估趋势 (Policy Evaluation Trend)
        </span>
        <span className="text-[10px] text-slate-500">过去7天动态安全阻断详情</span>
      </div>
      <div className="h-64">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={evaluationTrend}>
            <XAxis dataKey="day" stroke="#64748b" fontSize={11} tickLine={false} />
            <YAxis stroke="#64748b" fontSize={11} tickLine={false} />
            <ChartTooltip contentStyle={{ backgroundColor: '#020617', borderColor: '#334155', color: '#f8fafc' }} />
            <Legend wrapperStyle={{ fontSize: 11 }} />
            <Line type="monotone" dataKey="success" name="成功放行 (Allow)" stroke="#10b981" strokeWidth={2.5} dot={{ r: 3 }} />
            <Line type="monotone" dataKey="denied" name="安全拦截 (Deny)" stroke="#ef4444" strokeWidth={2.5} dot={{ r: 3 }} />
            <Line type="monotone" dataKey="warn" name="潜在风险警告 (Warn)" stroke="#f59e0b" strokeWidth={1.5} strokeDasharray="3 3" />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>

    {/* Pie Chart sensitive assets */}
    <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-4 flex flex-col justify-between">
      <div className="space-y-1">
        <div className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
          <PieChartIcon size={13} className="text-indigo-400" />
          安全标记保护下的高敏数据分布 (MAC Tag Distribution)
        </div>
        <p className="text-[10px] text-slate-500">按保密分类所涵盖的核心数源占比</p>
      </div>
      <div className="h-44 flex items-center justify-center relative">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={pieData}
              innerRadius={50}
              outerRadius={70}
              paddingAngle={3}
              dataKey="value"
            >
              {pieData.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={CHART_COLORS[index % CHART_COLORS.length]} />
              ))}
            </Pie>
            <ChartTooltip contentStyle={{ backgroundColor: '#020617', borderColor: '#334155', color: '#f8fafc' }} />
          </PieChart>
        </ResponsiveContainer>
        <div className="absolute flex flex-col items-center">
          <span className="text-xs font-bold font-mono text-slate-400">受护项</span>
          <span className="text-lg font-black font-mono text-white">100%</span>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2 text-[10px] text-slate-400">
        {markings.map((m, idx) => (
          <div key={m.id} className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-full shrink-0" style={{ backgroundColor: CHART_COLORS[idx % CHART_COLORS.length] }} />
            <span className="truncate">{m.id.split('_')[1] || m.id}</span>
          </div>
        ))}
      </div>
    </div>
  </div>

  {/* INTERACTIVE POLICY SIMULATOR BANNER */}
  <div className="bg-slate-950 border-2 border-indigo-900/40 rounded-xl p-5 space-y-4">
    <div className="flex items-center justify-between border-b border-slate-800 pb-3">
      <div className="space-y-0.5">
        <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
          <Zap size={14} className="text-indigo-400 animate-pulse" />
          零信任策略仿真演算器 (Zero-Trust Security Policy Simulator)
        </span>
        <p className="text-[10px] text-slate-500">模拟不同用户、敏感资产和具体分析目的在 ECOS 级联策略链条中的真实决策判定结果</p>
      </div>
      <button
        onClick={handleRunSimulation}
        className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1 cursor-pointer"
      >
        <Play size={12} />
        <span>一键仿真演算</span>
      </button>
    </div>

    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
      
      {/* Inputs */}
      <div className="space-y-3 bg-slate-900/80 p-3.5 rounded-lg border border-slate-800">
        <div>
          <label className="text-[10px] text-slate-400 font-bold block mb-1">模拟主体 (Subject User)</label>
          <select
            value={simUser}
            onChange={(e) => setSimUser(e.target.value)}
            className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-xs text-slate-200 font-mono outline-none"
          >
            <option value="analyst_li">analyst_li (分析师 - HQ)</option>
            <option value="hr_manager">hr_manager (HR主管)</option>
            <option value="external_auditor">external_auditor (外部审计员 - Expired)</option>
            <option value="contractor_eng">contractor_eng (外部承包商 - Isolated)</option>
            <option value="admin_guorong">admin_guorong (超级管理员 - Super)</option>
          </select>
        </div>

        <div>
          <label className="text-[10px] text-slate-400 font-bold block mb-1">敏感资源 (Sensitive Dataset)</label>
          <select
            value={simDataset}
            onChange={(e) => setSimDataset(e.target.value)}
            className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-xs text-slate-200 font-mono outline-none"
          >
            <option value="ds_flight_schedules">ds_flight_schedules (航班计划 - 无MAC锁)</option>
            <option value="ds_ticket_sales">ds_ticket_sales (机票财务 - MAC: Revenue)</option>
            <option value="ds_pilots_biography">ds_pilots_biography (机组隐私 - MAC: Pilot)</option>
          </select>
        </div>

        <div>
          <label className="text-[10px] text-slate-400 font-bold block mb-1">使用目的环境 (Purpose Context)</label>
          <select
            value={simPurpose}
            onChange={(e) => setSimPurpose(e.target.value)}
            className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-xs text-slate-200 font-mono outline-none"
          >
            {purposes.map(p => (
              <option key={p.id} value={p.id}>{p.name} [{p.status}]</option>
            ))}
          </select>
        </div>

        <div>
          <label className="text-[10px] text-slate-400 font-bold block mb-1">物理客户端 IP (Client IP - Whitelist CIDR)</label>
          <input
            type="text"
            value={simIp}
            onChange={(e) => setSimIp(e.target.value)}
            placeholder="例: 10.120.5.23"
            className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-xs text-slate-200 font-mono outline-none focus:border-indigo-500"
          />
          <span className="text-[8px] text-slate-500 mt-1 block">
            HQ段: 10.120.0.0/16 | 合作段: 172.16.45.0/24 | 承包段: 202.96.128.0/24
          </span>
        </div>
      </div>

      {/* Verdict & Details */}
      <div className="md:col-span-2 bg-slate-900/80 p-3.5 rounded-lg border border-slate-800 flex flex-col justify-between">
        {simResult ? (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-[10px] text-slate-400 font-bold">校验结果反馈 (Decision Result)</span>
              <span className={`px-2 py-0.5 rounded text-[10px] font-black font-mono tracking-wider ${
                simResult.verdict === 'GRANTED' ? 'bg-emerald-950 text-emerald-400 border border-emerald-800' : 'bg-red-950 text-red-400 border border-red-800'
              }`}>
                {simResult.verdict}
              </span>
            </div>
            <div className="space-y-1.5 font-mono text-[10px] text-slate-300 overflow-y-auto max-h-32 pr-2">
              {Array.isArray(simResult.traces) && simResult.traces.map((trace, idx) => (
                <div key={idx} className={`p-1 rounded ${
                  trace.includes('SUCCESS') || trace.includes('GRANTED') || trace.includes('通过')
                    ? 'bg-emerald-950/25 text-emerald-300' 
                    : trace.includes('DENIED') || trace.includes('拦截') || trace.includes('失败')
                    ? 'bg-red-950/25 text-red-300' 
                    : 'text-slate-300'
                }`}>
                  {trace}
                </div>
              ))}
            </div>
          </div>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center text-slate-500 space-y-2 py-6">
            <ShieldAlert size={24} className="text-slate-600" />
            <span className="text-xs">等待仿真触发，请点击右上角「一键仿真演算」按钮</span>
          </div>
        )}

        <div className="text-[9px] text-slate-500 border-t border-slate-800/60 pt-2 flex items-center gap-1.5">
          <Info size={10} />
          <span>本仿真完全模拟 Palantir ECOS 零信任架构下的 <strong>组织隔离 ➔ DAC ➔ MAC ➔ 目的链(PBAC)</strong> 四重复合门禁。</span>
        </div>
      </div>
    </div>
  </div>
</div>
  );
}
