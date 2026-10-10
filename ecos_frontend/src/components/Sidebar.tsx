/**
 * ECOS Sidebar — 四组flat结构 + 桌面端可折叠
 * Wave-2A T3: 所有 labelZh/descZh/groupZh 改为 `sidebar.*` namespace i18n
 * W190/C172: 导航项不再私有定义 —— 全部派生自 navigation/navConfig 单源。
 * @license Apache-2.0
 */

import React, { useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import TaskPanel from "./TaskPanel";
import AsyncTaskCenterView from "../pages/AsyncTaskCenterView";
import {
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  X,
} from "lucide-react";
import { useLanguage } from "./LanguageContext";
import { useTheme } from "./ThemeContext";
import { showToastGlobal } from "./common/Toast";
import {
  SIDEBAR_GROUPS,
  SIDEBAR_PRODUCT_ITEMS,
  SIDEBAR_ITEMS,
  type SidebarEntry,
  type SidebarGroup,
} from "../navigation/navConfig";

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

  // ── 导航项：全部派生自 navigation/navConfig（W190 单源，禁止本地私有表）──
  // SIDEBAR_GROUPS = 总览/资源/系统 三组；SIDEBAR_PRODUCT_ITEMS = 产品功能折叠组。

  // Render a nav item button
  const renderNavItem = (item: SidebarEntry, keyPrefix: string = "") => {
    const isActive = currentView === item.id;
    const Icon = item.icon;
    return (
      <button
        key={`${keyPrefix}${item.id}`}
        id={`side-nav-${item.id}`}
        onClick={() => { navigate(item.route); onClose?.(); }}
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
  const renderGroup = (group: SidebarGroup, keyPrefix: string = "") => (
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
        <div className={`text-[10px] font-mono tracking-widest uppercase mt-1 leading-none ${styles.accentText}`}>
          {t("sidebar.brand.tagline")}
        </div>
      </div>

      {/* Navigation */}
      <div className="flex-1 overflow-y-auto px-3 py-4 space-y-4 scrollbar-thin">
        {SIDEBAR_GROUPS.map((group, i) => renderGroup(group, `grp${i}_`))}
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
            {SIDEBAR_PRODUCT_ITEMS.map(item => renderNavItem(item, "prod_"))}
          </div>
        )}
      </div>

      {/* Footer */}
      <div className={`p-4 border-t ${styles.sidebarBorder} bg-black/5 dark:bg-black/40 font-mono text-[10px] space-y-2 shrink-0`}>
        <div className="flex items-center justify-between py-0.5">
          <span className="flex items-center gap-1.5 opacity-85">
            <span className={`w-2 h-2 rounded-full bg-current ${statusMetrics.serviceStatus === "UP" ? styles.successText : styles.dangerText}`}></span>
            {t("sidebar.footer.kernel")}
          </span>
          <span className={`font-bold ${statusMetrics.serviceStatus === "UP" ? styles.successText : styles.dangerText}`}>
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
            {statusMetrics.taskRunning > 0 && <span className={styles.successText}>{statusMetrics.taskRunning} {t("sidebar.footer.running_count")} </span>}
            {statusMetrics.taskPending > 0 && <span className={styles.warningText}>{statusMetrics.taskPending} {t("sidebar.footer.waiting")} </span>}
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
            className={`hidden md:flex absolute bottom-3 right-2 w-7 h-7 items-center justify-center rounded-full ${styles.sidebarHoverBg} ${styles.sidebarBorder} shadow-md opacity-60 hover:opacity-100 transition-all z-10`}
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
          {SIDEBAR_ITEMS.map(item => {
            const Icon = item.icon;
            const isActive = currentView === item.id;
            const label = t(item.labelKey);
            return (
              <button
                key={"rail-" + item.id}
                onClick={() => { navigate(item.route); }}
                title={label}
                className={`w-10 h-10 flex items-center justify-center rounded-lg transition-all relative group
                  ${isActive
                    ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText}`
                    : `opacity-60 hover:opacity-100 ${styles.sidebarHoverBg}`
                  }`}
              >
                <Icon className={`w-[18px] h-[18px] ${isActive ? styles.sidebarActiveText : ""}`} />
                {/* Tooltip on hover */}
                <span className={`absolute left-full ml-2 px-2 py-1 ${styles.cardBg} ${styles.cardText} text-xs rounded whitespace-nowrap opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity z-[60]`}>
                  {label}
                </span>
              </button>
            );
          })}

          {/* Separator before expand button */}
          <div className="flex-1" />
          <div className={`w-8 h-px ${styles.sidebarBorder} mb-1`} />

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
          <div className={`rounded-xl shadow-2xl overflow-hidden ${styles.cardBg} w-[95vw] max-w-[1400px] h-[85vh]`} onClick={e => e.stopPropagation()}>
            <div className={`flex items-center justify-between px-6 py-3 border-b ${styles.cardBorder}`}>
              <h2 className={`font-bold text-lg ${styles.cardText}`}>{t("sidebar.task_center")}</h2>
              <button onClick={() => setIsTaskPanelOpen(false)} className={`p-1 ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/5 rounded ${styles.cardText}`}>
                <X size={20} />
              </button>
            </div>
            <div className="h-[calc(85vh-52px)] overflow-auto">
              <AsyncTaskCenterView
                showToast={(type, msg) => showToastGlobal(type, msg)}
                onViewModeChange={(mode) => { navigate("/" + mode); setIsTaskPanelOpen(false); }}
              />
            </div>
          </div>
        </div>
      )}
    </>
  );
}
