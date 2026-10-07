/**
 * DataSourceList — 数据源列表+搜索+操作+资源浏览
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import { Database, Plus, Trash2, RefreshCw, Zap, CheckCircle, XCircle, AlertCircle, Search, Loader2, Play } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { useDict } from "../../hooks/useDict";
import type { DataSource, DataResource, DataField } from "../../types";
import { fetchDataSources, deleteDataSource, testDataSourceConnection, collectMetadata, fetchResources, fetchFields } from "../../api";

interface DataSourceListProps { onOpenWizard: () => void; }

const getJdbcUrl = (ds: DataSource): string => {
  const cfg = ds.connectionConfig;
  if (!cfg) return "—";
  if (typeof cfg === "string") { try { const p = JSON.parse(cfg); return p.jdbcUrl || "—"; } catch { return cfg; } }
  return (cfg as Record<string, unknown>).jdbcUrl as string || "—";
};

export default function DataSourceList({ onOpenWizard }: DataSourceListProps) {
  const { styles } = useTheme();
  const { locale, t } = useLanguage();
  const { getLabel: getDsTypeLabel } = useDict("datasource_type", locale);
  const { getLabel: getDsStatusLabel, getColor: getDsStatusColor } = useDict("datasource_status", locale);

  const [dataSources, setDataSources] = useState<DataSource[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [collectingId, setCollectingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [testResults, setTestResults] = useState<Record<string, { success: boolean; message: string }>>({});
  const [collectResults, setCollectResults] = useState<Record<string, { success: boolean; message: string }>>({});
  const [expandedDs, setExpandedDs] = useState<string | null>(null);
  const [resources, setResources] = useState<DataResource[]>([]);
  const [resourcesLoading, setResourcesLoading] = useState(false);
  const [expandedResource, setExpandedResource] = useState<string | null>(null);
  const [fields, setFields] = useState<DataField[]>([]);
  const [fieldsLoading, setFieldsLoading] = useState(false);

  const loadDataSources = useCallback(() => {
    setLoading(true); setError(null);
    fetchDataSources().then((d) => { setDataSources(d); setLoading(false); })
      .catch((e: Error) => { setError(e.message || t("dw.datasource.list.loadFail")); setLoading(false); });
  }, [t]);

  useEffect(() => { loadDataSources(); }, [loadDataSources]);

  const filtered = dataSources.filter(ds => {
    if (!searchKeyword.trim()) return true;
    const kw = searchKeyword.toLowerCase();
    return (ds.datasourceName || "").toLowerCase().includes(kw) || (ds.datasourceType || "").toLowerCase().includes(kw);
  });

  const handleTest = async (id: string) => {
    setTestingId(id); setActionError(null);
    try {
      const r = await testDataSourceConnection(id);
      setTestResults(prev => ({ ...prev, [id]: { success: r.success, message: r.success ? t("dw.datasource.list.testSuccess") : t("dw.datasource.list.testFail") } }));
    } catch (e: unknown) {
      setTestResults(prev => ({ ...prev, [id]: { success: false, message: e instanceof Error ? e.message : "Test failed" } }));
    } finally { setTestingId(null); }
  };

  const handleCollect = async (id: string) => {
    setCollectingId(id); setActionError(null);
    try {
      const r = await collectMetadata(id);
      setCollectResults(prev => ({ ...prev, [id]: { success: true, message: t("dw.datasource.list.collectDone", { n: r.resourcesCollected, ms: r.elapsedMs }) } }));
    } catch (e: unknown) {
      setCollectResults(prev => ({ ...prev, [id]: { success: false, message: e instanceof Error ? e.message : "Collection failed" } }));
    } finally { setCollectingId(null); }
  };

  const handleBrowseResources = async (dsId: string) => {
    if (expandedDs === dsId) { setExpandedDs(null); setResources([]); setExpandedResource(null); setFields([]); return; }
    setExpandedDs(dsId); setExpandedResource(null); setFields([]); setResourcesLoading(true);
    try { setResources(await fetchResources(dsId)); } catch (e) { console.error(e); setResources([]); } finally { setResourcesLoading(false); }
  };

  const handleBrowseFields = async (resourceId: string) => {
    if (expandedResource === resourceId) { setExpandedResource(null); setFields([]); return; }
    setExpandedResource(resourceId); setFieldsLoading(true);
    try { setFields(await fetchFields(resourceId)); } catch (e) { console.error(e); setFields([]); } finally { setFieldsLoading(false); }
  };

  const handleDelete = async (id: string, name: string) => {
    if (!window.confirm(t("dw.datasource.list.confirmDelete", { name }))) return;
    setDeletingId(id); setActionError(null);
    try { await deleteDataSource(id); loadDataSources(); } catch (e: unknown) { setActionError(e instanceof Error ? e.message : t("dw.datasource.list.deleteFail")); } finally { setDeletingId(null); }
  };

  const statusBadge = (status: string) => {
    const s = (status || "").toUpperCase();
    const isActive = s === "ACTIVE" || s === "ONLINE" || s === "CONNECTED";
    const isError = s === "ERROR" || s === "OFFLINE" || s === "DISCONNECTED";
    const label = getDsStatusLabel(status) || status || "UNKNOWN";
    const dictColor = getDsStatusColor(status);
    return (
      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-bold border ${isActive ? "bg-green-50 border-green-200 text-green-700" : isError ? "bg-red-50 border-red-200 text-red-700" : "bg-amber-50 border-amber-200 text-amber-700"}`} style={dictColor ? { borderColor: dictColor, color: dictColor, backgroundColor: `${dictColor}15` } : undefined}>
        {isActive ? <CheckCircle className="w-3 h-3" /> : isError ? <XCircle className="w-3 h-3" /> : <AlertCircle className="w-3 h-3" />}{label}
      </span>
    );
  };

  const resultBanner = (res: { success: boolean; message: string } | undefined) =>
    !res ? null : (
      <div className={`mt-1.5 text-[10px] px-2 py-1 rounded flex items-center gap-1 ${res.success ? "bg-green-50 text-green-700 border border-green-200" : "bg-red-50 text-red-700 border border-red-200"}`}>
        {res.success ? <CheckCircle className="w-3 h-3 shrink-0" /> : <XCircle className="w-3 h-3 shrink-0" />}<span className="truncate">{res.message}</span>
      </div>
    );

  // ── Render ─────────────────────────────────────────────
  const ErrorBanner = ({ msg, onRetry, onDismiss }: { msg: string | null; onRetry?: () => void; onDismiss: () => void }) => !msg ? null : (
    <div className="rounded-lg p-3 mb-4 flex items-center gap-2 text-sm bg-red-50 border border-red-200 text-red-700">
      <AlertCircle className="w-4 h-4 shrink-0" /><span className="flex-1">{msg}</span>
      {onRetry && <button onClick={onRetry} className="inline-flex items-center gap-1 px-2 py-1 text-xs font-semibold bg-white border border-current/20 rounded hover:bg-opacity-80 transition cursor-pointer"><RefreshCw className="w-3 h-3" />{t("dw.datasource.list.retry")}</button>}
      <button onClick={onDismiss} className="text-current/60 hover:text-current cursor-pointer">&times;</button>
    </div>
  );

  return (
    <div className={`flex-1 overflow-y-auto ${styles.appBg} p-4 sm:p-6 lg:p-8 ${styles.appText} flex flex-col h-full font-sans animate-fade-in max-w-7xl mx-auto w-full`}>
      <ErrorBanner msg={error} onRetry={loadDataSources} onDismiss={() => setError(null)} />
      <ErrorBanner msg={actionError} onDismiss={() => setActionError(null)} />

      {/* Page Header */}
      <div className="flex items-center justify-between mb-6 shrink-0">
        <div>
          <h1 className={`text-xl font-bold tracking-tight ${styles.cardText} flex items-center gap-2`}><Database className={`w-5 h-5 ${styles.accentText}`} />{t("dw.datasource.list.title")}</h1>
          <p className={`text-xs ${styles.muted} mt-1.5`}>{t("dw.datasource.list.subtitle")}</p>
        </div>
        <button onClick={onOpenWizard} className={`${styles.accentBg} ${styles.accentHover} text-white rounded-lg px-4 py-2 text-xs font-semibold flex items-center gap-2 cursor-pointer transition shadow-xs`}><Plus className="w-3.5 h-3.5" />{t("dw.datasource.list.register")}</button>
      </div>

      {/* Search Bar */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 mb-5 flex items-center gap-3 shrink-0 shadow-xs`}>
        <div className={`flex-1 ${styles.inputBg} border ${styles.inputBorder} rounded-lg px-3.5 py-2 flex items-center gap-2 text-xs`}>
          <Search className={`w-3.5 h-3.5 ${styles.muted} shrink-0`} />
          <input type="text" className={`bg-transparent border-0 outline-hidden w-full ${styles.cardText} placeholder:${styles.cardTextMuted}`} placeholder={t("dw.datasource.list.searchPH")} value={searchKeyword} onChange={e => setSearchKeyword(e.target.value)} />
        </div>
        <button onClick={loadDataSources} className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold ${styles.muted} hover:${styles.cardText} hover:${styles.appBg}/50 rounded-lg transition cursor-pointer`} title={t("dw.datasource.list.refresh")}><RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} /></button>
      </div>

      {/* Table / States */}
      <div className="flex-1 overflow-y-auto pr-1 scrollbar-thin">
        {loading ? (
          <div className="py-16 text-center"><Loader2 className={`w-8 h-8 mx-auto ${styles.muted} animate-spin mb-3`} /><p className={`text-xs ${styles.muted}`}>{t("dw.datasource.list.loading")}</p></div>
        ) : filtered.length === 0 ? (
          <div className={`py-24 text-center ${styles.cardBg} border border-dashed ${styles.cardBorder} rounded-xl shadow-xs`}>
            <Database className={`w-10 h-10 mx-auto ${styles.muted} mb-2`} />
            <p className={`text-sm ${styles.cardTextMuted} font-bold`}>{searchKeyword.trim() ? t("dw.datasource.list.noMatch") : t("dw.datasource.list.empty")}</p>
          </div>
        ) : (
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-xs overflow-hidden`}>
            <table className="w-full text-xs">
              <thead>
                <tr className={`${styles.appBg} border-b ${styles.cardBorder}`}>
                  <th className={`text-left px-4 py-3 font-bold ${styles.cardText}`}>{t("dw.datasource.list.name")}</th>
                  <th className={`text-left px-4 py-3 font-bold ${styles.cardText}`}>{t("dw.datasource.list.dbType")}</th>
                  <th className={`text-left px-4 py-3 font-bold ${styles.cardText}`}>JDBC URL</th>
                  <th className={`text-left px-4 py-3 font-bold ${styles.cardText}`}>{t("dw.datasource.list.status")}</th>
                  <th className={`text-left px-4 py-3 font-bold ${styles.cardText}`}>{t("dw.datasource.list.created")}</th>
                  <th className={`text-right px-4 py-3 font-bold ${styles.cardText}`}>{t("dw.datasource.list.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((ds, idx) => {
                  const dsId = ds.datasourceId;
                  const isT = testingId === dsId, isC = collectingId === dsId, isD = deletingId === dsId;
                  const typeLabel = getDsTypeLabel(ds.datasourceType || "JDBC");
                  return (
                    <tr key={dsId} className={`border-b ${styles.cardBorder} hover:${styles.appBg}/50 transition ${idx === filtered.length - 1 ? "border-b-0" : ""}`}>
                      <td className="px-4 py-3.5"><div className="flex items-center gap-2"><Database className={`w-4 h-4 ${styles.accentText} shrink-0`} /><span className={`font-bold ${styles.cardText} text-sm`}>{ds.datasourceName || "—"}</span></div></td>
                      <td className="px-4 py-3.5"><span className={`font-mono text-[10px] font-bold ${styles.inputBg} border ${styles.inputBorder} ${styles.cardTextMuted} px-2 py-0.5 rounded-md`}>{typeLabel}</span></td>
                      <td className="px-4 py-3.5"><span className={`${styles.cardTextMuted} font-mono text-[10px] truncate max-w-[200px] block`}>{getJdbcUrl(ds)}</span></td>
                      <td className="px-4 py-3.5">{statusBadge(ds.status || "UNKNOWN")}{resultBanner(testResults[dsId])}{resultBanner(collectResults[dsId])}</td>
                      <td className={`px-4 py-3.5 ${styles.muted} font-mono text-[11px] whitespace-nowrap`}>{ds.createdAt || "—"}</td>
                      <td className="px-4 py-3.5">
                        <div className="flex items-center justify-end gap-1.5">
                          <ActionBtn label={t("dw.datasource.list.test")} loading={isT} icon={Play} color="blue" onClick={() => handleTest(dsId)} />
                          <ActionBtn label={t("dw.datasource.list.collect")} loading={isC} icon={Zap} color="purple" onClick={() => handleCollect(dsId)} />
                          <ActionBtn label={t("dw.datasource.list.browse")} loading={false} icon={Database} color="green" active={expandedDs === dsId} onClick={() => handleBrowseResources(dsId)} />
                          <ActionBtn label="" loading={isD} icon={Trash2} color="red" onClick={() => handleDelete(dsId, ds.datasourceName || dsId)} />
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {/* Resource Browser Panel */}
            {expandedDs && (
              <div className="border-t-2 border-green-100 bg-green-50/30 p-4 animate-fade-in">
                <div className="flex items-center gap-2 mb-3">
                  <Database className="w-4 h-4 text-green-600" />
                  <h3 className={`text-sm font-bold ${styles.cardText}`}>{t("dw.datasource.list.resources")}</h3>
                  <span className={`text-[10px] ${styles.cardTextMuted}`}>({resources.length})</span>
                  {resourcesLoading && <Loader2 className="w-3 h-3 animate-spin text-green-500" />}
                </div>
                {resources.length === 0 && !resourcesLoading ? (
                  <p className={`text-xs ${styles.muted} py-4 text-center`}>{t("dw.datasource.list.noResources")}</p>
                ) : (
                  <div className="space-y-1.5 max-h-96 overflow-y-auto pr-1">
                    {resources.map(r => (
                      <div key={r.resourceId}>
                        <button onClick={() => handleBrowseFields(r.resourceId)} className={`w-full text-left px-3 py-2 rounded-lg text-xs flex items-center gap-2 transition cursor-pointer ${expandedResource === r.resourceId ? "bg-green-100 border border-green-300" : `${styles.cardBg} border ${styles.cardBorder} hover:border-green-300 hover:bg-green-50`}`}>
                          <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${r.resourceType === "TABLE" ? "bg-blue-100 text-blue-700" : "bg-purple-100 text-purple-700"}`}>{r.resourceType || "?"}</span>
                          <span className={`font-bold ${styles.cardText} flex-1`}>{r.resourceName}</span>
                          <span className={`text-[10px] ${styles.cardTextMuted}`}>{r.fieldCount ?? "?"} {t("dw.datasource.list.fields")}</span>
                        </button>
                        {expandedResource === r.resourceId && (
                          <div className="mt-1 ml-4 border-l-2 border-green-200 pl-4 animate-fade-in">
                            {fieldsLoading ? (
                              <div className={`py-2 flex items-center gap-2 text-xs ${styles.muted}`}><Loader2 className="w-3 h-3 animate-spin" />{t("dw.datasource.list.loadingFields")}</div>
                            ) : fields.length === 0 ? (
                              <p className={`text-xs ${styles.muted} py-2`}>{t("dw.datasource.list.noFields")}</p>
                            ) : (
                              <table className="w-full text-[10px]">
                                <thead><tr className={`${styles.muted} border-b border-green-200`}><th className="text-left py-1.5 font-semibold w-6" /><th className="text-left py-1.5 font-semibold">{t("dw.datasource.list.field")}</th><th className="text-left py-1.5 font-semibold">{t("dw.datasource.list.type")}</th><th className="text-left py-1.5 font-semibold">{t("dw.datasource.list.nullable")}</th><th className="text-left py-1.5 font-semibold">{t("dw.datasource.list.length")}</th></tr></thead>
                                <tbody>
                                  {fields.map(f => (
                                    <tr key={f.fieldId} className="border-b border-green-100/50 hover:bg-green-50/50">
                                      <td className="py-1.5">{f.primaryKey ? <span className="text-amber-500 text-[10px]" title="Primary Key">🔑</span> : null}</td>
                                      <td className={`py-1.5 font-mono ${styles.cardText} font-bold`}>{f.fieldName}</td>
                                      <td className={`py-1.5 ${styles.muted}`}>{f.dataType}</td>
                                      <td className="py-1.5"><span className={`px-1.5 py-0.5 rounded text-[9px] font-bold ${f.nullable ? "bg-amber-50 text-amber-600 border border-amber-200" : `${styles.appBorder} ${styles.cardTextMuted} border ${styles.inputBorder}`}`}>{f.nullable ? "NULL" : "NOT NULL"}</span></td>
                                      <td className={`py-1.5 ${styles.muted} font-mono`}>{f.dataLength || "—"}</td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            )}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Count badge */}
      <div className={`mt-3 text-[10px] ${styles.muted} text-right shrink-0`}>
        {t("dw.datasource.list.total")} <span className={`font-bold ${styles.cardTextMuted}`}>{filtered.length}</span> {t("dw.datasource.list.unit")}
        {searchKeyword.trim() && dataSources.length !== filtered.length ? ` (${t("dw.datasource.list.filteredFrom")} ${dataSources.length})` : ""}
      </div>
    </div>
  );
}

// ── Action button helper ─────────────────────────────────
function ActionBtn({ label, loading, icon: Icon, color, active, onClick }: { label: string; loading: boolean; icon: React.ElementType; color: string; active?: boolean; onClick: () => void }) {
  const colors: Record<string, string> = {
    blue: "border-blue-200 bg-blue-50 text-blue-700 hover:bg-blue-100",
    purple: "border-purple-200 bg-purple-50 text-purple-700 hover:bg-purple-100",
    green: "border-green-200 bg-green-50 text-green-700 hover:bg-green-100",
    red: "border-red-200 bg-red-50 text-red-600 hover:bg-red-100",
  };
  const activeColors: Record<string, string> = {
    green: "border-green-300 bg-green-100 text-green-700",
  };
  const cls = active && activeColors[color] ? activeColors[color] : colors[color] || colors.blue;
  return (
    <button onClick={onClick} disabled={loading} className={`inline-flex items-center gap-1 px-2.5 py-1.5 text-[10px] font-semibold rounded-md border transition cursor-pointer ${cls} disabled:opacity-50`}>
      {loading ? <Loader2 className="w-3 h-3 animate-spin" /> : label ? <><Icon className="w-3 h-3" />{label}</> : <Icon className="w-3 h-3" />}
    </button>
  );
}
