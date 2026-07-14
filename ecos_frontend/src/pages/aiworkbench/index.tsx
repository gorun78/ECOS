/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import {
  mockAIPAuditLogs
} from './mockData';
import { AIPLogicPipeline, AIPAgent, AIPModel, AIPGuardrail, AIPAuditLog } from '../../types/aiworkbench';
import { fetchAIPAgentsFromMesh, fetchGuardrailPolicies, fetchPipelineDefinitions, fetchAgentModels } from './api';
import DashboardView from './DashboardView';
import LogicView from './LogicView';
import AgentStudioView from './AgentStudioView';
import ChatbotStudioView from './ChatbotStudioView';
import ModelCatalogView from './ModelCatalogView';
import GuardrailsView from './GuardrailsView';
import KnowledgeView from './KnowledgeView';
import * as Icons from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface AIPWorkbenchProps {
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

export default function AIPWorkbench({ showToast }: AIPWorkbenchProps) {
  const [currentTab, setCurrentTab] = useState<'dashboard' | 'logic' | 'agent' | 'chatbot' | 'knowledge' | 'model' | 'guardrails'>('dashboard');

  // Master AIP States (loaded from backend API, fallback to empty)
  const [pipelines, setPipelines] = useState<AIPLogicPipeline[]>([]);
  const [agents, setAgents] = useState<AIPAgent[]>([]);
  const [models, setModels] = useState<AIPModel[]>([]);
  const [guardrails, setGuardrails] = useState<AIPGuardrail[]>([]);
  const [auditLogs, setAuditLogs] = useState<AIPAuditLog[]>(mockAIPAuditLogs);
  const { styles } = useTheme();

  // Load data from backend APIs on mount
  useEffect(() => {
    fetchAIPAgentsFromMesh().then(setAgents).catch(() => {});
    fetchGuardrailPolicies().then(setGuardrails).catch(() => {});
    fetchPipelineDefinitions().then(setPipelines).catch(() => {});
    fetchAgentModels().then(setModels).catch(() => {});
  }, []);

  const handleAddAuditLog = (newLog: AIPAuditLog) => {
    setAuditLogs(prev => [newLog, ...prev]);
  };

  return (
    <div className={"h-full w-full flex flex-row overflow-hidden " + styles.appBg + " " + styles.appText + " font-sans"}>
      
      {/* 1. AIP Left Sidebar */}
      <aside className="w-64 bg-slate-950 border-r border-slate-800 flex flex-col h-full select-none shrink-0 text-xs">
        
        {/* Title / Branding */}
        <div className="p-3 border-b border-slate-700 bg-slate-950">
          <div className="py-2 px-3 bg-slate-900 text-white rounded-lg flex items-center justify-between shadow-xs">
            <div className="flex items-center gap-2 truncate">
              <span className="p-1 rounded bg-blue-600 text-white flex items-center justify-center">
                <Icon name="Bot" size={13} className="animate-pulse" />
              </span>
              <div className="flex flex-col min-w-0">
                <span className="font-extrabold text-[11px] leading-tight text-white tracking-wide">ECOS AIP</span>
                <span className="text-[9px] text-slate-400 font-medium leading-none mt-0.5">AI集成开发控制台</span>
              </div>
            </div>
            <span className="text-[9px] bg-slate-800 text-slate-400 px-1 py-0.5 rounded font-mono select-none shrink-0">v2.4</span>
          </div>
        </div>

        {/* Sidebar Nav Items */}
        <div className="flex-1 overflow-y-auto p-3 space-y-1">
          {[
            { id: 'dashboard', label: '总览仪表盘', desc: 'AIP指标、动态与调用追踪', icon: 'LayoutDashboard' },
            { id: 'logic', label: 'AIP Logic 逻辑编排', desc: 'LLM工作流与计算管道', icon: 'Cpu' },
            { id: 'agent', label: 'AIP Agent 智能体协同', desc: '智能助理多步多模态协同', icon: 'Network' },
            { id: 'chatbot', label: 'AIP Chatbot Studio', desc: '对话智能工坊与嵌入式配置', icon: 'Bot' },
            { id: 'model', label: 'Model Catalog 模型目录', desc: '自研与第三方大模型集成', icon: 'Layers' },
            { id: 'guardrails', label: 'AIP Guardrails 安全审计', desc: '输入输出卫士与隐私脱敏', icon: 'ShieldCheck' }
          ].map(tab => {
            const isActive = currentTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setCurrentTab(tab.id as any)}
                className={`w-full p-2.5 rounded-lg text-left transition-all cursor-pointer flex items-start gap-3 border ${
                  isActive
                    ? 'bg-slate-950 text-slate-950 border-slate-700/80 shadow-xs'
                    : 'text-slate-400 hover:text-slate-100 hover:bg-slate-700/50 border-transparent'
                }`}
              >
                <span className={`p-1.5 rounded-md flex items-center justify-center shrink-0 transition-all ${
                  isActive ? 'bg-slate-900 text-white shadow-xs' : 'bg-slate-700 text-slate-300'
                }`}>
                  <Icon name={tab.icon} size={13} />
                </span>
                <div className="flex flex-col min-w-0">
                  <span className={`text-[11px] font-bold leading-normal ${isActive ? 'text-slate-950' : 'text-slate-300'}`}>{tab.label}</span>
                  <span className="text-[9px] truncate text-slate-400 mt-0.5 font-medium leading-none">
                    {tab.desc}
                  </span>
                </div>
              </button>
            );
          })}
        </div>

        {/* Sidebar Footer with Running Badge */}
        <div className="p-3 border-t border-slate-700 bg-slate-950 shrink-0">
          <div className="p-2.5 bg-slate-950 border border-slate-700 rounded-lg space-y-1.5 shadow-2xs">
            <div className="flex items-center justify-between text-[10px] font-bold text-slate-500">
              <span className="flex items-center gap-1.5">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-ping" />
                <span>LLM 安全引擎在线</span>
              </span>
              <span className="text-[9px] bg-emerald-50 text-emerald-600 px-1.5 py-0.5 rounded font-mono">ACTIVE</span>
            </div>
            <div className="w-full bg-slate-800 rounded-full h-1">
              <div className="bg-emerald-500 h-1 rounded-full w-4/5 animate-pulse"></div>
            </div>
            <p className="text-[9px] text-slate-400 leading-tight">安全代理: Active Shield v2.4 (Sovereign)</p>
          </div>
        </div>

      </aside>

      {/* 2. Active View Panel Render */}
      <div className="flex-1 overflow-hidden relative flex flex-col bg-slate-900">
        
        {/* Right Stage Sub-Header for View Title */}
        <div className="h-10 bg-slate-950 border-b border-slate-700 px-6 flex items-center justify-between shrink-0 select-none">
          <div className="flex items-center gap-2">
            <span className="font-extrabold text-xs text-slate-200">
              {currentTab === 'dashboard' && '总览仪表盘 (Dashboard)'}
              {currentTab === 'logic' && 'AIP Logic 逻辑编排 (Pipelines)'}
              {currentTab === 'agent' && 'AIP Agent 智能体协同 (Agents)'}
              {currentTab === 'chatbot' && 'AIP Chatbot Studio 智能对话工坊 (Chatbots)'}
              {currentTab === 'knowledge' && 'AIP Knowledge 知识库闭环 (Knowledge)'}
              {currentTab === 'model' && 'Model Catalog 模型目录 (Models)'}
              {currentTab === 'guardrails' && 'AIP Guardrails 安全审计 (Guardrails)'}
            </span>
            <span className="text-[10px] bg-slate-800 text-slate-500 px-1.5 py-0.5 rounded font-mono font-medium">
              ECOS_AIP_MODULE
            </span>
          </div>
          <div className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-full bg-blue-500 animate-pulse" />
            <span className="text-[10px] text-slate-400 font-medium">Sovereign Boundary</span>
          </div>
        </div>

        <div className="flex-1 overflow-hidden relative">
          {currentTab === 'dashboard' && (
            <DashboardView
              pipelines={pipelines}
              agents={agents}
              models={models}
              auditLogs={auditLogs}
              onNavigateToView={(view) => setCurrentTab(view as any)}
            />
          )}

          {currentTab === 'logic' && (
            <LogicView
              pipelines={pipelines}
              models={models}
              onUpdatePipelines={setPipelines}
              showToast={showToast}
            />
          )}

          {currentTab === 'agent' && (
            <AgentStudioView
              agents={agents}
              models={models}
              guardrails={guardrails}
              onUpdateAgents={setAgents}
              onAddAuditLog={handleAddAuditLog}
              showToast={showToast}
            />
          )}

          {currentTab === 'chatbot' && (
            <ChatbotStudioView
              agents={agents}
              models={models}
              guardrails={guardrails}
              onUpdateAgents={setAgents}
              onAddAuditLog={handleAddAuditLog}
              showToast={showToast}
            />
          )}

          {currentTab === 'knowledge' && (
            <KnowledgeView showToast={showToast} />
          )}

          {currentTab === 'model' && (
            <ModelCatalogView
              models={models}
              onUpdateModels={setModels}
              showToast={showToast}
            />
          )}

          {currentTab === 'guardrails' && (
            <GuardrailsView
              guardrails={guardrails}
              onUpdateGuardrails={setGuardrails}
              showToast={showToast}
            />
          )}
        </div>
      </div>

    </div>
  );
}
