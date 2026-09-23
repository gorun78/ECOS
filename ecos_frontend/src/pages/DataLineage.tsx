/**
 * DataLineage — 智能数据血缘（图形化展现）
 *
 * 视图策略：
 *  - 视图 A（默认）：「全局拓扑」— 从后端持久化表直接秒开，展示全量管道任务解析出的表级/字段级血缘
 *    （首次使用空表 → 显示"重新生成"引导按钮，调用 POST /api/v1/engine/data/lineage/topology/rebuild）
 *  - 视图 B：「单表查询」— 输入表名后实时解析，返回该表的字段级血缘
 *
 * 删除：
 *  - 旧版假 mock 兜底（误导用户显示不存在的血缘）
 *  - 硬编码的数据源下拉框（旧版 datasourceId="1" 未接入数据源列表，纯装饰）
 *
 * 兼容性：
 *  - 仍保留 /api/v1/engine/data/lineage?tableName=xx 实时解析端点（外部调用方可能依赖）
 *  - 与 LineageCompatController（/api/lineage/impact + /parse） 共存，不冲突
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useMemo } from "react";
import { GitBranch, Search, RefreshCw, AlertCircle, Info, Wand2, Table2, Package } from "lucide-react";
import GraphCanvas from "../components/GraphCanvas";
import { apiFetchData } from "../api";
import { fetchLineageTopology, rebuildLineage, fetchLineageImpact } from "./data-workbench/api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

/**
 * Lineage 视图模式
 * - topology: 全局拓扑视图（默认，从持久化表秒开）
 * - table: 单表查询（实时解析）
 */
type LineageMode = "topology" | "table";

/** 节点 data 形态：拓扑视图 & 表查询返回的都是 { id, type, label, table?, fields? } */
interface LNode {
  id: string;
  type: "table" | "field" | "unknown";
  label: string;
  table?: string;
  fields?: Array<{ name: string; type?: string }>;
}
interface LEdge {
  id: string;
  source: string;
  target: string;
  transform?: string;
  pipeline_task_name?: string;
}

interface DataLineageProps {
  /** 可选：进入时自动打开"单表查询"并填入该表名（用于从目录树"查看血缘"跳转） */
  initialTable?: string;
}

