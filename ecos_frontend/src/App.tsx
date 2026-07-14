/**
 * ECOS App — Layout shell with Sidebar + Topbar + <Outlet />
 * Routing is handled by React Router (HashRouter) in main.tsx.
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import { Outlet, useNavigate, useLocation } from "react-router-dom";
import { Bot } from "lucide-react";
import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";
import CommandPalette from "./components/CommandPalette";
import AIPCopilotDrawer from "./components/copilot/AIPCopilotDrawer";
import { useMobileSidebar } from "./hooks/useMobileSidebar";
import { useTheme } from "./components/ThemeContext";
import ErrorBoundary from "./components/common/ErrorBoundary";
import { apiTaskStats, type TaskStats } from "./api";

async function apiHealth(): Promise<string> {
  try {
    const r = await fetch("/api/health");
    const d = await r.json();
    return d.data?.status || d.status || "DOWN";
  } catch { return "DOWN"; }
}

interface Tab {
  id: string;
  label: string;
  active: boolean;
}

/** Map route paths to human-readable tab labels (Chinese default, translateTabLabel in Topbar handles localization) */
const TAB_LABELS: Record<string, string> = {
  mission_control: "认知蓝图",
  world_model: "战略目标",
  monitor: "监控中心",
  "security-center": "安全中心",
  security: "安全中心",
  marketplace: "数据市场",
  knowledge_graph: "企业知识图谱",
  knowledge_view: "知识工作台",
  ops_apps: "运营应用",
  iam: "用户管理",
  dict: "数据字典",
  "system-config": "系统配置",
  tenants: "租户管理",
  project_workbench: "项目工作台",
  "ai-workbench": "AI工作台",
  agent_studio: "AI工作台",
  agent_mesh: "Agent网格",
  "agent-builder": "Agent构建器",
  "agent-test": "Agent测试",
  ontology_workbench: "本体工作台",
  ontology: "本体浏览器",
  ontology_designer: "本体设计器",
  "business-workbench": "业务工作台",
  "data-workbench": "数据工作台",
  catalog: "数据目录",
  dataset_explorer: "数据集浏览器",
  pipeline: "管道构建器",
  workbook: "代码工作簿",
  lineage: "数据血缘",
  datasources: "物理表注册",
  workshop: "工作流设计",
  workflow_designer: "工作流设计",
  objects: "数据浏览器",
  dq_dashboard: "数据质量",
  glossary: "术语表",
  guardrails: "安全护栏",
  biz_dashboard: "信科数据仪表盘",
  project_tracker: "项目跟踪",
  contract_manager: "合同管理",
  ops_dashboard: "产值分配看板",
  kanban: "项目看板",
  "engine-tasks": "任务中心",
  telemetry: "遥测监控",
  tokens: "令牌管理",
};

