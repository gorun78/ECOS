/**
 * PlatformGovernance — 平台治理（#/platform-governance，2026-10-14 菜单重构·控制面板组）。
 *
 * 用户裁决（参考 release/v2.1-alpha 平台治理处置页）三 Tab：
 *   部署情况 = 各服务/引擎健康矩阵（真实探测，fail-closed）
 *   引擎配置 = 按 config_group 浏览 sysman 系统配置（正主，只读）
 *   平台自检 = monitor /health 全量自检 + 网关诊断/遥测 双入口 CTA
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useState } from "react";
import { Activity, SlidersHorizontal, ShieldCheck } from "lucide-react";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";
import DeploymentTab from "./governance/DeploymentTab";
import EngineConfigTab from "./governance/EngineConfigTab";
import DiagnosticsTab from "./governance/DiagnosticsTab";

type TabId = "deployment" | "config" | "diagnostics";

const TABS: ReadonlyArray<{ id: TabId; icon: typeof Activity; labelKey: string }> = [
  { id: "deployment", icon: Activity, labelKey: "platform.governance.tab.deployment" },
  { id: "config", icon: SlidersHorizontal, labelKey: "platform.governance.tab.config" },
  { id: "diagnostics", icon: ShieldCheck, labelKey: "platform.governance.tab.diagnostics" },
];

export default function PlatformGovernance() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [tab, setTab] = useState<TabId>("deployment");

  return (
    <div className={`flex-1 flex flex-col min-h-0 ${styles.appBg} ${styles.appText}`}>
      {/* 页头 */}
      <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
        <div className="flex items-center gap-2 flex-wrap">
          <span className={`p-1.5 rounded-md flex items-center justify-center ${styles.accentBg} ${styles.cardText}`}>
            <ShieldCheck size={16} />
          </span>
          <div className="min-w-0">
            <h1 className={`text-lg font-bold tracking-tight ${styles.appText}`}>{t("platform.governance.title")}</h1>
            <p className={`text-[11px] ${styles.cardTextMuted} -mt-0.5`}>{t("platform.governance.subtitle")}</p>
          </div>
          <div className="flex-1 min-w-2" />
          {/* Tab 切换（移动端横滚） */}
          <div className="flex items-center gap-1 overflow-x-auto max-w-full" role="tablist" aria-label={t("platform.governance.title")}>
            {TABS.map(({ id, icon: Icon, labelKey }) => {
              const active = tab === id;
              return (
                <button
                  key={id}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => setTab(id)}
                  className={`shrink-0 inline-flex items-center gap-1.5 rounded border px-2.5 py-1 text-[11px] font-semibold cursor-pointer transition-colors ${styles.cardBorder} ${active ? `${styles.accentText} ${styles.accentBorder}` : `${styles.cardTextMuted} hover:opacity-80`}`}
                >
                  <Icon size={13} />
                  {t(labelKey)}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* Tab 内容区：各 Tab 独立文件（前端规范 §十 收口） */}
      <div className="flex-1 min-h-0 flex flex-col">
        {tab === "deployment" && <DeploymentTab />}
        {tab === "config" && <EngineConfigTab />}
        {tab === "diagnostics" && <DiagnosticsTab />}
      </div>
    </div>
  );
}
