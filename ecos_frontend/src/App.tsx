/**
 * ECOS App — Layout shell with Sidebar + Topbar + <Outlet />
 * Routing is handled by React Router (HashRouter) in main.tsx.
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback, useRef } from "react";
import { Outlet, useNavigate, useLocation } from "react-router-dom";
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
import { apiHealth, notifyNetworkUp, apiCognitiveHealth } from "./api";
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
  // index 根是 <Navigate to="/decision" replace/>（2026-10-14 用户裁决，main.tsx）：
  // 登录落地即重定向到 /decision，渲染期内不会以 "decision" 之外的空根到达 App。
  // 兜底默认 "decision"：与导航首项（navConfig 总览组）一致，失效 URL 下 Tab/面包屑
  // 不再张冠李戴指向 mission_control（认知蓝图已不占首页）。
  const pathSegments = location.pathname.split("/").filter(Boolean);
  const currentView = pathSegments[0] || "decision";

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

  // 认知引擎版本（Sidebar 左下状态面板）：走 apiCognitiveHealth()（GET /api/v1/cognitive/health）。
  // 后端契约实测：gateway monolith 侧 cognitive2 的 CognitiveEngineOpenHealthController
  // （唯一 setVersion 的 VO）被 GatewayApplication excludeFilters 排除，gateway 该路径
  // 由 ai 侧/其它 Controller 承接且无 version 字段；独立 :18089 调试引擎侧才有
  // CognitiveEngineOpenHealthVO.version="v2.0"。⇒ 前端只能 fail-closed：请求体带了
  // 真实 version 才显示，否则 Sidebar 只渲染「认知引擎」label 不渲染 v?
  // （假字面量 1.0.0-SNAPSHOT 已在本波删除）。失败保留上次有效值，不清屏。
  const [engineVersion, setEngineVersion] = useState("");

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

  // Cognitive engine version polling (30s，与 health 同周期；失败保留上次值)
  useEffect(() => {
    const pollVersion = () => {
      apiCognitiveHealth()
        .then((s) => {
          const v = s?.version;
          if (typeof v === "string" && v) setEngineVersion(v);
        })
        .catch(() => {
          // 静默：Sidebar 占位文案已表达「取不到」，无需打扰
        });
    };
    pollVersion();
    const interval = setInterval(pollVersion, 30000);
    return () => clearInterval(interval);
  }, []);

  // Mobile sidebar hook
  const { isMobile, sidebarOpen, toggleSidebar, closeSidebar } = useMobileSidebar();

  // Workspace tabs. Labels are stored by key (not display string) so the
  // strip re-renders when the user switches locale. The `id` doubles as the
  // stable i18n id — Topbar.applyTranslateTabLabel will resolve keys to UI.
  const [openTabs, setOpenTabs] = useState<Tab[]>(() => [
    { id: "decision", label: getTabLabel((k) => k, "decision"), active: true }
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

  // Command palette toggle (Ctrl+K) — 全仓唯一 toggle 监听点（单源）。
  // CommandPalette 内部旧的同键位监听（只负责"已开时再按=关"）已删除：双监听
  // 在同一 keydown 里先后触发 open/close 竞争，语义漂移且不可调试
  // （检视报告 §4.1 P2 行66）。toggle 关闭由本处 !prev 分支承担。
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
    // select-none 从根容器移除（报告 §4.1 P1 行61：全局禁选导致 traceId/taskId/
    // 错误详情不可复制）。禁选语义收窄到导航/图标容器：Sidebar aside、Topbar 根、
    // CommandPalette 容器各自带 select-none；内容区 <main> 保持默认可选。
    <div className={`flex h-screen ${styles.appBg} ${styles.appText} overflow-hidden font-sans antialiased transition-colors duration-150`}>
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
          engineVersion,
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
          onCopilotOpen={() => setCopilotOpen(true)}
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
        onNavigate={(viewId, focusId) => {
          // 深链：命中对象 id 以 ?focus= 透传给分类页（报告 §4.1 P1 行64）。
          navigate(focusId ? `/${viewId}?focus=${encodeURIComponent(focusId)}` : "/" + viewId);
        }}
      />

      {/* 2026-10-14 用户裁决：Copilot 悬浮按钮入口去除，入口改由 Topbar 右上角承担。 */}

      {/* AIP Copilot drawer */}
      <AIPCopilotDrawer
        isOpen={copilotOpen}
        onClose={() => setCopilotOpen(false)}
      />
    </div>
  );
}
