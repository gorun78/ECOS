/**
 * engineConfigModel — 平台治理「引擎配置」页签的纯数据/纯函数层（无 React 依赖）。
 *
 * 从 EngineConfigTab 抽出，目的是让「取数 / 归一 / 分组 / 搜索 / 脱敏 / 徽标语义」
 * 都能被单测直接钉住，而不必渲染整页。展示组件只消费本文件导出的类型与纯函数。
 *
 * 数据事实（2026-11-25 对 :8080 live 实测 188 项 / 21 分组）：
 *   - GET /api/v1/system/config        → data.data[] 扁平行（config_group/config_key/config_value/...）
 *   - GET /api/v1/system/config/audit  → data.items[] 含 is_consumed / consumed_by（**只有审计口才有**）
 * 所以「是否被代码消费」必须来自审计接口；配置列表里不存在该字段，不得臆造为「否」。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

export interface SysConfigItem {
  key: string;
  value: string;
  label: string;
  labelZh: string;
  description: string;
  descriptionZh: string;
  group: string;
  /** 后端形态不统一（boolean/BOOLEAN、int/INTEGER/float 混用），统一小写归一后再判控件 */
  type: string;
  options?: string[];
  impactScope?: string;
  edition?: string;
  default_value?: string;
  sort_order: number;
}

export interface SysConfigGrouped {
  group: string;
  items: SysConfigItem[];
}

/** 审计口返回的消费态（与配置行以 key 关联）。 */
export interface ConfigConsumption {
  consumed: boolean;
  consumedBy: string[];
}

export type ConsumptionMap = Readonly<Record<string, ConfigConsumption>>;

/** 控件类别：布尔开关 / 枚举选择 / 数值 / 密文 / 普通文本。 */
export type ValueKind = "boolean" | "enum" | "number" | "secret" | "text";

/** 单条配置在「已配置值 vs 后端默认值」上的偏离状态。 */
export type DeltaState = "none" | "override" | "empty";

const SECRET_KEY_RE = /(password|passwd|secret|credential|private[_-]?key|access[_-]?key)/i;
/**
 * 「策略/规则」语义的键名 token —— 即便含 password/secret 字样，值也不是凭据本体
 * （如 password_require_digit 是布尔策略、password_min_length 是数值）。
 * 必须按 `._-` 分隔符整词匹配：否则 "min" 会命中 "minio"，把真密钥反判成普通文本。
 * 该分支只作用于 key 启发式，不影响 config_type=password 的显式密文判定。
 */
const POLICY_KEY_RE = /(?:^|[._-])(require|requires|policy|expire|expires|expiry|history|attempt|attempts|lockout|complexity|enable|enabled|allowed|length)(?:[._-]|$)/i;
const SECRET_TYPE = new Set(["password", "secret"]);

/** 类型归一：后端的 int/INTEGER/float/BOOL 混写统一到小写语义族。 */
export function normalizeType(raw: string | undefined): string {
  return String(raw ?? "").trim().toLowerCase();
}

export function isNumericType(type: string): boolean {
  return ["number", "int", "integer", "float", "double", "long", "decimal"].includes(normalizeType(type));
}

export function isBooleanType(type: string): boolean {
  return ["boolean", "bool"].includes(normalizeType(type));
}

export function isEnumType(type: string): boolean {
  return normalizeType(type) === "enum";
}

/**
 * 判定控件类别。类型优先，键名启发式只在「类型本身说不清是不是密文」时兜底。
 *
 * 教训（2026-11-25 live 实测）：不能一看到 key 含 password 就当密文——
 * `password_require_digit`（BOOLEAN）、`password_min_length`（number）这类
 * 密码策略项会被误打码成 ••••，语义完全丢失。故先按类型定族，只有 string/text/空
 * 这类未知族才用键名猜（如 dw.storage.minio.secret_key 类型写作 string）。
 */
export function valueKind(item: SysConfigItem): ValueKind {
  const t = normalizeType(item.type);
  if (SECRET_TYPE.has(t)) return "secret";
  if (isBooleanType(t)) return "boolean";
  if (isEnumType(t)) return "enum";
  if (isNumericType(t)) return "number";
  if (POLICY_KEY_RE.test(item.key)) return "text";
  if (SECRET_KEY_RE.test(item.key)) return "secret";
  return "text";
}

/** 布尔语义值：只有真值串才算 true，其余（含空）按 false 展示，不猜写入意图。 */
export function boolValue(value: string): boolean {
  const v = normalizeType(value);
  return v === "true" || v === "1" || v === "yes" || v === "on";
}

