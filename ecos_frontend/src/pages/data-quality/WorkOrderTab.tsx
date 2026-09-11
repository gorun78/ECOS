/**
 * PMO-48-C T14 — 数据质量中心 · 工单中心 Tab
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 后端 T12/T13 已就绪：
 *   GET  /api/v1/dq/work-orders?{status}&{severity}&{sort}
 *   POST /api/v1/dq/work-orders/{id}/assign?to=xxx
 *   POST /api/v1/dq/work-orders/{id}/start
 *   POST /api/v1/dq/work-orders/{id}/resolve
 *   POST /api/v1/dq/work-orders/{id}/verify?pass=true&note=...
 *   POST /api/v1/dq/work-orders/{id}/close
 *   POST /api/v1/dq/work-orders/{id}/reject
 *   POST /api/v1/dq/work-orders/{id}/run-rca
 *
 * 功能：
 * - 4 KPI 卡：PENDING / ASSIGNED / IN_WORK / RESOLVED
 * - 列表按 severity 降序
 * - 行展开显示详情（含 RCA 占位提示）
 * - [assign] 输入框弹层 / [start] / [resolve] 操作按钮
 * - [人工 RCA] 按钮调 run-rca
 */

import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Briefcase,
  Check,
  ClipboardList,
  Clock,
  Cpu,
  Loader2,
  Play,
  RefreshCw,
  Shield,
  User,
  UserPlus,
  Wrench,
  X,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { showToastGlobal } from "../../components/common/Toast";
import {
  DqWorkOrderVO,
  assignDqWorkOrder,
  dqWorkOrderAction,
  fetchDqWorkOrders,
} from "./api";

/** KPI 状态 */
const KPI_STATUSES = ["PENDING", "ASSIGNED", "IN_WORK", "RESOLVED"] as const;

/** 状态过滤选项（含全部） */
const STATUS_OPTIONS = [
  "PENDING",
  "ASSIGNED",
  "IN_WORK",
  "RESOLVED",
  "VERIFIED",
  "CLOSED",
  "REJECTED",
] as const;

/** 严重级别过滤选项 */
const SEVERITY_OPTIONS = ["CRITICAL", "HIGH", "MEDIUM", "LOW"] as const;

/** severity 排序权重：CRITICAL=4 > HIGH=3 > MEDIUM=2 > LOW=1 */
function severityWeight(sev: string): number {
  switch (sev) {
    case "CRITICAL":
      return 4;
    case "HIGH":
      return 3;
    case "MEDIUM":
      return 2;
    case "LOW":
      return 1;
    default:
      return 0;
  }
}

/** severity 徽章样式 */
function severityBadgeClass(sev: string): string {
  switch (sev) {
    case "CRITICAL":
      return "bg-red-500/15 text-red-700 dark:text-red-400 border border-red-500/30";
    case "HIGH":
      return "bg-orange-500/15 text-orange-700 dark:text-orange-400 border border-orange-500/30";
    case "MEDIUM":
      return "bg-yellow-500/15 text-yellow-700 dark:text-yellow-400 border border-yellow-500/30";
    default:
      return "bg-slate-500/15 text-slate-600 dark:text-slate-400 border border-slate-400/30";
  }
}

/** 工单状态徽章样式 */
function woStatusBadgeClass(status: string): string {
  switch (status) {
    case "PENDING":
      return "bg-red-500/10 text-red-600 dark:text-red-400";
    case "ASSIGNED":
      return "bg-blue-500/10 text-blue-600 dark:text-blue-400";
    case "IN_WORK":
      return "bg-yellow-500/10 text-yellow-600 dark:text-yellow-400";
    case "RESOLVED":
      return "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400";
    case "VERIFIED":
      return "bg-teal-500/10 text-teal-600 dark:text-teal-400";
    case "CLOSED":
      return "bg-slate-500/10 text-slate-500";
    case "REJECTED":
      return "bg-red-100/10 text-rose-600 dark:text-rose-400";
    default:
      return "bg-slate-500/10 text-slate-500";
  }
}

/** 格式化时间 */
function fmtTime(iso?: string): string {
  if (!iso) return "—";
  return iso.replace("T", " ").slice(0, 19);
}

/**
 * PMO-48-D T16 RCA 结果渲染。
 * rcaResult 为后端落库的 JSON 字符串（DqRcaResult 序列化），解析后渲染 rootCause /
 * candidates / causalChain / confidence；rcaResult 缺失时显示"采集中…"占位。
 */
