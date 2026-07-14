/**
 * OntologyDesigner — 可视化本体设计器
 * 创建/编辑实体、属性、关系，图形化查看本体结构
 * @license Apache-2.0
 */
import React, { useState, useEffect, useCallback } from "react";
import {
  Globe, Plus, Trash2, Edit3, X, GitBranch,
  Link2, Database, Loader2,
  Box, List, ArrowRight, AlertCircle, CheckCircle
} from "lucide-react";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";
import {
  fetchOntologyEntities, fetchEntityProperties, fetchOntologyRelationships,
  fetchEntityRelationships, createOntologyEntity, updateOntologyEntity,
  deleteOntologyEntity, createEntityProperty, updateEntityProperty,
  deleteEntityProperty, createEntityRelationship, deleteEntityRelationship,
} from "../api";
import type { OntologyEntity, OntologyProperty, OntologyRelationship } from "../api";
import OntologyGraph from "../components/OntologyGraph";
import type { GraphNode, GraphEdge } from "../components/OntologyGraph";
import AddEntityModal from "./OntologyDesigner/AddEntityModal";
import {
  ONTOLOGY_ID, PROP_TYPES, FUNCTION_TYPES, REL_TYPES,
  entTypeLabel, buildGraphNodes, buildGraphLinks,
} from "./OntologyDesigner/helpers";

