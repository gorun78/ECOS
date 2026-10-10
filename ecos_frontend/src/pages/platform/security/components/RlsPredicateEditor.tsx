/**
 * RlsPredicateEditor — RLS 谓词模板 + 绑定变量的受限编辑器。
 *
 * 设计约束（详细设计-01 B §3.1 #3 + C.2.2）：
 * 「只允许绑定变量」—— 每行片段 = 列名 + 操作符(=/>/</IN) + 一个绑定变量，
 * 绑定来源从白名单下拉选（禁自由文本 SQL）。列名与变量名均受
 * [a-zA-Z_][A-Za-z0-9_]* 校验；STATIC_LIST ≤50 项。多行用 AND 连接。
 * 这样 S-3（自由 SQL 注入策略）在 UI 层就无从发生；服务端 RlsPredicateValidator
 * 再校验一次（非法 → 400 ECOS-SEC-410，提示可绑定来源清单）。
 */
import React, { useEffect, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { useLanguage } from "../../../../components/LanguageContext";
import { useTheme } from "../../../../components/ThemeContext";
import type { RlsBinding } from "../../../../services/security";

/** 白名单来源 = 后端 BindingSource 枚举值。 */
export const BINDING_SOURCES = [
  "CONTEXT_USER_ID",
  "CONTEXT_TENANT_ID",
  "CONTEXT_ORG",
  "CONTEXT_CLEARANCE_LEVEL",
  "STATIC_LIST",
  "PROJECT_ATTRIBUTION_SUBQUERY",
] as const;
type Source = (typeof BINDING_SOURCES)[number];
type Op = "eq" | "in" | "gt" | "lt";
const OPS: Op[] = ["eq", "in", "gt", "lt"];

export interface ClauseDraft {
  id: string;
  column: string;
  op: Op;
  name: string;
  source: Source;
  staticItems?: string[];
}

const NAME_RE = /^[a-zA-Z_][a-zA-Z0-9_]*$/;
export const MAX_STATIC = 50;

/** 片段草稿 → (predicateTemplate, bindings[])，非法返回 error 码。 */
export function compileClauses(clauses: ClauseDraft[]): {
  predicateTemplate: string;
  bindings: RlsBinding[];
  error?: string;
} {
  if (clauses.length === 0) return { predicateTemplate: "", bindings: [], error: "no-clauses" };
  const seen = new Set<string>();
  const fragments: string[] = [];
  const bindings: RlsBinding[] = [];
  for (const c of clauses) {
    const col = c.column.trim();
    if (!NAME_RE.test(col)) return { predicateTemplate: "", bindings: [], error: "bad-column" };
    const nm = c.name.trim();
    if (!NAME_RE.test(nm)) return { predicateTemplate: "", bindings: [], error: "bad-binding" };
    if (seen.has(nm)) return { predicateTemplate: "", bindings: [], error: "dup-binding" };
    seen.add(nm);
    if (c.op === "in") fragments.push(`${col} IN (:${nm})`);
    else {
      const sym = c.op === "gt" ? ">" : c.op === "lt" ? "<" : "=";
      fragments.push(`${col} ${sym} :${nm}`);
    }
    if (c.source === "STATIC_LIST") {
      const items = (c.staticItems ?? []).filter((x) => x.trim().length > 0);
      if (items.length === 0) return { predicateTemplate: "", bindings: [], error: "static-empty" };
      if (items.length > MAX_STATIC) return { predicateTemplate: "", bindings: [], error: "static-too-long" };
      bindings.push({ name: nm, source: "STATIC_LIST", items });
    } else {
      bindings.push({ name: nm, source: c.source });
    }
  }
  return { predicateTemplate: fragments.join(" AND "), bindings };
}

/** 既有策略 → 片段草稿；模板无法结构化为片段时返回 null（调用方走只读）。 */
export function parseToClauses(
  predicateTemplate: string | undefined,
  bindings: RlsBinding[] | undefined
): ClauseDraft[] | null {
  if (!predicateTemplate || !predicateTemplate.trim()) return [];
  const parts = predicateTemplate.split(/(?=\sAND\s)/i).map((p) => p.trim()).filter(Boolean);
  if (!parts.length) return [];
  const byName = new Map<string, RlsBinding>();
  (bindings ?? []).forEach((b) => byName.set(b.name, b));
  const out: ClauseDraft[] = [];
  for (const p of parts) {
    let m = p.match(/^([A-Za-z_][A-Za-z0-9_]*)\s+IN\s*\(\s*:([A-Za-z_][A-Za-z0-9_]*)\s*\)$/i);
    if (m) {
      const b = byName.get(m[2]);
      if (!b) return null;
      out.push({ id: `c${out.length}`, column: m[1], op: "in", name: m[2], source: (b.source ?? "CONTEXT_USER_ID") as Source });
      continue;
    }
    m = p.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*(=|<|>)\s*:([A-Za-z_][A-Za-z0-9_]*)$/);
    if (m) {
      const b = byName.get(m[3]);
      if (!b) return null;
      const op: Op = m[2] === "<" ? "lt" : m[2] === ">" ? "gt" : "eq";
      out.push({ id: `c${out.length}`, column: m[1], op, name: m[3], source: (b.source ?? "CONTEXT_USER_ID") as Source, staticItems: b.items ? [...b.items] : [] });
      continue;
    }
    return null; // 自由文本 / 项目归属子查询模板 → 不可拆行，走只读兜底
  }
  return out;
}

