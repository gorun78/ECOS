/**
 * RlsPolicyTab — RLS 行级安全策略（参数化谓词）列表 + 新建/编辑 + 启停 + 删除。
 * 设计：详细设计-01 B §3.1 #2/#4/#5。
 *   列表列：策略名 / 目标表 / 谓词摘要(参数化) / 角色 / 优先级 / 生效 / 更新时间。
 *   空态强调「默认 DENY」；<L3 只读并提示「需 L3」（读失败 403 → 只读）。
 *   模板非法 400(EIDOS-SEC-410) → 行内提示可绑定来源清单。
 */
import React, { useCallback, useEffect, useState } from "react";
import { Plus, Trash2, Pencil, Save, X, ShieldCheck, ShieldOff, Inbox, Lock } from "lucide-react";
import { useLanguage } from "../../../../components/LanguageContext";
import { useTheme } from "../../../../components/ThemeContext";
import { useToast } from "../../../../components/common/Toast";
import { isNoAccessError } from "../../../../api";
import RlsPredicateEditor from "../components/RlsPredicateEditor";
import {
  type RlsPolicy,
  type RlsBinding,
  listRlsPolicies,
  createRlsPolicy,
  updateRlsPolicy,
  deleteRlsPolicy,
} from "../../../../services/security";

interface Props {
  policies: RlsPolicy[];
  loading: boolean;
  error?: string;
  onRefresh: () => void;
}

const EMPTY_FORM: RlsPolicy = {
  id: "",
  policyName: "",
  tableName: "",
  predicateTemplate: "",
  bindings: [],
  priority: 0,
  enabled: true,
  description: "",
};

