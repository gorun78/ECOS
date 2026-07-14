/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Search, Database, Cpu, Activity, Shield, Filter, Plus, FileText, Settings, ShieldAlert, BadgeInfo, AlertCircle, X, RefreshCw, Loader2, Table, Eye, GitBranch } from "lucide-react";
import { DataAsset, DataResource, DataSource } from "../types";
import { fetchDataSources, fetchResources, fetchAllResources, BulkResource } from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { useDict } from "../hooks/useDict";

/** Map a DataResource to a DataAsset for the existing catalog UI */
function resourceToAsset(r: DataResource, ds: DataSource): DataAsset {
  return {
    id: r.resourceId,
    name: r.resourceName,
    description: `${ds.datasourceName} · ${r.sourcePath || r.resourceName}`,
    type: r.resourceType === "VIEW" ? "view" : "dataset",
    owner: ds.datasourceName || "system",
    domain: r.sourcePath?.split(".")[0] || "default",
    tags: [r.resourceType || "TABLE", ds.datasourceType || "JDBC"],
    status: "Healthy" as const,
    qualityScore: 100,
    rows: 0,
    columns: r.fieldCount || 0,
    storageSize: "—",
    updatedAt: ds.createdAt?.slice(0, 10) || "—",
    schema: [],
    qualityRules: [],
    history: [],
    permissions: { owner: [ds.datasourceName || "system"], editor: [], viewer: [] },
  };
}

/** Map a BulkResource (from batch endpoint) to a DataAsset */
function bulkToAsset(r: BulkResource): DataAsset {
  return {
    id: r.resourceId,
    name: r.resourceName,
    description: `${r.datasourceName} · ${r.sourcePath || r.resourceName}`,
    type: r.resourceType === "VIEW" ? "view" : "dataset",
    owner: r.datasourceName || "system",
    domain: r.sourcePath?.split(".")[0] || "default",
    tags: [r.resourceType || "TABLE", r.datasourceType || "JDBC"],
    status: "Healthy" as const,
    qualityScore: 100,
    rows: 0,
    columns: r.fieldCount || 0,
    storageSize: "—",
    updatedAt: "—",
    schema: [],
    qualityRules: [],
    history: [],
    permissions: { owner: [r.datasourceName || "system"], editor: [], viewer: [] },
  };
}

