/**
 * DictManager — 数据字典全生命周期管理
 * 管理数据库表定义、字段元数据。左右双栏布局：
 *   左侧：表列表（搜索/筛选/新建）
 *   右侧：表详情 + 字段列表（CRUD + 状态流转）
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  Search, Plus, Edit3, Trash2, CheckCircle2,
  AlertCircle, X, Database, RotateCw,
  Columns3, Hash, Type, ToggleLeft, Key, FileText,
  GripVertical, ArrowUp, ArrowDown, ChevronDown,
  Tag, HardDrive, User, Calendar, Layers,
  BookOpen, ListTree, FolderTree,
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import {
  getDictTables, getDictTable, createDictTable,
  updateDictTable, deleteDictTable,
  createDictColumn, updateDictColumn, deleteDictColumn,
  type DictTable, type DictColumn,
} from "../services/dict";
import {
  listDictTypes, getDictItems, createDictItem,
  updateDictItem, deleteDictItem, fetchDictSubsystems,
  type DictType, type DictItem,
} from "../api";

// ── status config ──
const STATUS_OPTIONS = [
  { value: "", label: "全部" },
  { value: "DRAFT", label: "Draft" },
  { value: "PUBLISHED", label: "Published" },
  { value: "DEPRECATED", label: "Deprecated" },
];

const STATUS_META: Record<string, { label: string; bg: string; text: string }> = {
  DRAFT:      { label: "草稿",     bg: "bg-slate-100",  text: "text-slate-600" },
  PUBLISHED:  { label: "已发布",   bg: "bg-green-50",   text: "text-green-600" },
  DEPRECATED: { label: "已废弃",   bg: "bg-amber-50",   text: "text-amber-600" },
};

const SOURCE_OPTIONS = ["MySQL", "PostgreSQL", "Oracle", "Hive", "ClickHouse", "其他"];
const SQL_TYPES = [
  "VARCHAR", "CHAR", "TEXT", "LONGTEXT",
  "INT", "BIGINT", "SMALLINT", "TINYINT",
  "DECIMAL", "FLOAT", "DOUBLE",
  "DATE", "DATETIME", "TIMESTAMP",
  "BOOLEAN", "JSON", "BLOB",
];

const COLUMN_TYPE_CATEGORIES: Record<string, string[]> = {
  "字符串": ["VARCHAR", "CHAR", "TEXT", "LONGTEXT"],
  "数值": ["INT", "BIGINT", "SMALLINT", "TINYINT", "DECIMAL", "FLOAT", "DOUBLE"],
  "日期时间": ["DATE", "DATETIME", "TIMESTAMP"],
  "其他": ["BOOLEAN", "JSON", "BLOB"],
};

// ── Column Form State ──
interface ColumnFormState {
  id?: string;           // undefined = new, string = edit
  name: string;
  type: string;
  length: string;
  precision: string;
  scale: string;
  nullable: boolean;
  primaryKey: boolean;
  defaultValue: string;
  description: string;
}

const emptyColumnForm = (): ColumnFormState => ({
  name: "",
  type: "VARCHAR",
  length: "",
  precision: "",
  scale: "",
  nullable: true,
  primaryKey: false,
  defaultValue: "",
  description: "",
});

// ── Toast ──
const Toast: React.FC<{
  toast: { type: "success" | "error"; msg: string };
  onClose: () => void;
}> = ({ toast, onClose }) => (
  <div
    className={`fixed top-6 right-6 z-50 flex items-center gap-2.5 px-4 py-3
      rounded-lg shadow-lg text-sm font-medium transition-all
      ${toast.type === "success"
        ? "bg-emerald-50 dark:bg-emerald-950 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200"
        : "bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 text-red-800 dark:text-red-200"
      }`}
  >
    {toast.type === "success"
      ? <CheckCircle2 className="w-4 h-4 shrink-0" />
      : <AlertCircle className="w-4 h-4 shrink-0" />
    }
    <span>{toast.msg}</span>
    <button onClick={onClose} className="ml-2 opacity-60 hover:opacity-100">
      <X className="w-3.5 h-3.5" />
    </button>
  </div>
);

// ── Delete Confirm Dialog ──
const DeleteConfirm: React.FC<{
  targetName: string;
  targetType: "table" | "column";
  onConfirm: () => void;
  onCancel: () => void;
}> = ({ targetName, targetType, onConfirm, onCancel }) => (
  <div className="fixed inset-0 z-40 flex items-center justify-center">
    <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
    <div
      className="relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6"
      style={{
        background: "var(--content-bg, #fff)",
        border: "1px solid var(--border-color, #e0e0e0)",
      }}
    >
      <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 8, color: "var(--text-primary, #222)" }}>
        确认删除
      </h3>
      <p style={{ fontSize: 14, color: "var(--text-muted, #888)", marginBottom: 20 }}>
        确定要删除{targetType === "table" ? "数据表" : "字段"}「{targetName}」吗？此操作不可撤销。
      </p>
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
        <button
          onClick={onCancel}
          style={{
            padding: "6px 16px", borderRadius: 6, border: "1px solid var(--border-color, #d0d0d0)",
            background: "transparent", cursor: "pointer", fontSize: 13,
            color: "var(--text-primary, #333)",
          }}
        >
          取消
        </button>
        <button
          onClick={onConfirm}
          style={{
            padding: "6px 16px", borderRadius: 6, border: "none",
            background: "#c62828", color: "#fff", cursor: "pointer", fontSize: 13, fontWeight: 600,
          }}
        >
          删除
        </button>
      </div>
    </div>
  </div>
);

// ── Column Type Selector (categorized) ──
const ColumnTypeSelect: React.FC<{
  value: string;
  onChange: (v: string) => void;
  disabled?: boolean;
}> = ({ value, onChange, disabled }) => {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        disabled={disabled}
        className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700
          flex items-center justify-between outline-none disabled:opacity-50"
        onClick={() => setOpen(!open)}
      >
        <span className="font-mono">{value}</span>
        <ChevronDown size={14} className="text-slate-400" />
      </button>
      {open && (
        <div className="absolute z-30 left-0 right-0 mt-1 bg-white border border-slate-200 rounded-lg shadow-lg max-h-64 overflow-y-auto">
          {Object.entries(COLUMN_TYPE_CATEGORIES).map(([cat, types]) => (
            <div key={cat}>
              <div className="px-3 py-1.5 text-[10px] font-semibold text-slate-400 uppercase bg-slate-50">
                {cat}
              </div>
              {types.map(t => (
                <div
                  key={t}
                  className={`px-3 py-1.5 text-xs font-mono cursor-pointer hover:bg-indigo-50 ${
                    t === value ? "bg-indigo-100 text-indigo-700 font-semibold" : "text-slate-600"
                  }`}
                  onClick={() => { onChange(t); setOpen(false); }}
                >
                  {t}
                </div>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

// ═══════════════════════════════════════════
// Main Component
// ═══════════════════════════════════════════
export default function DictManager() {
  const { t } = useLanguage();
  // ── data state ──
  const [tables, setTables] = useState<DictTable[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [selectedTable, setSelectedTable] = useState<DictTable | null>(null);

  // ── filter state ──
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  // ── table form state ──
  const [tableMode, setTableMode] = useState<"view" | "create" | "edit">("view");
  const [tableFormName, setTableFormName] = useState("");
  const [tableFormNameZh, setTableFormNameZh] = useState("");
  const [tableFormSchema, setTableFormSchema] = useState("");
  const [tableFormSource, setTableFormSource] = useState("");
  const [tableFormDesc, setTableFormDesc] = useState("");
  const [tableFormTags, setTableFormTags] = useState("");

  // ── column form state ──
  const [colForm, setColForm] = useState<ColumnFormState>(emptyColumnForm());
  const [colFormOpen, setColFormOpen] = useState(false);   // inline add/edit row

  // ── toast ──
  const [toast, setToast] = useState<{ type: "success" | "error"; msg: string } | null>(null);

  // ── delete confirm ──
  const [deleteTarget, setDeleteTarget] = useState<{
    type: "table" | "column";
    id: string;
    name: string;
  } | null>(null);

  // ── debounce ref ──
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ── expanded column card ──
  const [expandedColId, setExpandedColId] = useState<string | null>(null);

  // ── view mode: "table" (数据表管理) or "dict" (字典项管理) ──
  const [viewMode, setViewMode] = useState<"table" | "dict">("dict");

  // ── dict mode state ──
  const [dictTypes, setDictTypes] = useState<DictType[]>([]);
  const [dictLoading, setDictLoading] = useState(false);
  const [selectedDictType, setSelectedDictType] = useState<string | null>(null);
  const [dictItems, setDictItems] = useState<DictItem[]>([]);
  const [dictSearch, setDictSearch] = useState("");

  // ── dict item form ──
  const [dictItemFormOpen, setDictItemFormOpen] = useState(false);
  const [dictItemForm, setDictItemForm] = useState<{
    editCode?: string;
    dictCode: string;
    extValue: string;
    dictLabel: string;
    status: string;
    sortOrder: string;
    description: string;
  }>({ dictCode: "", extValue: "", dictLabel: "", status: "active", sortOrder: "", description: "" });

  const showToast = useCallback((type: "success" | "error", msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3000);
  }, []);

  // ── Load tables ──
  const loadTables = useCallback(async (status?: string) => {
    setLoading(true);
    try {
      const result = await getDictTables(status ? { status } : undefined);
      setTables(result.items);
    } catch (e: any) {
      showToast("error", `加载失败: ${e.message}`);
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { loadTables(); }, [loadTables]);

  // ── Load full table with columns ──
  const loadTableDetail = useCallback(async (id: string) => {
    try {
      const t = await getDictTable(id);
      setSelectedTable(t);
      // also update in list
      setTables(prev => prev.map(x => x.id === id ? { ...x, columns: t.columns } : x));
    } catch (e: any) {
      showToast("error", `加载表详情失败: ${e.message}`);
    }
  }, [showToast]);

  // ── Status filter → refetch ──
  const handleStatusFilter = (v: string) => {
    setStatusFilter(v);
    setSelectedTable(null);
    setTableMode("view");
    loadTables(v || undefined);
  };

  // ── Search with debounce ──
  const handleSearchChange = (v: string) => {
    setSearch(v);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => { /* filtered below */ }, 300);
  };

  // ── Derived: filtered tables ──
  const filteredTables = tables.filter(t =>
    !search ||
    t.name.toLowerCase().includes(search.toLowerCase()) ||
    (t.nameZh && t.nameZh.includes(search)) ||
    (t.code && t.code.toLowerCase().includes(search.toLowerCase()))
  );

  // ── Select table ──
  const selectTable = async (t: DictTable) => {
    setTableMode("edit");
    setTableFormName(t.name);
    setTableFormNameZh(t.nameZh ?? "");
    setTableFormSchema(t.schema ?? "");
    setTableFormSource(t.source ?? "");
    setTableFormDesc(t.description ?? "");
    setTableFormTags((t.tags ?? []).join(", "));
    await loadTableDetail(t.id);
  };

  // ── Create new table ──
  const handleCreate = () => {
    setSelectedTable(null);
    setTableMode("create");
    setTableFormName("");
    setTableFormNameZh("");
    setTableFormSchema("");
    setTableFormSource("");
    setTableFormDesc("");
    setTableFormTags("");
  };

  // ── Cancel form ──
  const handleCancel = () => {
    setTableMode("view");
    setSelectedTable(null);
  };

  // ── Save table (create or update) ──
  const handleSaveTable = async () => {
    if (!tableFormName.trim()) {
      showToast("error", "表名不能为空");
      return;
    }
    setSaving(true);
    try {
      const tags = tableFormTags
        .split(",")
        .map(s => s.trim())
        .filter(Boolean);

      if (tableMode === "create") {
        const res = await createDictTable({
          name: tableFormName.trim(),
          nameZh: tableFormNameZh.trim(),
          schema: tableFormSchema.trim(),
          description: tableFormDesc.trim(),
          source: tableFormSource || undefined,
        });
        showToast("success", "数据表创建成功");
        await loadTables(statusFilter || undefined);
        // load new table detail
        await loadTableDetail(res.id);
        setTableMode("edit");
      } else if (selectedTable) {
        await updateDictTable(selectedTable.id, {
          name: tableFormName.trim(),
          nameZh: tableFormNameZh.trim(),
          description: tableFormDesc.trim(),
          tags: tags.length > 0 ? tags : undefined,
        });
        showToast("success", "数据表更新成功");
        await loadTables(statusFilter || undefined);
        await loadTableDetail(selectedTable.id);
      }
    } catch (e: any) {
      showToast("error", `保存失败: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  // ── Status transition ──
  const handleTransition = async (newStatus: string) => {
    if (!selectedTable) return;
    setSaving(true);
    try {
      await updateDictTable(selectedTable.id, { status: newStatus });
      showToast("success", `状态已变更为「${STATUS_META[newStatus]?.label ?? newStatus}」`);
      await loadTables(statusFilter || undefined);
      await loadTableDetail(selectedTable.id);
    } catch (e: any) {
      showToast("error", `状态变更失败: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  // ── Delete table ──
  const handleDeleteTable = async () => {
    if (!deleteTarget || deleteTarget.type !== "table") return;
    const name = deleteTarget.name;
    setSaving(true);
    try {
      await deleteDictTable(deleteTarget.id);
      showToast("success", `「${name}」已删除`);
      setDeleteTarget(null);
      if (selectedTable?.id === deleteTarget.id) {
        setSelectedTable(null);
        setTableMode("view");
      }
      await loadTables(statusFilter || undefined);
    } catch (e: any) {
      showToast("error", `删除失败: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  // ── Column CRUD ──

  const openNewColumn = () => {
    setColForm(emptyColumnForm());
    setColFormOpen(true);
    setExpandedColId(null);
  };

  const openEditColumn = (col: DictColumn) => {
    setColForm({
      id: col.id,
      name: col.name,
      type: col.type,
      length: col.length?.toString() ?? "",
      precision: col.precision?.toString() ?? "",
      scale: col.scale?.toString() ?? "",
      nullable: col.nullable,
      primaryKey: col.primaryKey,
      defaultValue: col.defaultValue ?? "",
      description: col.description ?? "",
    });
    setColFormOpen(true);
    setExpandedColId(null);
  };

  const cancelColumnForm = () => {
    setColFormOpen(false);
    setColForm(emptyColumnForm());
  };

  const handleSaveColumn = async () => {
    if (!selectedTable) return;
    if (!colForm.name.trim()) {
      showToast("error", "字段名不能为空");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        name: colForm.name.trim(),
        type: colForm.type,
        length: colForm.length ? parseInt(colForm.length, 10) : undefined,
        precision: colForm.precision ? parseInt(colForm.precision, 10) : undefined,
        scale: colForm.scale ? parseInt(colForm.scale, 10) : undefined,
        nullable: colForm.nullable,
        primaryKey: colForm.primaryKey,
        defaultValue: colForm.defaultValue || undefined,
        description: colForm.description.trim(),
      };

      if (colForm.id) {
        await updateDictColumn(selectedTable.id, colForm.id, payload);
        showToast("success", "字段更新成功");
      } else {
        await createDictColumn(selectedTable.id, payload);
        showToast("success", "字段添加成功");
      }
      await loadTableDetail(selectedTable.id);
      cancelColumnForm();
    } catch (e: any) {
      showToast("error", `保存字段失败: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteColumn = async () => {
    if (!selectedTable || !deleteTarget || deleteTarget.type !== "column") return;
    setSaving(true);
    try {
      await deleteDictColumn(selectedTable.id, deleteTarget.id);
      showToast("success", `字段「${deleteTarget.name}」已删除`);
      setDeleteTarget(null);
      await loadTableDetail(selectedTable.id);
    } catch (e: any) {
      showToast("error", `删除字段失败: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  // ── Dict mode handlers ──

  // ── Dict mode: subsystem grouping state ──
  const [subsystemGroups, setSubsystemGroups] = useState<Record<string, DictType[]>>({});
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set(["G1","G2","G3","G4","G5"]));

  const G1_G5_LABELS: Record<string, { zh: string; color: string; border: string; bg: string }> = {
    G1: { zh: "G1 数据集成", color: "text-blue-700", border: "border-blue-300", bg: "bg-blue-50" },
    G2: { zh: "G2 数据治理", color: "text-emerald-700", border: "border-emerald-300", bg: "bg-emerald-50" },
    G3: { zh: "G3 数据资产", color: "text-purple-700", border: "border-purple-300", bg: "bg-purple-50" },
    G4: { zh: "G4 AI智能体", color: "text-amber-700", border: "border-amber-300", bg: "bg-amber-50" },
    G5: { zh: "G5 系统管理", color: "text-slate-700", border: "border-slate-300", bg: "bg-slate-50" },
  };

  const toggleGroup = (g: string) => {
    setExpandedGroups(prev => {
      const next = new Set(prev);
      next.has(g) ? next.delete(g) : next.add(g);
      return next;
    });
  };

  const loadDictTypes = useCallback(async () => {
    setDictLoading(true);
    try {
      const groups = await fetchDictSubsystems();
      setSubsystemGroups(groups);
      // Also flatten for backward compat
      const all: DictType[] = [];
      Object.values(groups).forEach(arr => all.push(...arr));
      setDictTypes(all);
    } catch {
      // Fallback to flat list if /subsystems not available
      try {
        const types = await listDictTypes();
        setDictTypes(types);
      } catch (e: any) {
        showToast("error", `加载字典类型失败: ${e.message}`);
      }
    } finally {
      setDictLoading(false);
    }
  }, [showToast]);

  const loadDictItems = useCallback(async (dictType: string) => {
    setDictLoading(true);
    try {
      const items = await getDictItems(dictType);
      setDictItems(items);
    } catch (e: any) {
      showToast("error", `加载字典项失败: ${e.message}`);
      setDictItems([]);
    } finally {
      setDictLoading(false);
    }
  }, [showToast]);

  const handleSelectDictType = (dictType: string) => {
    setSelectedDictType(dictType);
    setDictItemFormOpen(false);
    loadDictItems(dictType);
  };

  const handleSwitchMode = (mode: "table" | "dict") => {
    setViewMode(mode);
    if (mode === "dict" && dictTypes.length === 0) {
      loadDictTypes();
    }
  };

  const openNewDictItem = () => {
    setDictItemForm({
      editCode: undefined,
      dictCode: "",
      extValue: "",
      dictLabel: "",
      status: "active",
      sortOrder: "",
      description: "",
    });
    setDictItemFormOpen(true);
  };

  const openEditDictItem = (item: DictItem) => {
    setDictItemForm({
      editCode: item.dictCode,
      dictCode: item.dictCode,
      extValue: item.extValue ?? "",
      dictLabel: item.dictLabel,
      status: item.status,
      sortOrder: item.sortOrder?.toString() ?? "",
      description: "",
    });
    setDictItemFormOpen(true);
  };

  const cancelDictItemForm = () => {
    setDictItemFormOpen(false);
  };

  const handleSaveDictItem = async () => {
    if (!selectedDictType) return;
    if (!dictItemForm.dictCode.trim() || !dictItemForm.dictLabel.trim()) {
      showToast("error", "编码和标签不能为空");
      return;
    }
    setSaving(true);
    try {
      if (dictItemForm.editCode) {
        await updateDictItem(selectedDictType, dictItemForm.editCode, {
          extValue: dictItemForm.extValue.trim() || undefined,
          dictLabel: dictItemForm.dictLabel.trim(),
          status: dictItemForm.status,
          sortOrder: dictItemForm.sortOrder ? parseInt(dictItemForm.sortOrder, 10) : undefined,
        });
        showToast("success", "字典项更新成功");
      } else {
        await createDictItem({
          dictType: selectedDictType,
          dictCode: dictItemForm.dictCode.trim(),
          extValue: dictItemForm.extValue.trim() || undefined,
          dictLabel: dictItemForm.dictLabel.trim(),
          status: dictItemForm.status,
          sortOrder: dictItemForm.sortOrder ? parseInt(dictItemForm.sortOrder, 10) : undefined,
        });
        showToast("success", "字典项创建成功");
      }
      await loadDictItems(selectedDictType);
      cancelDictItemForm();
    } catch (e: any) {
      showToast("error", `保存字典项失败: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteDictItem = async () => {
    if (!selectedDictType || !deleteTarget || deleteTarget.type !== "dictItem") return;
    setSaving(true);
    try {
      await deleteDictItem(selectedDictType, deleteTarget.id);
      showToast("success", `字典项「${deleteTarget.name}」已删除`);
      setDeleteTarget(null);
      await loadDictItems(selectedDictType);
    } catch (e: any) {
      showToast("error", `删除字典项失败: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  const filteredDictItems = dictItems.filter(item =>
    !dictSearch ||
    item.dictLabel.toLowerCase().includes(dictSearch.toLowerCase()) ||
    item.dictCode.toLowerCase().includes(dictSearch.toLowerCase()) ||
    item.extValue?.toLowerCase().includes(dictSearch.toLowerCase())
  );

  // ── Transition buttons ──
  const transitions = selectedTable
    ? (selectedTable.status === "DRAFT"
        ? [{ label: "发布", status: "PUBLISHED", variant: "primary" as const }]
        : selectedTable.status === "PUBLISHED"
        ? [{ label: "废弃", status: "DEPRECATED", variant: "danger" as const }]
        : selectedTable.status === "DEPRECATED"
        ? [{ label: "重新启用", status: "DRAFT", variant: "secondary" as const }]
        : [])
    : [];

  // ── Counts ──
  const counts = {
    all: tables.length,
    draft: tables.filter(t => t.status === "DRAFT").length,
    published: tables.filter(t => t.status === "PUBLISHED").length,
    deprecated: tables.filter(t => t.status === "DEPRECATED").length,
  };

  // ── Column type badge color ──
  const typeBadge = (t: string) => {
    if (["VARCHAR", "CHAR", "TEXT", "LONGTEXT"].includes(t))
      return "bg-blue-50 text-blue-600";
    if (["INT", "BIGINT", "SMALLINT", "TINYINT", "DECIMAL", "FLOAT", "DOUBLE"].includes(t))
      return "bg-emerald-50 text-emerald-600";
    if (["DATE", "DATETIME", "TIMESTAMP"].includes(t))
      return "bg-purple-50 text-purple-600";
    if (t === "BOOLEAN")
      return "bg-amber-50 text-amber-600";
    return "bg-slate-100 text-slate-600";
  };

  // ═══════════════════════════════════════════
  // Render
  // ═══════════════════════════════════════════
  return (
    <div className="flex-1 bg-[#F8FAFC] flex h-full overflow-hidden font-sans">
      {/* ── Toast ── */}
      {toast && <Toast toast={toast} onClose={() => setToast(null)} />}

      {/* ── Delete Confirm ── */}
      {deleteTarget && (
        <DeleteConfirm
          targetName={deleteTarget.name}
          targetType={deleteTarget.type === "dictItem" ? "column" : deleteTarget.type}
          onConfirm={
            deleteTarget.type === "table" ? handleDeleteTable :
            deleteTarget.type === "dictItem" ? handleDeleteDictItem :
            handleDeleteColumn
          }
          onCancel={() => setDeleteTarget(null)}
        />
      )}

      {/* ═══════════ Left Panel ═══════════ */}
      <div className="w-[340px] min-w-[280px] border-r border-[#E2E8F0] bg-white flex flex-col shrink-0">
        <div className="p-4 border-b border-[#E2E8F0]">
          {/* ── Mode Toggle ── */}
          <div className="flex mb-3 rounded-lg bg-slate-100 p-0.5">
            <button
              className={`flex-1 py-1.5 rounded-md text-xs font-semibold transition ${
                viewMode === "table"
                  ? "bg-white text-slate-800 shadow-sm"
                  : "text-slate-500 hover:text-slate-700"
              }`}
              onClick={() => handleSwitchMode("table")}
            >
              <Database size={13} className="inline mr-1" />
              数据表管理
            </button>
            <button
              className={`flex-1 py-1.5 rounded-md text-xs font-semibold transition ${
                viewMode === "dict"
                  ? "bg-white text-slate-800 shadow-sm"
                  : "text-slate-500 hover:text-slate-700"
              }`}
              onClick={() => handleSwitchMode("dict")}
            >
              <BookOpen size={13} className="inline mr-1" />
              字典项管理
            </button>
          </div>

          {/* ── Table Mode Header ── */}
          {viewMode === "table" && (
            <>
              <div className="text-base font-bold text-slate-800 mb-3 flex items-center gap-2">
                <Database size={18} className="text-indigo-500" />
                数据字典
                <span className="text-xs font-normal text-slate-400">{counts.all} 张表</span>
              </div>

              {/* Search + Filter */}
              <div className="flex gap-2 mb-2.5">
                <div className="flex items-center gap-1.5 flex-1 px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs">
                  <Search size={14} className="text-slate-400 shrink-0" />
                  <input
                    placeholder="搜索表名..."
                    value={search}
                    onChange={e => handleSearchChange(e.target.value)}
                    className="border-none outline-none flex-1 bg-transparent text-xs text-slate-700 placeholder-slate-400"
                  />
                </div>
                <select
                  className="px-2.5 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 min-w-[110px] outline-none"
                  value={statusFilter}
                  onChange={e => handleStatusFilter(e.target.value)}
                >
                  {STATUS_OPTIONS.map(o => (
                    <option key={o.value} value={o.value}>{o.label}</option>
                  ))}
                </select>
              </div>

              {/* New table button */}
              <button
                className="w-full py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold
                  flex items-center justify-center gap-1.5 cursor-pointer transition disabled:opacity-50"
                onClick={handleCreate}
                disabled={saving}
              >
                <Plus size={16} />
                新建数据表
              </button>

              {/* Quick stats */}
              <div className="flex gap-3 mt-2.5 text-[11px] text-slate-400">
                <span>草稿 {counts.draft}</span>
                <span>已发布 {counts.published}</span>
                <span>已废弃 {counts.deprecated}</span>
              </div>
            </>
          )}

          {/* ── Dict Mode Header ── */}
          {viewMode === "dict" && (
            <>
              <div className="text-base font-bold text-slate-800 mb-3 flex items-center gap-2">
                <BookOpen size={18} className="text-emerald-500" />
                字典分组
                <span className="text-xs font-normal text-slate-400">{dictTypes.length} 个类型</span>
              </div>
              <div className="flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs mb-2.5">
                <Search size={14} className="text-slate-400 shrink-0" />
                <input
                  placeholder="搜索字典类型..."
                  value={dictSearch}
                  onChange={e => setDictSearch(e.target.value)}
                  className="border-none outline-none flex-1 bg-transparent text-xs text-slate-700 placeholder-slate-400"
                />
              </div>
            </>
          )}
        </div>

        {/* ── Table Mode List ── */}
        {viewMode === "table" && (
          <div className="flex-1 overflow-y-auto">
            {loading ? (
              <div className="flex flex-col items-center justify-center py-12 text-slate-400 text-xs gap-2">
                <RotateCw size={18} className="animate-spin" />
                <div>加载中...</div>
              </div>
            ) : filteredTables.length === 0 ? (
              <div className="flex items-center justify-center py-12 text-slate-400 text-xs px-4 text-center">
                {search ? `未找到匹配「${search}」的数据表` : "暂无数据表，点击上方「+ 新建数据表」开始创建"}
              </div>
            ) : (
              filteredTables.map(t => {
                const meta = STATUS_META[t.status] ?? STATUS_META.DRAFT;
                const isActive = t.id === selectedTable?.id;
                const colCount = t.columns?.length ?? 0;
                return (
                  <div
                    key={t.id}
                    className={`flex flex-col gap-1 px-3 py-2.5 border-b border-slate-100 cursor-pointer transition text-xs ${
                      isActive ? "bg-indigo-50 border-l-2 border-l-indigo-500" : "hover:bg-slate-50"
                    }`}
                    onClick={() => selectTable(t)}
                  >
                    <div className="flex items-center gap-2">
                      <Database size={13} className="text-slate-400 shrink-0" />
                      <span className="flex-1 font-semibold text-slate-700 truncate">{t.name}</span>
                      <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold ${meta.bg} ${meta.text}`}>
                        {meta.label}
                      </span>
                    </div>
                    <div className="flex items-center gap-3 text-[10px] text-slate-400 pl-5">
                      {t.schema && <span className="flex items-center gap-0.5"><Layers size={10} />{t.schema}</span>}
                      {t.source && <span className="flex items-center gap-0.5"><HardDrive size={10} />{t.source}</span>}
                      <span className="flex items-center gap-0.5"><Columns3 size={10} />{colCount} 字段</span>
                    </div>
                    {/* Inline actions */}
                    <div className="flex gap-1 pl-5">
                      <button
                        className="p-0.5 hover:bg-slate-200 rounded"
                        title="编辑"
                        onClick={e => { e.stopPropagation(); selectTable(t); }}
                      >
                        <Edit3 size={12} className="text-slate-400" />
                      </button>
                      <button
                        className="p-0.5 hover:bg-red-50 rounded"
                        title="删除"
                        onClick={e => {
                          e.stopPropagation();
                          setDeleteTarget({ type: "table", id: t.id, name: t.name });
                        }}
                      >
                        <Trash2 size={12} className="text-red-400" />
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}

        {/* ── Dict Mode List (G1-G5 Subsystem Groups) ── */}
        {viewMode === "dict" && (
          <div className="flex-1 overflow-y-auto">
            {dictLoading ? (
              <div className="flex flex-col items-center justify-center py-12 text-slate-400 text-xs gap-2">
                <RotateCw size={18} className="animate-spin" />
                <div>加载中...</div>
              </div>
            ) : Object.keys(subsystemGroups).length === 0 && dictTypes.length === 0 ? (
              <div className="flex items-center justify-center py-12 text-slate-400 text-xs px-4 text-center">
                暂无可用的字典类型
              </div>
            ) : (
              // G1-G5 grouped accordion
              (Object.keys(subsystemGroups).length > 0
                ? Object.keys(subsystemGroups).sort()
                : ["G1","G2","G3","G4","G5"]
              ).map(groupKey => {
                const groupTypes = subsystemGroups[groupKey] || [];
                const isExpanded = expandedGroups.has(groupKey);
                const gMeta = G1_G5_LABELS[groupKey] || { zh: groupKey, color: "text-slate-600", border: "border-slate-200", bg: "bg-slate-50" };
                
                // Filter by search
                const visibleTypes = groupTypes.filter(dt =>
                  !dictSearch || 
                  (dt.dictName || dt.dictType).toLowerCase().includes(dictSearch.toLowerCase()) ||
                  dt.dictType.toLowerCase().includes(dictSearch.toLowerCase())
                );
                if (dictSearch && visibleTypes.length === 0 && groupTypes.length > 0) return null;

                const totalItems = groupTypes.reduce((sum, dt) => sum + (dt.itemCount || 0), 0);
                
                return (
                  <div key={groupKey} className="border-b border-slate-100">
                    {/* Group Header */}
                    <div
                      className={`flex items-center gap-2 px-3 py-2 cursor-pointer transition text-xs ${gMeta.bg} border-l-2 ${gMeta.border}`}
                      onClick={() => toggleGroup(groupKey)}
                    >
                      <ChevronDown size={12} className={`transition ${isExpanded ? "" : "-rotate-90"} text-slate-400`} />
                      <span className={`font-bold ${gMeta.color}`}>{gMeta.zh}</span>
                      <span className="text-slate-400">{groupTypes.length} 类型 · {totalItems} 项</span>
                    </div>
                    
                    {/* Group Items */}
                    {isExpanded && visibleTypes.map(dt => {
                      const isActive = dt.dictType === selectedDictType;
                      return (
                        <div
                          key={dt.dictType}
                          className={`flex flex-col gap-1 pl-7 pr-3 py-2 border-b border-slate-50 cursor-pointer transition text-xs ${
                            isActive ? "bg-emerald-50 border-l-2 border-l-emerald-500" : "hover:bg-slate-50"
                          }`}
                          onClick={() => handleSelectDictType(dt.dictType)}
                        >
                          <div className="flex items-center gap-2">
                            <FolderTree size={12} className={isActive ? "text-emerald-500" : "text-slate-400"} />
                            <span className={`flex-1 font-semibold truncate ${isActive ? "text-emerald-700" : "text-slate-700"}`}>
                              {dt.dictName || dt.dictType}
                            </span>
                            {dt.itemCount != null && (
                              <span className="text-[10px] text-slate-400 bg-slate-100 px-1.5 py-0.5 rounded-full">
                                {dt.itemCount}
                              </span>
                            )}
                          </div>
                          <div className="text-[10px] text-slate-400 pl-4">
                            <span className="font-mono">{dt.dictType}</span>
                            {dt.description && <span className="truncate"> — {dt.description}</span>}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                );
              })
            )}
          </div>
        )}
      </div>

      {/* ═══════════ Right Panel ═══════════ */}
      {/* ── Table Mode Right ── */}
      {viewMode === "table" && (
        <div className="flex-1 bg-white overflow-y-auto">
          {tableMode === "view" && !selectedTable ? (
            <div className="flex flex-col items-center justify-center h-full text-slate-400 text-xs gap-3">
              <Database size={48} className="opacity-25" />
              <div className="text-center">点击「+ 新建数据表」开始，或从左侧选择一个数据表查看详情</div>
            </div>
          ) : (
            <div className="p-6 space-y-5">
              {/* Header */}
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-bold text-slate-800 flex items-center gap-2">
                  <Database size={20} className="text-indigo-500" />
                  {tableMode === "create" ? "新建数据表" : selectedTable?.name ?? "表详情"}
                </h2>
                {selectedTable && (
                  <span className={`inline-block px-2 py-0.5 rounded text-[11px] font-semibold ${
                    (STATUS_META[selectedTable.status] ?? STATUS_META.DRAFT).bg
                  } ${(STATUS_META[selectedTable.status] ?? STATUS_META.DRAFT).text}`}>
                    {STATUS_META[selectedTable.status]?.label ?? selectedTable.status}
                  </span>
                )}
              </div>

              {/* ── Table Form ── */}
              <div className="grid grid-cols-2 gap-4 p-4 rounded-xl border border-slate-200 bg-slate-50/50">
                <div>
                  <div className="text-[11px] font-semibold text-slate-500 mb-1">表名 *</div>
                  <input
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50 font-mono"
                    placeholder="例如：user_info"
                    value={tableFormName}
                    onChange={e => setTableFormName(e.target.value)}
                    disabled={saving}
                  />
                </div>
                <div>
                  <div className="text-[11px] font-semibold text-slate-500 mb-1">中文名称</div>
                  <input
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50"
                    placeholder="例如：用户信息表"
                    value={tableFormNameZh}
                    onChange={e => setTableFormNameZh(e.target.value)}
                    disabled={saving}
                  />
                </div>
                <div>
                  <div className="text-[11px] font-semibold text-slate-500 mb-1">Schema / 库名</div>
                  <input
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50 font-mono"
                    placeholder="例如：public"
                    value={tableFormSchema}
                    onChange={e => setTableFormSchema(e.target.value)}
                    disabled={saving}
                  />
                </div>
                <div>
                  <div className="text-[11px] font-semibold text-slate-500 mb-1">数据源类型</div>
                  <select
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50"
                    value={tableFormSource}
                    onChange={e => setTableFormSource(e.target.value)}
                    disabled={saving}
                  >
                    <option value="">选择数据源</option>
                    {SOURCE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                </div>
                <div className="col-span-2">
                  <div className="text-[11px] font-semibold text-slate-500 mb-1">描述</div>
                  <textarea
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50 resize-none"
                    placeholder="描述该表的用途、业务含义..."
                    value={tableFormDesc}
                    onChange={e => setTableFormDesc(e.target.value)}
                    disabled={saving}
                    rows={3}
                  />
                </div>
                <div className="col-span-2">
                  <div className="text-[11px] font-semibold text-slate-500 mb-1">标签（逗号分隔）</div>
                  <input
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50"
                    placeholder="例如：核心, 客户域, PII"
                    value={tableFormTags}
                    onChange={e => setTableFormTags(e.target.value)}
                    disabled={saving}
                  />
                </div>
              </div>

              {/* Status transitions */}
              {transitions.length > 0 && (
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-[11px] font-semibold text-slate-400">状态流转:</span>
                  {transitions.map(tr => (
                    <button
                      key={tr.status}
                      className={`px-3 py-1.5 rounded-lg text-[11px] font-semibold transition disabled:opacity-50 ${
                        tr.variant === "primary"
                          ? "bg-indigo-600 hover:bg-indigo-700 text-white"
                          : tr.variant === "danger"
                          ? "bg-red-600 hover:bg-red-700 text-white"
                          : "bg-slate-100 hover:bg-slate-200 text-slate-600"
                      }`}
                      onClick={() => handleTransition(tr.status)}
                      disabled={saving}
                    >
                      {saving ? <RotateCw size={12} className="animate-spin inline mr-1" /> : null}
                      {tr.label}
                    </button>
                  ))}
                </div>
              )}

              {/* Table action buttons */}
              <div className="flex gap-2 pt-2 border-t border-slate-100">
                <button
                  className="px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold transition disabled:opacity-50 flex items-center gap-1"
                  onClick={handleSaveTable}
                  disabled={saving}
                >
                  {saving ? <RotateCw size={14} className="animate-spin" /> : null}
                  保存表信息
                </button>
                <button
                  className="px-4 py-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 text-xs font-semibold transition disabled:opacity-50"
                  onClick={handleCancel}
                  disabled={saving}
                >
                  取消
                </button>
                {selectedTable && (
                  <button
                    className="ml-auto px-4 py-2 rounded-lg bg-red-50 hover:bg-red-100 text-red-600 text-xs font-semibold transition disabled:opacity-50 flex items-center gap-1"
                    onClick={() => setDeleteTarget({ type: "table", id: selectedTable.id, name: selectedTable.name })}
                    disabled={saving}
                  >
                    <Trash2 size={14} />
                    删除此表
                  </button>
                )}
              </div>

              {/* ═══════ Column Section (only when editing a table) ═══════ */}
              {selectedTable && (
                <>
                  <div className="border-t border-slate-200 pt-5">
                    <div className="flex items-center justify-between mb-4">
                      <h3 className="text-sm font-bold text-slate-700 flex items-center gap-2">
                        <Columns3 size={16} className="text-indigo-500" />
                        字段列表
                        <span className="text-xs font-normal text-slate-400">
                          {selectedTable.columns?.length ?? 0} 个字段
                        </span>
                      </h3>
                      <button
                        className="px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-[11px] font-semibold transition disabled:opacity-50 flex items-center gap-1"
                        onClick={openNewColumn}
                        disabled={saving || colFormOpen}
                      >
                        <Plus size={14} />
                        添加字段
                      </button>
                    </div>

                    {/* ── Column Form (inline) ── */}
                    {colFormOpen && (
                      <div className="mb-4 p-4 rounded-xl border-2 border-indigo-200 bg-indigo-50/30">
                        <div className="text-xs font-semibold text-slate-600 mb-3">
                          {colForm.id ? "编辑字段" : "添加新字段"}
                        </div>
                        <div className="grid grid-cols-4 gap-3">
                          {/* Name */}
                          <div>
                            <div className="text-[10px] font-semibold text-slate-500 mb-0.5">字段名 *</div>
                            <input
                              className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 font-mono"
                              placeholder="column_name"
                              value={colForm.name}
                              onChange={e => setColForm(p => ({ ...p, name: e.target.value }))}
                              disabled={saving}
                            />
                          </div>
                          {/* Type */}
                          <div>
                            <div className="text-[10px] font-semibold text-slate-500 mb-0.5">类型</div>
                            <ColumnTypeSelect
                              value={colForm.type}
                              onChange={v => setColForm(p => ({ ...p, type: v }))}
                              disabled={saving}
                            />
                          </div>
                          {/* Length */}
                          <div>
                            <div className="text-[10px] font-semibold text-slate-500 mb-0.5">长度</div>
                            <input
                              className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 font-mono"
                              placeholder="255"
                              value={colForm.length}
                              onChange={e => setColForm(p => ({ ...p, length: e.target.value }))}
                              disabled={saving}
                            />
                          </div>
                          {/* Precision / Scale */}
                          <div className="flex gap-1.5">
                            <div className="flex-1">
                              <div className="text-[10px] font-semibold text-slate-500 mb-0.5">精度</div>
                              <input
                                className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 font-mono"
                                placeholder="10"
                                value={colForm.precision}
                                onChange={e => setColForm(p => ({ ...p, precision: e.target.value }))}
                                disabled={saving}
                              />
                            </div>
                            <div className="flex-1">
                              <div className="text-[10px] font-semibold text-slate-500 mb-0.5">标度</div>
                              <input
                                className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 font-mono"
                                placeholder="2"
                                value={colForm.scale}
                                onChange={e => setColForm(p => ({ ...p, scale: e.target.value }))}
                                disabled={saving}
                              />
                            </div>
                          </div>
                          {/* Default */}
                          <div>
                            <div className="text-[10px] font-semibold text-slate-500 mb-0.5">默认值</div>
                            <input
                              className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 font-mono"
                              placeholder="NULL"
                              value={colForm.defaultValue}
                              onChange={e => setColForm(p => ({ ...p, defaultValue: e.target.value }))}
                              disabled={saving}
                            />
                          </div>
                          {/* Description */}
                          <div className="col-span-2">
                            <div className="text-[10px] font-semibold text-slate-500 mb-0.5">描述</div>
                            <input
                              className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400"
                              placeholder="字段说明..."
                              value={colForm.description}
                              onChange={e => setColForm(p => ({ ...p, description: e.target.value }))}
                              disabled={saving}
                            />
                          </div>
                          {/* Nullable / PK toggles */}
                          <div className="flex items-end gap-3 pb-1">
                            <label className="flex items-center gap-1 text-[10px] text-slate-600 cursor-pointer">
                              <input
                                type="checkbox"
                                className="w-3.5 h-3.5 rounded accent-indigo-500"
                                checked={colForm.nullable}
                                onChange={e => setColForm(p => ({ ...p, nullable: e.target.checked }))}
                                disabled={saving}
                              />
                              可为空
                            </label>
                            <label className="flex items-center gap-1 text-[10px] text-slate-600 cursor-pointer">
                              <input
                                type="checkbox"
                                className="w-3.5 h-3.5 rounded accent-amber-500"
                                checked={colForm.primaryKey}
                                onChange={e => setColForm(p => ({ ...p, primaryKey: e.target.checked }))}
                                disabled={saving}
                              />
                              主键
                            </label>
                          </div>
                        </div>
                        {/* Column form actions */}
                        <div className="flex gap-2 mt-3 pt-3 border-t border-indigo-100">
                          <button
                            className="px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-[11px] font-semibold transition disabled:opacity-50 flex items-center gap-1"
                            onClick={handleSaveColumn}
                            disabled={saving}
                          >
                            {saving ? <RotateCw size={12} className="animate-spin" /> : null}
                            {colForm.id ? "更新字段" : "添加字段"}
                          </button>
                          <button
                            className="px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 text-[11px] font-semibold transition"
                            onClick={cancelColumnForm}
                            disabled={saving}
                          >
                            取消
                          </button>
                        </div>
                      </div>
                    )}

                    {/* ── Column List ── */}
                    {!selectedTable.columns || selectedTable.columns.length === 0 ? (
                      <div className="text-center py-8 text-slate-400 text-xs">
                        {colFormOpen ? null : "暂无字段，点击「添加字段」开始定义表结构"}
                      </div>
                    ) : (
                      <div className="border border-slate-200 rounded-xl overflow-hidden">
                        {/* Column header */}
                        <div className="grid grid-cols-[1fr_120px_80px_70px_100px_80px] gap-2 px-4 py-2 bg-slate-100 text-[10px] font-semibold text-slate-500 uppercase">
                          <span>字段名</span>
                          <span>类型</span>
                          <span>可空</span>
                          <span>主键</span>
                          <span>默认值</span>
                          <span>操作</span>
                        </div>

                        {[...selectedTable.columns]
                          .sort((a, b) => a.sortOrder - b.sortOrder)
                          .map(col => (
                            <div key={col.id}>
                              <div
                                className={`grid grid-cols-[1fr_120px_80px_70px_100px_80px] gap-2 px-4 py-2.5 border-t border-slate-100 text-xs
                                  cursor-pointer transition hover:bg-slate-50 ${
                                  expandedColId === col.id ? "bg-indigo-50/50" : ""
                                }`}
                                onClick={() => setExpandedColId(expandedColId === col.id ? null : col.id)}
                              >
                                <span className="font-mono font-semibold text-slate-700 truncate flex items-center gap-1.5">
                                  {col.primaryKey && <Key size={11} className="text-amber-500 shrink-0" />}
                                  {col.name}
                                </span>
                                <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono font-semibold ${typeBadge(col.type)}`}>
                                  {col.type}{col.length ? `(${col.length})` : ""}
                                </span>
                                <span className="text-slate-500 flex items-center gap-1">
                                  {col.nullable
                                    ? <span className="text-slate-400">YES</span>
                                    : <span className="text-red-500 font-semibold">NO</span>}
                                </span>
                                <span>
                                  {col.primaryKey
                                    ? <Key size={13} className="text-amber-500" />
                                    : <span className="text-slate-300">—</span>}
                                </span>
                                <span className="font-mono text-slate-400 truncate">
                                  {col.defaultValue ?? <span className="text-slate-300 italic">NULL</span>}
                                </span>
                                <span className="flex items-center gap-1">
                                  <button
                                    className="p-0.5 hover:bg-slate-200 rounded"
                                    title="编辑"
                                    onClick={e => { e.stopPropagation(); openEditColumn(col); }}
                                  >
                                    <Edit3 size={12} className="text-slate-400" />
                                  </button>
                                  <button
                                    className="p-0.5 hover:bg-red-50 rounded"
                                    title="删除"
                                    onClick={e => {
                                      e.stopPropagation();
                                      setDeleteTarget({ type: "column", id: col.id, name: col.name });
                                    }}
                                  >
                                    <Trash2 size={12} className="text-red-400" />
                                  </button>
                                </span>
                              </div>

                              {/* Expanded detail */}
                              {expandedColId === col.id && (
                                <div className="px-4 py-3 bg-slate-50 border-t border-slate-100 text-[11px] text-slate-500 grid grid-cols-2 gap-x-6 gap-y-1">
                                  <div><span className="font-semibold text-slate-400">字段名:</span> <span className="font-mono text-slate-700">{col.name}</span></div>
                                  <div><span className="font-semibold text-slate-400">类型:</span> <span className="font-mono text-slate-700">{col.type}{col.length ? `(${col.length})` : ""}{col.precision != null ? `(${col.precision},${col.scale ?? 0})` : ""}</span></div>
                                  <div><span className="font-semibold text-slate-400">可为空:</span> <span className="text-slate-700">{col.nullable ? "是" : "否"}</span></div>
                                  <div><span className="font-semibold text-slate-400">主键:</span> <span className="text-slate-700">{col.primaryKey ? "是" : "否"}</span></div>
                                  {col.defaultValue && <div className="col-span-2"><span className="font-semibold text-slate-400">默认值:</span> <span className="font-mono text-slate-700">{col.defaultValue}</span></div>}
                                  {col.description && <div className="col-span-2"><span className="font-semibold text-slate-400">描述:</span> <span className="text-slate-700">{col.description}</span></div>}
                                </div>
                              )}
                            </div>
                          ))}
                      </div>
                    )}
                  </div>

                  {/* Table metadata footer */}
                  <div className="border-t border-slate-200 pt-4 mt-2 flex flex-wrap gap-x-6 gap-y-1 text-[11px] text-slate-400">
                    {selectedTable.code && (
                      <span className="flex items-center gap-1"><Hash size={11} /> 编码: <span className="font-mono text-slate-500">{selectedTable.code}</span></span>
                    )}
                    {selectedTable.schema && (
                      <span className="flex items-center gap-1"><Layers size={11} /> Schema: <span className="font-mono text-slate-500">{selectedTable.schema}</span></span>
                    )}
                    {selectedTable.source && (
                      <span className="flex items-center gap-1"><HardDrive size={11} /> 数据源: <span className="text-slate-500">{selectedTable.source}</span></span>
                    )}
                    {selectedTable.owner && (
                      <span className="flex items-center gap-1"><User size={11} /> 负责人: <span className="text-slate-500">{selectedTable.owner}</span></span>
                    )}
                    {selectedTable.createdAt && (
                      <span className="flex items-center gap-1"><Calendar size={11} /> 创建: <span className="text-slate-500">{new Date(selectedTable.createdAt).toLocaleDateString("zh-CN")}</span></span>
                    )}
                    {selectedTable.tags && selectedTable.tags.length > 0 && (
                      <span className="flex items-center gap-1 flex-wrap">
                        <Tag size={11} />
                        {selectedTable.tags.map(tg => (
                          <span key={tg} className="px-1.5 py-0.5 rounded bg-slate-100 text-slate-500 text-[10px] font-mono">{tg}</span>
                        ))}
                      </span>
                    )}
                  </div>
                </>
              )}
            </div>
          )}
        </div>
      )}

      {/* ── Dict Mode Right ── */}
      {viewMode === "dict" && (
        <div className="flex-1 bg-white overflow-y-auto">
          {!selectedDictType ? (
            <div className="flex flex-col items-center justify-center h-full text-slate-400 text-xs gap-3">
              <BookOpen size={48} className="opacity-25" />
              <div className="text-center">从左侧选择一个字典类型查看其字典项</div>
            </div>
          ) : (
            <div className="p-6 space-y-4">
              {/* Header */}
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-bold text-slate-800 flex items-center gap-2">
                  <FolderTree size={20} className="text-emerald-500" />
                  {dictTypes.find(dt => dt.dictType === selectedDictType)?.dictName ?? selectedDictType}
                  <span className="text-sm font-normal text-slate-400 font-mono">({selectedDictType})</span>
                </h2>
                <button
                  className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-semibold transition disabled:opacity-50 flex items-center gap-1"
                  onClick={openNewDictItem}
                  disabled={saving || dictItemFormOpen}
                >
                  <Plus size={14} />
                  新增字典项
                </button>
              </div>

              {/* Search */}
              <div className="flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs w-64">
                <Search size={14} className="text-slate-400 shrink-0" />
                <input
                  placeholder="搜索字典项..."
                  value={dictSearch}
                  onChange={e => setDictSearch(e.target.value)}
                  className="border-none outline-none flex-1 bg-transparent text-xs text-slate-700 placeholder-slate-400"
                />
              </div>

              {/* ── Dict Item Form ── */}
              {dictItemFormOpen && (
                <div className="p-4 rounded-xl border-2 border-emerald-200 bg-emerald-50/30">
                  <div className="text-xs font-semibold text-slate-600 mb-3">
                    {dictItemForm.editCode ? "编辑字典项" : "新增字典项"}
                  </div>
                  <div className="grid grid-cols-3 gap-3">
                    <div>
                      <div className="text-[10px] font-semibold text-slate-500 mb-0.5">编码 *</div>
                      <input
                        className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-emerald-400 font-mono disabled:opacity-40"
                        placeholder="dict_code"
                        value={dictItemForm.dictCode}
                        onChange={e => setDictItemForm(p => ({ ...p, dictCode: e.target.value }))}
                        disabled={saving || !!dictItemForm.editCode}
                      />
                    </div>
                    <div>
                      <div className="text-[10px] font-semibold text-slate-500 mb-0.5">值</div>
                      <input
                        className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-emerald-400 font-mono"
                        placeholder="dict_value"
                        value={dictItemForm.extValue}
                        onChange={e => setDictItemForm(p => ({ ...p, extValue: e.target.value }))}
                        disabled={saving}
                      />
                    </div>
                    <div>
                      <div className="text-[10px] font-semibold text-slate-500 mb-0.5">标签 *</div>
                      <input
                        className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-emerald-400"
                        placeholder="显示名称"
                        value={dictItemForm.dictLabel}
                        onChange={e => setDictItemForm(p => ({ ...p, dictLabel: e.target.value }))}
                        disabled={saving}
                      />
                    </div>
                    <div>
                      <div className="text-[10px] font-semibold text-slate-500 mb-0.5">状态</div>
                      <select
                        className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-emerald-400"
                        value={dictItemForm.status}
                        onChange={e => setDictItemForm(p => ({ ...p, status: e.target.value }))}
                        disabled={saving}
                      >
                        <option value="active">启用</option>
                        <option value="inactive">禁用</option>
                      </select>
                    </div>
                    <div>
                      <div className="text-[10px] font-semibold text-slate-500 mb-0.5">排序</div>
                      <input
                        className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-emerald-400 font-mono"
                        placeholder="0"
                        value={dictItemForm.sortOrder}
                        onChange={e => setDictItemForm(p => ({ ...p, sortOrder: e.target.value }))}
                        disabled={saving}
                      />
                    </div>
                    <div>
                      <div className="text-[10px] font-semibold text-slate-500 mb-0.5">描述</div>
                      <input
                        className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-emerald-400"
                        placeholder="备注说明..."
                        value={dictItemForm.description}
                        onChange={e => setDictItemForm(p => ({ ...p, description: e.target.value }))}
                        disabled={saving}
                      />
                    </div>
                  </div>
                  <div className="flex gap-2 mt-3 pt-3 border-t border-emerald-100">
                    <button
                      className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-semibold transition disabled:opacity-50 flex items-center gap-1"
                      onClick={handleSaveDictItem}
                      disabled={saving}
                    >
                      {saving ? <RotateCw size={12} className="animate-spin" /> : null}
                      {dictItemForm.editCode ? "更新" : "创建"}
                    </button>
                    <button
                      className="px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 text-[11px] font-semibold transition"
                      onClick={cancelDictItemForm}
                      disabled={saving}
                    >
                      取消
                    </button>
                  </div>
                </div>
              )}

              {/* ── Dict Items Table ── */}
              {dictLoading ? (
                <div className="flex flex-col items-center justify-center py-12 text-slate-400 text-xs gap-2">
                  <RotateCw size={18} className="animate-spin" />
                  <div>加载中...</div>
                </div>
              ) : filteredDictItems.length === 0 ? (
                <div className="text-center py-12 text-slate-400 text-xs">
                  {dictSearch ? `未找到匹配「${dictSearch}」的字典项` : "暂无字典项，点击「新增字典项」开始添加"}
                </div>
              ) : (
                <div className="border border-slate-200 rounded-xl overflow-hidden">
                  <div className="grid grid-cols-[1fr_1fr_1.5fr_80px_80px_100px] gap-2 px-4 py-2 bg-slate-100 text-[10px] font-semibold text-slate-500 uppercase">
                    <span>编码</span>
                    <span>值</span>
                    <span>标签</span>
                    <span>排序</span>
                    <span>状态</span>
                    <span>操作</span>
                  </div>
                  {filteredDictItems
                    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
                    .map(item => (
                      <div
                        key={item.dictCode}
                        className="grid grid-cols-[1fr_1fr_1.5fr_80px_80px_100px] gap-2 px-4 py-2.5 border-t border-slate-100 text-xs hover:bg-slate-50"
                      >
                        <span className="font-mono font-semibold text-slate-700 truncate">{item.dictCode}</span>
                        <span className="font-mono text-slate-600 truncate">{item.extValue || "—"}</span>
                        <span className="text-slate-700 truncate">{item.dictLabel}</span>
                        <span className="text-slate-400">{item.sortOrder ?? 0}</span>
                        <span>
                          <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold ${
                            item.status === "active"
                              ? "bg-green-50 text-green-600"
                              : "bg-slate-100 text-slate-500"
                          }`}>
                            {item.status === "active" ? "启用" : "禁用"}
                          </span>
                        </span>
                        <span className="flex items-center gap-1">
                          <button
                            className="p-0.5 hover:bg-slate-200 rounded"
                            title="编辑"
                            onClick={() => openEditDictItem(item)}
                          >
                            <Edit3 size={12} className="text-slate-400" />
                          </button>
                          <button
                            className="p-0.5 hover:bg-red-50 rounded"
                            title="删除"
                            onClick={() => setDeleteTarget({ type: "dictItem", id: item.dictCode, name: item.dictLabel })}
                          >
                            <Trash2 size={12} className="text-red-400" />
                          </button>
                        </span>
                      </div>
                    ))}
                </div>
              )}

              {/* Description of selected dict type */}
              {selectedDictType && (
                <div className="border-t border-slate-200 pt-3 mt-2 text-[11px] text-slate-400">
                  {(() => {
                    const dt = dictTypes.find(d => d.dictType === selectedDictType);
                    return dt?.description ? (
                      <span>说明: {dt.description}</span>
                    ) : null;
                  })()}
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