function RcaResultView({
  rcaResult,
  rcaConfidence,
  styles,
  t,
}: {
  rcaResult?: string;
  rcaConfidence?: number;
  styles: ReturnType<typeof useTheme>["styles"];
  t: ReturnType<typeof useLanguage>["t"];
}) {
  const box = "mt-3 rounded-md border border-purple-500/20 bg-purple-500/5 p-3";
  const header = (
    <div className="flex items-center gap-1.5 text-[11px] font-semibold text-purple-600 dark:text-purple-400">
      <Cpu className="w-3.5 h-3.5" />
      RCA
    </div>
  );

  // 无 rcaResult → 占位（采集中）
  if (!rcaResult) {
    return (
      <div className="mt-3 text-[10px] font-mono opacity-40 px-1 py-2 rounded-md bg-slate-100 dark:bg-slate-800/40">
        {t("dw.dqRule.workOrders.rca_collectedAt")}
      </div>
    );
  }

  // 尝试 JSON 解析
  let parsed: Record<string, unknown> | null = null;
  try {
    const v = JSON.parse(rcaResult);
    if (v && typeof v === "object" && !Array.isArray(v)) parsed = v as Record<string, unknown>;
  } catch {
    /* 非 JSON 时降级原文 pre 显示 */
  }

  // 解析失败 → 原文降级
  if (!parsed) {
    return (
      <div className={box}>
        {header}
        <pre className="mt-1.5 text-[10px] font-mono whitespace-pre-wrap max-h-40 overflow-y-auto">{rcaResult}</pre>
      </div>
    );
  }

  const rc = typeof parsed.rootCause === "string" ? (parsed.rootCause as string) : "";
  const confNum = typeof parsed.confidence === "number" ? (parsed.confidence as number) : null;
  const finalConf = confNum ?? rcaConfidence ?? null;
  const chain = Array.isArray(parsed.causalChain)
    ? (parsed.causalChain as unknown[]).filter((x): x is string => typeof x === "string")
    : [];
  const candidates = Array.isArray(parsed.candidates)
    ? (parsed.candidates as Array<Record<string, unknown>>)
    : [];
  const isStub = rc.startsWith("STUB");

  return (
    <div className={box}>
      {header}
      {rc && (
        <div className={`mt-1.5 text-xs ${styles.cardText} ${isStub ? "opacity-60" : ""}`}>{rc}</div>
      )}
      {candidates.length > 0 && (
        <div className="mt-1.5 space-y-1">
          {candidates.map((c, i) => (
            <div key={i} className="flex items-center justify-between text-[10px] font-mono opacity-70">
              <span className="truncate">
                {typeof c.cause === "string" ? c.cause : String(c.cause ?? "")}
              </span>
              {typeof c.prob === "number" && (
                <span className="shrink-0 opacity-60">{((c.prob as number) * 100).toFixed(0)}%</span>
              )}
            </div>
          ))}
        </div>
      )}
      {chain.length > 0 && (
        <div className="mt-1.5 text-[10px] font-mono opacity-60">{chain.join(" → ")}</div>
      )}
      {finalConf !== null && (
        <span className="text-[10px] opacity-60 mt-1.5 inline-block" title={t("dw.dqRule.workOrders.rca_collectedAt")}>
          confidence: {finalConf.toFixed(2)}
        </span>
      )}
    </div>
  );
}

