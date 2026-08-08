import React from 'react';
import { Bot, Workflow, Database, ShieldAlert, Cpu, Activity, Info } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

export default function ClosedLoopTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="space-y-6 max-w-5xl">
      <div className={`${styles.accentBg} text-white p-5 rounded-2xl flex flex-col justify-between gap-2 shadow-md relative overflow-hidden`}>
        <div className="absolute right-0 bottom-0 opacity-10 transform translate-x-10 translate-y-10 scale-150"><Bot size={240} /></div>
        <div className="space-y-1 z-10">
          <span className="px-2 py-0.5 bg-blue-500 text-white text-[9px] font-black rounded uppercase tracking-wider">
            {locale === 'zh' ? '双轨知识闭环' : 'DUAL-TRACK CLOSED LOOP'}
          </span>
          <h1 className="text-base font-black tracking-tight">
            {locale === 'zh' ? '知识闭环设计器 (AIP Pipeline Configurator)' : 'Knowledge Closed-Loop Designer'}
          </h1>
          <p className="text-xs text-slate-300 font-sans max-w-2xl leading-relaxed">
            {locale === 'zh'
              ? '轨道A（平台自用）→ 元数据同步 → 血缘解析 → 本体对齐；轨道B（智能体知识）→ 图谱同步 → 术语/分类 → 向量索引。两条轨道汇合于RAG检索闭环。'
              : 'Track A (Platform) → Sync → Lineage → Ontology; Track B (Agent) → Graph → Glossary/Classification → Index. Both converge at RAG retrieval.'}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {[
          { q: locale === 'zh' ? '为什么需要双轨知识闭环？' : 'Why dual-track closed loop?', a: locale === 'zh' ? '传统AI Copilot仅有通用常识，对业务上下文、数据时效、本体语义、合规性完全缺失。轨道A提供平台级数据底座，轨道B构建智能体可理解的知识语义层，二者融合才能消除幻觉、防止安全溢出。' : 'Traditional AI Copilots lack business context, data freshness, ontology semantics, and compliance. Track A provides the data foundation; Track B builds agent-consumable knowledge. Together they eliminate hallucinations.' },
          { q: locale === 'zh' ? '知识库如何自动装配？' : 'How is the knowledge base assembled?', a: locale === 'zh' ? '联邦多模元数据同步引擎从集成工作台拉取宽表结构与血缘，提取本体ObjectType结构及Action，叠加安全围栏与行列掩码。元数据统一由Embedding模型向量化切片，构建结构化向量知识库。' : 'The federated metadata sync engine pulls table schemas and lineage from the integration workspace, extracts ObjectType structures and Actions, and layers security fences. All metadata is chunked and vectorized via Embedding models.' },
          { q: locale === 'zh' ? '安全护栏的作用？' : 'Role of guardrails?', a: locale === 'zh' ? '闭环关键在于数据流的向外延展与阻断返回。当Agent利用知识库生成修改建议时，强制触发Ontology Action人工确认，该修改行为在安全中心生成审计记录，实现自适应进化闭环。' : 'The loop key is data extension and blocking return. When an agent proposes changes via the knowledge base, Ontology Action human confirmation is enforced, generating audit records in the security center.' },
          { q: locale === 'zh' ? 'Agent Sandbox如何完善？' : 'How does Agent Sandbox work?', a: locale === 'zh' ? 'AIP Workbench中建立一键同步与仿真沙箱，开发者可在发布智能体之前，通过提示词和真实元数据对齐进行干涉测试，确保PII遮蔽和SQL row-filter在LLM推理层提前拦截。' : 'AIP Workbench provides one-click sync and simulation sandbox. Developers can test prompts against real metadata before agent deployment, ensuring PII masking and row-filter enforcement at the LLM layer.' },
        ].map((item, i) => (
          <div key={i} className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs space-y-2">
            <h3 className="font-black text-slate-800 text-xs flex items-center gap-1.5 text-blue-600">
              {[<Workflow size={13} key="w"/>, <Database size={13} key="d"/>, <ShieldAlert size={13} key="s"/>, <Cpu size={13} key="c"/>][i]}
              <span>Q{i+1}: {item.q}</span>
            </h3>
            <p className="text-[11px] text-slate-500 leading-relaxed font-sans">{item.a}</p>
          </div>
        ))}
      </div>

      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
        <h3 className="text-xs font-extrabold text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
          <Activity size={12} className="text-blue-500" />
          <span>{locale === 'zh' ? '闭环拓扑流程' : 'Closed-Loop Topology'}</span>
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3 text-center">
          {[
            { label: 'STEP 1', title: locale === 'zh' ? '元数据同步' : 'Sync', desc: locale === 'zh' ? 'Doris宽表、血缘、数据监控' : 'Doris tables, lineage, monitoring', color: 'slate' },
            { label: 'STEP 2', title: locale === 'zh' ? '本体对齐' : 'Ontology', desc: locale === 'zh' ? 'ObjectTypes, Links, Action算子' : 'ObjectTypes, Links, Actions', color: 'blue' },
            { label: 'STEP 3', title: locale === 'zh' ? '安全护栏' : 'Security', desc: locale === 'zh' ? '隔离网域, 密级锁, REDACT掩码' : 'Isolation, classification, REDACT', color: 'indigo' },
            { label: 'STEP 4', title: locale === 'zh' ? 'RAG闭环' : 'RAG Loop', desc: locale === 'zh' ? '统一向量切片，零幻觉生成' : 'Unified vectors, zero-hallucination', color: 'dark' },
          ].map((step, i) => (
            <div key={i} className={`p-3 rounded-xl space-y-1.5 relative ${
              step.color === 'dark' ? 'bg-slate-900 text-slate-300' :
              step.color === 'blue' ? 'bg-blue-50/50 border border-blue-200' :
              step.color === 'indigo' ? 'bg-indigo-50/50 border border-indigo-200' :
              'bg-slate-50 border border-slate-200'
            }`}>
              <div className={`text-[10px] font-mono font-bold ${
                step.color === 'dark' ? 'text-blue-400' :
                step.color === 'blue' ? 'text-blue-500' :
                step.color === 'indigo' ? 'text-indigo-500' : 'text-slate-400'
              }`}>{step.label}</div>
              <h4 className={`font-bold text-xs ${
                step.color === 'dark' ? 'text-white' : 'text-slate-800'
              }`}>{step.title}</h4>
              <p className={`text-[10px] font-sans ${
                step.color === 'dark' ? 'text-slate-400' : 'text-slate-500'
              }`}>{step.desc}</p>
            </div>
          ))}
        </div>

        <div className="p-3 bg-blue-50 border border-blue-200/50 rounded-lg text-[11px] text-blue-700 leading-relaxed flex items-start gap-2">
          <Info size={14} className="shrink-0 mt-0.5" />
          <span>{locale === 'zh'
            ? '闭环行动倡议： 使用下方配置面板启动闭环管道，或在侧边栏切换到各子模块进行精细操作。'
            : 'Action: Use the config panel below to start the pipeline, or navigate to sub-modules via the sidebar.'}</span>
        </div>
      </div>
    </div>
  );
}
