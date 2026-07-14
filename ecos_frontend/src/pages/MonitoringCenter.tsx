/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from "react";
import { BarChart3, CpuIcon, Shield, Database, Network, Brain, ChevronDown, Activity } from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import BasicMonitoringTab from "./monitoring/tabs/BasicMonitoringTab";
import DigitalTwinTab from "./monitoring/tabs/DigitalTwinTab";
import EngineMonitor from "./EngineMonitor";

// ── Main MonitoringCenter with Tabs ────────────────────────
interface TabDef {
  id: string;
  label: string;
  labelZh: string;
  icon: React.ComponentType<{ className?: string }>;
}

const TABS: TabDef[] = [
  { id: "metrics", label: "Metrics", labelZh: "基础指标", icon: BarChart3 },
  { id: "engines", label: "Engines", labelZh: "引擎监控", icon: Activity },
  { id: "twins", label: "Digital Twin", labelZh: "数字孪生", icon: CpuIcon },
];

// ── Engine drawer config ────────────────────────────────────
interface EngineDrawerDef {
  id: string;
  label: string;
  labelZh: string;
  icon: React.ComponentType<{ className?: string }>;
  engine: "security" | "data" | "ontology" | "cognitive";
}

const ENGINE_DRAWERS: EngineDrawerDef[] = [
  { id: "eng-security", label: "Security Engine", labelZh: "安全引擎", icon: Shield, engine: "security" },
  { id: "eng-data", label: "Data Engine", labelZh: "数据引擎", icon: Database, engine: "data" },
  { id: "eng-ontology", label: "Ontology Engine", labelZh: "本体引擎", icon: Network, engine: "ontology" },
  { id: "eng-cognitive", label: "Cognitive Engine", labelZh: "认知引擎", icon: Brain, engine: "cognitive" },
];

export default function MonitoringCenter() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [activeTab, setActiveTab] = useState(TABS[0].id);
  const [expandedEngines, setExpandedEngines] = useState<Set<string>>(new Set());

  const tl = (zh: string, en: string) => locale === "zh" ? zh : en;

  const toggleEngine = (id: string) => {
    setExpandedEngines(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Tab Bar */}
      <div className={`flex shrink-0 border-b ${styles.appBorder} bg-white/50 dark:bg-slate-900/30 px-2`}>
        {TABS.map(tab => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-1.5 px-4 py-2.5 text-xs font-medium transition-all border-b-2 -mb-px whitespace-nowrap ${
                isActive
                  ? "border-indigo-500 text-indigo-600 dark:text-indigo-400 bg-indigo-50/50 dark:bg-indigo-900/20"
                  : "border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300 hover:bg-slate-100/50 dark:hover:bg-slate-800/30"
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              <span>{tl(tab.labelZh, tab.label)}</span>
            </button>
          );
        })}
      </div>

      {/* Content Area */}
      <div className="flex-1 overflow-auto min-h-0">
        {activeTab === "metrics" && <BasicMonitoringTab />}
        {activeTab === "engines" && (
          <div className="p-4 space-y-3">
            <div className="text-sm font-semibold text-slate-500 dark:text-slate-400 mb-2">
              {tl("四引擎健康状态监控 — 点击展开查看详情", "Four-engine health monitoring — click to expand details")}
            </div>
            {ENGINE_DRAWERS.map(drawer => {
              const Icon = drawer.icon;
              const isExpanded = expandedEngines.has(drawer.id);
              return (
                <div
                  key={drawer.id}
                  className={`rounded-lg border ${styles.appBorder} bg-white dark:bg-slate-800 overflow-hidden`}
                >
                  <button
                    onClick={() => toggleEngine(drawer.id)}
                    className="w-full flex items-center gap-3 px-4 py-3 hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors"
                  >
                    <Icon className="w-5 h-5 text-indigo-500" />
                    <span className="flex-1 text-left font-medium text-sm">
                      {tl(drawer.labelZh, drawer.label)}
                    </span>
                    <ChevronDown
                      className={`w-4 h-4 text-slate-400 transition-transform duration-200 ${
                        isExpanded ? "rotate-180" : ""
                      }`}
                    />
                  </button>
                  {isExpanded && (
                    <div className="border-t border-slate-200 dark:border-slate-700">
                      <EngineMonitor engine={drawer.engine} />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
        {activeTab === "twins" && <DigitalTwinTab />}
      </div>
    </div>
  );
}
