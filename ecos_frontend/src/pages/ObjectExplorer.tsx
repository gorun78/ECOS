/**
 * ObjectExplorer — Object Runtime 全链路浏览器
 * 支持浏览/新建/编辑/状态流转/关系图/时间线
 *
 * H6-T4 拆分：JSX 区块 → ./ObjectExplorer/ObjectExplorer* 展示组件，
 * 表格列构造 → ./ObjectExplorer/buildObjectColumns；状态与 useEffect 顺序、依赖数组保持原样。
 *
 * @license Apache-2.0
 */

import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { Box, AlertCircle, X } from "lucide-react";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";
import {
  fetchObjects, searchObjects, fetchObjectDetail, fetchObjectSchema,
  createObject, updateObject, deleteObject,
  ObjectData, SchemaProperty,
  fetchAvailableTransitions, executeTransition,
  createObjectRelationship, fetchObjectTimeline,
  TimelineEvent,
} from "../api";
// T2: fetchEntityList/EntityListItem 由 api.ts 收敛到 services/ontologyApi.ts
import { fetchEntityList, EntityListItem } from "../services/ontologyApi";
import MobileDataTable, { MobileCardConfig } from "../components/common/MobileDataTable";
import DataTable, { ColumnConfig } from "../components/common/DataTable";
import { PAGE_SIZE, type Relation } from "./ObjectExplorer/helpers";
import { buildObjectColumns } from "./ObjectExplorer/buildObjectColumns";
import { ObjectExplorerTopBar } from "./ObjectExplorer/ObjectExplorerTopBar";
import { ObjectExplorerDetailPanel } from "./ObjectExplorer/ObjectExplorerDetailPanel";
import { ObjectExplorerFormModal } from "./ObjectExplorer/ObjectExplorerFormModal";
import { ObjectExplorerRelationModal } from "./ObjectExplorer/ObjectExplorerRelationModal";

// ---- Main component ----
export default function ObjectExplorer() {
  const { t } = useLanguage();
  const { styles } = useTheme();
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

  // ---- Build table columns from schema ----
  const tableColumns: ColumnConfig<ObjectData>[] = useMemo(() => buildObjectColumns(schema, styles), [schema, styles]);

  // ---- Mobile card config（移动端卡片态：ObjectExplorer 表格页）----
  // 卡片头：主键 id + 1 个最高优先级字段（status，呈现 Dup/Active 状态颜色）
  // 详情折叠区：schema 动态列（名称/编码等）
  // 操作列：保留在卡片底部 DT 内（onRowClick 触发 detail 打开）
  const mobileConfig: MobileCardConfig<ObjectData> = useMemo(() => {
    const detail = tableColumns.map((c) => c.key).filter((k) => k !== "id" && k !== "status");
    return {
      headerKeys: ["id", "status"],
      detailKeys: detail,
    };
  }, [tableColumns]);

  return (
    <div className={`flex flex-col h-full ${styles.appBg} font-sans ${styles.cardText}`}>
      {/* Error toast */}
      {error && (
        <div className={`absolute top-4 right-4 z-50 ${styles.dangerBg} border ${styles.dangerBorder} ${styles.dangerText} rounded-lg px-4 py-3 text-xs flex items-center gap-2 shadow-lg max-w-md animate-in slide-in-from-top-2`}>
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span className="flex-1">{error}</span>
          <button type="button" onClick={() => setError(null)} className="hover:opacity-70 rounded p-0.5"><X className="w-3 h-3" /></button>
        </div>
      )}

      {/* ── Top Bar ── */}
      <ObjectExplorerTopBar
        entityCode={entityCode}
        entityList={entityList}
        handleEntityChange={handleEntityChange}
        searchQ={searchQ}
        setSearchQ={setSearchQ}
        handleSearch={handleSearch}
        loadObjects={loadObjects}
        currentPage={currentPage}
        setShowForm={setShowForm}
        setFormData={setFormData}
      />

      {/* ── Main Area: Table + Detail Split ── */}
      <div className="flex-1 flex flex-col lg:flex-row min-h-0">
        {/* Left: Data Table */}
        <div className={`flex-1 min-w-0 flex flex-col border-r ${styles.cardBorder} ${styles.cardBg}`}>
          <MobileDataTable<ObjectData>
            mobileConfig={mobileConfig}
            columns={tableColumns}
            data={objects}
            rowKey="id"
            loading={loading}
            emptyTitle={`暂无${entityCode}对象`}
            emptyDescription="点击「新建」按钮创建第一个对象"
            emptyIcon={<Box className={`w-8 h-8 ${styles.cardTextMuted}`} />}
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
        <ObjectExplorerDetailPanel
          entityCode={entityCode}
          selectedId={selectedId}
          detail={detail}
          detailLoading={detailLoading}
          schema={schema}
          activeDetailTab={activeDetailTab}
          setActiveDetailTab={setActiveDetailTab}
          detailRelations={detailRelations}
          detailTimeline={detailTimeline}
          timelineLoading={timelineLoading}
          timelineTotal={timelineTotal}
          setTimelinePage={setTimelinePage}
          availableTransitions={availableTransitions}
          showStatusDropdown={showStatusDropdown}
          setShowStatusDropdown={setShowStatusDropdown}
          statusChanging={statusChanging}
          handleStatusChange={handleStatusChange}
          openEditForm={openEditForm}
          handleDelete={handleDelete}
          setRelFormData={setRelFormData}
          setShowRelationForm={setShowRelationForm}
          setSelectedId={setSelectedId}
          navigateToRelated={navigateToRelated}
        />
      </div>

      {/* ── Create/Edit Form Modal ── */}
      {showForm && (
        <ObjectExplorerFormModal
          showForm={showForm}
          entityCode={entityCode}
          schema={schema}
          formData={formData}
          setFormData={setFormData}
          setShowForm={setShowForm}
          handleCreate={handleCreate}
          handleEdit={handleEdit}
        />
      )}

      {/* Click-away handler for status dropdown */}
      {showStatusDropdown && (
        <div className="fixed inset-0 z-20" onClick={() => setShowStatusDropdown(false)} />
      )}

      {/* ── Gap 2: Add Relationship Modal ── */}
      {showRelationForm && (
        <ObjectExplorerRelationModal
          entityList={entityList}
          relFormData={relFormData}
          setRelFormData={setRelFormData}
          setShowRelationForm={setShowRelationForm}
          relCreating={relCreating}
          handleCreateRelation={handleCreateRelation}
        />
      )}
    </div>
  );
}