export default function OntologyDesigner() {
  const { t } = useLanguage();
  useTheme();
  const [entities, setEntities] = useState<OntologyEntity[]>([]);
  const [properties, setProperties] = useState<Record<string, OntologyProperty[]>>({});
  const [relationships, setRelationships] = useState<OntologyRelationship[]>([]);
  const [selectedEntityId, setSelectedEntityId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState<{ type: "ok" | "err"; msg: string } | null>(null);

  const [editEntity, setEditEntity] = useState<Partial<OntologyEntity> | null>(null);
  const [showAddEntity, setShowAddEntity] = useState(false);
  const [newEntity, setNewEntity] = useState({ code: "", name: "", description: "", entityType: "MASTER" });
  const [editingProp, setEditingProp] = useState<Partial<OntologyProperty> | null>(null);
  const [showAddProp, setShowAddProp] = useState(false);
  const [newProp, setNewProp] = useState({ code: "", name: "", propertyType: "STRING", functionType: "EXPRESSION", functionExpression: "", requiredFlag: 0, searchableFlag: 0 });
  const [showAddRel, setShowAddRel] = useState(false);
  const [newRel, setNewRel] = useState({ targetEntityId: "", code: "", name: "", relationshipType: "ONE_TO_MANY" });

  const selectedEntity = entities.find(e => e.id === selectedEntityId);
  const selectedProps = selectedEntityId ? (properties[selectedEntityId] || []) : [];
  const selectedRels = relationships.filter(r => r.sourceEntityId === selectedEntityId || r.targetEntityId === selectedEntityId);

  const showToast = (type: "ok" | "err", msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3000);
  };

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const ents = await fetchOntologyEntities(ONTOLOGY_ID);
      setEntities(ents);
      const propsMap: Record<string, OntologyProperty[]> = {};
      await Promise.all(ents.map(async e => {
        try { propsMap[e.id] = await fetchEntityProperties(e.id); } catch { propsMap[e.id] = []; }
      }));
      setProperties(propsMap);
      try {
        setRelationships(await fetchOntologyRelationships());
      } catch {
        const allRels: OntologyRelationship[] = [];
        await Promise.all(ents.map(async e => {
          try { allRels.push(...(await fetchEntityRelationships(e.id))); } catch {}
        }));
        setRelationships(allRels);
      }
    } catch (e: any) { showToast("err", e.message || "加载失败"); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { loadAll(); }, []);

  const handleCreateEntity = async () => {
    if (!newEntity.code || !newEntity.name) return;
    try {
      const created = await createOntologyEntity(ONTOLOGY_ID, newEntity);
      setEntities(prev => [...prev, created]);
      setProperties(prev => ({ ...prev, [created.id]: [] }));
      setShowAddEntity(false);
      setNewEntity({ code: "", name: "", description: "", entityType: "MASTER" });
      showToast("ok", `实体 "${created.name}" 已创建`);
    } catch (e: any) { showToast("err", e.message); }
  };

  const handleUpdateEntity = async () => {
    if (!editEntity?.id) return;
    try {
      const updated = await updateOntologyEntity(ONTOLOGY_ID, editEntity.id, editEntity);
      setEntities(prev => prev.map(e => e.id === updated.id ? updated : e));
      setEditEntity(null);
      showToast("ok", "实体已更新");
    } catch (e: any) { showToast("err", e.message); }
  };

  const handleDeleteEntity = async (id: string) => {
    if (!confirm(t("ontology_designer.confirm_delete"))) return;
    try {
      await deleteOntologyEntity(ONTOLOGY_ID, id);
      setEntities(prev => prev.filter(e => e.id !== id));
      if (selectedEntityId === id) setSelectedEntityId(null);
      showToast("ok", "实体已删除");
    } catch (e: any) { showToast("err", e.message); }
  };

  const handleCreateProp = async () => {
    if (!selectedEntityId || !newProp.code) return;
    try {
      const created = await createEntityProperty(selectedEntityId, newProp);
      setProperties(prev => ({ ...prev, [selectedEntityId]: [...(prev[selectedEntityId] || []), created] }));
      setShowAddProp(false);
      setNewProp({ code: "", name: "", propertyType: "STRING", functionType: "EXPRESSION", functionExpression: "", requiredFlag: 0, searchableFlag: 0 });
      showToast("ok", `属性 "${created.name || created.code}" 已添加`);
    } catch (e: any) { showToast("err", e.message); }
  };

  const handleUpdateProp = async () => {
    if (!editingProp?.id || !selectedEntityId) return;
    try {
      const updated = await updateEntityProperty(selectedEntityId, editingProp.id, editingProp);
      setProperties(prev => ({ ...prev, [selectedEntityId]: prev[selectedEntityId].map(p => p.id === updated.id ? updated : p) }));
      setEditingProp(null);
      showToast("ok", "属性已更新");
    } catch (e: any) { showToast("err", e.message); }
  };

  const handleDeleteProp = async (propId: string) => {
    if (!selectedEntityId) return;
    try {
      await deleteEntityProperty(selectedEntityId, propId);
      setProperties(prev => ({ ...prev, [selectedEntityId]: prev[selectedEntityId].filter(p => p.id !== propId) }));
    } catch (e: any) { showToast("err", e.message); }
  };

  const handleCreateRel = async () => {
    if (!selectedEntityId || !newRel.targetEntityId) return;
    try {
      const created = await createEntityRelationship(selectedEntityId, newRel);
      setRelationships(prev => [...prev, created]);
      setShowAddRel(false);
      setNewRel({ targetEntityId: "", code: "", name: "", relationshipType: "ONE_TO_MANY" });
      showToast("ok", `关系 "${created.code}" 已创建`);
    } catch (e: any) { showToast("err", e.message); }
  };

  const handleDeleteRel = async (relId: string) => {
    if (!selectedEntityId) return;
    try {
      await deleteEntityRelationship(selectedEntityId, relId);
      setRelationships(prev => prev.filter(r => r.id !== relId));
    } catch (e: any) { showToast("err", e.message); }
  };

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center bg-slate-50">
        <Loader2 className="w-6 h-6 text-slate-400 animate-spin" />
      </div>
    );
  }

  return (
    <div className="flex flex-col lg:flex-row h-full bg-slate-50 font-sans text-slate-800">
      {toast && (
        <div className={`absolute top-4 right-4 z-50 px-4 py-2.5 rounded-lg text-xs font-semibold shadow-lg flex items-center gap-2 animate-in slide-in-from-top-2 ${
          toast.type === "ok" ? "bg-green-50 border border-green-200 text-green-700" : "bg-red-50 border border-red-200 text-red-700"
        }`}>
          {toast.type === "ok" ? <CheckCircle className="w-3.5 h-3.5" /> : <AlertCircle className="w-3.5 h-3.5" />}
          {toast.msg}
        </div>
      )}

      {/* Left: Entity List */}
      <div className="w-full lg:w-64 shrink-0 border-r border-slate-200 bg-white flex flex-col max-h-48 lg:max-h-full">
        <div className="p-3 border-b border-slate-200 flex items-center justify-between">
          <h3 className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
            <Box className="w-3.5 h-3.5 text-indigo-500" /> 实体列表 ({entities.length})
          </h3>
          <button onClick={() => setShowAddEntity(true)} className="p-1 hover:bg-indigo-50 rounded text-indigo-600 transition" title="添加实体">
            <Plus className="w-3.5 h-3.5" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto">
          {entities.map(e => (
            <div key={e.id} onClick={() => setSelectedEntityId(e.id)}
              className={`px-3 py-2.5 border-b border-slate-100 cursor-pointer transition flex items-center gap-2.5 ${
                selectedEntityId === e.id ? "bg-indigo-50 border-l-2 border-l-indigo-500" : "hover:bg-slate-50"
              }`}>
              <Database className={`w-3.5 h-3.5 ${e.entityType === "MASTER" ? "text-amber-500" : "text-blue-500"} shrink-0`} />
              <div className="min-w-0 flex-1">
                <div className="text-xs font-semibold text-slate-800 truncate">{e.code}</div>
                <div className="text-[10px] text-slate-400 truncate">{e.name} · {entTypeLabel(e.entityType)}</div>
              </div>
              <span className="text-[9px] text-slate-300 font-mono">{properties[e.id]?.length || 0}p</span>
            </div>
          ))}
        </div>
      </div>

      {/* Center: Ontology Graph (interactive, draggable) */}
      <div className="flex-1 flex flex-col min-w-0 min-h-[300px] lg:min-h-0 p-2">
        <OntologyGraph
          nodes={entities.map((e, idx) => ({
            id: e.id,
            label: e.name || e.code,
            color: e.entityType === "MASTER" ? "border-amber-300 bg-amber-50" : "border-blue-300 bg-blue-50",
            x: 200 + (idx % 3) * 200,
            y: 150 + Math.floor(idx / 3) * 140,
            propertiesCount: properties[e.id]?.length || 0,
          }))}
          edges={relationships.map((r) => ({
            id: r.id,
            source: r.sourceEntityId,
            target: r.targetEntityId,
            label: r.code,
            cardinality: r.relationshipType,
          }))}
          selectedNodeId={selectedEntityId}
          onSelectNode={setSelectedEntityId}
        />
      </div>

      {/* Right: Detail Editor */}
      <div className="w-full lg:w-96 shrink-0 bg-white border-l border-slate-200 flex flex-col overflow-y-auto">
        {!selectedEntity ? (
          <div className="flex-1 flex items-center justify-center text-xs text-slate-400">
            <div className="text-center">
              <Globe className="w-8 h-8 mx-auto mb-2 text-slate-300" />
              选择左侧或画布中的实体
            </div>
          </div>
        ) : (
          <>
            {/* Entity Header */}
            <div className="p-4 border-b border-slate-200">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                  <Database className={`w-4 h-4 ${selectedEntity!.entityType === "MASTER" ? "text-amber-500" : "text-blue-500"}`} />
                  {selectedEntity!.code}
                </h3>
                <div className="flex gap-1">
                  <button onClick={() => setEditEntity({ ...selectedEntity! })} className="p-1.5 hover:bg-slate-100 rounded text-slate-500 transition" title="编辑实体">
                    <Edit3 className="w-3.5 h-3.5" />
                  </button>
                  <button onClick={() => handleDeleteEntity(selectedEntity!.id)} className="p-1.5 hover:bg-red-50 rounded text-red-400 transition" title="删除实体">
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              {editEntity && editEntity.id === selectedEntityId ? (
                <div className="space-y-2 bg-slate-50 rounded-lg p-3">
                  <input value={editEntity.code || ""} onChange={e => setEditEntity(prev => ({ ...prev, code: e.target.value }))}
                    className="w-full border border-slate-200 rounded px-2.5 py-1.5 text-xs" placeholder="编码" />
                  <input value={editEntity.name || ""} onChange={e => setEditEntity(prev => ({ ...prev, name: e.target.value }))}
                    className="w-full border border-slate-200 rounded px-2.5 py-1.5 text-xs" placeholder="名称" />
                  <textarea value={editEntity.description || ""} onChange={e => setEditEntity(prev => ({ ...prev, description: e.target.value }))}
                    className="w-full border border-slate-200 rounded px-2.5 py-1.5 text-xs" placeholder="描述" rows={2} />
                  <select value={editEntity.entityType || "MASTER"} onChange={e => setEditEntity(prev => ({ ...prev, entityType: e.target.value }))}
                    className="w-full border border-slate-200 rounded px-2.5 py-1.5 text-xs">
                    <option value="MASTER">主数据 (MASTER)</option>
                    <option value="TRANSACTION">事务 (TRANSACTION)</option>
                  </select>
                  <div className="flex gap-2">
                    <button onClick={handleUpdateEntity} className="flex-1 bg-indigo-500 text-white rounded px-3 py-1.5 text-xs font-semibold hover:bg-indigo-600 transition">保存</button>
                    <button onClick={() => setEditEntity(null)} className="px-3 py-1.5 bg-slate-200 text-slate-600 rounded text-xs hover:bg-slate-300 transition">取消</button>
                  </div>
                </div>
              ) : (
                <div className="text-[11px] text-slate-500 space-y-0.5">
                  <div><span className="text-slate-400">名称：</span>{selectedEntity!.name}</div>
                  <div><span className="text-slate-400">类型：</span>{entTypeLabel(selectedEntity!.entityType)}</div>
                  {selectedEntity!.description && <div><span className="text-slate-400">描述：</span>{selectedEntity!.description}</div>}
                </div>
              )}
            </div>

            {/* Properties Section */}
            <div className="p-4 border-b border-slate-200">
              <div className="flex items-center justify-between mb-3">
                <h4 className="text-xs font-bold text-slate-600 flex items-center gap-1.5">
                  <List className="w-3 h-3" /> 属性 ({selectedProps.length})
                </h4>
                <button onClick={() => setShowAddProp(true)} className="text-[10px] text-indigo-600 hover:text-indigo-800 font-semibold flex items-center gap-1">
                  <Plus className="w-3 h-3" /> 添加
                </button>
              </div>

              {showAddProp && (
                <div className="bg-indigo-50 rounded-lg p-3 mb-3 space-y-2">
                  <input value={newProp.code} onChange={e => setNewProp(p => ({...p, code: e.target.value}))}
                    className="w-full border border-indigo-200 rounded px-2.5 py-1.5 text-xs" placeholder="属性编码 (英文)" />
                  <input value={newProp.name} onChange={e => setNewProp(p => ({...p, name: e.target.value}))}
                    className="w-full border border-indigo-200 rounded px-2.5 py-1.5 text-xs" placeholder="属性名称" />
                  <select value={newProp.propertyType} onChange={e => setNewProp(p => ({...p, propertyType: e.target.value}))}
                    className="w-full border border-indigo-200 rounded px-2.5 py-1.5 text-xs">
                    {PROP_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                  {newProp.propertyType === "FUNCTION" && (
                    <>
                      <select value={newProp.functionType} onChange={e => setNewProp(p => ({...p, functionType: e.target.value}))}
                        className="w-full border border-indigo-200 rounded px-2.5 py-1.5 text-xs">
                        {FUNCTION_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                      </select>
                      <textarea value={newProp.functionExpression} onChange={e => setNewProp(p => ({...p, functionExpression: e.target.value}))}
                        className="w-full border border-indigo-200 rounded px-2.5 py-1.5 text-xs font-mono" placeholder="表达式，如: UPPER({name})" rows={2} />
                    </>
                  )}
                  <div className="flex items-center gap-4">
                    <label className="flex items-center gap-1 text-[10px] text-slate-600"><input type="checkbox" checked={newProp.requiredFlag === 1} onChange={e => setNewProp(p => ({...p, requiredFlag: e.target.checked ? 1 : 0}))} />必填</label>
                    <label className="flex items-center gap-1 text-[10px] text-slate-600"><input type="checkbox" checked={newProp.searchableFlag === 1} onChange={e => setNewProp(p => ({...p, searchableFlag: e.target.checked ? 1 : 0}))} />可搜索</label>
                  </div>
                  <div className="flex gap-2">
                    <button onClick={handleCreateProp} className="flex-1 bg-indigo-500 text-white rounded px-3 py-1.5 text-xs font-semibold">添加</button>
                    <button onClick={() => setShowAddProp(false)} className="px-3 py-1.5 bg-slate-200 rounded text-xs">取消</button>
                  </div>
                </div>
              )}

              <div className="space-y-1">
                {selectedProps.map(prop => (
                  <div key={prop.id} className="group flex items-center gap-2 px-2 py-1.5 rounded hover:bg-slate-50">
                    {editingProp?.id === prop.id ? (
                      <div className="flex-1 space-y-1">
                        <input value={editingProp.code || ""} onChange={e => setEditingProp(p => ({...p, code: e.target.value}))} className="w-full border rounded px-2 py-1 text-[10px]" />
                        <div className="flex gap-1">
                          <button onClick={handleUpdateProp} className="text-[10px] bg-green-500 text-white rounded px-2 py-0.5">保存</button>
                          <button onClick={() => setEditingProp(null)} className="text-[10px] bg-slate-200 rounded px-2 py-0.5">取消</button>
                        </div>
                      </div>
                    ) : (
                      <>
                        <span className="text-[10px] font-mono font-semibold text-slate-600 w-20 truncate">{prop.code}</span>
                        <span className="text-[10px] text-slate-400 flex-1 truncate">{prop.name || prop.code}</span>
                        <span className="text-[9px] font-mono text-slate-400 bg-slate-100 rounded px-1.5 py-0.5">{prop.propertyType === "FUNCTION" ? `${prop.propertyType}:${prop.functionType || "?"}` : prop.propertyType}</span>
                        {prop.requiredFlag === 1 && <span className="text-[9px] text-red-400 font-bold">*</span>}
                        <button onClick={() => setEditingProp({...prop})} className="opacity-0 group-hover:opacity-100 p-0.5 hover:bg-slate-200 rounded text-slate-400"><Edit3 className="w-3 h-3" /></button>
                        <button onClick={() => handleDeleteProp(prop.id)} className="opacity-0 group-hover:opacity-100 p-0.5 hover:bg-red-100 rounded text-red-400"><Trash2 className="w-3 h-3" /></button>
                      </>
                    )}
                  </div>
                ))}
                {selectedProps.length === 0 && <div className="text-[10px] text-slate-400 text-center py-4">暂无属性</div>}
              </div>
            </div>

            {/* Relationships Section */}
            <div className="p-4">
              <div className="flex items-center justify-between mb-3">
                <h4 className="text-xs font-bold text-slate-600 flex items-center gap-1.5">
                  <GitBranch className="w-3 h-3" /> 关系 ({selectedRels.length})
                </h4>
                <button onClick={() => setShowAddRel(true)} className="text-[10px] text-indigo-600 hover:text-indigo-800 font-semibold flex items-center gap-1">
                  <Plus className="w-3 h-3" /> 添加
                </button>
              </div>

              {showAddRel && (
                <div className="bg-indigo-50 rounded-lg p-3 mb-3 space-y-2">
                  <select value={newRel.targetEntityId} onChange={e => setNewRel(r => ({...r, targetEntityId: e.target.value}))}
                    className="w-full border border-indigo-200 rounded px-2.5 py-1.5 text-xs">
                    <option value="">选择目标实体</option>
                    {entities.filter(e => e.id !== selectedEntityId).map(e => <option key={e.id} value={e.id}>{e.code} ({e.name})</option>)}
                  </select>
                  <input value={newRel.code} onChange={e => setNewRel(r => ({...r, code: e.target.value}))}
                    className="w-full border border-indigo-200 rounded px-2.5 py-1.5 text-xs" placeholder="关系编码 (英文)" />
                  <input value={newRel.name} onChange={e => setNewRel(r => ({...r, name: e.target.value}))}
                    className="w-full border border-indigo-200 rounded px-2.5 py-1.5 text-xs" placeholder="关系名称" />
                  <select value={newRel.relationshipType} onChange={e => setNewRel(r => ({...r, relationshipType: e.target.value}))}
                    className="w-full border border-indigo-200 rounded px-2.5 py-1.5 text-xs">
                    {REL_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                  <div className="flex gap-2">
                    <button onClick={handleCreateRel} className="flex-1 bg-indigo-500 text-white rounded px-3 py-1.5 text-xs font-semibold">添加</button>
                    <button onClick={() => setShowAddRel(false)} className="px-3 py-1.5 bg-slate-200 rounded text-xs">取消</button>
                  </div>
                </div>
              )}

              <div className="space-y-1.5">
                {selectedRels.map(rel => {
                  const isSource = rel.sourceEntityId === selectedEntityId;
                  const otherId = isSource ? rel.targetEntityId : rel.sourceEntityId;
                  const otherEntity = entities.find(e => e.id === otherId);
                  return (
                    <div key={rel.id} className="group flex items-center gap-2 px-2 py-1.5 rounded hover:bg-slate-50">
                      <div className={`p-1 rounded ${isSource ? "bg-green-100" : "bg-blue-100"}`}>
                        {isSource ? <ArrowRight className="w-2.5 h-2.5 text-green-600" /> : <ArrowRight className="w-2.5 h-2.5 text-blue-600 rotate-180" />}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="text-[10px] font-semibold text-slate-700">{rel.code}</div>
                        <div className="text-[9px] text-slate-400">
                          {isSource ? "→" : "←"} {otherEntity?.code || otherId?.slice(0,8)}
                          <span className="ml-1 font-mono text-slate-300">[{rel.relationshipType}]</span>
                        </div>
                      </div>
                      <button onClick={() => handleDeleteRel(rel.id)} className="opacity-0 group-hover:opacity-100 p-0.5 hover:bg-red-100 rounded text-red-400">
                        <Trash2 className="w-3 h-3" />
                      </button>
                    </div>
                  );
                })}
                {selectedRels.length === 0 && <div className="text-[10px] text-slate-400 text-center py-4">暂无关系</div>}
              </div>
            </div>
          </>
        )}
      </div>

      {/* Add Entity Modal */}
      {showAddEntity && (
        <AddEntityModal newEntity={newEntity} setNewEntity={setNewEntity}
          onClose={() => setShowAddEntity(false)} onCreate={handleCreateEntity} />
      )}
    </div>
  );
}
