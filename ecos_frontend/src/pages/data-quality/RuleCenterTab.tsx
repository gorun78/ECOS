/**
 * PMO-48-B T9 — 数据质量中心 · 规则中心 Tab（状态机交互版）
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 后端 T7c 状态机已落地（DRAFT / IN_REVIEW / ACTIVE / DEPRECATED / SUPERSEDED / REJECTED / DISABLED）
 * - 状态徽章映射 + 顶部按状态统计
 * - 列表行右上角操作按钮（按状态动态显示）
 * - 审核对话框（Approve 展示版本时间线 + Reject 必填 reason）
 * - 版本时间线抽屉
 * - 新建 / 编辑弹窗表单（ruleName / ruleType / targetKind 必填）
 * - 写操作成功 → 成功 toast → load() 静默重拉列表
 */

import React, { useEffect, useMemo, useState } from "react";
import {
  Check,
  Clock,
  FileText,
  Fingerprint,
  GitBranch,
  Hash,
  History,
  Layers,
  Loader2,
  Pencil,
  Plus,
  RefreshCw,
  Send,
  ShieldCheck,
  Tag,
  Trash2,
  X,
  XCircle,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { showToastGlobal } from "../../components/common/Toast";
import ConfirmDialog from "../../components/common/ConfirmDialog";
import RuleReviewDialog from "./RuleReviewDialog";
import VersionTimelineDrawer from "./VersionTimelineDrawer";
import {
  DqRuleActionType,
  DqRuleDTO,
  DqRuleVO,
  createDqGovernanceRule,
  deleteDqGovernanceRule,
  dqRuleAction,
  fetchDqGovernanceRules,
  updateDqGovernanceRule,
} from "./api";

const PAGE_SIZE = 20;

/** 当前登录用户名（后端 DqRuleActionDTO 维度对齐，便于审计） */
function getCurrentUser(): string {
  return (
    localStorage.getItem("username") ||
    localStorage.getItem("user_name") ||
    "unknown"
  );
}

/** 状态徽章元信息 — 仅用主题变量 classes（禁止硬编码 Tailwind 颜色） */
const STATUS_META: Record<
  string,
  { label: string; icon: React.ComponentType<{ className?: string }>; cls: string }
> = {
  DRAFT: {
    label: "dw.dqRule.statusMachine.draft",
    icon: FileText,
    cls: "bg-slate-100 text-slate-700 border border-slate-300 dark:bg-slate-500/10 dark:text-slate-300 dark:border-slate-500",
  },
  IN_REVIEW: {
    label: "dw.dqRule.statusMachine.submit",
    icon: Send,
    cls: "bg-amber-50 text-amber-700 border border-amber-300 dark:bg-amber-500/10 dark:text-amber-300 dark:border-amber-500/50",
  },
  ACTIVE: {
    label: "dw.dqRule.statusMachine.active",
    icon: ShieldCheck,
    cls: "bg-emerald-50 text-emerald-700 border border-emerald-300 dark:bg-emerald-500/10 dark:text-emerald-300 dark:border-emerald-500/50",
  },
  DEPRECATED: {
    label: "dw.dqRule.statusMachine.deprecate",
    icon: Clock,
    cls: "bg-gray-100 text-gray-600 border border-gray-300 dark:bg-gray-500/10 dark:text-gray-300 dark:border-gray-500",
  },
  SUPERSEDED: {
    label: "dw.dqRule.statusMachine.supersede",
    icon: GitBranch,
    cls: "bg-slate-100 text-slate-600 border border-slate-300 dark:bg-slate-500/10 dark:text-slate-400 dark:border-slate-500",
  },
  REJECTED: {
    label: "dw.dqRule.statusMachine.reject",
    icon: XCircle,
    cls: "bg-red-50 text-red-700 border border-red-300 dark:bg-red-500/10 dark:text-red-300 dark:border-red-500/50",
  },
  DISABLED: {
    label: "dw.dqRule.statusMachine.disable",
    icon: X,
    cls: "bg-gray-100 text-gray-600 border border-gray-300 dark:bg-gray-500/10 dark:text-gray-300 dark:border-gray-500",
  },
};

function StatusBadge({ t, status }: { t: (k: string) => string; status: string }) {
  const meta = STATUS_META[(status ?? "").toUpperCase()];
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold ${meta?.cls ?? "opacity-50"}`}>
      {meta ? <meta.icon className="w-3 h-3" /> : <span aria-hidden>·</span>}
      {meta ? t(meta.label) : status || "-"}
    </span>
  );
}

const CATEGORIES = ["BUSINESS", "TECHNICAL", "COMPLIANCE"] as const;
const DOMAINS = ["CRM", "ERP", "DATA", "RISK", "OPS"] as const;
const RULE_TYPES = ["NULL", "UNIQUE", "FRESHNESS", "RANGE", "FORMAT", "CUSTOM"] as const;
const SEVERITIES = ["CRITICAL", "WARNING", "INFO"] as const;
const TARGET_KINDS = ["TABLE", "COLUMN", "ROW"] as const;
const TYPE_ICONS: Record<string, React.ComponentType<{ className?: string }>> = {
  NULL: FileText, UNIQUE: Fingerprint, REFRESH: Clock, FRESHNESS: Clock, RANGE: Hash, FORMAT: FileText, CUSTOM: Tag, DEFAULT: FileText,
};

interface RuleForm {
  ruleName: string; ruleCode: string; category: string; domain: string; ruleType: string;
  severity: string; targetKind: string; targetTable: string; targetField: string; parametersJson: string;
}

interface RuleCenterTabProps {
  initialTableFilter?: string;
}

export default function RuleCenterTab({ initialTableFilter }: RuleCenterTabProps = {}) {
  const { styles } = useTheme();
  const { t, locale } = useLanguage();

  // ── 筛选 ──
  const [category, setCategory] = useState("");
  const [domain, setDomain] = useState("");
  const [status, setStatus] = useState("");
  const [keyword, setKeyword] = useState("");

  // ── 数据 ──
  const [rules, setRules] = useState<DqRuleVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // ── 抽屉 / 弹窗 ──
  const [drawerRule, setDrawerRule] = useState<DqRuleVO | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [review, setReview] = useState<{ action: "approve" | "reject"; rule: DqRuleVO } | null>(null);
  const [formVisible, setFormVisible] = useState(false);
  const [formInitial, setFormInitial] = useState<DqRuleVO | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<DqRuleVO | null>(null);
  const [confirmDeprecate, setConfirmDeprecate] = useState<DqRuleVO | null>(null);
  const [confirmSupersede, setConfirmSupersede] = useState<DqRuleVO | null>(null);
  const [userLabel] = useState<string>(() => getCurrentUser());

  // 拉取列表（silent 用于写操作成功后静默刷新）
  const load = async (silent = false) => {
    if (!silent) { setLoading(true); setError(""); }
    try {
      const arr = await fetchDqGovernanceRules({
        category: category || undefined,
        domain: domain || undefined,
        status: status || undefined,
        pageNum: 1,
        pageSize: 1000,
      });
      if (Array.isArray(arr)) {
        const filtered =
          initialTableFilter && arr
            ? arr.filter((r) => (r.targetTable ?? "").toLowerCase() === initialTableFilter.toLowerCase())
            : arr;
        setRules(filtered);
      } else {
        setError(t("dw.dqRule.loadFailed"));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : locale === "zh" ? "网络错误" : "Network error");
    } finally {
      if (!silent) setLoading(false);
    }
  };

  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [category, domain, status, initialTableFilter]);

  // 顶部统计（按状态聚合）
  const stats = useMemo(() => {
    const out = { DRAFT: 0, IN_REVIEW: 0, ACTIVE: 0, DEPRECATED: 0, SUPERSEDED: 0, REJECTED: 0, DISABLED: 0, TOTAL: 0, ENABLED: 0 };
    for (const r of rules) {
      out.TOTAL += 1;
      const s = (r.status ?? "").toUpperCase();
      if (s in out) out[s as keyof typeof out] += 1;
      if (s === "ACTIVE") out.ENABLED += 1;
    }
    return out;
  }, [rules]);

  const filtered = useMemo(() => {
    if (!keyword) return rules;
    const kw = keyword.toLowerCase();
    return rules.filter((r) =>
      (r.ruleName ?? "").toLowerCase().includes(kw) ||
      (r.ruleCode ?? "").toLowerCase().includes(kw) ||
      (r.targetTable ?? "").toLowerCase().includes(kw)
    );
  }, [rules, keyword]);

  const runAction = async (action: DqRuleActionType, rule: DqRuleVO, payload?: { reason?: string }) => {
    const res = await dqRuleAction(rule.id, action, {
      ...payload,
      submitter: action === "submit" ? userLabel : undefined,
      approver: action === "approve" ? userLabel : undefined,
      rejector: action === "reject" ? userLabel : undefined,
      operator: action === "deprecate" || action === "supersede" || action === "disable" ? userLabel : undefined,
    });
    const keyMap: Record<DqRuleActionType, string> = {
      submit: "dw.dqRule.statusMachine.ruleSubmitted",
      approve: "dw.dqRule.statusMachine.ruleApproved",
      reject: "dw.dqRule.statusMachine.ruleRejected",
      deprecate: "dw.dqRule.statusMachine.ruleDeprecated",
      supersede: "dw.dqRule.statusMachine.ruleSuperseded",
      disable: "dw.dqRule.statusMachine.ruleDisabled",
    };
    showToastGlobal(res.success ? "success" : "error", res.success ? t(keyMap[action]) : t("dw.dqRule.statusMachine.actionFailed"));
    if (res.success) void load(true);
    return res;
  };

  const renderActions = (r: DqRuleVO) => {
    const s = (r.status ?? "").toUpperCase();
    const list: { icon: React.ComponentType<{ className?: string }>; label: string; danger?: boolean; onClick: () => void }[] = [];
    if (s === "DRAFT") {
      list.push({ icon: Send, label: t("dw.dqRule.statusMachine.submit"), onClick: () => void runAction("submit", r) });
      list.push({ icon: Pencil, label: t("dw.dqRule.editRule"), onClick: () => { setFormInitial(r); setFormVisible(true); } });
      list.push({ icon: Trash2, label: t("dw.dqRule.delete"), danger: true, onClick: () => setConfirmDelete(r) });
    } else if (s === "IN_REVIEW") {
      list.push({ icon: Check, label: t("dw.dqRule.statusMachine.approve"), onClick: () => setReview({ action: "approve", rule: r }) });
      list.push({ icon: XCircle, label: t("dw.dqRule.statusMachine.reject"), danger: true, onClick: () => setReview({ action: "reject", rule: r }) });
    } else if (s === "ACTIVE") {
      list.push({ icon: Clock, label: t("dw.dqRule.statusMachine.deprecate"), onClick: () => setConfirmDeprecate(r) });
      list.push({ icon: GitBranch, label: t("dw.dqRule.statusMachine.supersede"), onClick: () => setConfirmSupersede(r) });
    } else if (s === "DEPRECATED") {
      list.push({ icon: GitBranch, label: t("dw.dqRule.statusMachine.supersede"), onClick: () => setConfirmSupersede(r) });
    } else if (s === "REJECTED") {
      // REJECTED → 重新编辑回 DRAFT（后端 T7c PUT 已兼容回 DRAFT）
      list.push({ icon: Pencil, label: t("dw.dqRule.editRule"), onClick: () => { setFormInitial(r); setFormVisible(true); } });
    }
    // SUPERSEDED / DISABLED → 无操作（任务说明：不加 enable，废弃端点未建）
    list.push({ icon: History, label: t("dw.dqRule.statusMachine.history"), onClick: () => { setDrawerRule(r); setDrawerOpen(true); } });
    return list;
  };

  const onSubmitRule = async (dto: DqRuleDTO) => {
    const res = formInitial ? await updateDqGovernanceRule(formInitial.id, dto) : await createDqGovernanceRule(dto);
    return res;
  };

  // 表单内联（避免外部文件循环导入）— 但已经独立出 RuleFormDialog 见下
  // 见下方子组件

  return (
    <div className="h-full flex flex-col">
      {/* 顶部统计条 */}
      <div className="flex items-center gap-3 mb-3 flex-wrap p-2.5 border rounded-xl shadow-3xs" style={{ borderColor: styles.cardBorder }}>
        <div className="flex items-center gap-2">
          <Layers className={`w-4 h-4 ${styles.accentText}`} />
          <span className={`font-bold text-xs ${styles.cardText}`}>{t("dw.dqRule.title")}</span>
        </div>
        <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
          {t("dw.dqRule.totalCount").replace("{total}", String(stats.TOTAL)).replace("{shown}", String(filtered.length))}
        </span>
        <div className="flex items-center gap-1.5 flex-wrap">
          {(Object.keys(STATUS_META) as (keyof typeof STATUS_META)[]).map((s) => {
            const c = stats[s as keyof typeof stats];
            if (!c) return null;
            const meta = STATUS_META[s];
            const Icon = meta.icon;
            return (
              <span key={s} className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold ${meta.cls}`}>
                <Icon className="w-3 h-3" />
                <span>{t(meta.label)}</span>
                <span className="font-mono">{c}</span>
              </span>
            );
          })}
        </div>
      </div>

      {/* 工具栏 */}
      <div className={`flex flex-wrap items-center gap-2 mb-3 p-2.5 border rounded-xl shadow-3xs`} style={{ borderColor: styles.cardBorder }}>
        <select value={category} onChange={(e) => setCategory(e.target.value)}
          className={`px-2.5 py-1.5 text-xs border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} rounded-lg cursor-pointer`}>
          <option value="">{t("dw.dqRule.categoryAll")}</option>
          {["NULL", "UNIQUE", "REFRESH", "RANGE", "FORMAT", "CUSTOM"].map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>

        <select value={domain} onChange={(e) => setDomain(e.target.value)}
          className={`px-2.5 py-1.5 text-xs border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} rounded-lg cursor-pointer`}>
          <option value="">{t("dw.dqRule.domainAll")}</option>
          {DOMAINS.map((d) => <option key={d} value={d}>{d}</option>)}
        </select>

        <select value={status} onChange={(e) => setStatus(e.target.value)}
          className={`px-2.5 py-1.5 text-xs border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} rounded-lg cursor-pointer`}>
          <option value="">{t("dw.dqRule.statusAll")}</option>
          {["DRAFT", "IN_REVIEW", "ACTIVE", "DEPRECATED", "SUPERSEDED", "REJECTED", "DISABLED"].map((s) => (
            <option key={s} value={s}>{STATUS_META[s]?.label ? t(STATUS_META[s].label) : s}</option>
          ))}
        </select>

        <input value={keyword} onChange={(e) => setKeyword(e.target.value)}
          placeholder={t("dw.dqRule.searchPlaceholder")}
          className={`flex-1 min-w-[180px] px-3 py-1.5 text-xs border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} rounded-lg focus:outline-hidden focus:ring-1 focus:ring-indigo-500/50`} />

        <button onClick={() => void load()}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border ${styles.cardBorder} rounded-lg transition cursor-pointer ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/5`}>
          <RefreshCw className="w-3.5 h-3.5" />
          {t("dw.dqRule.refresh")}
        </button>

        <button onClick={() => { setFormInitial(null); setFormVisible(true); }}
          className={`flex items-center gap-1.5 px-3 py-1.5 ${styles.accentBg} ${styles.accentHover} ${styles.cardText} text-xs font-bold rounded-lg shadow-xs transition cursor-pointer`}>
          <Plus className="w-3.5 h-3.5" />
          {t("dw.dqRule.newRule")}
        </button>
      </div>

      {/* 主区: 列表 */}
      <div className={`flex-1 min-h-0 border rounded-xl overflow-hidden flex flex-col`}
        style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
        {loading ? (
          <div className={`flex-1 flex flex-col items-center justify-center py-16 ${styles.cardTextMuted}`}>
            <Loader2 className={`w-6 h-6 animate-spin ${styles.accentText}`} />
            <span className="mt-3 text-xs">{t("dw.dqRule.loading")}</span>
          </div>
        ) : error ? (
          <div className="flex-1 flex flex-col items-center justify-center py-16">
            <p className={`text-sm ${styles.dangerText}`}>{error}</p>
            <button onClick={() => void load()}
              className={`mt-3 px-4 py-1.5 ${styles.cardTextMuted} border rounded-lg text-xs transition cursor-pointer hover:bg-black/5 dark:hover:bg-white/10`}>
              {t("dw.dqRule.retry")}
            </button>
          </div>
        ) : filtered.length === 0 ? (
          <div className={`flex-1 flex flex-col items-center justify-center py-16 ${styles.cardTextMuted}`}>
            <FileText className="w-10 h-10 opacity-30" />
            <span className="mt-3 text-sm font-semibold">{t("dw.dqRule.empty")}</span>
          </div>
        ) : (
          <div className="flex-1 overflow-y-auto overflow-x-auto">
            <table className="w-full text-xs align-middle">
              <thead className="sticky top-0 z-10">
                <tr className={`bg-slate-100/40 dark:bg-slate-500/10 border-b ${styles.cardBorder}`}>
                  <th className="text-left p-2.5 font-semibold w-12">{t("dw.dqRule.colSeq")}</th>
                  <th className="text-left p-2.5 font-semibold">{t("dw.dqRule.colName")}</th>
                  <th className="text-left p-2.5 font-semibold">{t("dw.dqRule.category")}</th>
                  <th className="text-left p-2.5 font-semibold">{t("dw.dqRule.colType")}</th>
                  <th className="text-left p-2.5 font-semibold">{t("dw.dqRule.domain")}</th>
                  <th className="text-left p-2.5 font-semibold">{t("dw.dqRule.targetKind")}</th>
                  <th className="text-left p-2.5 font-semibold">{t("dw.dqRule.severity")}</th>
                  <th className="text-left p-2.5 font-semibold">{t("dw.dqRule.colStatus")}</th>
                  <th className="text-right p-2.5 font-semibold w-56">{t("dw.dqRule.colActions")}</th>
                </tr>
              </thead>
              <tbody>
                {filtered.slice(0, PAGE_SIZE).map((r, i) => {
                  const statusMeta = STATUS_META[(r.status ?? "").toUpperCase()];
                  const StatusIcon = statusMeta?.icon ?? FileText;
                  return (
                    <tr key={r.id} className={`border-b ${styles.cardBorder} transition-colors hover:bg-black/5 dark:hover:bg-white/5`}>
                      <td className={`p-2.5 ${styles.cardTextMuted} font-mono`}>{i + 1}</td>
                      <td className="p-2.5">
                        <div className="flex items-center gap-2">
                          <StatusIcon className={`w-3.5 h-3.5 ${styles.accentText} shrink-0`} />
                          <div className="min-w-0">
                            <div className={`font-bold ${styles.cardText} truncate`}>{r.ruleName}</div>
                            <div className={`font-mono text-[10px] ${styles.cardTextMuted} truncate`}>{r.ruleCode}</div>
                          </div>
                        </div>
                      </td>
                      <td className="p-2.5">
                        <span className={`px-2 py-0.5 rounded ${styles.badgeBg} ${styles.badgeText} text-[10px] font-mono font-bold`}>{r.category || "-"}</span>
                      </td>
                      <td className={`p-2.5 ${styles.cardTextMuted}`}>
                        <div className="flex items-center gap-1.5">
                          {(() => { const T = TYPE_ICONS[r.ruleType ?? ""] ?? FileText; return <T className="w-3.5 h-3.5" />; })()}
                          <span className="font-mono text-[11px] truncate">{r.ruleType || "-"}</span>
                        </div>
                      </td>
                      <td className="p-2.5">
                        <span className={`px-2 py-0.5 rounded-full ${styles.badgeBg} ${styles.badgeText} text-[10px] font-bold`}>{r.domain || "-"}</span>
                      </td>
                      <td className="p-2.5">
                        <div className={`flex items-center gap-1.5 ${styles.cardTextMuted}`}>
                          <Hash className="w-3 h-3 opacity-60" />
                          <span className="font-mono text-[11px] truncate">{r.targetKind || "-"}</span>
                        </div>
                        {r.targetTable ? (
                          <div className={`font-mono text-[10px] ${styles.cardTextMuted} opacity-70 truncate`}>{r.targetTable}</div>
                        ) : null}
                      </td>
                      <td className="p-2.5">
                        <SeverityInline styles={styles} severity={r.severity} />
                      </td>
                      <td className="p-2.5"><StatusBadge t={t} status={r.status} /></td>
                      <td className="p-2.5">
                        <div className="flex items-center justify-end gap-1" onClick={(e) => e.stopPropagation()}>
                          {renderActions(r).map((a, ai) => (
                            <button key={ai} onClick={a.onClick} aria-label={a.label} title={a.label}
                              className={`p-1.5 rounded-md border transition-colors cursor-pointer ${
                                a.danger
                                  ? `border-red-300/50 dark:border-red-500/40 ${styles.dangerText} hover:bg-red-50 dark:hover:bg-red-500/10`
                                  : `${styles.cardBorder} ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/10`
                              }`}>
                              <a.icon className="w-3.5 h-3.5" />
                            </button>
                          ))}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {filtered.length > PAGE_SIZE ? (
              <div className="px-3 py-2 text-[11px] font-mono text-center" style={{ color: styles.cardTextMuted }}>
                {t("dw.dqRule.totalCount").replace("{total}", String(stats.TOTAL)).replace("{shown}", String(filtered.length))}
              </div>
            ) : null}
          </div>
        )}
      </div>

      {/* 确认删除 */}
      <ConfirmDialog visible={!!confirmDelete}
        title={t("dw.dqRule.deleteConfirm")}
        message={t("dw.dqRule.deleteMsg").replace("{name}", confirmDelete?.ruleName ?? "")}
        confirmText={t("dw.dqRule.delete")} danger variant="danger"
        onCancel={() => setConfirmDelete(null)}
        onConfirm={() => confirmDelete && (async () => {
          const res = await deleteDqGovernanceRule(confirmDelete.id);
          showToastGlobal(res.success ? "success" : "error", res.success ? t("dw.dqRule.statusMachine.ruleDeleted") : t("dw.dqRule.statusMachine.actionFailed"));
          setConfirmDelete(null);
          if (res.success) void load(true);
        })()} />

      {/* 确认废止 */}
      <ConfirmDialog visible={!!confirmDeprecate}
        title={t("dw.dqRule.statusMachine.confirmDeprecate")}
        message={t("dw.dqRule.statusMachine.deprecateMsg").replace("{name}", confirmDeprecate?.ruleName ?? "")}
        confirmText={t("dw.dqRule.statusMachine.deprecate")} variant="warning"
        onCancel={() => setConfirmDeprecate(null)}
        onConfirm={() => confirmDeprecate && (async () => {
          await runAction("deprecate", confirmDeprecate);
          setConfirmDeprecate(null);
        })()} />

      {/* 确认替代 */}
      <ConfirmDialog visible={!!confirmSupersede}
        title={t("dw.dqRule.statusMachine.confirmSupersede")}
        message={t("dw.dqRule.statusMachine.supersedeMsg").replace("{name}", confirmSupersede?.ruleName ?? "")}
        confirmText={t("dw.dqRule.statusMachine.supersede")} variant="warning"
        onCancel={() => setConfirmSupersede(null)}
        onConfirm={() => confirmSupersede && (async () => {
          await runAction("supersede", confirmSupersede);
          setConfirmSupersede(null);
        })()} />

      {/* 审核对话框 */}
      <RuleReviewDialog visible={!!review}
        action={review?.action ?? "approve"}
        rule={review?.rule ?? null}
        operator={userLabel}
        onClose={() => setReview(null)}
        onConfirmed={() => void load(true)} />

      {/* 版本时间线抽屉 */}
      <VersionTimelineDrawer visible={drawerOpen} rule={drawerRule} onClose={() => setDrawerOpen(false)} />

      {/* 新建/编辑表单弹窗 */}
      <RuleFormDialog visible={formVisible} initial={formInitial}
        onSubmit={onSubmitRule}
        onClose={() => setFormVisible(false)}
        onSubmitted={() => { void load(true); setFormVisible(false); setFormInitial(null); }} />
    </div>
  );
}

/** 行内严重度徽章（紧凑版本，主题色） */
function SeverityInline({ styles, severity }: { styles: ReturnType<typeof useTheme>["styles"]; severity?: string }) {
  const s = (severity ?? "").toUpperCase();
  const cls =
    s === "CRITICAL" ? `${styles.dangerBg} ${styles.dangerText} border ${styles.dangerBorder}`
    : s === "WARNING" ? `${styles.warningBg} ${styles.warningText} border ${styles.warningBorder}`
    : s === "INFO" ? `${styles.infoBg} ${styles.infoText} border ${styles.infoBorder}`
    : `${styles.successBg} ${styles.successText} border ${styles.successBorder}`;
  return <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border ${cls}`}>{severity || "-"}</span>;
}