export default function DataCatalog() {
  const navigate = useNavigate();
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const { getLabel: getTypeLabel, getOptions: typeOptions } = useDict("resource_type", locale);
  const { getLabel: getStatusLabel } = useDict("catalog_status", locale);
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState<string>("all");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [dataAssets, setDataAssets] = useState<DataAsset[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [stats, setStats] = useState({ datasources: 0, tables: 0, views: 0 });

  const loadAssets = async () => {
    setLoading(true);
    setError(null);
    try {
      // 优先使用批量端点：一次 HTTP 返回所有资源+数据源信息
      const bulk = await fetchAllResources();
      if (bulk.length > 0) {
        const allAssets = bulk.map(bulkToAsset);
        setDataAssets(allAssets);
        const dsIds = new Set(bulk.map(r => r.datasourceId));
        setStats({
          datasources: dsIds.size,
          tables: allAssets.filter(a => a.type === "dataset").length,
          views: allAssets.filter(a => a.type === "view").length,
        });
        return;
      }
      // 兜底：并行拉取（Promise.allSettled 替代串行 N+1）
      const sources = await fetchDataSources();
      const allAssets: DataAsset[] = [];
      const results = await Promise.allSettled(
        sources.map(ds =>
          fetchResources(ds.datasourceId).then(resources =>
            resources.map(r => resourceToAsset(r, ds))
          )
        )
      );
      for (const result of results) {
        if (result.status === "fulfilled") {
          allAssets.push(...result.value);
        }
      }
      setDataAssets(allAssets);
      setStats({
        datasources: sources.length,
        tables: allAssets.filter(a => a.type === "dataset").length,
        views: allAssets.filter(a => a.type === "view").length,
      });
    } catch (e: any) {
      console.warn("DataCatalog: failed to load assets", e.message);
      setError(locale === "zh" ? "无法加载数据资源，请先注册数据源并采集元数据" : "Failed to load data resources. Register a datasource and collect metadata first.");
      setDataAssets([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadAssets(); }, []);

  const filtered = dataAssets.filter((asset) => {
    const matchesSearch =
      asset.name.toLowerCase().includes(search.toLowerCase()) ||
      asset.description.toLowerCase().includes(search.toLowerCase()) ||
      asset.owner.toLowerCase().includes(search.toLowerCase());
    const matchesType = typeFilter === "all" || asset.type === typeFilter;
    const matchesStatus = statusFilter === "all" || asset.status === statusFilter;
    return matchesSearch && matchesType && matchesStatus;
  });

  return (
    <div className={`flex-1 overflow-y-auto ${styles.appBg} p-4 sm:p-6 lg:p-8 ${styles.appText} flex flex-col h-full font-sans animate-fade-in max-w-7xl mx-auto w-full`}>
      
      {/* Status Banner */}
      {error && (
        <div className="rounded-lg p-3 mb-4 flex items-center gap-2 text-sm bg-amber-50 border border-amber-200 text-amber-700">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span className="flex-1">{error}</span>
          <button onClick={loadAssets} className="inline-flex items-center gap-1 px-2 py-1 text-xs font-semibold bg-white border border-current/20 rounded hover:bg-opacity-80 transition">
            <RefreshCw className="w-3 h-3" />{locale === "zh" ? "重试" : "Retry"}
          </button>
          <button onClick={() => setError(null)} className="text-current/60 hover:text-current">&times;</button>
        </div>
      )}

      {/* Page Header */}
      <div className="flex items-center justify-between mb-4 shrink-0">
        <div>
          <h1 className={`text-xl font-bold tracking-tight ${styles.cardText} flex items-center gap-2`}>
            <Database className="w-5 h-5 text-indigo-500" />
            {t("catalog.title")}
          </h1>
          <p className={`text-xs ${styles.muted} mt-1.5`}>
            {stats.datasources > 0
              ? `${stats.datasources} ${locale === "zh" ? "个数据源" : "datasources"} · ${stats.tables} ${locale === "zh" ? "张表" : "tables"} · ${stats.views} ${locale === "zh" ? "个视图" : "views"}`
              : t("catalog.desc")}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => navigate("/lineage")}
            className={`${styles.cardBg} hover:bg-slate-50 border ${styles.cardBorder} hover:border-slate-300 ${styles.cardText} rounded-lg px-3 py-2 text-xs font-medium flex items-center gap-1.5 cursor-pointer transition shadow-xs`}
          >
            <GitBranch className="w-3.5 h-3.5 text-indigo-500" />
            {locale === "zh" ? "全局血缘" : "Lineage"}
          </button>
          <button
            onClick={() => navigate("/dq_dashboard")}
            className={`${styles.cardBg} hover:bg-slate-50 border ${styles.cardBorder} hover:border-slate-300 ${styles.cardText} rounded-lg px-3 py-2 text-xs font-medium flex items-center gap-1.5 cursor-pointer transition shadow-xs`}
          >
            <Shield className="w-3.5 h-3.5 text-emerald-500" />
            {locale === "zh" ? "质量仪表盘" : "DQ Dashboard"}
          </button>
          <button
            onClick={() => navigate("/datasources")}
            className="bg-indigo-500 hover:bg-indigo-600 text-white rounded-lg px-4 py-2 text-xs font-semibold flex items-center gap-2 cursor-pointer transition shadow-xs"
          >
            <Database className="w-3.5 h-3.5" />
            {locale === "zh" ? "管理数据源" : "Manage Sources"}
          </button>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 mb-5 flex flex-col sm:flex-row flex-wrap items-stretch sm:items-center gap-3 shrink-0 shadow-xs`}>
        <div className={`w-full sm:flex-1 sm:min-w-[200px] rounded-lg px-3.5 py-2 flex items-center gap-2 text-xs ${styles.inputBg} ${styles.inputBorder} border`}>
          <Search className="w-3.5 h-3.5 text-slate-400 shrink-0" />
          <input
            type="text"
            className={`bg-transparent border-0 outline-hidden w-full ${styles.inputText} placeholder-slate-400`}
            placeholder={t("catalog.placeholder")}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="flex items-center gap-2 text-xs">
          <Filter className="w-3.5 h-3.5 text-slate-400" />
          <select
            className={`rounded-lg px-3 py-1.5 text-xs outline-hidden hover:opacity-80 transition cursor-pointer font-sans ${styles.inputBg} ${styles.inputBorder} border ${styles.inputText}`}
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
          >
            <option value="all">{locale === "zh" ? "所有资源类型" : "All Types"}</option>
            {typeOptions().length > 0
              ? typeOptions().map((opt) => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))
              : (<>
                  <option value="dataset">{locale === "zh" ? "物理表 (TABLE)" : "Tables"}</option>
                  <option value="view">{locale === "zh" ? "视图 (VIEW)" : "Views"}</option>
                </>)
            }
          </select>

          <select
            className={`rounded-lg px-3 py-1.5 text-xs outline-hidden hover:opacity-80 transition cursor-pointer font-sans ${styles.inputBg} ${styles.inputBorder} border ${styles.inputText}`}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="all">{locale === "zh" ? "所有健康等级" : "All Health Stages"}</option>
            <option value="Healthy">{getStatusLabel("Healthy") !== "Healthy" ? getStatusLabel("Healthy") : locale === "zh" ? "正常运行 (已通过校验)" : "Healthy (Passed Checks)"}</option>
            <option value="Warning">{getStatusLabel("Warning") !== "Warning" ? getStatusLabel("Warning") : locale === "zh" ? "存在警告 (待审查)" : "Warning (Review Needed)"}</option>
            <option value="Failed">{getStatusLabel("Failed") !== "Failed" ? getStatusLabel("Failed") : locale === "zh" ? "已失效 (流水线降级)" : "Failed (Degraded Pipeline)"}</option>
          </select>
        </div>
      </div>

      {/* Grid of Datasets */}
      <div className="flex-1 overflow-y-auto space-y-3 pr-1 scrollbar-thin">
        {loading ? (
          <div className={`py-24 text-center ${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-xs`}>
            <Loader2 className="w-10 h-10 mx-auto text-indigo-500 mb-2 animate-spin" />
            <p className={`text-sm ${styles.muted} font-bold`}>{locale === "zh" ? "正在加载数据资源..." : "Loading data resources..."}</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className={`py-24 text-center ${styles.cardBg} border border-dashed ${styles.cardBorder} rounded-xl shadow-xs`}>
            <ShieldAlert className="w-10 h-10 mx-auto text-slate-300 mb-2" />
            <p className={`text-sm ${styles.muted} font-bold`}>{locale === "zh" ? "在当前的过滤条件下，没有符合条件的企业资产" : "No matching catalogs in target filter options"}</p>
          </div>
        ) : (
          filtered.map((asset) => {
            return (
              <div
                key={asset.id}
                id={`catalog-asset-${asset.id}`}
                onClick={() => navigate("/dataset_explorer/" + asset.id)}
                className={`${styles.cardBg} hover:bg-slate-50 border ${styles.cardBorder} hover:border-slate-300 rounded-xl p-5 transition duration-150 cursor-pointer flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xs`}
              >
                {/* Visual block */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2.5 mb-2 flex-wrap">
                    <span className={`text-sm font-bold ${styles.cardText} hover:text-indigo-500 transition`}>
                      {asset.name}
                    </span>
                    <span className="text-[10px] font-bold font-mono bg-slate-50 border border-slate-200 text-slate-500 px-2.5 py-0.5 rounded-md">
                      {asset.domain}
                    </span>
                    <span
                      className={`text-[10px] px-2 py-0.5 font-bold rounded-md border ${
                        asset.status === "Healthy"
                          ? "bg-green-50 border-green-200 text-green-700"
                          : asset.status === "Warning"
                          ? "bg-amber-50 border-amber-200 text-amber-700"
                          : "bg-red-50 border-red-200 text-red-700"
                      }`}
                    >
                      {getStatusLabel(asset.status) !== asset.status
                        ? getStatusLabel(asset.status)
                        : locale === "zh"
                        ? asset.status === "Healthy"
                          ? "良好"
                          : asset.status === "Warning"
                          ? "警告"
                          : "故障"
                        : asset.status}
                    </span>
                  </div>

                  <p className={`text-xs ${styles.muted} line-clamp-2 max-w-3xl leading-relaxed`}>
                    {asset.description}
                  </p>

                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 mt-4.5 text-[11px] text-slate-400 font-mono">
                    <span className="flex items-center gap-1.5">
                      <Database className="w-3.5 h-3.5 text-slate-400" />
                      {locale === "zh" ? "数据源" : "Source"}: <strong className={`${styles.cardText} font-sans font-bold`}>{asset.owner}</strong>
                    </span>
                    <span>•</span>
                    <span>
                      {locale === "zh" ? "列数" : "Columns"}: <strong className={`${styles.cardText} font-sans font-bold`}>{asset.columns}</strong>
                    </span>
                    <span>•</span>
                    <span>
                      {locale === "zh" ? "类型" : "Type"}: <span className={`text-[10px] px-1.5 py-0.5 rounded font-bold font-sans ${
                        asset.type === "view" 
                          ? "bg-purple-50 text-purple-600 border border-purple-200" 
                          : "bg-blue-50 text-blue-600 border border-blue-200"
                      }`}>{asset.type === "view" ? "VIEW" : "TABLE"}</span>
                    </span>
                    {asset.tags && asset.tags.length > 0 && (
                      <>
                        <span>•</span>
                        <span className="flex items-center gap-1">
                          {asset.tags.map((tag, i) => (
                            <span key={i} className="text-[10px] bg-slate-100 border border-slate-200 px-1.5 py-0.5 rounded text-slate-500 font-sans">{tag}</span>
                          ))}
                        </span>
                      </>
                    )}
                  </div>
                </div>

                {/* Right side: column count badge */}
                <div className="flex items-center gap-4 shrink-0 self-end md:self-center">
                  <div className="text-right">
                    <span className="text-[10px] uppercase font-bold font-mono tracking-wider text-slate-450 block">{locale === "zh" ? "字段数" : "Fields"}</span>
                    <span className={`text-xl font-extrabold font-sans ${styles.cardText}`}>
                      {asset.columns}
                    </span>
                  </div>
                </div>

              </div>
            );
          })
        )}
      </div>

    </div>
  );
}
