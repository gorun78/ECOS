/**
 * ECOS Sidebar — 四组flat结构 + 桌面端可折叠
 * 总览 | 资源概览 | 系统管理 | 产品功能(5项平铺)
 * @license Apache-2.0
 */

import React, { useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import TaskPanel from "./TaskPanel";
import AsyncTaskCenterView from "./AsyncTaskCenterView";
import {
  Shield,
  LayoutDashboard,
  Play,
  GitPullRequest,
  Briefcase,
  Gauge,
  Target,
  Store,
  Cpu,
  Users as UsersIcon,
  Network,
  Table2,
  Settings,
  Building,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  X,
} from "lucide-react";
import { useLanguage } from "./LanguageContext";
import { useTheme } from "./ThemeContext";

interface SidebarProps {
  width?: number;
  statusMetrics?: {
    serviceStatus: string;
    engineVersion: string;
    taskRunning: number;
    taskPending: number;
    taskTotal: number;
  };
  collapsed?: boolean;
  onClose?: () => void;
  desktopCollapsed?: boolean;
  onDesktopToggle?: () => void;
}

interface NavItem {
  id: string;
  label: string;
  labelZh: string;
  icon: React.ComponentType<{ className?: string }>;
  desc: string;
  descZh: string;
}

interface NavGroup {
  group: string;
  groupZh: string;
  items: NavItem[];
}

export default function Sidebar({
  width = 240,
  statusMetrics = { serviceStatus: "UP", engineVersion: "1.0.0", taskRunning: 0, taskPending: 0, taskTotal: 0 },
  collapsed = false,
  onClose,
  desktopCollapsed = false,
  onDesktopToggle,
}: SidebarProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const [isTaskPanelOpen, setIsTaskPanelOpen] = useState(false);
  const [productCollapsed, setProductCollapsed] = useState(true);

  const pathSegments = location.pathname.split("/").filter(Boolean);
  const currentView = pathSegments[0] || "world_model";

  // ── 总览组 ──────────────────────────────────────────
  const overviewGroup: NavGroup = {
    group: "总览",
    groupZh: "总览",
    items: [
      { id: "world_model", label: "Strategic Goals", labelZh: "战略目标", icon: Target, desc: "Enterprise strategic goals", descZh: "企业战略目标与决策模拟" },
      { id: "monitor", label: "Monitoring Center", labelZh: "监控中心", icon: Gauge, desc: "System status & alerts", descZh: "系统运行状态与告警" },
      { id: "security-center", label: "Security Center", labelZh: "安全中心", icon: Shield, desc: "Security org, access control, audit logs", descZh: "安全组织、访问控制、分类标记、审计日志" },
    ],
  };

  // ── 资源概览组 ──────────────────────────────────────
  const resourceGroup: NavGroup = {
    group: "资源概览",
    groupZh: "资源概览",
    items: [
      { id: "marketplace", label: "Marketplace", labelZh: "数据市场", icon: Store, desc: "Browse data assets", descZh: "浏览数据资产，申请访问权限" },
      { id: "knowledge_graph", label: "Knowledge Graph", labelZh: "知识图谱", icon: BookOpen, desc: "Interactive ontology graph exploration", descZh: "交互式本体图谱探索" },
      { id: "ops_apps", label: "Operational Apps", labelZh: "运营应用", icon: Play, desc: "Operational apps", descZh: "运营应用快速入口" },
    ],
  };

  // ── 系统管理组 ──────────────────────────────────────
  const systemGroup: NavGroup = {
    group: "系统管理",
    groupZh: "系统管理",
    items: [
      { id: "iam", label: "IAM User Mgmt", labelZh: "用户管理", icon: UsersIcon, desc: "Users, roles, orgs & permissions", descZh: "用户/角色/组织/权限统一管理" },
      { id: "dict", label: "Data Dictionary", labelZh: "数据字典", icon: Table2, desc: "Data dictionary management", descZh: "数据字典与表结构定义管理" },
      { id: "system-config", label: "System Config", labelZh: "系统配置", icon: Settings, desc: "System configuration", descZh: "系统可配置参数管理" },
      { id: "tenants", label: "Tenant Mgmt", labelZh: "租户管理", icon: Building, desc: "Multi-tenant management", descZh: "多租户创建/配额/用量/账单管理" },
    ],
  };

  // ── 产品功能 — 5项平铺：项目→AI→知识→本体→数据 ──
  const productItems: NavItem[] = [
    { id: "project_workbench", label: "Project Workbench", labelZh: "项目工作台", icon: Briefcase, desc: "Scenario management & project orchestration", descZh: "场景调度·项目管理·资源编排" },
    { id: "agent_studio", label: "AI Workbench", labelZh: "AI工作台", icon: Cpu, desc: "Agent orchestration & model catalog", descZh: "Agent协同·模型目录·安全审计" },
    { id: "knowledge_view", label: "Knowledge Workbench", labelZh: "知识工作台", icon: BookOpen, desc: "Vector store, RAG retrieval", descZh: "知识库管理·RAG检索·数据对象图谱导入" },
    { id: "ontology_workbench", label: "Ontology Workbench", labelZh: "本体工作台", icon: Network, desc: "Ontology modeling & entity management", descZh: "本体建模·实体管理·关系图谱·术语标准" },
    { id: "data-workbench", label: "Data Workbench", labelZh: "数据工作台", icon: LayoutDashboard, desc: "Data sources, pipelines, governance", descZh: "数据源·管道·治理·血缘·调度" },
  ];

  // Render a nav item button
  const renderNavItem = (item: NavItem, keyPrefix: string = "") => {
    const isActive = currentView === item.id;
    const Icon = item.icon;
    return (
      <button
        key={`${keyPrefix}${item.id}`}
        id={`side-nav-${item.id}`}
        onClick={() => { navigate("/" + item.id); onClose?.(); }}
        className={`w-full text-left flex items-start gap-2.5 px-3 py-2 rounded-sm transition-all duration-150 outline-hidden ${
          isActive
            ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText}`
            : `${styles.sidebarHoverBg} opacity-80 hover:opacity-100`
        }`}
      >
        <Icon className={`w-[16px] h-[16px] shrink-0 mt-0.5 ${isActive ? styles.sidebarActiveText : "opacity-60"}`} />
        <div className="flex-1 min-w-0">
          <span className="text-[13px] font-medium block truncate leading-none">
            {locale === "zh" ? item.labelZh : item.label}
          </span>
          <span className="text-[9.5px] opacity-60 overflow-hidden text-ellipsis block truncate mt-1">
            {locale === "zh" ? item.descZh : item.desc}
          </span>
        </div>
      </button>
    );
  };

  // Render a group section
  const renderGroup = (group: NavGroup, keyPrefix: string = "") => (
    <div className="space-y-1">
      <span className="px-3 text-[9px] uppercase font-mono tracking-wider opacity-60 block mb-1.5 font-semibold">
        {locale === "zh" ? group.groupZh : group.group}
      </span>
      <div className="space-y-0.5">
        {group.items.map(item => renderNavItem(item, keyPrefix))}
      </div>
    </div>
  );

  const sidebarContent = (
    <>
      {/* Brand Header */}
      <div className={`px-6 py-6 border-b ${styles.sidebarBorder}`}>
        <div className={`font-extrabold text-[22px] tracking-tight ${styles.cardText}`}>
          ECOS <span className="font-light opacity-65 text-xs">v2.0</span>
        </div>
        <div className="text-[10px] font-mono tracking-widest text-indigo-500 dark:text-[#3B82F6] uppercase mt-1 leading-none">
          {locale === "zh" ? "企业认知操作系统" : "Enterprise Cognitive OS"}
        </div>
      </div>

      {/* Navigation */}
      <div className="flex-1 overflow-y-auto px-3 py-4 space-y-4 scrollbar-thin">
        {/* ── 总览 ────────────────────────────────────── */}
        {renderGroup(overviewGroup, "ov_")}

        {/* ── 资源概览 ────────────────────────────────── */}
        {renderGroup(resourceGroup, "res_")}

        {/* ── 系统管理 ────────────────────────────────── */}
        {renderGroup(systemGroup, "sys_")}
      </div>

      {/* ── 产品功能（可折叠，默认收起）─────────────────── */}
      <div className={`border-t ${styles.sidebarBorder} px-3 py-3 space-y-2 shrink-0`}>
        <button
          onClick={() => setProductCollapsed(!productCollapsed)}
          className="flex items-center justify-between w-full px-1 hover:opacity-80 transition-opacity"
        >
          <span className="text-[9px] uppercase font-mono tracking-wider opacity-50 font-semibold">
            {locale === "zh" ? "产品功能" : "Product Features"}
          </span>
          <ChevronDown className={`w-3.5 h-3.5 opacity-40 transition-transform duration-200 ${productCollapsed ? '' : 'rotate-180'}`} />
        </button>
        {!productCollapsed && (
          <div className="space-y-0.5">
            {productItems.map(item => renderNavItem(item, "prod_"))}
          </div>
        )}
      </div>

      {/* Footer */}
      <div className={`p-4 border-t ${styles.sidebarBorder} bg-black/5 dark:bg-black/40 font-mono text-[10px] space-y-2 shrink-0`}>
        <div className="flex items-center justify-between py-0.5">
          <span className="flex items-center gap-1.5 opacity-85">
            <span className={`w-2 h-2 rounded-full ${statusMetrics.serviceStatus === "UP" ? "bg-[#4ADE80]" : "bg-[#EF4444]"}`}></span>
            内核状态
          </span>
          <span className={`font-bold ${statusMetrics.serviceStatus === "UP" ? "text-[#4ADE80]" : "text-[#EF4444]"}`}>
            {statusMetrics.serviceStatus === "UP" ? "运行中" : statusMetrics.serviceStatus}
          </span>
        </div>
        <div
          className="flex items-center justify-between py-0.5 opacity-85 hover:opacity-100 cursor-pointer hover:bg-white/5 rounded px-0.5 transition"
          onClick={() => setIsTaskPanelOpen(true)}
          title="点击打开任务引擎面板"
        >
          <span>任务引擎</span>
          <span className="font-bold">
            {statusMetrics.taskRunning > 0 && <span className="text-[#4ADE80]">{statusMetrics.taskRunning} 运行 </span>}
            {statusMetrics.taskPending > 0 && <span className="text-[#F59E0B]">{statusMetrics.taskPending} 等待 </span>}
            {statusMetrics.taskTotal > 0 && <span className="opacity-60">{statusMetrics.taskTotal} 总计</span>}
            {statusMetrics.taskTotal === 0 && <span className="opacity-50">空闲</span>}
          </span>
        </div>
        <div className={`flex items-center justify-between py-0.5 text-[9.5px] border-t ${styles.sidebarBorder} pt-2 opacity-70`}>
          <span>认知引擎</span>
          <span className="font-bold font-sans">v{statusMetrics.engineVersion}</span>
        </div>
      </div>
    </>
  );

  return (
    <>
      {/* Mobile overlay */}
      <div
        className={`md:hidden fixed inset-0 z-40 bg-black/50 transition-opacity duration-300 ${
          collapsed ? "opacity-100 pointer-events-auto" : "opacity-0 pointer-events-none"
        }`}
        onClick={onClose}
      />

      {/* Sidebar */}
      <aside
        style={desktopCollapsed ? undefined : { width: `${width}px` }}
        className={`${styles.sidebarBg} border-r ${styles.sidebarBorder} flex flex-col h-full ${styles.sidebarText} select-none shrink-0 transition-all duration-300
          fixed left-0 top-0 z-50
          ${collapsed ? "translate-x-0 opacity-100 pointer-events-auto" : "-translate-x-full opacity-0 pointer-events-none"}
          md:relative md:pointer-events-auto
          ${desktopCollapsed ? "md:-translate-x-full md:opacity-0 md:w-0 md:border-0" : "md:translate-x-0 md:opacity-100"}`}
      >
        {sidebarContent}

        {/* Floating collapse button — bottom-right of sidebar (desktop only) */}
        {!desktopCollapsed && (
          <button
            onClick={onDesktopToggle}
            className="hidden md:flex absolute bottom-3 right-2 w-7 h-7 items-center justify-center rounded-full bg-slate-300/60 dark:bg-slate-600/60 hover:bg-slate-400/70 dark:hover:bg-slate-500/70 shadow-md opacity-60 hover:opacity-100 transition-all z-10"
            title={locale === "zh" ? "收起侧边栏" : "Collapse sidebar"}
          >
            <ChevronLeft className="w-[14px] h-[14px]" />
          </button>
        )}
      </aside>

      {/* Desktop icon rail (visible when collapsed) */}
      {desktopCollapsed && (
        <nav
          className={`hidden md:flex fixed left-0 top-0 h-full z-50 ${styles.sidebarBg} border-r ${styles.sidebarBorder}
            w-14 flex-col items-center py-3 gap-1 shadow-lg`}
        >
          {/* All nav icons flat */}
          {[...overviewGroup.items, ...resourceGroup.items, ...systemGroup.items, ...productItems].map(item => {
            const Icon = item.icon;
            const isActive = currentView === item.id;
            const label = locale === "zh" ? item.labelZh : item.label;
            return (
              <button
                key={"rail-" + item.id}
                onClick={() => { navigate("/" + item.id); }}
                title={label}
                className={`w-10 h-10 flex items-center justify-center rounded-lg transition-all relative group
                  ${isActive
                    ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText}`
                    : `opacity-60 hover:opacity-100 ${styles.sidebarHoverBg}`
                  }`}
              >
                <Icon className={`w-[18px] h-[18px] ${isActive ? styles.sidebarActiveText : ""}`} />
                {/* Tooltip on hover */}
                <span className="absolute left-full ml-2 px-2 py-1 bg-slate-800 dark:bg-slate-700 text-white text-xs rounded whitespace-nowrap opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity z-[60]">
                  {label}
                </span>
              </button>
            );
          })}

          {/* Separator before expand button */}
          <div className="flex-1" />
          <div className="w-8 h-px bg-slate-300/50 dark:bg-slate-600/50 mb-1" />

          {/* Expand button */}
          <button
            onClick={onDesktopToggle}
            className="w-10 h-10 flex items-center justify-center rounded-lg opacity-50 hover:opacity-100 transition-all"
            title={locale === "zh" ? "展开侧边栏" : "Expand sidebar"}
          >
            <ChevronRight className="w-[16px] h-[16px]" />
          </button>
        </nav>
      )}

      {/* AsyncTaskCenterView — 异步任务中心 (替换旧TaskPanel) */}
      {isTaskPanelOpen && (
        <div className="fixed inset-0 z-[60] bg-black/50 flex items-start justify-center pt-12" onClick={() => setIsTaskPanelOpen(false)}>
          <div className="bg-white dark:bg-slate-900 w-[95vw] max-w-[1400px] h-[85vh] rounded-xl shadow-2xl overflow-hidden" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between px-6 py-3 border-b border-slate-200 dark:border-slate-700">
              <h2 className="font-bold text-lg">{locale === "zh" ? "异步任务中心" : "Async Task Center"}</h2>
              <button onClick={() => setIsTaskPanelOpen(false)} className="p-1 hover:bg-slate-100 dark:hover:bg-slate-800 rounded">
                <X size={20} />
              </button>
            </div>
            <div className="h-[calc(85vh-52px)] overflow-auto">
              <AsyncTaskCenterView 
                showToast={(type, msg) => console.log(`[TaskCenter] ${type}: ${msg}`)}
                onViewModeChange={(mode) => { navigate("/" + mode); setIsTaskPanelOpen(false); }}
              />
            </div>
          </div>
        </div>
      )}
    </>
  );
}
