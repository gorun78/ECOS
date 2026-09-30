/**
 * blueprintData — ECOS 六层蓝图数据模型：BlueprintLayer 类型、API 映射纯函数、硬编码兜底蓝图。
 * 由 CognitiveOperatingSystem.tsx 机械抽取（PMO-74 H6-T4），字段映射与兜底数组内容逐行一致；
 * 原数组内联引用组件内 layerCoverage state，改为纯函数入参（同名同用法）。
 * @license Apache-2.0
 */
import { Target, Layers, Cpu, Globe, Database, Shield } from "lucide-react";

// Blueprint layer specification details matching user diagram
export interface BlueprintLayer {
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

/** Map one cognitive-engine API layer record to BlueprintLayer (extracted from the load effect; both original branches shared this identical body). */
export function mapApiBlueprintLayer(l: any): BlueprintLayer {
  return {
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
  };
}

/** Map representation of the ECOS 6 Layers Functional Diagram — hardcoded fallback used when the API gives no data. */
export function buildFallbackBlueprintLayers(layerCoverage: Record<string, number>): BlueprintLayer[] {
  return [
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
}