export default function RlsPolicyTab({ policies, loading, error, onRefresh }: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const { showToast } = useToast();

  const [readOnly, setReadOnly] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null); // "new" 或已有 id
  const [form, setForm] = useState<RlsPolicy>(EMPTY_FORM);
  const [clauseError, setClauseError] = useState<string | null>(null);
  const [bindingError, setBindingError] = useState<string | null>(null);

  // 契约签名要求 PolicyTab 持有 { policies, onSave, onToggle }——本组件内聚
  // 数据拉取 + 增删改到控制面板（无独立父 fetch，直接走 services/security）。
  useEffect(() => {
    void onRefresh();
  }, [onRefresh]);

  const handleSave = useCallback(
    async (p: RlsPolicy) => {
      try {
        if (p.id) await updateRlsPolicy(p.id, toInput(p));
        else await createRlsPolicy(toInput(p));
        setClauseError(null);
        setBindingError(null);
        setEditingId(null);
        setForm(EMPTY_FORM);
        showToast("info", t("platform.security.message.saved"));
        await onRefresh();
      } catch (e) {
        // ECOS-SEC-410 模板非法 / NoAccess（<L3）
        if (isNoAccessError(e)) {
          setReadOnly(true);
          showToast("error", t("platform.security.state.forbidden"));
          return;
        }
        const msg = String((e as Error)?.message ?? "");
        if (msg.includes("410") || /绑定|变量|模板|谓词|column|binding|clause|不允许|非法/i.test(msg)) {
          setBindingError(t("platform.security.error.illegalTemplate"));
        } else if (msg && !msg.startsWith("API ")) {
          setBindingError(msg);
        } else {
          setBindingError(t("platform.security.error.saveFailed"));
        }
      }
    },
    [onRefresh, showToast, t]
  );

  const handleToggle = useCallback(
    async (p: RlsPolicy) => {
      try {
        await updateRlsPolicy(p.id, toInput({ ...p, enabled: !p.enabled }));
        await onRefresh();
      } catch (e) {
        showToast("error", String((e as Error)?.message ?? ""));
      }
    },
    [onRefresh, showToast]
  );

  const handleDelete = useCallback(
    async (p: RlsPolicy) => {
      try {
        await deleteRlsPolicy(p.id);
        showToast("info", t("platform.security.message.deleted"));
        await onRefresh();
      } catch (e) {
        showToast("error", String((e as Error)?.message ?? ""));
      }
    },
    [onRefresh, showToast]
  );

  const startEdit = (p: RlsPolicy) => {
    setEditingId(p.id);
    setForm({ ...EMPTY_FORM, ...p });
    setClauseError(null);
    setBindingError(null);
  };
  const startNew = () => {
    setEditingId("new");
    setForm({ ...EMPTY_FORM });
    setClauseError(null);
    setBindingError(null);
  };

  const inputCls = `px-2 py-1 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} text-xs`;
  const setFormField = <K extends keyof RlsPolicy>(k: K, v: RlsPolicy[K]) =>
    setForm((prev) => ({ ...prev, [k]: v }));

  return (
    <div className="space-y-3" data-testid="rls-tab">
      {/* 顶行：空态/DENY 提示 + 新建 */}
      <div className={`flex items-center justify-between gap-2`}>
        <div className={`flex items-center gap-2 text-xs ${styles.muted}`}>
          {!readOnly ? <ShieldCheck className="w-4 h-4" /> : <ShieldOff className="w-4 h-4" />}
          <span data-testid="rls-default-deny">{t("platform.security.hint.defaultDeny")}</span>
        </div>
        {!readOnly && (
          <button
            type="button"
            onClick={startNew}
            data-testid="rls-new"
            className={`inline-flex items-center gap-1 px-2 py-1 rounded border ${styles.accentBorder} ${styles.accentText} text-xs cursor-pointer hover:opacity-80`}
          >
            <Plus className="w-3.5 h-3.5" />
            {t("platform.security.action.new")}
          </button>
        )}
      </div>

      {readOnly && (
        <div className={`flex items-center gap-2 text-xs ${styles.muted} px-1`}>
          <Lock className="w-3.5 h-3.5" />
          {t("platform.security.state.needsL3")}
        </div>
      )}

      {/* 编辑/新建面板 */}
      {editingId && !readOnly && (
        <div
          className={`rounded-md border ${styles.appBorder} ${styles.cardBg} p-3 space-y-3`}
          data-testid="rls-form"
        >
          <div className="flex items-center justify-between">
            <h4 className={`text-sm font-semibold ${styles.cardText}`}>
              {editingId === "new" ? t("platform.security.action.new") : t("platform.security.action.edit")}
            </h4>
            <button
              type="button"
              onClick={() => {
                setEditingId(null);
                setForm(EMPTY_FORM);
                setClauseError(null);
                setBindingError(null);
              }}
              aria-label={t("platform.security.action.cancel")}
              className={`p-1 border rounded ${styles.appBorder} ${styles.muted} cursor-pointer hover:opacity-80`}
            >
              <X className="w-4 h-4" />
            </button>
          </div>
          <div className="grid gap-2 sm:grid-cols-2">
            <label className={`flex flex-col gap-1 text-[10px] font-mono ${styles.muted}`}>
              {t("platform.security.field.policyName")}
              <input value={form.policyName} onChange={(e) => setFormField("policyName", e.target.value)} className={inputCls} />
            </label>
            <label className={`flex flex-col gap-1 text-[10px] font-mono ${styles.muted}`}>
              {t("platform.security.field.table")}
              <input value={form.tableName} onChange={(e) => setFormField("tableName", e.target.value)} placeholder="ecos_dw.t_order" className={inputCls} />
            </label>
            <label className={`flex flex-col gap-1 text-[10px] font-mono ${styles.muted}`}>
              {t("platform.security.field.role")}
              <input value={form.role ?? ""} onChange={(e) => setFormField("role", e.target.value)} placeholder="(留空=全体)" className={inputCls} />
            </label>
            <label className={`flex flex-col gap-1 text-[10px] font-mono ${styles.muted}`}>
              {t("platform.security.field.priority")}
              <input
                type="number"
                value={form.priority ?? 0}
                onChange={(e) => setFormField("priority", Number(e.target.value) || 0)}
                className={inputCls}
              />
            </label>
          </div>
          <RlsPredicateEditor
            policyId={editingId === "new" ? null : editingId}
            template={form.predicateTemplate}
            bindings={form.bindings}
            onChange={(v) => {
              setForm((prev) => ({ ...prev, ...v }));
              setBindingError(null);
            }}
            onInvalid={(code) => {
              setClauseError(code);
            }}
          />
          {clauseError && (
            <p role="alert" className={`text-[11px] ${styles.dangerText}`} data-testid="rls-clause-error" aria-describedby="rls-predicate-editor">
              {t(`platform.security.clauseError.${clauseError}`)}
            </p>
          )}
          <label className={`flex flex-col gap-1 text-[10px] font-mono ${styles.muted}`}>
            {t("platform.security.field.description")}
            <input value={form.description ?? ""} onChange={(e) => setFormField("description", e.target.value)} className={inputCls} />
          </label>
          <div className="flex items-center gap-3">
            <label className={`flex items-center gap-1.5 text-xs ${styles.cardText}`}>
              <input
                type="checkbox"
                checked={form.enabled ?? true}
                onChange={(e) => setFormField("enabled", e.target.checked)}
              />
              {t("platform.security.field.enabled")}
            </label>
            <div className="flex-1" />
            <button
              type="button"
              onClick={() => void handleSave(form)}
              disabled={clauseError !== null || !form.predicateTemplate}
              data-testid="rls-save"
              className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-md text-xs font-medium cursor-pointer border ${styles.accentBorder} ${styles.accentText} disabled:opacity-50`}
            >
              <Save className="w-3.5 h-3.5" />
              {t("platform.security.action.save")}
            </button>
          </div>
          {bindingError && (
            <p role="alert" className={`text-[11px] ${styles.dangerText}`} data-testid="rls-save-error">
              {bindingError}
            </p>
          )}
        </div>
      )}

      {/* 列表 */}
      {loading ? (
        <div className="space-y-2" aria-busy="true">
          {[1, 2, 3].map((i) => (
            <div key={i} className={`h-12 rounded border ${styles.appBorder} animate-pulse`} />
          ))}
        </div>
      ) : policies.length === 0 ? (
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-md p-6 flex flex-col items-center gap-2`}>
          <Inbox className={`w-7 h-7 ${styles.muted}`} />
          <div className={`text-sm ${styles.cardText}`}>{t("platform.security.state.empty")}</div>
          <div className={`text-xs ${styles.muted}`}>{t("platform.security.hint.defaultDeny")}</div>
        </div>
      ) : error ? (
        <p role="alert" className={`text-xs ${styles.dangerText}`}>{error}</p>
      ) : (
        <RlsTable
          policies={policies}
          readOnly={readOnly}
          onEdit={startEdit}
          onToggle={handleToggle}
          onDelete={handleDelete}
        />
      )}
    </div>
  );
}