export default function WorkOrderTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [orders, setOrders] = useState<DqWorkOrderVO[]>([]);
  const [statusFilter, setStatusFilter] = useState("");
  const [sevFilter, setSevFilter] = useState("");
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [assignTarget, setAssignTarget] = useState<string | null>(null);
  const [assigneeInput, setAssigneeInput] = useState("");

  const load = useCallback(async () => {
    try {
      const q: { status?: string; severity?: string; sort?: string } = {};
      if (statusFilter) q.status = statusFilter;
      if (sevFilter) q.severity = sevFilter;
      const r = await fetchDqWorkOrders(q);
      let arr = r ?? [];
      // 本地按 severity 降序排列（与后端 sort 语义一致，兼容后端未实现 sort 的情况）
      arr = [...arr].sort((a, b) => severityWeight(b.severity) - severityWeight(a.severity));
      setOrders(arr);
    } finally {
      setLoading(false);
    }
  }, [statusFilter, sevFilter]);

  useEffect(() => {
    setLoading(true);
    void load();
  }, [load]);

  /** KPI 计数（不过滤，独立统计全量） */
  const kpiCounts = useMemo(() => {
    const counts: Record<string, number> = { PENDING: 0, ASSIGNED: 0, IN_WORK: 0, RESOLVED: 0 };
    for (const o of orders) {
      if (o.status in counts) (counts as Record<string, number>)[o.status] += 1;
    }
    return counts;
  }, [orders]);

  /** 通用动作回调（含 toast + 重载） */
  const doAction = useCallback(
    async (id: string, action: string, body?: Record<string, unknown>, successKey?: string) => {
      setBusyId(id);
      try {
        const r = await dqWorkOrderAction(id, action, body);
        if (r.success) {
          const key = successKey ?? `dw.dqRule.workOrders.toast.${action}`;
          showToastGlobal("success", t(key));
          await load();
        } else {
          showToastGlobal("error", r.error ?? t("dw.dqRule.workOrders.actionFailed"));
        }
      } finally {
        setBusyId(null);
      }
    },
    [load, t],
  );

  /** submit 指派 */
  const submitAssign = useCallback(
    async (orderId: string) => {
      const to = assigneeInput.trim();
      if (!to) return;
      setBusyId(orderId);
      try {
        const r = await assignDqWorkOrder(orderId, to);
        if (r.success) {
          showToastGlobal("success", t("dw.dqRule.workOrders.assignSuccess"));
          setAssignTarget(null);
          setAssigneeInput("");
          await load();
        } else {
          showToastGlobal("error", r.error ?? t("dw.dqRule.workOrders.actionFailed"));
        }
      } finally {
        setBusyId(null);
      }
    },
    [assigneeInput, load, t],
  );

  return (
    <div className="flex-1 min-h-0 flex flex-col space-y-4">
      {/* KPI 卡 */}
      <div className="grid grid-cols-4 gap-4">
        {KPI_STATUSES.map((st, idx) => {
          const icons = [Briefcase, UserPlus, Wrench, Check] as const;
          const Icon = icons[idx] ?? Briefcase;
          const count = kpiCounts[st] ?? 0;
          const colors: Record<string, string> = {
            PENDING: "text-red-500",
            ASSIGNED: "text-blue-500",
            IN_WORK: "text-yellow-500",
            RESOLVED: "text-emerald-500",
          };
          return (
            <button
              key={st}
              onClick={() => setStatusFilter(statusFilter === st ? "" : st)}
              className={`rounded-md p-4 text-left cursor-pointer transition border ${
                statusFilter === st ? styles.cardBorder : "transparent"
              } ${styles.cardBg} hover:bg-slate-50 dark:hover:bg-slate-800/40`}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <Icon className={`w-5 h-5 ${colors[st] ?? styles.cardTextMuted}`} />
                  <span className={`text-xs font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>
                    {t(`dw.dqRule.workOrders.status.${st}`)}
                  </span>
                </div>
                <span className={`text-2xl font-bold ${styles.cardText}`}>{count}</span>
              </div>
            </button>
          );
        })}
      </div>

      {/* 过滤区 */}
      <div className="flex items-center gap-2 flex-wrap">
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className={`h-8 rounded-md px-2.5 text-xs border ${styles.cardBorder} ${styles.inputBg} ${styles.cardText}`}
        >
          <option value="">{t("dw.dqRule.alerts.filterAll")}</option>
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {t(`dw.dqRule.workOrders.status.${s}`)}
            </option>
          ))}
        </select>

        <select
          value={sevFilter}
          onChange={(e) => setSevFilter(e.target.value)}
          className={`h-8 rounded-md px-2.5 text-xs border ${styles.cardBorder} ${styles.inputBg} ${styles.cardText}`}
        >
          <option value="">{t("dw.dqRule.alerts.filterAll")}</option>
          {SEVERITY_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {t(`dw.dqRule.workOrders.severity.${s}`)}
            </option>
          ))}
        </select>

        <button
          onClick={() => {
            setLoading(true);
            void load();
          }}
          className="h-8 px-3 rounded-md flex items-center gap-1.5 text-xs border cursor-pointer transition hover:opacity-80"
          style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {t("dw.dqRule.refresh")}
        </button>
      </div>

      {/* 工单列表 */}
      <div className={`flex-1 min-h-0 overflow-y-auto rounded-md border ${styles.cardBorder} ${styles.cardBg}`}>
        {loading && orders.length === 0 ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin opacity-50" />
          </div>
        ) : orders.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-2">
            <ClipboardList className="w-8 h-8 opacity-30" />
            <span className={`text-xs ${styles.cardTextMuted}`}>{t("dw.dqRule.workOrders.noWorkOrders")}</span>
          </div>
        ) : (
          <div>
            {orders.map((o) => {
              const isBusy = busyId === o.id;
              const expanded = expandedId === o.id;
              const isP2p3 = o.severity === "MEDIUM" || o.severity === "LOW";
              return (
                <React.Fragment key={o.id}>
                  <div
                    className={`flex items-center gap-3 px-4 py-3 border-b border-b-slate-100 dark:border-b-slate-800 ${
                      expanded ? "bg-slate-50 dark:bg-slate-800/40" : "hover:bg-slate-50 dark:hover:bg-slate-800/20"
                    } transition`}
                  >
                    {/* severity 徽章 */}
                    <button
                      onClick={() => setExpandedId(expanded ? null : o.id)}
                      className={`shrink-0 cursor-pointer ${severityBadgeClass(o.severity)}`}
                    >
                      <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold tracking-wide">
                        {o.severity}
                      </span>
                    </button>

                    {/* 工单号 + 标题 */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-[10px] opacity-60">{o.orderNo}</span>
                        <span className={`font-medium text-xs truncate ${styles.cardText}`} title={o.title}>
                          {o.title}
                        </span>
                      </div>
                      <div className="flex items-center gap-3 mt-1 text-[10px] font-mono opacity-50">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3" />
                          {fmtTime(o.createdAt)}
                        </span>
                        {o.assignedTo && (
                          <span className="flex items-center gap-1">
                            <User className="w-3 h-3" />
                            {o.assignedTo}
                          </span>
                        )}
                      </div>
                    </div>

                    {/* 状态徽章 */}
                    <span
                      className={`shrink-0 inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium ${woStatusBadgeClass(o.status)}`}
                    >
                      {t(`dw.dqRule.workOrders.status.${o.status}`)}
                    </span>

                    {/* 操作按钮组 */}
                    <div className="shrink-0 flex items-center gap-1">
                      {isBusy ? (
                        <Loader2 className="w-4 h-4 animate-spin opacity-50" />
                      ) : (
                        <>
                          {(o.status === "PENDING") && (
                            <button
                              onClick={() => { setAssignTarget(o.id); setAssigneeInput(""); }}
                              className="h-6 px-2 rounded text-[10px] border cursor-pointer
                                border-blue-500/40 text-blue-600 dark:text-blue-400 hover:bg-blue-500/10"
                              title={t("dw.dqRule.workOrders.action.assign")}
                            >
                              <UserPlus className="w-3 h-3 inline" />
                              {" "}{t("dw.dqRule.workOrders.action.assign")}
                            </button>
                          )}
                          {(o.status === "ASSIGNED") && (
                            <button
                              onClick={() => doAction(o.id, "start")}
                              className="h-6 px-2 rounded text-[10px] border cursor-pointer
                                border-emerald-500/40 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/10"
                              title={t("dw.dqRule.workOrders.action.start")}
                            >
                              <Play className="w-3 h-3 inline" />
                              {" "}{t("dw.dqRule.workOrders.action.start")}
                            </button>
                          )}
                          {(o.status === "IN_WORK") && (
                            <button
                              onClick={() => doAction(o.id, "resolve", { note: "" })}
                              className="h-6 px-2 rounded text-[10px] border cursor-pointer
                                border-teal-500/40 text-teal-600 dark:text-teal-400 hover:bg-teal-500/10"
                              title={t("dw.dqRule.workOrders.action.resolve")}
                            >
                              <Wrench className="w-3 h-3 inline" />
                              {" "}{t("dw.dqRule.workOrders.action.resolve")}
                            </button>
                          )}
                          {(o.status === "RESOLVED") && (
                            <button
                              onClick={() => doAction(o.id, "close")}
                              className="h-6 px-2 rounded text-[10px] border cursor-pointer
                                border-slate-500/40 text-slate-500 dark:text-slate-400 hover:bg-slate-500/10"
                              title={t("dw.dqRule.workOrders.action.close")}
                            >
                              <X className="w-3 h-3 inline" />
                              {" "}{t("dw.dqRule.workOrders.action.close")}
                            </button>
                          )}
                          {isP2p3 && (
                            <button
                              onClick={() => doAction(o.id, "run-rca")}
                              className="h-6 px-2 rounded text-[10px] border cursor-pointer
                                border-purple-500/40 text-purple-600 dark:text-purple-400 hover:bg-purple-500/10 flex items-center gap-0.5"
                              title={t("dw.dqRule.workOrders.runRca")}
                            >
                              <Cpu className="w-3 h-3" />
                              🔍
                            </button>
                          )}
                        </>
                      )}
                    </div>
                  </div>

                  {/* 展开详情 */}
                  {expanded && (
                    <div className="px-5 py-3 bg-slate-50/60 dark:bg-slate-800/30 border-b border-b-slate-100 dark:border-b-slate-800 space-y-2">
                      <div className="grid grid-cols-2 gap-x-6 gap-y-2 text-[11px]">
                        <div>
                          <span className={`opacity-50 ${styles.cardTextMuted}`}>{t("dw.dqRule.workOrders.colDesc")}</span>
                          <div className={`mt-0.5 ${styles.cardText}`}>{o.description || "—"}</div>
                        </div>
                        <div>
                          <span className={`opacity-50 ${styles.cardTextMuted}`}>handlingMode</span>
                          <div className={`mt-0.5 font-mono ${styles.cardText}`}>{o.handlingMode}</div>
                        </div>
                        {o.repairAction && (
                          <div>
                            <span className={`opacity-50 ${styles.cardTextMuted}`}>repairAction</span>
                            <div className={`mt-0.5 font-mono ${styles.cardText}`}>{o.repairAction}</div>
                          </div>
                        )}
                        {o.repairStatus && (
                          <div>
                            <span className={`opacity-50 ${styles.cardTextMuted}`}>repairStatus</span>
                            <div className={`mt-0.5 ${styles.cardText}`}>{o.repairStatus}</div>
                          </div>
                        )}
                        <div>
                          <span className={`opacity-50 ${styles.cardTextMuted}`}>retryCount</span>
                          <div className={`mt-0.5 font-mono ${styles.cardText}`}>{o.retryCount}</div>
                        </div>
                        {o.resolutionNote && (
                          <div>
                            <span className={`opacity-50 ${styles.cardTextMuted}`}>resolutionNote</span>
                            <div className={`mt-0.5 ${styles.cardText}`}>{o.resolutionNote}</div>
                          </div>
                        )}
                      </div>

                      {/* RCA 区（PMO-48-D T16：rcaResult 真接 cognitive，JSON 解析渲染） */}
                      <RcaResultView
                        rcaResult={o.rcaResult}
                        rcaConfidence={o.rcaConfidence}
                        styles={styles}
                        t={t}
                      />
                    </div>
                  )}

                  {/* 指派弹层 */}
                  {assignTarget === o.id && (
                    <div className="px-5 py-3 bg-blue-50/40 dark:bg-blue-900/20 border-b border-b-slate-100 dark:border-b-slate-800 flex items-center gap-2">
                      <User className="w-4 h-4 text-blue-500 shrink-0" />
                      <input
                        autoFocus
                        value={assigneeInput}
                        onChange={(e) => setAssigneeInput(e.target.value)}
                        onKeyDown={(e) => { if (e.key === "Enter") void submitAssign(o.id); }}
                        placeholder={t("dw.dqRule.workOrders.assignHint")}
                        className={`flex-1 h-7 rounded-md px-2.5 text-xs border ${styles.cardBorder} bg-transparent ${styles.cardText} outline-none`}
                      />
                      <button
                        onClick={() => void submitAssign(o.id)}
                        disabled={!assigneeInput.trim()}
                        className="h-7 px-3 rounded-md text-xs font-medium cursor-pointer transition
                          bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed"
                      >
                        {t("dw.dqRule.workOrders.action.assign")}
                      </button>
                      <button
                        onClick={() => { setAssignTarget(null); setAssigneeInput(""); }}
                        className="h-7 px-2 rounded-md text-xs border cursor-pointer hover:opacity-70"
                        style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}
                      >
                        <X className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  )}
                </React.Fragment>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
