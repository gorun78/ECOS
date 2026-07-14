/**
 * Row_colTab — 安全中心 "row_col" 标签页
 * @license Apache-2.0
 */
import React from 'react';
import { Database, EyeOff, Filter, X, Shield, ShieldAlert, ShieldCheck, BookOpen, ChevronRight, Plus } from 'lucide-react';
import { LucideIcon } from '../helpers';
import type { TabProps } from './types';

export default function Row_colTab(props: TabProps) {
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
  
  {/* Left Datasets with custom security settings */}
  <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-4">
    <div className="flex items-center justify-between border-b border-slate-800 pb-2">
      <span className="text-xs font-bold text-slate-200">受动态策略约束的数据表</span>
      <span className="text-[9px] font-mono text-slate-500">ROW/COL CONFIGS</span>
    </div>
    <div className="space-y-2">
      {rowColPolicies.map(item => (
        <button
          key={item.datasetId}
          onClick={() => setSelectedRowColDs(item.datasetId)}
          className={`w-full text-left p-3 rounded-lg border transition-all flex flex-col gap-1 cursor-pointer ${
            selectedRowColDs === item.datasetId
              ? 'bg-slate-900 border-indigo-500/50 shadow-md shadow-indigo-500/5'
              : 'bg-slate-950 border-slate-800 hover:bg-slate-900'
          }`}
        >
          <div className="text-xs font-extrabold text-slate-100 truncate">{item.datasetName}</div>
          <div className="flex items-center gap-2 mt-1.5 text-[9px] text-slate-500">
            <span>列脱敏: {item.columnMasks.filter(c => c.active && c.maskType !== 'NONE').length}项</span>
            <span>•</span>
            <span>行过滤: {item.rowFilters.filter(r => r.active).length}条</span>
          </div>
        </button>
      ))}
    </div>
  </div>

  {/* Right Detail configuration & Interactive Preview */}
  <div className="lg:col-span-2 bg-slate-950 border border-slate-800 rounded-xl p-5 space-y-6">
    {(() => {
      const currentPolicy = rowColPolicies.find(p => p.datasetId === selectedRowColDs);
      if (!currentPolicy) return <div className="text-slate-500 text-xs">请选择数据集</div>;

      return (
        <div className="space-y-6">
          
          <div className="border-b border-slate-800 pb-4">
            <h3 className="text-sm font-black text-slate-100">{currentPolicy.datasetName}</h3>
            <p className="text-[10px] text-slate-400">在此处针对特定的列项设置动态遮蔽(SHA256哈希、全部抹除或部分脱敏)，或添加SQL风格的行级隔离条件。</p>
          </div>

          {/* Column Masking section */}
          <div className="space-y-3">
            <div className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
              <EyeOff size={13} className="text-indigo-400" />
              <span>列级敏感字段遮蔽规则 (Dynamic Column Masking)</span>
            </div>
            <div className="border border-slate-800 rounded-lg overflow-hidden bg-slate-900/40">
              <table className="w-full text-left text-xs text-slate-300">
                <thead className="bg-slate-950 text-slate-400 text-[10px] font-extrabold uppercase tracking-wider border-b border-slate-800">
                  <tr>
                    <th className="p-3">列字段名</th>
                    <th className="p-3">脱敏手段</th>
                    <th className="p-3">运行状态</th>
                    <th className="p-3 text-right">切换开关</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-mono text-[11px]">
                  {currentPolicy.columnMasks.map(col => (
                    <tr key={col.column} className="hover:bg-slate-900/30">
                      <td className="p-3 font-semibold text-slate-200">{col.column}</td>
                      <td className="p-3">
                        <span className={`px-2 py-0.5 rounded text-[9px] font-bold ${
                          col.maskType === 'REDACT' ? 'bg-red-950 text-red-400 border border-red-900/30' :
                          col.maskType === 'SHA256' ? 'bg-orange-950 text-orange-400 border border-orange-900/30' :
                          col.maskType === 'PARTIAL' ? 'bg-blue-950 text-blue-400 border border-blue-900/30' :
                          'bg-slate-800 text-slate-400'
                        }`}>
                          {col.maskType === 'NONE' ? '不遮蔽 (NONE)' : col.maskType}
                        </span>
                      </td>
                      <td className="p-3">
                        <span className={`h-1.5 w-1.5 inline-block rounded-full mr-1.5 ${col.active && col.maskType !== 'NONE' ? 'bg-emerald-400 animate-pulse' : 'bg-slate-500'}`} />
                        <span>{col.active && col.maskType !== 'NONE' ? '已拦截脱敏中' : '明文放行'}</span>
                      </td>
                      <td className="p-3 text-right">
                        <button
                          disabled={col.maskType === 'NONE'}
                          onClick={() => handleToggleColumnMask(currentPolicy.datasetId, col.column)}
                          className={`p-1 px-2 text-[10px] font-bold rounded cursor-pointer ${
                            col.maskType === 'NONE' ? 'opacity-30 cursor-not-allowed' :
                            col.active ? 'bg-emerald-950 text-emerald-400 hover:bg-emerald-900' : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
                          }`}
                        >
                          {col.active ? '激活生效' : '已关闭'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Row Filters */}
          <div className="space-y-3">
            <div className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
              <Filter size={13} className="text-indigo-400" />
              <span>行级动态物理隔离策略 (Row-Level Security Policies)</span>
            </div>
            {currentPolicy.rowFilters.length === 0 ? (
              <div className="text-center p-6 bg-slate-900/30 border border-slate-800 rounded-lg text-slate-500 text-xs">
                当前数据集没有添加任何行过滤策略。
              </div>
            ) : (
              <div className="space-y-2">
                {currentPolicy.rowFilters.map((filter, idx) => (
                  <div key={idx} className="bg-slate-900/60 border border-slate-800 p-3.5 rounded-lg flex items-center justify-between">
                    <div className="space-y-1">
                      <div className="text-[10px] text-indigo-400 font-mono font-bold">SQL FILTER: {filter.filterSql}</div>
                      <p className="text-[10px] text-slate-400">{filter.description}</p>
                    </div>
                    <button
                      onClick={() => handleToggleRowFilter(currentPolicy.datasetId, idx)}
                      className={`p-1.5 rounded text-[10px] font-black cursor-pointer ${
                        filter.active ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/40' : 'bg-slate-800 text-slate-400'
                      }`}
                    >
                      {filter.active ? '过滤激活中' : '未开启'}
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Dynamic Preview Simulation */}
          <div className="bg-slate-900/30 border border-slate-800 rounded-lg p-4 space-y-3.5">
            <div className="flex items-center justify-between border-b border-slate-800/60 pb-2">
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                当前脱敏输出预览 (Dynamic Masking Simulation Preview)
              </span>
              <span className="text-[8px] bg-slate-800 px-1.5 py-0.5 rounded text-slate-500 font-mono">
                Based on Active Policies
              </span>
            </div>

            {/* Interactive columns view */}
            {currentPolicy.datasetId === 'ds_flight_schedules' ? (
              <div className="font-mono text-[10px] text-slate-300 space-y-2">
                <div className="grid grid-cols-3 gap-2 bg-slate-950 p-2 rounded text-slate-400 font-bold border-b border-slate-800">
                  <span>航班号 (flight_num)</span>
                  <span>机长ID (captain_id)</span>
                  <span>延误时长 (delay_minutes)</span>
                </div>
                <div className="grid grid-cols-3 gap-2 p-2 rounded bg-slate-900/80 hover:bg-slate-900">
                  <span>CA-1209</span>
                  <span className="text-orange-400">
                    {currentPolicy.columnMasks.find(c => c.column === 'captain_id')?.active ? '0x8f7d9a1c... (SHA256)' : 'captain_john_09'}
                  </span>
                  <span>12 分钟</span>
                </div>
                <div className="grid grid-cols-3 gap-2 p-2 rounded bg-slate-900/80 hover:bg-slate-900">
                  <span>MU-3342</span>
                  <span className="text-orange-400">
                    {currentPolicy.columnMasks.find(c => c.column === 'captain_id')?.active ? '0xa2f10b89... (SHA256)' : 'captain_lee_32'}
                  </span>
                  <span>3 分钟</span>
                </div>
              </div>
            ) : currentPolicy.datasetId === 'ds_pilots_biography' ? (
              <div className="font-mono text-[10px] text-slate-300 space-y-2">
                <div className="grid grid-cols-3 gap-2 bg-slate-950 p-2 rounded text-slate-400 font-bold border-b border-slate-800">
                  <span>身份证号 (ssn_number)</span>
                  <span>邮箱地址 (email_address)</span>
                  <span>基本月薪 (base_salary)</span>
                </div>
                <div className="grid grid-cols-3 gap-2 p-2 rounded bg-slate-900/80 hover:bg-slate-900">
                  <span className="text-red-400">
                    {currentPolicy.columnMasks.find(c => c.column === 'ssn_number')?.active ? '[REDACTED] (绝对遮蔽)' : '11010119881023001X'}
                  </span>
                  <span className="text-blue-400">
                    {currentPolicy.columnMasks.find(c => c.column === 'email_address')?.active ? 'jo***@aviation.com' : 'john.smith@aviation.com'}
                  </span>
                  <span className="text-red-400">
                    {currentPolicy.columnMasks.find(c => c.column === 'base_salary')?.active ? '[REDACTED] (绝对遮蔽)' : '¥45,000'}
                  </span>
                </div>
              </div>
            ) : (
              <div className="text-center py-4 text-slate-500 text-xs">暂无可用预览行数据</div>
            )}
          </div>
        </div>
      );
    })()}
  </div>
</div>
  );
}