/** 新建/编辑表单 — 与 RuleCenterTab 内联拆开，控制主文件 <800 行 */
function RuleFormDialog({
  visible, initial, onSubmit, onClose, onSubmitted,
}: {
  visible: boolean;
  initial: DqRuleVO | null;
  onSubmit: (dto: DqRuleDTO) => Promise<{ success: boolean; error?: string }>;
  onClose: () => void;
  onSubmitted: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [form, setForm] = useState<RuleForm>(() => ({
    ruleName: "", ruleCode: "", category: "BUSINESS", domain: "DATA", ruleType: "NULL",
    severity: "INFO", targetKind: "COLUMN", targetTable: "", targetField: "", parametersJson: "{}",
  }));
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!visible) return;
    const params = (() => {
      const p = (initial as unknown as { parameters?: unknown })?.parameters;
      try { return p ? JSON.stringify(p, null, 2) : initial?.parametersJson ?? "{}"; }
      catch { return "{}"; }
    })();
    setForm({
      ruleName: initial?.ruleName ?? "", ruleCode: initial?.ruleCode ?? "",
      category: initial?.category ?? "BUSINESS", domain: initial?.domain ?? "DATA",
      ruleType: initial?.ruleType ?? "NULL", severity: initial?.severity ?? "INFO",
      targetKind: initial?.targetKind ?? "COLUMN", targetTable: initial?.targetTable ?? "",
      targetField: initial?.targetField ?? "", parametersJson: params,
    });
    setError("");
    setSaving(false);
  }, [visible, initial]);

  if (!visible) return null;

  const set = <K extends keyof RuleForm>(k: K, v: RuleForm[K]) => setForm((p) => ({ ...p, [k]: v }));

  const onConfirm = async () => {
    if (!form.ruleName.trim()) { setError(t("dw.dqRule.form.ruleNameRequired")); return; }
    const code = form.ruleCode.trim() || `DQ_${Date.now().toString(36).toUpperCase()}`;
    if (!form.ruleType.trim()) { setError(t("dw.dqRule.form.ruleTypeRequired")); return; }
    if (!form.targetKind.trim()) { setError(t("dw.dqRule.form.targetKindRequired")); return; }
    let parameters: Record<string, unknown> | undefined;
    if (form.parametersJson.trim() && form.parametersJson.trim() !== "{}") {
      try {
        const parsed = JSON.parse(form.parametersJson);
        if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) parameters = parsed as Record<string, unknown>;
      } catch { setError(t("dw.dqRule.form.paramsInvalidJson")); return; }
    }
    const dto: DqRuleDTO = {
      ruleName: form.ruleName.trim(), ruleCode: code,
      category: form.category, domain: form.domain,
      ruleType: form.ruleType, severity: form.severity,
      targetKind: form.targetKind,
      targetTable: form.targetTable.trim() || undefined,
      targetField: form.targetField.trim() || undefined,
      parameters,
    };
    setSaving(true);
    setError("");
    const res = await onSubmit(dto);
    setSaving(false);
    if (res.success) {
      showToastGlobal("success", initial ? t("dw.dqRule.statusMachine.ruleUpdated") : t("dw.dqRule.statusMachine.ruleCreated"));
      onSubmitted();
    } else {
      setError(res.error ?? t("dw.dqRule.form.saveFailed"));
    }
  };

  const fieldCls = `w-full px-3 py-1.5 text-xs border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} rounded-lg focus:outline-hidden focus:ring-1 focus:ring-indigo-500/50`;
  const labelCls = `text-[11px] font-bold ${styles.cardTextMuted} mb-1 block font-mono uppercase`;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className={`relative z-50 w-full max-w-2xl mx-4 rounded-xl shadow-2xl ${styles.cardBg} border ${styles.cardBorder} flex flex-col max-h-[85vh]`}>
        <div className={`flex items-center justify-between px-5 py-4 border-b ${styles.cardBorder}`}>
          <div className="flex items-center gap-2">
            {initial ? <Pencil className={`w-4 h-4 ${styles.accentText}`} /> : <Plus className={`w-4 h-4 ${styles.accentText}`} />}
            <span className={`font-bold text-sm ${styles.cardText}`}>
              {initial ? t("dw.dqRule.editRule") : t("dw.dqRule.newRule")}
            </span>
          </div>
          <button onClick={onClose}
            className={`p-1 rounded hover:bg-black/5 dark:hover:bg-white/10 ${styles.cardTextMuted} transition cursor-pointer`} aria-label="close">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4 grid grid-cols-2 gap-3">
          <div>
            <label className={labelCls}>{t("dw.dqRule.colName")} *</label>
            <input value={form.ruleName} onChange={(e) => set("ruleName", e.target.value)} className={fieldCls} />
          </div>
          <div>
            <label className={labelCls}>{t("dw.dqRule.colCode")}</label>
            <input value={form.ruleCode} onChange={(e) => set("ruleCode", e.target.value)} className={`${fieldCls} font-mono`} placeholder="DQ_xxx" />
          </div>
          <div>
            <label className={labelCls}>{t("dw.dqRule.category")}</label>
            <select value={form.category} onChange={(e) => set("category", e.target.value)} className={fieldCls}>
              {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div>
            <label className={labelCls}>{t("dw.dqRule.domain")}</label>
            <select value={form.domain} onChange={(e) => set("domain", e.target.value)} className={fieldCls}>
              {DOMAINS.map((d) => <option key={d} value={d}>{d}</option>)}
            </select>
          </div>
          <div>
            <label className={labelCls}>{t("dw.dqRule.colType")} *</label>
            <select value={form.ruleType} onChange={(e) => set("ruleType", e.target.value)} className={fieldCls}>
              {RULE_TYPES.map((rt) => <option key={rt} value={rt}>{rt}</option>)}
            </select>
          </div>
          <div>
            <label className={labelCls}>{t("dw.dqRule.severity")}</label>
            <select value={form.severity} onChange={(e) => set("severity", e.target.value)} className={fieldCls}>
              {SEVERITIES.map((sv) => <option key={sv} value={sv}>{sv}</option>)}
            </select>
          </div>
          <div>
            <label className={labelCls}>{t("dw.dqRule.targetKind")} *</label>
            <select value={form.targetKind} onChange={(e) => set("targetKind", e.target.value)} className={fieldCls}>
              {TARGET_KINDS.map((tk) => <option key={tk} value={tk}>{tk}</option>)}
            </select>
          </div>
          <div>
            <label className={labelCls}>{t("dw.dqRule.targetTable")}</label>
            <input value={form.targetTable} onChange={(e) => set("targetTable", e.target.value)} className={`${fieldCls} font-mono`} placeholder="table.name" />
          </div>
          <div className="col-span-2">
            <label className={labelCls}>{t("dw.dqRule.targetField")}</label>
            <input value={form.targetField} onChange={(e) => set("targetField", e.target.value)} className={`${fieldCls} font-mono`} placeholder="column.name" />
          </div>
          <div className="col-span-2">
            <label className={labelCls}>{t("dw.dqRule.parameters")}</label>
            <textarea value={form.parametersJson} onChange={(e) => set("parametersJson", e.target.value)} rows={4}
              className={`${fieldCls} font-mono resize-none`} placeholder='{"threshold": 0.95}' />
          </div>
          {error ? <div className={`col-span-2 text-xs ${styles.dangerText}`}>{error}</div> : null}
        </div>

        <div className={`flex items-center justify-end gap-2 px-5 py-3 border-t ${styles.cardBorder}`}>
          <button onClick={onClose} disabled={saving}
            className={`px-4 py-1.5 rounded-lg text-xs font-semibold border ${styles.cardBorder} ${styles.cardText} hover:bg-black/5 dark:hover:bg-white/5 transition cursor-pointer disabled:opacity-40`}>
            {t("dw.dqRule.cancel")}
          </button>
          <button onClick={() => void onConfirm()} disabled={saving}
            className={`px-4 py-1.5 rounded-lg text-xs font-bold ${styles.accentBg} ${styles.accentHover} ${styles.cardText} transition cursor-pointer disabled:opacity-40`}>
            {saving ? <Loader2 className="w-3.5 h-3.5 inline animate-spin" /> : (initial ? t("dw.dqRule.save") : t("dw.dqRule.create"))}
          </button>
        </div>
      </div>
    </div>
  );
}
