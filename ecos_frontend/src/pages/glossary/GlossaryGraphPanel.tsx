/**
 * GlossaryGraphPanel — 术语词条关系图谱（图谱 Tab）。
 *
 * 职责：以 cytoscape 渲染词条关系图谱，支持深度切换（1/2/3）、刷新、重新布局，
 * 点击节点回传词条 id 给父级（父级切到「详情」Tab）。
 * 数据/接口：services/glossary 的 fetchGlossaryGraph（GET /terms/{id}/graph?depth=N）。
 *
 * 说明：复用项目既有 cytoscape 能力（package.json 已含 cytoscape，参见 CausalGraphView），
 * 不引入任何新依赖；cytoscape 绘制于 canvas，无法使用 Tailwind class，故样式取具体色值，
 * 节点配色来自字典 glossary_term_type 的 extValue（getColor）。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useCallback, useEffect, useRef, useState } from "react";
import cytoscape, { Core, EventObject, LayoutOptions, ElementDefinition } from "cytoscape";
import { Network, RefreshCw, Shuffle, RotateCw } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { useDict } from "../../hooks/useDict";
import { fetchGlossaryGraph, type GlossaryGraph } from "../../services/glossary";

// ── canvas 色值常量（canvas 不识别 Tailwind class，故为具体值）──
/** 节点文字颜色：统一白字 */
const NODE_TEXT_COLOR = "#ffffff";
/** 节点文字描边：深灰描边保证浅色主题下也可读 */
const NODE_TEXT_OUTLINE = "#1f2937";
/** 边与中性描边色（中性灰阶） */
const EDGE_COLOR = "#94a3b8";
/** 字典缺少 extValue 时的节点兜底色（中性灰） */
const FALLBACK_NODE_COLOR = "#6b7280";
/** 选中节点高亮边框色 */
const SELECT_BORDER_COLOR = "#f59e0b";

/** 深度可选值 */
const DEPTH_OPTIONS = [1, 2, 3];

interface GlossaryGraphPanelProps {
  /** 中心词条 id */
  termId: number;
  /** 点击节点回传词条 id */
  onSelectTerm: (id: number) => void;
}

