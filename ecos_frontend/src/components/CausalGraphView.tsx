/**
 * P2-4 CausalGraphView — Cytoscape.js DAG 因果图可视化 + What-If 推演
 * Connected to: /api/causal/graph, /api/causal/paths
 */
import React, { useEffect, useRef, useState, useCallback } from "react";
import cytoscape, { Core, EventObject } from "cytoscape";
import { Network, Search, Sliders, RefreshCw, AlertCircle } from "lucide-react";
import { useLanguage } from "./LanguageContext";
import { useTheme } from "./ThemeContext";
import { fetchCausalGraph, fetchCausalPaths } from "../api";

// ── Types ──────────────────────────────────────
interface GraphData {
  nodes: any[];
  edges: any[];
  nodeCount: number;
  edgeCount: number;
}

interface PathData {
  from: string;
  to: string;
  hops: number;
  nodes: { id: string; name: string }[];
  edges: { type: string; strength: number }[];
}

// ── Main Component ─────────────────────────────
export default function CausalGraphView() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const containerRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<Core | null>(null);

  const [data, setData] = useState<GraphData>({ nodes: [], edges: [], nodeCount: 0, edgeCount: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedNode, setSelectedNode] = useState<any>(null);
  const [pathFrom, setPathFrom] = useState("");
  const [pathTo, setPathTo] = useState("");
  const [pathResult, setPathResult] = useState<PathData | null>(null);
  const [pathLoading, setPathLoading] = useState(false);

  // What-if sliders
  const [whatIfVars, setWhatIfVars] = useState<Record<string, number>>({
    dataQuality: 0.7,
    maintenanceBudget: 0.5,
    automationLevel: 0.4,
  });

  // ── Load data ────────────────────────────────
  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const resp = await fetchCausalGraph();
      const d = resp?.data || resp;
      setData({
        nodes: d.nodes || [],
        edges: d.edges || [],
        nodeCount: d.nodeCount || 0,
        edgeCount: d.edgeCount || 0,
      });
    } catch (e: any) {
      setError("加载因果图失败: " + (e?.message || "Unknown"));
    } finally {
      setLoading(false);
    }
  }, []);

  // ── Path query ───────────────────────────────
  const queryPath = useCallback(async () => {
    if (!pathFrom || !pathTo) return;
    setPathLoading(true);
    try {
      const resp = await fetchCausalPaths(pathFrom, pathTo);
      const d = resp?.data || resp;
      if (d && d.nodes) {
        setPathResult(d as PathData);
        // Highlight path in Cytoscape
        if (cyRef.current) {
          const cy = cyRef.current;
          cy.elements().removeClass("highlighted path-edge");
          const pathNames = new Set(d.nodes.map((n: any) => n.name));
          d.edges.forEach((_: any, i: number) => {
            const srcName = d.nodes[i]?.name;
            const tgtName = d.nodes[i + 1]?.name;
            if (srcName && tgtName) {
              cy.edges().filter(e => {
                const s = e.source().data("name");
                const t = e.target().data("name");
                return s === srcName && t === tgtName;
              }).addClass("path-edge");
            }
          });
          cy.nodes().filter(n => pathNames.has(n.data("name"))).addClass("highlighted");
        }
      }
    } catch (e: any) {
      setError("路径查询失败: " + (e?.message || "Unknown"));
    } finally {
      setPathLoading(false);
    }
  }, [pathFrom, pathTo]);

  // ── Init Cytoscape ───────────────────────────
  useEffect(() => {
    if (!containerRef.current || data.nodes.length === 0) return;

    // Destroy existing
    if (cyRef.current) cyRef.current.destroy();

    const elements: any[] = [];

    // Add nodes
    data.nodes.forEach((n: any) => {
      elements.push({
        data: { id: n.name, name: n.name, category: n.category, type: n.type },
        classes: n.category === "数据治理" ? "root" : "leaf",
      });
    });

    // Add edges
    data.edges.forEach((e: any, i: number) => {
      const srcNode = data.nodes.find((n: any) => n.id === e.source);
      const tgtNode = data.nodes.find((n: any) => n.id === e.target);
      if (srcNode && tgtNode) {
        elements.push({
          data: {
            id: `e${i}`,
            source: srcNode.name,
            target: tgtNode.name,
            strength: e.strength,
            type: e.type,
          },
        });
      }
    });

    const cy = cytoscape({
      container: containerRef.current,
      elements,
      style: [
        {
          selector: "node",
          style: {
            "background-color": "#4f46e5",
            label: "data(name)",
            "text-valign": "center",
            "text-halign": "center",
            color: "#e0e7ff",
            "font-size": "10px",
            "text-wrap": "wrap",
            "text-max-width": "100px",
            width: 60,
            height: 60,
            "border-width": 2,
            "border-color": "#818cf8",
            "font-weight": "bold",
          },
        },
        {
          selector: "node.root",
          style: { "background-color": "#7c3aed", "border-color": "#a78bfa", width: 70, height: 70 },
        },
        {
          selector: "node:selected",
          style: { "border-color": "#f59e0b", "border-width": 3 },
        },
        {
          selector: "node.highlighted",
          style: { "background-color": "#f59e0b", "border-color": "#fbbf24", color: "#1c1917" },
        },
        {
          selector: "edge",
          style: {
            width: 2,
            "line-color": "#6366f1",
            "target-arrow-color": "#6366f1",
            "target-arrow-shape": "triangle",
            "curve-style": "bezier",
            "arrow-scale": 1.2,
            opacity: 0.7,
          },
        },
        {
          selector: "edge.path-edge",
          style: { width: 4, "line-color": "#f59e0b", "target-arrow-color": "#f59e0b", opacity: 1 },
        },
      ],
      layout: {
        name: "dagre" in cytoscape.prototype ? "dagre" : "breadthfirst",
        directed: true,
        spacingFactor: 1.2,
        animate: true,
        animationDuration: 500,
      } as any,
    });

    // Click handler
    cy.on("tap", "node", (evt: EventObject) => {
      const node = evt.target;
      setSelectedNode({
        name: node.data("name"),
        category: node.data("category"),
        type: node.data("type"),
        degree: node.connectedEdges().length,
      });
      setPathFrom(node.data("name"));
    });

    // Edge labels
    cy.edges().forEach((edge) => {
      const strength = edge.data("strength");
      const type = edge.data("type");
      if (strength) {
        edge.style("label", `${type || ""} ${(strength * 100).toFixed(0)}%`);
        edge.style("font-size", "8px");
        edge.style("color", "#a5b4fc");
      }
    });

    cyRef.current = cy;

    return () => {
      cy.destroy();
    };
  }, [data]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // ── What-if impact calc ─────────────────────
  const calcWhatIf = () => {
    const dq = whatIfVars.dataQuality || 0;
    const mb = whatIfVars.maintenanceBudget || 0;
    const al = whatIfVars.automationLevel || 0;
    return [
      { dimension: "通行效率", impact: +(dq * 0.5 + al * 0.4 + mb * 0.1).toFixed(2) },
      { dimension: "养护成本", impact: +(mb * 0.6 + dq * 0.3 + al * 0.1).toFixed(2) },
      { dimension: "应急响应", impact: +(dq * 0.4 + al * 0.3 + mb * 0.3).toFixed(2) },
      { dimension: "用户满意度", impact: +(dq * 0.3 + mb * 0.3 + al * 0.4).toFixed(2) },
      { dimension: "营收", impact: +(dq * 0.2 + mb * 0.3 + al * 0.5).toFixed(2) },
    ];
  };

  const impacts = calcWhatIf();

  // ── Render ───────────────────────────────────
  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Toolbar */}
      <div className="flex items-center gap-2 mb-3 shrink-0 flex-wrap">
        <button
          onClick={loadData}
          disabled={loading}
          className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded text-xs font-medium transition-all
            ${styles.accentBg} ${styles.accentHover} text-white disabled:opacity-50`}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {loading ? "加载中..." : locale === "zh" ? "刷新" : "Refresh"}
        </button>
        <span className="text-xs opacity-60">
          {data.nodeCount} {locale === "zh" ? "节点" : "nodes"} · {data.edgeCount} {locale === "zh" ? "边" : "edges"}
        </span>
      </div>

      {error && (
        <div className="mb-3 px-3 py-2 rounded text-xs bg-red-500/10 border border-red-500/30 text-red-400 flex items-center gap-2 shrink-0">
          <AlertCircle className="w-3.5 h-3.5" />{error}
        </div>
      )}

      {/* Main grid: Graph + Side panels */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-[1fr_260px] gap-3 min-h-0">
        {/* Cytoscape container */}
        <div className={`border rounded-lg overflow-hidden relative ${styles.cardBorder}`}>
          {data.nodes.length === 0 && !loading ? (
            <div className="absolute inset-0 flex items-center justify-center">
              <div className="text-center opacity-50">
                <Network className="w-10 h-10 mx-auto mb-2" />
                <p className="text-sm">{locale === "zh" ? "暂无因果图数据" : "No causal graph data"}</p>
              </div>
            </div>
          ) : (
            <div ref={containerRef} className="w-full h-full" style={{ minHeight: 400 }} />
          )}
        </div>

        {/* Right side panels */}
        <div className="flex flex-col gap-3 min-h-0 overflow-auto">
          {/* What-If Panel */}
          <div className={`border rounded-lg p-3 shrink-0 ${styles.cardBorder} ${styles.cardBg}`}>
            <h4 className="flex items-center gap-1.5 text-xs font-semibold mb-2 opacity-80">
              <Sliders className="w-3.5 h-3.5" />
              {locale === "zh" ? "What-If 推演" : "What-If"}
            </h4>
            <div className="space-y-2">
              <div>
                <div className="flex justify-between text-[10px] opacity-60 mb-0.5">
                  <span>{locale === "zh" ? "数据质量" : "Data Quality"}</span>
                  <span>{(whatIfVars.dataQuality * 100).toFixed(0)}%</span>
                </div>
                <input type="range" min="0" max="1" step="0.05"
                  value={whatIfVars.dataQuality}
                  onChange={e => setWhatIfVars({ ...whatIfVars, dataQuality: +e.target.value })}
                  className="w-full h-1 accent-indigo-500" />
              </div>
              <div>
                <div className="flex justify-between text-[10px] opacity-60 mb-0.5">
                  <span>{locale === "zh" ? "养护预算" : "Budget"}</span>
                  <span>{(whatIfVars.maintenanceBudget * 100).toFixed(0)}%</span>
                </div>
                <input type="range" min="0" max="1" step="0.05"
                  value={whatIfVars.maintenanceBudget}
                  onChange={e => setWhatIfVars({ ...whatIfVars, maintenanceBudget: +e.target.value })}
                  className="w-full h-1 accent-indigo-500" />
              </div>
              <div>
                <div className="flex justify-between text-[10px] opacity-60 mb-0.5">
                  <span>{locale === "zh" ? "自动化水平" : "Automation"}</span>
                  <span>{(whatIfVars.automationLevel * 100).toFixed(0)}%</span>
                </div>
                <input type="range" min="0" max="1" step="0.05"
                  value={whatIfVars.automationLevel}
                  onChange={e => setWhatIfVars({ ...whatIfVars, automationLevel: +e.target.value })}
                  className="w-full h-1 accent-indigo-500" />
              </div>
            </div>
            {/* Impact preview */}
            <div className="mt-3 pt-2 border-t border-white/5">
              <h5 className="text-[10px] font-semibold opacity-60 mb-1">
                {locale === "zh" ? "预估影响" : "Predicted Impact"}
              </h5>
              <div className="space-y-1">
                {impacts.map((item) => (
                  <div key={item.dimension} className="flex items-center gap-1.5 text-[10px]">
                    <span className="w-14 opacity-70 truncate">{item.dimension}</span>
                    <div className="flex-1 h-1.5 rounded-full bg-white/5 overflow-hidden">
                      <div
                        className="h-full rounded-full transition-all duration-300"
                        style={{
                          width: `${Math.min(100, item.impact * 100)}%`,
                          background: item.impact >= 0.7 ? "#10b981" : item.impact >= 0.4 ? "#f59e0b" : "#ef4444",
                        }}
                      />
                    </div>
                    <span className="w-8 text-right opacity-80">{(item.impact * 100).toFixed(0)}%</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Path Query Panel */}
          <div className={`border rounded-lg p-3 shrink-0 ${styles.cardBorder} ${styles.cardBg}`}>
            <h4 className="flex items-center gap-1.5 text-xs font-semibold mb-2 opacity-80">
              <Search className="w-3.5 h-3.5" />
              {locale === "zh" ? "路径查询" : "Path Query"}
            </h4>
            <select
              className={`w-full px-2 py-1.5 rounded border text-xs mb-1.5 outline-none ${styles.inputBg} ${styles.inputBorder}`}
              value={pathFrom}
              onChange={e => { setPathFrom(e.target.value); setPathResult(null); }}
            >
              <option value="">{locale === "zh" ? "选择起点" : "From"}</option>
              {data.nodes.map((n: any) => (
                <option key={n.name} value={n.name}>{n.name}</option>
              ))}
            </select>
            <select
              className={`w-full px-2 py-1.5 rounded border text-xs mb-2 outline-none ${styles.inputBg} ${styles.inputBorder}`}
              value={pathTo}
              onChange={e => { setPathTo(e.target.value); setPathResult(null); }}
            >
              <option value="">{locale === "zh" ? "选择终点" : "To"}</option>
              {data.nodes.map((n: any) => (
                <option key={n.name} value={n.name}>{n.name}</option>
              ))}
            </select>
            <button
              onClick={queryPath}
              disabled={!pathFrom || !pathTo || pathLoading}
              className={`w-full px-3 py-1.5 rounded text-xs font-medium transition-all
                ${styles.accentBg} ${styles.accentHover} text-white disabled:opacity-30`}
            >
              {pathLoading ? "..." : locale === "zh" ? "查询路径" : "Find Path"}
            </button>
            {pathResult && (
              <div className="mt-2 p-2 rounded bg-amber-500/5 border border-amber-500/20 text-[10px]">
                <p className="font-medium text-amber-400">
                  {pathResult.from} → {pathResult.to}
                </p>
                <p className="opacity-60 mt-0.5">
                  {locale === "zh" ? "步数" : "Hops"}: {pathResult.hops} · {pathResult.nodes.length} {locale === "zh" ? "节点" : "nodes"}
                </p>
              </div>
            )}
          </div>

          {/* Selected Node Detail */}
          {selectedNode && (
            <div className={`border rounded-lg p-3 shrink-0 ${styles.cardBorder} ${styles.cardBg}`}>
              <h4 className="text-xs font-semibold mb-1.5 opacity-80">
                {locale === "zh" ? "节点详情" : "Node Detail"}
              </h4>
              <div className="text-[10px] space-y-1">
                <p><span className="opacity-50">Name:</span> {selectedNode.name}</p>
                <p><span className="opacity-50">Category:</span> {selectedNode.category}</p>
                <p><span className="opacity-50">Degree:</span> {selectedNode.degree}</p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
