/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import type { ThemeStyles } from '../../../components/ThemeContext';
import * as Icons from 'lucide-react';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface ClosedLoopTabProps {
  styles: ThemeStyles;
}

export default function ClosedLoopTab({ styles }: ClosedLoopTabProps) {
  const { t } = useLanguage();
  return (
    <div className="space-y-6 max-w-4xl">
      
      {/* Header banner */}
      <div className={`${styles.appBg} text-white p-5 rounded-2xl flex flex-col justify-between gap-2 shadow-md relative overflow-hidden`}>
        <div className="absolute right-0 bottom-0 opacity-10 transform translate-x-10 translate-y-10 scale-150">
          <Icon name="Bot" size={240} />
        </div>
        <div className="space-y-1 z-10">
          <span className="px-2 py-0.5 bg-blue-500 text-white text-[9px] font-black rounded uppercase tracking-wider">
            Closed-Loop Research Design
          </span>
          <h1 className="text-base font-black tracking-tight">
            集成工作台、本体工作台、安全中心数据赋能 AIP Copilot 研究方案
          </h1>
          <p className={`text-xs ${styles.cardTextMuted} font-sans max-w-2xl leading-relaxed`}>
            本方案设计了数据全景元数据向 Agent 智能体输送、形成高精度向量知识库 (Vector Knowledge Base)，进而生成动作建议，最终经过安全过滤与审计写回物理/本体存储的"完美闭环"。
          </p>
        </div>
      </div>

      {/* Core Q&A Section */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        
        <div className={`${styles.cardBg} border ${styles.cardBorder} p-4 rounded-xl shadow-xs space-y-2`}>
          <h3 className={`font-black ${styles.cardText} text-xs flex items-center gap-1.5 text-blue-600`}>
            <Icon name="Workflow" size={13} />
            <span>Q1: {t('aiworkbench.knowledge.closedLoop.whyTitle')}</span>
          </h3>
          <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed font-sans`}>
            传统的 AI Copilot 仅具备大模型的通用常识，对<strong>航空调度场景中的业务上下文、物理数据时效、本体层级语义（Doris物理库到Flight实体的关系）以及严密的 GDPR/审计合规性完全感知缺失</strong>。通过融合三大工作台的数据与元数据，才能为 Copilot 注入"上帝视角"的数据语义及安全底网，消除生成幻觉，防止安全溢出。
          </p>
        </div>

        <div className={`${styles.cardBg} border ${styles.cardBorder} p-4 rounded-xl shadow-xs space-y-2`}>
          <h3 className={`font-black ${styles.cardText} text-xs flex items-center gap-1.5 text-indigo-600`}>
            <Icon name="Database" size={13} />
            <span>Q2: {t('aiworkbench.knowledge.closedLoop.howTitle')}</span>
          </h3>
          <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed font-sans`}>
            本平台采用<strong>联邦多模元数据同步引擎</strong>。首先从集成工作台拉取宽表结构与血缘；其次提取本体 ObjectType 结构及提权动作(Actions)；最后叠加安全中心的安全围栏与行列掩码。元数据统一由 <strong>Embedding 模型向量化</strong>切片，构建成高度结构化的<strong>向量特征图谱知识库</strong>，直接对齐至 Agent Copilot，随用随检索(RAG)。
          </p>
        </div>

        <div className={`${styles.cardBg} border ${styles.cardBorder} p-4 rounded-xl shadow-xs space-y-2`}>
          <h3 className={`font-black ${styles.cardText} text-xs flex items-center gap-1.5 text-rose-600`}>
            <Icon name="ShieldAlert" size={13} />
            <span>Q3: {t('aiworkbench.knowledge.closedLoop.safetyTitle')}？</span>
          </h3>
          <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed font-sans`}>
            <strong>闭环的关键在于数据流的向外延展与阻断返回。</strong>当 Agent 利用知识库向用户生成修改建议（如UA102航班延误120分钟）时，会强制触发 Ontology Action 人工确认(Guardrails)；当用户确认授权写回本体后，该修改行为会直接在安全中心生成一条高敏 Audit 审计记录，重新成为系统诊断的元数据，实现"自适应进化闭环"。
          </p>
        </div>

        <div className={`${styles.cardBg} border ${styles.cardBorder} p-4 rounded-xl shadow-xs space-y-2 flex flex-col justify-between`}>
          <h3 className={`font-black ${styles.cardText} text-xs flex items-center gap-1.5 text-emerald-600`}>
            <Icon name="Cpu" size={13} />
            <span>Q4: 智能体工作台(Agent Sandbox)如何完善？</span>
          </h3>
          <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed font-sans`}>
            通过在 AIP Workbench 中建立<strong>一键同步与仿真沙箱(Sandbox Simulation)</strong>，开发者可以在设计、发布智能体之前，直接通过提示词和真实元数据对齐进行"干涉测试"，确保所有的 PII 遮蔽和 SQL row-filter 在 LLM 推理层就能提前拦截生效。
          </p>
        </div>

      </div>

      {/* Visual Interactive Map Schema */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs space-y-4`}>
        <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1.5`}>
          <Icon name="Activity" size={12} className="text-blue-500" />
          <span>四维元数据物理-逻辑-安全-推理完美闭环流动拓扑 (Closed-Loop Topology)</span>
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3 text-center">
          
          {/* Step 1 */}
          <div className={`p-3 ${styles.inputBg} border ${styles.cardBorder} rounded-xl space-y-1.5 relative`}>
            <div className={`text-[10px] font-mono font-bold ${styles.cardTextMuted}`}>1. 集成工作台 (数据源)</div>
            <h4 className={`font-bold ${styles.cardText} text-xs`}>物理层元数据</h4>
            <p className={`text-[10px] ${styles.cardTextMuted} font-sans`}>Doris宽表、Apache血缘、数据健壮性监控等底层物理元数据</p>
            <div className={`absolute top-1/2 -right-2 transform -translate-y-1/2 hidden md:block ${styles.cardTextMuted}`}>▶</div>
          </div>

          {/* Step 2 */}
          <div className="p-3 bg-blue-50/50 border border-blue-200 rounded-xl space-y-1.5 relative">
            <div className="text-[10px] font-mono font-bold text-blue-500">2. 本体工作台 (逻辑映射)</div>
            <h4 className="font-bold text-blue-800 text-xs">逻辑层语义</h4>
            <p className="text-[10px] text-blue-600 font-sans">Object Types (Flight, Pilot), Links及可调用的写回Action算子</p>
            <div className={`absolute top-1/2 -right-2 transform -translate-y-1/2 hidden md:block ${styles.cardTextMuted}`}>▶</div>
          </div>

          {/* Step 3 */}
          <div className="p-3 bg-indigo-50/50 border border-indigo-200 rounded-xl space-y-1.5 relative">
            <div className={`text-[10px] font-mono font-bold ${styles.accentText}`}>3. 安全中心 (阻断防漏)</div>
            <h4 className="font-bold text-indigo-800 text-xs">安全规则网格</h4>
            <p className="text-[10px] text-indigo-600 font-sans">隔离网域 (Orgs), 密级锁标记, 列级 REDACT 掩码及 row-filter</p>
            <div className={`absolute top-1/2 -right-2 transform -translate-y-1/2 hidden md:block ${styles.cardTextMuted}`}>▶</div>
          </div>

          {/* Step 4 */}
          <div className={`p-3 ${styles.appBg} ${styles.cardTextMuted} rounded-xl space-y-1.5`}>
            <div className="text-[10px] font-mono font-bold text-blue-400">4. Agent 知识库</div>
            <h4 className="font-bold text-white text-xs">向量化 RAG 索引</h4>
            <p className={`text-[10px] ${styles.cardTextMuted} font-sans`}>统一向量切片，零幻觉生成方案，安全写回与审计，形成最终闭环</p>
          </div>

        </div>

        <div className="p-3 bg-blue-50 border border-blue-200/50 rounded-lg text-[11px] text-blue-700 leading-relaxed flex items-start gap-2">
          <Icon name="Info" size={14} className="shrink-0 mt-0.5" />
          <span><strong>{t('aiworkbench.knowledge.closedLoop.ctaTitle')}：</strong> 您可以点击左边侧边栏的<strong>【元数据集成同步】</strong>模拟一键调取三大工作台元数据，在<strong>【知识向量索引构建】</strong>中切片，最后在<strong>【知识检索与 RAG 模拟】</strong>中直接体验无幻觉的端到端对话效果。</span>
        </div>
      </div>

    </div>
  );
}