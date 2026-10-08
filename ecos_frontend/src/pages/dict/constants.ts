// ── DictManager constants & types ──

export const STATUS_OPTIONS: Array<{ value: string; labelKey: string }> = [
  { value: "", labelKey: "platform.dictionary.status.all" },
  { value: "DRAFT", labelKey: "platform.dictionary.draft" },
  { value: "PUBLISHED", labelKey: "platform.dictionary.published" },
  { value: "DEPRECATED", labelKey: "platform.dictionary.status.deprecated" },
];

export const STATUS_META: Record<string, { labelKey: string; bg: string; text: string }> = {
  DRAFT:      { labelKey: "platform.dictionary.draft",      bg: "bg-slate-100",  text: "text-slate-600" },
  PUBLISHED:  { labelKey: "platform.dictionary.published",  bg: "bg-green-50",   text: "text-green-600" },
  DEPRECATED: { labelKey: "platform.dictionary.status.deprecated", bg: "bg-amber-50",   text: "text-amber-600" },
};

// note: `value` is the raw API payload for the `source` column — must stay byte-identical to existing stored
// data; `labelKey` is display-only. Change `value` only after a data migration.
export const SOURCE_OPTIONS: Array<{ value: string; labelKey?: string }> = [
  { value: "MySQL" },
  { value: "PostgreSQL" },
  { value: "Oracle" },
  { value: "Hive" },
  { value: "ClickHouse" },
  { value: "其他", labelKey: "platform.dictionary.source.other" },
];

export const SQL_TYPES = [
  "VARCHAR", "CHAR", "TEXT", "LONGTEXT",
  "INT", "BIGINT", "SMALLINT", "TINYINT",
  "DECIMAL", "FLOAT", "DOUBLE",
  "DATE", "DATETIME", "TIMESTAMP",
  "BOOLEAN", "JSON", "BLOB",
];

export const COLUMN_TYPE_CATEGORIES: Record<string, { labelKey: string; types: string[] }> = {
  string:   { labelKey: "platform.dictionary.coltypes.string",   types: ["VARCHAR", "CHAR", "TEXT", "LONGTEXT"] },
  number:   { labelKey: "platform.dictionary.coltypes.number",   types: ["INT", "BIGINT", "SMALLINT", "TINYINT", "DECIMAL", "FLOAT", "DOUBLE"] },
  datetime: { labelKey: "platform.dictionary.coltypes.datetime", types: ["DATE", "DATETIME", "TIMESTAMP"] },
  other:    { labelKey: "platform.dictionary.coltypes.other",    types: ["BOOLEAN", "JSON", "BLOB"] },
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

export const G1_G5_LABELS: Record<string, { labelKey: string; color: string; border: string; bg: string }> = {
  G1: { labelKey: "platform.dictionary.group.G1", color: "text-blue-700", border: "border-blue-300", bg: "bg-blue-50" },
  G2: { labelKey: "platform.dictionary.group.G2", color: "text-emerald-700", border: "border-emerald-300", bg: "bg-emerald-50" },
  G3: { labelKey: "platform.dictionary.group.G3", color: "text-purple-700", border: "border-purple-300", bg: "bg-purple-50" },
  G4: { labelKey: "platform.dictionary.group.G4", color: "text-amber-700", border: "border-amber-300", bg: "bg-amber-50" },
  G5: { labelKey: "platform.dictionary.group.G5", color: "text-slate-700", border: "border-slate-300", bg: "bg-slate-50" },
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
