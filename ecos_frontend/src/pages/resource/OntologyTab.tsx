/**
 * OntologyTab — 资源中心「本体图谱」页签（2026-10-14 菜单重构）。
 *
 * 降级裁决（登记）：本体工作台本体视图嵌带 workbench 深链 UI（Topbar 内 Tab 条、
 * deep-link banner），直接内嵌会渲染失效控件；故本页 = 内嵌 GraphExplorerTab
 * （zero-props，真实知识图谱探索器——本体对象间关系视图）+ 本体工作台入口卡。
 * 完整对象/关系/属性编辑仍在 #/ontology_workbench（菜单「工作区·本体工作台」）。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, ArrowRight, Network } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import GraphExplorerTab from "../knowledge/tabs/GraphExplorerTab";

export default function OntologyTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const navigate = useNavigate();
  const [disclaimerOpen, setDisclaimerOpen] = useState(true);

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-3 p-4 overflow-y-auto">
      <p className={`text-xs ${styles.cardTextMuted}`}>{t("platform.resource.ontology.desc")}</p>

      {/* 降级注记（可折叠）：假真理红线——本 Tab 不内嵌完整本体工作台，入口直跳正主 */}
      <div className={`rounded-md border px-3 py-2 text-[11px] ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
        <button
          type="button"
          onClick={() => setDisclaimerOpen((v) => !v)}
          className="flex items-center gap-1.5 font-bold cursor-pointer"
        >
          <AlertTriangle size={13} /> {t("platform.resource.knowledge.degraded")}
        </button>
        {disclaimerOpen && (
          <div className="mt-2 flex items-center justify-between gap-2 flex-wrap">
            <span className={`${styles.cardTextMuted}`}>{t("platform.resource.ontology.desc")}</span>
            <button
              type="button"
              onClick={() => { navigate("/ontology_workbench"); setDisclaimerOpen(false); }}
              className={`inline-flex items-center gap-1 rounded border px-2.5 py-1 cursor-pointer ${styles.accentText} ${styles.accentBorder}`}
            >
              {t("app.tab.ontology_workbench")} <ArrowRight size={12} />
            </button>
          </div>
        )}
      </div>

      {/* 内嵌图谱探索器（真实数据：/api/v1/knowledge/graph 等，fail-closed 错误态自渲染） */}
      <div className="flex-1 min-h-40">
        <GraphExplorerTab />
      </div>
    </div>
  );
}
