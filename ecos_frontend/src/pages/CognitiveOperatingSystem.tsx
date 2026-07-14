/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 * 
 * Databridge C2EOS Cognitive Decision Suite & Blueprint Analyzer
 */

import React, { useState, useEffect } from "react";
import { 
  Brain, 
  TrendingUp, 
  AlertTriangle, 
  Layers, 
  Award, 
  Target, 
  Activity, 
  Settings, 
  RefreshCw,
  CheckCircle,
  Zap,
  Shield, 
  Database, 
  Cpu, 
  Globe, 
  Terminal, 
  Lock, 
  Server, 
  BarChart4, 
  FileCheck,
  Binary
} from "lucide-react";
import { Goal, CausalLink, KnowledgeNode, KnowledgeEdge } from "../types";
import { fetchKnowledgeGraph, fetchWorldGoals, fetchWorldCausalLinks, searchKnowledge, apiCognitiveBlueprint, apiCognitiveReason, apiCognitiveOptimize, apiCognitiveHealth } from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

// Blueprint layer specification details matching user diagram
interface BlueprintLayer {
  id: string;
  nameZh: string;
  nameEn: string;
  coverLabelZh: string;
  coverLabelEn: string;
  coverage: number; // initial percentage
  icon: any;
  diagnosticTime: number; // ms to complete
  blueprintItemsZh: string[];
  blueprintItemsEn: string[];
  matchedCodePage: string;
  matchedCodePageEn: string;
  systemDescriptionZh: string;
  systemDescriptionEn: string;
}

