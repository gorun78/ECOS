/**
 * navConfig — 导航唯一事实源（详设-08 W190/C172，R-51① 一次到位）
 *
 * 单一来源派生三张消费表：
 *  - SIDEBAR_GROUPS / SIDEBAR_PRODUCT_ITEMS — components/Sidebar.tsx
 *  - TAB_LABEL_KEY / resolveTabLabel        — App.tsx 工作区标签条
 *  - PALETTE_ITEMS / TYPE_META_MAP          — components/CommandPalette.tsx
 *
 * 消费方不得再持有私有映射表。口径说明见《详细设计-08》§一.2：
 * 内容路由根共 52 条（实测 main.tsx），其中进入 NAV_CONFIG 的有 45 项，
 * 其余 15 根在 DELIBERATELY_UNLINKED 白名单中逐一登记理由（R-52① 口径：
 * 待产品定档，本轮不擅自补入口也不下线）。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import type { ComponentType } from "react";
import {
  Activity,
  BookOpen,
  Briefcase,
  ClipboardList,
  Command,
  Compass,
  Cpu,
  Database,
  Gauge,
  GitBranch,
  Globe,
  Layers,
  LayoutDashboard,
  Network,
  Settings2,
  Shield,
  Table2,
} from "lucide-react";

export type IconComponent = ComponentType<{ className?: string }>;

export type SidebarGroupId = "overview" | "workspaces" | "resources" | "control";

/** 命令面板静态项的 i18n 键包（语料在 locales/navigation/*，非 common ns）。 */
export interface PaletteMeta {
  titleKey: string;
  descKey: string;
  categoryKey: string;
}

export interface NavEntry {
  /** 路由根 id（= URL 第一段，如 "security-center"）。 */
  id: string;
  /** 完整导航路径（Sidebar/Palette/Tab 统一 navigate(route)）。 */
  route: string;
  /** 标签 i18n key；必须真实存在于 locales 语料（门禁 noSilentFallbackToRawKey）。 */
  labelKey: string;
  /** 归属 Sidebar 分组；缺省 = 不进 Sidebar。 */
  group?: SidebarGroupId;
  /** Sidebar 副标题 i18n key（分组项必填）。 */
  descKey?: string;
  /** lucide 图标（Sidebar 项与命令面板项必填）。 */
  icon?: IconComponent;
  /** 是否参与工作区标签条（TAB_LABEL_KEY）。 */
  tabbable: boolean;
  /** 命令面板静态入口（存在即进 PALETTE_ITEMS）。 */
  palette?: PaletteMeta;
  /** 全局搜索命中该条目时展示的分类 i18n key（TYPE_META_MAP）。 */
  categoryKey?: string;
  /** 全局搜索命中类型 → 反查导航目标（TYPE_NAV_MAP / TYPE_META_MAP）。 */
  searchTypes?: string[];
  /** 可见角色（预留，本轮未启用）。 */
  roles?: string[];
}

/** Sidebar 分组项：icon/descKey 必填，构造时校验（fail-fast 单源纪律）。 */
export interface SidebarEntry extends NavEntry {
  icon: IconComponent;
  descKey: string;
}

export interface SidebarGroup {
  groupKey: string;
  items: SidebarEntry[];
}

const GROUP_KEYS: Record<SidebarGroupId, string> = {
  overview: "sidebar.group.overview",
  workspaces: "sidebar.group.workspaces",
  resources: "sidebar.group.resources",
  control: "sidebar.group.control",
};

const p = (id: string, categoryKey: string): PaletteMeta => ({
  titleKey: `palette.item.${id}.title`,
  descKey: `palette.item.${id}.desc`,
  categoryKey,
});

const CAT = {
  data: "palette.category.data_platform",
  knowledge: "palette.category.knowledge_layer",
  cognitive: "palette.category.cognitive_layer",
  security: "palette.category.security_audit",
} as const;

