/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from "react";
import { X, ChevronRight, Palette, Settings, Menu } from "lucide-react";
import { useLanguage } from "./LanguageContext";
import { useTheme, ThemeId } from "./ThemeContext";

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
}

export default function Topbar({
  currentView,
  onSearchOpen,
  openTabs,
  onTabSelect,
  onTabClose,
  onMenuToggle
}: TopbarProps) {
  const { locale, setLocale, t } = useLanguage();
  const { activeTheme, setActiveTheme, styles } = useTheme();

  // Settings values loading from localStorage or default
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [email, setEmail] = useState(() => localStorage.getItem("ecos_user_email") || "guorongxiao@gmail.com");
  const [clearance, setClearance] = useState(() => localStorage.getItem("ecos_user_clearance") || "");
  const [workstation, setWorkstation] = useState(() => localStorage.getItem("ecos_user_workstation") || "WS-COSMOS-09");
  const [auditMode, setAuditMode] = useState(() => localStorage.getItem("ecos_user_audit") || "full");
  const [sandbox, setSandbox] = useState(() => {
    const val = localStorage.getItem("ecos_user_sandbox");
    return val !== "false"; // default to true
  });
  const [toast, setToast] = useState<string | null>(null);

  const displayClearance = clearance || (locale === "zh" ? "4级高阶授权专家" : "Commander Level 4");

  const handleSaveSettings = () => {
    localStorage.setItem("ecos_user_email", email);
    localStorage.setItem("ecos_user_clearance", clearance);
    localStorage.setItem("ecos_user_workstation", workstation);
    localStorage.setItem("ecos_user_audit", auditMode);
    localStorage.setItem("ecos_user_sandbox", String(sandbox));
    
    setToast(t("topbar.user.save.success"));
    setTimeout(() => {
      setToast(null);
    }, 4000);
  };

  const handleResetCache = () => {
    localStorage.removeItem("ecos_user_email");
    localStorage.removeItem("ecos_user_clearance");
    localStorage.removeItem("ecos_user_workstation");
    localStorage.removeItem("ecos_user_audit");
    localStorage.removeItem("ecos_user_sandbox");
    
    setEmail("guorongxiao@gmail.com");
    setClearance("");
    setWorkstation("WS-COSMOS-09");
    setAuditMode("full");
    setSandbox(true);
    
    setToast(t("topbar.user.reset.success"));
    setTimeout(() => {
      setToast(null);
    }, 4000);
  };


  const getViewBreadcrumbs = () => {
    // 总览组
    if (currentView === "world_model" || currentView === "mission_control")
      return [locale === "zh" ? "总览" : "Overview", t("nav.item.strategic_goals")];
    if (currentView === "monitor")
      return [locale === "zh" ? "总览" : "Overview", t("nav.item.monitor")];
    if (currentView === "security-center" || currentView === "security")
      return [locale === "zh" ? "总览" : "Overview", locale === "zh" ? "安全中心" : "Security Center"];
    // 资源概览组
    if (currentView === "marketplace")
      return [locale === "zh" ? "资源概览" : "Resources", locale === "zh" ? "数据市场" : "Marketplace"];
    if (currentView === "knowledge_graph")
      return [locale === "zh" ? "资源概览" : "Resources", locale === "zh" ? "知识图谱" : "Knowledge Graph"];
    if (currentView === "ops_apps")
      return [locale === "zh" ? "资源概览" : "Resources", locale === "zh" ? "运营应用" : "Operational Apps"];
    // 系统管理组
    if (currentView === "iam")
      return [locale === "zh" ? "系统管理" : "System", locale === "zh" ? "用户管理" : "IAM User Mgmt"];
    if (currentView === "dict")
      return [locale === "zh" ? "系统管理" : "System", locale === "zh" ? "数据字典" : "Data Dictionary"];
    if (currentView === "system-config")
      return [locale === "zh" ? "系统管理" : "System", locale === "zh" ? "系统配置" : "System Config"];
    if (currentView === "tenants")
      return [locale === "zh" ? "系统管理" : "System", locale === "zh" ? "租户管理" : "Tenant Mgmt"];
    // 产品功能组
    if (currentView === "project_workbench")
      return [locale === "zh" ? "产品功能" : "Product", locale === "zh" ? "项目工作台" : "Project Workbench"];
    if (currentView === "agent_studio" || currentView === "ai-workbench")
      return [locale === "zh" ? "产品功能" : "Product", locale === "zh" ? "AI工作台" : "AI Workbench"];
    if (currentView === "knowledge_view")
      return [locale === "zh" ? "产品功能" : "Product", locale === "zh" ? "知识工作台" : "Knowledge Workbench"];
    if (currentView === "ontology_workbench" || currentView === "ontology" || currentView === "business-workbench")
      return [locale === "zh" ? "产品功能" : "Product", locale === "zh" ? "本体工作台" : "Ontology Workbench"];
    if (currentView === "data-workbench" || currentView === "catalog" || currentView === "dataset_explorer" || currentView === "lineage" || currentView === "pipeline" || currentView === "workbook")
      return [locale === "zh" ? "产品功能" : "Product", locale === "zh" ? "数据工作台" : "Data Workbench"];
    // 其他
    if (currentView === "workshop" || currentView === "workflow_designer")
      return [locale === "zh" ? "产品功能" : "Product", locale === "zh" ? "工作流设计" : "Workshop"];
    if (currentView === "biz_dashboard")
      return [locale === "zh" ? "运营应用" : "Apps", locale === "zh" ? "信科数据仪表盘" : "Biz Dashboard"];
    if (currentView === "project_tracker")
      return [locale === "zh" ? "运营应用" : "Apps", locale === "zh" ? "项目跟踪" : "Project Tracker"];
    if (currentView === "contract_manager")
      return [locale === "zh" ? "运营应用" : "Apps", locale === "zh" ? "合同管理" : "Contract Manager"];
    if (currentView === "ops_dashboard")
      return [locale === "zh" ? "运营应用" : "Apps", locale === "zh" ? "产值分配看板" : "Ops Dashboard"];
    if (currentView === "objects")
      return [locale === "zh" ? "业务工作台" : "Business", locale === "zh" ? "对象浏览器" : "Object Explorer"];
    if (currentView === "glossary")
      return [locale === "zh" ? "系统管理" : "System", locale === "zh" ? "术语管理" : "Glossary"];
    if (currentView === "guardrails")
      return [locale === "zh" ? "安全中心" : "Security", locale === "zh" ? "安全护栏" : "Guardrails"];
    if (currentView === "kanban")
      return [locale === "zh" ? "系统管理" : "System", locale === "zh" ? "项目看板" : "Kanban"];
    if (currentView === "engine-tasks" || currentView === "task_center")
      return [locale === "zh" ? "系统管理" : "System", locale === "zh" ? "任务中心" : "Task Center"];
    return [locale === "zh" ? "总览" : "Overview", currentView];
  };

  const translateTabLabel = (label: string) => {
    // If already in locale language (most labels are Chinese now), return as-is
    if (label === "认知蓝图") return locale === "zh" ? "认知蓝图" : "Mission Control";
    if (label === "监控中心") return locale === "zh" ? "监控中心" : "Monitoring Center";
    if (label === "安全中心") return locale === "zh" ? "安全中心" : "Security Center";
    if (label === "数据市场") return locale === "zh" ? "数据市场" : "Marketplace";
    if (label === "企业知识图谱") return locale === "zh" ? "企业知识图谱" : "Knowledge Graph";
    if (label === "知识工作台") return locale === "zh" ? "知识工作台" : "Knowledge Workbench";
    if (label === "运营应用") return locale === "zh" ? "运营应用" : "Operational Apps";
    if (label === "用户管理") return locale === "zh" ? "用户管理" : "IAM";
    if (label === "数据字典") return locale === "zh" ? "数据字典" : "Data Dictionary";
    if (label === "系统配置") return locale === "zh" ? "系统配置" : "System Config";
    if (label === "租户管理") return locale === "zh" ? "租户管理" : "Tenants";
    if (label === "项目工作台") return locale === "zh" ? "项目工作台" : "Project Workbench";
    if (label === "AI工作台") return locale === "zh" ? "AI工作台" : "AI Workbench";
    if (label === "Agent网格") return locale === "zh" ? "Agent网格" : "Agent Mesh";
    if (label === "Agent构建器") return locale === "zh" ? "Agent构建器" : "Agent Builder";
    if (label === "Agent测试") return locale === "zh" ? "Agent测试" : "Agent Test";
    if (label === "本体工作台") return locale === "zh" ? "本体工作台" : "Ontology Workbench";
    if (label === "本体浏览器") return locale === "zh" ? "本体浏览器" : "Ontology Explorer";
    if (label === "本体设计器") return locale === "zh" ? "本体设计器" : "Ontology Designer";
    if (label === "业务工作台") return locale === "zh" ? "业务工作台" : "Business Workbench";
    if (label === "数据工作台") return locale === "zh" ? "数据工作台" : "Data Workbench";
    if (label === "数据目录") return locale === "zh" ? "数据目录" : "Data Catalog";
    if (label === "数据集浏览器") return locale === "zh" ? "数据集浏览器" : "Dataset Explorer";
    if (label === "管道构建器") return locale === "zh" ? "管道构建器" : "Pipeline Builder";
    if (label === "代码工作簿") return locale === "zh" ? "代码工作簿" : "Code Workbook";
    if (label === "数据血缘") return locale === "zh" ? "数据血缘" : "Data Lineage";
    if (label === "物理表注册") return locale === "zh" ? "物理表注册" : "Data Sources";
    if (label === "工作流设计") return locale === "zh" ? "工作流设计" : "Workshop";
    if (label === "数据浏览器") return locale === "zh" ? "数据浏览器" : "Object Explorer";
    if (label === "数据质量") return locale === "zh" ? "数据质量" : "Data Quality";
    if (label === "术语表") return locale === "zh" ? "术语表" : "Glossary";
    if (label === "安全护栏") return locale === "zh" ? "安全护栏" : "Guardrails";
    if (label === "信科数据仪表盘") return locale === "zh" ? "信科数据仪表盘" : "Biz Dashboard";
    if (label === "项目跟踪") return locale === "zh" ? "项目跟踪" : "Project Tracker";
    if (label === "合同管理") return locale === "zh" ? "合同管理" : "Contract Manager";
    if (label === "产值分配看板") return locale === "zh" ? "产值分配看板" : "Ops Dashboard";
    if (label === "项目看板") return locale === "zh" ? "项目看板" : "Kanban";
    if (label === "任务中心") return locale === "zh" ? "任务中心" : "Task Center";
    if (label === "战略目标") return locale === "zh" ? "战略目标" : "Strategic Goals";
    // Backward compat: old English labels
    return label;
  };

  const breadcrumbs = getViewBreadcrumbs();

  return (
    <header className={`h-[64px] ${styles.cardBg} border-b ${styles.cardBorder} flex items-center justify-between px-6 select-none shrink-0 ${styles.cardText} font-sans shadow-xs transition-colors duration-150`}>
      
      {/* Left Box: Hamburger (mobile) + Breadcrumb mapping */}
      <div className={`flex items-center gap-1.5 text-xs font-mono ${styles.cardTextMuted} shrink-0`}>
        {/* Hamburger menu — visible only on mobile */}
        <button
          onClick={onMenuToggle}
          className={`md:hidden p-1.5 -ml-1 rounded-md hover:bg-black/5 dark:hover:bg-white/10 transition-colors ${styles.cardText}`}
          aria-label="Toggle sidebar"
        >
          <Menu className="w-5 h-5" />
        </button>
        <span className={`${styles.cardText} font-bold tracking-tight`}>ECOS</span>
        {breadcrumbs.map((bc, idx) => (
          <React.Fragment key={idx}>
            <ChevronRight className="w-3.5 h-3.5 opacity-40 mx-0.5 shrink-0" />
            <span className={idx === breadcrumbs.length - 1 ? "text-[#3B82F6] font-bold" : "opacity-75"}>
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
                  ? `${styles.cardBg} ${styles.cardBorder} border-t-2 border-t-indigo-500 text-indigo-500 shadow-xs dark:border-t-emerald-500 dark:text-emerald-400`
                  : `${styles.appBg} border-transparent opacity-80 hover:opacity-100`
              }`}
              onClick={() => onTabSelect(tab.id)}
            >
              <span className={isActive ? `${styles.cardText} font-bold` : `${styles.cardTextMuted}`}>{translateTabLabel(tab.label)}</span>
              <button
                className="p-0.5 rounded-full text-transparent group-hover:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-800 transition shrink-0"
                onClick={(e) => {
                  e.stopPropagation();
                  onTabClose(tab.id);
                }}
              >
                <X className="w-2.5 h-2.5" />
              </button>
            </div>
          );
        })}
      </div>

      {/* Right Box: Global search trigger & UTC Clock */}
      <div className="flex items-center gap-4 shrink-0">
        
        {/* Theme Selector Switcher */}
        <div className={`flex items-center gap-1.5 ${styles.inputBg} border ${styles.inputBorder} rounded-lg px-2.5 py-1 select-none shrink-0 shadow-3xs transition duration-150`}>
          <Palette className={`w-3.5 h-3.5 ${styles.cardTextMuted}`} />
          <select
            value={activeTheme}
            onChange={(e) => setActiveTheme(e.target.value as ThemeId)}
            className={`bg-transparent border-0 outline-hidden text-[11px] font-bold cursor-pointer font-sans shrink-0 pr-1 ${styles.cardText}`}
          >
            <option value="slate-light" className="text-slate-800 bg-white">{t("theme.slate-light")}</option>
            <option value="deep-space" className="text-slate-100 bg-[#111827]">{t("theme.deep-space")}</option>
            <option value="cyber-terminal" className="text-emerald-400 bg-black">{t("theme.cyber-terminal")}</option>
            <option value="royal-purple" className="text-purple-200 bg-[#18112A]">{t("theme.royal-purple")}</option>
          </select>
        </div>

        {/* Language Selector Switcher */}
        <div className={`flex items-center ${styles.inputBg} border ${styles.inputBorder} rounded-lg p-0.5 select-none shrink-0 shadow-3xs transition duration-150`}>
          <button
            onClick={() => setLocale("zh")}
            id="lang-switch-zh"
            className={`px-2.5 py-1 text-[10px] font-bold font-sans rounded-md transition duration-150 cursor-pointer ${
              locale === "zh"
                ? "bg-white dark:bg-slate-700 text-indigo-700 dark:text-indigo-300 shadow-2xs font-extrabold"
                : "opacity-50 hover:opacity-100"
            }`}
          >
            中文
          </button>
          <button
            onClick={() => setLocale("en")}
            id="lang-switch-en"
            className={`px-2.5 py-1 text-[10px] font-bold font-sans rounded-md transition duration-150 cursor-pointer ${
              locale === "en"
                ? "bg-white dark:bg-slate-700 text-indigo-700 dark:text-indigo-300 shadow-2xs font-extrabold"
                : "opacity-50 hover:opacity-100"
            }`}
          >
            EN
          </button>
        </div>

        {/* User profile identifier circle with initial values */}
        <div className="relative">
          <div 
            id="user-profile-trigger"
            onClick={() => setIsSettingsOpen(!isSettingsOpen)}
            className={`flex items-center gap-3 cursor-pointer group select-none p-1 rounded-lg hover:bg-black/5 dark:hover:bg-white/5 transition-colors duration-150`}
          >
            <div className="text-right hidden md:block">
              <div className={`text-xs font-bold ${styles.cardText} group-hover:text-indigo-500 dark:group-hover:text-emerald-400 transition-colors`}>{t("topbar.admin")}</div>
              <div className={`text-[10px] ${styles.cardTextMuted} font-medium`}>{displayClearance}</div>
            </div>
            <div className={`w-9 h-9 border ${styles.cardBorder} rounded-full ${styles.badgeBg} flex items-center justify-center font-bold ${styles.badgeText} text-xs transition group-hover:border-indigo-500 dark:group-hover:border-emerald-500 shadow-3xs`}>
              AD
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
                className={`absolute right-0 mt-2 w-80 max-w-sm rounded-xl border ${styles.cardBorder} ${styles.cardBg} ${styles.cardText} p-4 shadow-xl z-50 space-y-4 animate-in fade-in slide-in-from-top-2 duration-150`}
              >
                {/* Header title */}
                <div className={`flex items-center gap-2 pb-2 border-b border-dashed ${styles.cardBorder}`}>
                  <Settings className="w-4 h-4 text-indigo-500" />
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
                      className={`w-full text-xs p-1.5 px-3 rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-hidden focus:ring-1 focus:ring-indigo-500/50 transition`}
                      placeholder="e.g. user@enterprise.com"
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
                        className={`w-full text-xs p-1.5 px-3 rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-hidden focus:ring-1 focus:ring-indigo-500/50 transition`}
                        placeholder={locale === "zh" ? "4级高阶授权专家" : "Commander Level 4"}
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
                        className={`w-full text-xs p-1.5 px-3 rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-hidden focus:ring-1 focus:ring-indigo-500/50 transition`}
                        placeholder="WS-COSMOS-09"
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
                      className={`w-full text-xs p-1.5 px-3 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 ${styles.cardText} focus:outline-hidden focus:ring-1 focus:ring-indigo-500/50 cursor-pointer`}
                    >
                      <option value="full" className="text-slate-800 bg-white dark:text-slate-100 dark:bg-slate-900">
                        {t("topbar.user.auditlevel.full")}
                      </option>
                      <option value="standard" className="text-slate-800 bg-white dark:text-slate-100 dark:bg-slate-900">
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
                      className="rounded border-slate-300 text-indigo-650 focus:ring-indigo-500 mt-0.5 h-3.5 w-3.5"
                    />
                    <span className={`text-[10.5px] font-semibold leading-normal ${styles.cardText}`}>{t("topbar.user.sandbox")}</span>
                  </label>
                </div>

                {/* Toast alerts box */}
                {toast && (
                  <div className={`text-[10px] p-2 leading-relaxed rounded border bg-emerald-500/10 text-emerald-500 border-emerald-500/20 font-mono`}>
                    {toast}
                  </div>
                )}

                {/* Confirm submit and reset buttons */}
                <div className={`flex gap-2 pt-2 border-t border-dashed ${styles.cardBorder}`}>
                  <button 
                    type="button"
                    onClick={handleSaveSettings}
                    className="flex-1 py-1.5 px-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-[10.5px] font-bold cursor-pointer transition leading-none h-8"
                  >
                    {t("topbar.user.save")}
                  </button>
                  <button 
                    type="button"
                    onClick={handleResetCache}
                    className={`py-1.5 px-2 border ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/5 rounded-lg text-[10.5px] font-semibold cursor-pointer transition leading-none h-8`}
                  >
                    {t("topbar.user.reset")}
                  </button>
                </div>

                {/* Logout */}
                <button
                  type="button"
                  onClick={() => {
                    localStorage.removeItem("ecos_token");
                    localStorage.removeItem("ecos_user");
                    window.location.href = "/#/login";
                  }}
                  className="w-full py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-[10.5px] font-bold cursor-pointer transition leading-none h-8 mt-1"
                >
                  {locale === "zh" ? "退出登录" : "Logout"}
                </button>

              </div>
            </>
          )}

        </div>

      </div>
    </header>
  );
}
