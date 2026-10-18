/**
 * Wave-2A T3: Topbar 所有 19 个 breadcrumb + 37 个 translateTabLabel
 * 全部改为 `topbar.tab.*` / `topbar.group.*` / `topbar.bc.*` / `topbar.lang.*` i18n
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from "react";
import { X, ChevronRight, Palette, Settings, Menu, ShieldQuestion, UserRound, Bot } from "lucide-react";
import { useLanguage } from "./LanguageContext";
import { useTheme, ThemeId } from "./ThemeContext";
import { fetchUserSecurityProfile, updateUserSecurityProfile } from "../api";
import { getUserIdentity, getUserInitials, performLogout } from "../services/auth";
import ConfirmDialog from "./common/ConfirmDialog";

interface Tab {
  id: string;
  label: string;
  active: boolean;
}

interface TopbarProps {
  currentView: string;
  onSearchOpen: () => void;
  openTabs: Tab[];
  onTabSelect: (tabId: string) => void;
  onTabClose: (tabId: string) => void;
  onMenuToggle?: () => void;
  /** 2026-10-14：Copilot 入口从悬浮按钮移入 Topbar 右上角，点击开启抽屉。 */
  onCopilotOpen?: () => void;
}

/** Chinese label → i18n key (used to translate existing Chinese tab labels). */
const TAB_ZH_TO_KEY: Record<string, string> = {
  "认知蓝图": "topbar.tab.mission_control",
  "监控中心": "topbar.tab.monitor",
  "安全中心": "topbar.tab.security_center",
  "数据市场": "topbar.tab.marketplace",
  // W196 图谱同名异义分职（报告 §三）：#/knowledge_graph 实挂 GraphExplorerView
  // （本体图谱，金I），旧标签「企业知识图谱/知识图谱」与 dccheng 知识图谱域撞名。
  // 显示值已改「本体图谱浏览器」；下列三个旧中文串保留作历史 Tab 标签翻译兼容。
  "企业知识图谱": "topbar.tab.knowledge_graph",
  "知识图谱": "topbar.tab.knowledge_graph",
  "本体图谱浏览器": "topbar.tab.knowledge_graph",
  "知识工作台": "topbar.tab.knowledge_view",
  "运营应用": "topbar.tab.ops_apps",
  "用户管理": "topbar.tab.iam",
  "数据字典": "topbar.tab.dict",
  "系统配置": "topbar.tab.system_config",
  "租户管理": "topbar.tab.system_config",
  "场景工作台": "topbar.tab.project_workbench",
  "AI工作台": "topbar.tab.ai_workbench",
  "Agent 网格": "topbar.tab.agent_mesh",
  "Agent网格": "topbar.tab.agent_mesh",
  "Agent 构建器": "topbar.tab.agent_builder",
  "Agent构建器": "topbar.tab.agent_builder",
  "Agent 测试": "topbar.tab.agent_test",
  "Agent测试": "topbar.tab.agent_test",
  "本体工作台": "topbar.tab.ontology_workbench",
  "本体浏览器": "topbar.tab.ontology_explorer",
  "本体设计器": "topbar.tab.ontology_designer",
  "业务工作台": "topbar.tab.business_workbench",
  "数据工作台": "topbar.tab.data_workbench",
  "数据目录": "topbar.tab.data_workbench",
  "数据集浏览器": "topbar.tab.dataset_explorer",
  "管道构建器": "topbar.tab.pipeline_builder",
  "代码工作簿": "topbar.tab.workbook",
  "数据血缘": "topbar.tab.lineage",
  "物理表注册": "topbar.tab.datasources",
  "工作流设计": "topbar.tab.workflow_design",
  "数据浏览器": "topbar.tab.objects",
  "数据质量": "topbar.tab.dq_dashboard",
  "术语表": "topbar.tab.glossary",
  "安全护栏": "topbar.tab.guardrails",
  "信科数据仪表盘": "topbar.tab.biz_dashboard",
  "项目跟踪": "topbar.tab.project_tracker",
  "合同管理": "topbar.tab.contract_manager",
  "产值分配看板": "topbar.tab.ops_dashboard",
  "项目看板": "topbar.tab.kanban",
  "任务中心": "topbar.tab.engine_tasks",
  "战略目标": "topbar.tab.world_model_strategic",
};

