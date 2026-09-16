/**
 * 数据质量中心 · 规则中心 Tab（Phase 1 只读）
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 数据源 = legacy 只读端点 GET /api/v1/ecos/dq/rules（见 api.ts fetchEcosDqRules），
 * 与数据工作台「数据质量」页同源（读表 ecos_dq_rule_v2）。
 * 治理端点 GET /api/v1/dq/rules 实测 404，后端不可达，故本 Tab 的写操作 UI
 * （新建 / 编辑 / 审批 / 驳回 / 删除 / 废止 / 替代 / 版本历史）已移除，仅保留只读展示。
 * - 状态徽章映射 + 顶部按状态统计（legacy 仅 ACTIVE / DISABLED 两类）
 * - 规则类型 / 状态 / 关键词筛选全部为前端内存筛选（legacy 端点不支持服务端筛选）
 */

import React, { useEffect, useMemo, useState } from "react";
import {
  Clock,
  FileText,
  Fingerprint,
  GitBranch,
  Hash,
  Info,
  Layers,
  Loader2,
  RefreshCw,
  Send,
  ShieldCheck,
  Tag,
  X,
  XCircle,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { DqRuleVO, fetchEcosDqRules } from "./api";

const PAGE_SIZE = 20;

/** 中性状态徽章结构帧 — 主题变量 + fallback（替代原硬编码 slate 结构色，跨 4 主题一致） */
const NEUTRAL_BADGE_CLS =
  "bg-[var(--muted,rgba(100,116,139,0.08))] border border-[color-mix(in_srgb,var(--card-border,#E2E8F0)_55%,transparent)]";

/** 状态徽章元信息 — 仅用主题变量 classes（禁止硬编码 Tailwind 颜色） */
const STATUS_META: Record<
  string,
  { label: string; icon: React.ComponentType<{ className?: string }>; cls: string }
> = {
  DRAFT: {
    label: "dw.dqRule.statusMachine.draft",
    icon: FileText,
    cls: `${NEUTRAL_BADGE_CLS} text-[var(--card-text,#334155)]`,
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
    cls: `${NEUTRAL_BADGE_CLS} text-[#475569]/70`,
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

const TYPE_ICONS: Record<string, React.ComponentType<{ className?: string }>> = {
  NULL: FileText, UNIQUE: Fingerprint, REFRESH: Clock, FRESHNESS: Clock, RANGE: Hash, FORMAT: FileText, CUSTOM: Tag, DEFAULT: FileText,
};

/** 状态筛选可选值 — legacy enabled 只有两种取值（对应 ACTIVE / DISABLED） */
const STATUS_OPTIONS = ["ACTIVE", "DISABLED"] as const;

interface RuleCenterTabProps {
  initialTableFilter?: string;
}

export default function RuleCenterTab({ initialTableFilter }: RuleCenterTabProps = {}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // ── 前端筛选（legacy 端点无服务端筛选，全部为内存过滤）──
  const [ruleType, setRuleType] = useState("");
  const [status, setStatus] = useState("");
  const [keyword, setKeyword] = useState("");

  // ── 数据 ──
  const [rules, setRules] = useState<DqRuleVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  /** 拉取规则列表（legacy 只读端点） */
  const load = async () => {
    setLoading(true);
    setError("");
    try {
      const arr = await fetchEcosDqRules();
      // legacy 数据无 targetTable 字段：仅当返回结果中确有该字段时才按 initialTableFilter 过滤，
      // 否则忽略该参数（否则过滤条件恒不命中，列表会恒为空）
      const hasTargetTable = arr.some((r) => (r.targetTable ?? "").trim() !== "");
      const next =
        initialTableFilter && hasTargetTable
          ? arr.filter((r) => (r.targetTable ?? "").toLowerCase() === initialTableFilter.toLowerCase())
          : arr;
      setRules(next);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("dw.dqRule.loadFailed"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [initialTableFilter]);

  // 顶部统计（按状态聚合；legacy 仅 ACTIVE / DISABLED 两类，其余计数 0 由渲染处自动隐藏）
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

  // 规则类型下拉选项 — 从已加载数据动态派生，不硬编码枚举
  const ruleTypeOptions = useMemo(
    () => Array.from(new Set(rules.map((r) => r.ruleType).filter(Boolean))).sort(),
    [rules],
  );

  // 前端内存筛选：规则类型 + 状态 + 关键词（按规则名称匹配）
  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return rules.filter((r) => {
      if (ruleType && r.ruleType !== ruleType) return false;
      if (status && (r.status ?? "").toUpperCase() !== status) return false;
      if (kw && !(r.ruleName ?? "").toLowerCase().includes(kw)) return false;
      return true;
    });
  }, [rules, ruleType, status, keyword]);

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

      {/* 工具栏（只读：无新建/编辑入口） */}
      <div className={`flex flex-wrap items-center gap-2 mb-3 p-2.5 border rounded-xl shadow-3xs`} style={{ borderColor: styles.cardBorder }}>
        <select value={ruleType} onChange={(e) => setRuleType(e.target.value)}
          aria-label={t("dw.dqRule.colType")}
          className={`px-2.5 py-1.5 text-xs border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} rounded-lg cursor-pointer`}>
          <option value="">{t("dw.dqRule.ruleTypeAll")}</option>
          {ruleTypeOptions.map((rt) => (
            <option key={rt} value={rt}>{rt}</option>
          ))}
        </select>

        <select value={status} onChange={(e) => setStatus(e.target.value)}
          aria-label={t("dw.dqRule.status")}
          className={`px-2.5 py-1.5 text-xs border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} rounded-lg cursor-pointer`}>
          <option value="">{t("dw.dqRule.statusAll")}</option>
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>{STATUS_META[s]?.label ? t(STATUS_META[s].label) : s}</option>
          ))}
        </select>

        <input value={keyword} onChange={(e) => setKeyword(e.target.value)}
          placeholder={t("dw.dqRule.searchPlaceholderName")}
          className={`flex-1 min-w-[180px] px-3 py-1.5 text-xs border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} rounded-lg focus:outline-hidden focus:ring-1 focus:ring-indigo-500/50`} />

        <button onClick={() => void load()}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border ${styles.cardBorder} rounded-lg transition cursor-pointer ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/5`}>
          <RefreshCw className="w-3.5 h-3.5" />
          {t("dw.dqRule.refresh")}
        </button>

        {/* 只读提示：治理端点 404，写操作 UI 已隐藏 */}
        <span className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 text-[11px] border rounded-lg ${styles.cardBorder} ${styles.cardTextMuted}`}>
          <Info className="w-3.5 h-3.5 shrink-0" />
          {t("dw.dqRule.onlyReadPhase1")}
        </span>
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
                <tr className={`${styles.appBg} border-b ${styles.cardBorder}`}>
                  <th className="text-left p-2.5 font-semibold w-12">{t("dw.dqRule.colSeq")}</th>
                  <th className="text-left p-2.5 font-semibold">{t("dw.dqRule.colName")}</th>
                  <th className="text-left p-2.5 font-semibold">{t("dw.dqRule.colType")}</th>
                  <th className="text-left p-2.5 font-semibold">{t("dw.dqRule.severity")}</th>
                  <th className="text-left p-2.5 font-semibold">{t("dw.dqRule.colStatus")}</th>
                </tr>
              </thead>
              <tbody>
                {filtered.slice(0, PAGE_SIZE).map((r, i) => {
                  const statusMeta = STATUS_META[(r.status ?? "").toUpperCase()];
                  const StatusIcon = statusMeta?.icon ?? FileText;
                  const TypeIcon = TYPE_ICONS[r.ruleType ?? ""] ?? FileText;
                  return (
                    <tr key={r.id} className={`border-b ${styles.cardBorder} transition-colors hover:bg-black/5 dark:hover:bg-white/5`}>
                      <td className={`p-2.5 ${styles.cardTextMuted} font-mono`}>{i + 1}</td>
                      <td className="p-2.5">
                        <div className="flex items-center gap-2">
                          <StatusIcon className={`w-3.5 h-3.5 ${styles.accentText} shrink-0`} />
                          <span className={`font-bold ${styles.cardText} truncate`}>{r.ruleName}</span>
                        </div>
                      </td>
                      <td className={`p-2.5 ${styles.cardTextMuted}`}>
                        <div className="flex items-center gap-1.5">
                          <TypeIcon className="w-3.5 h-3.5" />
                          <span className="font-mono text-[11px] truncate">{r.ruleType || "-"}</span>
                        </div>
                      </td>
                      <td className="p-2.5">
                        <SeverityInline styles={styles} severity={r.severity} />
                      </td>
                      <td className="p-2.5"><StatusBadge t={t} status={r.status} /></td>
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
