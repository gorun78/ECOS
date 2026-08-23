// ── DictManager constants & types ──

export const STATUS_OPTIONS = [
  { value: "", label: "全部" },
  { value: "DRAFT", label: "Draft" },
  { value: "PUBLISHED", label: "Published" },
  { value: "DEPRECATED", label: "Deprecated" },
];

export const STATUS_META: Record<string, { label: string; bg: string; text: string }> = {
  DRAFT:      { label: "草稿",     bg: "bg-slate-100",  text: "text-slate-600" },
  PUBLISHED:  { label: "已发布",   bg: "bg-green-50",   text: "text-green-600" },
  DEPRECATED: { label: "已废弃",   bg: "bg-amber-50",   text: "text-amber-600" },
};

export const SOURCE_OPTIONS = ["MySQL", "PostgreSQL", "Oracle", "Hive", "ClickHouse", "其他"];

export const SQL_TYPES = [
  "VARCHAR", "CHAR", "TEXT", "LONGTEXT",
  "INT", "BIGINT", "SMALLINT", "TINYINT",
  "DECIMAL", "FLOAT", "DOUBLE",
  "DATE", "DATETIME", "TIMESTAMP",
  "BOOLEAN", "JSON", "BLOB",
];

export const COLUMN_TYPE_CATEGORIES: Record<string, string[]> = {
  "字符串": ["VARCHAR", "CHAR", "TEXT", "LONGTEXT"],
  "数值": ["INT", "BIGINT", "SMALLINT", "TINYINT", "DECIMAL", "FLOAT", "DOUBLE"],
  "日期时间": ["DATE", "DATETIME", "TIMESTAMP"],
  "其他": ["BOOLEAN", "JSON", "BLOB"],
};

// ── Column Form State ──
export interface ColumnFormState {
  id?: string;
  name: string;
  type: string;
  length: string;
  precision: string;
  scale: string;
  nullable: boolean;
  primaryKey: boolean;
  defaultValue: string;
  description: string;
}

export const emptyColumnForm = (): ColumnFormState => ({
  name: "",
  type: "VARCHAR",
  length: "",
  precision: "",
  scale: "",
  nullable: true,
  primaryKey: false,
  defaultValue: "",
  description: "",
});

export const G1_G5_LABELS: Record<string, { zh: string; color: string; border: string; bg: string }> = {
  G1: { zh: "G1 数据集成", color: "text-blue-700", border: "border-blue-300", bg: "bg-blue-50" },
  G2: { zh: "G2 数据治理", color: "text-emerald-700", border: "border-emerald-300", bg: "bg-emerald-50" },
  G3: { zh: "G3 数据资产", color: "text-purple-700", border: "border-purple-300", bg: "bg-purple-50" },
  G4: { zh: "G4 AI智能体", color: "text-amber-700", border: "border-amber-300", bg: "bg-amber-50" },
  G5: { zh: "G5 系统管理", color: "text-slate-700", border: "border-slate-300", bg: "bg-slate-50" },
};

// ── Column type badge color ──
export const typeBadge = (t: string) => {
  if (["VARCHAR", "CHAR", "TEXT", "LONGTEXT"].includes(t))
    return "bg-blue-50 text-blue-600";
  if (["INT", "BIGINT", "SMALLINT", "TINYINT", "DECIMAL", "FLOAT", "DOUBLE"].includes(t))
    return "bg-emerald-50 text-emerald-600";
  if (["DATE", "DATETIME", "TIMESTAMP"].includes(t))
    return "bg-purple-50 text-purple-600";
  if (t === "BOOLEAN")
    return "bg-amber-50 text-amber-600";
  return "bg-slate-100 text-slate-600";
};
