/**
 * ObjectExplorerFormModal — 新建/编辑对象弹窗（自 ObjectExplorer.tsx JSX 原样迁出，H6-T4）
 * @license Apache-2.0
 */

import type { Dispatch, SetStateAction } from "react";
import { Plus, Edit3, X } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import type { SchemaProperty } from "../../api";
import { FormField } from "./ObjectExplorerFormField";

interface ObjectExplorerFormModalProps {
  showForm: "create" | "edit";
  entityCode: string;
  schema: SchemaProperty[];
  formData: Record<string, string>;
  setFormData: Dispatch<SetStateAction<Record<string, string>>>;
  setShowForm: Dispatch<SetStateAction<"create" | "edit" | null>>;
  handleCreate: () => Promise<void>;
  handleEdit: () => Promise<void>;
}

export function ObjectExplorerFormModal({
  showForm,
  entityCode,
  schema,
  formData,
  setFormData,
  setShowForm,
  handleCreate,
  handleEdit,
}: ObjectExplorerFormModalProps) {
  const { styles } = useTheme();

  return (
    <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.overlayBg}`} onClick={() => setShowForm(null)}>
      <div
        className={`${styles.cardBg} rounded-xl shadow-2xl w-full sm:w-[460px] max-h-[85vh] overflow-y-auto animate-in zoom-in-95 mx-4 sm:mx-auto`}
        onClick={e => e.stopPropagation()}
      >
        <div className={`p-4 border-b ${styles.cardBorder} flex items-center justify-between sticky top-0 ${styles.cardBg} z-10`}>
          <h3 className={`text-sm font-bold ${styles.cardText} flex items-center gap-2`}>
            {showForm === "create" ? (
              <><Plus className="w-4 h-4 text-blue-500" />新建 {entityCode}</>
            ) : (
              <><Edit3 className="w-4 h-4 text-blue-500" />编辑 {entityCode}</>
            )}
          </h3>
          <button onClick={() => setShowForm(null)} className={`${styles.sidebarHoverBg} rounded p-1 transition`}>
            <X className={`w-4 h-4 ${styles.cardTextMuted}`} />
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
        <div className={`p-4 border-t ${styles.cardBorder} flex gap-2 justify-end sticky bottom-0 ${styles.cardBg}`}>
          <button
            onClick={() => setShowForm(null)}
            className={`px-4 py-2 text-xs font-semibold ${styles.cardTextMuted} ${styles.sidebarHoverBg} rounded-lg transition`}
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
  );
}
