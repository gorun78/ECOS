/**
 * ObjectExplorerTopBar — 实体选择 + 搜索 + 刷新/新建（自 ObjectExplorer.tsx JSX 原样迁出，H6-T4）
 * 实体列表失败 ⇒ 下拉禁用 + 错误标注 + 重试按钮（FALLBACK_ENTITIES 演示兜底已移除，无假真理）。
 * @license Apache-2.0
 */

import type { Dispatch, SetStateAction } from "react";
import { Search, Plus, X, RefreshCw, ChevronDown, AlertCircle } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import type { EntityListItem } from "../../services/ontologyApi";

interface ObjectExplorerTopBarProps {
  entityCode: string;
  entityList: EntityListItem[];
  entityListLoading: boolean;
  entityListError: boolean;
  onRetryEntityList: () => void;
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
  entityListLoading,
  entityListError,
  onRetryEntityList,
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
  const { t } = useLanguage();

  const selectOptions = entityList.length > 0
    ? entityList.map(e => e.code)
    : (entityCode ? [entityCode] : []);

  return (
    <div className={`shrink-0 ${styles.inputBg} border-b ${styles.cardBorder} px-4 py-3 flex items-center gap-3`}>
      {/* Entity selector dropdown */}
      <div className="relative">
        <select
          value={entityCode}
          onChange={(e) => handleEntityChange(e.target.value)}
          disabled={entityListLoading || entityListError || selectOptions.length === 0}
          className={`appearance-none rounded-lg px-3 py-2 pr-8 text-xs font-semibold outline-none focus:ring-1 focus:ring-current cursor-pointer transition disabled:cursor-not-allowed disabled:opacity-60 ${styles.appBg} border ${styles.cardBorder} ${styles.cardText}`}
        >
          {selectOptions.length === 0 ? (
            <option value="">
              {entityListLoading ? t("dw.objectExplorer.entity.loading") : t("dw.objectExplorer.entity.unavailable")}
            </option>
          ) : (
            selectOptions.map(ec => (
              <option key={ec} value={ec}>{ec}</option>
            ))
          )}
        </select>
        <ChevronDown className={`absolute right-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 ${styles.cardTextMuted} pointer-events-none`} />
      </div>

      {/* Entity list error: annotation + retry（不再回退演示实体） */}
      {entityListError && !entityListLoading && (
        <button type="button"
          onClick={onRetryEntityList}
          className={`shrink-0 flex items-center gap-1.5 text-[10px] font-semibold px-2.5 py-1.5 rounded-lg border transition hover:opacity-80 ${styles.dangerBg} ${styles.dangerText} border-current`}
          title={t("dw.objectExplorer.entity.retryHint")}
        >
          <AlertCircle className="w-3 h-3" />
          {t("dw.objectExplorer.entity.loadFailed")}
          <RefreshCw className="w-2.5 h-2.5" />
          {t("dw.objectExplorer.entity.retry")}
        </button>
      )}

      {/* Search input */}
      <div className={`flex-1 max-w-md rounded-lg px-3 py-2 flex items-center gap-2 text-xs transition focus-within:ring-1 focus-within:ring-current ${styles.appBg} border ${styles.cardBorder}`}>
        <Search className={`w-3.5 h-3.5 ${styles.cardTextMuted} shrink-0`} />
        <input
          type="text"
          value={searchQ}
          onChange={e => setSearchQ(e.target.value)}
          onKeyDown={e => e.key === "Enter" && handleSearch()}
          placeholder={t("dw.objectExplorer.searchPlaceholder")}
          className={`bg-transparent border-0 outline-none w-full ${styles.cardText}`}
        />
        {searchQ && (
          <button type="button" onClick={() => { setSearchQ(""); loadObjects(entityCode, 1, ""); }} className={`${styles.cardTextMuted} hover:opacity-70 rounded`}>
            <X className="w-3 h-3" />
          </button>
        )}
      </div>

      {/* Refresh + Create */}
      <div className="flex items-center gap-1.5">
        <button type="button"
          onClick={() => loadObjects(entityCode, currentPage, searchQ)}
          className={`p-2 ${styles.cardTextMuted} hover:opacity-70 ${styles.sidebarActiveBg} rounded-lg transition`}
          title={t("dw.objectExplorer.refresh")}
        >
          <RefreshCw className="w-3.5 h-3.5" />
        </button>
        <button type="button"
          onClick={() => { setShowForm("create"); setFormData({}); }}
          disabled={entityListLoading || entityListError}
          className={`${styles.accentBg} ${styles.accentText} hover:opacity-90 rounded-lg px-3 py-2 text-xs font-semibold flex items-center gap-1.5 transition shrink-0 disabled:opacity-50 disabled:cursor-not-allowed`}
          title={entityListError ? t("dw.objectExplorer.entity.createBlockedHint") : undefined}
        >
          <Plus className="w-3.5 h-3.5" />{t("dw.objectExplorer.create")}
        </button>
      </div>
    </div>
  );
}
