/**
 * ObjectExplorerTopBar — 实体选择 + 搜索 + 刷新/新建（自 ObjectExplorer.tsx JSX 原样迁出，H6-T4）
 * @license Apache-2.0
 */

import type { Dispatch, SetStateAction } from "react";
import { Search, Plus, X, RefreshCw, ChevronDown } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import type { EntityListItem } from "../../services/ontologyApi";
import { FALLBACK_ENTITIES } from "./helpers";

interface ObjectExplorerTopBarProps {
  entityCode: string;
  entityList: EntityListItem[];
  handleEntityChange: (ec: string) => void;
  searchQ: string;
  setSearchQ: Dispatch<SetStateAction<string>>;
  handleSearch: () => void;
  loadObjects: (ec: string, page: number, kw?: string) => Promise<void>;
  currentPage: number;
  setShowForm: Dispatch<SetStateAction<"create" | "edit" | null>>;
  setFormData: Dispatch<SetStateAction<Record<string, string>>>;
}

export function ObjectExplorerTopBar({
  entityCode,
  entityList,
  handleEntityChange,
  searchQ,
  setSearchQ,
  handleSearch,
  loadObjects,
  currentPage,
  setShowForm,
  setFormData,
}: ObjectExplorerTopBarProps) {
  const { styles } = useTheme();

  return (
    <div className={`shrink-0 ${styles.inputBg} border-b ${styles.cardBorder} px-4 py-3 flex items-center gap-3`}>
      {/* Entity selector dropdown */}
      <div className="relative">
        <select
          value={entityCode}
          onChange={(e) => handleEntityChange(e.target.value)}
          className={`appearance-none rounded-lg px-3 py-2 pr-8 text-xs font-semibold outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 cursor-pointer transition ${styles.appBg} border ${styles.cardBorder} ${styles.cardText}`}
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
        <ChevronDown className={`absolute right-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 ${styles.cardTextMuted} pointer-events-none`} />
      </div>

      {/* Search input */}
      <div className={`flex-1 max-w-md rounded-lg px-3 py-2 flex items-center gap-2 text-xs transition focus-within:border-blue-500 focus-within:ring-1 focus-within:ring-blue-500 ${styles.appBg} border ${styles.cardBorder}`}>
        <Search className={`w-3.5 h-3.5 ${styles.cardTextMuted} shrink-0`} />
        <input
          type="text"
          value={searchQ}
          onChange={e => setSearchQ(e.target.value)}
          onKeyDown={e => e.key === "Enter" && handleSearch()}
          placeholder="搜索对象..."
          className={`bg-transparent border-0 outline-none w-full ${styles.cardText}`}
        />
        {searchQ && (
          <button type="button" onClick={() => { setSearchQ(""); loadObjects(entityCode, 1, ""); }} className={`${styles.cardTextMuted} ${styles.sidebarHoverBg}`}>
            <X className="w-3 h-3" />
          </button>
        )}
      </div>

      {/* Refresh + Create */}
      <div className="flex items-center gap-1.5">
        <button type="button"
          onClick={() => loadObjects(entityCode, currentPage, searchQ)}
          className={`p-2 ${styles.cardTextMuted} ${styles.sidebarHoverBg} rounded-lg transition`}
          title="刷新"
        >
          <RefreshCw className="w-3.5 h-3.5" />
        </button>
        <button type="button"
          onClick={() => { setShowForm("create"); setFormData({}); }}
          className="bg-blue-500 hover:bg-blue-600 text-white rounded-lg px-3 py-2 text-xs font-semibold flex items-center gap-1.5 transition shrink-0"
        >
          <Plus className="w-3.5 h-3.5" />新建
        </button>
      </div>
    </div>
  );
}