/**
 * props：
 *   policyId?   编辑态时传，用于把谓词原文回填成片段草稿
 *   template    谓词模板原文（可含 :name）
 *   bindings    该模板声明的绑定
 *   onChange    编译成功（void error）时回调结构化谓词
 *   onInvalid   编译失败时回调 error 码（父组件渲染行内提示）
 */
export default function RlsPredicateEditor({
  template,
  bindings,
  policyId,
  onChange,
  onInvalid,
}: {
  template?: string;
  bindings?: RlsBinding[];
  policyId?: string | null;
  onChange: (v: { predicateTemplate: string; bindings: RlsBinding[] }) => void;
  onInvalid: (code: string | null) => void;
}) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  // 用 policyId+template 作为回填键：切策略/新建时重算一次草稿
  const sourceKey = `${policyId ?? "new"}::${template ?? ""}`;
  const [clauses, setClauses] = useState<ClauseDraft[]>(() =>
    parseToClauses(template, bindings) ?? []
  );
  const [readOnly, setReadOnly] = useState<string>(() =>
    parseToClauses(template, bindings) === null && template ? template : ""
  );

  useEffect(() => {
    const parsed = parseToClauses(template, bindings);
    if (parsed) {
      setClauses(parsed);
      setReadOnly("");
      onInvalid(parsed.length ? null : "no-clauses");
    } else if (template) {
      setReadOnly(template);
      setClauses([]);
      onInvalid(null);
    } else {
      setClauses([]);
      setReadOnly("");
      onInvalid("no-clauses");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceKey]);

  const commit = (next: ClauseDraft[]) => {
    setClauses(next);
    const compiled = compileClauses(next);
    if (compiled.error) onInvalid(compiled.error);
    else {
      onInvalid(null);
      onChange(compiled);
    }
  };

  const update = (id: string, patch: Partial<ClauseDraft>) =>
    commit(clauses.map((c) => (c.id === id ? { ...c, ...patch } : c)));

  const addClause = () =>
    commit([
      ...clauses,
      {
        id: `c${Date.now()}`,
        column: "",
        op: "eq",
        name: "",
        source: "CONTEXT_ORG",
        staticItems: [],
      },
    ]);

  const removeClause = (id: string) => commit(clauses.filter((c) => c.id !== id));

  const inputCls = `px-2 py-1 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} text-xs w-full`;
  const labelCls = `text-[10px] font-mono ${styles.muted}`;

  // 只读兜底：模板无法拆行（含项目归属子查询/存量 filter_expr）
  if (readOnly) {
    return (
      <div data-testid="rls-predicate-readonly" className={`space-y-2`}>
        <span className={labelCls}>{t("platform.security.field.predicate")}</span>
        <pre className={`px-3 py-2 rounded border ${styles.appBorder} ${styles.cardBg} ${styles.muted} text-xs font-mono break-all`}>
          {readOnly}
        </pre>
      </div>
    );
  }

  return (
    <div className="space-y-2" data-testid="rls-predicate-editor">
      <span className={labelCls}>{t("platform.security.field.predicate")}</span>
      {clauses.map((c, i) => (
        <div
          key={c.id}
          className={`grid gap-2 items-end rounded border ${styles.appBorder} p-2 ${i > 0 ? "mt-1" : ""}`}
          data-testid={`rls-clause-${i}`}
        >
          {i > 0 && (
            <div className={`text-[10px] ${styles.muted}`}>{t("platform.security.and")}</div>
          )}
          <label className={`flex flex-col gap-1 ${labelCls}`}>
            {t("platform.security.field.column")}
            <input
              value={c.column}
              onChange={(e) => update(c.id, { column: e.target.value })}
              placeholder="department_id"
              className={inputCls}
              aria-label={t("platform.security.field.column")}
            />
          </label>
          <label className={`flex flex-col gap-1 ${labelCls}`}>
            {t("platform.security.field.operator")}
            <select
              value={c.op}
              onChange={(e) => update(c.id, { op: e.target.value as Op })}
              className={inputCls}
              aria-label={t("platform.security.field.operator")}
            >
              {OPS.map((o) => (
                <option key={o} value={o}>
                  {o === "eq" ? "=" : o === "in" ? "IN" : o === "gt" ? ">" : "<"}
                </option>
              ))}
            </select>
          </label>
          <label className={`flex flex-col gap-1 ${labelCls}`}>
            {t("platform.security.field.bindVar")}
            <input
              value={c.name}
              onChange={(e) => update(c.id, { name: e.target.value })}
              placeholder="userDeptId"
              className={inputCls}
              aria-label={t("platform.security.field.bindVar")}
            />
          </label>
          <label className={`flex flex-col gap-1 ${labelCls}`}>
            {t("platform.security.field.source")}
            <select
              value={c.source}
              onChange={(e) => update(c.id, { source: e.target.value as Source })}
              className={inputCls}
              aria-label={t("platform.security.field.source")}
            >
              {BINDING_SOURCES.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </label>
          <button
            type="button"
            onClick={() => removeClause(c.id)}
            aria-label={t("platform.security.action.removeClause")}
            data-testid={`rls-clause-remove-${i}`}
            className={`inline-flex items-center px-2 py-1 rounded border ${styles.dangerBorder} ${styles.dangerText} text-xs cursor-pointer hover:opacity-80`}
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
          {c.source === "STATIC_LIST" && (
            <label className={`flex flex-col gap-1 ${labelCls}`}>
              {t("platform.security.field.staticItems")}
              <input
                value={(c.staticItems ?? []).join(",")}
                onChange={(e) =>
                  update(c.id, { staticItems: e.target.value.split(",").slice(0, MAX_STATIC) })
                }
                placeholder={"1,2,3"}
                className={inputCls}
                aria-label={t("platform.security.field.staticItems")}
              />
            </label>
          )}
        </div>
      ))}
      <button
        type="button"
        onClick={addClause}
        data-testid="rls-clause-add"
        className={`inline-flex items-center gap-1 px-2 py-1 rounded border ${styles.accentBorder} ${styles.accentText} text-xs cursor-pointer hover:opacity-80`}
      >
        <Plus className="w-3.5 h-3.5" />
        {t("platform.security.action.addClause")}
      </button>
    </div>
  );
}