export default function Topbar({
  currentView,
  onSearchOpen,
  openTabs,
  onTabSelect,
  onTabClose,
  onMenuToggle,
  onCopilotOpen,
}: TopbarProps) {
  const { locale, setLocale, t } = useLanguage();
  const { activeTheme, setActiveTheme, styles } = useTheme();

  // Settings values loading from localStorage (cache) or default
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [email, setEmail] = useState(() => localStorage.getItem("ecos_user_email") || "");
  const [clearance, setClearance] = useState(() => localStorage.getItem("ecos_user_clearance") || "");
  const [workstation, setWorkstation] = useState(() => localStorage.getItem("ecos_user_workstation") || "");
  const [auditMode, setAuditMode] = useState(() => localStorage.getItem("ecos_user_audit") || "full");
  const [sandbox, setSandbox] = useState(() => {
    const val = localStorage.getItem("ecos_user_sandbox");
    return val !== "false"; // default to true
  });
  const [toast, setToast] = useState<string | null>(null);

  // 真实登录身份（services/auth 单源）：无身份时显式降级为「未登录/未知账号」，
  // 绝不显示常量 "AD"/admin（检视报告 §4.1 P0：顶栏身份是假真理）。
  const identity = getUserIdentity();
  const displayName = identity?.username || t("topbar.user.anonymous");
  const initials = getUserInitials() || "—";

  // 敏感项（安全准入等级 / 沙盒审查 / 清空缓存）改动需二次确认门（报告 §4.1 P1）
  const [confirmAction, setConfirmAction] = useState<"save" | "reset" | null>(null);

  // Load security profile from backend on mount (sync with td_user_security_profile)
  useEffect(() => {
    const userId = localStorage.getItem("ecos_user_id") || "";
    if (!userId) return;
    fetchUserSecurityProfile(userId).then((p) => {
      if (p.clearanceLevel !== undefined) {
        const levelStr = String(p.clearanceLevel);
        setClearance(levelStr);
        localStorage.setItem("ecos_user_clearance", levelStr);
      }
      if (p.linkedWorkstation) {
        setWorkstation(p.linkedWorkstation);
        localStorage.setItem("ecos_user_workstation", p.linkedWorkstation);
      }
      if (p.auditMode) {
        setAuditMode(p.auditMode);
        localStorage.setItem("ecos_user_audit", p.auditMode);
      }
      if (p.sandboxMandatory !== undefined) {
        setSandbox(p.sandboxMandatory);
        localStorage.setItem("ecos_user_sandbox", String(p.sandboxMandatory));
      }
    }).catch((e) => {
      console.warn("Topbar: failed to load security profile, falling back to local cache", e);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const displayClearance = clearance || t("topbar.role.default");

  const handleSaveSettings = async () => {
    // Save to localStorage (cache)
    localStorage.setItem("ecos_user_email", email);
    localStorage.setItem("ecos_user_clearance", clearance);
    localStorage.setItem("ecos_user_workstation", workstation);
    localStorage.setItem("ecos_user_audit", auditMode);
    localStorage.setItem("ecos_user_sandbox", String(sandbox));

    // Sync to backend
    const userId = localStorage.getItem("ecos_user_id") || "";
    if (userId) {
      try {
        await updateUserSecurityProfile(userId, {
          clearanceLevel: parseInt(clearance) || 0,
          linkedWorkstation: workstation,
          auditMode: auditMode,
          sandboxMandatory: sandbox,
        });
      } catch (e) {
        console.warn("Topbar: failed to sync security profile to backend", e);
        setToast(t("topbar.user.save.localOnly"));
        setTimeout(() => { setToast(null); }, 4000);
        return;
      }
    }

    setToast(t("topbar.user.save.success"));
    setTimeout(() => {
      setToast(null);
    }, 4000);
  };

  const handleResetCache = async () => {
    localStorage.removeItem("ecos_user_email");
    localStorage.removeItem("ecos_user_clearance");
    localStorage.removeItem("ecos_user_workstation");
    localStorage.removeItem("ecos_user_audit");
    localStorage.removeItem("ecos_user_sandbox");

    setEmail("");
    setClearance("");
    setWorkstation("");
    setAuditMode("full");
    setSandbox(true);

    // Reset backend profile too
    const userId = localStorage.getItem("ecos_user_id") || "";
    if (userId) {
      try {
        await updateUserSecurityProfile(userId, {
          clearanceLevel: 0,
          linkedWorkstation: "",
          auditMode: "full",
          sandboxMandatory: true,
        });
      } catch (e) {
        console.warn("Topbar: failed to reset security profile on backend", e);
      }
    }

    setToast(t("topbar.user.reset.success"));
    setTimeout(() => {
      setToast(null);
    }, 4000);
  };


  const getViewBreadcrumbs = (): string[] => {
    // 总览组
    if (currentView === "world_model" || currentView === "mission_control")
      return [t("topbar.group.overview"), t("topbar.bc.mission_control")];
    if (currentView === "monitor")
      return [t("topbar.group.overview"), t("topbar.tab.monitor")];
    if (currentView === "security-center" || currentView === "security")
      return [t("topbar.group.overview"), t("topbar.tab.security_center")];
    // 资源概览组
    if (currentView === "marketplace")
      return [t("topbar.group.resources"), t("topbar.tab.marketplace")];
    if (currentView === "knowledge_graph")
      return [t("topbar.group.resources"), t("topbar.tab.knowledge_graph")];
    if (currentView === "ops_apps")
      return [t("topbar.group.resources"), t("topbar.tab.ops_apps")];
    // 系统管理组
    if (currentView === "iam")
      return [t("topbar.group.system"), t("topbar.bc.iam")];
    if (currentView === "dict")
      return [t("topbar.group.system"), t("topbar.tab.dict")];
    if (currentView === "system-config")
      return [t("topbar.group.system"), t("topbar.tab.system_config")];
    if (currentView === "tenants")
      return [t("topbar.group.system"), t("topbar.tab.system_config")];
    // 产品功能组
    if (currentView === "project_workbench")
      return [t("topbar.group.product"), t("topbar.tab.project_workbench")];
    if (currentView === "agent_studio" || currentView === "ai-workbench")
      return [t("topbar.group.product"), t("topbar.tab.ai_workbench")];
    if (currentView === "knowledge_view")
      return [t("topbar.group.product"), t("topbar.tab.knowledge_view")];
    if (currentView === "ontology_workbench" || currentView === "ontology" || currentView === "business-workbench")
      return [t("topbar.group.product"), t("topbar.tab.ontology_workbench")];
    if (currentView === "data-workbench" || currentView === "catalog" || currentView === "dataset_explorer" || currentView === "lineage" || currentView === "pipeline" || currentView === "workbook")
      return [t("topbar.group.product"), t("topbar.tab.data_workbench")];
    // 其他
    if (currentView === "decision")
      return [t("topbar.group.overview"), t("topbar.tab.decision")];
    if (currentView === "workshop" || currentView === "workflow_designer")
      return [t("topbar.group.product"), t("topbar.tab.workflow_design")];
    if (currentView === "biz_dashboard")
      return [t("topbar.bc.app_group"), t("topbar.tab.biz_dashboard")];
    if (currentView === "project_tracker")
      return [t("topbar.bc.app_group"), t("topbar.tab.project_tracker")];
    if (currentView === "contract_manager")
      return [t("topbar.bc.app_group"), t("topbar.tab.contract_manager")];
    if (currentView === "ops_dashboard")
      return [t("topbar.bc.app_group"), t("topbar.tab.ops_dashboard")];
    if (currentView === "objects")
      return [t("topbar.group.business"), t("topbar.tab.objects")];
    if (currentView === "glossary")
      return [t("topbar.group.system"), t("topbar.tab.glossary")];
    if (currentView === "guardrails")
      return [t("topbar.group.security"), t("topbar.tab.guardrails")];
    if (currentView === "kanban")
      return [t("topbar.group.system"), t("topbar.tab.kanban")];
    if (currentView === "engine-tasks" || currentView === "task_center")
      return [t("topbar.group.system"), t("topbar.tab.engine_tasks")];
    return [t("topbar.group.overview"), t(`topbar.bc.${currentView}`, currentView)];
  };

  /** Translate existing tab labels (Chinese labels passed by App.tsx). */
  const translateTabLabel = (label: string): string => {
    const key = TAB_ZH_TO_KEY[label];
    if (key) return t(key);
    // Backward compat: old English labels return as-is
    return label;
  };

  const breadcrumbs = getViewBreadcrumbs();

  return (
    <header className={`h-[64px] ${styles.cardBg} border-b ${styles.cardBorder} flex items-center justify-between px-6 select-none shrink-0 ${styles.cardText} font-sans shadow-xs transition-colors duration-150`}>

      {/* Left Box: Hamburger (mobile) + Breadcrumb mapping */}
      <div className={`flex items-center gap-1.5 text-xs font-mono ${styles.cardTextMuted} shrink-0`}>
        {/* Hamburger menu — visible only on mobile */}
        <button type="button"
          onClick={onMenuToggle}
          className={`md:hidden p-1.5 -ml-1 rounded-md ${styles.sidebarHoverBg} transition-colors ${styles.cardText}`}
          aria-label={t("topbar.menu.toggle")}
        >
          <Menu className="w-5 h-5" />
        </button>
        <span className={`${styles.cardText} font-bold tracking-tight`}>ECOS</span>
        {breadcrumbs.map((bc, idx) => (
          <React.Fragment key={idx}>
            <ChevronRight className="w-3.5 h-3.5 opacity-40 mx-0.5 shrink-0" />
            <span className={idx === breadcrumbs.length - 1 ? `${styles.accentText} font-bold` : "opacity-75"}>
              {bc}
            </span>
          </React.Fragment>
        ))}
      </div>

      {/* Center Box: Palantir-style Workspace Tabs */}
      <div className="flex-1 max-w-xl mx-6 overflow-x-auto scrollbar-none flex items-end h-full gap-1 pt-3.5 px-2">
        {openTabs.map((tab) => {
          const isActive = tab.active;
          return (
            <div
              key={tab.id}
              className={`group h-9 px-3.5 rounded-t-md border-t border-x flex items-center gap-2 text-xs font-medium cursor-pointer transition shrink-0 ${
                isActive
                  ? `${styles.cardBg} ${styles.cardBorder} border-t-2 ${styles.accentText} border-t-current shadow-xs`
                  : `${styles.appBg} border-transparent opacity-80 hover:opacity-100`
              }`}
              onClick={() => onTabSelect(tab.id)}
            >
              <span className={isActive ? `${styles.cardText} font-bold` : `${styles.cardTextMuted}`}>{translateTabLabel(tab.label)}</span>
              {/* 关闭 X：旧实现 text-transparent + `hover:${styles.x}` 运行时拼类
                  （Vite 不生成该 utility → hover 无效，X 常态隐形 = 报告 §4.1 P1 死控件）。
                  改为 token 常显 + 命中区从 ~14px 放大到 32px（受 36px 标签条高度上限约束，
                  44px 需整条改版，已在报告登记为待办）。 */}
              <button type="button"
                aria-label={t("topbar.tab.close", { label: translateTabLabel(tab.label) })}
                title={t("topbar.tab.close", { label: translateTabLabel(tab.label) })}
                className={`-mr-1 h-8 w-8 shrink-0 flex items-center justify-center rounded-full ${styles.cardTextMuted} ${styles.sidebarHoverBg} transition`}
                onClick={(e) => {
                  e.stopPropagation();
                  onTabClose(tab.id);
                }}
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          );
        })}
      </div>

      {/* Right Box: Global search trigger & UTC Clock */}
      <div className="flex items-center gap-4 shrink-0">

        {/* ECOS Copilot 入口（2026-10-14 用户裁决：悬浮按钮去除，落位 Topbar 右上角） */}
        <button
          type="button"
          onClick={() => onCopilotOpen?.()}
          className={`h-8 w-8 flex items-center justify-center rounded-lg ${styles.accentBg} ${styles.accentSolidText} transition hover:opacity-90 focus:outline-none focus:ring-2 ${styles.accentRing}`}
          aria-label={t("app.copilot.open")}
          title={t("app.copilot.open")}
        >
          <Bot className="w-4 h-4" />
        </button>

        {/* Theme Selector Switcher */}
        <div className={`flex items-center gap-1.5 ${styles.inputBg} border ${styles.inputBorder} rounded-lg px-2.5 py-1 select-none shrink-0 shadow-3xs transition duration-150`}>
          <Palette className={`w-3.5 h-3.5 ${styles.cardTextMuted}`} />
          <select
            value={activeTheme}
            onChange={(e) => setActiveTheme(e.target.value as ThemeId)}
            className={`bg-transparent border-0 outline-hidden text-[11px] font-bold cursor-pointer font-sans shrink-0 pr-1 ${styles.cardText}`}
          >
            {/* 主题预览项：slate-light / deep-space 两项旧实现用 var(--text-primary)/var(--card)，
                这两个 CSS 变量在全仓从未定义（= 无效声明，选中态文字随系统色发虚）。
                改走 useTheme token；另两项保留各自主题的预览色。 */}
            <option value="slate-light" className={`${styles.inputText} ${styles.inputBg}`}>{t("theme.slate-light")}</option>
            <option value="deep-space" className={`${styles.inputText} ${styles.islandBg}`}>{t("theme.deep-space")}</option>
            <option value="cyber-terminal" className="text-emerald-400 bg-black">{t("theme.cyber-terminal")}</option>
            <option value="royal-purple" className="text-purple-200 bg-[#18112A]">{t("theme.royal-purple")}</option>
          </select>
        </div>

        {/* Language Selector Switcher */}
        <div className={`flex items-center ${styles.inputBg} border ${styles.inputBorder} rounded-lg p-0.5 select-none shrink-0 shadow-3xs transition duration-150`}>
          <button type="button"
            onClick={() => setLocale("zh")}
            id="lang-switch-zh"
            className={`px-2.5 py-1 text-[10px] font-bold font-sans rounded-md transition duration-150 cursor-pointer ${
              locale === "zh"
                ? `${styles.cardBg} ${styles.accentText} shadow-2xs font-extrabold`
                : "opacity-50 hover:opacity-100"
            }`}
          >
            {t("topbar.lang.switch_zh")}
          </button>
          <button type="button"
            onClick={() => setLocale("en")}
            id="lang-switch-en"
            className={`px-2.5 py-1 text-[10px] font-bold font-sans rounded-md transition duration-150 cursor-pointer ${
              locale === "en"
                ? `${styles.cardBg} ${styles.accentText} shadow-2xs font-extrabold`
                : "opacity-50 hover:opacity-100"
            }`}
          >
            {t("topbar.lang.switch_en")}
          </button>
        </div>

        {/* User profile identifier circle with initial values */}
        <div className="relative">
          <div
            id="user-profile-trigger"
            onClick={() => setIsSettingsOpen(!isSettingsOpen)}
            className={`flex items-center gap-3 cursor-pointer group select-none p-1 rounded-lg ${styles.sidebarHoverBg} transition-colors duration-150`}
          >
            <div className="text-right hidden md:block">
              <div className={`text-xs font-bold truncate max-w-[140px] ${styles.cardText}`}>{displayName}</div>
              <div className={`text-[10px] ${styles.cardTextMuted} font-medium`}>{displayClearance}</div>
            </div>
            {/* 头像首字母 = getUserIdentity()/getUserInitials() 真实值；无身份时显式占位 "—"，
                不再硬编常量 "AD"（报告 §4.1 P0 假真理）。 */}
            <div className={`w-9 h-9 border ${styles.cardBorder} rounded-full ${styles.badgeBg} flex items-center justify-center font-bold ${styles.badgeText} text-xs shadow-3xs`} aria-hidden="true">
              {initials}
            </div>
          </div>

          {isSettingsOpen && (
            <>
              {/* Overlay background to dismiss */}
              <div
                className="fixed inset-0 z-40"
                onClick={() => setIsSettingsOpen(false)}
              />
              <div
                id="user-settings-dropdown"
                className={`absolute right-0 mt-2 w-80 max-w-sm rounded-xl border ${styles.cardBorder} ${styles.cardBg} ${styles.cardText} p-4 shadow-xl z-50 space-y-4 animate-fade-in-down duration-150`}
              >
                {/* Header title */}
                <div className={`flex items-center gap-2 pb-2 border-b border-dashed ${styles.cardBorder}`}>
                  <Settings className={`w-4 h-4 ${styles.accentText}`} />
                  <span className="font-bold text-xs uppercase tracking-tight">{t("topbar.user.settings")}</span>
                </div>

                {/* Form controls */}
                <div className="space-y-3.5 text-xs">
                  {/* Email */}
                  <div>
                    <label className={`text-[10px] font-mono font-bold uppercase ${styles.cardTextMuted} block mb-1`}>
                      {t("topbar.user.email")}
                    </label>
                    <input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className={`w-full text-xs p-1.5 px-3 rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-hidden focus:ring-1 ${styles.accentRing} transition`}
                      placeholder={t("topbar.user.email.placeholder")}
                    />
                  </div>

                  {/* Access Clearance & Workstation */}
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className={`text-[10px] font-mono font-bold uppercase ${styles.cardTextMuted} block mb-1`}>
                        {t("topbar.user.clearance")}
                      </label>
                      <input
                        type="text"
                        value={clearance}
                        onChange={(e) => setClearance(e.target.value)}
                        className={`w-full text-xs p-1.5 px-3 rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-hidden focus:ring-1 ${styles.accentRing} transition`}
                        placeholder={t("topbar.role.default")}
                      />
                    </div>
                    <div>
                      <label className={`text-[10px] font-mono font-bold uppercase ${styles.cardTextMuted} block mb-1`}>
                        {t("topbar.user.workstation")}
                      </label>
                      <input
                        type="text"
                        value={workstation}
                        onChange={(e) => setWorkstation(e.target.value)}
                        className={`w-full text-xs p-1.5 px-3 rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-hidden focus:ring-1 ${styles.accentRing} transition`}
                        placeholder={t("topbar.user.workstation.placeholder")}
                      />
                    </div>
                  </div>

                  {/* Audit mode dropdown setup */}
                  <div>
                    <label className={`text-[10px] font-mono font-bold uppercase ${styles.cardTextMuted} block mb-1`}>
                      {t("topbar.user.auditlevel")}
                    </label>
                    <select
                      value={auditMode}
                      onChange={(e) => setAuditMode(e.target.value)}
                      className={`w-full text-xs p-1.5 px-3 rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-hidden focus:ring-1 ${styles.accentRing} cursor-pointer`}
                    >
                      <option value="full" className={`${styles.inputBg} ${styles.cardText}`}>
                        {t("topbar.user.auditlevel.full")}
                      </option>
                      <option value="standard" className={`${styles.inputBg} ${styles.cardText}`}>
                        {t("topbar.user.auditlevel.standard")}
                      </option>
                    </select>
                  </div>

                  {/* Sandbox safeguard checklist switch */}
                  <label className="flex items-start gap-2.5 cursor-pointer py-1 select-none">
                    <input
                      type="checkbox"
                      checked={sandbox}
                      onChange={(e) => setSandbox(e.target.checked)}
                      className={`rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.accentRing} focus:ring-1 mt-0.5 h-3.5 w-3.5`}
                    />
                    <span className={`text-[10.5px] font-semibold leading-normal ${styles.cardText}`}>{t("topbar.user.sandbox")}</span>
                  </label>

                  {/* 安全敏感项自助修改提示（报告 §4.1 P1）：等级/沙盒开关会写入后端安全档案，
                      保存前需二次确认；此处显式声明边界，避免"用户以为改了全局策略"的假真理。 */}
                  <p className={`text-[10px] leading-snug ${styles.warningText} ${styles.warningBg} border ${styles.warningBorder} rounded px-2 py-1.5`}>
                    {t("topbar.user.securityNotice")}
                  </p>
                </div>

                {/* Toast alerts box */}
                {toast && (
                  <div className={`text-[10px] p-2 leading-relaxed rounded border ${styles.successBg} ${styles.successText} ${styles.successBorder} font-mono`} role="status">
                    {toast}
                  </div>
                )}

                {/* Confirm submit and reset buttons — 均经 ConfirmDialog 二次确认门 */}
                <div className={`flex gap-2 pt-2 border-t border-dashed ${styles.cardBorder}`}>
                  <button
                    type="button"
                    onClick={() => setConfirmAction("save")}
                    className={`flex-1 py-1.5 px-2.5 ${styles.accentBg} ${styles.accentSolidText} rounded-lg text-[10.5px] font-bold cursor-pointer transition leading-none h-8`}
                  >
                    {t("topbar.user.save")}
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirmAction("reset")}
                    className={`py-1.5 px-2 border ${styles.cardBorder} ${styles.cardTextMuted} ${styles.sidebarHoverBg} rounded-lg text-[10.5px] font-semibold cursor-pointer transition leading-none h-8`}
                  >
                    {t("topbar.user.reset")}
                  </button>
                </div>

                {/* Logout — 走 services/auth performLogout() 单源清会话，
                    不再手删 localStorage 个别 key（旧实现漏删 roles/user_id，且硬跳 /#/login） */}
                <button
                  type="button"
                  onClick={() => {
                    setIsSettingsOpen(false);
                    performLogout();
                  }}
                  className={`w-full py-2 ${styles.dangerBg} ${styles.dangerText} border ${styles.dangerBorder} rounded-lg text-[10.5px] font-bold cursor-pointer transition leading-none h-8 mt-1`}
                >
                  {t("topbar.logout")}
                </button>

              </div>
            </>
          )}

        </div>

      </div>

      {/* 敏感设置二次确认门（报告 §4.1 P1）：安全准入等级 / 沙盒审查 / 工作区缓存重置 */}
      <ConfirmDialog
        open={confirmAction !== null}
        variant={confirmAction === "reset" ? "danger" : "warning"}
        title={t(confirmAction === "reset" ? "topbar.confirm.reset.title" : "topbar.confirm.save.title")}
        message={t(confirmAction === "reset" ? "topbar.confirm.reset.message" : "topbar.confirm.save.message")}
        confirmText={t("common.confirm")}
        cancelText={t("common.cancel")}
        onCancel={() => setConfirmAction(null)}
        onConfirm={() => {
          const action = confirmAction;
          setConfirmAction(null);
          if (action === "save") void handleSaveSettings();
          else if (action === "reset") void handleResetCache();
        }}
      />
    </header>
  );
}
