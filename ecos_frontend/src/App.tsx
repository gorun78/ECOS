/**
 * ECOS App — Layout shell with Sidebar + Topbar + <Outlet />
 * Routing is handled by React Router (HashRouter) in main.tsx.
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback, useRef } from "react";
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
import NetworkErrorBanner from "./components/NetworkErrorBanner";
import { resolveTabLabel } from "./navigation/navConfig";
import { apiHealth, notifyNetworkUp } from "./api";
import { apiTaskStats, type TaskStats } from "./services/taskCenter";

/**
 * Tab labels resolve through navigation/navConfig — the single source of
 * truth (W190). W201: unknown ids no longer fall back to a raw
 * `app.tab.{id}` key; resolveTabLabel logs in dev and shows the translated
 * placeholder instead (fail-closed, no English slug on screen).
 */
const getTabLabel = (t: (k: string) => string, id: string): string =>
  resolveTabLabel(t, id);

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
      apiTaskStats()
        .then((s) => setTaskStats(s))
        // PMO-43 T2: apiFetchData already dispatches `ecos-network-down` for
        // real network failures (the global NetworkErrorBanner reacts). The
        // catch here is defensive for non-transport errors (4xx/5xx business)
        // — in that case the banner stays hidden and the UI just keeps the
        // last known stats rather than the page blowing up.
        .catch(() => {});
    };
    poll();
    const interval = setInterval(poll, 10000);
    return () => clearInterval(interval);
  }, []);

  // Health polling — network failures are surfaced via the global
  // NetworkErrorBanner (T5/G3); DOWN/UP transitions feed the Sidebar
  // status chip, and recovery calls notifyNetworkUp exactly once per
  // DOWN→UP edge so the banner clears on recovery.
  const [serviceStatus, setServiceStatus] = useState("UP");
  const prevHealthUpRef = useRef(true);

  useEffect(() => {
    const poll = () => {
      apiHealth()
        .then((s) => {
          const up = s !== "DOWN";
          if (prevHealthUpRef.current === false && up) notifyNetworkUp(); // DOWN→UP edge
          prevHealthUpRef.current = up;
          setServiceStatus(s);
        })
        .catch(() => {
          // G3: track DOWN→UP edges so the banner clears on recovery.
          if (prevHealthUpRef.current) notifyNetworkUp(); // last UP → DOWN edge; banner re-raises on next DOWN
          prevHealthUpRef.current = false;
          setServiceStatus("DOWN");
        });
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
      {/* Global network/offline banner (PMO-43 T5) — fixed top overlay */}
      <NetworkErrorBanner />

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
            ? `${styles.accentBg} w-1.5`
            : `border-r ${styles.appBorder} ${styles.sidebarHoverBg}`
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
          {/* W214/C196 路由级错误边界：key = 内容路由根，单页崩溃只降级该页，
              导航到兄弟路由时边界整体重挂载（门禁 ErrorBoundary.route.test.tsx）。 */}
          <ErrorBoundary key={currentView}>
            <Outlet />
          </ErrorBoundary>
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
        className={`fixed bottom-6 right-6 z-50 h-14 w-14 rounded-full ${styles.accentBg} ${styles.accentHover} text-white shadow-lg shadow-black/30 flex items-center justify-center transition-colors duration-150 focus:outline-none focus:ring-2 focus:ring-offset-2`}
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