export default function GlossaryGraphPanel({ termId, onSelectTerm }: GlossaryGraphPanelProps) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const { getColor: getTypeColor, items: termTypeItems } = useDict("glossary_term_type", locale);
  const { getLabel: getRelLabel, items: relTypeItems } = useDict("glossary_relation_type", locale);

  const containerRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<Core | null>(null);

  const [depth, setDepth] = useState(1);
  const [graph, setGraph] = useState<GlossaryGraph | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  /** 拉取词条关系图谱 */
  const loadGraph = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await fetchGlossaryGraph(termId, depth);
      setGraph(data);
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      setError(`${t("glossary.graph.load_failed")}: ${message}`);
    } finally {
      setLoading(false);
    }
  }, [termId, depth, t]);

  useEffect(() => {
    void loadGraph();
  }, [loadGraph]);

  // ── 初始化 / 重建 cytoscape 实例 ──
  useEffect(() => {
    if (!containerRef.current || !graph) {
      return;
    }
    // 重建前销毁旧实例，避免内存泄漏
    if (cyRef.current) {
      cyRef.current.destroy();
      cyRef.current = null;
    }

    const nodeIds = new Set(graph.nodes.map((n) => String(n.id)));
    const elements: ElementDefinition[] = [
      ...graph.nodes.map((node) => ({
        data: {
          id: String(node.id),
          name: node.name,
          termType: node.termType,
          center: node.center,
        },
        classes: node.center ? "center" : "leaf",
      })),
      // 仅保留两端都在 nodes 内的边（后端已保证，此处再兜底一次）
      ...graph.edges
        .filter((edge) => nodeIds.has(String(edge.fromTermId)) && nodeIds.has(String(edge.toTermId)))
        .map((edge) => ({
          data: {
            id: `e${edge.id}`,
            source: String(edge.fromTermId),
            target: String(edge.toTermId),
            label: getRelLabel(edge.relationType),
          },
        })),
    ];

    const cy = cytoscape({
      container: containerRef.current,
      elements,
      style: [
        {
          selector: "node",
          style: {
            label: "data(name)",
            "text-valign": "center",
            "text-halign": "center",
            color: NODE_TEXT_COLOR,
            "text-outline-color": NODE_TEXT_OUTLINE,
            "text-outline-width": 2,
            "font-size": "10px",
            "text-wrap": "wrap",
            "text-max-width": "86px",
            width: 46,
            height: 46,
            "border-width": 1,
            "border-color": EDGE_COLOR,
          },
        },
        {
          // 中心词条：更大 + 更粗边框
          selector: "node.center",
          style: {
            width: 64,
            height: 64,
            "border-width": 4,
            "border-color": NODE_TEXT_OUTLINE,
            "font-weight": "bold",
          },
        },
        {
          selector: "node:selected",
          style: { "border-color": SELECT_BORDER_COLOR, "border-width": 4 },
        },
        {
          selector: "edge",
          style: {
            width: 1.5,
            "line-color": EDGE_COLOR,
            "target-arrow-color": EDGE_COLOR,
            "target-arrow-shape": "triangle",
            "curve-style": "bezier",
            "arrow-scale": 1,
            label: "data(label)",
            "font-size": "8px",
            color: NODE_TEXT_OUTLINE,
            "text-background-color": NODE_TEXT_COLOR,
            "text-background-opacity": 0.85,
            "text-background-padding": "2px",
          },
        },
      ],
      layout: { name: "cose", animate: false, padding: 30 } as LayoutOptions,
    });

    // 按词条类型着色（字典 extValue → getColor，缺失时用中性兜底色）
    cy.nodes().forEach((node) => {
      node.style("background-color", getTypeColor(node.data("termType"), FALLBACK_NODE_COLOR));
    });

    // 点击节点回传词条 id
    cy.on("tap", "node", (evt: EventObject) => {
      const id = Number(evt.target.id());
      if (Number.isFinite(id)) {
        onSelectTerm(id);
      }
    });

    cyRef.current = cy;

    return () => {
      cy.destroy();
      if (cyRef.current === cy) {
        cyRef.current = null;
      }
    };
  }, [graph, termTypeItems, relTypeItems, getTypeColor, getRelLabel, onSelectTerm]);

  /** 重新布局（对既有实例再跑一次 layout） */
  const handleRelayout = () => {
    const cy = cyRef.current;
    if (!cy) {
      return;
    }
    cy.layout({ name: "cose", animate: true, animationDuration: 400, padding: 30 } as LayoutOptions).run();
  };

  const isEmpty = !loading && !!graph && graph.edges.length === 0;

  return (
    <div className="flex flex-col h-full min-h-0">
      {/* 工具栏 */}
      <div className="flex items-center gap-2 mb-3 flex-wrap shrink-0">
        <label className={`text-[11px] font-semibold ${styles.cardTextMuted}`}>
          {t("glossary.graph.depth")}
        </label>
        <select
          className={`px-2.5 py-1.5 rounded-lg border text-xs outline-none
            ${styles.inputBorder} ${styles.inputBg} ${styles.inputText}`}
          value={depth}
          onChange={(e) => setDepth(Number(e.target.value))}
        >
          {DEPTH_OPTIONS.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>

        <button
          className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-medium
            transition disabled:opacity-50 ${styles.cardBorder} ${styles.sidebarHoverBg} ${styles.muted}`}
          onClick={loadGraph}
          disabled={loading}
        >
          <RefreshCw size={13} className={loading ? "animate-spin" : ""} />
          {t("glossary.graph.refresh")}
        </button>

        <button
          className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-medium
            transition disabled:opacity-50 ${styles.cardBorder} ${styles.sidebarHoverBg} ${styles.muted}`}
          onClick={handleRelayout}
          disabled={loading || !graph}
        >
          <Shuffle size={13} />
          {t("glossary.graph.relayout")}
        </button>

        {graph && (
          <span className={`text-[11px] ${styles.muted}`}>
            {graph.nodeCount} {t("glossary.graph.node_unit")} · {graph.edgeCount}{" "}
            {t("glossary.graph.edge_unit")}
          </span>
        )}
      </div>

      {error && (
        <div className={`mb-3 px-3 py-2 rounded-lg text-xs border shrink-0
          ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          {error}
        </div>
      )}

      {/* 画布 */}
      <div className={`relative flex-1 min-h-[400px] rounded-lg border overflow-hidden ${styles.cardBorder}`}>
        <div ref={containerRef} className="w-full h-full" style={{ minHeight: 400 }} />

        {loading && (
          <div className={`absolute inset-0 flex items-center justify-center gap-2 text-xs ${styles.muted}`}>
            <RotateCw size={16} className="animate-spin" />
            {t("glossary.loading")}
          </div>
        )}

        {isEmpty && (
          <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
            <div className={`text-center ${styles.muted}`}>
              <Network size={32} className="mx-auto mb-2 opacity-30" />
              <p className="text-xs">{t("glossary.graph.empty")}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}