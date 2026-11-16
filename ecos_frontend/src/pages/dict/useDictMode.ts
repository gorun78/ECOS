import { useState, useCallback, useRef } from "react";
import {
  listDictTypes, getDictItems, createDictItem,
  updateDictItem, deleteDictItem, fetchDictSubsystems,
  type DictType, type DictItem,
} from "../../api";

export function useDictMode(
  showToast: (type: "success" | "error", msg: string) => void,
  t: (key: string, params?: Record<string, string | number>) => string,
  // deleteTarget 由 DictManager 单点持有（同 useTableMode 的说明）
  deleteTarget: { type: "table" | "column" | "dictItem"; id: string; name: string } | null,
  setDeleteTarget: (t: { type: "table" | "column" | "dictItem"; id: string; name: string } | null) => void,
) {
  const [viewMode, setViewMode] = useState<"table" | "dict">("dict");
  const [dictTypes, setDictTypes] = useState<DictType[]>([]);
  const [dictLoading, setDictLoading] = useState(false);
  const [selectedDictType, setSelectedDictType] = useState<string | null>(null);
  const [dictItems, setDictItems] = useState<DictItem[]>([]);
  const [dictSearch, setDictSearch] = useState("");
  // 右侧字典项列表的搜索词与左树类型搜索分开：共用一个词时，
  // 搜类型名（如 e2e_ui_dict_type_x）必然把右表过滤成空，看起来像「选中类型却没有字典项」。
  const [itemSearch, setItemSearch] = useState("");
  const [dictItemFormOpen, setDictItemFormOpen] = useState(false);
  const [dictItemForm, setDictItemForm] = useState<{
    editCode?: string; dictCode: string; extValue: string;
    dictLabel: string; status: string; sortOrder: string; description: string;
  }>({ dictCode: "", extValue: "", dictLabel: "", status: "active", sortOrder: "", description: "" });
  const [subsystemGroups, setSubsystemGroups] = useState<Record<string, DictType[]>>({});
  // 展开态：默认展开「后端实际返回的分组」而非写死 G1~G5。
  // 后端把无 subsystem 的字典归到 G0（DictController#listBySubsystem），写死 G1~G5
  // 会让新建字典类型默认藏在折叠的 G0 里，用户看不到自己刚建的项。
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  const groupsInitializedRef = useRef(false);

  const toggleGroup = (g: string) => {
    setExpandedGroups(prev => { const next = new Set(prev); next.has(g) ? next.delete(g) : next.add(g); return next; });
  };

  const loadDictTypes = useCallback(async () => {
    setDictLoading(true);
    try {
      const groups = await fetchDictSubsystems();
      setSubsystemGroups(groups);
      // 首次加载把后端实际返回的分组全部展开（含兜底 G0）；用户后续的折叠操作不再被覆盖。
      if (!groupsInitializedRef.current) {
        groupsInitializedRef.current = true;
        setExpandedGroups(new Set(Object.keys(groups)));
      }
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
    setSelectedDictType(dictType); setDictItemFormOpen(false); setItemSearch("");
    loadDictItems(dictType);
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
    !itemSearch ||
    item.dictLabel.toLowerCase().includes(itemSearch.toLowerCase()) ||
    item.dictCode.toLowerCase().includes(itemSearch.toLowerCase()) ||
    item.extValue?.toLowerCase().includes(itemSearch.toLowerCase())
  );

  return {
    viewMode, dictTypes, dictLoading, selectedDictType, dictItems, dictSearch, itemSearch,
    dictItemFormOpen, dictItemForm, subsystemGroups, expandedGroups, deleteTarget, filteredDictItems,
    setViewMode, setDictSearch, setItemSearch, setDictItemForm,
    toggleGroup, loadDictTypes, handleSelectDictType, handleSwitchMode,
    openNewDictItem, openEditDictItem, cancelDictItemForm, handleSaveDictItem, handleDeleteDictItem,
  };
}
