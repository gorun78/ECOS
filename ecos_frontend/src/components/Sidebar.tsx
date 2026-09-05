/**
 * ECOS Sidebar — 四组flat结构 + 桌面端可折叠
 * Wave-2A T3: 所有 labelZh/descZh/groupZh 改为 `sidebar.*` namespace i18n
 * @license Apache-2.0
 */

import React, { useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import TaskPanel from "./TaskPanel";
import AsyncTaskCenterView from "../pages/AsyncTaskCenterView";
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
  Network,
  Table2,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  X,
} from "lucide-react";
import { useLanguage } from "./LanguageContext";
import { useTheme } from "./ThemeContext";
import type { ComponentType } from "react";

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

/** A nav item resolved to its i18n key (avoid stale labelZh/descZh strings). */
interface NavItem {
  id: string;
  labelKey: string;
  icon: ComponentType<{ className?: string }>;
  descKey: string;
}

interface NavGroup {
  groupKey: string;
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
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [isTaskPanelOpen, setIsTaskPanelOpen] = useState(false);
  const [productCollapsed, setProductCollapsed] = useState(true);

  const pathSegments = location.pathname.split("/").filter(Boolean);
  const currentView = pathSegments[0] || "world_model";

  // ── 总览组 ──────────────────────────────────────────
  const overviewGroup: NavGroup = {
    groupKey: "sidebar.group.overview",
    items: [
      { id: "world_model", labelKey: "app.tab.world_model", icon: Target, descKey: "sidebar.desc.world_model" },
      { id: "monitor", labelKey: "app.tab.monitor", icon: Gauge, descKey: "sidebar.desc.monitor" },
    ],
  };

  // ── 资源概览组 ──────────────────────────────────────
  const resourceGroup: NavGroup = {
    groupKey: "sidebar.group.resources",
    items: [
      { id: "marketplace", labelKey: "app.tab.marketplace", icon: Store, descKey: "sidebar.desc.marketplace" },
      { id: "knowledge_graph", labelKey: "app.tab.knowledge_graph", icon: BookOpen, descKey: "sidebar.desc.knowledge_graph" },
      { id: "ops_apps", labelKey: "app.tab.ops_apps", icon: Play, descKey: "sidebar.desc.ops_apps" },
    ],
  };

  // ── 系统管理组 ──────────────────────────────────────
  const systemGroup: NavGroup = {
    groupKey: "sidebar.group.system",
    items: [
      { id: "security-center", labelKey: "app.tab.security_center", icon: Shield, descKey: "sidebar.desc.security_center" },
      { id: "dict", labelKey: "app.tab.dict", icon: Table2, descKey: "sidebar.desc.dict" },
    ],
  };

  // ── 产品功能 — 5项平铺 ──────────────────────────────
  const productItems: NavItem[] = [
    { id: "project_workbench", labelKey: "app.tab.project_workbench", icon: Briefcase, descKey: "sidebar.desc.project_workbench" },
    { id: "agent_studio", labelKey: "app.tab.ai_workbench", icon: Cpu, descKey: "sidebar.desc.agent_studio" },
    { id: "knowledge_view", labelKey: "app.tab.knowledge_view", icon: BookOpen, descKey: "sidebar.desc.knowledge_view" },
    { id: "ontology_workbench", labelKey: "app.tab.ontology_workbench", icon: Network, descKey: "sidebar.desc.ontology_workbench" },
    { id: "data-workbench", labelKey: "app.tab.data_workbench", icon: LayoutDashboard, descKey: "sidebar.desc.data_workbench" },
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
            {t(item.labelKey)}
          </span>
          <span className="text-[9.5px] opacity-60 overflow-hidden text-ellipsis block truncate mt-1">
            {t(item.descKey)}
          </span>
        </div>
      </button>
    );
  };

  // Render a group section
  const renderGroup = (group: NavGroup, keyPrefix: string = "") => (
    <div className="space-y-1">
      <span className="px-3 text-[9px] uppercase font-mono tracking-wider opacity-60 block mb-1.5 font-semibold">
        {t(group.groupKey)}
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
          {t("sidebar.brand.tagline")}
        </div>
      </div>

      {/* Navigation */}
      <div className="flex-1 overflow-y-auto px-3 py-4 space-y-4 scrollbar-thin">
        {renderGroup(overviewGroup, "ov_")}
        {renderGroup(resourceGroup, "res_")}
        {renderGroup(systemGroup, "sys_")}
      </div>

      {/* ── 产品功能（可折叠，默认收起）─────────────────── */}
      <div className={`border-t ${styles.sidebarBorder} px-3 py-3 space-y-2 shrink-0`}>
        <button
          onClick={() => setProductCollapsed(!productCollapsed)}
          className="flex items-center justify-between w-full px-1 hover:opacity-80 transition-opacity"
        >
          <span className="text-[9px] uppercase font-mono tracking-wider opacity-50 font-semibold">
            {t("sidebar.group.product")}
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
            {t("sidebar.footer.kernel")}
          </span>
          <span className={`font-bold ${statusMetrics.serviceStatus === "UP" ? "text-[#4ADE80]" : "text-[#EF4444]"}`}>
            {statusMetrics.serviceStatus === "UP" ? t("sidebar.footer.running") : statusMetrics.serviceStatus}
          </span>
        </div>
        <div
          className="flex items-center justify-between py-0.5 opacity-85 hover:opacity-100 cursor-pointer hover:bg-white/5 rounded px-0.5 transition"
          onClick={() => setIsTaskPanelOpen(true)}
          title={t("sidebar.footer.task.open")}
        >
          <span>{t("sidebar.task.title")}</span>
          <span className="font-bold">
            {statusMetrics.taskRunning > 0 && <span className="text-[#4ADE80]">{statusMetrics.taskRunning} {t("sidebar.footer.running_count")} </span>}
            {statusMetrics.taskPending > 0 && <span className="text-[#F59E0B]">{statusMetrics.taskPending} {t("sidebar.footer.waiting")} </span>}
            {statusMetrics.taskTotal > 0 && <span className="opacity-60">{statusMetrics.taskTotal} {t("sidebar.footer.total")}</span>}
            {statusMetrics.taskTotal === 0 && <span className="opacity-50">{t("sidebar.footer.idle")}</span>}
          </span>
        </div>
        <div className={`flex items-center justify-between py-0.5 text-[9.5px] border-t ${styles.sidebarBorder} pt-2 opacity-70`}>
          <span>{t("sidebar.footer.engine")}</span>
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
            title={t("sidebar.desktop.collapse")}
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
            const label = t(item.labelKey);
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
            title={t("sidebar.desktop.expand")}
          >
            <ChevronRight className="w-[16px] h-[16px]" />
          </button>
        </nav>
      )}

      {/* AsyncTaskCenterView */}
      {isTaskPanelOpen && (
        <div className="fixed inset-0 z-[60] bg-black/50 flex items-start justify-center pt-12" onClick={() => setIsTaskPanelOpen(false)}>
          <div className="bg-white dark:bg-slate-900 w-[95vw] max-w-[1400px] h-[85vh] rounded-xl shadow-2xl overflow-hidden" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between px-6 py-3 border-b border-slate-200 dark:border-slate-700">
              <h2 className="font-bold text-lg">{t("sidebar.task_center")}</h2>
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
