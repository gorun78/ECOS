/**
 * SdResourcePickerModal.tsx — 资源 picker 弹层
 *
 * 选中某类资源后弹出，从 fetchSandboxAvailable 拉的可用项中 pick 一项 →
 * addResourceNode + 自动 bind edge。
 *
 * @license Apache-2.0
 */

import { useMemo, useState } from "react";
import React from "react";
import { X, Loader2 } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";

export interface SdResourcePickerItem {
  id: string;
  name: string;
  status?: string;
  childCount?: number;
}

export interface SdResourcePickerState {
  items: SdResourcePickerItem[];
  loading: boolean;
  error?: string;
  i18nP: `scenario.sandbox.palette.${string}`;
}

interface SdResourcePickerModalProps {
  state: SdResourcePickerState;
  onClose: () => void;
  onPick: (item: SdResourcePickerItem) => void;
}

const SdResourcePickerModal: React.FC<SdResourcePickerModalProps> = ({
  state,
  onClose,
  onPick,
}) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [q, setQ] = useState("");

  const filtered = useMemo(
    () =>
      q
        ? state.items.filter((it) => it.name.toLowerCase().includes(q.toLowerCase()))
        : state.items,
    [q, state.items]
  );

  return (
    <div
      className={`fixed inset-0 z-50 flex items-center justify-center ${styles.overlayBg}`}
      onClick={onClose}
    >
      <div
        className={`w-[520px] max-h-[640px] rounded-xl border shadow-2xl ${styles.cardBg} ${styles.cardBorder} flex flex-col`}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          className={`px-4 py-3 border-b ${styles.cardBorder} flex items-center justify-between`}
        >
          <h3 className={`text-sm font-bold ${styles.cardText}`}>
            {t("scenario.sandbox.modal.pick", { cat: t(state.i18nP) })}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className={`p-1 rounded cursor-pointer ${styles.sidebarHoverBg}`}
            title={t("scenario.sandbox.action.remove")}
          >
            <X size={14} className={styles.cardText} />
          </button>
        </div>
        <div className={`px-4 py-2.5 border-b ${styles.cardBorder}`}>
          <input
            autoFocus
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={t("scenario.sandbox.resource.filter")}
            className={`w-full px-3 py-1.5 rounded text-xs ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} outline-none`}
          />
        </div>
        <div className="flex-1 overflow-y-auto p-2">
          {state.loading ? (
            <div className={`flex justify-center py-10 ${styles.muted}`}>
              <Loader2 size={18} className="animate-spin" />
            </div>
          ) : state.error ? (
            <div className={`px-3 py-2 text-xs ${styles.dangerText}`}>
              {t("scenario.sandbox.resource.loadFail")}: {state.error}
            </div>
          ) : filtered.length === 0 ? (
            <div className={`px-3 py-3 text-xs ${styles.muted}`}>
              {t("scenario.sandbox.resource.empty")}
            </div>
          ) : (
            <div className="space-y-1">
              {filtered.map((it) => (
                <button
                  key={it.id}
                  type="button"
                  onClick={() => onPick(it)}
                  className={`w-full text-left px-3 py-2 rounded border cursor-pointer ${styles.cardBorder} ${styles.sidebarHoverBg}`}
                >
                  <div className={`text-xs font-semibold ${styles.cardText} truncate`}>
                    {it.name}
                  </div>
                  <div className={`text-[10px] font-mono ${styles.muted} truncate`}>
                    {it.id}
                    {typeof it.childCount === "number" && (
                      <span className="ml-2">
                        {t("scenario.sandbox.drawer.children")}: {it.childCount}
                      </span>
                    )}
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
        <div className={`px-4 py-2 text-[10px] ${styles.muted} border-t ${styles.cardBorder}`}>
          {t("scenario.sandbox.modal.hint")}
        </div>
      </div>
    </div>
  );
};

export default SdResourcePickerModal;
