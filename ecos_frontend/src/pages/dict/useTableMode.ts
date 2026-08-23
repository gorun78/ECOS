import { useState, useCallback, useEffect, useRef } from "react";
import {
  getDictTables, getDictTable, createDictTable,
  updateDictTable, deleteDictTable,
  createDictColumn, updateDictColumn, deleteDictColumn,
  type DictTable, type DictColumn,
} from "../../../services/dict";
import { STATUS_META, emptyColumnForm, type ColumnFormState } from "./constants";

export function useTableMode(showToast: (type: "success" | "error", msg: string) => void) {
  const [tables, setTables] = useState<DictTable[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [selectedTable, setSelectedTable] = useState<DictTable | null>(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [tableMode, setTableMode] = useState<"view" | "create" | "edit">("view");
  const [tableFormName, setTableFormName] = useState("");
  const [tableFormNameZh, setTableFormNameZh] = useState("");
  const [tableFormSchema, setTableFormSchema] = useState("");
  const [tableFormSource, setTableFormSource] = useState("");
  const [tableFormDesc, setTableFormDesc] = useState("");
  const [tableFormTags, setTableFormTags] = useState("");
  const [colForm, setColForm] = useState<ColumnFormState>(emptyColumnForm());
  const [colFormOpen, setColFormOpen] = useState(false);
  const [expandedColId, setExpandedColId] = useState<string | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{ type: "table" | "column" | "dictItem"; id: string; name: string } | null>(null);

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

  const loadTableDetail = useCallback(async (id: string) => {
    try {
      const t = await getDictTable(id);
      setSelectedTable(t);
      setTables(prev => prev.map(x => x.id === id ? { ...x, columns: t.columns } : x));
    } catch (e: any) {
      showToast("error", `加载表详情失败: ${e.message}`);
    }
  }, [showToast]);

  const handleStatusFilter = (v: string) => {
    setStatusFilter(v);
    setSelectedTable(null);
    setTableMode("view");
    loadTables(v || undefined);
  };

  const handleSearchChange = (v: string) => {
    setSearch(v);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {}, 300);
  };

  const filteredTables = tables.filter(t =>
    !search ||
    t.name.toLowerCase().includes(search.toLowerCase()) ||
    (t.nameZh && t.nameZh.includes(search)) ||
    (t.code && t.code.toLowerCase().includes(search.toLowerCase()))
  );

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

  const handleCreate = () => {
    setSelectedTable(null);
    setTableMode("create");
    setTableFormName(""); setTableFormNameZh(""); setTableFormSchema("");
    setTableFormSource(""); setTableFormDesc(""); setTableFormTags("");
  };

  const handleCancel = () => { setTableMode("view"); setSelectedTable(null); };

  const handleSaveTable = async () => {
    if (!tableFormName.trim()) { showToast("error", "表名不能为空"); return; }
    setSaving(true);
    try {
      const tags = tableFormTags.split(",").map(s => s.trim()).filter(Boolean);
      if (tableMode === "create") {
        const res = await createDictTable({
          name: tableFormName.trim(), nameZh: tableFormNameZh.trim(),
          schema: tableFormSchema.trim(), description: tableFormDesc.trim(),
          source: tableFormSource || undefined,
        });
        showToast("success", "数据表创建成功");
        await loadTables(statusFilter || undefined);
        await loadTableDetail(res.id);
        setTableMode("edit");
      } else if (selectedTable) {
        await updateDictTable(selectedTable.id, {
          name: tableFormName.trim(), nameZh: tableFormNameZh.trim(),
          description: tableFormDesc.trim(), tags: tags.length > 0 ? tags : undefined,
        });
        showToast("success", "数据表更新成功");
        await loadTables(statusFilter || undefined);
        await loadTableDetail(selectedTable.id);
      }
    } catch (e: any) { showToast("error", `保存失败: ${e.message}`); }
    finally { setSaving(false); }
  };

  const handleTransition = async (newStatus: string) => {
    if (!selectedTable) return;
    setSaving(true);
    try {
      await updateDictTable(selectedTable.id, { status: newStatus });
      showToast("success", `状态已变更为「${STATUS_META[newStatus]?.label ?? newStatus}」`);
      await loadTables(statusFilter || undefined);
      await loadTableDetail(selectedTable.id);
    } catch (e: any) { showToast("error", `状态变更失败: ${e.message}`); }
    finally { setSaving(false); }
  };

  const handleDeleteTable = async () => {
    if (!deleteTarget || deleteTarget.type !== "table") return;
    const name = deleteTarget.name;
    setSaving(true);
    try {
      await deleteDictTable(deleteTarget.id);
      showToast("success", `「${name}」已删除`);
      setDeleteTarget(null);
      if (selectedTable?.id === deleteTarget.id) { setSelectedTable(null); setTableMode("view"); }
      await loadTables(statusFilter || undefined);
    } catch (e: any) { showToast("error", `删除失败: ${e.message}`); }
    finally { setSaving(false); }
  };

  const openNewColumn = () => { setColForm(emptyColumnForm()); setColFormOpen(true); setExpandedColId(null); };
  const openEditColumn = (col: DictColumn) => {
    setColForm({ id: col.id, name: col.name, type: col.type,
      length: col.length?.toString() ?? "", precision: col.precision?.toString() ?? "",
      scale: col.scale?.toString() ?? "", nullable: col.nullable, primaryKey: col.primaryKey,
      defaultValue: col.defaultValue ?? "", description: col.description ?? "" });
    setColFormOpen(true); setExpandedColId(null);
  };
  const cancelColumnForm = () => { setColFormOpen(false); setColForm(emptyColumnForm()); };

  const handleSaveColumn = async () => {
    if (!selectedTable) return;
    if (!colForm.name.trim()) { showToast("error", "字段名不能为空"); return; }
    setSaving(true);
    try {
      const payload = { name: colForm.name.trim(), type: colForm.type,
        length: colForm.length ? parseInt(colForm.length, 10) : undefined,
        precision: colForm.precision ? parseInt(colForm.precision, 10) : undefined,
        scale: colForm.scale ? parseInt(colForm.scale, 10) : undefined,
        nullable: colForm.nullable, primaryKey: colForm.primaryKey,
        defaultValue: colForm.defaultValue || undefined, description: colForm.description.trim() };
      if (colForm.id) { await updateDictColumn(selectedTable.id, colForm.id, payload); showToast("success", "字段更新成功"); }
      else { await createDictColumn(selectedTable.id, payload); showToast("success", "字段添加成功"); }
      await loadTableDetail(selectedTable.id); cancelColumnForm();
    } catch (e: any) { showToast("error", `保存字段失败: ${e.message}`); }
    finally { setSaving(false); }
  };

  const handleDeleteColumn = async () => {
    if (!selectedTable || !deleteTarget || deleteTarget.type !== "column") return;
    setSaving(true);
    try {
      await deleteDictColumn(selectedTable.id, deleteTarget.id);
      showToast("success", `字段「${deleteTarget.name}」已删除`);
      setDeleteTarget(null); await loadTableDetail(selectedTable.id);
    } catch (e: any) { showToast("error", `删除字段失败: ${e.message}`); }
    finally { setSaving(false); }
  };

  const transitions = selectedTable
    ? (selectedTable.status === "DRAFT"
        ? [{ label: "发布", status: "PUBLISHED", variant: "primary" as const }]
        : selectedTable.status === "PUBLISHED"
        ? [{ label: "废弃", status: "DEPRECATED", variant: "danger" as const }]
        : selectedTable.status === "DEPRECATED"
        ? [{ label: "重新启用", status: "DRAFT", variant: "secondary" as const }]
        : [])
    : [];

  const counts = {
    all: tables.length,
    draft: tables.filter(t => t.status === "DRAFT").length,
    published: tables.filter(t => t.status === "PUBLISHED").length,
    deprecated: tables.filter(t => t.status === "DEPRECATED").length,
  };

  return {
    tables, loading, saving, setSaving, selectedTable, search, statusFilter, tableMode,
    tableFormName, tableFormNameZh, tableFormSchema, tableFormSource, tableFormDesc, tableFormTags,
    colForm, colFormOpen, expandedColId, deleteTarget, filteredTables, transitions, counts,
    setTableFormName, setTableFormNameZh, setTableFormSchema, setTableFormSource,
    setTableFormDesc, setTableFormTags, setColForm, setExpandedColId, setDeleteTarget,
    handleSearchChange, handleStatusFilter, handleCreate, handleCancel, handleSaveTable,
    handleTransition, selectTable, openNewColumn, openEditColumn, cancelColumnForm,
    handleSaveColumn, handleDeleteTable, handleDeleteColumn,
  };
}
