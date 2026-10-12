import { useState, useCallback } from "react";
import {
  listDictTypes, getDictItems, createDictItem,
  updateDictItem, deleteDictItem, fetchDictSubsystems,
  type DictType, type DictItem,
} from "../../api";

export function useDictMode(
  showToast: (type: "success" | "error", msg: string) => void,
  t: (key: string, params?: Record<string, string | number>) => string
) {
  const [viewMode, setViewMode] = useState<"table" | "dict">("dict");
  const [dictTypes, setDictTypes] = useState<DictType[]>([]);
  const [dictLoading, setDictLoading] = useState(false);
  const [selectedDictType, setSelectedDictType] = useState<string | null>(null);
  const [dictItems, setDictItems] = useState<DictItem[]>([]);
  const [dictSearch, setDictSearch] = useState("");
  const [dictItemFormOpen, setDictItemFormOpen] = useState(false);
  const [dictItemForm, setDictItemForm] = useState<{
    editCode?: string; dictCode: string; extValue: string;
    dictLabel: string; status: string; sortOrder: string; description: string;
  }>({ dictCode: "", extValue: "", dictLabel: "", status: "active", sortOrder: "", description: "" });
  const [subsystemGroups, setSubsystemGroups] = useState<Record<string, DictType[]>>({});
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set(["G1","G2","G3","G4","G5"]));
  const [deleteTarget, setDeleteTarget] = useState<{ type: "table" | "column" | "dictItem"; id: string; name: string } | null>(null);

  const toggleGroup = (g: string) => {
    setExpandedGroups(prev => { const next = new Set(prev); next.has(g) ? next.delete(g) : next.add(g); return next; });
  };

  const loadDictTypes = useCallback(async () => {
    setDictLoading(true);
    try {
      const groups = await fetchDictSubsystems();
      setSubsystemGroups(groups);
      const all: DictType[] = [];
      Object.values(groups).forEach(arr => all.push(...arr));
      setDictTypes(all);
    } catch {
      try {
        const types = await listDictTypes();
        setDictTypes(types);
      } catch (e: any) { showToast("error", t("dict.toast.loadTypesFailed", { msg: e.message })); }
    } finally { setDictLoading(false); }
  }, [showToast, t]);

  const loadDictItems = useCallback(async (dictType: string) => {
    setDictLoading(true);
    try { const items = await getDictItems(dictType); setDictItems(items); }
    catch (e: any) { showToast("error", t("dict.toast.loadItemsFailed", { msg: e.message })); setDictItems([]); }
    finally { setDictLoading(false); }
  }, [showToast, t]);

  const handleSelectDictType = (dictType: string) => {
    setSelectedDictType(dictType); setDictItemFormOpen(false); loadDictItems(dictType);
  };

  const handleSwitchMode = (mode: "table" | "dict") => {
    setViewMode(mode);
    if (mode === "dict" && dictTypes.length === 0) loadDictTypes();
  };

  const openNewDictItem = () => {
    setDictItemForm({ editCode: undefined, dictCode: "", extValue: "", dictLabel: "", status: "active", sortOrder: "", description: "" });
    setDictItemFormOpen(true);
  };

  const openEditDictItem = (item: DictItem) => {
    setDictItemForm({ editCode: item.dictCode, dictCode: item.dictCode, extValue: item.extValue ?? "",
      dictLabel: item.dictLabel, status: item.status, sortOrder: item.sortOrder?.toString() ?? "", description: "" });
    setDictItemFormOpen(true);
  };

  const cancelDictItemForm = () => { setDictItemFormOpen(false); };

  const handleSaveDictItem = async (savingFn: (b: boolean) => void) => {
    if (!selectedDictType) return;
    if (!dictItemForm.dictCode.trim() || !dictItemForm.dictLabel.trim()) { showToast("error", t("dict.toast.emptyRequired")); return; }
    savingFn(true);
    try {
      if (dictItemForm.editCode) {
        await updateDictItem(selectedDictType, dictItemForm.editCode, {
          extValue: dictItemForm.extValue.trim() || undefined, dictLabel: dictItemForm.dictLabel.trim(),
          status: dictItemForm.status, sortOrder: dictItemForm.sortOrder ? parseInt(dictItemForm.sortOrder, 10) : undefined,
        });
        showToast("success", t("dict.toast.itemUpdated"));
      } else {
        await createDictItem({
          dictType: selectedDictType, dictCode: dictItemForm.dictCode.trim(),
          extValue: dictItemForm.extValue.trim() || undefined, dictLabel: dictItemForm.dictLabel.trim(),
          status: dictItemForm.status, sortOrder: dictItemForm.sortOrder ? parseInt(dictItemForm.sortOrder, 10) : undefined,
        });
        showToast("success", t("dict.toast.itemCreated"));
      }
      await loadDictItems(selectedDictType); cancelDictItemForm();
    } catch (e: any) { showToast("error", t("dict.toast.saveItemFailed", { msg: e.message })); }
    finally { savingFn(false); }
  };

  const handleDeleteDictItem = async (savingFn: (b: boolean) => void) => {
    if (!selectedDictType || !deleteTarget || deleteTarget.type !== "dictItem") return;
    savingFn(true);
    try {
      await deleteDictItem(selectedDictType, deleteTarget.id);
      showToast("success", t("dict.toast.itemDeleted", { name: deleteTarget.name }));
      setDeleteTarget(null); await loadDictItems(selectedDictType);
    } catch (e: any) { showToast("error", t("dict.toast.deleteItemFailed", { msg: e.message })); }
    finally { savingFn(false); }
  };

  const filteredDictItems = dictItems.filter(item =>
    !dictSearch ||
    item.dictLabel.toLowerCase().includes(dictSearch.toLowerCase()) ||
    item.dictCode.toLowerCase().includes(dictSearch.toLowerCase()) ||
    item.extValue?.toLowerCase().includes(dictSearch.toLowerCase())
  );

  return {
    viewMode, dictTypes, dictLoading, selectedDictType, dictItems, dictSearch,
    dictItemFormOpen, dictItemForm, subsystemGroups, expandedGroups, deleteTarget, filteredDictItems,
    setViewMode, setDictSearch, setDictItemForm, setDeleteTarget,
    toggleGroup, loadDictTypes, handleSelectDictType, handleSwitchMode,
    openNewDictItem, openEditDictItem, cancelDictItemForm, handleSaveDictItem, handleDeleteDictItem,
  };
}
