/* Extracted from ConnectionsTab.tsx */
import React from 'react';
import LucideIcon from '../../LucideIcon';
import type { DataConnection } from '../../types';
import { useTheme } from "../../../../components/ThemeContext";
import { useLanguage } from "../../../../components/LanguageContext";

interface ConnectionListPanelProps {
  connections: DataConnection[];
  selectedConnId: string;
  setSelectedConnId: (v: string) => void;
  setEditingConn: (v: DataConnection | null) => void;
  handleDelete: (id: string, name: string) => void;
  deletingId: string | null;
  setShowAddConn: (v: boolean) => void;
  t: (key: string) => string;
}

/** 左侧数据源列表面板（卡片列表 + 新增入口） */
export default function ConnectionListPanel({
  connections, selectedConnId, setSelectedConnId, setEditingConn, handleDelete,
  deletingId, setShowAddConn, t,
}: ConnectionListPanelProps) {
  const { styles } = useTheme();
  const { t: tt } = useLanguage();

  return (
  <div className={`w-72 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col overflow-hidden shrink-0`}>
    <div className={`p-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}/40`}>
      <h3 className={`text-xs font-bold ${styles.cardText}`}>{t("dw.conn.title")}</h3>
      <button type="button"
        onClick={() => setShowAddConn(true)}
        className={`p-1 rounded ${styles.accentBg} ${styles.cardText} ${styles.accentHover} text-xs flex items-center gap-1 cursor-pointer font-medium`}
      >
        <LucideIcon name="Plus" size={12} />
        <span>{t("dw.txt.30f7dd")}</span>
      </button>
    </div>

    <div className="flex-1 overflow-y-auto p-2 space-y-1">
      {connections.length === 0 && (
        <div className={`text-center py-8 ${styles.cardTextMuted} text-xs`}>{t("dw.conn.empty")}</div>
      )}
      {connections.map(conn => {
        const isSelected = selectedConnId === conn.id;
        return (
          // 用 div[role=button] 取代原 <button>：HTML 严禁 <button> 内嵌 <button>，
          // 否则 React 19 会报 hydration/nesting 警告，且会破坏内层编辑/删除
          // 按钮的事件委托，导致「数据源卡片点不动」。
          <div
            key={conn.id}
            role="button"
            tabIndex={0}
            onClick={() => setSelectedConnId(conn.id)}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                setSelectedConnId(conn.id);
              }
            }}
            className={`w-full text-left p-3 rounded-lg border transition-all text-xs flex flex-col gap-1.5 cursor-pointer focus:outline-none ${
              isSelected
                ? `${styles.badgeBg} ${styles.accentBorder} shadow-2xs`
                : `${styles.cardBorder} hover:${styles.appBg}`
            }`}
          >
            <div className="flex justify-between items-center">
              <span className={`font-semibold ${styles.cardText} truncate pr-2`}>{conn.name}</span>
              <div className="flex items-center gap-1">
                <button type="button"
                  onClick={(e) => { e.stopPropagation(); setEditingConn(conn); }}
                  className={`p-1 rounded ${styles.cardTextMuted} hover:${styles.accentText} transition-colors cursor-pointer`}
                  title={tt('dw.conn.edit')}
                >
                  <LucideIcon name="Edit3" size={11} />
                </button>
                <button type="button"
                  onClick={(e) => { e.stopPropagation(); handleDelete(conn.id, conn.name); }}
                  disabled={deletingId === conn.id}
                  className={`p-1 rounded ${styles.cardTextMuted} hover:${styles.dangerText} transition-colors cursor-pointer disabled:opacity-50`}
                  title={tt('dw.conn.delete')}
                >
                  <LucideIcon name="Trash2" size={11} />
                </button>
                <span className={`h-2 w-2 rounded-full ${
                  conn.status === 'connected' ? styles.successBg :
                  conn.status === 'error' ? styles.dangerBg : styles.warningBg
                }`} title={conn.status} />
              </div>
            </div>
            <div className={`flex justify-between text-[10px] ${styles.cardTextMuted} font-mono`}>
              <span>{t("dw.type")} {conn.type.toUpperCase()}</span>
              <span>{conn.tablesAvailable.length} {t("dw.tablesDirs")}</span>
            </div>
          </div>
        );
      })}
    </div>
  </div>
  );
}
