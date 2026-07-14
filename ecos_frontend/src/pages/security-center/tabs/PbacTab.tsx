/**
 * PbacTab — 安全中心 "pbac" 标签页
 * @license Apache-2.0
 */
import React from 'react';
import { ClipboardList, Plus, X, Shield, ShieldAlert, ShieldCheck, BookOpen, ChevronRight, EyeOff } from 'lucide-react';
import { LucideIcon } from '../helpers';
import type { TabProps } from './types';

export default function PbacTab(props: TabProps) {
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
      <h3 className="text-sm font-black text-slate-100">基于分析用途授权控制 (Purpose-Based Access Control - PBAC)</h3>
      <p className="text-[10px] text-slate-400">只有定义合法、合规的数据分析「目的」(Purpose)，才能获得临时的密文解密密钥。任何违背目的的导出或越权行为将被严厉记录。</p>
    </div>
    <button
      onClick={() => setShowAddPurposeModal(true)}
      className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1 cursor-pointer"
    >
      <Plus size={13} />
      <span>添加合规用途项目</span>
    </button>
  </div>

  {/* Purpose Cards Grid */}
  <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
    {purposes.map(p => (
      <div key={p.id} className="bg-slate-950 border border-slate-800 rounded-xl p-5 space-y-4">
        <div className="flex items-start justify-between">
          <span className={`px-2 py-0.5 rounded text-[8px] font-black ${
            p.status === 'ACTIVE' ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/30' :
            p.status === 'EXPIRED' ? 'bg-red-950 text-red-400 border border-red-900/30' :
            'bg-slate-800 text-slate-400'
          }`}>
            {p.status}
          </span>
          <span className="text-[9px] text-slate-500 font-mono">ID: {p.id}</span>
        </div>

        <div className="space-y-1">
          <div className="text-xs font-extrabold text-slate-100">{p.name}</div>
          <p className="text-[10px] text-slate-400 leading-relaxed line-clamp-2">{p.description}</p>
        </div>

        <div className="h-px bg-slate-900" />

        <div className="space-y-2 text-[10px] text-slate-300">
          <div>
            <span className="text-slate-500">特许执行人员:</span>
            <div className="flex flex-wrap gap-1 mt-1">
              {p.authorizedUsers.map(u => (
                <span key={u} className="bg-slate-900 px-1.5 py-0.2 rounded font-mono text-indigo-300">
                  {u}
                </span>
              ))}
            </div>
          </div>

          <div>
            <span className="text-slate-500">锚定输入数据集:</span>
            <div className="flex flex-wrap gap-1 mt-1">
              {p.inputDatasets.map(ds => (
                <span key={ds} className="bg-slate-900 px-1.5 py-0.2 rounded font-mono text-slate-300">
                  {ds}
                </span>
              ))}
            </div>
          </div>

          <div>
            <span className="text-slate-500">强制动态脱敏要求:</span>
            <div className="flex flex-wrap gap-1 mt-1">
              {p.redactionRules.map(rule => (
                <span key={rule} className="bg-pink-950/30 text-pink-400 border border-pink-900/20 px-1.5 py-0.2 rounded font-mono">
                  {rule}
                </span>
              ))}
            </div>
          </div>

          <div className="flex items-center justify-between text-[9px] border-t border-slate-900 pt-2 text-slate-500">
            <span>届期截至日期:</span>
            <span className="font-mono text-slate-400 font-semibold">{p.expiresAt}</span>
          </div>
        </div>

        <div className="flex items-center justify-end gap-1.5 pt-1">
          {p.status === 'EXPIRED' ? (
            <button
              onClick={() => {
                setPurposes(purposes.map(purp => purp.id === p.id ? { ...purp, status: 'ACTIVE', expiresAt: '2027-01-01' } : purp));
              }}
              className="p-1 px-2.5 bg-indigo-900/40 hover:bg-indigo-900 text-[10px] font-bold text-indigo-300 rounded cursor-pointer"
            >
              重新激活
            </button>
          ) : (
            <button
              onClick={() => {
                setPurposes(purposes.map(purp => purp.id === p.id ? { ...purp, status: 'EXPIRED' } : purp));
              }}
              className="p-1 px-2.5 bg-red-950/20 hover:bg-red-950 text-[10px] font-bold text-red-400 rounded cursor-pointer"
            >
              立即吊销目的
            </button>
          )}
        </div>
      </div>
    ))}
  </div>
</div>
  );
}