/**
 * NAV_CONFIG — 47 项（2026-10-14 菜单重构：+decision / +resource-center /
 * +platform-governance），与既有 TAB_LABEL_KEY 一一对应。
 * 前 13 项带 group = Sidebar 入口（4 组：总览/工作区/资源概览/控制面板）；
 * 7 项 vestigial（security/catalog/dataset_explorer/pipeline/workbook/
 * lineage/datasources）当前无根路由，但保留 tabbable 以维持既有标签行为
 * （route 为意向路径，门禁方向是 main.tsx→NAV_CONFIG 成员检查，不要求反向可达）。
 *
 * 2026-10-14 结构调整（用户裁决）：
 *  - 总览组只留「决策面板」(新页 #/decision)；world_model / monitor 摘除 group
 *    （保留 tabbable + URL 可达，菜单出口移除，非下线）。
 *  - 新增「工作区」组：五个工作台按 场景→AI→知识→本体→数据 顺序。
 *  - 资源概览组只留「资源中心」(新页 #/resource-center)；marketplace /
 *    knowledge_graph / ops_apps 摘除 group（同上，保留 URL 可达）。
 *  - 「系统管理」组拆除，「产品功能」折叠组退役 → 合并为「控制面板」组：
 *    安全中心 / 任务中心(engine-tasks) / 监控中心 / 数据字典 / 平台治理(新页)。
 */
