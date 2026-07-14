/**
 * DacTab — 安全中心 "dac" 标签页
 * @license Apache-2.0
 */
import React from 'react';
import { FolderGit, Plus, X, Shield, ShieldAlert, ShieldCheck, Users, BookOpen, Key, ChevronRight, EyeOff, UserPlus } from 'lucide-react';
import { LucideIcon } from '../helpers';
import type { TabProps } from './types';

export default function DacTab(props: TabProps) {
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
  
  {/* Left Projects list */}
  <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-4">
    <div className="flex items-center justify-between border-b border-slate-800 pb-2">
      <span className="text-xs font-bold text-slate-200">受控安全工程项目</span>
      <span className="text-[9px] font-mono text-slate-500">DAC CONTROLS</span>
    </div>
    <div className="space-y-2">
      {projects.map(proj => (
        <button
          key={proj.id}
          onClick={() => setSelectedProjectId(proj.id)}
          className={`w-full text-left p-3 rounded-lg border transition-all flex flex-col gap-1 cursor-pointer ${
            selectedProjectId === proj.id
              ? 'bg-slate-900 border-indigo-500/50 shadow-md shadow-indigo-500/5'
              : 'bg-slate-950 border-slate-800 hover:bg-slate-900'
          }`}
        >
          <div className="text-xs font-extrabold text-slate-100 truncate">{proj.name}</div>
          <p className="text-[10px] text-slate-400 line-clamp-1">{proj.description}</p>
          <div className="flex items-center gap-1.5 mt-1 text-[9px] text-slate-500">
            <Users size={10} />
            <span>授权成员: {proj.members.length}</span>
            <span>•</span>
            <span className={proj.autoPropagation ? 'text-emerald-400' : 'text-slate-500'}>
              {proj.autoPropagation ? '沿袭传播已开' : '不传播'}
            </span>
          </div>
        </button>
      ))}
    </div>
  </div>

  {/* Right Members Detail */}
  <div className="lg:col-span-2 bg-slate-950 border border-slate-800 rounded-xl p-5 space-y-6">
    {(() => {
      const currentProj = projects.find(p => p.id === selectedProjectId);
      if (!currentProj) return <div className="text-slate-500 text-xs">请在左侧选择项目</div>;

      return (
        <div className="space-y-6">
          <div className="flex items-start justify-between border-b border-slate-800 pb-4">
            <div className="space-y-1">
              <h3 className="text-sm font-black text-slate-100">{currentProj.name}</h3>
              <p className="text-xs text-slate-400 leading-relaxed">{currentProj.description}</p>
            </div>
            <button
              onClick={() => setShowAddMemberModal(true)}
              className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1 shrink-0 cursor-pointer"
            >
              <UserPlus size={13} />
              <span>授予成员角色</span>
            </button>
          </div>

          {/* Project ACL Rules */}
          <div className="grid grid-cols-2 gap-4">
            <div className="bg-slate-900/60 border border-slate-800 p-3.5 rounded-lg flex items-center justify-between">
              <div className="space-y-1">
                <div className="text-[10px] text-slate-400 font-bold">跨组织可见性 (Discoverability)</div>
                <div className="text-xs text-slate-200">
                  {currentProj.discoverableAllOrgs ? '允许其他组织在全局索引中检索' : '仅限本组织可见与索引'}
                </div>
              </div>
              <button
                onClick={() => {
                  setProjects(projects.map(p => p.id === currentProj.id ? { ...p, discoverableAllOrgs: !p.discoverableAllOrgs } : p));
                }}
                className={`p-1 px-2.5 rounded text-[10px] font-black cursor-pointer ${
                  currentProj.discoverableAllOrgs ? 'bg-indigo-950 text-indigo-400' : 'bg-slate-800 text-slate-400'
                }`}
              >
                切换可见性
              </button>
            </div>

            <div className="bg-slate-900/60 border border-slate-800 p-3.5 rounded-lg flex items-center justify-between">
              <div className="space-y-1">
                <div className="text-[10px] text-slate-400 font-bold">继承传播锁 (Auto-Inheritance)</div>
                <div className="text-xs text-slate-200">
                  {currentProj.autoPropagation ? '下游派生项目强制自动继承此权限' : '当下游产生新工程时重置鉴权'}
                </div>
              </div>
              <button
                onClick={() => {
                  setProjects(projects.map(p => p.id === currentProj.id ? { ...p, autoPropagation: !p.autoPropagation } : p));
                }}
                className={`p-1 px-2.5 rounded text-[10px] font-black cursor-pointer ${
                  currentProj.autoPropagation ? 'bg-indigo-950 text-indigo-400' : 'bg-slate-800 text-slate-400'
                }`}
              >
                切换沿袭
              </button>
            </div>
          </div>

          {/* Members Table */}
          <div className="space-y-3">
            <div className="text-xs font-bold text-slate-300 flex items-center gap-1">
              <ShieldAlert size={13} className="text-indigo-400" />
              <span>成员自主授权列表 (Access Control List - ACL)</span>
            </div>
            <div className="border border-slate-800 rounded-lg overflow-hidden bg-slate-900/30">
              <table className="w-full text-left text-xs text-slate-300">
                <thead className="bg-slate-950 text-slate-400 text-[10px] font-extrabold uppercase tracking-wider border-b border-slate-800">
                  <tr>
                    <th className="p-3">用户名称 (Subject)</th>
                    <th className="p-3">分配角色 (Role)</th>
                    <th className="p-3">授权执行人 (Granted By)</th>
                    <th className="p-3">授权日期 (Granted At)</th>
                    <th className="p-3 text-right">操作</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-mono">
                  {currentProj.members.map(member => (
                    <tr key={member.username} className="hover:bg-slate-900/50">
                      <td className="p-3 font-semibold text-slate-200">{member.username}</td>
                      <td className="p-3">
                        <span className={`px-2 py-0.5 rounded text-[9px] font-extrabold ${
                          member.role === 'Owner' ? 'bg-red-950 text-red-400 border border-red-900/30' :
                          member.role === 'Editor' ? 'bg-blue-950 text-blue-400 border border-blue-900/30' :
                          member.role === 'Viewer' ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/30' :
                          'bg-slate-800 text-slate-400'
                        }`}>
                          {member.role}
                        </span>
                      </td>
                      <td className="p-3 text-slate-400">{member.grantedBy}</td>
                      <td className="p-3 text-slate-500">{member.grantedAt}</td>
                      <td className="p-3 text-right">
                        <button
                          onClick={() => {
                            setProjects(projects.map(p => {
                              if (p.id === currentProj.id) {
                                return {
                                  ...p,
                                  members: p.members.filter(m => m.username !== member.username)
                                };
                              }
                              return p;
                            }));
                          }}
                          className="text-red-400 hover:text-red-300 font-bold transition-colors cursor-pointer"
                        >
                          吊销
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      );
    })()}
  </div>
</div>
  );
}