function RlsTable({
  policies,
  readOnly,
  onEdit,
  onToggle,
  onDelete,
}: {
  policies: RlsPolicy[];
  readOnly: boolean;
  onEdit: (p: RlsPolicy) => void;
  onToggle: (p: RlsPolicy) => void;
  onDelete: (p: RlsPolicy) => void;
}) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return (
    <div className="overflow-x-auto">
      <table className={`w-full text-xs border-collapse ${styles.cardText}`} data-testid="rls-table">
        <thead>
          <tr className={`border-b ${styles.appBorder} text-[10px] uppercase tracking-wider ${styles.muted}`}>
            <th className="text-left px-2 py-1.5">{t("platform.security.field.policyName")}</th>
            <th className="text-left px-2 py-1.5">{t("platform.security.field.table")}</th>
            <th className="text-left px-2 py-1.5">{t("platform.security.field.predicate")}</th>
            <th className="text-left px-2 py-1.5">{t("platform.security.field.role")}</th>
            <th className="text-right px-2 py-1.5">{t("platform.security.field.priority")}</th>
            <th className="text-center px-2 py-1.5">{t("platform.security.field.enabled")}</th>
            <th className="text-right px-2 py-1.5">{t("platform.security.field.updatedAt")}</th>
            {!readOnly && <th className="text-right px-2 py-1.5" />}
          </tr>
        </thead>
        <tbody className={`divide-y ${styles.appBorder}`}>
          {policies.map((p) => (
            <tr key={p.id} className="hover:opacity-90">
              <td className="px-2 py-1.5 font-medium">{p.policyName}</td>
              <td className="px-2 py-1.5 font-mono">{p.tableName}</td>
              <td className="px-2 py-1.5 font-mono break-all max-w-xs">
                {p.predicateTemplate || p.filterExpr || "—"}
              </td>
              <td className="px-2 py-1.5">{p.role || "—"}</td>
              <td className="px-2 py-1.5 text-right tabular-nums">{p.priority ?? 0}</td>
              <td className="px-2 py-1.5 text-center">
                <select
                  data-testid={`rls-toggle-${p.id}`}
                  value={p.enabled ? "on" : "off"}
                  disabled={readOnly}
                  onChange={() => onToggle(p)}
                  className={`text-[11px] px-1 py-0.5 rounded border ${styles.inputBorder} ${styles.inputBg} ${p.enabled ? styles.successText : styles.muted} cursor-pointer disabled:opacity-60`}
                  aria-label={t("platform.security.field.enabled")}
                >
                  <option value="on">{t("platform.security.state.on")}</option>
                  <option value="off">{t("platform.security.state.off")}</option>
                </select>
              </td>
              <td className="px-2 py-1.5 text-right font-mono text-[10px]">{p.updatedAt ?? "—"}</td>
              {!readOnly && (
                <td className="px-2 py-1.5 text-right whitespace-nowrap">
                  <button type="button" onClick={() => onEdit(p)} aria-label={t("platform.security.action.edit")} data-testid={`rls-edit-${p.id}`} className={`p-1 ${styles.muted} cursor-pointer hover:opacity-70`}>
                    <Pencil className="w-3.5 h-3.5" />
                  </button>
                  <button type="button" onClick={() => onDelete(p)} aria-label={t("platform.security.action.delete")} data-testid={`rls-delete-${p.id}`} className={`p-1 ${styles.dangerText} cursor-pointer hover:opacity-70`}>
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// 表单 → 后端入参（camelCase，安全引擎 RlsController 收 canonical）
function toInput(p: RlsPolicy): {
  policyName: string;
  tableName: string;
  predicateTemplate: string;
  bindings: RlsBinding[];
  role?: string;
  priority?: number;
  enabled?: boolean;
  description?: string;
} {
  return {
    policyName: p.policyName,
    tableName: p.tableName,
    predicateTemplate: p.predicateTemplate ?? "",
    bindings: p.bindings ?? [],
    role: p.role,
    priority: p.priority,
    enabled: p.enabled,
    description: p.description,
  };
}
