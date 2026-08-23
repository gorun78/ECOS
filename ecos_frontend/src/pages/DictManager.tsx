/**
 * DictManager — 数据字典全生命周期管理
 * 管理数据库表定义、字段元数据。左右双栏布局：
 *   左侧：表列表（搜索/筛选/新建）
 *   右侧：表详情 + 字段列表（CRUD + 状态流转）
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useCallback } from "react";
import { useLanguage } from "../components/LanguageContext";
import { Toast, DeleteConfirm } from "./dict/SharedComponents";
import { DictLeftPanel } from "./dict/DictLeftPanel";
import { TableModePanel } from "./dict/TableModePanel";
import { ColumnSection } from "./dict/ColumnSection";
import { DictModePanel } from "./dict/DictModePanel";
import { useTableMode } from "./dict/useTableMode";
import { useDictMode } from "./dict/useDictMode";

export default function DictManager() {
  const { t } = useLanguage();
  const [toast, setToast] = useState<{ type: "success" | "error"; msg: string } | null>(null);

  const showToast = useCallback((type: "success" | "error", msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3000);
  }, []);

  const table = useTableMode(showToast);
  const dict = useDictMode(showToast);

  // Merge deleteTarget from both hooks (they each have their own)
  const deleteTarget = table.deleteTarget || dict.deleteTarget;
  const setDeleteTarget = (t: { type: "table" | "column" | "dictItem"; id: string; name: string } | null) => {
    table.setDeleteTarget(t);
    dict.setDeleteTarget(t);
  };

  return (
    <div className="flex-1 bg-[#F8FAFC] flex h-full overflow-hidden font-sans">
      {toast && <Toast toast={toast} onClose={() => setToast(null)} />}

      {deleteTarget && (
        <DeleteConfirm
          targetName={deleteTarget.name}
          targetType={deleteTarget.type === "dictItem" ? "column" : deleteTarget.type}
          onConfirm={
            deleteTarget.type === "table" ? table.handleDeleteTable :
            deleteTarget.type === "dictItem" ? () => dict.handleDeleteDictItem(table.setSaving) :
            table.handleDeleteColumn
          }
          onCancel={() => setDeleteTarget(null)}
        />
      )}

      <DictLeftPanel
        viewMode={dict.viewMode}
        handleSwitchMode={dict.handleSwitchMode}
        counts={table.counts}
        search={table.search}
        handleSearchChange={table.handleSearchChange}
        statusFilter={table.statusFilter}
        handleStatusFilter={table.handleStatusFilter}
        saving={table.saving}
        handleCreate={table.handleCreate}
        loading={table.loading}
        filteredTables={table.filteredTables}
        selectedTable={table.selectedTable}
        selectTable={table.selectTable}
        setDeleteTarget={setDeleteTarget}
        dictTypes={dict.dictTypes}
        dictSearch={dict.dictSearch}
        setDictSearch={dict.setDictSearch}
        dictLoading={dict.dictLoading}
        subsystemGroups={dict.subsystemGroups}
        expandedGroups={dict.expandedGroups}
        toggleGroup={dict.toggleGroup}
        handleSelectDictType={dict.handleSelectDictType}
        selectedDictType={dict.selectedDictType}
      />

      {dict.viewMode === "table" && (
        <TableModePanel
          tableMode={table.tableMode}
          selectedTable={table.selectedTable}
          saving={table.saving}
          tableFormName={table.tableFormName}
          setTableFormName={table.setTableFormName}
          tableFormNameZh={table.tableFormNameZh}
          setTableFormNameZh={table.setTableFormNameZh}
          tableFormSchema={table.tableFormSchema}
          setTableFormSchema={table.setTableFormSchema}
          tableFormSource={table.tableFormSource}
          setTableFormSource={table.setTableFormSource}
          tableFormDesc={table.tableFormDesc}
          setTableFormDesc={table.setTableFormDesc}
          tableFormTags={table.tableFormTags}
          setTableFormTags={table.setTableFormTags}
          transitions={table.transitions}
          handleTransition={table.handleTransition}
          handleSaveTable={table.handleSaveTable}
          handleCancel={table.handleCancel}
          setDeleteTarget={setDeleteTarget}
        >
          {table.selectedTable && (
            <ColumnSection
              selectedTable={table.selectedTable}
              saving={table.saving}
              colFormOpen={table.colFormOpen}
              colForm={table.colForm}
              setColForm={table.setColForm}
              openNewColumn={table.openNewColumn}
              openEditColumn={table.openEditColumn}
              cancelColumnForm={table.cancelColumnForm}
              handleSaveColumn={table.handleSaveColumn}
              expandedColId={table.expandedColId}
              setExpandedColId={table.setExpandedColId}
              setDeleteTarget={setDeleteTarget}
            />
          )}
        </TableModePanel>
      )}

      {dict.viewMode === "dict" && (
        <DictModePanel
          selectedDictType={dict.selectedDictType}
          dictTypes={dict.dictTypes}
          openNewDictItem={dict.openNewDictItem}
          saving={table.saving}
          dictItemFormOpen={dict.dictItemFormOpen}
          dictSearch={dict.dictSearch}
          setDictSearch={dict.setDictSearch}
          dictItemForm={dict.dictItemForm}
          setDictItemForm={dict.setDictItemForm}
          handleSaveDictItem={(savingFn) => dict.handleSaveDictItem(savingFn || table.setSaving)}
          cancelDictItemForm={dict.cancelDictItemForm}
          dictLoading={dict.dictLoading}
          filteredDictItems={dict.filteredDictItems}
          openEditDictItem={dict.openEditDictItem}
          setDeleteTarget={setDeleteTarget}
        />
      )}
    </div>
  );
}
