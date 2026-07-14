/**
 * ECOS 项目工作台 — fusion Tab
 * 从 ScenarioManagementView.tsx L1067-1262 拆分。
 * 引用的父级状态: ['activeScenario']
 */

import React from 'react';

import LucideIcon from '../../../components/LucideIcon';
import type { BusinessScenario } from '../types';

interface Props {
  activeScenario: BusinessScenario;
}

export default function FusionMatrixTab({ activeScenario }: Props) {
  return (
<div className="space-y-4">

  {/* Visual Fusion Flow Blueprint */}
  <div className="bg-slate-950 border border-slate-800 p-4 rounded-xl">
    <span className="text-xs font-extrabold text-slate-300 flex items-center gap-1.5 mb-3">
      <LucideIcon name="Workflow" size={14} className="text-indigo-400" />
      本场景全栈能力链路融合蓝图 (ECOS Capability Lineage Flow)
    </span>

    <div className="grid grid-cols-1 md:grid-cols-6 gap-3 items-center relative text-center">

      {/* Node 1: Physical Data */}
      <div className="bg-slate-900/90 border border-slate-800 p-3 rounded-lg relative flex flex-col items-center">
        <div className="p-2 rounded-full bg-blue-950 text-blue-400 mb-1.5">
          <LucideIcon name="Database" size={14} />
        </div>
        <span className="text-[10px] font-bold text-slate-300">1. 数据资产 (Data)</span>
        <div className="mt-2 space-y-1 w-full">
          {activeScenario.bindings.datasets.map(d => (
            <span key={d} className="block text-[8px] bg-slate-850 px-1 py-0.5 rounded border border-slate-800 font-mono text-slate-400 truncate">{d}</span>
          ))}
        </div>
      </div>

      <div className="hidden md:flex justify-center text-indigo-600">
        <LucideIcon name="ArrowRight" size={16} />
      </div>

      {/* Node 2: Logic Ontology */}
      <div className="bg-slate-900/90 border border-slate-800 p-3 rounded-lg relative flex flex-col items-center">
        <div className="p-2 rounded-full bg-indigo-950 text-indigo-400 mb-1.5">
          <LucideIcon name="Boxes" size={14} />
        </div>
        <span className="text-[10px] font-bold text-slate-300">2. 本体映射 (Ontology)</span>
        <div className="mt-2 space-y-1 w-full">
          {activeScenario.bindings.objectTypes.map(o => (
            <span key={o} className="block text-[8px] bg-slate-850 px-1 py-0.5 rounded border border-slate-800 font-mono text-slate-400 truncate">{o}</span>
          ))}
        </div>
      </div>

      <div className="hidden md:flex justify-center text-indigo-600">
        <LucideIcon name="ArrowRight" size={16} />
      </div>

      {/* Node 3: Knowledge RAG */}
      <div className="bg-slate-900/90 border border-slate-800 p-3 rounded-lg relative flex flex-col items-center">
        <div className="p-2 rounded-full bg-amber-950 text-amber-400 mb-1.5">
          <LucideIcon name="BookOpen" size={14} />
        </div>
        <span className="text-[10px] font-bold text-slate-300">3. 知识背景 (RAG)</span>
        <div className="mt-2 space-y-1 w-full">
          {activeScenario.bindings.knowledgeBases.map(k => (
            <span key={k} className="block text-[8px] bg-slate-850 px-1 py-0.5 rounded border border-slate-800 font-sans text-slate-400 truncate">{k}</span>
          ))}
        </div>
      </div>

      <div className="hidden md:flex justify-center text-indigo-600">
        <LucideIcon name="ArrowRight" size={16} />
      </div>

      {/* Node 4: AI Copilot */}
      <div className="bg-slate-900/90 border border-slate-800 p-3 rounded-lg relative flex flex-col items-center">
        <div className="p-2 rounded-full bg-pink-950 text-pink-400 mb-1.5">
          <LucideIcon name="Bot" size={14} />
        </div>
        <span className="text-[10px] font-bold text-slate-300">4. AI 决策 (Agents)</span>
        <div className="mt-2 space-y-1 w-full">
          {activeScenario.bindings.aiAgents.map(a => (
            <span key={a} className="block text-[8px] bg-slate-850 px-1 py-0.5 rounded border border-slate-800 font-sans text-slate-400 truncate">{a}</span>
          ))}
        </div>
      </div>

      <div className="hidden md:flex justify-center text-indigo-600">
        <LucideIcon name="ArrowRight" size={16} />
      </div>

      {/* Node 5: Zero-Trust Guardrails */}
      <div className="bg-slate-900/90 border border-slate-800 p-3 rounded-lg relative flex flex-col items-center">
        <div className="p-2 rounded-full bg-rose-950 text-rose-400 mb-1.5">
          <LucideIcon name="ShieldAlert" size={14} />
        </div>
        <span className="text-[10px] font-bold text-slate-300">5. 安全围栏 (Security)</span>
        <div className="mt-2 space-y-1 w-full">
          {activeScenario.bindings.securityPolicies.map(s => (
            <span key={s} className="block text-[8px] bg-slate-850 px-1 py-0.5 rounded border border-slate-800 font-mono text-slate-400 truncate">{s}</span>
          ))}
        </div>
      </div>

      <div className="hidden md:flex justify-center text-indigo-600">
        <LucideIcon name="ArrowRight" size={16} />
      </div>

      {/* Node 6: Applications */}
      <div className="bg-slate-900/90 border border-slate-800 p-3 rounded-lg relative flex flex-col items-center">
        <div className="p-2 rounded-full bg-violet-950 text-violet-400 mb-1.5">
          <LucideIcon name="LayoutGrid" size={14} />
        </div>
        <span className="text-[10px] font-bold text-slate-300">6. 构建面板 (App UI)</span>
        <div className="mt-2 space-y-1 w-full">
          {activeScenario.bindings.interfaces.map(i => (
            <span key={i} className="block text-[8px] bg-slate-850 px-1 py-0.5 rounded border border-slate-800 font-sans text-slate-400 truncate">{i}</span>
          ))}
        </div>
      </div>

    </div>
  </div>

  {/* Scenario Goals & Business Justifications */}
  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col justify-between">
      <div>
        <span className="text-xs font-bold text-slate-400 flex items-center gap-1">
          <LucideIcon name="Goal" size={12} className="text-blue-500" />
          场景战略目标与业务痛点
        </span>
        <h3 className="text-sm font-bold text-white mt-2 leading-snug">{activeScenario.businessGoal}</h3>
        <p className="text-xs text-slate-400 mt-2 leading-relaxed">
          本场景的核心目标是利用<b>大模型对账推理 + 本体多对多约束</b>，彻底根除企业内各子公司/部门间长期存在的数据孤岛问题。通过建立点对点逻辑实体，把业务总监从繁重的传统关系型 SQL 手工核实与越权隐患中解放出来。
        </p>
      </div>

      <div className="mt-4 bg-slate-950/60 p-3 rounded border border-slate-800 text-[11px] leading-relaxed">
        💡 <b>产品经理建议 (PM Advice)</b>：在暑运大流量时段，可配合「AI 工作台」的<b>自动提案引擎</b>启动二次人工核实（Dual-Review Control Flow）。任何涉及航班状态物理写入的动作，都必须经过本场景安全卡片的总监二次数字签名验证方可写入物理主库。
      </div>
    </div>

    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl">
      <span className="text-xs font-bold text-slate-400 flex items-center gap-1 mb-3">
        <LucideIcon name="PieChart" size={12} className="text-indigo-400" />
        当前场景融合完整度度量 (Integrity Assessment)
      </span>

      <div className="space-y-4">
        <div>
          <div className="flex justify-between text-xs font-mono mb-1.5">
            <span className="text-slate-400 flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-blue-500" />
              本体多对多数据映射对齐度 (Ontology Mapping)
            </span>
            <span className="font-bold text-white">{activeScenario.metrics.integrityScore}%</span>
          </div>
          <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
            <div className="bg-blue-500 h-full rounded-full transition-all duration-500" style={{ width: `${activeScenario.metrics.integrityScore}%` }} />
          </div>
        </div>

        <div>
          <div className="flex justify-between text-xs font-mono mb-1.5">
            <span className="text-slate-400 flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-indigo-500" />
              底层物理主键契约一致性 (Physical Primary Key Contract)
            </span>
            <span className="font-bold text-white">{activeScenario.metrics.mappingCompleteness}%</span>
          </div>
          <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
            <div className="bg-indigo-500 h-full rounded-full transition-all duration-500" style={{ width: `${activeScenario.metrics.mappingCompleteness}%` }} />
          </div>
        </div>

        <div>
          <div className="flex justify-between text-xs font-mono mb-1.5">
            <span className="text-slate-400 flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
              零信任边界合规防御度 (Zero-Trust Guardrail Coverage)
            </span>
            <span className="font-bold text-white">{activeScenario.metrics.threatBlockRate}%</span>
          </div>
          <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
            <div className="bg-emerald-500 h-full rounded-full transition-all duration-500" style={{ width: `${activeScenario.metrics.threatBlockRate}%` }} />
          </div>
        </div>

        <div>
          <div className="flex justify-between text-xs font-mono mb-1.5">
            <span className="text-slate-400 flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
              场景综合运行 SLA 达标指数 (Overall SLA Score)
            </span>
            <span className="font-bold text-white">{activeScenario.metrics.slaScore}%</span>
          </div>
          <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
            <div className="bg-amber-500 h-full rounded-full transition-all duration-500" style={{ width: `${activeScenario.metrics.slaScore}%` }} />
          </div>
        </div>
      </div>
    </div>
  </div>

</div>
  );
}
