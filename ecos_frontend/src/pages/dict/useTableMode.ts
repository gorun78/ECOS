import { useState, useCallback, useEffect, useRef } from "react";
import {
  getDictTables, getDictTable, createDictTable,
  updateDictTable, deleteDictTable,
  createDictColumn, updateDictColumn, deleteDictColumn,
  type DictTable, type DictColumn,
} from "../../services/dict";
import { STATUS_META, emptyColumnForm, type ColumnFormState } from "./constants";

export function useTableMode(
  showToast: (type: "success" | "error", msg: string) => void,
  t: (key: string, params?: Record<string, string | number>) => string
) {
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
      showToast("error", t("dict.toast.loadTablesFailed", { msg: e.message }));
    } finally {
      setLoading(false);
    }
  }, [showToast, t]);

  useEffect(() => { loadTables(); }, [loadTables]);

  const loadTableDetail = useCallback(async (id: string) => {
    try {
      const detail = await getDictTable(id);
      setSelectedTable(detail);
      setTables(prev => prev.map(x => x.id === id ? { ...x, columns: detail.columns } : x));
    } catch (e: any) {
      showToast("error", t("dict.toast.loadTableDetailFailed", { msg: e.message }));
    }
  }, [showToast, t]);

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
    if (!tableFormName.trim()) { showToast("error", t("dict.toast.tableNameRequired")); return; }
    setSaving(true);
    try {
      const tags = tableFormTags.split(",").map(s => s.trim()).filter(Boolean);
      if (tableMode === "create") {
        const res = await createDictTable({
          name: tableFormName.trim(), nameZh: tableFormNameZh.trim(),
          schema: tableFormSchema.trim(), description: tableFormDesc.trim(),
          source: tableFormSource || undefined,
        });
        showToast("success", t("dict.toast.tableCreated"));
        await loadTables(statusFilter || undefined);
        await loadTableDetail(res.id);
        setTableMode("edit");
      } else if (selectedTable) {
        await updateDictTable(selectedTable.id, {
          name: tableFormName.trim(), nameZh: tableFormNameZh.trim(),
          description: tableFormDesc.trim(), tags: tags.length > 0 ? tags : undefined,
        });
        showToast("success", t("dict.toast.tableUpdated"));
        await loadTables(statusFilter || undefined);
        await loadTableDetail(selectedTable.id);
      }
    } catch (e: any) { showToast("error", t("dict.toast.saveTableFailed", { msg: e.message })); }
    finally { setSaving(false); }
  };

  const handleTransition = async (newStatus: string) => {
    if (!selectedTable) return;
    setSaving(true);
    try {
      await updateDictTable(selectedTable.id, { status: newStatus });
      showToast("success", t("dict.toast.statusChanged", { name: STATUS_META[newStatus] ? t(STATUS_META[newStatus].labelKey) : newStatus }));
      await loadTables(statusFilter || undefined);
      await loadTableDetail(selectedTable.id);
    } catch (e: any) { showToast("error", t("dict.toast.transitionFailed", { msg: e.message })); }
    finally { setSaving(false); }
  };

  const handleDeleteTable = async () => {
    if (!deleteTarget || deleteTarget.type !== "table") return;
    const name = deleteTarget.name;
    setSaving(true);
    try {
      await deleteDictTable(deleteTarget.id);
      showToast("success", t("dict.toast.tableDeleted", { name }));
      setDeleteTarget(null);
      if (selectedTable?.id === deleteTarget.id) { setSelectedTable(null); setTableMode("view"); }
      await loadTables(statusFilter || undefined);
    } catch (e: any) { showToast("error", t("dict.toast.deleteTableFailed", { msg: e.message })); }
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
    if (!colForm.name.trim()) { showToast("error", t("dict.toast.columnNameRequired")); return; }
    setSaving(true);
    try {
      const payload = { name: colForm.name.trim(), type: colForm.type,
        length: colForm.length ? parseInt(colForm.length, 10) : undefined,
        precision: colForm.precision ? parseInt(colForm.precision, 10) : undefined,
        scale: colForm.scale ? parseInt(colForm.scale, 10) : undefined,
        nullable: colForm.nullable, primaryKey: colForm.primaryKey,
        defaultValue: colForm.defaultValue || undefined, description: colForm.description.trim() };
      if (colForm.id) { await updateDictColumn(selectedTable.id, colForm.id, payload); showToast("success", t("dict.toast.columnUpdated")); }
      else { await createDictColumn(selectedTable.id, payload); showToast("success", t("dict.toast.columnCreated")); }
      await loadTableDetail(selectedTable.id); cancelColumnForm();
    } catch (e: any) { showToast("error", t("dict.toast.saveColumnFailed", { msg: e.message })); }
    finally { setSaving(false); }
  };

  const handleDeleteColumn = async () => {
    if (!selectedTable || !deleteTarget || deleteTarget.type !== "column") return;
    setSaving(true);
    try {
      await deleteDictColumn(selectedTable.id, deleteTarget.id);
      showToast("success", t("dict.toast.columnDeleted", { name: deleteTarget.name }));
      setDeleteTarget(null); await loadTableDetail(selectedTable.id);
    } catch (e: any) { showToast("error", t("dict.toast.deleteColumnFailed", { msg: e.message })); }
    finally { setSaving(false); }
  };

  const transitions = selectedTable
    ? (selectedTable.status === "DRAFT"
        ? [{ label: t("dict.transition.publish"), status: "PUBLISHED", variant: "primary" as const }]
        : selectedTable.status === "PUBLISHED"
        ? [{ label: t("dict.transition.deprecate"), status: "DEPRECATED", variant: "danger" as const }]
        : selectedTable.status === "DEPRECATED"
        ? [{ label: t("dict.transition.reactivate"), status: "DRAFT", variant: "secondary" as const }]
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