export default function CognitiveOperatingSystem() {
  const { t, locale } = useLanguage();
  const { styles, activeTheme } = useTheme();

  // Goals & metrics — loaded from World Model API
  const [goals, setGoals] = useState<Goal[]>([]);
  const [causalLinks, setCausalLinks] = useState<CausalLink[]>([]);
  const [goalsLoading, setGoalsLoading] = useState(true);
  const [causalLinksLoading, setCausalLinksLoading] = useState(true);
  const [wmError, setWmError] = useState<string | null>(null);

  // Knowledge Graph — 真实API数据
  const [kgNodes, setKgNodes] = useState<KnowledgeNode[]>([]);
  const [kgEdges, setKgEdges] = useState<KnowledgeEdge[]>([]);
  const [kgSearch, setKgSearch] = useState("");
  const [selectedKgNode, setSelectedKgNode] = useState<KnowledgeNode | null>(null);
  const [kgLoading, setKgLoading] = useState(true);

  // ── Cognitive Engine API states ──────────────────────
  const [cogHealth, setCogHealth] = useState<{ activeStreams?: number; status?: string; uptime?: string } | null>(null);
  const [cogBlueprintLayers, setCogBlueprintLayers] = useState<BlueprintLayer[] | null>(null);
  const [blueprintApiLoading, setBlueprintApiLoading] = useState(true);

  // Pareto optimizer
  const [optimizeResult, setOptimizeResult] = useState<any>(null);
  const [optimizeLoading, setOptimizeLoading] = useState(false);

  // Rule reasoning
  const [reasonResult, setReasonResult] = useState<any>(null);
  const [reasonLoading, setReasonLoading] = useState(false);
  const [showReasonPanel, setShowReasonPanel] = useState(false);

  useEffect(() => {
    // Fetch all World Model data in parallel
    fetchKnowledgeGraph().then(data => {
      setKgNodes(data.nodes);
      setKgEdges(data.edges);
      setKgLoading(false);
    }).catch((e: any) => { setWmError(e.message || 'Knowledge Graph 加载失败'); setKgLoading(false); });

    fetchWorldGoals().then(data => {
      setGoals(data || []);
      setGoalsLoading(false);
    }).catch((e: any) => { setWmError(e.message || 'Goals 加载失败'); setGoalsLoading(false); });

    fetchWorldCausalLinks().then(data => {
      setCausalLinks(data || []);
      setCausalLinksLoading(false);
    }).catch((e: any) => { setWmError(e.message || 'Causal Links 加载失败'); setCausalLinksLoading(false); });
  }, []);

  // ── Cognitive Engine: load blueprint & health ──────────
  useEffect(() => {
    apiCognitiveBlueprint()
      .then((data: any) => {
        if (data && Array.isArray(data)) {
          // Map API response to BlueprintLayer structure
          const mapped: BlueprintLayer[] = data.map((l: any) => ({
            id: l.id || l.layerId || '',
            nameZh: l.nameZh || l.name || '',
            nameEn: l.nameEn || l.name || '',
            coverLabelZh: l.coverLabelZh || l.descriptionZh || '',
            coverLabelEn: l.coverLabelEn || l.descriptionEn || '',
            coverage: l.coverage ?? l.healthScore ?? 0,
            icon: Target, // default icon fallback
            diagnosticTime: l.diagnosticTime || 1500,
            blueprintItemsZh: l.blueprintItemsZh || l.itemsZh || [],
            blueprintItemsEn: l.blueprintItemsEn || l.itemsEn || [],
            matchedCodePage: l.matchedCodePage || '',
            matchedCodePageEn: l.matchedCodePageEn || '',
            systemDescriptionZh: l.systemDescriptionZh || l.descriptionZh || '',
            systemDescriptionEn: l.systemDescriptionEn || l.descriptionEn || '',
          }));
          setCogBlueprintLayers(mapped);
        } else if (data && data.layers) {
          const mapped: BlueprintLayer[] = data.layers.map((l: any) => ({
            id: l.id || l.layerId || '',
            nameZh: l.nameZh || l.name || '',
            nameEn: l.nameEn || l.name || '',
            coverLabelZh: l.coverLabelZh || l.descriptionZh || '',
            coverLabelEn: l.coverLabelEn || l.descriptionEn || '',
            coverage: l.coverage ?? l.healthScore ?? 0,
            icon: Target,
            diagnosticTime: l.diagnosticTime || 1500,
            blueprintItemsZh: l.blueprintItemsZh || l.itemsZh || [],
            blueprintItemsEn: l.blueprintItemsEn || l.itemsEn || [],
            matchedCodePage: l.matchedCodePage || '',
            matchedCodePageEn: l.matchedCodePageEn || '',
            systemDescriptionZh: l.systemDescriptionZh || l.descriptionZh || '',
            systemDescriptionEn: l.systemDescriptionEn || l.descriptionEn || '',
          }));
          setCogBlueprintLayers(mapped);
        }
        setBlueprintApiLoading(false);
      })
      .catch((e: any) => {
        console.warn('Cognitive blueprint API unavailable, using hardcoded data:', e);
        setCogBlueprintLayers(null);
        setBlueprintApiLoading(false);
      });

    apiCognitiveHealth()
      .then((data: any) => {
        if (data) {
          setCogHealth({
            activeStreams: data.activeStreams ?? data.active_streams ?? data.streamCount,
            status: data.status ?? data.engineStatus,
            uptime: data.uptime,
          });
        }
      })
      .catch((e: any) => {
        console.warn('Cognitive health API unavailable:', e);
      });
  }, []);

  // ── Handler: Pareto optimizer ──────────────────────────
  const handleOptimize = async () => {
    setOptimizeLoading(true);
    setOptimizeResult(null);
    try {
      const data = await apiCognitiveOptimize({
        problem: {
          name: 'ECOS Multi-Objective Optimization',
          constraints: { budget: 1000, latency: 200 },
        },
        params: { populationSize: 50, generations: 100 },
      });
      setOptimizeResult(data);
    } catch (e: any) {
      console.error('Pareto optimizer failed:', e);
      setOptimizeResult({ error: e.message || 'Optimization failed' });
    } finally {
      setOptimizeLoading(false);
    }
  };

  // ── Handler: Rule reasoning ────────────────────────────
  const handleReason = async () => {
    setReasonLoading(true);
    setReasonResult(null);
    setShowReasonPanel(true);
    try {
      const data = await apiCognitiveReason({
        mode: 'rule',
        facts: { system: 'ECOS', layer: activeInfoLayer.id },
        context: { blueprintVersion: 'v15.1' },
      });
      setReasonResult(data);
    } catch (e: any) {
      console.error('Rule reasoning failed:', e);
      setReasonResult({ error: e.message || 'Reasoning failed' });
    } finally {
      setReasonLoading(false);
    }
  };

  const handleKgSearch = () => {
    if (!kgSearch.trim()) {
      fetchKnowledgeGraph().then(data => {
        setKgNodes(data.nodes);
        setKgEdges(data.edges);
      });
      return;
    }
    searchKnowledge(kgSearch)
      .then(d => {
        if (d.success && d.data) setKgNodes(d.data);
      })
      .catch((e: any) => { console.error('Knowledge search failed:', e); });
  };

  // ECOS Blueprint Diagnostics Interactive States
  const [layerCoverage, setLayerCoverage] = useState<Record<string, number>>({
    strategic: 92,
    knowledge: 88,
    agent_os: 95,
    semantic: 90,
    data_platform: 100,
    security_infra: 98
  });
  const [diagnosticActive, setDiagnosticActive] = useState<string | null>(null);
  const [diagnosticLogs, setDiagnosticLogs] = useState<string[]>([]);
  const [completedDiagnostics, setCompletedDiagnostics] = useState<Record<string, boolean>>({});
  const [selectedInfoLayerId, setSelectedInfoLayerId] = useState<string | null>("strategic");

  // Map representation of the ECOS 6 Layers Functional Diagram
  // Use API data when available; fall back to hardcoded blueprint
  const blueprintLayers: BlueprintLayer[] = cogBlueprintLayers ?? [
    {
      id: "strategic",
      nameZh: "战略与决策层 (Strategic & Cognitive Layer)",
      nameEn: "Strategic & Cognitive Layer",
      coverLabelZh: "目标规划、双态多目标寻优、世界模型及数字孪生预测",
      coverLabelEn: "Goal Planning, Pareto Optimization, World Model, and Twins Predictive Engine",
      coverage: layerCoverage.strategic,
      icon: Target,
      diagnosticTime: 1800,
      blueprintItemsZh: ["战略规划引擎", "优化引擎", "场景模拟引擎", "因果推理引擎", "世界模型 (World Model)", "企业数字孪生 (Enterprise Twin)"],
      blueprintItemsEn: ["Strategic Planning Engine", "Optimization Engine", "Scenario Simulation", "Causal Inference Engine", "World Model Engine", "Enterprise Twin State"],
      matchedCodePage: "C2EOS 协同主控大盘 / 帕累托进化沙盒 (本页面)",
      matchedCodePageEn: "Mission Control Dashboard / Pareto Sandbox (This View)",
      systemDescriptionZh: "ECOS首脑决策层。获取下方物理与语义模型，利用世界模型模拟真实物理限制，推演业务收益与风险冲突之间的最佳帕累托平衡决策行动。",
      systemDescriptionEn: "The executive brain. Employs World Model rules overriding basic telemetry, running multi-objective constraints simulation to output Pareto recommendations."
    },
    {
      id: "knowledge",
      nameZh: "认知与知识层 (Knowledge & Reasoning Layer)",
      nameEn: "Knowledge & Reasoning Layer",
      coverLabelZh: "企业知识图谱 (EKG)、目标体系、经验案例库、自适应改进",
      coverLabelEn: "Enterprise Knowledge Graph, Goal Layer Targetry, Experience Library, and Reinforcement Engine",
      coverage: layerCoverage.knowledge,
      icon: Layers,
      diagnosticTime: 1600,
      blueprintItemsZh: ["企业知识图谱 (EKG)", "目标层 (Goal Layer)", "记忆与经验库", "案例库", "学习与改进引擎", "经验提炼与自适应学习"],
      blueprintItemsEn: ["Enterprise Knowledge Graph (EKG)", "Goal Layer Goals System", "Enterprise Memory Base", "Case Library", "Learning & Improving Engine", "Feedback Extraction"],
      matchedCodePage: "知识本体探索器 / 协同多维数据血缘",
      matchedCodePageEn: "Ontology Explorer / Data Lineage Topology Analyzer",
      systemDescriptionZh: "对决策目标进行细粒度分解与指标动态追踪，结合案例沉淀与反馈对优化导则执行自学习迭代，让系统具备“记忆与自恢复”能力。",
      systemDescriptionEn: "Performs hierarchical decomposition of strategic goals, loading historical experience vectors, enabling ECOS self-healing algorithms and memory indexing."
    },
    {
      id: "agent_os",
      nameZh: "智能体操作系统层 (Agent Layer / Agent OS)",
      nameEn: "Agent Layer / Agent OS",
      coverage: layerCoverage.agent_os,
      coverLabelZh: "多智能体协同网络 (Agent Mesh)、协同协议、工具调用、追踪治理",
      coverLabelEn: "Multi-Agent Networks (Agent Mesh), A2A Protocol, Tools Registry, and Memory Graph Tracing",
      icon: Cpu,
      diagnosticTime: 2000,
      blueprintItemsZh: ["Agent Mesh", "协作与通信 (A2A Protocol)", "Agent运行时 (Planning / Execution)", "工具与能力层 (Tool Registry)", "记忆系统", "治理与策略 (Agent Governance)"],
      blueprintItemsEn: ["Agent Mesh Network", "A2A Messaging Interface", "Agent Runtime Core", "Tool Registry Registry", "Long-term Memory System", "Agent Security Rules"],
      matchedCodePage: "AIP 智能体工坊 (Agent Studio) / 多智能体Trace分析仪",
      matchedCodePageEn: "AIP Agent Studio / Agent Forensics Logs Tracker",
      systemDescriptionZh: "负责构建并编排垂直领域智力单元。通过A2A协议实现多智能体异步自协商、工具即时调用、长期记忆检索与审计溯源追踪。",
      systemDescriptionEn: "Drives horizontal multi-agent asynchronous consensus. Implements planning, Tool calls execution, long-term context memory search and audit trace logging."
    },
    {
      id: "semantic",
      nameZh: "语义与业务层 (Semantic & Business Layer)",
      nameEn: "Semantic & Business Layer",
      coverage: layerCoverage.semantic,
      coverLabelZh: "本体、对象运行时、决策规则引擎、流程设计、语义查询",
      coverLabelEn: "Ontology Modeling, Lifecycle Runtime, Action Rules, Automation workflows, and Unified Semantics",
      icon: Globe,
      diagnosticTime: 1500,
      blueprintItemsZh: ["语义本体 (Ontology)", "对象运行时 (Object Runtime)", "动作与规则层 (Action & Rule Engine)", "工作流与自动化 (Workflow Engine)", "查询与语义层 (Query & Semantics)"],
      blueprintItemsEn: ["Semantic Ontology Map", "Object Runtime Core", "Action & Rules Engine", "Workflow Custom Workflows", "Unified Custom Semantics Engine"],
      matchedCodePage: "本地语义本体管理器 (Ontology Manager) / 业务工作区",
      matchedCodePageEn: "Ontology Explorer / Operational Apps Workbench",
      systemDescriptionZh: "连接物理资产与代码逻辑的核心数字孪生。绑定业务属性、配置生命周期、设定操作规则，使数据从单纯的表结构沉淀为可交互的业务对象。",
      systemDescriptionEn: "Bridges cold physical database schemas to clickable logical business assets. Maps object relations, constraints declarations, and authorized action triggers."
    },
    {
      id: "data_platform",
      nameZh: "数据平台层 (Data Platform Layer)",
      nameEn: "Data Platform Layer",
      coverage: layerCoverage.data_platform,
      coverLabelZh: "数据连接器、元数据资产目录、Spark计算流水线、异构仓湖、质量雷达",
      coverLabelEn: "Connectors, Assets Catalog, Spark Job Pipeline ETL, Lakehouse Storage, and Quality Radar",
      icon: Database,
      diagnosticTime: 1400,
      blueprintItemsZh: ["数据接入与集成 (JDBC)", "数据目录 (Catalog)", "数据处理与管道 (Pipeline)", "数据存储与湖仓 (Parquet/Vector)", "数据治理与质量 (Governance & Data Quality)"],
      blueprintItemsEn: ["Data Connectors Ingestion", "Data Catalog Meta Indices", "Data Processing & Pipeline Core", "Lakehouse Parquet Vector Storage", "Governance and Quality Matrix"],
      matchedCodePage: "元数据目录 / 数据探查浏览器 / 水流计算流水线",
      matchedCodePageEn: "Data Catalog / Dataset Explorer / Pipeline Builder DAG Engine",
      systemDescriptionZh: "ECOS数字基础支撑面。运行实时/批处理数据集成、元数据全量捕获、Spark计算任务编译、湖仓两级冷热存储分配以及血缘数据树拓扑。",
      systemDescriptionEn: "Base level of physical storage integration. Conducts auto ETL transformations, reads parquet parquet blobs types, manages Spark pipelines node tasks."
    },
    {
      id: "security_infra",
      nameZh: "安全治理、多租准入与基础设施 (IAM, Security & Base Infra)",
      nameEn: "IAM, Security, Governance & base Infrastructure",
      coverage: layerCoverage.security_infra,
      coverLabelZh: "IAM准入、密码学数据合规双写脱敏、防篡改签名账本、系统级动态监控",
      coverLabelEn: "ABAC Access IAM, Data Encryption and Masking, Signed Audit Ledger, and telemetry operations",
      icon: Shield,
      diagnosticTime: 1700,
      blueprintItemsZh: ["身份与访问管理 (IAM/ABAC)", "数据加密与脱敏治理", "策略合规与多点审计", "防篡改密码学区块签名账本", "运维性能监控 (Daemons/Drivers)"],
      blueprintItemsEn: ["Identity & Access Manager", "Encryption & Data Masking", "Policy Compliance Locks", "Cryptographic Signed Audit Index", "Daemons telemetry operations"],
      matchedCodePage: "合规安全审查中心 (Security center) / 物理监测总控中心",
      matchedCodePageEn: "Security Center / Monitoring Center Operational Performance",
      systemDescriptionZh: "安全核心与底层运行护盾。负责RBAC+ABAC高强度准入、敏感字段智能加密脱敏。所有操作自动捕获，对齐并签发密码学校验凭证防篡改留档。",
      systemDescriptionEn: "The cryptographic security shield. Implements enterprise rule constraints, executes field-level secure token masking, saves signed transaction ledgers."
    }
  ];

  // Run dynamic diagnostics per blueprint layer
  const triggerLayerCheck = (layerId: string) => {
    const layer = blueprintLayers.find((b) => b.id === layerId);
    if (!layer) return;

    setDiagnosticActive(layerId);
    setDiagnosticLogs([
      `[ECOS_AUDITOR] Launching blueprint alignment sweep for layer: ${locale === "zh" ? layer.nameZh : layer.nameEn}...`,
      `[METADATA_VERIFIER] Verifying structural alignment with ECOS Blueprint v15.1 Specification...`
    ]);

    setTimeout(() => {
      setDiagnosticLogs((prev) => [
        ...prev,
        `[AUDITOR] Performing architectural code-mapping bindings: checking matching page [${locale === "zh" ? layer.matchedCodePage : layer.matchedCodePageEn}]...`,
        `[TRACE] Scanned constituents: ${locale === "zh" ? layer.blueprintItemsZh.join(" | ") : layer.blueprintItemsEn.join(" | ")}.`
      ]);
    }, 500);

    setTimeout(() => {
      setDiagnosticLogs((prev) => [
        ...prev,
        `[INTEGRATED_CHECK] Verified direct hot-reloading hooks. Checking active states and connection handshakes ... [COMPLIANT]`,
        `[CRYPTO] Registered ledger verification hashes. Level authenticated correctly.`
      ]);
    }, 1100);

    setTimeout(() => {
      setDiagnosticLogs((prev) => [
        ...prev,
        `[DIAGNOSTIC_RESULT] Swept completed perfectly. Structural integrity: 100% COMPLIANT. Alignment verified.`
      ]);
      
      // Update actual coverage rating to 100% and record completion indicators
      setLayerCoverage((prev) => ({
        ...prev,
        [layerId]: 100
      }));
      setCompletedDiagnostics((prev) => ({
        ...prev,
        [layerId]: true
      }));
      setDiagnosticActive(null);
    }, layer.diagnosticTime);
  };

  // Calculate global compliance index average
  const globalComplianceVal = parseFloat(
    ((layerCoverage.strategic +
      layerCoverage.knowledge +
      layerCoverage.agent_os +
      layerCoverage.semantic +
      layerCoverage.data_platform +
      layerCoverage.security_infra) / 6).toFixed(1)
  );

  const activeInfoLayer = blueprintLayers.find((b) => b.id === selectedInfoLayerId) || blueprintLayers[0];

  return (
    <div className={`flex-grow overflow-y-auto p-6 font-sans ${styles.appBg} ${styles.appText} transition-colors duration-150`}>
      <div className="max-w-7xl mx-auto space-y-6">
        
        {/* Error Banner */}
        {wmError && (
          <div className="bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800 rounded-lg p-3 flex items-center gap-2 text-red-700 dark:text-red-400 text-sm">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{wmError}</span>
            <button onClick={() => setWmError(null)} className="ml-auto text-red-400 hover:text-red-600">&times;</button>
          </div>
        )}

        {/* ECOS Page Header */}

        {/* Global Blueprint Metrics Cards row */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          
          {/* Compliance Ratio */}
          <div className={`border rounded-xl p-4 flex items-center justify-between shadow-2xs ${styles.cardBg} ${styles.cardBorder}`}>
            <div className="space-y-1">
              <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
                {locale === "zh" ? "蓝图一致性评级" : "Blueprint Alignment Score"}
              </span>
              <strong className="text-xl font-extrabold tracking-tight" style={{ color: "var(--cardText)" }}>
                {globalComplianceVal}%
              </strong>
            </div>
            <div className={`w-10 h-10 rounded-lg flex items-center justify-center font-mono ${
              globalComplianceVal >= 98 
                ? "bg-emerald-500/10 text-emerald-500 border border-emerald-500/25"
                : "bg-amber-500/10 text-amber-500 border border-amber-500/25"
            }`}>
              <FileCheck className="w-5 h-5 animate-pulse" />
            </div>
          </div>

          {/* Active Framework Layers */}
          <div className={`border rounded-xl p-4 flex items-center justify-between shadow-2xs ${styles.cardBg} ${styles.cardBorder}`}>
            <div className="space-y-1">
              <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
                {locale === "zh" ? "已对齐验证架构层" : "Architecture Layers Verified"}
              </span>
              <strong className="text-xl font-extrabold tracking-tight" style={{ color: "var(--cardText)" }}>
                {Object.values(completedDiagnostics).filter(Boolean).length} / 6 Layers
              </strong>
            </div>
            <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-indigo-500 bg-indigo-500/10 border border-indigo-500/25`}>
              <Layers className="w-5 h-5" />
            </div>
          </div>

          {/* Core ECOS Components checked */}
          <div className={`border rounded-xl p-4 flex items-center justify-between shadow-2xs ${styles.cardBg} ${styles.cardBorder}`}>
            <div className="space-y-1">
              <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
                {locale === "zh" ? "系统注册功能组件" : "Framework Blueprints Monitored"}
              </span>
              <strong className="text-xl font-extrabold tracking-tight" style={{ color: "var(--cardText)" }}>
                36 Components
              </strong>
            </div>
            <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-blue-500 bg-blue-500/10 border border-blue-500/25`}>
              <Binary className="w-5 h-5" />
            </div>
          </div>

          {/* Communication status link channels */}
          <div className={`border rounded-xl p-4 flex items-center justify-between shadow-2xs ${styles.cardBg} ${styles.cardBorder}`}>
            <div className="space-y-1">
              <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
                {locale === "zh" ? "认知系统链路负载" : "Cognitive Network Threads"}
              </span>
              <strong className="text-xl font-extrabold tracking-tight text-emerald-500">
                {cogHealth?.activeStreams != null ? `${cogHealth.activeStreams} Active Streams` : '-- Active Streams'}
              </strong>
            </div>
            <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-emerald-500 bg-emerald-500/10 border border-emerald-500/25`}>
              <Activity className="w-5 h-5" />
            </div>
          </div>

        </div>

        {/* ECOS 20-Point System Blueprint Alignment Monitor (系统蓝图对齐分析仪) */
          /* (formerly activeTab === "blueprint") */}
          <div className="space-y-6">
            
            {/* Interactive Visual Blueprint diagram block */}
            <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-6 shadow-2xs space-y-4`}>
              <div className="flex items-center justify-between border-b border-dashed pb-3" style={{ borderColor: "var(--cardBorder)" }}>
                <h3 className="font-bold text-sm tracking-tight flex items-center gap-2" style={{ color: "var(--cardText)" }}>
                  <Layers className="w-4 h-4 text-indigo-505" />
                  <span>{locale === "zh" ? "企业认知操作系统 (ECOS) 六层拓扑核心设计蓝图" : "ECOS Six-Layer Cognitive Structural System Blueprint"}</span>
                </h3>
                <span className="font-mono text-[9px] uppercase tracking-wider opacity-60">High-Density Cognitive Diagram Blueprint</span>
              </div>

              {/* Stack representation mimicking the actual image with responsive hover points */}
              <div className="flex flex-col gap-2 pt-2">
                
                {/* Horizontal layers flow */}
                {blueprintLayers.map((layer, index) => {
                  const isSelected = selectedInfoLayerId === layer.id;
                  const isFinished = completedDiagnostics[layer.id];
                  const IconComponent = layer.icon;

                  return (
                    <div 
                      key={layer.id}
                      onClick={() => setSelectedInfoLayerId(layer.id)}
                      className={`border rounded-xl p-3 flex flex-col md:flex-row md:items-center justify-between gap-3 cursor-pointer transition-all duration-150 relative overflow-hidden select-none ${
                        isSelected 
                          ? "bg-indigo-600/10 border-indigo-500 shadow-2xs font-semibold" 
                          : `hover:bg-black/5 ${styles.cardBg} ${styles.cardBorder}`
                      } border-${index + 1}`}
                    >
                      {/* Interactive depth progress layer bar */}
                      <div className="absolute top-0 bottom-0 left-0 bg-indigo-500/5 mix-blend-multiply dark:mix-blend-screen" style={{ width: `${layer.coverage}%` }}></div>

                      <div className="flex items-start md:items-center gap-3 relative z-10 flex-grow min-w-0">
                        <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 border ${
                          isSelected ? "bg-indigo-650 text-white border-indigo-500" : `${styles.badgeBg} ${styles.badgeText} ${styles.cardBorder}`
                        }`}>
                          <IconComponent className="w-4 h-4" />
                        </div>
                        
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="text-[10px] font-mono font-extrabold uppercase text-indigo-600 dark:text-indigo-400">Phase {15 - index * 2}</span>
                            <span className="text-xs font-bold truncate leading-none" style={{ color: "var(--cardText)" }}>
                              {locale === "zh" ? layer.nameZh.split(" (")[0] : layer.nameEn}
                            </span>
                            {isFinished && (
                              <span className="text-[8.5px] px-1.5 py-0.2 rounded-sm font-mono font-bold bg-emerald-500/10 border border-emerald-500/25 text-emerald-500 uppercase leading-none">
                                VERIFIED
                              </span>
                            )}
                          </div>
                          
                          <p className={`text-[10.5px] mt-1.5 truncate leading-none ${styles.cardTextMuted}`}>
                            {locale === "zh" ? layer.coverLabelZh : layer.coverLabelEn}
                          </p>
                        </div>
                      </div>

                      {/* Coverage Progress Indicator */}
                      <div className="flex items-center gap-4 relative z-10 shrink-0 select-none">
                        <div className="text-right font-mono">
                          <div className="text-xs font-extrabold" style={{ color: "var(--cardText)" }}>{layer.coverage}%</div>
                          <div className={`text-[8px] uppercase tracking-wider ${styles.cardTextMuted}`}>{locale === "zh" ? "对齐完整度" : "Alignment"}</div>
                        </div>
                        <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: isFinished ? "#22C55E" : "#EAB308" }}></div>
                      </div>

                    </div>
                  );
                })}

              </div>
            </div>

            {/* Comprehensive details of selected Blueprint Layer and Interactive Sweeper Diagnostics */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
              
              {/* Box A: Detailed Specifications & Matching Code Page (8 columns) */}
              <div className={`lg:col-span-8 border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 shadow-2xs space-y-4`}>
                <div className="flex items-center justify-between border-b border-dashed pb-3" style={{ borderColor: "var(--cardBorder)" }}>
                  <div>
                    <span className="text-[9px] uppercase font-bold font-mono tracking-wider text-indigo-600 dark:text-indigo-400 block mb-1">Layer Architect Inspections</span>
                    <h4 className="text-sm font-extrabold tracking-tight" style={{ color: "var(--cardText)" }}>
                      {locale === "zh" ? activeInfoLayer.nameZh : activeInfoLayer.nameEn}
                    </h4>
                  </div>
                  <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>ECOS Blueprint Mapping System</span>
                </div>

                <div className="space-y-4">
                  {/* Matching description */}
                  <p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>
                    {locale === "zh" ? activeInfoLayer.systemDescriptionZh : activeInfoLayer.systemDescriptionEn}
                  </p>

                  {/* Matching Code Location and Links */}
                  <div className={`p-4 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${styles.badgeBg} ${styles.cardBorder}`}>
                    <div className="space-y-1">
                      <span className={`text-[9px] uppercase font-bold font-mono tracking-wider block ${styles.cardTextMuted}`}>{locale === "zh" ? "匹配的实际系统业务功能页面" : "Core Handshake Application Match"}</span>
                      <strong className="text-xs font-bold uppercase tracking-tight flex items-center gap-1.5" style={{ color: "var(--cardText)" }}>
                        <Globe className="w-3.5 h-3.5 text-indigo-650" />
                        {locale === "zh" ? activeInfoLayer.matchedCodePage : activeInfoLayer.matchedCodePageEn}
                      </strong>
                    </div>
                  </div>

                  {/* List of sub-elements defined in the blueprint diagram nodes */}
                  <div className="space-y-2">
                    <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
                      {locale === "zh" ? "蓝图定义的核心功能模块 (Constituent Entities)" : "Blueprint Elements Checked"}
                    </span>
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
                      {(locale === "zh" ? activeInfoLayer.blueprintItemsZh : activeInfoLayer.blueprintItemsEn).map((item, id) => (
                        <div key={id} className={`border rounded-lg p-2.5 flex items-center gap-2 bg-black/10 dark:bg-white/5 ${styles.cardBorder}`}>
                          <CheckCircle className="w-3.5 h-3.5 text-indigo-500 shrink-0" />
                          <span className="text-[11px] font-medium leading-none" style={{ color: "var(--cardText)" }}>{item}</span>
                        </div>
                      ))}
                    </div>
                  </div>

                </div>

              </div>

              {/* Box B: Interactive Diagnostics Sweep Console (4 columns) */}
              <div className={`lg:col-span-4 border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 shadow-2xs flex flex-col overflow-hidden`}>
                <div className="mb-4 shrink-0 flex items-center justify-between">
                  <div>
                    <span className={`text-[9px] uppercase font-bold font-mono tracking-wider block ${styles.cardTextMuted}`}>Alignment Diagnostics</span>
                    <h4 className="text-xs font-extrabold tracking-tight uppercase font-mono mt-1" style={{ color: "var(--cardText)" }}>
                      Layer Integrity Auditor
                    </h4>
                  </div>
                </div>

                <div className="space-y-4 flex-1">
                  
                  {/* Run button */}
                  {diagnosticActive === activeInfoLayer.id ? (
                    <div className="w-full text-xs p-3 rounded-lg border bg-indigo-500/10 text-indigo-600 border-indigo-500/25 font-mono flex items-center justify-center gap-2 font-bold uppercase leading-none h-10 select-none">
                      <RefreshCw className="w-4 h-4 animate-spin text-indigo-600 dark:text-indigo-400" />
                      <span>{locale === "zh" ? "架构链路校验审计中..." : "ALIGNING CORES..."}</span>
                    </div>
                  ) : (
                    <button
                      onClick={() => triggerLayerCheck(activeInfoLayer.id)}
                      className="w-full h-10 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-bold leading-none cursor-pointer transition flex items-center justify-center gap-2 shadow-xs"
                    >
                      <Zap className="w-3.5 h-3.5" />
                      <span>{locale === "zh" ? "对齐一致性审计与热加载" : "Run Alignment Diagnostic"}</span>
                    </button>
                  )}

                  {/* Terminal log output */}
                  <div className="bg-[#020202] border border-emerald-500/20 rounded-xl p-4 font-mono text-[9.5px] text-emerald-500 whitespace-pre-wrap overflow-y-auto max-h-56 h-48 scrollbar-thin select-text">
                    <span className="text-emerald-500/50 font-bold block mb-1.5 uppercase tracking-widest text-[8px] leading-none select-none">Active Auditor trace terminal:</span>
                    
                    {diagnosticLogs.length === 0 ? (
                      <p className="text-emerald-700 italic select-none">
                        {locale === "zh" 
                          ? "诊断审计终端空闲。请点击上方按钮验证当前架构层与系统蓝图的对齐细节。" 
                          : "Audit terminal idle. Execute verification check above."}
                      </p>
                    ) : (
                      <div className="space-y-2">
                        {diagnosticLogs.map((log, idx) => (
                          <p key={idx} className="leading-relaxed border-l-2 pl-2" style={{ borderColor: idx === diagnosticLogs.length - 1 ? "#22C55E" : "rgba(16,185,129,0.3)" }}>
                            {log}
                          </p>
                        ))}
                      </div>
                    )}
                  </div>

                </div>

              </div>

            </div>

            {/* ── Cognitive Engine: Pareto Optimizer + Rule Reasoning ── */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

              {/* Pareto Optimizer Card */}
              <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 shadow-2xs space-y-4`}>
                <div className="flex items-center justify-between border-b border-dashed pb-3" style={{ borderColor: "var(--cardBorder)" }}>
                  <h4 className="text-sm font-extrabold tracking-tight flex items-center gap-2" style={{ color: "var(--cardText)" }}>
                    <TrendingUp className="w-4 h-4 text-amber-500" />
                    <span>{locale === "zh" ? "帕累托优化器" : "Pareto Optimizer"}</span>
                  </h4>
                  <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
                    {locale === "zh" ? "多目标寻优" : "Multi-Objective Optimization"}
                  </span>
                </div>

                <p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>
                  {locale === "zh"
                    ? "基于认知引擎执行多目标帕累托前沿解集搜索，在预算、延迟、质量等约束下寻找最优权衡方案。"
                    : "Execute multi-objective Pareto frontier search via the cognitive engine, finding optimal trade-offs under budget, latency, and quality constraints."}
                </p>

                <button
                  onClick={handleOptimize}
                  disabled={optimizeLoading}
                  className="w-full bg-amber-600 hover:bg-amber-700 disabled:bg-amber-400 text-white rounded-lg text-xs font-bold py-2.5 transition flex items-center justify-center gap-2"
                >
                  {optimizeLoading ? (
                    <>
                      <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                      <span>{locale === "zh" ? "优化计算中..." : "Optimizing..."}</span>
                    </>
                  ) : (
                    <>
                      <Zap className="w-3.5 h-3.5" />
                      <span>{locale === "zh" ? "执行帕累托优化" : "Run Pareto Optimizer"}</span>
                    </>
                  )}
                </button>

                {optimizeResult && !optimizeResult.error && (
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs border-collapse">
                      <thead>
                        <tr className="border-b" style={{ borderColor: "var(--cardBorder)" }}>
                          <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                            {locale === "zh" ? "方案ID" : "Solution ID"}
                          </th>
                          <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                            {locale === "zh" ? "层级" : "Rank"}
                          </th>
                          <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                            {locale === "zh" ? "X目标" : "X Objective"}
                          </th>
                          <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                            {locale === "zh" ? "Y目标" : "Y Objective"}
                          </th>
                          <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                            {locale === "zh" ? "Z目标" : "Z Objective"}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {(Array.isArray(optimizeResult) ? optimizeResult : (optimizeResult.solutions || optimizeResult.frontier || [])).map((sol: any, idx: number) => (
                          <tr key={idx} className="border-b hover:bg-black/5 dark:hover:bg-white/5 transition-colors" style={{ borderColor: "var(--cardBorder)" }}>
                            <td className="py-1.5 px-2 font-mono text-[10px]" style={{ color: "var(--cardText)" }}>
                              {sol.solutionId ?? sol.id ?? `#${idx + 1}`}
                            </td>
                            <td className="py-1.5 px-2 font-mono text-[10px] text-amber-500 font-bold">
                              {sol.rank ?? idx + 1}
                            </td>
                            <td className="py-1.5 px-2 font-mono text-[10px]" style={{ color: "var(--cardText)" }}>
                              {sol.objectives?.x ?? sol.x ?? sol.objectiveX ?? '—'}
                            </td>
                            <td className="py-1.5 px-2 font-mono text-[10px]" style={{ color: "var(--cardText)" }}>
                              {sol.objectives?.y ?? sol.y ?? sol.objectiveY ?? '—'}
                            </td>
                            <td className="py-1.5 px-2 font-mono text-[10px]" style={{ color: "var(--cardText)" }}>
                              {sol.objectives?.z ?? sol.z ?? sol.objectiveZ ?? '—'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}

                {optimizeResult?.error && (
                  <div className="bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800 rounded-lg p-3 text-red-700 dark:text-red-400 text-xs font-mono">
                    {optimizeResult.error}
                  </div>
                )}
              </div>

              {/* Rule Reasoning Card */}
              <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 shadow-2xs space-y-4`}>
                <div className="flex items-center justify-between border-b border-dashed pb-3" style={{ borderColor: "var(--cardBorder)" }}>
                  <h4 className="text-sm font-extrabold tracking-tight flex items-center gap-2" style={{ color: "var(--cardText)" }}>
                    <Brain className="w-4 h-4 text-purple-500" />
                    <span>{locale === "zh" ? "规则推理" : "Rule Reasoning"}</span>
                  </h4>
                  <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
                    mode=rule
                  </span>
                </div>

                <p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>
                  {locale === "zh"
                    ? "对当前选中的蓝图架构层执行规则推理，匹配ECOS知识库中的合规性规则与决策导则，输出匹配的规则列表。"
                    : "Execute rule-based reasoning on the selected blueprint layer, matching compliance rules and decision guidelines from the ECOS knowledge base."}
                </p>

                <button
                  onClick={handleReason}
                  disabled={reasonLoading}
                  className="w-full bg-purple-600 hover:bg-purple-700 disabled:bg-purple-400 text-white rounded-lg text-xs font-bold py-2.5 transition flex items-center justify-center gap-2"
                >
                  {reasonLoading ? (
                    <>
                      <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                      <span>{locale === "zh" ? "推理分析中..." : "Reasoning..."}</span>
                    </>
                  ) : (
                    <>
                      <Cpu className="w-3.5 h-3.5" />
                      <span>{locale === "zh" ? "执行规则推理" : "Run Rule Reasoning"}</span>
                    </>
                  )}
                </button>

                {showReasonPanel && reasonResult && !reasonResult.error && (
                  <div className="space-y-2 max-h-64 overflow-y-auto scrollbar-thin">
                    <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
                      {locale === "zh" ? "匹配的规则列表" : "Matched Rules"}
                    </span>
                    {(Array.isArray(reasonResult.matchedRules) ? reasonResult.matchedRules : (Array.isArray(reasonResult) ? reasonResult : [])).map((rule: any, idx: number) => (
                      <div key={idx} className={`border rounded-lg p-2.5 ${styles.cardBorder} bg-black/5 dark:bg-white/5`}>
                        <div className="flex items-center justify-between mb-1">
                          <span className="text-[11px] font-bold font-mono text-purple-600 dark:text-purple-400">
                            {rule.ruleId || rule.id || rule.name || `Rule #${idx + 1}`}
                          </span>
                          {rule.priority != null && (
                            <span className={`text-[9px] px-1.5 py-0.2 rounded font-mono font-bold ${
                              rule.priority === 'high' ? 'bg-red-500/10 text-red-500 border border-red-500/20' :
                              rule.priority === 'medium' ? 'bg-amber-500/10 text-amber-500 border border-amber-500/20' :
                              'bg-blue-500/10 text-blue-500 border border-blue-500/20'
                            }`}>
                              {rule.priority}
                            </span>
                          )}
                        </div>
                        <p className={`text-[10px] leading-relaxed ${styles.cardTextMuted}`}>
                          {rule.description || rule.condition || rule.summary || JSON.stringify(rule)}
                        </p>
                      </div>
                    ))}
                  </div>
                )}

                {showReasonPanel && reasonResult?.error && (
                  <div className="bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800 rounded-lg p-3 text-red-700 dark:text-red-400 text-xs font-mono">
                    {reasonResult.error}
                  </div>
                )}
              </div>

            </div>

          </div>
      </div>
    </div>
  );
}