export default function DataLineage({ initialTable }: DataLineageProps = {}) {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  // ── 视图模式 ──────────────────────────────────────────
  // 如果外部传入了 initialTable,初始切到 "table" 模式
  const [mode, setMode] = useState<LineageMode>(initialTable ? "table" : "topology");

  // ── 节点 / 边 ────────────────────────────────────────
  const [nodes, setNodes] = useState<LNode[]>([]);
  const [edges, setEdges] = useState<LEdge[]>([]);
  const [loading, setLoading] = useState(true);
  const [rebuilding, setRebuilding] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [emptyHint, setEmptyHint] = useState<string | null>(null); // 后端告知的友好提示

  // ── 表查询输入 ───────────────────────────────────────
  const [tableName, setTableName] = useState(initialTable || "");
  const [queryMsg, setQueryMsg] = useState<string | null>(null);

  // ── 选中节点/边 ──────────────────────────────────────
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<LEdge | null>(null);
  const [search, setSearch] = useState("");

  // ── 影响度分析 (P2) ────────────────────────────────
  const [impactDepth, setImpactDepth] = useState<number>(3);       // 追溯层数 (1-8)
  // 抽出接口协作类型：
  //   选点节点后自动拉一次 impact; 用户点 "展开 N 层" 按钮改 depth 再拉一次
  type ImpactNode = { id: string; hop: number; label: string; riskScore: number };
  type ImpactRes = {
    startNode: string; canonicalStartNode: string; matched: boolean; depth: number;
    severity: string; totalRisk: number;
    downstream: ImpactNode[]; upstream: ImpactNode[]; branchCount: number;
  } | null;
  const [impactData, setImpactData] = useState<ImpactRes>(null);
  const [impactLoading, setImpactLoading] = useState(false);
  // P2 多层展开: 把选中节点 + N 层可达节点 .fit 到画布; 其他节点/边隐藏
  // 用户操作节点/切层数 → 重算; toggle 开/关 → 显示全量或聚焦子图
  const [focusMode, setFocusMode] = useState<boolean>(false);

  // ── 统计统计 ─────────────────────────────────────────
  const [fromDb, setFromDb] = useState<boolean | undefined>(undefined);

  // ── 加载全局拓扑（默认视图） ─────────────────────────
  const loadTopology = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const topo = await fetchLineageTopology();
      setNodes((topo.nodes as any[]).map(n => ({
        id: n.id,
        type: (n.type as LNode["type"]) || "unknown",
        label: n.label || n.table || n.id,
        table: n.table,
      })));
      setEdges((topo.edges as any[]).map(e => ({
        id: e.id,
        source: e.source,
        target: e.target,
        transform: e.transform,
        pipeline_task_name: e.pipeline_task_name,
      })));
      setFromDb(topo.from_db);
      if (topo.total_nodes === 0) {
        setEmptyHint(
          locale === "zh"
            ? "暂无血缘数据。点击「重新生成」从管道任务 SQL 解析血缘，或在「单表查询」中搜索特定表。"
            : "No lineage data. Click 'Rebuild' to parse from pipeline SQL, or use single-table query."
        );
      } else {
        setEmptyHint(null);
      }
    } catch (e) {
      setError((e as Error).message || "load topology failed");
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [locale]);

  // ── 重新生成血缘（pipelines 全量解析 + 持久化） ───────
  const handleRebuild = useCallback(async () => {
    setRebuilding(true);
    setError(null);
    setEmptyHint(null);
    try {
      const stats = await rebuildLineage(0);
      if (stats.total_nodes === 0 && stats.tasks_parsed === 0) {
        setEmptyHint(
          locale === "zh"
            ? `扫描了 ${stats.definitions_scanned ?? 0} 个管道定义与 ${stats.tasks_scanned} 个旧管道任务，但没有发现任何 SQL 节点。请在「管道 Builder」中创建至少一个 TRANSFORM_SQL 或 SOURCE_JDBC 节点（config.sql 含 SELECT/INSERT/...），再重新生成血缘。`
            : `Scanned ${stats.definitions_scanned ?? 0} pipeline definitions + ${stats.tasks_scanned} legacy tasks but found no SQL nodes. Add at least one TRANSFORM_SQL / SOURCE_JDBC node (config.sql) in Pipeline Builder, then Rebuild.`
        );
      }
      await loadTopology();
    } catch (e) {
      setError("rebuild failed: " + (e as Error).message);
    } finally {
      setRebuilding(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadTopology, locale]);

  // ── 单表查询（实时解析） ─────────────────────────────
  const handleQueryTable = useCallback(async () => {
    const tbl = tableName.trim();
    if (!tbl) {
      setQueryMsg(locale === "zh" ? "输入表名以查询单表血缘" : "Enter a table name to query lineage");
      return;
    }
    setMode("table");
    setQueryMsg(null);
    setLoading(true);
    const url = `/api/v1/engine/data/lineage?tableName=${encodeURIComponent(tbl)}`;
    try {
      const data: any = await apiFetchData(url);
      if (data?.nodes?.length > 0) {
        setNodes(data.nodes.map((n: any) => ({
          id: n.id || n.table,
          type: (n.type as LNode["type"]) || "table",
          label: n.table || n.id,
          table: n.table,
          fields: Array.isArray(n.fields) ? n.fields : undefined,
        })));
        setEdges((data.edges || []).map((e: any, i: number) => ({
          id: e.id || `edge_${i}`,
          source: e.source,
          target: e.target,
          transform: e.transform || "",
        })));
        setEmptyHint(null);
      } else {
        setNodes([]);
        setEdges([]);
        setEmptyHint(
          data?.message ||
          (locale === "zh" ? `未找到表 ${tbl} 的血缘关系` : `No lineage found for table ${tbl}`)
        );
      }
    } catch (e) {
      setNodes([]);
      setEdges([]);
      setEmptyHint((e as Error).message || (locale === "zh" ? "查询失败" : "Query failed"));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tableName, locale]);

  // 初次挂载即拉全局拓扑
  useEffect(() => { loadTopology(); }, [loadTopology]);

  // ── P2 影响度: 选中节点或 depth 变化时自动拉 ──────────
  useEffect(() => {
    if (!selectedNodeId) {
      setImpactData(null);
      return;
    }
    let cancelled = false;
    setImpactLoading(true);
    fetchLineageImpact(selectedNodeId, impactDepth)
      .then(r => { if (!cancelled) setImpactData(r); })
      .catch(() => { if (!cancelled) setImpactData(null); })
      .finally(() => { if (!cancelled) setImpactLoading(false); });
    return () => { cancelled = true; };
  }, [selectedNodeId, impactDepth, mode]);

  // 切换节点 / 重新查询时清旧 impact(UI 干净)
  useEffect(() => {
    setImpactData(null);
  }, [tableName, mode]);

  // ── 节点 / 边 筛选 ──────────────────────────────────
  const filteredNodes = useMemo(
    () => (search ? nodes.filter(n => n.label.toLowerCase().includes(search.toLowerCase())) : nodes),
    [nodes, search]
  );
  // 选中节点详情
  const selectedNode = useMemo(() => nodes.find(n => n.id === selectedNodeId) || null, [nodes, selectedNodeId]);
  // 选中节点的上下游关联边
  const relatedEdges = useMemo(
    () => edges.filter(e => e.source === selectedNodeId || e.target === selectedNodeId),
    [edges, selectedNodeId]
  );

  // ── P2 多层展开: 选中节点 + N 层可达节点 形成的子图 ──
  // focusedNodeIds: 既包含选中节点本身, 也包括 N 层可达的 downstream + upstream (匹配 impactData)
  // focusedEdges: 两端节点均在 focusedNodeIds 中的边
  const focusedNodeIds = useMemo(() => {
    if (!focusMode || !selectedNodeId || !impactData) return null;
    const ids = new Set<string>([
      selectedNodeId,
      impactData.canonicalStartNode || selectedNodeId,
    ]);
    (impactData.downstream || []).forEach(n => ids.add(n.id));
    (impactData.upstream || []).forEach(n => ids.add(n.id));
    return ids;
  }, [focusMode, selectedNodeId, impactData]);

  // 画布渲染视图: focusMode 开 → 用 focusedNodeIds 过滤; 否则用 filteredNodes
  const visibleNodes = useMemo(() => {
    if (!focusMode || !focusedNodeIds) return filteredNodes;
    return filteredNodes.filter(n => focusedNodeIds.has(n.id));
  }, [focusMode, focusedNodeIds, filteredNodes]);

  const visibleEdges = useMemo(() => {
    if (!focusMode || !focusedNodeIds) return edges;
    return edges.filter(e => focusedNodeIds.has(e.source) && focusedNodeIds.has(e.target));
  }, [focusMode, focusedNodeIds, edges]);

  // ── 切换模式 ─────────────────────────────────────────
  const switchMode = (m: LineageMode) => {
    setMode(m);
    setError(null);
    setEmptyHint(null);
    setQueryMsg(null);
    setSelectedNodeId(null);
    setSelectedEdge(null);
    if (m === "topology") loadTopology();
  };

  // ── 视图切换 tab 样式 ────────────────────────────────
  const tabClass = (active: boolean) =>
    `px-3 py-1.5 rounded text-[11px] font-semibold transition ${
      active ? styles.accentBg + " " + styles.accentText : styles.muted + " hover:opacity-80"
    }`;

  return (
    <div className={`flex-1 ${styles.appBg} flex flex-col h-full font-sans overflow-hidden`}>
      {/* ── Header ─────────────────────────────────────── */}
      <div className={`${styles.cardBg} border-b ${styles.cardBorder} p-4 shrink-0 flex items-center justify-between gap-4`}>
        <div>
          <h1 className={`text-lg font-bold ${styles.cardText} flex items-center gap-2`}>
            <GitBranch className="text-indigo-600 w-5 h-5" />
            {locale === "zh" ? "智能数据血缘" : "Data Lineage"}
          </h1>
          <p className={`text-[11px] ${styles.muted} mt-0.5`}>
            {nodes.length} {locale === "zh" ? "节点" : "nodes"} · {edges.length} {locale === "zh" ? "边" : "edges"}
            {fromDb !== undefined && mode === "topology" && (
              <span className={fromDb ? " text-emerald-500" : " text-amber-500"}>
                {" "}· {fromDb ? (locale === "zh" ? "已持久化" : "persisted") : (locale === "zh" ? "实时" : "live")}
              </span>
            )}
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          {/* 视图切换 */}
          <div className={`flex items-center gap-1 ${styles.cardBg} border ${styles.cardBorder} rounded-lg p-1`}>
            <button onClick={() => switchMode("topology")} className={tabClass(mode === "topology")}>
              <span className="inline-flex items-center gap-1">
                <Package className="w-3 h-3" />
                {locale === "zh" ? "全局拓扑" : "Topology"}
              </span>
            </button>
            <button onClick={() => switchMode("table")} className={tabClass(mode === "table")}>
              <span className="inline-flex items-center gap-1">
                <Table2 className="w-3 h-3" />
                {locale === "zh" ? "单表查询" : "Table Query"}
              </span>
            </button>
          </div>

          {/* 重新生成（仅拓扑视图） */}
          {mode === "topology" && (
            <button
              onClick={handleRebuild}
              disabled={rebuilding}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold border transition disabled:opacity-40"
              style={{ borderColor: styles.cardBorder, color: styles.cardText }}
            >
              {rebuilding ? (
                <RefreshCw className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <Wand2 className="w-3.5 h-3.5" />
              )}
              {rebuilding
                ? (locale === "zh" ? "重建中..." : "Rebuilding...")
                : (locale === "zh" ? "重新生成" : "Rebuild")}
            </button>
          )}

          {/* 单表查询输入 */}
          {mode === "table" && (
            <>
              <input
                type="text"
                className={`bg-transparent outline-none text-[11px] border rounded px-2 py-1 w-40 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                placeholder={locale === "zh" ? "输入表名 (如 raw_orders)" : "Table name"}
                value={tableName}
                onChange={e => setTableName(e.target.value)}
                onKeyDown={e => e.key === "Enter" && handleQueryTable()}
              />
              <button
                onClick={handleQueryTable}
                disabled={loading}
                className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold border transition disabled:opacity-40"
                style={{ borderColor: styles.cardBorder, color: styles.cardText }}
              >
                <Search className="w-3.5 h-3.5" />
                {locale === "zh" ? "查询" : "Query"}
              </button>
            </>
          )}

          {/* 搜索（两视图都可） */}
          <div className={`flex items-center gap-1.5 rounded-lg px-2.5 py-1 border ${styles.inputBg} ${styles.inputBorder}`}>
            <Search className={`w-3 h-3 ${styles.muted}`} />
            <input
              type="text"
              className={`bg-transparent outline-none text-[11px] ${styles.inputText} w-28`}
              placeholder={locale === "zh" ? "搜索节点..." : "Search..."}
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
          </div>

          {/* 刷新（等价 reload 当前视图） */}
          <button
            onClick={() => (mode === "topology" ? loadTopology() : handleQueryTable())}
            className="p-1.5 rounded hover:opacity-70 transition"
            title={locale === "zh" ? "刷新" : "Refresh"}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${styles.muted} ${loading ? "animate-spin" : ""}`} />
          </button>
        </div>
      </div>

      {/* 错误条 */}
      {error && (
        <div className="bg-red-50 border-b border-red-200 px-4 py-2 text-[11px] text-red-700 flex items-center gap-2 shrink-0 dark:bg-red-500/10 dark:border-red-500/30">
          <AlertCircle className="w-3 h-3" /> {error}
        </div>
      )}

      {/* 表查询提示条 */}
      {mode === "table" && queryMsg && (
        <div className="bg-blue-50 border-b border-blue-200 px-4 py-2 text-[11px] text-blue-700 flex items-center gap-2 shrink-0 dark:bg-blue-500/10 dark:border-blue-500/30">
          <Info className="w-3 h-3" /> {queryMsg}
        </div>
      )}

      {/* 空数据引导条（持久化空表 / 表查询未命中） */}
      {emptyHint && !loading && (
        <div className="bg-amber-50 border-b border-amber-200 px-4 py-2 text-[11px] text-amber-700 flex items-center justify-between gap-2 shrink-0 dark:bg-amber-500/10 dark:border-amber-500/30">
          <span className="flex items-center gap-2">
            <AlertCircle className="w-3 h-3" /> {emptyHint}
          </span>
          {mode === "topology" && (
            <button
              onClick={handleRebuild}
              disabled={rebuilding}
              className="px-2 py-0.5 rounded border text-[10px] font-semibold transition disabled:opacity-40"
              style={{ borderColor: styles.cardBorder, color: styles.cardText }}
            >
              {rebuilding ? (locale === "zh" ? "重建中..." : "Rebuilding...") : (locale === "zh" ? "重新生成" : "Rebuild now")}
            </button>
          )}
        </div>
      )}

      {/* ── Main: Graph + Property Panel ─────────────────── */}
      <div className="flex-1 flex min-h-0">
        {/* Graph Canvas */}
        <div className="flex-1 flex min-w-0">
          {loading ? (
            <div className="flex items-center justify-center h-full">
              <RefreshCw className={`w-10 h-10 ${styles.muted} animate-spin`} />
            </div>
          ) : (
            <GraphCanvas
              nodes={visibleNodes}
              links={visibleEdges}
              selectedNodeId={selectedNodeId}
              onSelectNode={setSelectedNodeId}
              interactive={true}
            />
          )}
        </div>

        {/* Property Panel */}
        {selectedNode && (
          <div className={`w-full lg:w-[320px] ${styles.cardBg} border-l ${styles.cardBorder} p-4 overflow-y-auto shrink-0 shadow-lg`}>
            <div className="flex items-center justify-between mb-4">
              <h3 className={`text-sm font-bold ${styles.cardText}`}>
                {locale === "zh" ? "节点详情" : "Node Detail"}
              </h3>
              <button
                onClick={() => { setSelectedNodeId(null); setSelectedEdge(null); }}
                className={`text-[11px] ${styles.muted} hover:opacity-80 transition`}
              >
                {locale === "zh" ? "关闭" : "Close"}
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <span className={`text-[10px] ${styles.muted} uppercase tracking-wider block`}>ID</span>
                <span className={`font-mono break-all ${styles.cardText}`}>{selectedNode.id}</span>
              </div>
              <div>
                <span className={`text-[10px] ${styles.muted} uppercase tracking-wider block`}>
                  {locale === "zh" ? "名称" : "Name"}
                </span>
                <span className={`font-semibold ${styles.cardText}`}>{selectedNode.label}</span>
              </div>
              <div>
                <span className={`text-[10px] ${styles.muted} uppercase tracking-wider block`}>
                  {locale === "zh" ? "类型" : "Type"}
                </span>
                <span className={`px-2 py-0.5 rounded-full ${styles.badgeBg} ${styles.badgeText} text-[10px] font-semibold`}>
                  {selectedNode.type}
                </span>
              </div>

              {/* 字段列表（字段级血缘查询可能有 fields） */}
              {selectedNode.fields?.length > 0 && (
                <div className={`pt-2 border-t ${styles.divider}`}>
                  <span className={`text-[10px] ${styles.muted} uppercase tracking-wider block mb-2`}>
                    {locale === "zh" ? "字段列表" : "Fields"} ({selectedNode.fields.length})
                  </span>
                  <div className="space-y-1 max-h-40 overflow-y-auto">
                    {selectedNode.fields.map((f: any, i: number) => (
                      <div key={i} className="flex justify-between text-[10px]">
                        <span className={`font-mono ${styles.cardTextMuted}`}>{f.name || f}</span>
                        {f.type && <span className={styles.muted}>{f.type}</span>}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* 上下游关系 */}
              <div className={`pt-3 border-t ${styles.divider}`}>
                <span className={`text-[10px] ${styles.muted} uppercase tracking-wider block mb-2`}>
                  {locale === "zh" ? "上下游关系" : "Up/Downstream"} ({relatedEdges.length})
                </span>
                {relatedEdges.length === 0 ? (
                  <div className={`text-[10px] ${styles.muted}`}>
                    {locale === "zh" ? "无关联血缘" : "No related edges"}
                  </div>
                ) : (
                  <div className="space-y-1.5 max-h-60 overflow-y-auto">
                    {relatedEdges.map(e => {
                      const isSource = e.source === selectedNode.id;
                      const other = isSource ? e.target : e.source;
                      return (
                        <button
                          key={e.id}
                          onClick={() => setSelectedEdge(e)}
                          className={`w-full text-left rounded px-1.5 py-1 text-[10px] cursor-pointer transition ${
                            selectedEdge?.id === e.id ? styles.infoBg : styles.sidebarHoverBg
                          }`}
                        >
                          <div className={`flex items-center gap-1 ${styles.muted} font-mono`}>
                            <span className={isSource ? "text-emerald-500" : "text-amber-500"}>
                              {isSource ? "→" : "←"}
                            </span>
                            <span className="truncate">{other}</span>
                          </div>
                          {e.transform && (
                            <div className="text-[9px] text-blue-500 font-mono mt-0.5" title={e.transform}>
                              {e.transform.length > 60 ? e.transform.substring(0, 60) + "..." : e.transform}
                            </div>
                          )}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* 选中边详情 */}
              {selectedEdge?.transform && (
                <div className={`pt-3 border-t ${styles.divider}`}>
                  <span className={`text-[10px] text-blue-600 uppercase tracking-wider block mb-1`}>
                    {locale === "zh" ? "变换 / SQL" : "Transform / SQL"}
                  </span>
                  <pre className={`text-[10px] ${styles.cardTextMuted} ${styles.appBg} rounded p-2 whitespace-pre-wrap break-all font-mono`}>
                    {selectedEdge.transform}
                  </pre>
                </div>
              )}

              {/* ── P2 影响度分析 ──────────────────────── */}
              <div className={`pt-3 border-t ${styles.divider}`}>
                <div className="flex items-center justify-between mb-2">
                  <span className={`text-[10px] uppercase tracking-wider font-semibold ${styles.muted}`}>
                    {locale === "zh" ? "影响度分析" : "Impact Analysis"}
                  </span>
                  {impactLoading && (
                    <RefreshCw className={`w-3 h-3 ${styles.muted} animate-spin`} />
                  )}
                </div>

                {/* 层数选择 */}
                <div className="flex items-center gap-2 mb-2">
                  <span className={`text-[10px] ${styles.muted}`}>
                    {locale === "zh" ? "追溯层数" : "Depth"}
                  </span>
                  <select
                    value={impactDepth}
                    onChange={e => setImpactDepth(Math.max(1, Math.min(8, Number(e.target.value) || 1)))}
                    className={`bg-transparent border rounded px-1.5 py-0.5 text-[10px] focus:outline-none ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                  >
                    {[1, 2, 3, 4, 5, 6, 7, 8].map(d => (
                      <option key={d} value={d}>{d}</option>
                    ))}
                  </select>
                  <span className={`text-[10px] ${styles.muted}`}>
                    ({selectedNodeId ? (impactData ? (impactData.matched ? (locale === "zh" ? "已匹配" : "matched") : (locale === "zh" ? "节点未命中" : "not matched")) : (locale === "zh" ? "加载中..." : "loading...")) : (locale === "zh" ? "未选择节点" : "no node")})
                  </span>
                </div>

                {/* P2 多层展开 toggle: 开 → 画布只显示 selectedNode + N 层可达子图 */}
                {selectedNodeId && impactData && (
                  <div className={`flex items-center justify-between mb-3 px-2 py-1.5 rounded-lg border ${styles.appBg} ${focusMode ? styles.accentBorder : styles.cardBorder}`}>
                    <span className={`text-[10px] ${focusMode ? "text-indigo-600 font-semibold" : styles.muted}`}>
                      {focusMode
                        ? (locale === "zh" ? `已聚焦 ${impactDepth} 层子图` : `Focused: ${impactDepth}-hop subgraph`)
                        : (locale === "zh" ? "仅看 N 层" : "Focus N hops")}
                    </span>
                    <button
                      onClick={() => setFocusMode(v => !v)}
                      className={`relative inline-flex items-center h-4 w-7 rounded-full transition ${
                        focusMode ? styles.accentBg : styles.appBg
                      }`}
                      title={focusMode
                        ? (locale === "zh" ? "切回全量" : "Show all")
                        : (locale === "zh" ? "只看 N 层" : "Focus N hops")}
                    >
                      <span className={`inline-block h-3 w-3 rounded-full bg-white transition transform ${
                        focusMode ? "translate-x-3.5" : "translate-x-0.5"
                      }`} />
                    </button>
                  </div>
                )}

                {impactData && (
                  <>
                    {/* 风险评分卡片 */}
                    <div className={`rounded-lg border px-2 py-1.5 mb-2 ${
                      impactData.severity === "CRITICAL" ? "border-red-300 bg-red-50 dark:bg-red-500/10 dark:border-red-500/40"
                        : impactData.severity === "HIGH" ? "border-orange-300 bg-orange-50 dark:bg-orange-500/10 dark:border-orange-500/40"
                        : impactData.severity === "MEDIUM" ? "border-amber-300 bg-amber-50 dark:bg-amber-500/10 dark:border-amber-500/40"
                        : impactData.severity === "LOW" ? "border-emerald-300 bg-emerald-50 dark:bg-emerald-500/10 dark:border-emerald-500/40"
                        : `${styles.cardBorder} ${styles.appBg}`
                    }`}>
                      <div className="flex items-center justify-between">
                        <span className={`text-[10px] font-bold uppercase tracking-wider ${
                          impactData.severity === "CRITICAL" ? "text-red-600 dark:text-red-400"
                            : impactData.severity === "HIGH" ? "text-orange-600 dark:text-orange-400"
                            : impactData.severity === "MEDIUM" ? "text-amber-600 dark:text-amber-400"
                            : impactData.severity === "LOW" ? "text-emerald-600 dark:text-emerald-400"
                            : styles.muted
                        }`}>
                          {impactData.severity}
                        </span>
                        <span className={`text-[10px] ${styles.muted}`}>
                          {impactData.totalRisk}/100
                        </span>
                      </div>
                      <div className={`text-[9px] mt-0.5 ${styles.muted}`}>
                        {impactData.depth} hops · {impactData.downstream.length}↓ {impactData.upstream.length}↑
                      </div>
                    </div>

                    {/* 下游 / 上游 N 层节点列表 */}
                    <div className="space-y-2 max-h-60 overflow-y-auto">
                      {impactData.downstream.length === 0 && impactData.upstream.length === 0 && (
                        <div className={`text-[10px] ${styles.muted}`}>
                          {locale === "zh" ? "该节点在 N 层内无可达节点（无血缘边）" : "No reachable nodes within N hops"}
                        </div>
                      )}
                      {impactData.downstream.length > 0 && (
                        <div>
                          <div className={`text-[9px] font-semibold uppercase tracking-wider text-emerald-600 dark:text-emerald-400 mb-1`}>
                            ↓ {locale === "zh" ? "下游 N 层可达" : "Downstream"} ({impactData.downstream.length})
                          </div>
                          {impactData.downstream.slice(0, 20).map((n, i) => (
                            <button
                              key={`d_${i}`}
                              onClick={() => setSelectedNodeId(n.id)}
                              className={`w-full flex items-center gap-1.5 text-left px-1.5 py-0.5 rounded ${styles.sidebarHoverBg} transition text-[10px]`}
                            >
                              <span className="font-mono text-amber-500">H{n.hop}</span>
                              <span className={`truncate flex-1 ${styles.cardTextMuted}`}>{n.label}</span>
                              <span className={`text-[9px] font-semibold ${
                                n.riskScore >= 80 ? "text-red-500" : n.riskScore >= 50 ? "text-orange-500" : "text-emerald-500"
                              }`}>{n.riskScore}</span>
                            </button>
                          ))}
                          {impactData.downstream.length > 20 && (
                            <div className={`text-[9px] ${styles.muted} px-1 mt-0.5`}>
                              {locale === "zh" ? `还有 ${impactData.downstream.length - 20} 个...` : `+${impactData.downstream.length - 20} more...`}
                            </div>
                          )}
                        </div>
                      )}
                      {impactData.upstream.length > 0 && (
                        <div>
                          <div className={`text-[9px] font-semibold uppercase tracking-wider text-sky-600 dark:text-sky-400 mb-1`}>
                            ↑ {locale === "zh" ? "上游 N 层可达" : "Upstream"} ({impactData.upstream.length})
                          </div>
                          {impactData.upstream.slice(0, 20).map((n, i) => (
                            <button
                              key={`u_${i}`}
                              onClick={() => setSelectedNodeId(n.id)}
                              className={`w-full flex items-center gap-1.5 text-left px-1.5 py-0.5 rounded ${styles.sidebarHoverBg} transition text-[10px]`}
                            >
                              <span className="font-mono text-amber-500">H{n.hop}</span>
                              <span className={`truncate flex-1 ${styles.cardTextMuted}`}>{n.label}</span>
                              <span className={`text-[9px] font-semibold ${
                                n.riskScore >= 80 ? "text-red-500" : n.riskScore >= 50 ? "text-orange-500" : "text-emerald-500"
                              }`}>{n.riskScore}</span>
                            </button>
                          ))}
                          {impactData.upstream.length > 20 && (
                            <div className={`text-[9px] ${styles.muted} px-1 mt-0.5`}>
                              {locale === "zh" ? `还有 ${impactData.upstream.length - 20} 个...` : `+${impactData.upstream.length - 20} more...`}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
