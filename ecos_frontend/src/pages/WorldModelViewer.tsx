/**
 * WorldModelViewer v2 — 世界模型浏览器：7 标签页金字塔可视化（Tab 状态同步到 URL ?tab=）
 * Connected to backend: /api/v1/ecos/world-model/*
 *
 * Tabs: Goal Tree | Execution Tracking | Causal Graph | Scenarios | Review | Simulation | Pareto
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import { useSearchParams } from "react-router-dom";
import {
  Target, GitBranch, Network, RefreshCw,
  AlertCircle, XCircle,
  TrendingUp, BarChart3, ClipboardList, Zap,
  Calendar, Clock,
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import ConfirmDialog from "../components/common/ConfirmDialog";
import ScenarioSandbox from "../components/ScenarioSandbox";
import ErrorBoundary from "../components/common/ErrorBoundary";
import {
  fetchWorldGoals,
  fetchWorldScenarios,
  fetchWorldCausalLinks,
  fetchWorldCausalGraph,
  fetchWorldGoalTree,
  fetchGoalTracking,
  createWorldModelItem,
  updateWorldModelItem,
  deleteWorldModelItem,
  compareWorldScenarios,
  paretoOptimize,
  fetchParetoProblems,
  fetchParetoResult,
} from "../api";
import type { Goal, CausalLink, Scenario } from "../types";
import type { ParetoOptimizeResult, ParetoProblem } from "../api";

// ── Sub-modules ─────────────────────────────
import CrudDialog from "./worldmodel/CrudDialog";
import GoalTreeTab from "./worldmodel/tabs/GoalTreeTab";
import TrackingTab from "./worldmodel/tabs/TrackingTab";
import CausalTab from "./worldmodel/tabs/CausalTab";
import ScenariosTab from "./worldmodel/tabs/ScenariosTab";
import ReviewTab from "./worldmodel/tabs/ReviewTab";
import ParetoTab from "./worldmodel/tabs/ParetoTab";
import { API_BASE, apiFetchData } from "../services/httpClient";
import { SYSTEM_ORGANIZATIONS_TREE_URL } from "../api/iamClient";

// ── Main Component ───────────────────────────
export default function WorldModelViewer() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  const TAB_COUNT = 7;
  const [searchParams, setSearchParams] = useSearchParams();
  const rawTab = Number(searchParams.get("tab"));
  const tab = Number.isFinite(rawTab) ? Math.min(Math.max(Math.trunc(rawTab), 0), TAB_COUNT - 1) : 0;
  const setTab = (i: number) => {
    const next = new URLSearchParams(searchParams);
    next.set("tab", String(i));
    setSearchParams(next, { replace: true });
  };

  // 删除确认门：handleDelete 改两段式（点击 → 确认弹窗 → 真实删除），子 Tab 传入签名不变
  const [pendingDelete, setPendingDelete] = useState<{ type: string; id: number | string } | null>(null);
  // 新建目标「编码待后端发号/手工填写」显式标注（弹窗遮挡页面横幅不可见时配合 toast）
  const [showCodePending, setShowCodePending] = useState(false);

  const [goals, setGoals] = useState<Goal[]>([]);
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [trackingData, setTrackingData] = useState<any[]>([]);
  const [trackingLoading, setTrackingLoading] = useState(false);
  const [causalLinks, setCausalLinks] = useState<CausalLink[]>([]);
  const [causalGraph, setCausalGraph] = useState<{ nodes: any[]; edges: any[] }>({ nodes: [], edges: [] });
  const [goalTree, setGoalTree] = useState<Goal[]>([]);
  const [selectedGoal, setSelectedGoal] = useState<Goal | null>(null);
  const [dlgOpen, setDlgOpen] = useState(false);
  const [dlgType, setDlgType] = useState("");
  const [form, setForm] = useState<Record<string, any>>({});
  const [compareIds, setCompareIds] = useState<(number | string)[]>([]);
  const [compareResult, setCompareResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [emptyMsg, setEmptyMsg] = useState<Record<string, string>>({});

  // ── Pareto state ──────────────────────────
  const [paretoForm, setParetoForm] = useState({
    numObjectives: 2,
    numVariables: 3,
    populationSize: 100,
    generations: 50,
  });
  const [paretoResult, setParetoResult] = useState<ParetoOptimizeResult | null>(null);
  const [paretoHistory, setParetoHistory] = useState<ParetoProblem[]>([]);
  const [paretoLoading, setParetoLoading] = useState(false);
  const [paretoError, setParetoError] = useState("");

  // Expand/collapse for goal tree
  const [expandedIds, setExpandedIds] = useState<Set<number | string>>(new Set());

  // ── Data fetch ────────────────────────────
  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError("");
    const messages: Record<string, string> = {};

    try {
      const g = await fetchWorldGoals();
      setGoals(Array.isArray(g) ? g : []);
    } catch (e: any) {
      messages.goals = e.message;
      setGoals([]);
    }

    try {
      const sc = await fetchWorldScenarios();
      setScenarios(Array.isArray(sc) ? sc : []);
    } catch { setScenarios([]); }

    try {
      const cl = await fetchWorldCausalLinks();
      setCausalLinks(Array.isArray(cl) ? cl : []);
    } catch { setCausalLinks([]); }

    try {
      const gr = await fetchWorldCausalGraph();
      setCausalGraph(gr || { nodes: [], edges: [] });
    } catch { setCausalGraph({ nodes: [], edges: [] }); }

    try {
      const gt = await fetchWorldGoalTree();
      setGoalTree(Array.isArray(gt) ? gt : []);
    } catch { setGoalTree([]); }

    setEmptyMsg(messages);
    setLoading(false);
  }, []);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  // ── Load goal tracking when goal selected ──
  useEffect(() => {
    if (!selectedGoal) { setTrackingData([]); return; }
    setTrackingLoading(true);
    fetchGoalTracking(Number(selectedGoal.id))
      .then((d: any) => setTrackingData(d?.tracking || []))
      .catch(() => setTrackingData([]))
      .finally(() => setTrackingLoading(false));
  }, [selectedGoal]);

  // ── Expand / Collapse all ────────────────
  function collectAllIds(nodes: Goal[]): (number | string)[] {
    const ids: (number | string)[] = [];
    const walk = (n: Goal) => { ids.push(n.id); n.children?.forEach(walk); };
    nodes.forEach(walk);
    return ids;
  }

  function expandAll() {
    const allIds = collectAllIds(goalTree.length > 0 ? goalTree : goals.filter(g => !g.parentId));
    setExpandedIds(new Set(allIds));
  }

  function collapseAll() {
    setExpandedIds(new Set());
  }

  // ── Toggle single node ────────────────────
  function toggleExpand(id: number | string) {
    setExpandedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  // ── CRUD ────────────────────────────────
  const [orgTree, setOrgTree] = useState<any[]>([]);
  const [userList, setUserList] = useState<any[]>([]);

  async function executeDelete(type: string, id: number | string) {
    try {
      await deleteWorldModelItem(type, id as any);
    } catch (e: any) {
      setError(e.message);
    }
    fetchAll();
    if (selectedGoal && selectedGoal.id === id) setSelectedGoal(null);
  }

  /** 删除入口：先进确认门，确认后才发真实 DELETE */
  function handleDelete(type: string, id: number | string) {
    setPendingDelete({ type, id });
  }

  async function openDlg(type: string, item?: any) {
    setDlgType(type);

    if (type === "goals" && !item) {
      // New goal: set defaults（编码不本地生成——无 /next-code 端点，
      // 本地 Math.random 造号属「无假真理」违例：留空 + 页面显式标注「待后端发号/手工填写」）
      const now = new Date();
      const year = now.getFullYear();
      const defaults: Record<string, any> = {
        status: "ACTIVE",
        priority: 0,
        startDate: `${year}-01-01`,
        endDate: `${year + 1}-01-01`,
        code: "",
      };
      setShowCodePending(true);

      // Fetch org tree (fire and forget)
      fetchOrgs();
      // Fetch users (fire and forget)
      fetchUsers();

      setForm(defaults);
    } else {
      setShowCodePending(false);
      setForm(item || (type === "goals"
        ? { status: "ACTIVE", priority: 0 }
        : type === "scenarios"
          ? { status: "DRAFT", probability: 0.5, impactScore: 0 }
          : { relationType: "ENABLES", strength: 0.5 }
      ));
    }
    setDlgOpen(true);
  }

  async function fetchOrgs() {
    try {
      // §5.3：ApiResponse 解包只在 httpClient（apiFetchData），页面不手拆信封
      // 基址自 #145/B6-14 起取 api 层族单源常量，本文件不再书写该族根体（属主唯一）
      const org = await apiFetchData<any>(SYSTEM_ORGANIZATIONS_TREE_URL);
      if (org) setOrgTree([org]);
    } catch {}
  }

  async function fetchUsers() {
    try {
      const page = await apiFetchData<any>(`${API_BASE}/v1/system/users?pageSize=200`);
      const users = Array.isArray(page) ? page : page?.data;
      if (users) setUserList(users);
    } catch {}
  }

  async function saveDlg() {
    try {
      if (form.id) {
        await updateWorldModelItem(dlgType, form.id, form);
      } else {
        await createWorldModelItem(dlgType, form);
      }
    } catch (e: any) {
      setError(e.message);
      return;
    }
    setShowCodePending(false);
    setDlgOpen(false);
    fetchAll();
  }

  async function doCompare() {
    if (compareIds.length < 2) return;
    try {
      const res = await compareWorldScenarios({ scenarioIds: compareIds });
      if (res) setCompareResult(res);
    } catch (e: any) {
      setError(e.message);
    }
  }

  // ── Pareto handlers ────────────────────────
  async function runParetoOptimize() {
    setParetoLoading(true);
    setParetoError("");
    try {
      const res = await paretoOptimize(paretoForm);
      setParetoResult(res);
      // Refresh history
      const problems = await fetchParetoProblems();
      setParetoHistory(problems);
    } catch (e: any) {
      setParetoError(e.message || t("platform.wm.viewer.paretoOptimizeFailed"));
    } finally {
      setParetoLoading(false);
    }
  }

  async function loadParetoHistory() {
    try {
      const problems = await fetchParetoProblems();
      setParetoHistory(problems);
    } catch { /* silent */ }
  }

  async function loadParetoResult(problemId: string) {
    setParetoLoading(true);
    setParetoError("");
    try {
      const res = await fetchParetoResult(problemId);
      setParetoResult(res);
    } catch (e: any) {
      setParetoError(e.message || t("platform.wm.viewer.paretoLoadFailed"));
    } finally {
      setParetoLoading(false);
    }
  }

  // Load pareto history when entering the tab
  useEffect(() => {
    if (tab === 6) loadParetoHistory();
  }, [tab]);

  // ── Year selector ───────────────────────
  const currentYear = new Date().getFullYear();
  const [selectedYear, setSelectedYear] = useState<number>(currentYear);
  const [showHistory, setShowHistory] = useState(false);

  // ── Build flat goals lookup for tree ────
  const topLevelGoals = goalTree.length > 0
    ? goalTree
    : goals.filter(g => !g.parentId);

  // ── Tab definitions ──────────────────────
  const tabs = [
    { label: t("platform.wm.tabs.goalTree"), icon: Target },
    { label: t("platform.wm.tabs.track"), icon: TrendingUp },
    { label: t("platform.wm.tabs.causal"), icon: Network },
    { label: t("platform.wm.tabs.scenarios"), icon: GitBranch },
    { label: t("platform.wm.tabs.review"), icon: ClipboardList },
    { label: t("platform.wm.tabs.simulation"), icon: BarChart3 },
    { label: t("platform.wm.tabs.pareto"), icon: Zap },
  ];

  // ── Render ──────────────────────────────
  return (
    <div className={`flex-1 overflow-y-auto ${styles.appBg} p-4 sm:p-6 ${styles.appText} flex flex-col h-full font-sans`}>
      {/* Header */}
      <div className="flex justify-between items-center mb-5 shrink-0">
        <div>
          <h1 className="text-xl font-bold tracking-tight flex items-center gap-2">
            <Target className={`w-5 h-5 shrink-0 ${styles.accentText}`} />
            {t("platform.wm.viewer.title")}
          </h1>
          <p className={`text-xs mt-0.5 ${styles.cardTextMuted}`}>
            {t("platform.wm.viewer.subtitle")}{showHistory ? t("platform.wm.viewer.historySuffix") : ""}
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* Year selector */}
          <div className="flex items-center gap-1.5">
            <Calendar className="w-3.5 h-3.5 opacity-50" />
            <select
              value={selectedYear}
              onChange={(e) => setSelectedYear(Number(e.target.value))}
              className={`text-xs font-medium rounded px-2 py-1.5 border ${styles.cardBorder} ${styles.cardBg} ${styles.appText} cursor-pointer outline-hidden`}
            >
              {Array.from({ length: 5 }, (_, i) => currentYear - i).map(y => (
                <option key={y} value={y}>{y}{y === currentYear ? t("platform.wm.viewer.currentYear") : ""}</option>
              ))}
            </select>
          </div>

          {/* History toggle */}
          <button type="button"
            onClick={() => setShowHistory(!showHistory)}
            className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded text-xs font-medium border transition-all cursor-pointer ${
              showHistory
                ? `${styles.warningBg} ${styles.warningBorder} ${styles.warningText}`
                : `${styles.cardBorder} opacity-60 hover:opacity-100`
            }`}
          >
            <Clock className="w-3.5 h-3.5" />
            {t("platform.wm.viewer.history")}
          </button>

          <button type="button"
            onClick={fetchAll}
            disabled={loading}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-medium transition-all
              ${styles.accentBg} ${styles.accentHover} text-white disabled:opacity-50 disabled:cursor-not-allowed`}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
            {loading ? t("platform.wm.viewer.loading") : t("platform.wm.viewer.refresh")}
          </button>
        </div>
      </div>

      {/* Error bar */}
      {error && (
        <div className={`mb-4 px-3 py-2 rounded text-xs flex items-center gap-2 shrink-0 ${styles.dangerBg} border ${styles.dangerBorder} ${styles.dangerText}`}>
          <AlertCircle className="w-4 h-4 shrink-0" />
          {error}
          <button type="button" onClick={() => setError("")} className="ml-auto hover:opacity-80 cursor-pointer" aria-label={t("common.close")}>
            <XCircle className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* 新建目标时编码待补提示（无发号端点，如实标注） */}
      {showCodePending && (
        <div className={`mb-2 px-3 py-2 rounded text-xs flex items-center gap-2 shrink-0 ${styles.warningBg} border ${styles.warningBorder} ${styles.warningText}`}>
          <AlertCircle className="w-3.5 h-3.5 shrink-0" />
          {t("dashboard.wm.codePending")}
          <button type="button" onClick={() => setShowCodePending(false)} aria-label={t("common.close")} className="ml-auto hover:opacity-80 cursor-pointer">
            <XCircle className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Tabs */}
      <div className={`flex gap-0 border-b mb-5 shrink-0 ${styles.sidebarBorder}`}>
        {tabs.map((tabItem, i) => (
          <button type="button"
            key={i}
            onClick={() => { setTab(i); }}
            aria-current={tab === i ? "page" : undefined}
            className={`px-3 sm:px-4 py-2 text-xs sm:text-sm font-medium transition-all duration-150 border-b-2 -mb-[1px] flex items-center gap-1.5 cursor-pointer ${
              tab === i
                ? `border-current ${styles.accentText}`
                : `border-transparent ${styles.cardTextMuted} hover:opacity-90`
            }`}
          >
            <tabItem.icon className="w-3.5 h-3.5 shrink-0" />
            <span className="hidden sm:inline">{tabItem.label}</span>
          </button>
        ))}
      </div>

      {/* ═══════ TAB 0: Goal Tree ═══════ */}
      {tab === 0 && (
        <GoalTreeTab
          topLevelGoals={topLevelGoals}
          loading={loading}
          locale={locale}
          styles={styles}
          expandedIds={expandedIds}
          toggleExpand={toggleExpand}
          setSelectedGoal={setSelectedGoal}
          selectedGoal={selectedGoal}
          openDlg={openDlg}
          handleDelete={handleDelete}
          expandAll={expandAll}
          collapseAll={collapseAll}
        />
      )}

      {/* ═══════ TAB 1: Execution Tracking ═══════ */}
      {tab === 1 && (
        <TrackingTab
          selectedGoal={selectedGoal}
          locale={locale}
          styles={styles}
          trackingData={trackingData}
          trackingLoading={trackingLoading}
        />
      )}

      {/* ═══════ TAB 2: Causal Graph (Cytoscape.js) ═══════ */}
      {tab === 2 && <CausalTab />}

      {/* ═══════ TAB 3: Scenarios ═══════ */}
      {tab === 3 && (
        <ScenariosTab
          scenarios={scenarios}
          goals={goals}
          loading={loading}
          locale={locale}
          styles={styles}
          openDlg={openDlg}
          handleDelete={handleDelete}
          compareIds={compareIds}
          setCompareIds={setCompareIds}
          doCompare={doCompare}
          compareResult={compareResult}
        />
      )}

      {/* ═══════ TAB 4: Review ═══════ */}
      {tab === 4 && (
        <ReviewTab
          selectedGoal={selectedGoal}
          locale={locale}
          styles={styles}
        />
      )}

      {/* Tab 5: 决策模拟沙箱 (ScenarioSandbox) */}
      {tab === 5 && (
        <div className="flex-1 overflow-y-auto space-y-4">
          {showHistory && (
            <div className={`px-3 py-2 rounded text-xs flex items-center gap-2 ${styles.warningBg} border ${styles.warningBorder} ${styles.warningText}`}>
              <AlertCircle className="w-3.5 h-3.5 shrink-0" />
              {t("platform.wm.viewer.historyBanner", { year: selectedYear })}
            </div>
          )}
          <ErrorBoundary>
          <ScenarioSandbox />
          </ErrorBoundary>
        </div>
      )}

      {/* ═══════ TAB 6: Pareto Optimization ═══════ */}
      {tab === 6 && (
        <ParetoTab
          locale={locale}
          styles={styles}
          paretoHistory={paretoHistory}
          paretoResult={paretoResult}
          paretoForm={paretoForm}
          setParetoForm={setParetoForm}
          paretoLoading={paretoLoading}
          paretoError={paretoError}
          setParetoError={setParetoError}
          runParetoOptimize={runParetoOptimize}
          loadParetoResult={loadParetoResult}
        />
      )}

      {/* ═══════ CRUD Dialog ═══════ */}
      <CrudDialog
        open={dlgOpen}
        dlgType={dlgType}
        form={form}
        setForm={setForm}
        onSave={saveDlg}
        onClose={() => setDlgOpen(false)}
        styles={styles}
        goals={goals}
        goalTree={goalTree}
        orgTree={orgTree}
        userList={userList}
      />

      {/* ═══════ 删除确认门 ═══════ */}
      <ConfirmDialog
        visible={pendingDelete != null}
        title={t("dashboard.wm.confirmDeleteTitle")}
        message={t("dashboard.wm.confirmDeleteMessage")}
        danger
        onConfirm={() => {
          const pd = pendingDelete;
          setPendingDelete(null);
          if (pd) executeDelete(pd.type, pd.id);
        }}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
