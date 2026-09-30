/**
 * ObjectExplorerPropertiesTab — 详情「基本属性」页签（自 ObjectExplorer.tsx JSX 原样迁出，H6-T4）
 * @license Apache-2.0
 */

import { useTheme } from "../../components/ThemeContext";
import type { ObjectData, SchemaProperty } from "../../api";

export function PropertiesTab({ detail, schema }: { detail: ObjectData; schema: SchemaProperty[] }) {
  const { styles } = useTheme();

  return (
    <div className="space-y-3">
      {schema.map(prop => {
        const val = detail[prop.code];
        return (
          <div key={prop.code} className="group">
            <label className={`text-[10px] font-semibold ${styles.cardTextMuted} uppercase tracking-wider`}>
              {prop.name || prop.code}
              {prop.required && <span className="text-red-400 ml-1">*</span>}
            </label>
            <div className={`text-xs ${styles.cardText} mt-0.5 ${styles.appBg} border ${styles.cardBorder} rounded px-2.5 py-1.5 font-mono break-all`}>
              {val != null ? String(val) : <span className={`${styles.cardTextMuted} italic`}>未设置</span>}
            </div>
          </div>
        );
      })}
      {schema.length === 0 && detail && Object.entries(detail)
        .filter(([k]) => !["id", "entityCode", "status", "createdAt", "updatedAt", "relations", "timeline"].includes(k))
        .map(([k, v]) => (
          <div key={k}>
            <label className={`text-[10px] font-semibold ${styles.cardTextMuted} uppercase tracking-wider`}>{k}</label>
            <div className={`text-xs ${styles.cardText} mt-0.5 ${styles.appBg} border ${styles.cardBorder} rounded px-2.5 py-1.5 font-mono break-all`}>
              {v != null ? String(v) : <span className={`${styles.cardTextMuted} italic`}>—</span>}
            </div>
          </div>
        ))}
    </div>
  );
}
