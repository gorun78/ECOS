/**
 * types.ts — 沙盘 (Scenario Sandbox) 节点/边/布局 schema
 *
 * 对齐：
 *   - backend SandboxSaveDTO（POST /api/v1/workspace/scenarios/{id}/sandbox/layout）
 *   - V150 sandbox_layout.layout_jsonb（实际落库 schema）
 *
 * 4 类节点 / 4 类边：resource/hub/mind/insight，bind/inherit/feed/insight。
 *
 * @license Apache-2.0
 */

// ── 枚举 ──────────────────────────────────────────────────

/** 沙盘节点类型 */
export type SdBNodeType = "resource" | "hub" | "mind" | "insight";

/** 6 类资源分类（与 §2.7 后端 available/{cat} 端点分类对齐） */
export type ResourceCategory =
  | "DATASOURCE"
  | "ONTOLOGY_ENTITY"
  | "KNOWLEDGE_ARTICLE"
  | "AGENT_PROFILE"
  | "SECURITY_POLICY"
  | "INTERFACE_REF";

/** 认知四件套端点（diagnose/forecast/simulate/policy） */
export type CognitiveEp = "diagnose" | "forecast" | "simulate" | "policy";

/** 资源子类状态点（ok/warn/error） */
export type SdBResourceStatus = "ok" | "warn" | "error";

/** 场景运行状态（hub 用） */
export type SdBScenarioStatus = "DRAFT" | "ACTIVE" | "SUSPENDED" | "COMPLETED";

/** 节点状态（与 hub 是否已绑定） */
export type SdBNodeStatus = "idle" | "linked" | "orphan";

// ── 节点 data 形态（type 字段决定）────────────────────────

/**
 * Resource 节点 data 形态
 * - category：6 类资源分类
 * - targetId：真实资源 ID（hub bind 时回填）
 * - targetName：展示名
 * - childCount：子项数（hub 视图）
 * - status：ok/warn/error 三态 stat dot
 */
export interface SdBResourceData {
  category: ResourceCategory;
  targetId: string;
  targetName: string;
  childCount?: number;
  status?: SdBResourceStatus;
}

/**
 * Hub 节点 data 形态（每场景一个中央枢纽）
 * - scenarioId：场景 ID
 * - status：场景运行状态（与 Scenario.status 对齐）
 * - activeMindId：当前激活心智 id
 * - beliefProb：不确定性概率条（动态 length，默认 4 档）
 * - beliefsVariable：展示用的变量名（如 "lead_time_prob"）
 * - epEnabled：认知四件套开关
 */
export interface SdBHubData {
  scenarioId: string;
  status: SdBScenarioStatus;
  activeMindId?: string;
  beliefProb: number[];
  beliefsVariable: string;
  epEnabled: Record<CognitiveEp, boolean>;
}

/**
 * Mind 变体卡片节点 data 形态
 * - mindId：心智 variant id
 * - label：显示名（继承自 base 或新 label）
 * - active：是否激活
 * - threeFactorSummary：三要素摘要（推测/偏差/溯源 短文本）
 */
export interface SdBMindData {
  mindId: string;
  label: string;
  active: boolean;
  threeFactorSummary: string;
}

/**
 * Insight 卡片节点 data 形态（P3b 激活，本期 stub）
 * - ep：哪件套
 * - summary：摘要文本
 * - payloadHash：hash 留痕
 * - ok：是否成功
 */
export interface SdBInsightData {
  ep: CognitiveEp;
  summary: string;
  payloadHash: string;
  ok: boolean;
}

// ── SdBNode：ReactFlow 通用节点包装 ───────────────────────

/** 沙盘节点（包装 data 形态，type 决定形态消费方） */
export interface SdBNode {
  id: string;
  position: { x: number; y: number };
  /** 节点类型（决定 data 形态：resource/hub/mind/insight） */
  type: SdBNodeType;
  /** data 按 type 字段实际形态消费，外部用 SdBResourceData / SdBHubData / SdBMindData / SdBInsightData */
  data:
    | SdBResourceData
    | SdBHubData
    | SdBMindData
    | SdBInsightData;
  /** selected 态由 ReactFlow 自管，业务层不存 */
  selected?: boolean;
  /** 锚点（ReactFlow 内部 placement） */
  measured?: { width?: number; height?: number };
}

/** 沙盘边 4 类 */
export type SdBEdgeKind = "bind" | "inherit" | "feed" | "insight";

/** 沙盘边 */
export interface SdBEdge {
  id: string;
  source: string;
  target: string;
  kind: SdBEdgeKind;
  label?: string;
  animated?: boolean;
}

/** 视口（与 @xyflow/react 的 Viewport 对齐） */
export interface SdViewport {
  x: number;
  y: number;
  zoom: number;
}

/** 完整布局（对齐 backend SandboxSaveDTO） */
export interface SdLayout {
  nodes: SdBNode[];
  edges: SdBEdge[];
  viewport: SdViewport;
}

// ── 保存相关 ───────────────────────────────────────────────

/** saveRequest body（expectedVersion 走乐观锁） */
export interface SdLayoutSaveRequest {
  nodes: SdBNode[];
  edges: SdBEdge[];
  viewport: SdViewport;
  expectedVersion: number;
}

/** 保存错误：版本号冲突 */
export interface SdLayoutSaveConflict {
  kind: "VERSION_CONFLICT";
  /** 服务端最新版本号 */
  serverVersion: number;
}

/** Mind variant（POST /minds / PATCH /minds/{id} 形态） */
export interface SdMindVariant {
  mindId: string;
  label: string;
  active: boolean;
  inheritedFrom?: string;
  confidence?: number;
  createdAt?: string;
}

/** Mind base（兼容端点） */
export interface SdMindBase {
  mindId: string;
  label: string;
  businessDomain: string;
  variableOfInterest: string;
  createdAt?: string;
}

/** 资源可用项（6 类 available 端点返回） */
export interface SdResourceItem {
  id: string;
  name: string;
  /** 子项数（hub 视图填充） */
  childCount?: number;
  /** 资源状态点 */
  status?: SdBResourceStatus;
}