export const NAV_ENTRIES: readonly NavEntry[] = [
  // ── Sidebar：总览组 ─────────────────────────────────────────────
  { id: "decision", route: "/decision", labelKey: "app.tab.decision", group: "overview", descKey: "sidebar.desc.decision", icon: Compass, tabbable: true },
  // ── Sidebar：工作区组（顺序：场景→AI→知识→本体→数据，用户裁决）─────────
  // 2026-10-14 用户裁决：场景工作台入口指向 L1「我的场景」（#/workbench，详设-07），
  // 非 /project_workbench 高级技术视图（ScenarioManagementView，保留可达，不在本组出项）。
  { id: "workbench", route: "/workbench", labelKey: "app.tab.workbench", group: "workspaces", descKey: "sidebar.desc.workbench", icon: Briefcase, tabbable: true },
  { id: "agent_studio", route: "/agent_studio", labelKey: "app.tab.ai_workbench", group: "workspaces", descKey: "sidebar.desc.agent_studio", icon: Cpu, tabbable: true, palette: p("agent_studio", CAT.cognitive), searchTypes: ["Agent"] },
  { id: "knowledge_view", route: "/knowledge_view", labelKey: "app.tab.knowledge_view", group: "workspaces", descKey: "sidebar.desc.knowledge_view", icon: BookOpen, tabbable: true },
  { id: "ontology_workbench", route: "/ontology_workbench", labelKey: "app.tab.ontology_workbench", group: "workspaces", descKey: "sidebar.desc.ontology_workbench", icon: Network, tabbable: true },
  { id: "data-workbench", route: "/data-workbench", labelKey: "app.tab.data_workbench", group: "workspaces", descKey: "sidebar.desc.data_workbench", icon: LayoutDashboard, tabbable: true },
  // ── Sidebar：资源概览组 ─────────────────────────────────────────
  { id: "resource-center", route: "/resource-center", labelKey: "app.tab.resource_center", group: "resources", descKey: "sidebar.desc.resource_center", icon: Layers, tabbable: true, searchTypes: ["Asset", "Knowledge", "OntologyEntity"], categoryKey: CAT.knowledge },
  // ── Sidebar：控制面板组 ─────────────────────────────────────────
  { id: "security-center", route: "/security-center", labelKey: "app.tab.security_center", group: "control", descKey: "sidebar.desc.security_center", icon: Shield, tabbable: true, palette: p("security-center", CAT.security) },
  { id: "engine-tasks", route: "/engine-tasks", labelKey: "app.tab.engine_tasks", group: "control", descKey: "sidebar.desc.engine_tasks", icon: ClipboardList, tabbable: true },
  { id: "monitor", route: "/monitor", labelKey: "app.tab.monitor", group: "control", descKey: "sidebar.desc.monitor", icon: Gauge, tabbable: true },
  { id: "dict", route: "/dict", labelKey: "app.tab.dict", group: "control", descKey: "sidebar.desc.dict", icon: Table2, tabbable: true },
  { id: "platform-governance", route: "/platform-governance", labelKey: "app.tab.platform_governance", group: "control", descKey: "sidebar.desc.platform_governance", icon: Settings2, tabbable: true },
  // ── 菜单外保留 tabbable（2026-10-14 摘出菜单，URL/标签条仍可达）──
  { id: "world_model", route: "/world_model", labelKey: "app.tab.world_model", tabbable: true },
  { id: "marketplace", route: "/marketplace", labelKey: "app.tab.marketplace", tabbable: true, searchTypes: ["Asset"], categoryKey: CAT.data },
  { id: "knowledge_graph", route: "/knowledge_graph", labelKey: "app.tab.knowledge_graph", tabbable: true, searchTypes: ["Knowledge"], categoryKey: CAT.knowledge },
  { id: "ops_apps", route: "/ops_apps", labelKey: "app.tab.ops_apps", tabbable: true },
  // ── 其余 tabbable 内容根 / 兼容别名 / vestigial ─────────────────
  { id: "mission_control", route: "/mission_control", labelKey: "app.tab.mission_control", tabbable: true, icon: Command, palette: p("mission_control", CAT.cognitive), searchTypes: ["Goal", "Scenario"] },
  { id: "security", route: "/security-center", labelKey: "app.tab.security_center", tabbable: true },
  { id: "ai-workbench", route: "/ai-workbench", labelKey: "app.tab.ai_workbench", tabbable: true },
  // workbench（详设-07 WP-D 场景工作台 L1/L2，W209 冻结路由根 fdee86d6）——
  // 2026-10-14 用户裁决：升为工作区组成项（见上「工作区组」，route=/workbench）。
  { id: "agent_mesh", route: "/agent_mesh", labelKey: "app.tab.agent_mesh", tabbable: true },
  { id: "agent-builder", route: "/agent-builder", labelKey: "app.tab.agent_builder", tabbable: true },
  { id: "agent-test", route: "/agent-test", labelKey: "app.tab.agent_test", tabbable: true },
  { id: "ontology", route: "/ontology", labelKey: "app.tab.ontology", tabbable: true, icon: Globe, palette: p("ontology", CAT.knowledge), searchTypes: ["OntologyEntity"] },
  { id: "ontology_designer", route: "/ontology_designer", labelKey: "app.tab.ontology_designer", tabbable: true },
  { id: "business-workbench", route: "/business-workbench", labelKey: "app.tab.business_workbench", tabbable: true },
  { id: "catalog", route: "/catalog", labelKey: "app.tab.catalog", tabbable: true, icon: Database, palette: p("catalog", CAT.data), searchTypes: ["Object"] },
  { id: "dataset_explorer", route: "/dataset_explorer", labelKey: "app.tab.dataset_explorer", tabbable: true },
  { id: "pipeline", route: "/pipeline", labelKey: "app.tab.pipeline", tabbable: true, icon: Activity, palette: p("pipeline", CAT.data), searchTypes: ["Workflow", "Pipeline"] },
  { id: "workbook", route: "/workbook", labelKey: "app.tab.workbook", tabbable: true },
  { id: "lineage", route: "/lineage", labelKey: "app.tab.lineage", tabbable: true, icon: GitBranch, palette: p("lineage", CAT.data) },
  { id: "datasources", route: "/datasources", labelKey: "app.tab.datasources", tabbable: true },
  { id: "workshop", route: "/workshop", labelKey: "app.tab.workflow", tabbable: true },
  { id: "workflow_designer", route: "/workflow_designer", labelKey: "app.tab.workflow", tabbable: true },
  { id: "objects", route: "/objects", labelKey: "app.tab.objects", tabbable: true },
  { id: "dq_dashboard", route: "/dq_dashboard", labelKey: "app.tab.dq_dashboard", tabbable: true },
  { id: "glossary", route: "/glossary", labelKey: "app.tab.glossary", tabbable: true },
  { id: "guardrails", route: "/guardrails", labelKey: "app.tab.guardrails", tabbable: true },
  { id: "biz_dashboard", route: "/biz_dashboard", labelKey: "app.tab.biz_dashboard", tabbable: true },
  { id: "project_tracker", route: "/project_tracker", labelKey: "app.tab.project_tracker", tabbable: true },
  { id: "contract_manager", route: "/contract_manager", labelKey: "app.tab.contract_manager", tabbable: true },
  { id: "ops_dashboard", route: "/ops_dashboard", labelKey: "app.tab.ops_dashboard", tabbable: true },
  { id: "system-config", route: "/system-config", labelKey: "app.tab.system_config", tabbable: true },
  { id: "iam", route: "/iam", labelKey: "app.tab.iam", tabbable: true },
  { id: "kanban", route: "/kanban", labelKey: "app.tab.kanban", tabbable: true },
  { id: "telemetry", route: "/telemetry", labelKey: "app.tab.telemetry", tabbable: true },
  { id: "tokens", route: "/tokens", labelKey: "app.tab.tokens", tabbable: true },
  { id: "gateway-diagnostics", route: "/gateway-diagnostics", labelKey: "app.tab.gateway_diagnostics", tabbable: true },
  { id: "alerts-center", route: "/alerts-center", labelKey: "app.tab.alerts_center", tabbable: true },
];

