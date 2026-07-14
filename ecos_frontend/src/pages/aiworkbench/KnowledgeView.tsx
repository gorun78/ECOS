/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import * as Icons from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface KnowledgeViewProps {
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

interface MetadataAsset {
  id: string;
  source: 'integration' | 'ontology' | 'security';
  name: string;
  type: string;
  recordsOrFields: string;
  syncStatus: 'synced' | 'pending' | 'out_of_date';
  chunksCount: number;
  lastSynced: string;
}

export default function KnowledgeView({ showToast }: KnowledgeViewProps) {
  const { styles } = useTheme();
  const [activeSubTab, setActiveSubTab] = useState<'architecture' | 'sync' | 'lineage' | 'ontology' | 'index' | 'rag'>('architecture');

  // Ontology Alignment Manager States
  const [ontologyMappings, setOntologyMappings] = useState<any[]>([]);
  const [availableTables, setAvailableTables] = useState<any[]>([]);
  const [isOntologyLoading, setIsOntologyLoading] = useState(false);
  const [editingOntology, setEditingOntology] = useState<any | null>(null);
  const [isExporting, setIsExporting] = useState(false);
  const [exportedMarkdown, setExportedMarkdown] = useState('');
  const [showExportModal, setShowExportModal] = useState(false);

  const fetchOntologyData = async () => {
    setIsOntologyLoading(true);
    try {
      const res = await fetch('/api/ontology/mappings');
      const data = await res.json();
      if (data.success) {
        setOntologyMappings(data.mappings || []);
        setAvailableTables(data.availableTables || []);
      }
    } catch (e) {
      console.error('Failed to fetch ontology mappings', e);
    } finally {
      setIsOntologyLoading(false);
    }
  };

  const handleSaveOntologyMappings = async (updatedMappings: any[]) => {
    try {
      const res = await fetch('/api/ontology/mappings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mappings: updatedMappings })
      });
      const data = await res.json();
      if (data.success) {
        showToast?.('success', '本体对齐规则与多对多物理映射已成功保存并下发！');
        setOntologyMappings(data.mappings);
        await fetchMetadataAndLogs();
      } else {
        showToast?.('error', data.error || '保存映射失败');
      }
    } catch (e: any) {
      showToast?.('error', '保存失败: ' + e.message);
    }
  };

  const handleExportOntology = async () => {
    setIsExporting(true);
    try {
      const res = await fetch('/api/ontology/export');
      const data = await res.json();
      if (data.success) {
        setExportedMarkdown(data.knowledgeMarkdown);
        setShowExportModal(true);
        showToast?.('success', '本体元数据 RAG 知识包成功导出！');
      }
    } catch (e: any) {
      showToast?.('error', '导出失败: ' + e.message);
    } finally {
      setIsExporting(false);
    }
  };

  // Lineage & Downstream Impact States
  const [lineageNodes, setLineageNodes] = useState<any[]>([]);
  const [lineageLinks, setLineageLinks] = useState<any[]>([]);
  const [selectedStartNode, setSelectedStartNode] = useState('postgresql_raw_sched.flights_raw');
  const [impactResult, setImpactResult] = useState<any>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [isParsing, setIsParsing] = useState(false);
  const [parserFormatSelected, setParserFormatSelected] = useState<'openlineage' | 'atlas'>('openlineage');
  const [rawPayloadInput, setRawPayloadInput] = useState(`{
  "eventType": "COMPLETE",
  "eventTime": "2026-07-04T12:00:00Z",
  "producer": "https://github.com/OpenLineage/OpenLineage",
  "job": {
    "namespace": "ds_scheduler",
    "name": "spark_clean_flight_acars_job"
  },
  "inputs": [
    {
      "namespace": "postgresql_raw_sched",
      "name": "flights_raw"
    }
  ],
  "outputs": [
    {
      "namespace": "doris_production_olap",
      "name": "ds_flights_clean"
    }
  ]
}`);

  // Simulation parameters managed in server-side state
  const [isSchemaDrift, setIsSchemaDrift] = useState(false);
  const [isSlaBreach, setIsSlaBreach] = useState(false);
  const [auditLogs, setAuditLogs] = useState<any[]>([]);

  // Metadata Assets List
  const [assets, setAssets] = useState<MetadataAsset[]>([
    // Integration Workspace Metadata
    { id: 'int-1', source: 'integration', name: 'ds_flights_clean (航班运行大宽表)', type: 'Doris 物理表', recordsOrFields: '14,250条记录 / 8列', syncStatus: 'synced', chunksCount: 45, lastSynced: '2026-07-04 10:20' },
    { id: 'int-2', source: 'integration', name: 'ds_pilots_biography (机组档案大宽表)', type: 'Doris 物理表', recordsOrFields: '380条记录 / 6列', syncStatus: 'synced', chunksCount: 12, lastSynced: '2026-07-04 10:20' },
    { id: 'int-3', source: 'integration', name: 'flights_raw (签派流水 Bronze 表)', type: 'PostgreSQL 物理表', recordsOrFields: '240,000条记录 / 3列', syncStatus: 'pending', chunksCount: 0, lastSynced: '未同步' },
    
    // Ontology Workspace Metadata
    { id: 'ont-1', source: 'ontology', name: 'AviationFlight (航班对象主体)', type: 'Ontology ObjectType', recordsOrFields: '属性: 14个 / 动作: 2个', syncStatus: 'synced', chunksCount: 18, lastSynced: '2026-07-04 11:15' },
    { id: 'ont-2', source: 'ontology', name: 'AviationPilot (飞行员对象主体)', type: 'Ontology ObjectType', recordsOrFields: '属性: 9个 / 动作: 1个', syncStatus: 'synced', chunksCount: 12, lastSynced: '2026-07-04 11:15' },
    { id: 'ont-3', source: 'ontology', name: 'act_reschedule_flight (重新指派班机算子)', type: 'Ontology Action', recordsOrFields: '参数: 4个 / 鉴权: 签派总监', syncStatus: 'synced', chunksCount: 8, lastSynced: '2026-07-04 11:16' },

    // Security Center Metadata
    { id: 'sec-1', source: 'security', name: 'Org_EU_Ops (欧盟运营专属物理隔离)', type: '组织安全性规范', recordsOrFields: 'IP Whitelist / 跨域限制', syncStatus: 'synced', chunksCount: 15, lastSynced: '2026-07-04 10:40' },
    { id: 'sec-2', source: 'security', name: 'proj_passenger_eu (GDPR敏感项目ACL)', type: '项目级访问控制', recordsOrFields: '成员: 2 / 继承传递启用', syncStatus: 'synced', chunksCount: 10, lastSynced: '2026-07-04 10:50' },
    { id: 'sec-3', source: 'security', name: 'ssn_number (列级最高掩蔽REDACT策略)', type: '动态脱敏安全策略', recordsOrFields: '敏感字段 / 列级脱敏', syncStatus: 'pending', chunksCount: 0, lastSynced: '未同步' }
  ]);

  // Embedding Configuration States
  const [embeddingModel, setEmbeddingModel] = useState('text-embedding-004');
  const [chunkSize, setChunkSize] = useState(512);
  const [overlap, setOverlap] = useState(50);
  const [isSyncingAll, setIsSyncingAll] = useState(false);
  const [syncLogs, setSyncLogs] = useState<string[]>([]);
  const [vectorChunks, setVectorChunks] = useState<any[]>([]);
  const [pgvectorSql, setPgvectorSql] = useState('');
  const [milvusCode, setMilvusCode] = useState('');
  const [persistenceTab, setPersistenceTab] = useState<'pgvector' | 'milvus'>('pgvector');

  // Fetch current vector index status
  const fetchIndexStatus = async () => {
    try {
      const res = await fetch('/api/knowledge/index-status');
      const data = await res.json();
      if (data.success) {
        setVectorChunks(data.chunks || []);
        setPgvectorSql(data.pgvectorSql || '');
        setMilvusCode(data.milvusCode || '');
        if (data.logs && data.logs.length > 0) {
          setSyncLogs(data.logs);
        }
      }
    } catch (e) {
      console.warn('Failed to fetch vector status:', e);
    }
  };

  useEffect(() => {
    if (activeSubTab === 'index') {
      fetchIndexStatus();
    }
  }, [activeSubTab]);

  // RAG Search simulation States
  const [queryInput, setQueryInput] = useState('查询欧盟GDPR合规隔离下，飞行员张建国的执勤及SSN脱敏状态');
  const [isRetrieving, setIsRetrieving] = useState(false);
  const [retrievedDocs, setRetrievedDocs] = useState<Array<{ title: string; type: string; snippet: string; score: number }>>([]);
  const [ragPrompt, setRagPrompt] = useState<string>('');
  const [llmOutput, setLlmOutput] = useState<string>('');

  // Fetch initial metadata and states from Express backend
  const fetchMetadataAndLogs = async () => {
    try {
      const resMeta = await fetch('/api/integration/metadata');
      const dataMeta = await resMeta.json();
      setIsSchemaDrift(dataMeta.simulationState.isSchemaDriftActive);
      setIsSlaBreach(dataMeta.simulationState.isSlaBreachActive);
      
      if (dataMeta.lineage) {
        setLineageNodes(dataMeta.lineage.nodes || []);
        setLineageLinks(dataMeta.lineage.links || []);
      }

      const resLogs = await fetch('/api/integration/logs');
      const dataLogs = await resLogs.json();
      setAuditLogs(dataLogs.logs || []);

      // If schema drift is active, adjust int-2 syncStatus
      setAssets(prev => prev.map(a => {
        if (a.id === 'int-2') {
          return { ...a, syncStatus: dataMeta.simulationState.isSchemaDriftActive ? 'out_of_date' : 'synced' };
        }
        return a;
      }));
    } catch (e) {
      console.error('Failed to connect to backend metadata APIs', e);
    }
  };

  const handleParseLineage = async () => {
    setIsParsing(true);
    try {
      let parsedObj;
      try {
        parsedObj = JSON.parse(rawPayloadInput);
      } catch (err) {
        showToast?.('error', 'Payload JSON 格式错误，请检查输入！');
        setIsParsing(false);
        return;
      }

      const res = await fetch('/api/lineage/parse', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ payload: parsedObj })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        showToast?.('success', data.message);
        setLineageNodes(data.lineage.nodes);
        setLineageLinks(data.lineage.links);
        await fetchMetadataAndLogs();
      } else {
        showToast?.('error', data.error || '解析血缘失败');
      }
    } catch (err: any) {
      showToast?.('error', '解析异常: ' + err.message);
    } finally {
      setIsParsing(false);
    }
  };

  const handleRunImpactAnalysis = async (startNodeId: string) => {
    setIsAnalyzing(true);
    try {
      const res = await fetch(`/api/lineage/impact?startNode=${encodeURIComponent(startNodeId)}`);
      const data = await res.json();
      if (res.ok && data.success) {
        setImpactResult(data);
      } else {
        showToast?.('error', data.error || '分析影响度失败');
      }
    } catch (err: any) {
      showToast?.('error', '分析异常: ' + err.message);
    } finally {
      setIsAnalyzing(false);
    }
  };

  useEffect(() => {
    fetchMetadataAndLogs();
    fetchOntologyData();
  }, []);

  // Recalculate impact analysis automatically when table selection or simulation status changes
  useEffect(() => {
    if (selectedStartNode) {
      handleRunImpactAnalysis(selectedStartNode);
    }
  }, [selectedStartNode, isSchemaDrift, isSlaBreach, lineageNodes]);

  // Handle setting Simulation state
  const handleToggleSimulation = async (type: 'drift' | 'sla' | 'reset') => {
    try {
      const res = await fetch('/api/integration/metadata/drift', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type })
      });
      const data = await res.json();
      if (data.success) {
        showToast?.('success', data.message);
        await fetchMetadataAndLogs();
      }
    } catch (e) {
      showToast?.('error', '推送异常模拟事件失败');
    }
  };

  // Handle single asset sync
  const handleSyncAsset = (id: string) => {
    setAssets(prev => prev.map(a => {
      if (a.id === id) {
        return {
          ...a,
          syncStatus: 'synced',
          chunksCount: a.chunksCount === 0 ? Math.floor(Math.random() * 20) + 10 : a.chunksCount,
          lastSynced: new Date().toISOString().replace('T', ' ').substring(0, 16)
        };
      }
      return a;
    }));
    showToast?.('success', '该表或策略元数据已被重新拉取并成功序列化！');
  };

  // Handle entire pipeline sync
  const handleSyncAll = async () => {
    setIsSyncingAll(true);
    setSyncLogs(['🔄 [0.0s] 启动 AIP Closed-Loop 元数据提取与向量化索引计算管道...']);

    try {
      const res = await fetch('/api/knowledge/sync', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ embeddingModel, chunkSize, overlap })
      });
      const data = await res.json();
      if (data.success) {
        setSyncLogs([]);
        for (let i = 0; i < data.logs.length; i++) {
          await new Promise(resolve => setTimeout(resolve, 150));
          setSyncLogs(prev => [...prev, data.logs[i]]);
        }
        setVectorChunks(data.chunks || []);
        setPgvectorSql(data.pgvectorSql || '');
        setMilvusCode(data.milvusCode || '');
        showToast?.('success', '元数据一键向量对齐与持久化代码生成成功！');
      } else {
        showToast?.('error', data.error || '索引同步失败');
      }
    } catch (e: any) {
      showToast?.('error', '元数据同步异常: ' + e.message);
    } finally {
      setIsSyncingAll(false);
      await fetchMetadataAndLogs();
    }
  };

  // Run RAG Semantic Retrieve & Prompt Engineering Simulation with the Express Backend
  const handleRunRAG = async () => {
    if (!queryInput.trim()) return;
    setIsRetrieving(true);
    setRetrievedDocs([]);
    setRagPrompt('');
    setLlmOutput('');

    try {
      const response = await fetch('/api/knowledge/query', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query: queryInput })
      });
      const data = await response.json();
      
      if (data.success) {
        setRetrievedDocs(data.groundedDocs.map((doc: any) => ({
          title: doc.title,
          type: doc.title.includes('Security') ? '安全元数据' : doc.title.includes('Ontology') ? '本体元数据' : '集成元数据',
          snippet: doc.snippet || 'N/A',
          score: doc.score
        })));
        setRagPrompt(data.promptGrounded);
        setLlmOutput(data.answer);
      } else {
        showToast?.('error', '元数据知识召回失败');
      }
    } catch (e) {
      showToast?.('error', '与大模型交互失败，请检查网络');
    } finally {
      setIsRetrieving(false);
    }
  };

  return (
    <div className="flex h-full select-none text-xs overflow-hidden">
      
      {/* Left side sub-nav */}
      <div className={`w-48 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col shrink-0 h-full p-2.5 space-y-1`}>
        <div className={`px-2 py-1.5 text-[10px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider`}>
          闭环功能分区
        </div>
        {[
          { id: 'architecture', label: '研究方案 / 闭环设计', icon: 'FileText' },
          { id: 'sync', label: '元数据集成同步', icon: 'Combine' },
          { id: 'lineage', label: '血缘解析与级联影响', icon: 'Network' },
          { id: 'ontology', label: '语义本体对齐映射', icon: 'Workflow' },
          { id: 'index', label: '知识向量索引构建', icon: 'Cpu' },
          { id: 'rag', label: '知识检索与 RAG 模拟', icon: 'SearchCheck' }
        ].map(item => {
          const isActive = activeSubTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setActiveSubTab(item.id as any)}
              className={`w-full px-3 py-2 rounded-lg font-bold text-left flex items-center gap-2 transition-all cursor-pointer ${
                isActive
                  ? `${styles.accentBg} text-white shadow-xs`
                  : 'text-slate-600 hover:bg-slate-50'
              }`}
            >
              <Icon name={item.icon} size={13} />
              <span>{item.label}</span>
            </button>
          );
        })}

        <div className={`mt-auto p-2.5 ${styles.inputBg} rounded-xl border ${styles.cardBorder} space-y-2`}>
          <p className={`font-extrabold text-[10px] ${styles.cardText} flex items-center gap-1.5`}>
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
            <span>完美闭环就绪</span>
          </p>
          <p className={`text-[9px] ${styles.cardTextMuted} leading-relaxed font-sans`}>
            集成、本体、安全中心的数据经由此处融合计算，向 Copilot 提供高精度知识驱动。
          </p>
        </div>
      </div>

      {/* Main Panel */}
      <div className={`flex-1 ${styles.appBg} p-6 overflow-y-auto h-full`}>
        
        {/* TAB 1: RESEARCH REPORT & ARCHITECTURE DESIGN */}
        {activeSubTab === 'architecture' && (
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
                  本方案设计了数据全景元数据向 Agent 智能体输送、形成高精度向量知识库 (Vector Knowledge Base)，进而生成动作建议，最终经过安全过滤与审计写回物理/本体存储的“完美闭环”。
                </p>
              </div>
            </div>

            {/* Core Q&A Section */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              
              <div className={`${styles.cardBg} border ${styles.cardBorder} p-4 rounded-xl shadow-xs space-y-2`}>
                <h3 className={`font-black ${styles.cardText} text-xs flex items-center gap-1.5 text-blue-600`}>
                  <Icon name="Workflow" size={13} />
                  <span>Q1: 为什么需要这个联邦知识闭环？</span>
                </h3>
                <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed font-sans`}>
                  传统的 AI Copilot 仅具备大模型的通用常识，对<strong>航空调度场景中的业务上下文、物理数据时效、本体层级语义（Doris物理库到Flight实体的关系）以及严密的 GDPR/审计合规性完全感知缺失</strong>。通过融合三大工作台的数据与元数据，才能为 Copilot 注入“上帝视角”的数据语义及安全底网，消除生成幻觉，防止安全溢出。
                </p>
              </div>

              <div className={`${styles.cardBg} border ${styles.cardBorder} p-4 rounded-xl shadow-xs space-y-2`}>
                <h3 className={`font-black ${styles.cardText} text-xs flex items-center gap-1.5 text-indigo-600`}>
                  <Icon name="Database" size={13} />
                  <span>Q2: 知识库如何构建与自动装配？</span>
                </h3>
                <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed font-sans`}>
                  本平台采用<strong>联邦多模元数据同步引擎</strong>。首先从集成工作台拉取宽表结构与血缘；其次提取本体 ObjectType 结构及提权动作(Actions)；最后叠加安全中心的安全围栏与行列掩码。元数据统一由 <strong>Embedding 模型向量化</strong>切片，构建成高度结构化的<strong>向量特征图谱知识库</strong>，直接对齐至 Agent Copilot，随用随检索(RAG)。
                </p>
              </div>

              <div className={`${styles.cardBg} border ${styles.cardBorder} p-4 rounded-xl shadow-xs space-y-2`}>
                <h3 className={`font-black ${styles.cardText} text-xs flex items-center gap-1.5 text-rose-600`}>
                  <Icon name="ShieldAlert" size={13} />
                  <span>Q3: 完美闭环中安全护栏的作用？</span>
                </h3>
                <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed font-sans`}>
                  <strong>闭环的关键在于数据流的向外延展与阻断返回。</strong>当 Agent 利用知识库向用户生成修改建议（如UA102航班延误120分钟）时，会强制触发 Ontology Action 人工确认(Guardrails)；当用户确认授权写回本体后，该修改行为会直接在安全中心生成一条高敏 Audit 审计记录，重新成为系统诊断的元数据，实现“自适应进化闭环”。
                </p>
              </div>

              <div className={`${styles.cardBg} border ${styles.cardBorder} p-4 rounded-xl shadow-xs space-y-2 flex flex-col justify-between`}>
                <h3 className={`font-black ${styles.cardText} text-xs flex items-center gap-1.5 text-emerald-600`}>
                  <Icon name="Cpu" size={13} />
                  <span>Q4: 智能体工作台(Agent Sandbox)如何完善？</span>
                </h3>
                <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed font-sans`}>
                  通过在 AIP Workbench 中建立<strong>一键同步与仿真沙箱(Sandbox Simulation)</strong>，开发者可以在设计、发布智能体之前，直接通过提示词和真实元数据对齐进行“干涉测试”，确保所有的 PII 遮蔽和 SQL row-filter 在 LLM 推理层就能提前拦截生效。
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
                  <div className="text-[10px] font-mono font-bold text-indigo-500">3. 安全中心 (阻断防漏)</div>
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
                <span><strong>闭环行动倡议：</strong> 您可以点击左边侧边栏的<strong>【元数据集成同步】</strong>模拟一键调取三大工作台元数据，在<strong>【知识向量索引构建】</strong>中切片，最后在<strong>【知识检索与 RAG 模拟】</strong>中直接体验无幻觉的端到端对话效果。</span>
              </div>
            </div>

          </div>
        )}

        {/* TAB 2: METADATA FEDERATION SYNC */}
        {activeSubTab === 'sync' && (
          <div className="space-y-6">
            
            {/* Title */}
            <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
              <div className="space-y-1">
                <h2 className={`text-sm font-black ${styles.cardText}`}>多模态联邦元数据集成中心 (Metadata Sync)</h2>
                <p className={`text-xs ${styles.cardTextMuted}`}>动态监控、抓取和转换物理数据集、逻辑本体语义以及最高安全规则定义至本地缓存中，等待切块向量化。</p>
              </div>

              <button
                onClick={handleSyncAll}
                disabled={isSyncingAll}
                className={`px-4 py-2 ${styles.cardBg} hover:bg-slate-800 text-white font-bold rounded-lg transition-all flex items-center gap-1.5 shadow-sm cursor-pointer ${
                  isSyncingAll ? 'opacity-70 cursor-not-allowed' : ''
                }`}
              >
                {isSyncingAll ? (
                  <>
                    <span className={`w-3.5 h-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                    <span>联邦网格抓取中...</span>
                  </>
                ) : (
                  <>
                    <Icon name="RefreshCw" size={12} />
                    <span>一键全站元数据同步 (Federated Sync)</span>
                  </>
                )}
              </button>
            </div>

            {/* Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              
              {/* Sync list */}
              <div className="lg:col-span-2 space-y-4">
                
                {/* Real-time Exception & Drift Simulation Center */}
                <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-4`}>
                  <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
                    <h3 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5 text-rose-600`}>
                      <Icon name="ShieldAlert" size={13} />
                      <span>物理元数据漂移与调度 SLA 阻流仿真中心 (Exception Lab)</span>
                    </h3>
                    <div className="flex gap-1.5">
                      <span className={`px-2 py-0.5 rounded-full text-[9px] font-extrabold border ${isSchemaDrift || isSlaBreach ? 'bg-rose-50 border-rose-200 text-rose-600 animate-pulse' : 'bg-slate-50 border-slate-200 text-slate-500'}`}>
                        {isSchemaDrift || isSlaBreach ? '● 存在激活异常' : '● 网格运行稳定'}
                      </span>
                    </div>
                  </div>

                  <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed font-sans`}>
                    在此可以直接热注入物理 Doris 库 Schema 漂移变动或 Airflow/DolphinScheduler SLA 调度断流事件。系统会触发异常捕获并推送到审计监控中心，实时重新组装 Grounding 检索知识库！
                  </p>

                  <div className="flex flex-wrap gap-2">
                    <button
                      onClick={() => handleToggleSimulation('drift')}
                      className={`px-3 py-1.5 rounded-lg text-[10px] font-bold cursor-pointer transition-all flex items-center gap-1.5 border ${
                        isSchemaDrift 
                          ? 'bg-rose-50 border-rose-300 text-rose-700 font-extrabold' 
                          : 'bg-white border-slate-200 text-slate-700 hover:bg-slate-50'
                      }`}
                    >
                      <Icon name="AlertTriangle" size={11} className={isSchemaDrift ? 'animate-bounce' : ''} />
                      <span>注入 Doris Schema 物理漂移</span>
                    </button>

                    <button
                      onClick={() => handleToggleSimulation('sla')}
                      className={`px-3 py-1.5 rounded-lg text-[10px] font-bold cursor-pointer transition-all flex items-center gap-1.5 border ${
                        isSlaBreach 
                          ? 'bg-amber-50 border-amber-300 text-amber-700 font-extrabold' 
                          : 'bg-white border-slate-200 text-slate-700 hover:bg-slate-50'
                      }`}
                    >
                      <Icon name="Clock" size={11} className={isSlaBreach ? 'animate-pulse' : ''} />
                      <span>注入 DolphinScheduler SLA 时效断流</span>
                    </button>

                    <button
                      onClick={() => handleToggleSimulation('reset')}
                      className={`px-3 py-1.5 rounded-lg text-[10px] font-bold ${styles.appBg} hover:bg-slate-200 ${styles.cardTextMuted} cursor-pointer transition-all flex items-center gap-1 border border-slate-300 ml-auto`}
                    >
                      <Icon name="Check" size={11} />
                      <span>复位大盘状态 (Reset)</span>
                    </button>
                  </div>
                </div>

                {/* Real-time Audit Logs fetched from backend */}
                <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-2.5`}>
                  <h4 className={`font-extrabold ${styles.cardText} text-[11px] flex items-center gap-1 ${styles.cardTextMuted} border-b ${styles.cardBorder} pb-2`}>
                    <Icon name="FileClock" size={12} />
                    <span>安全与血缘监控异常审计热推送记录 ({auditLogs.length})</span>
                  </h4>
                  
                  <div className="space-y-1.5 max-h-36 overflow-y-auto font-mono text-[9px]">
                    {auditLogs.length === 0 ? (
                      <p className={`${styles.cardTextMuted} py-4 text-center`}>暂无高危异常审计事件</p>
                    ) : (
                      auditLogs.map((log: any, i: number) => (
                        <div key={i} className={`p-2 rounded-lg ${styles.inputBg} border ${styles.cardBorder} flex items-start justify-between gap-4`}>
                          <div className="space-y-1">
                            <div className="flex items-center gap-2">
                              <span className={`px-1.5 py-0.2 rounded-xs font-bold text-[8px] uppercase ${
                                log.severity === 'HIGH' ? 'bg-rose-100 text-rose-700' :
                                log.severity === 'MEDIUM' ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-700'
                              }`}>
                                {log.severity}
                              </span>
                              <span className={`${styles.cardText} font-bold`}>{log.event}</span>
                            </div>
                            <p className={`${styles.cardTextMuted} font-sans leading-relaxed`}>{log.details}</p>
                          </div>
                          <span className={`${styles.cardTextMuted} shrink-0 text-[8px]`}>{log.timestamp}</span>
                        </div>
                      ))
                    )}
                  </div>
                </div>

                <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider pt-2`}>
                  三大工作台元数据同步列表 ({assets.length})
                </h3>

                <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden shadow-xs`}>
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className={`${styles.inputBg} ${styles.cardTextMuted} text-[10px] font-extrabold uppercase tracking-wider border-b ${styles.cardBorder}`}>
                        <th className="p-3">资产名称</th>
                        <th className="p-3">资产来源</th>
                        <th className="p-3">元数据类型</th>
                        <th className="p-3">物理数据体量/要素</th>
                        <th className="p-3">切块数</th>
                        <th className="p-3 text-right">状态 / 操作</th>
                      </tr>
                    </thead>
                    <tbody className={`divide-y ${styles.cardBorder}`}>
                      {assets.map(asset => {
                        return (
                          <tr key={asset.id} className={`hover:${styles.appBg} transition-colors`}>
                            <td className={`p-3 font-bold ${styles.cardText}`}>
                              {asset.name}
                            </td>
                            <td className="p-3">
                              <span className={`px-2 py-0.5 rounded-full font-bold text-[9px] ${
                                asset.source === 'integration' ? 'bg-amber-50 text-amber-600 border border-amber-200' :
                                asset.source === 'ontology' ? 'bg-blue-50 text-blue-600 border border-blue-200' :
                                'bg-rose-50 text-rose-600 border border-rose-200'
                              }`}>
                                {asset.source === 'integration' ? '集成工作台' :
                                 asset.source === 'ontology' ? '本体工作台' :
                                 '安全中心'}
                              </span>
                            </td>
                            <td className={`p-3 ${styles.cardTextMuted} font-medium`}>
                              {asset.type}
                            </td>
                            <td className={`p-3 ${styles.cardTextMuted} font-mono text-[10px]`}>
                              {asset.recordsOrFields}
                            </td>
                            <td className={`p-3 ${styles.cardTextMuted} font-mono font-bold`}>
                              {asset.chunksCount > 0 ? `${asset.chunksCount} chunks` : '-'}
                            </td>
                            <td className="p-3 text-right">
                              {asset.syncStatus === 'synced' ? (
                                <div className="flex items-center justify-end gap-1.5">
                                  <span className="text-emerald-600 font-bold text-[10px] flex items-center gap-1">
                                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                                    <span>最新已对齐</span>
                                  </span>
                                  <button
                                    onClick={() => handleSyncAsset(asset.id)}
                                    className={`p-1 ${styles.cardTextMuted} hover:${styles.cardTextMuted} cursor-pointer`}
                                    title="重新同步"
                                  >
                                    <Icon name="RotateCw" size={10} />
                                  </button>
                                </div>
                              ) : asset.syncStatus === 'out_of_date' ? (
                                <button
                                  onClick={() => handleSyncAsset(asset.id)}
                                  className="px-2 py-1 bg-amber-500 hover:bg-amber-600 text-white font-bold rounded-md transition-colors cursor-pointer text-[9px] flex items-center gap-1 ml-auto"
                                >
                                  <Icon name="AlertCircle" size={9} />
                                  <span>更迭更新</span>
                                </button>
                              ) : (
                                <button
                                  onClick={() => handleSyncAsset(asset.id)}
                                  className="px-2 py-1 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-md transition-colors cursor-pointer text-[9px] flex items-center gap-1 ml-auto"
                                >
                                  <Icon name="Download" size={9} />
                                  <span>拉取同步</span>
                                </button>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Sync Real-time Console */}
              <div className={`${styles.appBg} rounded-xl p-4 flex flex-col h-[400px] shadow-md border ${styles.cardBorder} ${styles.cardTextMuted}`}>
                <div className={`border-b ${styles.cardBorder} pb-2.5 mb-3 flex items-center justify-between`}>
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-ping" />
                    <span className="font-mono text-white text-[10px] font-bold">Closed-Loop Listener Pipeline</span>
                  </div>
                  <span className={`text-[8px] ${styles.cardTextMuted} font-mono`}>STATUS: STABLE</span>
                </div>

                <div className="flex-1 overflow-y-auto space-y-2.5 font-mono text-[9px] leading-relaxed scrollbar-thin scrollbar-thumb-slate-800">
                  {syncLogs.length === 0 ? (
                    <div className={`h-full flex flex-col items-center justify-center ${styles.cardTextMuted} text-center space-y-1.5`}>
                      <Icon name="Terminal" size={24} className={`${styles.cardTextMuted}`} />
                      <p>等待联邦同步事件触发...</p>
                      <p className={`text-[8px] ${styles.cardTextMuted}`}>点击“一键全站元数据同步”捕获动态流</p>
                    </div>
                  ) : (
                    syncLogs.map((log, idx) => (
                      <p key={idx} className={`${log.includes('✅') ? 'text-emerald-400 font-bold' : log.includes('🤖') ? 'text-blue-400 font-bold' : 'text-slate-300'}`}>
                        {log}
                      </p>
                    ))
                  )}
                </div>

                <div className={`border-t ${styles.cardBorder} pt-3 mt-3 text-[9px] ${styles.cardTextMuted} font-mono flex justify-between items-center`}>
                  <span>Doris CB Optimizer Sync Grid</span>
                  <span>v2.0-Sovereign</span>
                </div>
              </div>

            </div>

          </div>
        )}

        {/* TAB 2.5: LINEAGE PARSER & IMPACT ANALYSIS */}
        {activeSubTab === 'lineage' && (
          <div className="space-y-6">
            <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
              <div className="space-y-1">
                <h2 className={`text-sm font-black ${styles.cardText}`}>元数据血缘解析器与下游影响分析 (Lineage Parser & Impact Lab)</h2>
                <p className={`text-xs ${styles.cardTextMuted}`}>
                  支持实时解析 OpenLineage 及 Apache Atlas 统一血缘，智能提取「数据源 ➔ 原始表 ➔ 宽表 ➔ 本体逻辑对象 ➔ 终端报表」的拓扑 DAG，并基于级联风险算法深度评估物理表故障或漂移对下游的影响。
                </p>
              </div>
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
              
              {/* Left Column: Parser Input */}
              <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs flex flex-col space-y-4`}>
                <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2.5`}>
                  <h3 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5`}>
                    <Icon name="Code" size={13} className="text-indigo-600" />
                    <span>血缘元数据注入解析器 (Lineage Parser)</span>
                  </h3>
                  <div className={`flex ${styles.appBg} p-0.5 rounded-lg border ${styles.cardBorder} text-[10px]`}>
                    <button
                      onClick={() => {
                        setParserFormatSelected('openlineage');
                        setRawPayloadInput(JSON.stringify({
                          "eventType": "COMPLETE",
                          "eventTime": "2026-07-04T12:00:00Z",
                          "producer": "https://github.com/OpenLineage/OpenLineage",
                          "job": { "namespace": "ds_scheduler", "name": "spark_clean_flight_acars_job" },
                          "inputs": [{ "namespace": "postgresql_raw_sched", "name": "flights_raw" }],
                          "outputs": [{ "namespace": "doris_production_olap", "name": "ds_flights_clean" }]
                        }, null, 2));
                      }}
                      className={`px-2.5 py-1 rounded-md font-bold transition-all cursor-pointer ${parserFormatSelected === 'openlineage' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500 hover:text-slate-900'}`}
                    >
                      OpenLineage
                    </button>
                    <button
                      onClick={() => {
                        setParserFormatSelected('atlas');
                        setRawPayloadInput(JSON.stringify([
                          {
                            "typeName": "spark_process",
                            "attributes": {
                              "name": "spark_process_pilots_biography_sync",
                              "qualifiedName": "spark_process_pilots_biography_sync@cluster",
                              "inputs": [{ "typeName": "hive_table", "uniqueAttributes": { "qualifiedName": "postgresql_raw_sched.pilots_raw@cluster" } }],
                              "outputs": [{ "typeName": "hive_table", "uniqueAttributes": { "qualifiedName": "doris_production_olap.ds_pilots_biography@cluster" } }]
                            }
                          }
                        ], null, 2));
                      }}
                      className={`px-2.5 py-1 rounded-md font-bold transition-all cursor-pointer ${parserFormatSelected === 'atlas' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500 hover:text-slate-900'}`}
                    >
                      Apache Atlas
                    </button>
                  </div>
                </div>

                <div className="flex flex-col flex-1 space-y-2">
                  <div className="flex justify-between items-center text-[10px]">
                    <span className={`${styles.cardTextMuted} font-bold uppercase tracking-wider font-mono`}>Payload JSON Input</span>
                    <button 
                      onClick={() => {
                        if (parserFormatSelected === 'openlineage') {
                          setRawPayloadInput(JSON.stringify({
                            "eventType": "COMPLETE",
                            "eventTime": "2026-07-04T12:00:00Z",
                            "producer": "https://github.com/OpenLineage/OpenLineage",
                            "job": { "namespace": "ds_scheduler", "name": "spark_clean_flight_acars_job" },
                            "inputs": [{ "namespace": "postgresql_raw_sched", "name": "flights_raw" }],
                            "outputs": [{ "namespace": "doris_production_olap", "name": "ds_flights_clean" }]
                          }, null, 2));
                        } else {
                          setRawPayloadInput(JSON.stringify([
                            {
                              "typeName": "spark_process",
                              "attributes": {
                                "name": "spark_process_pilots_biography_sync",
                                "qualifiedName": "spark_process_pilots_biography_sync@cluster",
                                "inputs": [{ "typeName": "hive_table", "uniqueAttributes": { "qualifiedName": "postgresql_raw_sched.pilots_raw@cluster" } }],
                                "outputs": [{ "typeName": "hive_table", "uniqueAttributes": { "qualifiedName": "doris_production_olap.ds_pilots_biography@cluster" } }]
                              }
                            }
                          ], null, 2));
                        }
                      }}
                      className="text-indigo-600 hover:text-indigo-700 font-bold hover:underline cursor-pointer"
                    >
                      重置默认 Payload
                    </button>
                  </div>

                  <textarea
                    value={rawPayloadInput}
                    onChange={(e) => setRawPayloadInput(e.target.value)}
                    className={`flex-1 min-h-[160px] p-3 font-mono text-[10px] ${styles.appBg} text-slate-200 rounded-lg border ${styles.cardBorder} focus:outline-none focus:ring-1 focus:ring-indigo-500 leading-relaxed resize-none`}
                    placeholder="输入 OpenLineage RunEvent 或 Apache Atlas JSON payload..."
                  />
                </div>

                <button
                  onClick={handleParseLineage}
                  disabled={isParsing || !rawPayloadInput.trim()}
                  className="w-full py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-extrabold rounded-lg shadow-sm transition-all flex items-center justify-center gap-1.5 cursor-pointer text-xs"
                >
                  {isParsing ? (
                    <>
                      <span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
                      <span>正在解析血缘格式...</span>
                    </>
                  ) : (
                    <>
                      <Icon name="FileInput" size={12} />
                      <span>解析并合并血缘 DAG (Parse Metadata)</span>
                    </>
                  )}
                </button>
              </div>

              {/* Right Column: Live Lineage Map */}
              <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs flex flex-col space-y-4`}>
                <h3 className={`font-extrabold ${styles.cardText} text-xs border-b ${styles.cardBorder} pb-2.5 flex items-center gap-1.5`}>
                  <Icon name="Network" size={13} className="text-emerald-600" />
                  <span>多维级联血缘拓扑 DAG (Live Lineage Map)</span>
                </h3>

                <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed font-sans -mt-1`}>
                  下面展示实时由底层数据资产向顶层 Ontology 及报表层级联透传的依赖流 (共 {lineageNodes.length} 个节点，{lineageLinks.length} 条关系)。
                </p>

                <div className={`flex-1 ${styles.inputBg} border ${styles.cardBorder}/60 rounded-xl p-4 overflow-y-auto max-h-[300px] space-y-3`}>
                  <div className={`text-[9px] font-bold ${styles.cardTextMuted} uppercase tracking-wider mb-2 flex justify-between font-mono`}>
                    <span>拓扑层级 (Lineage Layers)</span>
                    <span>物理 ➔ 逻辑 ➔ 语义 ➔ 展现</span>
                  </div>

                  <div className="space-y-4">
                    {/* Layer Group 1: Physical DataSources */}
                    <div className="space-y-1.5">
                      <div className={`text-[8px] font-extrabold ${styles.cardTextMuted} tracking-wider uppercase`}>1. 物理数据源与原始流水表 (Raw DataSources)</div>
                      <div className="flex flex-wrap gap-2">
                        {lineageNodes.filter(n => n.type === 'physical_table').map(node => (
                          <div key={node.id} className={`p-2 ${styles.cardBg} border ${styles.cardBorder} rounded-lg flex items-center gap-2 hover:border-slate-400 transition-all cursor-pointer shadow-xs`}>
                            <span className="w-2 h-2 rounded-full bg-slate-400" />
                            <div>
                              <div className={`font-mono text-[9px] font-bold ${styles.cardTextMuted}`}>{node.id}</div>
                              <div className={`text-[8px] ${styles.cardTextMuted} font-sans`}>{node.label}</div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Layer Group 2: ETL Pipelines */}
                    <div className="space-y-1.5">
                      <div className={`text-[8px] font-extrabold ${styles.cardTextMuted} tracking-wider uppercase`}>2. 调度与调度清洗任务 (ETL Pipelines)</div>
                      <div className="flex flex-wrap gap-2">
                        {lineageNodes.filter(n => n.type === 'etl_job').map(node => (
                          <div key={node.id} className="p-2 bg-blue-50/50 border border-blue-200 rounded-lg flex items-center gap-2 hover:border-blue-400 transition-all cursor-pointer shadow-xs">
                            <span className="w-2 h-2 rounded-full bg-blue-500 animate-pulse" />
                            <div>
                              <div className="font-mono text-[9px] font-bold text-blue-800">{node.id}</div>
                              <div className="text-[8px] text-blue-500 font-sans">{node.label}</div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Layer Group 3: Cleansed OLAP Tables */}
                    <div className="space-y-1.5">
                      <div className={`text-[8px] font-extrabold ${styles.cardTextMuted} tracking-wider uppercase`}>3. 物理大宽表资产层 (Cleansed OLAP)</div>
                      <div className="flex flex-wrap gap-2">
                        {lineageNodes.filter(n => n.type === 'olap_table').map(node => (
                          <div key={node.id} className="p-2 bg-emerald-50/50 border border-emerald-200 rounded-lg flex items-center gap-2 hover:border-emerald-400 transition-all cursor-pointer shadow-xs">
                            <span className="w-2 h-2 rounded-full bg-emerald-500" />
                            <div>
                              <div className="font-mono text-[9px] font-bold text-emerald-800">{node.id}</div>
                              <div className="text-[8px] text-emerald-500 font-sans">{node.label}</div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Layer Group 4: Ontology Object Semantics */}
                    <div className="space-y-1.5">
                      <div className={`text-[8px] font-extrabold ${styles.cardTextMuted} tracking-wider uppercase`}>4. 本体层逻辑对象语义 (Ontology Object Semantics)</div>
                      <div className="flex flex-wrap gap-2">
                        {lineageNodes.filter(n => n.type === 'ontology_object').map(node => (
                          <div key={node.id} className="p-2 bg-indigo-50/50 border border-indigo-200 rounded-lg flex items-center gap-2 hover:border-indigo-400 transition-all cursor-pointer shadow-xs">
                            <span className="w-2 h-2 rounded-full bg-indigo-500" />
                            <div>
                              <div className="font-mono text-[9px] font-bold text-indigo-800">{node.id}</div>
                              <div className="text-[8px] text-indigo-500 font-sans">{node.label}</div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Layer Group 5: Reporting Dashboard */}
                    <div className="space-y-1.5">
                      <div className={`text-[8px] font-extrabold ${styles.cardTextMuted} tracking-wider uppercase`}>5. 级联消费端与决策看板 (Downstream Reports)</div>
                      <div className="flex flex-wrap gap-2">
                        {lineageNodes.filter(n => n.type === 'dashboard').map(node => (
                          <div key={node.id} className="p-2 bg-rose-50/50 border border-rose-200 rounded-lg flex items-center gap-2 hover:border-rose-400 transition-all cursor-pointer shadow-xs">
                            <span className="w-2 h-2 rounded-full bg-rose-500" />
                            <div>
                              <div className="font-mono text-[9px] font-bold text-rose-800">{node.id}</div>
                              <div className="text-[8px] text-rose-500 font-sans">{node.label}</div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>

                  </div>
                </div>
              </div>

            </div>

            {/* Bottom Panel: Downstream Impact Analysis */}
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs space-y-4`}>
              <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.cardBorder} pb-3 gap-3`}>
                <div className="space-y-1">
                  <h3 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5`}>
                    <Icon name="ShieldCheck" size={13} className="text-rose-600" />
                    <span>级联下游延迟与漂移影响分析 (Impact Analysis)</span>
                  </h3>
                  <p className={`text-[10px] ${styles.cardTextMuted}`}>选择任意物理表或数据源，自动递归推导所有级联依赖的下游组件并实时判定延迟溢出风险。</p>
                </div>

                <div className="flex items-center gap-2">
                  <span className={`text-[10px] font-bold ${styles.cardTextMuted} font-sans`}>选择物理分析对象:</span>
                  <select
                    value={selectedStartNode}
                    onChange={(e) => setSelectedStartNode(e.target.value)}
                    className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg text-xs font-mono ${styles.cardBg} ${styles.cardText} font-bold cursor-pointer`}
                  >
                    {lineageNodes.filter(n => n.type === 'physical_table' || n.type === 'olap_table').map(n => (
                      <option key={n.id} value={n.id}>{n.id} ({n.type === 'physical_table' ? '物理源表' : 'OLAP大宽表'})</option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Impact analysis results dashboard */}
              {isAnalyzing ? (
                <div className="py-12 flex flex-col items-center justify-center space-y-2">
                  <span className="w-6 h-6 border-2 border-indigo-600 border-t-transparent rounded-full animate-spin" />
                  <span className={`${styles.cardTextMuted} font-bold text-[10px]`}>递归推导下游拓扑链条中...</span>
                </div>
              ) : impactResult ? (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                  
                  {/* Dial Panel */}
                  <div className={`${styles.inputBg} border ${styles.cardBorder} rounded-xl p-4 flex flex-col items-center justify-center text-center space-y-3 relative overflow-hidden`}>
                    
                    {/* Background indicator */}
                    <div className={`absolute top-0 left-0 right-0 h-1.5 ${
                      impactResult.severity === 'CRITICAL' ? 'bg-rose-500 animate-pulse' :
                      impactResult.severity === 'HIGH' ? 'bg-amber-500' :
                      impactResult.severity === 'MEDIUM' ? 'bg-orange-400' : 'bg-emerald-500'
                    }`} />

                    <span className={`text-[9px] font-extrabold ${styles.cardTextMuted} uppercase tracking-widest font-mono`}>级联时效延迟评分</span>
                    
                    <div className="space-y-1">
                      <div className={`text-4xl font-black ${
                        impactResult.severity === 'CRITICAL' ? 'text-rose-600 animate-pulse' :
                        impactResult.severity === 'HIGH' ? 'text-amber-600' :
                        impactResult.severity === 'MEDIUM' ? 'text-orange-500' : 'text-emerald-600'
                      }`}>
                        {impactResult.totalRisk}
                      </div>
                      <div className={`text-[10px] font-extrabold uppercase px-2 py-0.5 rounded-full inline-block ${
                        impactResult.severity === 'CRITICAL' ? 'bg-rose-100 text-rose-700 font-black animate-bounce' :
                        impactResult.severity === 'HIGH' ? 'bg-amber-100 text-amber-700' :
                        impactResult.severity === 'MEDIUM' ? 'bg-orange-100 text-orange-700' : 'bg-emerald-100 text-emerald-700'
                      }`}>
                        {impactResult.severity} 风险等级
                      </div>
                    </div>

                    <div className={`text-[10px] ${styles.cardTextMuted} leading-relaxed font-sans max-w-xs font-medium`}>
                      {impactResult.severity === 'CRITICAL' ? (
                        <p className="text-rose-600 font-bold">
                          🚨 警告：上游已探测到高危 Schema 漂移或调度断流异常！此故障已对下游多级语义层和终端报表产生了灾难性传导，请立即启动安全阻断！
                        </p>
                      ) : impactResult.severity === 'HIGH' ? (
                        <p className="text-amber-600 font-bold">
                          ⚠️ 注意：由于当前激活了时效断流事件，该物理表所关联的下游报表已面临高度的延迟和计算偏误，SLA 指标已被溢出。
                        </p>
                      ) : (
                        <p className={`${styles.cardTextMuted}`}>
                          目前级联链路状态良好。此表的变动会沿拓扑进行正常衰减影响，暂未触发强烈的时效溢出警告。
                        </p>
                      )}
                    </div>
                  </div>

                  {/* Impact Path and Hops */}
                  <div className={`lg:col-span-2 ${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
                    <h4 className={`font-extrabold ${styles.cardTextMuted} text-[10px] uppercase tracking-wider border-b ${styles.cardBorder} pb-2 flex justify-between`}>
                      <span>级联依赖组件影响范围 ({impactResult.impactedNodes.length})</span>
                      <span className={`font-mono ${styles.cardTextMuted}`}>Starting Node: {selectedStartNode}</span>
                    </h4>

                    {impactResult.impactedNodes.length === 0 ? (
                      <div className={`h-full flex flex-col items-center justify-center py-6 ${styles.cardTextMuted} text-center`}>
                        <Icon name="ShieldAlert" size={18} className={`${styles.cardTextMuted}`} />
                        <p className="text-[10px] font-bold">无下游依赖组件</p>
                        <p className={`text-[9px] ${styles.cardTextMuted}`}>该物理表在当前的血缘拓扑中处于叶子/终点阶段。</p>
                      </div>
                    ) : (
                      <div className="space-y-2.5 max-h-48 overflow-y-auto">
                        {impactResult.impactedNodes.map((node: any, i: number) => (
                          <div key={i} className={`flex items-center justify-between p-2 rounded-lg ${styles.inputBg} border ${styles.cardBorder} hover:bg-slate-100/70 transition-all text-[10px]`}>
                            <div className="space-y-0.5">
                              <div className="flex items-center gap-1.5">
                                <span className={`px-1.5 py-0.2 rounded-xs font-bold text-[8px] uppercase ${
                                  node.type === 'etl_job' ? 'bg-blue-100 text-blue-700' :
                                  node.type === 'olap_table' ? 'bg-emerald-100 text-emerald-700' :
                                  node.type === 'ontology_object' ? 'bg-indigo-100 text-indigo-700' :
                                  'bg-rose-100 text-rose-700'
                                }`}>
                                  {node.type === 'etl_job' ? '管道任务' :
                                   node.type === 'olap_table' ? 'OLAP宽表' :
                                   node.type === 'ontology_object' ? '本体对象' : '下游报表'}
                                </span>
                                <span className={`font-bold ${styles.cardText} font-mono`}>{node.id}</span>
                              </div>
                              <p className={`text-[9px] ${styles.cardTextMuted} font-sans`}>
                                传导链条: {node.path.join(' ➔ ')}
                              </p>
                            </div>

                            <div className="text-right shrink-0">
                              <div className={`font-mono font-bold ${styles.cardTextMuted}`}>级联距离: {node.hopCount} Hops</div>
                              <div className={`font-mono font-extrabold text-[11px] ${
                                node.riskScore > 80 ? 'text-rose-600' :
                                node.riskScore > 50 ? 'text-amber-600' : 'text-emerald-600'
                              }`}>
                                风险评分: {node.riskScore}
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                </div>
              ) : (
                <div className={`py-6 text-center ${styles.cardTextMuted} text-[10px]`}>请选择物理分析对象启动级联影响推导。</div>
              )}
            </div>

          </div>
        )}

        {/* TAB: ONTOLOGY ALIGNMENT MANAGER */}
        {activeSubTab === 'ontology' && (
          <div className="space-y-6">
            
            {/* Header */}
            <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.cardBorder} pb-4 gap-4`}>
              <div className="space-y-1">
                <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
                  <Icon name="Workflow" size={16} className="text-blue-600 animate-pulse" />
                  <span>语义本体与分布式物理宽表对齐管理器 (Ontology-to-Physical Column Aligner)</span>
                </h2>
                <p className={`text-xs ${styles.cardTextMuted} font-sans`}>
                  建立强类型对齐契约：将逻辑本体字段 (Ontology Properties) 与 Doris / PostgreSQL 物理大宽表列名进行多对多映射绑定，并编译导出为 RAG 模型检索的无幻觉先验知识图谱。
                </p>
              </div>

              <div className="flex items-center gap-2.5">
                <button
                  onClick={handleExportOntology}
                  disabled={isExporting}
                  className="px-3.5 py-1.5 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white font-extrabold rounded-lg shadow-sm flex items-center gap-1.5 cursor-pointer text-xs transition-all"
                >
                  <Icon name="Download" size={12} />
                  <span>{isExporting ? '编译导出中...' : '导出 RAG 先验知识元数据包'}</span>
                </button>
              </div>
            </div>

            {/* Quick stats cards */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div className={`${styles.cardBg} border ${styles.cardBorder} p-3.5 rounded-xl flex items-center justify-between`}>
                <div>
                  <span className={`${styles.cardTextMuted} font-mono text-[9px] block uppercase`}>Active Ontologies</span>
                  <span className={`text-base font-black ${styles.cardText} font-mono`}>{ontologyMappings.length} Entities</span>
                </div>
                <span className="p-2 bg-blue-50 text-blue-600 rounded-lg"><Icon name="Cpu" size={14} /></span>
              </div>
              <div className={`${styles.cardBg} border ${styles.cardBorder} p-3.5 rounded-xl flex items-center justify-between`}>
                <div>
                  <span className={`${styles.cardTextMuted} font-mono text-[9px] block uppercase`}>Physical Targets</span>
                  <span className={`text-base font-black ${styles.cardText} font-mono`}>{availableTables.length} OLAP Tables</span>
                </div>
                <span className="p-2 bg-emerald-50 text-emerald-600 rounded-lg"><Icon name="Database" size={14} /></span>
              </div>
              <div className={`${styles.cardBg} border ${styles.cardBorder} p-3.5 rounded-xl flex items-center justify-between`}>
                <div>
                  <span className={`${styles.cardTextMuted} font-mono text-[9px] block uppercase`}>Mapped Connections</span>
                  <span className={`text-base font-black ${styles.cardText} font-mono`}>Many-to-Many</span>
                </div>
                <span className="p-2 bg-indigo-50 text-indigo-600 rounded-lg"><Icon name="Combine" size={14} /></span>
              </div>
              <div className={`${styles.cardBg} border ${styles.cardBorder} p-3.5 rounded-xl flex items-center justify-between`}>
                <div>
                  <span className={`${styles.cardTextMuted} font-mono text-[9px] block uppercase`}>Alignment Integrity</span>
                  <span className="text-base font-black text-emerald-600 font-mono">100% Strong-Typed</span>
                </div>
                <span className="p-2 bg-emerald-50 text-emerald-600 rounded-lg"><Icon name="ShieldCheck" size={14} /></span>
              </div>
            </div>

            {/* Main Manager Layout */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
              
              {/* Left Side: Ontology Entities List (3 cols) */}
              <div className="lg:col-span-3 space-y-4">
                <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
                  <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
                    <h3 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5`}>
                      <Icon name="Layers" size={12} className={`${styles.cardTextMuted}`} />
                      <span>语义本体实体 (Ontology Entities)</span>
                    </h3>
                    <button
                      onClick={() => {
                        const newId = prompt('请输入新本体实体标识符 (如 AircraftMaintenance):');
                        if (newId) {
                          const name = prompt('请输入该本体实体的中文显示名称 (如 飞机维保本体):') || newId;
                          const desc = prompt('请输入本体描述:') || '新物理对齐实体';
                          const newEntity: any = {
                            entityId: newId,
                            entityName: newId,
                            chineseName: name,
                            description: desc,
                            mappings: []
                          };
                          const updated = [...ontologyMappings, newEntity];
                          setOntologyMappings(updated);
                          setEditingOntology(newEntity);
                          showToast?.('success', `本地成功创建本体 ${newId}，请为其配置物理列映射规则。`);
                        }
                      }}
                      className="text-blue-600 hover:text-blue-800 font-bold text-[10px] flex items-center gap-0.5 cursor-pointer"
                    >
                      <Icon name="Plus" size={10} />
                      <span>新建</span>
                    </button>
                  </div>

                  <div className="space-y-1.5">
                    {ontologyMappings.map(ent => {
                      const isSelected = editingOntology?.entityId === ent.entityId;
                      return (
                        <button
                          key={ent.entityId}
                          onClick={() => setEditingOntology(ent)}
                          className={`w-full p-2.5 rounded-lg border text-left flex flex-col space-y-1 transition-all cursor-pointer ${
                            isSelected
                              ? '${styles.accentBg} ${styles.accentBorder} text-white shadow-sm'
                              : 'bg-slate-50 hover:bg-slate-100 border-slate-200 text-slate-700'
                          }`}
                        >
                          <div className="flex items-center justify-between w-full">
                            <span className="font-black text-xs">{ent.entityId}</span>
                            <span className={`text-[8px] px-1.5 py-0.5 rounded font-mono ${
                              isSelected ? 'bg-blue-500 text-white' : 'bg-slate-200 text-slate-600'
                            }`}>
                              {ent.mappings?.length || 0} fields
                            </span>
                          </div>
                          <span className={`text-[9px] truncate block ${isSelected ? 'text-slate-300' : 'text-slate-500'}`}>
                            {ent.chineseName || ent.entityName}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Helpful Tip */}
                <div className="p-4 bg-blue-50/50 border border-blue-100 rounded-xl space-y-2 text-[10px] leading-relaxed text-blue-800 font-sans">
                  <p className="font-extrabold flex items-center gap-1.5">
                    <Icon name="Lightbulb" size={12} className="text-blue-600" />
                    <span>多对多穿透绑定</span>
                  </p>
                  <p>
                    系统完美支持多对多映射。例如，您可以将逻辑 <code>AviationPilot</code> 实体的 <code>lastAssignedFlightId</code> 穿透映射至另一个物理表 <code>ds_flights_clean.flight_id</code> 中，形成物理跨源级联，确保智能体查询可以轻松跨表对齐。
                  </p>
                </div>
              </div>

              {/* Right Side: Properties & Column Mapping Table (9 cols) */}
              <div className="lg:col-span-9">
                {editingOntology ? (
                  <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs space-y-5`}>
                    
                    {/* Selected Entity Info header */}
                    <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.cardBorder} pb-3 gap-3`}>
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className={`font-black ${styles.cardText} text-sm font-mono`}>{editingOntology.entityId}</span>
                          <span className="px-2 py-0.5 bg-blue-50 text-blue-700 text-[10px] font-bold rounded-md">
                            {editingOntology.chineseName || '逻辑语义实体'}
                          </span>
                        </div>
                        <p className={`text-[11px] ${styles.cardTextMuted} font-sans`}>{editingOntology.description}</p>
                      </div>

                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => {
                            if (confirm(`确认要删除 ${editingOntology.entityId} 语义实体及其全部列级映射吗？`)) {
                              const remaining = ontologyMappings.filter(e => e.entityId !== editingOntology.entityId);
                              setOntologyMappings(remaining);
                              setEditingOntology(remaining[0] || null);
                              handleSaveOntologyMappings(remaining);
                            }
                          }}
                          className="px-2.5 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 hover:border-rose-300 font-bold rounded-lg text-[10px] flex items-center gap-1 cursor-pointer transition-all"
                        >
                          <Icon name="Trash2" size={11} />
                          <span>删除此实体</span>
                        </button>
                      </div>
                    </div>

                    {/* Mappings Table */}
                    <div className="space-y-3">
                      <div className="flex items-center justify-between">
                        <span className={`text-[10px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono`}>对齐映射清单 (Logical-to-Physical Column Mappings)</span>
                        <button
                          onClick={() => {
                            const newMappingItem = {
                              logicalField: 'newField',
                              logicalType: 'String',
                              physicalTable: availableTables[0]?.tableName || 'ds_flights_clean',
                              physicalColumn: availableTables[0]?.columns[0]?.name || 'flight_id',
                              description: '新映射物理属性'
                            };
                            const updatedMappings = ontologyMappings.map(e => {
                              if (e.entityId === editingOntology.entityId) {
                                return {
                                  ...e,
                                  mappings: [...(e.mappings || []), newMappingItem]
                                };
                              }
                              return e;
                            });
                            setOntologyMappings(updatedMappings);
                            setEditingOntology(updatedMappings.find(e => e.entityId === editingOntology.entityId));
                          }}
                          className="text-blue-600 hover:text-blue-800 font-bold text-[10px] flex items-center gap-0.5 cursor-pointer"
                        >
                          <Icon name="Plus" size={11} />
                          <span>添加属性映射 (Add Row)</span>
                        </button>
                      </div>

                      {/* Actual Table */}
                      <div className={`border ${styles.cardBorder} rounded-xl overflow-hidden`}>
                        <table className="w-full text-left border-collapse">
                          <thead>
                            <tr className={`${styles.inputBg} border-b ${styles.cardBorder} text-[10px] font-extrabold ${styles.cardTextMuted} font-sans select-none`}>
                              <th className="p-3">逻辑属性名 (Logical Field)</th>
                              <th className="p-3">数据类型 (Type)</th>
                              <th className="p-3">物理库表 (Physical Table)</th>
                              <th className="p-3">物理列字段 (Physical Column)</th>
                              <th className="p-3">注释及说明 (Description)</th>
                              <th className="p-3 text-center">操作</th>
                            </tr>
                          </thead>
                          <tbody className={`divide-y ${styles.cardBorder} text-[11px]`}>
                            {(!editingOntology.mappings || editingOntology.mappings.length === 0) ? (
                              <tr>
                                <td colSpan={6} className={`p-8 text-center ${styles.cardTextMuted} font-sans`}>
                                  ⚠️ 尚未为此实体配置列级对齐。请点击右上角「添加属性映射」开始绑定。
                                </td>
                              </tr>
                            ) : (
                              editingOntology.mappings.map((m: any, idx: number) => {
                                // Find available columns for the selected physicalTable
                                const matchedTable = availableTables.find(t => t.tableName === m.physicalTable);
                                const availableCols = matchedTable ? matchedTable.columns : [];

                                return (
                                  <tr key={idx} className={`hover:${styles.appBg}`}>
                                    {/* Logical Field Name */}
                                    <td className="p-3">
                                      <input
                                        type="text"
                                        value={m.logicalField}
                                        onChange={(e) => {
                                          const nextVal = e.target.value;
                                          const updated = ontologyMappings.map(ent => {
                                            if (ent.entityId === editingOntology.entityId) {
                                              const newM = [...ent.mappings];
                                              newM[idx] = { ...newM[idx], logicalField: nextVal };
                                              return { ...ent, mappings: newM };
                                            }
                                            return ent;
                                          });
                                          setOntologyMappings(updated);
                                          setEditingOntology(updated.find(ent => ent.entityId === editingOntology.entityId));
                                        }}
                                        className={`w-full px-2 py-1 border ${styles.cardBorder} rounded-md font-mono text-[10px] font-bold ${styles.cardTextMuted} ${styles.cardBg}`}
                                      />
                                    </td>

                                    {/* Logical Type */}
                                    <td className="p-3">
                                      <select
                                        value={m.logicalType}
                                        onChange={(e) => {
                                          const nextVal = e.target.value;
                                          const updated = ontologyMappings.map(ent => {
                                            if (ent.entityId === editingOntology.entityId) {
                                              const newM = [...ent.mappings];
                                              newM[idx] = { ...newM[idx], logicalType: nextVal };
                                              return { ...ent, mappings: newM };
                                            }
                                            return ent;
                                          });
                                          setOntologyMappings(updated);
                                          setEditingOntology(updated.find(ent => ent.entityId === editingOntology.entityId));
                                        }}
                                        className={`px-1.5 py-1 border ${styles.cardBorder} rounded-md font-bold text-[10px] ${styles.cardBg} ${styles.cardTextMuted}`}
                                      >
                                        <option value="String">String</option>
                                        <option value="Integer">Integer</option>
                                        <option value="Double">Double</option>
                                        <option value="DateTime">DateTime</option>
                                        <option value="Boolean">Boolean</option>
                                      </select>
                                    </td>

                                    {/* Physical Table Selection */}
                                    <td className="p-3">
                                      <select
                                        value={m.physicalTable}
                                        onChange={(e) => {
                                          const nextTable = e.target.value;
                                          // Default column of new table
                                          const matchedT = availableTables.find(t => t.tableName === nextTable);
                                          const firstCol = matchedT?.columns[0]?.name || 'flight_id';

                                          const updated = ontologyMappings.map(ent => {
                                            if (ent.entityId === editingOntology.entityId) {
                                              const newM = [...ent.mappings];
                                              newM[idx] = { ...newM[idx], physicalTable: nextTable, physicalColumn: firstCol };
                                              return { ...ent, mappings: newM };
                                            }
                                            return ent;
                                          });
                                          setOntologyMappings(updated);
                                          setEditingOntology(updated.find(ent => ent.entityId === editingOntology.entityId));
                                        }}
                                        className={`px-1.5 py-1 border ${styles.cardBorder} rounded-md font-bold text-[10px] ${styles.cardBg} text-blue-800`}
                                      >
                                        {availableTables.map(t => (
                                          <option key={t.tableName} value={t.tableName}>{t.tableName}</option>
                                        ))}
                                      </select>
                                    </td>

                                    {/* Physical Column Selection */}
                                    <td className="p-3">
                                      <select
                                        value={m.physicalColumn}
                                        onChange={(e) => {
                                          const nextCol = e.target.value;
                                          const updated = ontologyMappings.map(ent => {
                                            if (ent.entityId === editingOntology.entityId) {
                                              const newM = [...ent.mappings];
                                              newM[idx] = { ...newM[idx], physicalColumn: nextCol };
                                              return { ...ent, mappings: newM };
                                            }
                                            return ent;
                                          });
                                          setOntologyMappings(updated);
                                          setEditingOntology(updated.find(ent => ent.entityId === editingOntology.entityId));
                                        }}
                                        className={`px-1.5 py-1 border ${styles.cardBorder} rounded-md font-mono text-[10px] font-bold ${styles.cardBg} text-emerald-800`}
                                      >
                                        {availableCols.map((c: any) => (
                                          <option key={c.name} value={c.name}>{c.name} ({c.type})</option>
                                        ))}
                                      </select>
                                    </td>

                                    {/* Description/Explanation */}
                                    <td className="p-3">
                                      <input
                                        type="text"
                                        value={m.description}
                                        onChange={(e) => {
                                          const nextVal = e.target.value;
                                          const updated = ontologyMappings.map(ent => {
                                            if (ent.entityId === editingOntology.entityId) {
                                              const newM = [...ent.mappings];
                                              newM[idx] = { ...newM[idx], description: nextVal };
                                              return { ...ent, mappings: newM };
                                            }
                                            return ent;
                                          });
                                          setOntologyMappings(updated);
                                          setEditingOntology(updated.find(ent => ent.entityId === editingOntology.entityId));
                                        }}
                                        className={`w-full px-2 py-1 border ${styles.cardBorder} rounded-md text-[10px] ${styles.cardTextMuted} ${styles.cardBg}`}
                                      />
                                    </td>

                                    {/* Delete Row Button */}
                                    <td className="p-3 text-center">
                                      <button
                                        onClick={() => {
                                          const updated = ontologyMappings.map(ent => {
                                            if (ent.entityId === editingOntology.entityId) {
                                              const newM = ent.mappings.filter((_: any, i: number) => i !== idx);
                                              return { ...ent, mappings: newM };
                                            }
                                            return ent;
                                          });
                                          setOntologyMappings(updated);
                                          setEditingOntology(updated.find(ent => ent.entityId === editingOntology.entityId));
                                        }}
                                        className="p-1 rounded bg-rose-50 text-rose-600 hover:bg-rose-100 cursor-pointer transition-colors"
                                      >
                                        <Icon name="Trash2" size={11} />
                                      </button>
                                    </td>
                                  </tr>
                                );
                              })
                            )}
                          </tbody>
                        </table>
                      </div>
                    </div>

                    {/* Submit and Save Panel */}
                    <div className={`flex items-center justify-between pt-4 border-t ${styles.cardBorder}`}>
                      <div className={`text-[10px] ${styles.cardTextMuted}`}>
                        * 注意：保存映射后，系统将实时更新 RAG 先验上下文数据库，AI 生成将根据此架构强类型对齐。
                      </div>
                      <button
                        onClick={() => handleSaveOntologyMappings(ontologyMappings)}
                        className={`px-5 py-2 ${styles.appBg} hover:bg-slate-800 text-white font-extrabold rounded-lg shadow-sm flex items-center gap-1.5 cursor-pointer text-xs transition-colors`}
                      >
                        <Icon name="Save" size={12} />
                        <span>应用并保存强对齐契约 (Save & Apply)</span>
                      </button>
                    </div>

                  </div>
                ) : (
                  <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-12 text-center ${styles.cardTextMuted} space-y-2`}>
                    <Icon name="Workflow" size={24} className={`mx-auto ${styles.cardTextMuted} animate-pulse`} />
                    <p className="font-bold text-xs">请在左侧选择或创建一个本体对象 (Ontology Entity)</p>
                    <p className={`text-[10px] ${styles.cardTextMuted}`}>选择实体后即可开始建立列级强类型物理对齐。</p>
                  </div>
                )}
              </div>

            </div>

            {/* Export Markdown Modal popup */}
            {showExportModal && (
              <div className={`fixed inset-0 z-50 flex items-center justify-center p-4 ${styles.appBg}/60 backdrop-blur-xs animate-fade-in`}>
                <div className={`${styles.cardBg} rounded-2xl max-w-2xl w-full border ${styles.cardBorder} shadow-xl overflow-hidden flex flex-col max-h-[85vh]`}>
                  
                  {/* Modal Header */}
                  <div className={`${styles.cardBg} text-white p-4 flex items-center justify-between`}>
                    <div className="flex items-center gap-2">
                      <Icon name="Download" size={15} className="text-blue-400" />
                      <span className="font-black text-xs">已编译的 Ontology Schema 先验知识元数据包 (RAG Prior-Knowledge)</span>
                    </div>
                    <button
                      onClick={() => setShowExportModal(false)}
                      className={`${styles.cardTextMuted} hover:text-white font-bold cursor-pointer`}
                    >
                      <Icon name="X" size={16} />
                    </button>
                  </div>

                  {/* Modal Content */}
                  <div className="p-5 overflow-y-auto space-y-4">
                    <p className={`text-[11px] ${styles.cardTextMuted} font-sans`}>
                      下面的元数据包已成功融合成无幻觉 RAG 专属的非结构化上下文契约。当 Copilot 运行时，此文本会与检索意图自动对齐，强行拦截 AI 漂移并对齐底细列：
                    </p>

                    <pre className={`p-4 ${styles.appBg} text-slate-200 rounded-xl font-mono text-[9px] whitespace-pre-wrap leading-relaxed select-text max-h-[350px] overflow-y-auto`}>
                      {exportedMarkdown}
                    </pre>

                    <div className="p-3 bg-blue-50 border border-blue-100 rounded-xl text-[10px] text-blue-700 font-sans flex items-start gap-1.5">
                      <Icon name="Info" size={13} className="shrink-0 mt-0.5" />
                      <span>该元数据包已经和后端 RAG 推理引擎 (Grounded RAG Sandbox) 彻底绑定。您可以关闭此弹窗，直接切换到「知识检索与 RAG 模拟」分区分区测试您的全新映射关系！</span>
                    </div>
                  </div>

                  {/* Modal Footer */}
                  <div className={`p-4 ${styles.inputBg} border-t ${styles.cardBorder} flex items-center justify-end gap-2 shrink-0`}>
                    <button
                      onClick={() => {
                        navigator.clipboard.writeText(exportedMarkdown);
                        showToast?.('success', '知识包已成功复制到剪贴板！');
                      }}
                      className="px-4 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg text-xs cursor-pointer flex items-center gap-1 transition-all"
                    >
                      <Icon name="Copy" size={12} />
                      <span>复制知识包</span>
                    </button>
                    <button
                      onClick={() => setShowExportModal(false)}
                      className={`px-4 py-1.5 bg-slate-200 hover:bg-slate-300 ${styles.cardTextMuted} font-bold rounded-lg text-xs cursor-pointer transition-all`}
                    >
                      <span>关闭</span>
                    </button>
                  </div>

                </div>
              </div>
            )}

          </div>
        )}

        {/* TAB 3: VECTOR INDEX BUILDER */}
        {activeSubTab === 'index' && (
          <div className="space-y-6">
            
            {/* Title */}
            <div className={`border-b ${styles.cardBorder} pb-3 space-y-1`}>
              <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
                <span className="p-1.5 rounded-lg bg-indigo-50 text-indigo-600">
                  <Icon name="Binary" size={16} />
                </span>
                <span>AIP Knowledge 联邦知识库切块向量化索引引擎 (text-embedding-004)</span>
              </h2>
              <p className={`text-xs ${styles.cardTextMuted}`}>
                将集成工作台的 Doris 物理结构、本体工作台的逻辑映射、安全中心的列级脱敏策略进行统一序列化切块，调用 Google Gemini 
                Sovereign 嵌入模型转换为 768 维特征向量，并提供 PGVector / Milvus 数据库持久化落地演示。
              </p>
            </div>

            {/* Config & Monitor Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              
              {/* Left Column: Config Panel & Progress (5 cols) */}
              <div className="lg:col-span-5 space-y-4">
                
                {/* Config Panel */}
                <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-4`}>
                  <h3 className={`font-extrabold ${styles.cardText} text-xs border-b ${styles.cardBorder} pb-2 flex items-center gap-1.5`}>
                    <Icon name="Settings" size={13} className={`${styles.cardTextMuted}`} />
                    <span>向量分片参数配置 (Chunking Config)</span>
                  </h3>

                  <div className="space-y-3">
                    <div className="space-y-1">
                      <label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>元数据向量模型 (Embedding Model)</label>
                      <select
                        value={embeddingModel}
                        onChange={e => setEmbeddingModel(e.target.value)}
                        className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs font-mono ${styles.cardBg} ${styles.cardText}`}
                      >
                        <option value="text-embedding-004">Google Text-Embedding-004 (768 维)</option>
                      </select>
                    </div>

                    <div className="space-y-1">
                      <div className="flex justify-between items-center">
                        <label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>分片大小 (Chunk Size)</label>
                        <span className={`font-mono text-[10px] font-bold ${styles.cardTextMuted} ${styles.appBg} px-1.5 py-0.5 rounded`}>{chunkSize} 字符</span>
                      </div>
                      <input
                        type="range"
                        min={128}
                        max={1024}
                        step={64}
                        value={chunkSize}
                        onChange={e => setChunkSize(Number(e.target.value))}
                        className="w-full h-1.5 bg-slate-150 rounded-lg appearance-none cursor-pointer accent-indigo-600"
                      />
                    </div>

                    <div className="space-y-1">
                      <div className="flex justify-between items-center">
                        <label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>重叠度 (Overlap Size)</label>
                        <span className={`font-mono text-[10px] font-bold ${styles.cardTextMuted} ${styles.appBg} px-1.5 py-0.5 rounded`}>{overlap} 字符</span>
                      </div>
                      <input
                        type="range"
                        min={10}
                        max={200}
                        step={10}
                        value={overlap}
                        onChange={e => setOverlap(Number(e.target.value))}
                        className="w-full h-1.5 bg-slate-150 rounded-lg appearance-none cursor-pointer accent-indigo-600"
                      />
                    </div>
                  </div>

                  <button
                    onClick={handleSyncAll}
                    disabled={isSyncingAll}
                    className={`w-full py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-1.5 shadow-sm cursor-pointer ${
                      isSyncingAll ? 'opacity-75 cursor-not-allowed' : ''
                    }`}
                  >
                    {isSyncingAll ? (
                      <>
                        <span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                        <span>向量对齐与特征提取中...</span>
                      </>
                    ) : (
                      <>
                        <Icon name="Cpu" size={13} />
                        <span>构建闭环元数据向量库 (Sync)</span>
                      </>
                    )}
                  </button>
                </div>

                {/* Live Monitor Console */}
                <div className={`${styles.cardBg} rounded-xl p-4 border ${styles.cardBorder} space-y-2 shadow-inner`}>
                  <div className="flex items-center justify-between border-b border-slate-900 pb-2">
                    <span className={`text-[10px] font-extrabold ${styles.cardTextMuted} font-mono tracking-wider flex items-center gap-1.5`}>
                      <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                      SYSTEM PIPELINE MONITOR
                    </span>
                    <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>NODE v20.10.0</span>
                  </div>
                  <div className="font-mono text-[9.5px] leading-relaxed h-52 overflow-y-auto space-y-1 scrollbar-thin scrollbar-thumb-slate-800">
                    {syncLogs.length > 0 ? (
                      syncLogs.map((log, idx) => {
                        let textClass = "text-slate-300";
                        if (log.includes('🚨')) textClass = "text-rose-400 font-bold";
                        else if (log.includes('✅')) textClass = "text-emerald-400";
                        else if (log.includes('🔄')) textClass = "text-amber-400";
                        return (
                          <div key={idx} className={textClass}>
                            {log}
                          </div>
                        );
                      })
                    ) : (
                      <div className={`${styles.cardTextMuted} italic`}>等待启动 AIP 元数据提取同步监听...</div>
                    )}
                  </div>
                </div>

              </div>

              {/* Right Column: Statistics, Real-time Chunks, & Persistence Scripts (7 cols) */}
              <div className="lg:col-span-7 space-y-4">
                
                {/* Stats row */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                  <div className={`${styles.cardBg} border ${styles.cardBorder} p-3 rounded-xl shadow-xs flex items-center justify-between`}>
                    <div>
                      <span className={`${styles.cardTextMuted} font-mono text-[9px] block`}>CUMULATIVE CHUNKS</span>
                      <span className={`text-base font-black ${styles.cardText} font-mono`}>{vectorChunks.length} chunks</span>
                    </div>
                    <span className="p-2 rounded-lg bg-blue-50 text-blue-600">
                      <Icon name="Layers" size={14} />
                    </span>
                  </div>

                  <div className={`${styles.cardBg} border ${styles.cardBorder} p-3 rounded-xl shadow-xs flex items-center justify-between`}>
                    <div>
                      <span className={`${styles.cardTextMuted} font-mono text-[9px] block`}>VECTOR DIMENSION</span>
                      <span className={`text-base font-black ${styles.cardText} font-mono`}>768-Dim</span>
                    </div>
                    <span className="p-2 rounded-lg bg-indigo-50 text-indigo-600">
                      <Icon name="Binary" size={14} />
                    </span>
                  </div>

                  <div className={`${styles.cardBg} border ${styles.cardBorder} p-3 rounded-xl shadow-xs flex items-center justify-between`}>
                    <div>
                      <span className={`${styles.cardTextMuted} font-mono text-[9px] block`}>INDEX INTEGRITY</span>
                      <span className="text-base font-black text-emerald-600 font-mono">100% HEALTH</span>
                    </div>
                    <span className="p-2 rounded-lg bg-emerald-50 text-emerald-600">
                      <Icon name="CheckCircle2" size={14} />
                    </span>
                  </div>
                </div>

                {/* Real-time Document Chunks Browser */}
                <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
                  <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
                    <h3 className={`font-bold ${styles.cardText} text-xs flex items-center gap-1.5`}>
                      <Icon name="Settings" size={13} className={`${styles.cardTextMuted}`} />
                      <span>768 维元数据分片浏览器 (Vector Chunk Browser)</span>
                    </h3>
                    <span className={`text-[10px] ${styles.cardTextMuted}`}>数量: {vectorChunks.length} 个分片</span>
                  </div>

                  <div className="space-y-2.5 max-h-60 overflow-y-auto">
                    {vectorChunks.length > 0 ? (
                      vectorChunks.map((chunk, idx) => (
                        <div key={idx} className={`border ${styles.cardBorder} rounded-lg p-2.5 ${styles.inputBg} space-y-1.5`}>
                          <div className="flex items-center justify-between text-[10px]">
                            <div className="flex items-center gap-1.5">
                              <span className="px-1.5 py-0.5 bg-indigo-50 text-indigo-700 rounded-md font-bold text-[9px]">
                                {chunk.source === 'security' ? '安全规则' : chunk.source === 'ontology' ? '语义本体' : '物理集成'}
                              </span>
                              <span className={`font-bold ${styles.cardTextMuted} truncate max-w-[200px]`}>{chunk.title}</span>
                            </div>
                            <span className={`${styles.cardTextMuted} font-mono text-[9px]`}>Chunk #{idx + 1} ({chunk.chunkSize} chars)</span>
                          </div>
                          
                          <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed font-sans ${styles.cardBg} border ${styles.cardBorder} p-2 rounded-md`}>
                            {chunk.text}
                          </p>

                          <div className={`pt-1 border-t ${styles.cardBorder}/60 flex flex-col gap-0.5`}>
                            <span className={`text-[8.5px] font-bold ${styles.cardTextMuted} font-mono`}>GOOGLE TEXT-EMBEDDING-004 (768-DIM VECTOR):</span>
                            <span className="text-[8.5px] text-indigo-600 bg-indigo-50/50 p-1 rounded font-mono truncate">
                              {chunk.vectorPreview}
                            </span>
                          </div>
                        </div>
                      ))
                    ) : (
                      <div className={`py-8 text-center ${styles.cardTextMuted} space-y-1`}>
                        <Icon name="FolderClosed" size={24} className={`${styles.cardTextMuted} mx-auto`} />
                        <p className="text-xs">暂无切块向量。请点击左侧「构建闭环元数据向量库」运行计算管道！</p>
                      </div>
                    )}
                  </div>
                </div>

                {/* Local Vector DB Persistence Code Generator */}
                <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
                  <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.cardBorder} pb-2 gap-2`}>
                    <h3 className={`font-bold ${styles.cardText} text-xs flex items-center gap-1.5`}>
                      <Icon name="Database" size={13} className="text-indigo-600" />
                      <span>本地向量数据库持久化写入演示 (Persistence Sandbox)</span>
                    </h3>
                    
                    {/* Segmented control */}
                    <div className={`flex ${styles.appBg} p-0.5 rounded-lg border ${styles.cardBorder}/60 shrink-0`}>
                      <button
                        onClick={() => setPersistenceTab('pgvector')}
                        className={`px-2 py-0.5 rounded-md text-[9px] font-bold transition-all cursor-pointer ${
                          persistenceTab === 'pgvector' 
                            ? 'bg-white text-indigo-700 shadow-xs' 
                            : 'text-slate-500 hover:text-slate-800'
                        }`}
                      >
                        PGVector (SQL)
                      </button>
                      <button
                        onClick={() => setPersistenceTab('milvus')}
                        className={`px-2 py-0.5 rounded-md text-[9px] font-bold transition-all cursor-pointer ${
                          persistenceTab === 'milvus' 
                            ? 'bg-white text-indigo-700 shadow-xs' 
                            : 'text-slate-500 hover:text-slate-800'
                        }`}
                      >
                        Milvus (Node.js)
                      </button>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <div className={`flex justify-between items-center text-[10px] ${styles.cardTextMuted}`}>
                      <span>{persistenceTab === 'pgvector' ? '🐘 PostgreSQL + PGVector 模式声明及批量写回' : '⚡ Milvus 向量引擎 Node.js SDK 动态写入脚本'}</span>
                      <button
                        onClick={() => {
                          const code = persistenceTab === 'pgvector' ? pgvectorSql : milvusCode;
                          if (code) {
                            navigator.clipboard.writeText(code);
                            showToast?.('success', '代码已复制到剪贴板！');
                          }
                        }}
                        className="text-indigo-600 hover:text-indigo-800 font-bold flex items-center gap-1 cursor-pointer"
                      >
                        <Icon name="Copy" size={11} />
                        <span>一键复制演示代码</span>
                      </button>
                    </div>

                    <div className={`${styles.appBg} ${styles.cardTextMuted} rounded-xl p-3 h-48 overflow-y-auto font-mono text-[9.5px] leading-relaxed border border-slate-850`}>
                      {persistenceTab === 'pgvector' ? (
                        pgvectorSql ? (
                          <pre className="whitespace-pre">{pgvectorSql}</pre>
                        ) : (
                          <div className={`${styles.cardTextMuted} italic py-12 text-center`}>暂无 PGVector SQL 代码，请先启动向量计算管道。</div>
                        )
                      ) : (
                        milvusCode ? (
                          <pre className="whitespace-pre">{milvusCode}</pre>
                        ) : (
                          <div className={`${styles.cardTextMuted} italic py-12 text-center`}>暂无 Milvus SDK 写入代码，请先启动向量计算管道。</div>
                        )
                      )}
                    </div>
                  </div>
                </div>

              </div>

            </div>

          </div>
        )}

        {/* TAB 4: RAG & COPILOT SIMULATION PLAYGROUND */}
        {activeSubTab === 'rag' && (
          <div className="space-y-6">
            
            {/* Title */}
            <div className={`border-b ${styles.cardBorder} pb-3 space-y-1`}>
              <h2 className={`text-sm font-black ${styles.cardText}`}>联邦知识库检索与大模型推理仿真沙箱 (Semantic Grounding Sandbox)</h2>
              <p className={`text-xs ${styles.cardTextMuted}`}>模拟 AIP Copilot 收到用户自然语言提问时，如何在物理、本体和安全三个数据库中进行相关元数据检索，并融合成强上下文 Prompt 呈送给大模型，生成完美答案的动态过程。</p>
            </div>

            {/* Sandbox Layout */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              
              {/* Left query column: 5 columns */}
              <div className="lg:col-span-5 space-y-4">
                
                <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-4`}>
                  <div className={`border-b ${styles.cardBorder} pb-2 flex items-center gap-2`}>
                    <span className="p-1.5 rounded bg-blue-50 text-blue-600">
                      <Icon name="Keyboard" size={13} />
                    </span>
                    <h3 className={`font-bold ${styles.cardText} text-xs`}>第1步: 输入自然语言查询 (User Query)</h3>
                  </div>

                  <div className="space-y-2">
                    <textarea
                      value={queryInput}
                      onChange={e => setQueryInput(e.target.value)}
                      rows={3}
                      className={`w-full px-3 py-2 border ${styles.cardBorder} rounded-lg text-xs font-sans leading-relaxed ${styles.cardTextMuted} focus:outline-hidden focus:border-blue-500`}
                      placeholder="请输入关于物理调度或安全权限的复杂提问..."
                    />
                    
                    {/* Pre-canned prompts */}
                    <div className="space-y-1.5">
                      <span className={`text-[9px] ${styles.cardTextMuted} font-extrabold uppercase block`}>推荐测试问题 (Closed-Loop Test Prompts):</span>
                      <div className="flex flex-col gap-1">
                        {[
                          '查询欧盟GDPR合规隔离下，飞行员张建国的执勤及SSN脱敏状态',
                          '帮我评估客票营收明细表 ds_ticket_sales 被高危扫描的风险及阻断日志',
                          '查询 UA102 航班在 Apache Doris 物理库中对应的上游链路与时效影响'
                        ].map((p, idx) => (
                          <button
                            key={idx}
                            onClick={() => setQueryInput(p)}
                            className={`text-left px-2 py-1 ${styles.inputBg} border ${styles.cardBorder} hover:bg-blue-50 hover:border-blue-200 rounded-lg text-[10px] ${styles.cardTextMuted} truncate cursor-pointer transition-all`}
                          >
                            💡 {p}
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>

                  <button
                    onClick={handleRunRAG}
                    disabled={isRetrieving || !queryInput.trim()}
                    className={`w-full py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-1.5 shadow-sm cursor-pointer ${
                      isRetrieving ? 'opacity-70 cursor-not-allowed' : ''
                    }`}
                  >
                    {isRetrieving ? (
                      <>
                        <span className={`w-3.5 h-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                        <span>向量对齐与元数据检索中...</span>
                      </>
                    ) : (
                      <>
                        <Icon name="Flame" size={13} />
                        <span>开始检索并模拟 AI 推理</span>
                      </>
                    )}
                  </button>
                </div>

                {/* Step 2: Retrieved Grounded Metadata chunks */}
                {retrievedDocs.length > 0 && (
                  <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
                    <div className={`border-b ${styles.cardBorder} pb-2 flex items-center justify-between`}>
                      <div className={`flex items-center gap-1.5 font-bold ${styles.cardText} text-xs`}>
                        <Icon name="Layers" size={12} className="text-emerald-500" />
                        <span>第2步: 向量相关度检索结果 (Grounding)</span>
                      </div>
                      <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>DORIS VEC MATCH</span>
                    </div>

                    <div className="space-y-2 max-h-56 overflow-y-auto">
                      {retrievedDocs.map((doc, idx) => (
                        <div key={idx} className={`p-2 ${styles.inputBg} border ${styles.cardBorder} rounded-lg space-y-1`}>
                          <div className="flex items-center justify-between text-[10px]">
                            <span className={`font-bold ${styles.cardText}`}>{doc.title}</span>
                            <span className="px-1.5 bg-emerald-50 text-emerald-600 text-[9px] font-bold rounded-md">
                              Similarity: {(doc.score * 100).toFixed(0)}%
                            </span>
                          </div>
                          <p className={`text-[9px] ${styles.cardTextMuted} leading-relaxed font-sans`}>{doc.snippet}</p>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

              </div>

              {/* Right prompt & model output column: 7 columns */}
              <div className="lg:col-span-7 space-y-4">
                
                {/* RAG prompt box */}
                {ragPrompt && (
                  <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-2`}>
                    <h3 className={`font-bold ${styles.cardText} text-xs flex items-center gap-1.5 text-indigo-600 border-b ${styles.cardBorder} pb-2`}>
                      <Icon name="Sparkles" size={13} />
                      <span>第3步: 融合生成的 AI 提示词 (RAG Prompt Context)</span>
                    </h3>
                    <div className={`${styles.appBg} ${styles.cardTextMuted} rounded-xl p-3 h-32 overflow-y-auto font-mono text-[9px] leading-relaxed`}>
                      {ragPrompt}
                    </div>
                  </div>
                )}

                {/* Final model output response */}
                <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-sm space-y-3`}>
                  <h3 className={`font-bold ${styles.cardText} text-xs flex items-center gap-1.5 text-emerald-600 border-b ${styles.cardBorder} pb-2`}>
                    <Icon name="Bot" size={14} className="animate-bounce" />
                    <span>第4步: AIP Copilot 无幻觉合规输出 (AI Output)</span>
                  </h3>

                  {isRetrieving ? (
                    <div className={`py-8 text-center ${styles.cardTextMuted} space-y-2`}>
                      <Icon name="RefreshCw" size={24} className={`animate-spin ${styles.cardTextMuted} mx-auto`} />
                      <p className="text-xs font-medium">模型正在依据三大工作台元数据进行强类型合规校正推理，请稍候...</p>
                    </div>
                  ) : llmOutput ? (
                    <div className={`${styles.inputBg} border ${styles.cardBorder} p-4 rounded-xl ${styles.cardTextMuted} text-[11px] font-sans leading-relaxed whitespace-pre-wrap`}>
                      {llmOutput}
                    </div>
                  ) : (
                    <div className={`py-8 text-center ${styles.cardTextMuted} space-y-1`}>
                      <Icon name="Bot" size={24} className={`${styles.cardTextMuted} mx-auto`} />
                      <p>等待运行 RAG 仿真推理...</p>
                      <p className={`text-[10px] ${styles.cardTextMuted}`}>左侧点击按钮，即可一键观察闭环装配流程</p>
                    </div>
                  )}
                </div>

              </div>

            </div>

          </div>
        )}

      </div>

    </div>
  );
}
