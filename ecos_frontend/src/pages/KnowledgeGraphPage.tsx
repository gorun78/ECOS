/**
 * 企业知识图谱 — 本体实体关系可视化
 * Connected to /api/v1/ecos/knowledge-graph
 */
import React, { useState, useEffect } from "react";
import { Network, Loader2, AlertCircle, Building2, GitBranch } from "lucide-react";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";
import { fetchEcosKnowledgeGraph } from "../api";

export default function KnowledgeGraphPage() {
  const { t } = useLanguage();
  useTheme();
  const [graph, setGraph] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchEcosKnowledgeGraph()
      .then(setGraph)
      .catch((e: any) => setError(e.message || t("加载失败")))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return (
    <div className="h-full flex items-center justify-center">
      <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
    </div>
  );
  if (error) return (
    <div className="h-full flex items-center justify-center">
      <div className="text-center"><AlertCircle className="w-10 h-10 text-red-400 mx-auto mb-2" /><p>{error}</p></div>
    </div>
  );

  const nodes = graph?.nodes || [];
  const edges = graph?.edges || [];
  const stats = graph?.stats || {};
  const domainColors: Record<string, string> = {
    [t("采购域")]: "bg-amber-100 text-amber-700 border-amber-300",
    [t("财务域")]: "bg-emerald-100 text-emerald-700 border-emerald-300",
    [t("项目域")]: "bg-blue-100 text-blue-700 border-blue-300",
    [t("资产域")]: "bg-purple-100 text-purple-700 border-purple-300",
  };

  // Group by domain
  const byDomain: Record<string, any[]> = {};
  nodes.forEach((n: any) => {
    const d = n.domainName || t("其他");
    if (!byDomain[d]) byDomain[d] = [];
    byDomain[d].push(n);
  });

  return (
    <div className="flex-1 overflow-auto p-6 lg:p-8">
      <div className="flex items-center gap-3 mb-6">
        <Network className="w-7 h-7 text-indigo-500" />
        <div>
          <h1 className="text-2xl font-bold">{t("企业知识图谱")}</h1>
          <p className="text-sm text-slate-400 mt-1">
            {stats.nodeCount} {t("实体")} · {stats.edgeCount} {t("关系")} · {(stats.domains || []).length} {t("业务域")}
          </p>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        <StatCard icon={Building2} label={t("实体节点")} value={stats.nodeCount} color="indigo" />
        <StatCard icon={GitBranch} label={t("语义关系")} value={stats.edgeCount} color="emerald" />
        <StatCard icon={Network} label={t("业务域")} value={(stats.domains || []).length} color="amber" />
      </div>

      {/* Domain-based groups */}
      <div className="space-y-4">
        {Object.entries(byDomain).map(([domain, domainNodes]) => (
          <div key={domain} className="bg-white dark:bg-slate-800 rounded-xl border p-5">
            <div className="flex items-center gap-2 mb-3">
              <span className={`text-xs px-2 py-1 rounded border font-medium ${domainColors[domain] || "bg-slate-100"}`}>
                {domain}
              </span>
              <span className="text-xs text-slate-400">{domainNodes.length} {t("实体")}</span>
            </div>
            <div className="flex flex-wrap gap-2">
              {domainNodes.map((n: any) => (
                <span key={n.code} className="px-3 py-1.5 rounded-lg border text-sm bg-slate-50 dark:bg-slate-900/50">
                  {n.name}
                  <span className="text-[10px] text-slate-400 ml-1 font-mono">{n.code}</span>
                </span>
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* Edges */}
      <div className="mt-6 bg-white dark:bg-slate-800 rounded-xl border p-5">
        <h3 className="text-sm font-semibold mb-3">{t("语义关系")} ({edges.length})</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          {edges.map((e: any, i: number) => (
            <div key={i} className="flex items-center gap-2 p-2 rounded bg-slate-50 dark:bg-slate-900/50 text-xs">
              <span className="font-mono">{e.source}</span>
              <span className="text-[10px] px-1 rounded bg-indigo-100 dark:bg-indigo-900/30 text-indigo-600">{e.relationshipType}</span>
              <span className="font-mono">{e.target}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function StatCard({ icon: Icon, label, value, color }: { icon: any; label: string; value: number; color: string }) {
  const colors: Record<string, string> = {
    indigo: "bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600", emerald: "bg-emerald-50 dark:bg-emerald-900/20 text-emerald-600",
    amber: "bg-amber-50 dark:bg-amber-900/20 text-amber-600",
  };
  return (
    <div className="bg-white dark:bg-slate-800 rounded-xl border p-4 flex items-center gap-3">
      <div className={`p-2 rounded-lg ${colors[color]}`}><Icon className="w-5 h-5" /></div>
      <div><p className="text-xs text-slate-400">{label}</p><p className="text-xl font-bold">{value}</p></div>
    </div>
  );
}
