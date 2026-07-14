/**
 * ObjectExplorer — Object Runtime 全链路浏览器
 * 支持浏览/新建/编辑/状态流转/关系图/时间线
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useMemo, useRef } from "react";
import {
  Search, Plus, Box, Activity, Clock, GitBranch,
  AlertCircle, Check, X, Edit3, Loader2, ArrowLeft, ArrowRight,
  FileText, Link2, Tag, Trash2, ChevronRight, User,
  Calendar, Settings, RefreshCw, ArrowRightLeft, Shield,
  ChevronDown
} from "lucide-react";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";
import {
  fetchObjects, searchObjects, fetchObjectDetail, fetchObjectSchema,
  createObject, updateObject, deleteObject,
  ObjectData, SchemaProperty,
  fetchAvailableTransitions, executeTransition,
  createObjectRelationship, fetchObjectTimeline,
  TimelineEvent,
  fetchEntityList, EntityListItem,
} from "../api";
import DataTable, { ColumnConfig } from "../components/common/DataTable";
import { FALLBACK_ENTITIES, EVENT_LABELS, STATUS_COLORS, PAGE_SIZE, type Relation } from "./ObjectExplorer/helpers";

// ---- Main component ----
export default function ObjectExplorer() {
  const { t } = useLanguage();
  useTheme();
  const [entityCode, setEntityCode] = useState<string>("Customer");
  const [entityList, setEntityList] = useState<EntityListItem[]>([]);
  const [entityListLoading, setEntityListLoading] = useState(true);
  const [entityListError, setEntityListError] = useState(false);
  const [objects, setObjects] = useState<ObjectData[]>([]);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [searchQ, setSearchQ] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ObjectData | null>(null);
  const [detailRelations, setDetailRelations] = useState<Relation[]>([]);
  const [detailTimeline, setDetailTimeline] = useState<TimelineEvent[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [activeDetailTab, setActiveDetailTab] = useState<"properties" | "relations" | "timeline">("properties");
  const [showForm, setShowForm] = useState<"create" | "edit" | null>(null);
  const [schema, setSchema] = useState<SchemaProperty[]>([]);
  const [formData, setFormData] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [statusChanging, setStatusChanging] = useState(false);
  const [showStatusDropdown, setShowStatusDropdown] = useState(false);

  // Gap 1: Available transitions from backend
  const [availableTransitions, setAvailableTransitions] = useState<{ transitionCode: string; toStatus: string; transitionName: string }[]>([]);

  // Gap 2: Relationship form
  const [showRelationForm, setShowRelationForm] = useState(false);
  const [relFormData, setRelFormData] = useState({ targetObjectId: "", targetEntityCode: "", relationshipCode: "", relationshipType: "OneToMany" });
  const [relCreating, setRelCreating] = useState(false);

  // Gap 3: Timeline pagination
  const [timelinePage, setTimelinePage] = useState(1);
  const [timelineTotal, setTimelineTotal] = useState(0);
  const [timelineLoading, setTimelineLoading] = useState(false);

  // Navigation ref for cross-entity related-object jumps
  const navigatingRef = useRef(false);

  const navigateToRelated = (targetEntityCode: string, targetObjectId: string) => {
    if (!targetEntityCode || !targetObjectId) return;
    navigatingRef.current = true;
    setEntityCode(targetEntityCode);
    setSelectedId(targetObjectId);
  };

  // ── Load entity list dynamically from API ──
  useEffect(() => {
    setEntityListLoading(true);
    setEntityListError(false);
    fetchEntityList()
      .then(list => {
        if (list && list.length > 0) {
          setEntityList(list);
          setEntityCode(list[0].code);
        } else {
          // API returned empty list, use fallback
          setEntityListError(true);
        }
      })
      .catch(() => {
        setEntityListError(true);
      })
      .finally(() => setEntityListLoading(false));
  }, []);

  // Load objects
  const loadObjects = useCallback(async (ec: string, page: number, kw?: string) => {
    setLoading(true);
    setError(null);
    try {
      if (kw && kw.trim()) {
        const result = await searchObjects(kw, ec);
        setObjects(result.data);
        setTotal(result.total);
      } else {
        const result = await fetchObjects(ec, kw, page, PAGE_SIZE);
        setObjects(result.data);
        setTotal(result.total);
      }
    } catch (e: any) {
      setError(e.message || "加载失败");
      setObjects([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    setCurrentPage(1);
    if (!navigatingRef.current) {
      setSelectedId(null);
    }
    navigatingRef.current = false;
    loadObjects(entityCode, 1, searchQ);
  }, [entityCode]);

  useEffect(() => {
    loadObjects(entityCode, currentPage, searchQ);
  }, [currentPage]);

  // Load entity schema
  useEffect(() => {
    fetchObjectSchema(entityCode).then(s => setSchema(s.properties || []))
      .catch(() => { /* schema endpoint may not exist — use empty schema */ });
  }, [entityCode]);

  // Load detail when an object is selected
  useEffect(() => {
    if (!selectedId) { setDetail(null); setDetailRelations([]); setDetailTimeline([]); setAvailableTransitions([]); return; }
    setDetailLoading(true);
    setTimelinePage(1);
    setTimelineTotal(0);
    Promise.all([
      fetchObjectDetail(entityCode, selectedId),
      fetchAvailableTransitions(entityCode, selectedId).catch((): null => { setError('Transitions 加载失败'); return null; }),
    ])
      .then(([d, transitions]) => {
        setDetail(d);
        setDetailRelations(d.relations || []);
        // Don't load timeline from detail — it's fetched independently
        if (transitions) {
          setAvailableTransitions(transitions.availableTransitions || []);
        }
      })
      .catch(() => { /* detail endpoint may not exist */ })
      .finally(() => setDetailLoading(false));
  }, [selectedId, entityCode]);

  // Gap 3: Load timeline independently when tab is selected
  useEffect(() => {
    if (!selectedId || activeDetailTab !== "timeline") return;
    setTimelineLoading(true);
    fetchObjectTimeline(entityCode, selectedId, timelinePage, PAGE_SIZE)
      .then(page => {
        if (timelinePage === 1) {
          setDetailTimeline(page.data || []);
        } else {
          setDetailTimeline(prev => [...prev, ...(page.data || [])]);
        }
        setTimelineTotal(page.total);
      })
      .catch(e => setError(e.message))
      .finally(() => setTimelineLoading(false));
  }, [selectedId, entityCode, activeDetailTab, timelinePage]);

  const handleSearch = () => {
    setCurrentPage(1);
    loadObjects(entityCode, 1, searchQ);
  };

  const handleEntityChange = (ec: string) => {
    setEntityCode(ec);
    setSelectedId(null);
    setSearchQ("");
  };

  // ---- Create ----
  const handleCreate = async () => {
    try {
      const created = await createObject(entityCode, formData);
      setObjects(prev => [created, ...prev]);
      setTotal(prev => prev + 1);
      setSelectedId(created.id);
      setShowForm(null);
      setFormData({});
    } catch (e: any) {
      setError(e.message);
    }
  };

  // ---- Edit ----
  const handleEdit = async () => {
    if (!selectedId) return;
    try {
      const updated = await updateObject(entityCode, selectedId, formData);
      setDetail(updated);
      setObjects(prev => prev.map(o => o.id === selectedId ? { ...o, ...updated } : o));
      setShowForm(null);
      setFormData({});
    } catch (e: any) {
      setError(e.message);
    }
  };

  const openEditForm = () => {
    if (!detail) return;
    const data: Record<string, string> = {};
    schema.forEach(prop => {
      data[prop.code] = detail[prop.code] != null ? String(detail[prop.code]) : "";
    });
    // Also include non-schema fields
    Object.entries(detail).forEach(([k, v]) => {
      if (!["id", "entityCode", "status", "createdAt", "updatedAt", "relations", "timeline"].includes(k) && data[k] === undefined) {
        data[k] = v != null ? String(v) : "";
      }
    });
    setFormData(data);
    setShowForm("edit");
  };

  // ---- Delete ----
  const handleDelete = async (id: string) => {
    if (!confirm(t("object_explorer.confirm_delete"))) return;
    try {
      await deleteObject(entityCode, id);
      setObjects(prev => prev.filter(o => o.id !== id));
      setTotal(prev => prev - 1);
      if (selectedId === id) setSelectedId(null);
    } catch (e: any) { setError(e.message); }
  };

  // Gap 2: Create relationship
  const handleCreateRelation = async () => {
    if (!selectedId || !relFormData.targetObjectId || !relFormData.relationshipCode) return;
    setRelCreating(true);
    try {
      await createObjectRelationship(entityCode, selectedId, {
        targetObjectId: relFormData.targetObjectId,
        targetEntityCode: relFormData.targetEntityCode || entityCode,
        relationshipCode: relFormData.relationshipCode,
        relationshipType: relFormData.relationshipType,
        properties: {},
      });
      setShowRelationForm(false);
      // Refresh relations
      fetchObjectDetail(entityCode, selectedId)
        .then(d => setDetailRelations(d.relations || []))
        .catch((e: any) => { console.error('Refresh relations failed:', e); });
    } catch (e: any) { setError(e.message); }
    finally { setRelCreating(false); }
  };

  // Gap 1: Status change via backend transition
  const handleStatusChange = async (transitionCode: string) => {
    if (!selectedId) return;
    setShowStatusDropdown(false);
    setStatusChanging(true);
    try {
      const result = await executeTransition(entityCode, selectedId, transitionCode, "admin");
      setDetail(prev => prev ? { ...prev, status: result.newStatus } : null);
      setObjects(prev => prev.map(o => o.id === selectedId ? { ...o, status: result.newStatus } : o));
      // Refresh available transitions after status change
      fetchAvailableTransitions(entityCode, selectedId)
        .then(t => setAvailableTransitions(t.availableTransitions || []))
        .catch((e: any) => { console.error('Refresh transitions failed:', e); });
    } catch (e: any) { setError(e.message); }
    finally { setStatusChanging(false); }
  };

  // Derive display name from object data
  const displayName = (obj: ObjectData) => obj.name || obj.code || obj.id?.slice(0, 8) || "—";

  // ---- Build table columns from schema ----
  const tableColumns: ColumnConfig<ObjectData>[] = useMemo(() => {
    const cols: ColumnConfig<ObjectData>[] = [
      {
        key: "id",
        label: "ID",
        width: "100px",
        render: (_, record) => (
          <span className="font-mono text-[10px] text-slate-400">{record.id?.slice(0, 12)}</span>
        ),
      },
      {
        key: "status",
        label: "状态",
        width: "80px",
        render: (val) => (
          <span className={`text-[10px] px-1.5 py-0.5 rounded border font-semibold ${STATUS_COLORS[val] || STATUS_COLORS.Draft}`}>
            {val || "Draft"}
          </span>
        ),
      },
    ];

    if (schema.length > 0) {
      const schemaCols: ColumnConfig<ObjectData>[] = schema.map(prop => ({
        key: prop.code,
        label: prop.name || prop.code,
        render: (val: any) => (
          <span className="truncate block max-w-[180px]" title={val != null ? String(val) : ""}>
            {val != null ? String(val) : <span className="text-slate-300 italic">—</span>}
          </span>
        ),
      }));
      // Insert schema columns after ID
      cols.splice(1, 0, ...schemaCols);
    } else {
      // Fallback columns
      cols.splice(1, 0,
        { key: "name", label: "名称", render: (val: any) => <span className="font-medium text-slate-700 truncate block max-w-[160px]">{val || "—"}</span> },
        { key: "code", label: "编码", render: (val: any) => <span className="font-mono text-[10px] truncate block max-w-[120px]">{val || "—"}</span> },
      );
    }

    cols.push({
      key: "createdAt",
      label: "创建时间",
      width: "140px",
      render: (val: any) => (
        <span className="text-[10px] text-slate-400">
          {val ? val.replace("T", " ").slice(0, 19) : "—"}
        </span>
      ),
    });

    return cols;
  }, [schema]);

  return (
    <div className="flex flex-col h-full bg-slate-50 font-sans text-slate-800">
      {/* Error toast */}
      {error && (
        <div className="absolute top-4 right-4 z-50 bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-xs flex items-center gap-2 shadow-lg max-w-md animate-in slide-in-from-top-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span className="flex-1">{error}</span>
          <button onClick={() => setError(null)} className="hover:bg-red-100 rounded p-0.5"><X className="w-3 h-3" /></button>
        </div>
      )}

      {/* ── Top Bar ── */}
      <div className="shrink-0 bg-white border-b border-slate-200 px-4 py-3 flex items-center gap-3">
        {/* Entity selector dropdown */}
        <div className="relative">
          <select
            value={entityCode}
            onChange={(e) => handleEntityChange(e.target.value)}
            className="appearance-none bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 pr-8 text-xs font-semibold text-slate-700 outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 cursor-pointer transition"
          >
            {(() => {
              const entities = entityList.length > 0
                ? entityList.map(e => e.code)
                : FALLBACK_ENTITIES;
              return entities.map(ec => (
                <option key={ec} value={ec}>{ec}</option>
              ));
            })()}
          </select>
          <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400 pointer-events-none" />
        </div>

        {/* Search input */}
        <div className="flex-1 max-w-md bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 flex items-center gap-2 text-xs transition focus-within:border-blue-500 focus-within:ring-1 focus-within:ring-blue-500">
          <Search className="w-3.5 h-3.5 text-slate-400 shrink-0" />
          <input
            type="text"
            value={searchQ}
            onChange={e => setSearchQ(e.target.value)}
            onKeyDown={e => e.key === "Enter" && handleSearch()}
            placeholder="搜索对象..."
            className="bg-transparent border-0 outline-none w-full placeholder-slate-400"
          />
          {searchQ && (
            <button onClick={() => { setSearchQ(""); loadObjects(entityCode, 1, ""); }} className="text-slate-400 hover:text-slate-600">
              <X className="w-3 h-3" />
            </button>
          )}
        </div>

        {/* Refresh + Create */}
        <div className="flex items-center gap-1.5">
          <button
            onClick={() => loadObjects(entityCode, currentPage, searchQ)}
            className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition"
            title="刷新"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => { setShowForm("create"); setFormData({}); }}
            className="bg-blue-500 hover:bg-blue-600 text-white rounded-lg px-3 py-2 text-xs font-semibold flex items-center gap-1.5 transition shrink-0"
          >
            <Plus className="w-3.5 h-3.5" />新建
          </button>
        </div>
      </div>

      {/* ── Main Area: Table + Detail Split ── */}
      <div className="flex-1 flex flex-col lg:flex-row min-h-0">
        {/* Left: Data Table */}
        <div className="flex-1 min-w-0 flex flex-col border-r border-slate-200 bg-white">
          <DataTable<ObjectData>
            columns={tableColumns}
            data={objects}
            rowKey="id"
            loading={loading}
            emptyTitle={`暂无${entityCode}对象`}
            emptyDescription="点击「新建」按钮创建第一个对象"
            emptyIcon={<Box className="w-8 h-8 text-slate-300" />}
            emptyAction={{ label: "新建对象", onClick: () => { setShowForm("create"); setFormData({}); } }}
            pageSize={PAGE_SIZE}
            currentPage={currentPage}
            total={total}
            onPageChange={setCurrentPage}
            onRowClick={(record) => setSelectedId(record.id)}
            className="flex-1"
          />
        </div>

        {/* Right: Detail Panel */}
        <div className="w-full lg:w-[420px] shrink-0 flex flex-col bg-white min-w-0">
          {!selectedId ? (
            <div className="flex-1 flex items-center justify-center text-slate-400 text-xs">
              <div className="text-center">
                <ChevronRight className="w-10 h-10 mx-auto mb-3 text-slate-300" />
                选择左侧对象查看详情
              </div>
            </div>
          ) : detailLoading ? (
            <div className="flex-1 flex items-center justify-center">
              <Loader2 className="w-6 h-6 text-slate-400 animate-spin" />
            </div>
          ) : detail ? (
            <>
              {/* Detail header */}
              <div className="p-4 border-b border-slate-200 shrink-0">
                <div className="flex items-start justify-between mb-2">
                  <div className="min-w-0">
                    <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2 truncate">
                      <Box className="w-4 h-4 text-blue-500 shrink-0" />
                      {displayName(detail)}
                    </h2>
                    <div className="text-[10px] text-slate-400 font-mono mt-0.5">
                      {detail.entityCode} · {detail.id}
                    </div>
                  </div>
                </div>

                {/* Status + Actions row */}
                <div className="flex items-center gap-2 flex-wrap">
                  {/* Status badge + transition dropdown */}
                  <div className="relative">
                    <button
                      onClick={() => setShowStatusDropdown(!showStatusDropdown)}
                      disabled={statusChanging}
                      className={`text-[10px] px-2 py-1 rounded border font-semibold flex items-center gap-1 transition disabled:opacity-50 ${STATUS_COLORS[detail.status] || STATUS_COLORS.Draft}`}
                    >
                      {detail.status || "Draft"}
                      <ChevronDown className="w-2.5 h-2.5" />
                    </button>
                    {showStatusDropdown && availableTransitions.length > 0 && (
                      <div className="absolute top-full left-0 mt-1 bg-white border border-slate-200 rounded-lg shadow-lg py-1 z-30 min-w-[120px]">
                        {availableTransitions.map(t => (
                          <button
                            key={t.transitionCode}
                            onClick={() => handleStatusChange(t.transitionCode)}
                            className="w-full text-left px-3 py-1.5 text-[10px] font-semibold text-slate-700 hover:bg-blue-50 hover:text-blue-500 transition flex items-center gap-2"
                          >
                            <ArrowRightLeft className="w-2.5 h-2.5" />
                            → {t.toStatus}
                            {t.transitionName && <span className="text-slate-400 font-normal ml-1">({t.transitionName})</span>}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Edit button */}
                  <button
                    onClick={openEditForm}
                    className="text-[10px] bg-slate-50 hover:bg-slate-100 text-slate-600 border border-slate-200 rounded px-2 py-1 font-semibold transition flex items-center gap-1"
                  >
                    <Edit3 className="w-2.5 h-2.5" />编辑
                  </button>

                  {/* Delete button */}
                  <button
                    onClick={() => handleDelete(detail.id)}
                    className="text-[10px] bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 rounded px-2 py-1 font-semibold transition flex items-center gap-1"
                  >
                    <Trash2 className="w-2.5 h-2.5" />删除
                  </button>
                </div>
              </div>

              {/* Tab bar */}
              <div className="flex border-b border-slate-200 shrink-0 px-4 gap-0">
                {(["properties", "relations", "timeline"] as const).map(tab => (
                  <button
                    key={tab}
                    onClick={() => setActiveDetailTab(tab)}
                    className={`px-3 py-2 text-[11px] font-semibold border-b-2 transition ${
                      activeDetailTab === tab
                        ? "border-blue-500 text-blue-500"
                        : "border-transparent text-slate-500 hover:text-slate-700"
                    }`}
                  >
                    {tab === "properties" && <><FileText className="w-3 h-3 inline mr-1" />基本属性</>}
                    {tab === "relations" && <><Link2 className="w-3 h-3 inline mr-1" />关联对象 ({detailRelations.length})</>}
                    {tab === "timeline" && <><Clock className="w-3 h-3 inline mr-1" />时间线 ({detailTimeline.length})</>}
                  </button>
                ))}
              </div>

              {/* Tab content */}
              <div className="flex-1 overflow-y-auto p-4">
                {activeDetailTab === "properties" && (
                  <div className="space-y-3">
                    {schema.map(prop => {
                      const val = detail[prop.code];
                      return (
                        <div key={prop.code} className="group">
                          <label className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
                            {prop.name || prop.code}
                            {prop.required && <span className="text-red-400 ml-1">*</span>}
                          </label>
                          <div className="text-xs text-slate-700 mt-0.5 bg-slate-50 border border-slate-200 rounded px-2.5 py-1.5 font-mono break-all">
                            {val != null ? String(val) : <span className="text-slate-300 italic">未设置</span>}
                          </div>
                        </div>
                      );
                    })}
                    {schema.length === 0 && detail && Object.entries(detail)
                      .filter(([k]) => !["id", "entityCode", "status", "createdAt", "updatedAt", "relations", "timeline"].includes(k))
                      .map(([k, v]) => (
                        <div key={k}>
                          <label className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">{k}</label>
                          <div className="text-xs text-slate-700 mt-0.5 bg-slate-50 border border-slate-200 rounded px-2.5 py-1.5 font-mono break-all">
                            {v != null ? String(v) : <span className="text-slate-300 italic">—</span>}
                          </div>
                        </div>
                      ))}
                  </div>
                )}

                {activeDetailTab === "relations" && (
                  <div className="space-y-2">
                    {/* Gap 2: Add relationship button */}
                    <button
                      onClick={() => {
                        setRelFormData({ targetObjectId: "", targetEntityCode: entityCode, relationshipCode: "", relationshipType: "OneToMany" });
                        setShowRelationForm(true);
                      }}
                      className="w-full text-[10px] bg-blue-50 hover:bg-blue-100 text-blue-500 border border-blue-200 rounded-lg px-3 py-2 font-semibold transition flex items-center justify-center gap-1.5"
                    >
                      <Plus className="w-3 h-3" />添加关系
                    </button>
                    {detailRelations.length === 0 ? (
                      <div className="text-center py-12 text-xs text-slate-400">
                        <GitBranch className="w-6 h-6 mx-auto mb-2 text-slate-300" />
                        无关联对象
                      </div>
                    ) : (
                      detailRelations.map(rel => {
                        const isSource = rel.sourceObjectId === selectedId;
                        return (
                          <div
                            key={rel.id}
                            onClick={() => {
                              const targetCode = rel.targetEntityCode || entityCode;
                              const targetId = isSource ? rel.targetObjectId : rel.sourceObjectId;
                              if (targetCode === entityCode) {
                                setSelectedId(targetId);
                              } else {
                                navigateToRelated(targetCode, targetId);
                              }
                            }}
                            className="bg-white border border-slate-200 rounded-lg p-3 flex items-center gap-3 hover:border-blue-500 hover:bg-blue-50/30 transition cursor-pointer"
                          >
                            <div className={`p-1.5 rounded shrink-0 ${isSource ? "bg-green-50" : "bg-blue-50"}`}>
                              {isSource ? (
                                <ArrowRight className="w-3.5 h-3.5 text-green-500" />
                              ) : (
                                <ArrowRight className="w-3.5 h-3.5 text-blue-500 rotate-180" />
                              )}
                            </div>
                            <div className="flex-1 min-w-0">
                              <div className="text-[11px] font-semibold text-slate-700">{rel.relationCode}</div>
                              <div className="text-[10px] text-slate-400 font-mono mt-0.5">
                                {isSource ? "→" : "←"} {rel.targetEntityCode || "?"} · {rel.targetObjectId?.slice(0, 8)}
                              </div>
                            </div>
                            {rel.targetData && (
                              <div className="text-[10px] text-slate-500 bg-slate-50 rounded px-2 py-0.5 max-w-[120px] truncate">
                                {rel.targetData.name || rel.targetData.code || rel.targetObjectId?.slice(0, 8)}
                              </div>
                            )}
                            <ChevronRight className="w-3 h-3 text-slate-300 shrink-0" />
                          </div>
                        );
                      })
                    )}
                  </div>
                )}

                {activeDetailTab === "timeline" && (
                  <div className="space-y-2 relative before:absolute before:left-[11px] before:top-2 before:bottom-2 before:w-px before:bg-slate-200">
                    {timelineLoading && detailTimeline.length === 0 ? (
                      <div className="text-center py-12 text-xs text-slate-400">
                        <Loader2 className="w-6 h-6 mx-auto mb-2 text-slate-400 animate-spin" />
                        加载时间线...
                      </div>
                    ) : detailTimeline.length === 0 ? (
                      <div className="text-center py-12 text-xs text-slate-400">
                        <Clock className="w-6 h-6 mx-auto mb-2 text-slate-300" />
                        暂无操作记录
                      </div>
                    ) : (
                      <>
                        {detailTimeline.map(evt => (
                          <div key={evt.id} className="flex items-start gap-3 pl-6 relative">
                            <div className={`absolute left-[7px] top-1.5 w-[9px] h-[9px] rounded-full border-2 ${
                              evt.eventType === "created" ? "bg-green-100 border-green-400" :
                              evt.eventType === "deleted" ? "bg-red-100 border-red-400" :
                              evt.eventType === "status_changed" ? "bg-blue-100 border-blue-400" :
                              "bg-slate-100 border-slate-400"
                            }`} />
                            <div className="flex-1 min-w-0">
                              <div className="text-[11px] font-semibold text-slate-700">
                                {EVENT_LABELS[evt.eventType] || evt.eventType}
                                {evt.eventType === "status_changed" && evt.eventDetail && (
                                  <span className="text-[10px] font-normal text-slate-500 ml-1">
                                    {typeof evt.eventDetail === "object" ? `${evt.eventDetail.from} → ${evt.eventDetail.to}` : ""}
                                  </span>
                                )}
                              </div>
                              <div className="flex items-center gap-2 text-[10px] text-slate-400 mt-0.5">
                                <User className="w-2.5 h-2.5" />
                                <span>{evt.operator || "system"}</span>
                                <Calendar className="w-2.5 h-2.5 ml-1" />
                                <span>{evt.createdAt?.replace("T", " ").slice(0, 19)}</span>
                              </div>
                            </div>
                          </div>
                        ))}
                        {/* Gap 3: Load more button */}
                        {detailTimeline.length < timelineTotal && (
                          <div className="pl-6 pt-2">
                            <button
                              onClick={() => setTimelinePage(prev => prev + 1)}
                              disabled={timelineLoading}
                              className="w-full text-[10px] bg-slate-50 hover:bg-slate-100 text-slate-600 border border-slate-200 rounded-lg px-3 py-2 font-semibold transition flex items-center justify-center gap-1.5 disabled:opacity-50"
                            >
                              {timelineLoading ? (
                                <><Loader2 className="w-3 h-3 animate-spin" />加载中...</>
                              ) : (
                                <>加载更多 ({detailTimeline.length}/{timelineTotal})</>
                              )}
                            </button>
                          </div>
                        )}
                      </>
                    )}
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center text-slate-400 text-xs">
              <div className="text-center">
                <AlertCircle className="w-8 h-8 mx-auto mb-2 text-slate-300" />
                详情暂不可用
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── Create/Edit Form Modal ── */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={() => setShowForm(null)}>
          <div
            className="bg-white rounded-xl shadow-2xl w-full sm:w-[460px] max-h-[85vh] overflow-y-auto animate-in zoom-in-95 mx-4 sm:mx-auto"
            onClick={e => e.stopPropagation()}
          >
            <div className="p-4 border-b border-slate-200 flex items-center justify-between sticky top-0 bg-white z-10">
              <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                {showForm === "create" ? (
                  <><Plus className="w-4 h-4 text-blue-500" />新建 {entityCode}</>
                ) : (
                  <><Edit3 className="w-4 h-4 text-blue-500" />编辑 {entityCode}</>
                )}
              </h3>
              <button onClick={() => setShowForm(null)} className="hover:bg-slate-100 rounded p-1 transition">
                <X className="w-4 h-4 text-slate-500" />
              </button>
            </div>
            <div className="p-4 space-y-3">
              {schema.length === 0 ? (
                <>
                  <FormField label="name" required value={formData["name"] || ""} onChange={v => setFormData(prev => ({ ...prev, name: v }))} />
                  <FormField label="code" required value={formData["code"] || ""} onChange={v => setFormData(prev => ({ ...prev, code: v }))} />
                </>
              ) : (
                schema.map(prop => (
                  <div key={prop.code}>
                    <FormField
                      label={prop.code}
                      placeholder={prop.name}
                      required={prop.required}
                      value={formData[prop.code] || ""}
                      onChange={v => setFormData(prev => ({ ...prev, [prop.code]: v }))}
                    />
                  </div>
                ))
              )}
            </div>
            <div className="p-4 border-t border-slate-200 flex gap-2 justify-end sticky bottom-0 bg-white">
              <button
                onClick={() => setShowForm(null)}
                className="px-4 py-2 text-xs font-semibold text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-lg transition"
              >
                取消
              </button>
              <button
                onClick={showForm === "create" ? handleCreate : handleEdit}
                className="px-4 py-2 text-xs font-semibold text-white bg-blue-500 hover:bg-blue-600 rounded-lg transition"
              >
                {showForm === "create" ? "创建" : "保存"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Click-away handler for status dropdown */}
      {showStatusDropdown && (
        <div className="fixed inset-0 z-20" onClick={() => setShowStatusDropdown(false)} />
      )}

      {/* ── Gap 2: Add Relationship Modal ── */}
      {showRelationForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={() => setShowRelationForm(false)}>
          <div
            className="bg-white rounded-xl shadow-2xl w-full sm:w-[400px] animate-in zoom-in-95 mx-4 sm:mx-auto"
            onClick={e => e.stopPropagation()}
          >
            <div className="p-4 border-b border-slate-200 flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                <Link2 className="w-4 h-4 text-blue-500" />添加关系
              </h3>
              <button onClick={() => setShowRelationForm(false)} className="hover:bg-slate-100 rounded p-1 transition">
                <X className="w-4 h-4 text-slate-500" />
              </button>
            </div>
            <div className="p-4 space-y-3">
              <FormField label="目标对象 ID" required value={relFormData.targetObjectId} onChange={v => setRelFormData(prev => ({ ...prev, targetObjectId: v }))} />
              <div>
                <label className="block text-[10px] font-semibold text-slate-500 uppercase tracking-wider mb-1">目标实体</label>
                <select
                  value={relFormData.targetEntityCode}
                  onChange={e => setRelFormData(prev => ({ ...prev, targetEntityCode: e.target.value }))}
                  className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-700 outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition font-mono"
                >
                  {(() => {
                    const entities = entityList.length > 0
                      ? entityList.map(e => e.code)
                      : FALLBACK_ENTITIES;
                    return entities.map(ec => (
                      <option key={ec} value={ec}>{ec}</option>
                    ));
                  })()}
                </select>
              </div>
              <FormField label="关系编码" required placeholder="如 supplier_of" value={relFormData.relationshipCode} onChange={v => setRelFormData(prev => ({ ...prev, relationshipCode: v }))} />
              <div>
                <label className="block text-[10px] font-semibold text-slate-500 uppercase tracking-wider mb-1">关系类型</label>
                <select
                  value={relFormData.relationshipType}
                  onChange={e => setRelFormData(prev => ({ ...prev, relationshipType: e.target.value }))}
                  className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-700 outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition font-mono"
                >
                  <option value="OneToOne">OneToOne</option>
                  <option value="OneToMany">OneToMany</option>
                  <option value="ManyToMany">ManyToMany</option>
                </select>
              </div>
            </div>
            <div className="p-4 border-t border-slate-200 flex gap-2 justify-end">
              <button
                onClick={() => setShowRelationForm(false)}
                className="px-4 py-2 text-xs font-semibold text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-lg transition"
              >
                取消
              </button>
              <button
                onClick={handleCreateRelation}
                disabled={relCreating || !relFormData.targetObjectId || !relFormData.relationshipCode}
                className="px-4 py-2 text-xs font-semibold text-white bg-blue-500 hover:bg-blue-600 rounded-lg transition disabled:opacity-50 flex items-center gap-1.5"
              >
                {relCreating ? <><Loader2 className="w-3 h-3 animate-spin" />创建中...</> : "创建"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ---- Helper: form field ----
function FormField({ label, placeholder, required, value, onChange }: {
  label: string;
  placeholder?: string;
  required?: boolean;
  value: string;
  onChange: (val: string) => void;
}) {
  return (
    <div>
      <label className="block text-[10px] font-semibold text-slate-500 uppercase tracking-wider mb-1">
        {label}
        {required && <span className="text-red-400 ml-0.5">*</span>}
      </label>
      <input
        type="text"
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder || label}
        className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-700 outline-none placeholder-slate-400 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition font-mono"
      />
    </div>
  );
}
