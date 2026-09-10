/**
 * PMO-48-A T5（Phase 1/2）+ PMO-48-C T14（Phase 3）
 * 数据质量中心 · 主入口路由页
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 独立顶级路由 /dq_dashboard。
 * Phase 3 T14 扩展：3 Tab → 6 Tab
 *   规则中心 / 6 维度 / 监控调度 / 告警中心 / 工单中心 / 自检
 *
 * Tab 状态支持 ?tab=xxx 路径参数切换（如省从目录树跳转）。
 * Tab 组件懒加载，主入口 < 120 行。
 */

import React, { lazy, Suspense, useCallback, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  Activity,
  Bell,
  Briefcase,
  CalendarClock,
  Gauge,
  Shield,
} from "lucide-react";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";

// 懒加载各 Tab 子组件
const RuleCenterTab = lazy(() => import("./data-quality/RuleCenterTab"));
const DimensionTab = lazy(() => import("./data-quality/DimensionTab"));
const ScheduleTab = lazy(() => import("./data-quality/ScheduleTab"));
const AlertCenterTab = lazy(() => import("./data-quality/AlertCenterTab"));
const WorkOrderTab = lazy(() => import("./data-quality/WorkOrderTab"));
const SelfCheckTab = lazy(() => import("./data-quality/SelfCheckTab"));

/** Tab ID 枚举 */
type TabId = "rules" | "dimension" | "schedule" | "alerts" | "workOrders" | "selfcheck";

/** Tab 配置 */
const TABS: {
  id: TabId;
  icon: React.ComponentType<{ className?: string }>;
  i18nKey: string;
}[] = [
  { id: "rules", icon: Shield, i18nKey: "dw.dqRule.tabRuleCenter" },
  { id: "dimension", icon: Gauge, i18nKey: "dw.dqRule.tabDimension" },
  { id: "schedule", icon: CalendarClock, i18nKey: "dw.dqRule.schedules.tab.name" },
  { id: "alerts", icon: Bell, i18nKey: "dw.dqRule.alerts.tab.name" },
  { id: "workOrders", icon: Briefcase, i18nKey: "dw.dqRule.workOrders.tab.name" },
  { id: "selfcheck", icon: Activity, i18nKey: "dw.dqRule.tabSelfCheck" },
];

/** 有效 TabId 集合 */
const VALID_TAB_IDS = new Set(TABS.map((t) => t.id));

/** Suspense fallback */
function TabFallback() {
  return (
    <div className="h-full flex items-center justify-center">
      <div className="h-5 w-5 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
    </div>
  );
}

export default function DataQualityDashboard() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [searchParams, setSearchParams] = useSearchParams();

  // 从 ?tab= 参数读取初始 Tab（支持外部跳转）
  const tabParam = searchParams.get("tab");
  const initialTab: TabId =
    tabParam && VALID_TAB_IDS.has(tabParam as TabId) ? (tabParam as TabId) : "rules";

  const [activeTab, setActiveTab] = useState<TabId>(initialTab);

  /** 切换 Tab 时同步 URL（让浏览器/分享链接支持深链） */
  const handleTabChange = useCallback(
    (id: TabId) => {
      setActiveTab(id);
      const next = new URLSearchParams(searchParams);
      next.set("tab", id);
      next.delete("table"); // 清除 table 参数（跳转语义一次性使用）
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  // CatalogContextMenu "配置 DQ 规则" 跳转带 ?table=xxx
  const tableFilter = searchParams.get("table") ?? undefined;

  return (
    <div className={`flex-1 min-h-0 flex flex-col ${styles.appBg}`}>
      {/* 顶部标题区 */}
      <div className="shrink-0 px-6 pt-4 pb-0 space-y-1">
        <h1 className="font-bold text-xl tracking-tight">{t("app.tab.dq_dashboard")}</h1>
        <p className={`text-xs ${styles.cardTextMuted}`}>{t("dw.dqRule.subtitle")}</p>
      </div>

      {/* Tab 切换条 */}
      <div className={`${styles.cardBg} border-b ${styles.cardBorder} shrink-0`}>
        <div className="max-w-7xl mx-auto px-6 flex items-end gap-0.5 pt-3 overflow-x-auto">
          {TABS.map((tab) => {
            const TabIcon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => handleTabChange(tab.id)}
                className={`h-9 px-3 rounded-t-md border-t-2 flex items-center gap-2 text-xs font-medium whitespace-nowrap cursor-pointer transition ${
                  isActive
                    ? `bg-transparent border-transparent ${styles.cardText} font-bold ${styles.accentBorder} border-t-indigo-500 dark:border-t-emerald-500 dark:text-emerald-400`
                    : "border-transparent opacity-70 hover:opacity-100"
                } ${!isActive ? styles.cardTextMuted : ""}`}
              >
                <TabIcon className={`w-3.5 h-3.5 shrink-0 ${isActive ? styles.accentText : ""}`} />
                {t(tab.i18nKey)}
              </button>
            );
          })}
        </div>
      </div>

      {/* 当前 Tab 渲染 */}
      <div className="flex-1 min-h-0 overflow-hidden px-6 py-4">
        <div className="max-w-7xl mx-auto h-full flex flex-col">
          <Suspense fallback={<TabFallback />}>
            {activeTab === "rules" && <RuleCenterTab initialTableFilter={tableFilter} />}
            {activeTab === "dimension" && <DimensionTab />}
            {activeTab === "schedule" && <ScheduleTab />}
            {activeTab === "alerts" && <AlertCenterTab />}
            {activeTab === "workOrders" && <WorkOrderTab />}
            {activeTab === "selfcheck" && <SelfCheckTab />}
          </Suspense>
        </div>
      </div>
    </div>
  );
}
