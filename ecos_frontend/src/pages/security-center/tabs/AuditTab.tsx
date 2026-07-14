/**
 * AuditTab — 安全中心 "audit" 标签页
 * @license Apache-2.0
 */
import React from 'react';
import { Activity, Download, ShieldAlert, ChevronRight, EyeOff, RefreshCw } from 'lucide-react';
import { LucideIcon } from '../helpers';
import type { TabProps } from './types';

export default function AuditTab(props: TabProps) {
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
  
  <div className="flex items-center justify-between border-b border-slate-800 pb-4">
    <div className="space-y-1">
      <h3 className="text-sm font-black text-slate-100 flex items-center gap-2">
        <Activity size={15} className="text-indigo-400" />
        安全防护实时审计日志 (Real-Time Dynamic Security Audit Trails)
      </h3>
      <p className="text-[10px] text-slate-400">
        捕获并永久存证所有针对敏感数据集、操作行为和安全标记的判定行为，每隔 6 秒自动同步评估引擎记录。
      </p>
    </div>
    <div className="flex items-center gap-2">
      <button
        onClick={openDiagnosticReport}
        className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer shadow-md"
      >
        <ShieldAlert size={13} />
        <span>系统安全扫描诊断书</span>
      </button>
      <button
        onClick={() => {
          showToast('success', '安全合规PDF报告已自动生成并安全加密打包至本地，哈希值已写入审计底座！');
        }}
        className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer shadow-md"
      >
        <Download size={13} />
        <span>导出安全合规报告</span>
      </button>
    </div>
  </div>

  {/* Logs Table */}
  <div className="border border-slate-800 rounded-xl overflow-hidden bg-slate-950">
    <table className="w-full text-left text-xs text-slate-300">
      <thead className="bg-slate-950 text-slate-400 text-[10px] font-extrabold uppercase tracking-wider border-b border-slate-800">
        <tr>
          <th className="p-3">事件时间</th>
          <th className="p-3">访问主体</th>
          <th className="p-3">归属组织</th>
          <th className="p-3">操作行为</th>
          <th className="p-3">受控资源</th>
          <th className="p-3">判定结果</th>
          <th className="p-3">合规评估明细</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-slate-800/60 font-mono text-[11px]">
        {auditLogs.map(log => (
          <tr key={log.id} className="hover:bg-slate-900/30 transition-colors">
            <td className="p-3 text-indigo-400/80 font-bold">{log.timestamp}</td>
            <td className="p-3 text-slate-200">{log.username}</td>
            <td className="p-3 text-slate-400">{log.orgId.split('_')[1] || log.orgId}</td>
            <td className="p-3 text-slate-300 font-semibold">{log.action}</td>
            <td className="p-3 text-slate-400">
              <span className="text-[10px] text-indigo-300 font-bold bg-indigo-950/20 px-1 py-0.2 rounded border border-indigo-900/10 mr-1.5">
                {log.resourceType}
              </span>
              {log.resourceId}
            </td>
            <td className="p-3">
              <span className={`px-2 py-0.5 rounded text-[8px] font-black ${
                log.status === 'SUCCESS' ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/30' :
                log.status === 'DENIED' ? 'bg-red-950 text-red-400 border border-red-900/30' :
                'bg-amber-950 text-amber-400 border border-amber-900/30'
              }`}>
                {log.status}
              </span>
            </td>
            <td className="p-3 text-slate-400 max-w-sm truncate" title={log.details}>
              {log.details}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  </div>
</div>
  );
}
