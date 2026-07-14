import React, { useState, useEffect } from 'react';
import { Bot, Workflow, Database, ShieldAlert, Cpu, Activity, Info, Save, Play, RefreshCw } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import type { ClosedLoopConfig, KnowledgeAsset } from '../typesAndConstants';

const DEFAULT_CONFIG: ClosedLoopConfig = {
  id: 'cl-default',
  name: '知识闭环 AIP Pipeline',
  sources: [
    { track: 'platform', sourceType: 'metadata', enabled: true },
    { track: 'platform', sourceType: 'lineage', enabled: true },
    { track: 'platform', sourceType: 'ontology', enabled: true },
    { track: 'agent', sourceType: 'business_object', enabled: true },
  ],
  vectorModel: 'text-embedding-004',
  chunkSize: 512,
  overlap: 50,
  targetIndex: 'knowledge_chunks',
};

export default function ClosedLoopTab() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const [config, setConfig] = useState<ClosedLoopConfig>(DEFAULT_CONFIG);
  const [assets, setAssets] = useState<KnowledgeAsset[]>([]);
  const [isRunning, setIsRunning] = useState(false);
  const [logs, setLogs] = useState<string[]>([]);

  const addLog = (msg: string) => setLogs(prev => [...prev, msg]);

  const handleToggleSource = (idx: number) => {
    setConfig(prev => {
      const sources = [...prev.sources];
      sources[idx] = { ...sources[idx], enabled: !sources[idx].enabled };
      return { ...prev, sources };
    });
  };

  const handleRunPipeline = async () => {
    setIsRunning(true);
    setLogs([]);
    addLog(`[${new Date().toISOString()}] 闭环管道启动...`);
    try {
      await knowledgeApi.syncVectors({
        embeddingModel: config.vectorModel,
        chunkSize: config.chunkSize,
        overlap: config.overlap,
      });
      addLog(`[${new Date().toISOString()}] 向量同步任务已提交`);
      addLog(`[OK] 闭环管道触发成功`);
    } catch (e: any) {
      addLog(`[ERROR] ${e.message}`);
    } finally {
      setIsRunning(false);
    }
  };

  const handleSave = async () => {
    try {
      await knowledgeApi.updateSettings({
        defaultVectorModel: config.vectorModel,
        defaultChunkSize: config.chunkSize,
        defaultOverlap: config.overlap,
      });
      addLog(`[${new Date().toISOString()}] 配置已保存`);
    } catch {
      addLog(`[WARN] 保存配置失败，后端未响应`);
    }
  };

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

      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
        <h3 className="text-xs font-extrabold text-slate-800 flex items-center gap-1.5">
          <RefreshCw size={13} className="text-indigo-600" />
          <span>{locale === 'zh' ? '闭环管道配置与执行' : 'Pipeline Config & Execute'}</span>
        </h3>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {config.sources.map((src, idx) => (
            <button
              key={idx}
              onClick={() => handleToggleSource(idx)}
              className={`p-3 rounded-xl border text-left transition-all cursor-pointer ${
                src.enabled
                  ? 'bg-emerald-50 border-emerald-200 shadow-xs'
                  : 'bg-slate-50 border-slate-200 opacity-60'
              }`}
            >
              <div className="flex items-center gap-2 mb-1">
                <span className={`w-2 h-2 rounded-full ${src.enabled ? 'bg-emerald-500' : 'bg-slate-300'}`} />
                <span className="text-[9px] font-bold uppercase tracking-wider text-slate-400">
                  {src.track === 'platform' ? '轨道A' : '轨道B'}
                </span>
              </div>
              <div className="font-bold text-xs text-slate-800">{src.sourceType}</div>
              <div className="text-[9px] text-slate-500">
                {src.enabled ? (locale === 'zh' ? '已启用' : 'Enabled') : (locale === 'zh' ? '已禁用' : 'Disabled')}
              </div>
            </button>
          ))}
        </div>

        <div className="grid grid-cols-3 gap-3">
          <div className="space-y-1">
            <label className="block text-slate-600 font-bold text-[10px] uppercase">
              {locale === 'zh' ? '向量模型' : 'Vector Model'}
            </label>
            <select
              value={config.vectorModel}
              onChange={e => setConfig(prev => ({ ...prev, vectorModel: e.target.value }))}
              className="w-full px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-mono bg-white text-slate-800"
            >
              <option value="text-embedding-004">Gemini Embedding (256)</option>
              <option value="bge-large-zh-v1.5">BGE 中文大模型 (1024)</option>
              <option value="text-embedding-3-small">OpenAI Small (1536)</option>
            </select>
          </div>
          <div className="space-y-1">
            <label className="block text-slate-600 font-bold text-[10px] uppercase">
              {locale === 'zh' ? '切块大小' : 'Chunk Size'}
            </label>
            <select
              value={config.chunkSize}
              onChange={e => setConfig(prev => ({ ...prev, chunkSize: Number(e.target.value) }))}
              className="w-full px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-mono bg-white text-slate-800"
            >
              {[256, 512, 1024, 2048].map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          </div>
          <div className="space-y-1">
            <label className="block text-slate-600 font-bold text-[10px] uppercase">
              {locale === 'zh' ? '重叠度' : 'Overlap'}
            </label>
            <select
              value={config.overlap}
              onChange={e => setConfig(prev => ({ ...prev, overlap: Number(e.target.value) }))}
              className="w-full px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-mono bg-white text-slate-800"
            >
              {[25, 50, 100, 150].map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleRunPipeline}
            disabled={isRunning}
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs transition-all disabled:opacity-60"
          >
            {isRunning
              ? <><span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" /><span>{locale === 'zh' ? '管道执行中...' : 'Running...'}</span></>
              : <><Play size={12} /><span>{locale === 'zh' ? '执行闭环管道' : 'Run Pipeline'}</span></>
            }
          </button>
          <button
            onClick={handleSave}
            className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs transition-all"
          >
            <Save size={12} />
            <span>{locale === 'zh' ? '保存配置' : 'Save Config'}</span>
          </button>
        </div>

        {logs.length > 0 && (
          <div className="bg-slate-900 text-slate-300 rounded-xl p-3 h-32 overflow-y-auto font-mono text-[9px] leading-relaxed border border-slate-800">
            {logs.map((log, i) => <div key={i} className={log.includes('[OK]') ? 'text-emerald-400 font-bold' : log.includes('[ERROR]') ? 'text-rose-400 font-bold' : ''}>{log}</div>)}
          </div>
        )}
      </div>
    </div>
  );
}
