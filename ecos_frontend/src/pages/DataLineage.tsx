/**
 * DataLineage — 智能数据血缘（图形化展现）
 * 使用 GraphCanvas 组件展示表级/字段级血缘关系图
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect } from "react";
import { GitBranch, Search, RefreshCw, AlertCircle, Info, ExternalLink } from "lucide-react";
import { useNavigate } from "react-router-dom";
import GraphCanvas from "../components/GraphCanvas";
import { apiFetchData, fetchDatasets } from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

export default function DataLineage() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const navigate = useNavigate();
  const [nodes, setNodes] = useState<any[]>([]);
  const [links, setLinks] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [search, setSearch] = useState("");

  const [datasourceId, setDatasourceId] = useState("1");
  const [tableName, setTableName] = useState("");
  const [selectedEdge, setSelectedEdge] = useState<any>(null);

  const fetchLineage = () => {
    setLoading(true);
    const url = tableName
      ? `/api/v1/engine/data/lineage?datasourceId=${datasourceId}&tableName=${encodeURIComponent(tableName)}`
      : `/api/v1/engine/data/lineage?datasourceId=${datasourceId}`;

    apiFetchData(url)
      .then((data: any) => {
        if (data?.nodes?.length > 0) {
          const graphNodes = data.nodes.map((n: any) => ({
            id: n.id || n.table,
            type: n.type || "table",
            label: n.table || n.id,
            status: "active",
            fields: n.fields || [],
          }));
          const graphLinks = (data.edges || []).map((e: any, i: number) => ({
            id: e.id || `edge_${i}`,
            source: e.source,
            target: e.target,
            transform: e.transform || "",
            animated: i % 2 === 0,
          }));
          setNodes(graphNodes);
          setLinks(graphLinks);
          setError(null);
        } else {
          throw new Error("No lineage data");
        }
      })
      .catch(() => {
        const mock = buildMockLineage();
        setNodes(mock.nodes);
        setLinks(mock.links);
        setError("Backend unavailable — showing mock lineage");
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchLineage(); }, []);

  const buildMockLineage = () => {
    const mockNodes = [
      { id: "raw_orders", type: "source", label: "raw_orders", status: "active", owner: "Ingestion", updatedAt: "2026-06-20" },
      { id: "raw_customers", type: "source", label: "raw_customers", status: "active", owner: "Ingestion", updatedAt: "2026-06-20" },
      { id: "raw_machines", type: "source", label: "raw_machines", status: "active", owner: "IoT Platform", updatedAt: "2026-06-19" },
      { id: "ods_orders_clean", type: "dataset", label: "ods_orders_clean", status: "active", owner: "DataOps", updatedAt: "2026-06-21" },
      { id: "ods_customers_dedup", type: "dataset", label: "ods_customers_dedup", status: "active", owner: "DataOps", updatedAt: "2026-06-21" },
      { id: "dwd_fact_orders", type: "dataset", label: "dwd_fact_orders", status: "active", owner: "DataEng", updatedAt: "2026-06-22" },
      { id: "dwd_dim_machines", type: "dataset", label: "dwd_dim_machines", status: "active", owner: "DataEng", updatedAt: "2026-06-22" },
      { id: "ads_customer360", type: "target", label: "ads_customer360", status: "active", owner: "Analytics", updatedAt: "2026-06-22" },
      { id: "ads_ops_dashboard", type: "target", label: "ads_ops_dashboard", status: "active", owner: "Analytics", updatedAt: "2026-06-22" },
    ];
    const mockLinks = [
      { id: "e1", source: "raw_orders", target: "ods_orders_clean", animated: true },
      { id: "e2", source: "raw_customers", target: "ods_customers_dedup", animated: true },
      { id: "e3", source: "ods_orders_clean", target: "dwd_fact_orders", animated: false },
      { id: "e4", source: "ods_customers_dedup", target: "dwd_fact_orders", animated: false },
      { id: "e5", source: "dwd_fact_orders", target: "ads_customer360", animated: true },
      { id: "e6", source: "raw_machines", target: "dwd_dim_machines", animated: true },
      { id: "e7", source: "dwd_dim_machines", target: "ads_ops_dashboard", animated: true },
    ];
    return { nodes: mockNodes, links: mockLinks };
  };

  const tl = (zh: string, en: string) => locale === "zh" ? zh : en;

  const filteredNodes = search
    ? nodes.filter(n => n.label.toLowerCase().includes(search.toLowerCase()))
    : nodes;

  const selectedNode = nodes.find(n => n.id === selectedNodeId);

  return (
    <div className={`flex-1 ${styles.appBg} flex flex-col h-full font-sans overflow-hidden`}>
      {/* Header */}
      <div className={`${styles.cardBg} border-b ${styles.cardBorder} p-4 shrink-0 flex items-center justify-between gap-4`}>
        <div>
          <h1 className={`text-lg font-bold ${styles.cardText} flex items-center gap-2`}>
            <GitBranch className="text-indigo-600 w-5 h-5" />
            {tl("智能数据血缘", "Data Lineage")}
          </h1>
          <p className={`text-[11px] ${styles.muted} mt-0.5`}>
            {nodes.length} nodes · {links.length} edges
          </p>
        </div>
        <div className="flex items-center gap-2">
          <input type="text"
            className={`bg-transparent outline-none text-[11px] border rounded px-2 py-0.5 w-16 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
            placeholder={tl("表名", "table")}
            value={tableName} onChange={e => setTableName(e.target.value)} />
          <button onClick={fetchLineage}
            className="p-1.5 rounded hover:bg-slate-100 text-slate-500 text-[10px]">
            {tl("查询", "Query")}
          </button>
          <div className={`flex items-center gap-1.5 rounded-lg px-2.5 py-1 border ${styles.inputBg} ${styles.inputBorder}`}>
            <Search className={`w-3 h-3 ${styles.muted}`} />
            <input type="text"
              className={`bg-transparent outline-none text-[11px] ${styles.inputText} placeholder-slate-400 w-28`}
              placeholder={tl("搜索节点...", "Search...")}
              value={search} onChange={e => setSearch(e.target.value)} />
          </div>
          <button onClick={() => window.location.reload()}
            className="p-1.5 rounded hover:bg-slate-100 text-slate-500">
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          </button>
        </div>
      </div>

      {error && (
        <div className="bg-amber-50 border-b border-amber-200 px-4 py-2 text-[11px] text-amber-700 flex items-center gap-2 shrink-0">
          <AlertCircle className="w-3 h-3" /> {error}
        </div>
      )}

      {/* Main: Graph + Property Panel */}
      <div className="flex-1 flex min-h-0">
        {/* Graph Canvas */}
        <div className="flex-1 flex min-w-0">
          {loading ? (
            <div className="flex items-center justify-center h-full">
              <RefreshCw className="w-10 h-10 text-slate-300 animate-spin" />
            </div>
          ) : (
            <GraphCanvas
              nodes={filteredNodes}
              links={links}
              selectedNodeId={selectedNodeId}
              onSelectNode={setSelectedNodeId}
              interactive={true}
            />
          )}
        </div>

        {/* Property Panel */}
        {selectedNode && (
          <div className={`w-[300px] ${styles.cardBg} border-l ${styles.cardBorder} p-4 overflow-y-auto shrink-0 shadow-lg`}>
            <div className="flex items-center justify-between mb-4">
              <h3 className={`text-sm font-bold ${styles.cardText}`}>
                {tl("节点详情", "Node Detail")}
              </h3>
              <button onClick={() => setSelectedNodeId(null)}
                className={`text-[11px] ${styles.muted} hover:text-slate-600`}>
                {tl("关闭", "Close")}
              </button>
            </div>
            <div className="space-y-3 text-xs">
              <div>
                <span className={`text-[10px] ${styles.muted} uppercase tracking-wider block`}>ID</span>
                <span className="font-mono text-slate-700">{selectedNode.id}</span>
              </div>
              <div>
                <span className={`text-[10px] ${styles.muted} uppercase tracking-wider block`}>{tl("表名", "Table")}</span>
                <span className={`font-semibold ${styles.cardText}`}>{selectedNode.label}</span>
              </div>
              <div>
                <span className={`text-[10px] ${styles.muted} uppercase tracking-wider block`}>{tl("类型", "Type")}</span>
                <span className="px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-600 text-[10px] font-semibold">{selectedNode.type}</span>
              </div>

              {/* 字段列表 */}
              {selectedNode.fields?.length > 0 && (
                <div className="pt-2 border-t border-slate-100">
                  <span className={`text-[10px] ${styles.muted} uppercase tracking-wider block mb-2`}>
                    {tl("字段列表", "Fields")} ({selectedNode.fields.length})
                  </span>
                  <div className="space-y-1 max-h-40 overflow-y-auto">
                    {selectedNode.fields.map((f: any, i: number) => (
                      <div key={i} className="flex justify-between text-[10px]">
                        <span className="font-mono text-slate-600">{f.name || f}</span>
                        {f.type && <span className={styles.muted}>{f.type}</span>}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* 关联关系 */}
              <div className="pt-3 border-t border-slate-100">
                <span className={`text-[10px] ${styles.muted} uppercase tracking-wider block mb-2`}>
                  {tl("上下游关系", "Up/Downstream")}
                </span>
                <div className="space-y-1.5">
                  {links.filter(l => l.source === selectedNode.id || l.target === selectedNode.id).map(l => {
                    const isSource = l.source === selectedNode.id;
                    return (
                      <div key={l.id} onClick={() => setSelectedEdge(l)}
                        className={`cursor-pointer rounded px-1.5 py-1 text-[10px] ${l.transform ? 'hover:bg-blue-50' : ''}`}>
                        <div className="flex items-center gap-1 text-slate-500 font-mono">
                          {isSource ? "→" : "←"} {isSource ? l.target : l.source}
                        </div>
                        {l.transform && (
                          <div className="text-[9px] text-blue-500 font-mono mt-0.5 truncate" title={l.transform}>
                            SQL: {l.transform.substring(0, 40)}{l.transform.length > 40 ? "..." : ""}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* 选中边SQL详情 */}
              {selectedEdge?.transform && (
                <div className="pt-3 border-t border-blue-100">
                  <span className={`text-[10px] text-blue-600 uppercase tracking-wider block mb-1`}>
                    SQL变换
                  </span>
                  <pre className="text-[10px] text-slate-600 bg-slate-50 rounded p-2 whitespace-pre-wrap break-all font-mono">
                    {selectedEdge.transform}
                  </pre>
                </div>
              )}

            </div>
          </div>
        )}
      </div>
    </div>
  );
}
