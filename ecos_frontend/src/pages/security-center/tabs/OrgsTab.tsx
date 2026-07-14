/**
 * OrgsTab — 安全中心 "orgs" 标签页
 * @license Apache-2.0
 */
import React from 'react';
import { Globe, Plus, X, Shield, ShieldAlert, ShieldCheck, Users, BookOpen, EyeOff, ChevronRight } from 'lucide-react';
import { LucideIcon } from '../helpers';
import type { TabProps } from './types';

export default function OrgsTab(props: TabProps) {
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
  
  <div className="flex items-center justify-between">
    <div>
      <h3 className="text-sm font-black text-slate-100">ECOS 组织边界配置 (Organizations Setup)</h3>
      <p className="text-[10px] text-slate-400">ECOS 最高级别安全隔离机制。即使授予了文件权限，非共享协议白名单下的跨组织访问也将被绝对硬隔离阻断。</p>
    </div>
    <button
      onClick={() => setShowAddOrgModal(true)}
      className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1 cursor-pointer"
    >
      <Plus size={13} />
      <span>添加安全组织</span>
    </button>
  </div>

  {/* Organization List Grid */}
  <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
    {orgs.map(org => (
      <div key={org.id} className="bg-slate-950 border border-slate-800 rounded-xl p-5 space-y-4">
        <div className="flex items-start justify-between">
          <div className="space-y-0.5">
            <div className="text-xs font-extrabold text-slate-100 flex items-center gap-1.5">
              <Globe size={13} className="text-indigo-400" />
              <span>{org.name}</span>
            </div>
            <div className="text-[10px] text-slate-500 font-mono">ID: {org.id}</div>
          </div>
          <span className={`px-2 py-0.5 rounded text-[8px] font-black ${
            org.isolationMode ? 'bg-red-950 text-red-400 border border-red-900/40' : 'bg-blue-950 text-blue-400 border border-blue-900/40'
          }`}>
            {org.isolationMode ? '严密单边隔离' : '多域弹性共享'}
          </span>
        </div>

        <div className="h-px bg-slate-900" />

        <div className="space-y-2 text-xs text-slate-300">
          <div className="flex justify-between items-center">
            <span className="text-slate-500">组织活跃成员:</span>
            <span className="font-mono font-bold text-slate-200">{org.memberCount} 人</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-slate-500">已授权跨组织共享:</span>
            <span className="font-mono text-[10px] text-indigo-400">
              {org.crossOrgSharing.length > 0 ? org.crossOrgSharing.join(', ') : '无共享'}
            </span>
          </div>
          <div className="space-y-1">
            <div className="text-slate-500">活跃白名单IP段 (CIDR):</div>
            <div className="flex flex-wrap gap-1">
              {org.ipRanges.map(ip => (
                <span key={ip} className="bg-slate-900 border border-slate-800 px-1.5 py-0.5 rounded text-[10px] font-mono text-indigo-300">
                  {ip}
                </span>
              ))}
            </div>
          </div>
        </div>

        <div className="flex items-center justify-end gap-1.5 pt-2">
          <button 
            onClick={() => {
              const ip = prompt('输入要追加的 IP 白名单 CIDR (例 10.150.0.0/16):');
              if (ip) {
                setOrgs(orgs.map(o => o.id === org.id ? { ...o, ipRanges: [...o.ipRanges, ip] } : o));
              }
            }}
            className="p-1 bg-slate-900 hover:bg-slate-800 rounded border border-slate-800 text-[10px] font-bold text-slate-300 cursor-pointer"
          >
            追加网段
          </button>
          <button 
            onClick={() => {
              setOrgs(orgs.filter(o => o.id !== org.id));
            }}
            className="p-1 bg-red-950/20 hover:bg-red-950 text-[10px] font-bold text-red-400 rounded cursor-pointer"
          >
            删除组织
          </button>
        </div>
      </div>
    ))}
  </div>
</div>
  );
}