export default function App() {
  const { styles } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();

  // Derive currentView from URL path
  const pathSegments = location.pathname.split("/").filter(Boolean);
  const currentView = pathSegments[0] || "world_model";

  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false);
  const [sidebarWidth, setSidebarWidth] = useState<number>(240);
  const [desktopCollapsed, setDesktopCollapsed] = useState(false);
  const [isResizing, setIsResizing] = useState<boolean>(false);

  // AIP Copilot drawer
  const [copilotOpen, setCopilotOpen] = useState(false);

  // Task stats polling
  const [taskStats, setTaskStats] = useState<TaskStats>({
    total: 0, running: 0, pending: 0, succeeded: 0, failed: 0, cancelled: 0
  });

  useEffect(() => {
    const poll = () => {
      apiTaskStats().then(s => setTaskStats(s)).catch(() => {});
    };
    poll();
    const interval = setInterval(poll, 10000);
    return () => clearInterval(interval);
  }, []);

  // Health polling
  const [serviceStatus, setServiceStatus] = useState("UP");

  useEffect(() => {
    const poll = () => {
      apiHealth().then(s => setServiceStatus(s)).catch(() => setServiceStatus("DOWN"));
    };
    poll();
    const interval = setInterval(poll, 30000);
    return () => clearInterval(interval);
  }, []);

  // Mobile sidebar hook
  const { isMobile, sidebarOpen, toggleSidebar, closeSidebar } = useMobileSidebar();

  // Workspace tabs
  const [openTabs, setOpenTabs] = useState<Tab[]>([
    { id: "world_model", label: "战略目标", active: true }
  ]);

  // Sync active tab with current URL
  useEffect(() => {
    setOpenTabs((prev) => {
      const existing = prev.find((t) => t.id === currentView);
      if (existing) {
        return prev.map((t) => ({ ...t, active: t.id === currentView }));
      }
      const label = TAB_LABELS[currentView] || currentView;
      return [...prev.map((t) => ({ ...t, active: false })), { id: currentView, label, active: true }];
    });
  }, [currentView]);

  // Sidebar resize
  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isResizing) return;
      const newWidth = Math.max(160, Math.min(450, e.clientX));
      setSidebarWidth(newWidth);
    };
    const handleMouseUp = () => setIsResizing(false);
    if (isResizing) {
      window.addEventListener("mousemove", handleMouseMove);
      window.addEventListener("mouseup", handleMouseUp);
    }
    return () => {
      window.removeEventListener("mousemove", handleMouseMove);
      window.removeEventListener("mouseup", handleMouseUp);
    };
  }, [isResizing]);

  // Command palette toggle (Ctrl+K)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        setCommandPaletteOpen((prev) => !prev);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  // Navigation handler
  const handleNavigate = useCallback((viewId: string) => {
    navigate("/" + viewId);
    if (isMobile) closeSidebar();
  }, [navigate, isMobile, closeSidebar]);

  // Tab selection
  const handleTabSelect = useCallback((tabId: string) => {
    if (isMobile) closeSidebar();
    navigate("/" + tabId);
  }, [navigate, isMobile, closeSidebar]);

  const handleTabClose = useCallback((tabId: string) => {
    if (openTabs.length === 1) return;
    const targetIdx = openTabs.findIndex((t) => t.id === tabId);
    const updated = openTabs.filter((t) => t.id !== tabId);
    setOpenTabs(updated);
    if (openTabs[targetIdx]?.active && updated.length > 0) {
      const nextId = updated[Math.min(targetIdx, updated.length - 1)].id;
      navigate("/" + nextId);
    }
  }, [openTabs, navigate]);

  return (
    <div className={`flex h-screen ${styles.appBg} ${styles.appText} overflow-hidden font-sans select-none antialiased transition-colors duration-150`}>
      {/* Sidebar */}
      <Sidebar
        width={sidebarWidth}
        collapsed={sidebarOpen}
        onClose={closeSidebar}
        desktopCollapsed={desktopCollapsed}
        onDesktopToggle={() => setDesktopCollapsed(!desktopCollapsed)}
        statusMetrics={{
          serviceStatus,
          engineVersion: "1.0.0-SNAPSHOT",
          taskRunning: taskStats.running,
          taskPending: taskStats.pending,
          taskTotal: taskStats.total
        }}
      />

      {/* Draggable divider — hidden when sidebar collapsed */}
      {!desktopCollapsed && (
      <div
        id="sidebar-drag-handle"
        className={`hidden md:block w-1 hover:w-1.5 active:w-1.5 h-full cursor-col-resize shrink-0 transition-all duration-150 relative z-30 ${
          isResizing
            ? "bg-indigo-500/80 w-1.5 shadow-[0_0_8px_rgba(99,102,241,0.5)]"
            : "border-r border-slate-200/50 dark:border-slate-800/10 hover:bg-indigo-500/30"
        }`}
        onMouseDown={(e) => {
          e.preventDefault();
          setIsResizing(true);
        }}
      />
      )}

      {/* Main content */}
      <div className={`flex-1 flex flex-col min-w-0 h-full overflow-hidden ${styles.appBg} transition-colors duration-150`}>
        <Topbar
          currentView={currentView}
          onSearchOpen={() => setCommandPaletteOpen(true)}
          openTabs={openTabs}
          onTabSelect={handleTabSelect}
          onTabClose={handleTabClose}
          onMenuToggle={toggleSidebar}
        />

        <main className="flex-1 min-h-0 flex flex-col relative overflow-hidden">
          <div key={currentView} className="contents">
            <ErrorBoundary>
            <Outlet />
            </ErrorBoundary>
          </div>
        </main>
      </div>

      {/* Command palette */}
      <CommandPalette
        isOpen={commandPaletteOpen}
        onClose={() => setCommandPaletteOpen(false)}
        onNavigate={(viewId) => {
          navigate("/" + viewId);
        }}
      />

      {/* AIP Copilot floating entry button */}
      <button
        type="button"
        aria-label="打开 AIP Copilot"
        onClick={() => setCopilotOpen(true)}
        className="fixed bottom-6 right-6 z-50 h-14 w-14 rounded-full bg-slate-900 hover:bg-slate-800 dark:bg-indigo-600 dark:hover:bg-indigo-500 text-white shadow-lg shadow-black/30 flex items-center justify-center transition-colors duration-150 focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2"
      >
        <Bot className="h-7 w-7" />
      </button>

      {/* AIP Copilot drawer */}
      <AIPCopilotDrawer
        isOpen={copilotOpen}
        onClose={() => setCopilotOpen(false)}
      />
    </div>
  );
}