/**
 * 与后端 default_value 的偏离判定。
 * 口径：后端未提供默认值 → none（无从比较，不臆断「已修改」）；
 *       值为空 → empty（缺值，比偏离更该提示）；值≠默认 → override。
 */
export function deltaState(item: SysConfigItem): DeltaState {
  const def = item.default_value ?? "";
  if (def === "") return "none";
  const value = item.value ?? "";
  if (value === "") return "empty";
  return value === def ? "none" : "override";
}

/**
 * 脱敏展示：密文只留首尾少量字符。极短值整体打码（长度本身就是秘密的一部分）。
 * 返回 null 表示调用方应显示「未设置」。
 */
export function maskSecret(value: string): string | null {
  if (!value) return null;
  if (value.length <= 6) return "•".repeat(value.length);
  return `${value.slice(0, 2)}${"•".repeat(Math.min(8, value.length - 4))}${value.slice(-2)}`;
}

/** 分组排序权重：引擎/核心域在前，wagent.* 细分族按字母序垫后（21 组不至于迷宫）。 */
const GROUP_ORDER: ReadonlyArray<string> = [
  "global",
  "security",
  "llm",
  "ai-engine",
  "cognitive",
  "knowledge",
  "ontology-engine",
  "data-engine",
  "storage",
  "cache",
  "event",
  "g1-data-foundation",
  "g2-biz-semantics",
  "g3-operations",
  "g4-agent",
  "fc.backtest",
];

function groupRank(group: string): number {
  const i = GROUP_ORDER.indexOf(group);
  return i === -1 ? GROUP_ORDER.length : i;
}

/** 稳定排序：先按已知权重，再按字母序，保证同数据两次渲染顺序一致。 */
export function sortGroups(groups: SysConfigGrouped[]): SysConfigGrouped[] {
  return [...groups].sort((a, b) => {
    const d = groupRank(a.group) - groupRank(b.group);
    return d !== 0 ? d : a.group.localeCompare(b.group);
  });
}

/** 组内排序：按后端 sort_order，其次按 key 字母序（同一 sort_order 时不留随机序）。 */
export function sortItems(items: SysConfigItem[]): SysConfigItem[] {
  return [...items].sort((a, b) => {
    if (a.sort_order !== b.sort_order) return a.sort_order - b.sort_order;
    return a.key.localeCompare(b.key);
  });
}

export interface SearchOutcome {
  groups: SysConfigGrouped[];
  matchedItems: number;
}

/**
 * 关键字过滤：命中 key / 中文标签 / 英文标签 / 描述 / 值 任一即保留。
 * 返回过滤后的分组（空组剔除）与命中条目数，供页头显示「N / M 项」。
 */
export function searchConfigs(groups: SysConfigGrouped[], keyword: string): SearchOutcome {
  const q = keyword.trim().toLowerCase();
  if (!q) {
    return {
      groups,
      matchedItems: groups.reduce((n, g) => n + g.items.length, 0),
    };
  }
  const hits = (item: SysConfigItem): boolean => {
    const haystack = [item.key, item.labelZh, item.label, item.description, item.value];
    return haystack.some((s) => String(s ?? "").toLowerCase().includes(q));
  };
  const filtered: SysConfigGrouped[] = [];
  let matchedItems = 0;
  for (const g of groups) {
    const items = g.items.filter(hits);
    if (items.length > 0) {
      filtered.push({ group: g.group, items });
      matchedItems += items.length;
    }
  }
  return { groups: filtered, matchedItems };
}

/** 页头统计：条目 / 分组 / 已消费 / 偏离默认 四数（全部来自真实数据）。 */
export interface ConfigStats {
  items: number;
  groups: number;
  consumed: number;
  overrides: number;
}

export function computeStats(
  groups: SysConfigGrouped[],
  consumption: ConsumptionMap,
): ConfigStats {
  let items = 0;
  let consumed = 0;
  let overrides = 0;
  for (const g of groups) {
    for (const item of g.items) {
      items += 1;
      if (consumption[item.key]?.consumed) consumed += 1;
      if (deltaState(item) === "override") overrides += 1;
    }
  }
  return { items, groups: groups.length, consumed, overrides };
}

/** 分组在过滤后的命中数（组头徽标用）。 */
export function groupConsumedCount(items: SysConfigItem[], consumption: ConsumptionMap): number {
  return items.reduce((n, item) => n + (consumption[item.key]?.consumed ? 1 : 0), 0);
}