// ── 派生：Sidebar ───────────────────────────────────────────────────────────

function toSidebarEntry(e: NavEntry): SidebarEntry {
  if (!e.icon || !e.descKey) {
    // 单源纪律：分组项缺 icon/descKey 属配置错误，开发期即炸，不允许静默降级。
    throw new Error(`navConfig: sidebar entry "${e.id}" must define icon and descKey`);
  }
  return e as SidebarEntry;
}

const sidebarGroupsOf = (groupId: SidebarGroupId): SidebarGroup => ({
  groupKey: GROUP_KEYS[groupId],
  items: NAV_ENTRIES.filter((e) => e.group === groupId).map(toSidebarEntry),
});

/** Sidebar 四组（总览/工作区/资源概览/控制面板），组内顺序 = NAV_ENTRIES 声明顺序。 */
export const SIDEBAR_GROUPS: readonly SidebarGroup[] = [
  sidebarGroupsOf("overview"),
  sidebarGroupsOf("workspaces"),
  sidebarGroupsOf("resources"),
  sidebarGroupsOf("control"),
];

/**
 * 历史兼容导出（2026-10-14 后语义 = 控制面板组）：「产品功能」折叠组在
 * 2026-10-14 菜单重构中退役（4 组全部平铺），该导出保留以避免消费端
 * （__tests__/navConfig.test.ts）断言形状漂移；Sidebar.tsx 不再消费。
 */
export const SIDEBAR_PRODUCT_ITEMS: readonly SidebarEntry[] = SIDEBAR_GROUPS[3].items;

/** Sidebar 全部 12 项（总览1 + 工作区5 + 资源概览1 + 控制面板5，用户裁决 2026-10-14）。 */
export const SIDEBAR_ITEMS: readonly SidebarEntry[] = [
  ...SIDEBAR_GROUPS.flatMap((g) => g.items),
];

// ── 派生：工作区标签条（App.tsx）────────────────────────────────────────────

/** route id → i18n labelKey（45 键全量，替代 App.tsx 原私有表）。 */
export const TAB_LABEL_KEY: Record<string, string> = Object.fromEntries(
  NAV_ENTRIES.filter((e) => e.tabbable).map((e) => [e.id, e.labelKey]),
);

/**
 * W201/C183：标签解析 fail-closed。
 * 未知 id 不再回落到 raw `app.tab.{id}`（历史行为 = 界面直接显示英文 slug）。
 * 现在：开发期 console.error + 渲染占位文案（EmptyState 语义，"未知页面"）。
 */
export function resolveTabLabel(
  t: (key: string) => string,
  id: string,
): string {
  const key = TAB_LABEL_KEY[id];
  if (!key) {
    // import.meta.env 在当前 tsconfig 无 vite/client 类型声明（与 DataEngineConfigPanelTypes.tsx 同口径），安全 cast 读取 DEV
    const dev = (import.meta as unknown as { env?: { DEV?: boolean } }).env?.DEV;
    if (dev) {
      console.error(`[navConfig] no tab labelKey for route id "${id}" — showing placeholder (W201 fail-closed, no raw-key fallback).`);
    }
    return t("tab.unknown");
  }
  return t(key);
}

