/**
 * buildObjectColumns — ObjectExplorer 表格列构造（自 ObjectExplorer.tsx 的 useMemo 体原样迁出，H6-T4）
 * 纯函数：useMemo 仍留在父组件同一位置，依赖数组不变。
 * @license Apache-2.0
 */

import type { ColumnConfig } from "../../components/common/DataTable";
import type { ThemeStyles } from "../../components/ThemeContext";
import type { ObjectData, SchemaProperty } from "../../api";
import { STATUS_COLORS } from "./helpers";

export function buildObjectColumns(schema: SchemaProperty[], styles: ThemeStyles): ColumnConfig<ObjectData>[] {
  const cols: ColumnConfig<ObjectData>[] = [
    {
      key: "id",
      label: "ID",
      width: "100px",
      render: (_, record) => (
        <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>{record.id?.slice(0, 12)}</span>
      ),
    },
    {
      key: "status",
      label: "状态",
      width: "80px",
      render: (val) => (
        <span className={`text-[10px] px-1.5 py-0.5 rounded border font-semibold ${STATUS_COLORS[val] || STATUS_COLORS.Draft}`}>
          {val || "Draft"}
        </span>
      ),
    },
  ];

  if (schema.length > 0) {
    const schemaCols: ColumnConfig<ObjectData>[] = schema.map(prop => ({
      key: prop.code,
      label: prop.name || prop.code,
      render: (val: unknown) => (
        <span className="truncate block max-w-[180px]" title={val != null ? String(val) : ""}>
          {val != null ? String(val) : <span className={`${styles.cardTextMuted} italic`}>—</span>}
        </span>
      ),
    }));
    // Insert schema columns after ID
    cols.splice(1, 0, ...schemaCols);
  } else {
    // Fallback columns
    cols.splice(1, 0,
      { key: "name", label: "名称", render: (val: string) => <span className={`font-medium ${styles.cardText} truncate block max-w-[160px]`}>{val || "—"}</span> },
      { key: "code", label: "编码", render: (val: string) => <span className={`font-mono text-[10px] truncate block max-w-[120px] ${styles.cardText}`}>{val || "—"}</span> },
    );
  }

  cols.push({
    key: "createdAt",
    label: "创建时间",
    width: "140px",
    render: (val: string) => (
      <span className={`text-[10px] ${styles.cardTextMuted}`}>
        {val ? val.replace("T", " ").slice(0, 19) : "—"}
      </span>
    ),
  });

  return cols;
}
