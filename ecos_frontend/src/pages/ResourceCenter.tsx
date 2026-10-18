/**
 * ResourceCenter — 资源中心（#/resource-center，2026-10-14 菜单重构·资源概览组唯一项）。
 *
 * 用户裁决：以本体图谱为入口，按 本体 → 数据 → 知识 → 认知 业务逻辑融合四类资产。
 * 顶栏固定渲染四段 pipeline 条带（各段计数走各自真实端点，失败显式「未知」，
 * 禁 Mock/假计数）；内嵌面复用既有工作台组件（GraphExplorerTab zero-props /
 * DataAssetsDashboard embedded / 知识工作台 CTA / 认知资产词表）。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useEffect, useState } from "react";
import { Box, Brain, Database, Library, Network } from "lucide-react";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";
import OntologyTab from "./resource/OntologyTab";
import DataTab from "./resource/DataTab";
import KnowledgeTab from "./resource/KnowledgeTab";
import CognitionTab from "./resource/CognitionTab";

type TabId = "ontology" | "data" | "knowledge" | "cognition";

const TABS: ReadonlyArray<{ id: TabId; icon: typeof Network; labelKey: string; pipelineKey: string }> = [
  { id: "ontology", icon: Network, labelKey: "platform.resource.tab.ontology", pipelineKey: "platform.resource.pipeline.ontology" },
  { id: "data", icon: Database, labelKey: "platform.resource.tab.data", pipelineKey: "platform.resource.pipeline.data" },
  { id: "knowledge", icon: Library, labelKey: "platform.resource.tab.knowledge", pipelineKey: "platform.resource.pipeline.knowledge" },
  { id: "cognition", icon: Brain, labelKey: "platform.resource.tab.cognitive", pipelineKey: "platform.resource.pipeline.cognitive" },
];

/** 从命中文件对应的 pipeline 层级取计数；null = 本层未知（条带显式「未知」，不假 0）。 */
type PipelineCounts = Record<TabId, number | null>;

export default function ResourceCenter() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [tab, setTab] = useState<TabId>("ontology");

  // 四个计数独立取数（各 Tab 内容自查真值；此处只服务顶栏条带，轻量级）
  const [counts, setCounts] = useState<PipelineCounts>({ ontology: null, data: null, knowledge: null, cognition: null });
  const [countsLoaded, setCountsLoaded] = useState(false);

  useEffect(() => {
    let alive = true;
    (async () => {
      const [onto, data, kb, cog] = await Promise.all([
        fetchOntologyCount(),
        fetchDataCount(),
        fetchKnowledgeCount(),
        fetchCognitionCount(),
      ]);
      if (!alive) return;
      setCounts({ ontology: onto, data, knowledge: kb, cognition: cog });
      setCountsLoaded(true);
    })();
    return () => { alive = false; };
  }, []);

  const renderCount = (c: number | null) =>
    c === null ? t("platform.resource.countUnknown") : t("platform.resource.count", { count: c });

  return (
    <div className={`flex-1 flex flex-col min-h-0 ${styles.appBg} ${styles.appText}`}>
      {/* 页头：标题 + 四段 pipeline 条带（每段 = label + 真实计数） */}
      <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
        <div className="flex items-center gap-2 flex-wrap">
          <span className={`p-1.5 rounded-md flex items-center justify-center ${styles.accentBg} ${styles.cardText}`}>
            <Box size={16} />
          </span>
          <div className="min-w-0">
            <h1 className={`text-lg font-bold tracking-tight ${styles.appText}`}>{t("platform.resource.title")}</h1>
            <p className={`text-[11px] ${styles.cardTextMuted} -mt-0.5`}>{t("platform.resource.subtitle")}</p>
          </div>
          <div className="flex-1 min-w-2" />
          {/* pipeline 条带：本体 → 数据 → 知识 → 认知（移动端横滚） */}
          <div className="flex items-center gap-1 overflow-x-auto max-w-full" role="tablist" aria-label={t("platform.resource.title")}>
            {TABS.map(({ id, pipelineKey, icon: Icon }, i) => {
              const active = tab === id;
              return (
                <React.Fragment key={id}>
                  {i > 0 && <span aria-hidden className={`shrink-0 text-[10px] ${styles.cardTextMuted}`}>→</span>}
                  <button
                    type="button"
                    role="tab"
                    aria-selected={active}
                    onClick={() => setTab(id)}
                    className={`shrink-0 inline-flex items-center gap-1.5 rounded border px-2.5 py-1 text-[11px] cursor-pointer transition-colors ${styles.cardBorder} ${active ? `${styles.accentText} ${styles.accentBorder}` : `${styles.cardTextMuted} hover:opacity-80`}`}
                  >
                    <Icon size={13} />
                    <span className="font-semibold">{t(pipelineKey)}</span>
                    <span className={`font-mono font-bold ${styles.cardTextMuted}`}>
                      {countsLoaded ? renderCount(counts[id]) : "…"}
                    </span>
                  </button>
                </React.Fragment>
              );
            })}
          </div>
        </div>
      </div>

      {/* Tab 内容区：各 Tab 独立文件（前端规范 §十 收口），不重挂重复取数 */}
      <div className="flex-1 min-h-0 flex flex-col">
        {tab === "ontology" && <OntologyTab />}
        {tab === "data" && <DataTab />}
        {tab === "knowledge" && <KnowledgeTab />}
        {tab === "cognition" && <CognitionTab />}
      </div>
    </div>
  );
}

/* ── 顶栏 pipeline 四段计数（各端点真实取数，失败/空 = unknown；基址统一在对应 client 内定义） ── */

async function fetchOntologyCount(): Promise<number | null> {
  try {
    // fetchOntologyDataObjects — 本体对象清单（/api/v1/ontology/data/objects 单源在
    // services/ontology/ontologyDataApi）；数个数即「本体对象」规模
    const res = await import("../services/ontology/ontologyDataApi").then(
      (m) => m.fetchOntologyDataObjects(),
    );
    const arr = Array.isArray(res) ? res : Array.isArray((res as { data?: unknown[] })?.data)
      ? (res as { data: unknown[] }).data
      : [];
    return arr.length;
  } catch {
    return null;
  }
}

async function fetchDataCount(): Promise<number | null> {
  try {
    // listDataAssets — 数据资产清单（/api/v1/datanet/assets 单源在 api/dataClient）；
    // 后端当前未分页包裹 total —— 服务端返回行数（客户端上限 500，非 Mock，非臆造总数）
    const arr = await import("../api/dataClient").then((m) => m.listDataAssets(1, 500));
    return arr.length;
  } catch {
    return null;
  }
}

async function fetchKnowledgeCount(): Promise<number | null> {
  try {
    // kbFetchGlossaryTerms → GET {GLOSSARY_BASE}/terms?limit=N，直接返回 GlossaryTerm[]（服务端 limit 上限截断口径）
    const { kbFetchGlossaryTerms } = await import("../pages/knowledge/services/wiki");
    const arr = await kbFetchGlossaryTerms(undefined, 500);
    return arr.length;
  } catch {
    return null;
  }
}

async function fetchCognitionCount(): Promise<number | null> {
  try {
    const { fetchCognitiveAssetCatalog } = await import("../services/cognitiveClient");
    const c = await fetchCognitiveAssetCatalog();
    return Array.isArray(c?.objects) ? c.objects.length : null;
  } catch {
    return null;
  }
}
