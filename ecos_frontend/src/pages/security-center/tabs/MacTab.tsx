/**
 * MacTab — 安全中心 "mac" 标签页
 * @license Apache-2.0
 */
import React from 'react';
import { Tag, X, Shield, ShieldAlert, ShieldCheck, Plus, BookOpen, ChevronRight, EyeOff, Workflow, Database, ArrowRight, Cpu } from 'lucide-react';
import { LucideIcon } from '../helpers';
import type { TabProps } from './types';

export default function MacTab(props: TabProps) {
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
<div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
  
  {/* Left Markings List */}
  <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-4">
    <div className="flex items-center justify-between border-b border-slate-800 pb-2">
      <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
        <Tag size={13} className="text-pink-400" />
        安全标记 (MAC Markings)
      </span>
      <button
        onClick={() => setShowAddMarkingModal(true)}
        className="p-1 bg-indigo-600 hover:bg-indigo-500 text-white rounded font-bold text-[10px] cursor-pointer"
      >
        + 新建标记
      </button>
    </div>

    <div className="space-y-2">
      {markings.map(m => (
        <button
          key={m.id}
          onClick={() => setSelectedMarkingId(m.id)}
          className={`w-full text-left p-3 rounded-lg border transition-all flex flex-col gap-1 cursor-pointer ${
            selectedMarkingId === m.id
              ? 'bg-slate-900 border-pink-500/40 shadow-md shadow-pink-500/5'
              : 'bg-slate-950 border-slate-800 hover:bg-slate-900'
          }`}
        >
          <div className="text-xs font-extrabold text-slate-100 flex items-center gap-1.5">
            <span className="h-1.5 w-1.5 rounded-full bg-pink-500" />
            <span>{m.id}</span>
          </div>
          <p className="text-[10px] text-slate-400 line-clamp-1">{m.description}</p>
          <div className="flex items-center gap-2 mt-1.5 text-[9px] text-slate-500">
            <span className="bg-slate-800 px-1 py-0.2 rounded font-mono text-slate-300">{m.classificationLevel}</span>
            <span>锁定数据集: {m.appliedDatasets.length}</span>
          </div>
        </button>
      ))}
    </div>
  </div>

  {/* Right Detail & Lineage Simulator */}
  <div className="lg:col-span-2 bg-slate-950 border border-slate-800 rounded-xl p-5 space-y-6">
    {(() => {
      const currentMark = markings.find(m => m.id === selectedMarkingId);
      if (!currentMark) return <div className="text-slate-500 text-xs">请选择一个安全标记</div>;

      return (
        <div className="space-y-6">
          
          <div className="border-b border-slate-800 pb-4 space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-[10px] bg-pink-950/60 text-pink-400 border border-pink-900/40 px-2 py-0.5 rounded font-mono font-bold uppercase">
                {currentMark.classificationLevel} 级密文安全标记
              </span>
              <span className="text-[10px] text-slate-500">API Key: {currentMark.apiName}</span>
            </div>
            <h3 className="text-sm font-black text-slate-100">{currentMark.displayName}</h3>
            <p className="text-xs text-slate-400 leading-relaxed">{currentMark.description}</p>
          </div>

          {/* Detail attributes */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="bg-slate-900/60 border border-slate-800 p-3.5 rounded-lg space-y-2">
              <span className="text-[10px] text-slate-400 font-bold block">获授此标记的用户组 (Authorized Recipient Groups)</span>
              <div className="flex flex-wrap gap-1.5 pt-1">
                {currentMark.grantedGroups.map(grp => (
                  <span key={grp} className="bg-slate-950 border border-slate-800 px-2 py-0.5 rounded text-[10px] font-mono text-pink-300">
                    {grp}
                  </span>
                ))}
                <button
                  onClick={() => {
                    const newGrp = prompt('输入要追加授权的 LDAP 用户组名称:');
                    if (newGrp) {
                      setMarkings(markings.map(m => m.id === currentMark.id ? { ...m, grantedGroups: [...m.grantedGroups, newGrp] } : m));
                    }
                  }}
                  className="text-pink-400 hover:text-pink-300 font-bold text-[10px] px-1 bg-slate-950/50 rounded hover:bg-slate-900 cursor-pointer"
                >
                  + 增授
                </button>
              </div>
            </div>

            <div className="bg-slate-900/60 border border-slate-800 p-3.5 rounded-lg space-y-2">
              <span className="text-[10px] text-slate-400 font-bold block">当前锚定的根源数据集 (Anchored Root Datasets)</span>
              <div className="flex flex-wrap gap-1.5 pt-1">
                {currentMark.appliedDatasets.map(ds => (
                  <span key={ds} className="bg-slate-950 border border-slate-800 px-2 py-0.5 rounded text-[10px] font-mono text-slate-300">
                    {ds}
                  </span>
                ))}
              </div>
            </div>
          </div>

          {/* Lineage Propagation simulator graph */}
          <div className="bg-slate-900/40 border border-slate-800 rounded-lg p-4 space-y-4">
            <div className="space-y-0.5">
              <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                <Workflow size={13} className="text-pink-400" />
                Palantir 沿袭安全级联传递图 (Marking Propagation & Lineage Tracker)
              </span>
              <p className="text-[10px] text-slate-500">展现标记由于向下衍生转换，从而在底层管道中对下游节点产生的自动化锁定传染效果</p>
            </div>

            <div className="flex flex-col md:flex-row items-center justify-between gap-4 p-4 bg-slate-950/80 rounded border border-slate-800 relative">
              
              {/* Node 1 */}
              <div className="flex-1 text-center p-3 rounded bg-slate-900 border-2 border-slate-800 relative z-10 flex flex-col items-center gap-1">
                <Database size={16} className="text-slate-400" />
                <span className="text-[10px] font-bold text-slate-300">根源数据集</span>
                <span className="text-[8px] font-mono text-slate-500">ds_aviation_raw</span>
                <div className="mt-1.5 flex items-center gap-1 text-[8px] bg-pink-950/60 text-pink-400 border border-pink-900/30 px-1 rounded">
                  <Tag size={8} />
                  <span>已打标锁</span>
                </div>
              </div>

              {/* Arrow */}
              <div className="text-slate-700 font-bold flex items-center justify-center">
                <ArrowRight size={14} className="rotate-90 md:rotate-0" />
              </div>

              {/* Node 2 */}
              <div className="flex-1 text-center p-3 rounded bg-slate-900 border-2 border-pink-800/40 relative z-10 flex flex-col items-center gap-1 shadow-md shadow-pink-950/10">
                <Cpu size={16} className="text-pink-400" />
                <span className="text-[10px] font-bold text-slate-300">清洗同步管道</span>
                <span className="text-[8px] font-mono text-slate-500">sync_aviation_cleansed</span>
                <div className="mt-1.5 text-[8px] text-pink-400 font-extrabold flex items-center gap-0.5">
                  <span className="h-1 w-1 rounded-full bg-pink-500 animate-ping" />
                  <span>安全自动穿透 (Inherited)</span>
                </div>
              </div>

              {/* Arrow */}
              <div className="text-slate-700 font-bold flex items-center justify-center">
                <ArrowRight size={14} className="rotate-90 md:rotate-0" />
              </div>

              {/* Node 3 */}
              <div className="flex-1 text-center p-3 rounded bg-slate-900 border-2 border-pink-700/60 relative z-10 flex flex-col items-center gap-1 shadow-lg shadow-pink-950/20">
                <Workflow size={16} className="text-pink-400 animate-pulse" />
                <span className="text-[10px] font-bold text-slate-200">派生本体对象</span>
                <span className="text-[8px] font-mono text-slate-500">Aviation_Object_Derived</span>
                <div className="mt-1.5 flex items-center gap-1 text-[8px] bg-red-950 text-red-400 border border-red-900/30 px-1 rounded font-black">
                  <ShieldAlert size={8} />
                  <span>终点级联锁定</span>
                </div>
              </div>
            </div>

            <p className="text-[9px] text-slate-500 text-center italic">
              * ECOS 安全标准：任何衍生、转化、联结该根源数源的数据项都将自动继承 {currentMark.id}，无法通过普通视图清洗逻辑逃逸。
            </p>
          </div>
        </div>
      );
    })()}
  </div>
</div>
  );
}