// ── 派生：命令面板（CommandPalette.tsx）─────────────────────────────────────

export interface PaletteItem {
  id: string;
  icon: IconComponent;
  titleKey: string;
  descKey: string;
  categoryKey: string;
}

export const PALETTE_ITEMS: readonly PaletteItem[] = NAV_ENTRIES
  .filter((e): e is NavEntry & { palette: PaletteMeta; icon: IconComponent } => !!e.palette && !!e.icon)
  .map((e) => ({
    id: e.id,
    icon: e.icon,
    titleKey: e.palette.titleKey,
    descKey: e.palette.descKey,
    categoryKey: e.palette.categoryKey,
  }));

export interface TypeMeta {
  navId: string;
  categoryKey: string; // 缺省 = palette.category.other（消费端兜底）
  icon: IconComponent;
}

/** 全局搜索命中类型反查：type → {navId, categoryKey, icon}。 */
export const TYPE_META_MAP: Record<string, TypeMeta> = {};
for (const e of NAV_ENTRIES) {
  for (const type of e.searchTypes ?? []) {
    TYPE_META_MAP[type] = {
      navId: e.id,
      categoryKey: e.categoryKey ?? e.palette?.categoryKey ?? "palette.category.other",
      icon: e.icon ?? Database,
    };
  }
}

/** type → 导航目标 id（TYPE_NAV_MAP，派生自 searchTypes 反查）。 */
export const TYPE_NAV_MAP: Record<string, string> = Object.fromEntries(
  Object.entries(TYPE_META_MAP).map(([type, meta]) => [type, meta.navId]),
);

// ── 白名单：main.tsx 中存在、但本轮刻意不进入 NAV_CONFIG 的内容路由根 ────────
// R-52①：待产品定档 —— 不擅自补入口，也不擅自下线；每个根一行理由。
export const DELIBERATELY_UNLINKED: Readonly<Record<string, string>> = {
  "engine-ai": "口径 C 硬孤岛：全仓零导航引用，仅手动 URL 可达；待产品定档，不擅自补入口也不下线",
  "engine-cognitive": "口径 C 硬孤岛：引擎监控页无全局入口；待产品定档，不擅自补入口也不下线",
  "engine-data": "口径 C 硬孤岛：同上",
  "engine-knowledge": "口径 C 硬孤岛：同上",
  "engine-ontology": "口径 C 硬孤岛：同上",
  "engine-security": "口径 C 硬孤岛：同上",
  case_library: "口径 C 硬孤岛：案例库仅从认知页跳转进入；待产品定档是否升为一级导航",
  domain_designer: "口径 C 硬孤岛：main.tsx 仅剩 Navigate 重定向兼容；待后续 Wave 清理",
  domains: "旧重定向兼容路由（Navigate → ontology_workbench），非页面根；保留至重定向下线",
  "scenario-sandbox": "沙箱演练页：由场景详情动作进入，天然不该有全局入口",
  project_workbench:
    "2026-10-14 用户裁决：侧栏场景工作台入口改指 /workbench（工作区组成项）；" +
    "本项目为「场景管理·规划·评审」高级技术视图，保留 URL 可达，由 workbench 页内链接直达，不再出侧栏项",
  knowledge: "与 knowledge_view 双入口并存（B.0 归一遗留）：待后续 Wave 合并删除",
  chatbot_studio: "入口由 ai-workbench 页面内跳转提供；待产品定档是否独立入口",
  alerts: "旧告警页：新 alerts-center 已入 NAV_CONFIG；legacy 待下线",
  tenants: "口径 A 无入口根（Sidebar 不含）；待产品定档",
};

/** main.tsx 中不属于"内容路由"、天然不进入导航口径的根（登录壳/通配）。 */
export const NON_NAV_ROUTE_ROOTS: readonly string[] = ["login", "no-access", "*"];
