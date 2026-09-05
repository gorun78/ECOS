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
import { useLanguage } from "./components/LanguageContext";
import ErrorBoundary from "./components/common/ErrorBoundary";
import { apiTaskStats, type TaskStats } from "./api";

/**
 * Route path → i18n key (common.app.tab.{id}).
 * Keep these tab labels out of the JS source so they follow the
 * active locale — switching zh/en re-renders the openTabs strip.
 * (Previously hardcoded as `TAB_LABELS: Record<string, string>`.)
 */
const TAB_LABEL_KEY: Record<string, string> = {
  mission_control: "app.tab.mission_control",
  world_model: "app.tab.world_model",
  monitor: "app.tab.monitor",
  "security-center": "app.tab.security_center",
  security: "app.tab.security_center",
  marketplace: "app.tab.marketplace",
  knowledge_graph: "app.tab.knowledge_graph",
  knowledge_view: "app.tab.knowledge_view",
  ops_apps: "app.tab.ops_apps",
  iam: "app.tab.iam",
  dict: "app.tab.dict",
  "system-config": "app.tab.system_config",
  project_workbench: "app.tab.project_workbench",
  "ai-workbench": "app.tab.ai_workbench",
  agent_studio: "app.tab.ai_workbench",
  agent_mesh: "app.tab.agent_mesh",
  "agent-builder": "app.tab.agent_builder",
  "agent-test": "app.tab.agent_test",
  ontology_workbench: "app.tab.ontology_workbench",
  ontology: "app.tab.ontology",
  ontology_designer: "app.tab.ontology_designer",
  "business-workbench": "app.tab.business_workbench",
  "data-workbench": "app.tab.data_workbench",
  catalog: "app.tab.catalog",
  dataset_explorer: "app.tab.dataset_explorer",
  pipeline: "app.tab.pipeline",
  workbook: "app.tab.workbook",
  lineage: "app.tab.lineage",
  datasources: "app.tab.datasources",
  workshop: "app.tab.workflow",
  workflow_designer: "app.tab.workflow",
  objects: "app.tab.objects",
  dq_dashboard: "app.tab.dq_dashboard",
  glossary: "app.tab.glossary",
  guardrails: "app.tab.guardrails",
  biz_dashboard: "app.tab.biz_dashboard",
  project_tracker: "app.tab.project_tracker",
  contract_manager: "app.tab.contract_manager",
  ops_dashboard: "app.tab.ops_dashboard",
  kanban: "app.tab.kanban",
  "engine-tasks": "app.tab.engine_tasks",
  telemetry: "app.tab.telemetry",
  tokens: "app.tab.tokens",
};
/** Default tab label: return the raw route id for unknown views (keep prior behaviour). */
const getTabLabel = (t: (k: string) => string, id: string): string =>
  t(TAB_LABEL_KEY[id] ?? `app.tab.${id}`);

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

export default function App() {
  const { styles } = useTheme();
  const { t } = useLanguage();
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

  // Workspace tabs. Labels are stored by key (not display string) so the
  // strip re-renders when the user switches locale. The `id` doubles as the
  // stable i18n id — Topbar.applyTranslateTabLabel will resolve keys to UI.
  const [openTabs, setOpenTabs] = useState<Tab[]>(() => [
    { id: "world_model", label: getTabLabel((k) => k, "world_model"), active: true }
  ]);

  // Re-resolve every tab label whenever the locale changes. Without this
  // effect, switching zh↔en leaves the existing tabs permanently stale.
  useEffect(() => {
    setOpenTabs((prev) => prev.map((tab) => ({ ...tab, label: getTabLabel(t, tab.id) })));
  }, [t]);

  // Sync active tab with current URL
  useEffect(() => {
    setOpenTabs((prev) => {
      const existing = prev.find((t) => t.id === currentView);
      if (existing) {
        return prev.map((t) => ({ ...t, active: t.id === currentView }));
      }
      const label = getTabLabel(t, currentView);
      return [...prev.map((t) => ({ ...t, active: false })), { id: currentView, label, active: true }];
    });
  }, [currentView, t]);

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
        aria-label={t("app.copilot.open")}
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
