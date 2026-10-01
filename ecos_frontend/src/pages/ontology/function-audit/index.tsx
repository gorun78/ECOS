/**
 * function-audit/index.tsx — Function 审计页（F03-16 / B.5）
 *
 * 意图：回答"哪个函数被谁在何时执行、结果与耗时"。
 * 字段：functionName / callerId / status / expression(≤512 可展开) / durationMs / cacheHit / traceId / createdAt。
 * 交互：
 *   - 筛选：status / callerId（参数化，禁前端拼 SQL）；
 *   - traceId 可复制（用于分册 00 链路追踪）；
 *   - 空态：解释"审计上下文缺失的执行已被拒绝（400 ECOS-ONTO-060），不会写入本表"；
 *   - 权限：非管理员只见自身 caller_id，主体列表仅管理员（避免人员目录泄露）。
 * 数据源：services/ontologyClient.ts（唯一事实源）。
 */
import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Activity,
  CheckCircle2,
  Copy,
  Filter,
  Loader2,
  RefreshCw,
  Search,
  ShieldAlert,
} from "lucide-react";
import { useLanguage } from "../../../components/LanguageContext";
import { useTheme } from "../../../components/ThemeContext";
import {
  fetchFunctionAudit,
  type FunctionAuditRow,
} from "../../../services/ontologyClient";

interface FunctionAuditPageProps {
  onToast: (type: "success" | "info" | "error", message: string) => void;
}

/** 当前登录用户名（非管理员只看自身 callerId）。 */
function currentUsername(): string {
  try {
    const raw = localStorage.getItem("ecos_user");
    if (raw) {
      const p = JSON.parse(raw);
      if (typeof p === "object" && p && p.username) return String(p.username);
    }
  } catch {
    /* ignore */
  }
  return localStorage.getItem("ecos_user") || "admin";
}

const PAGE_SIZE = 20;

/** 状态 → 主题语义色。 */
function statusTone(
  status: string,
  styles: ReturnType<typeof useTheme>["styles"]
): string {
  if (status === "SUCCESS") return `${styles.successBg} ${styles.successText}`;
  if (status === "FORBIDDEN") return `${styles.warningBg} ${styles.cardText}`;
  if (status === "ERROR") return `${styles.dangerBg} ${styles.dangerText}`;
  return `${styles.infoBg} ${styles.infoText}`;
}

export default function FunctionAuditPage({ onToast }: FunctionAuditPageProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const [rows, setRows] = useState<FunctionAuditRow[]>([]);
  const [total, setTotal] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 筛选参数（参数化，禁拼 SQL）
  const [statusFilter, setStatusFilter] = useState("");
  const [callerFilter, setCallerFilter] = useState("");

  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());

  const me = useMemo(() => currentUsername(), []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const vo = await fetchFunctionAudit({
        status: statusFilter || undefined,
        callerId: callerFilter || undefined,
        page,
        pageSize: PAGE_SIZE,
      });
      // 非管理员：只保留自身 caller_id 的行（客户端侧二次护栏；服务端未强制时兜底）
      const items = (vo?.items || []).filter(
        r => callerFilter || String(r.caller_id || "") === me
      );
      setRows(items);
      setTotal(vo?.total ?? items.length);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [statusFilter, callerFilter, page, me]);

  useEffect(() => {
    load();
  }, [load]);

  const copyTrace = useCallback(
    async (traceId: string | undefined) => {
      if (!traceId) return;
      try {
        await navigator.clipboard.writeText(traceId);
        onToast("success", t("ow.fnAudit.msg.copied"));
      } catch {
        onToast("error", t("ow.fnAudit.msg.copyFailed"));
      }
    },
    [onToast, t]
  );

  const toggleExpanded = (id: string) =>
    setExpandedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const hasExpr = (r: FunctionAuditRow) => !!r.expression && String(r.expression).length > 80;
  const fmtDate = (v?: string | number) =>
    v ? String(v).slice(0, 19).replace("T", " ") : "—";

  return (
    <div className="p-4 h-full overflow-hidden text-xs">
      {/* 筛选工具条 */}
      <div className={`flex flex-wrap items-center gap-2 mb-3 rounded-lg border ${styles.cardBorder} ${styles.cardBg} px-3 py-2`}>
        <span className={`flex items-center gap-1.5 font-semibold ${styles.cardTextMuted}`}>
          <Filter size={12} /> {t("ow.fnAudit.filters.title")}
        </span>
        <select
          value={statusFilter}
          onChange={e => { setStatusFilter(e.target.value); setPage(1); }}
          className={`px-2 py-1 rounded border ${styles.appBorder} ${styles.inputBg} ${styles.inputText} text-[10px]`}
        >
          <option value="">{t("ow.fnAudit.status.all")}</option>
          <option value="SUCCESS">{t("ow.fnAudit.status.SUCCESS")}</option>
          <option value="FORBIDDEN">{t("ow.fnAudit.status.FORBIDDEN")}</option>
          <option value="ERROR">{t("ow.fnAudit.status.ERROR")}</option>
        </select>
        <div className="relative">
          <Search size={11} className={`absolute left-2 top-1/2 -translate-y-1/2 ${styles.muted}`} />
          <input
            value={callerFilter}
            onChange={e => { setCallerFilter(e.target.value); setPage(1); }}
            placeholder={t("ow.fnAudit.filters.caller")}
            className={`pl-6 pr-2 py-1 rounded border ${styles.appBorder} ${styles.inputBg} ${styles.inputText} text-[10px] w-44`}
          />
        </div>
        <button
          onClick={() => { setStatusFilter(""); setCallerFilter(""); setPage(1); }}
          className={`px-2 py-1 rounded text-[10px] font-semibold ${styles.inputBg} ${styles.inputText} ${styles.sidebarHoverBg}`}
        >
          {t("ow.fnAudit.filters.reset")}
        </button>
        <div className="ml-auto flex items-center gap-2">
          {total !== null && (
            <span className={`text-[10px] ${styles.muted}`}>{t("ow.fnAudit.total")}: {total}</span>
          )}
          <button
            onClick={load}
            className={`flex items-center gap-1 px-2 py-1 rounded text-[10px] font-semibold ${styles.inputBg} ${styles.inputText} ${styles.sidebarHoverBg}`}
          >
            <RefreshCw size={10} className={loading ? "animate-spin" : ""} /> {t("ow.fnAudit.refresh")}
          </button>
        </div>
      </div>

      {/* 表格 */}
      <div className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} h-[calc(100%-4.5rem)] min-h-0 overflow-auto`}>
        {loading && rows.length === 0 ? (
          <div className="space-y-2 p-3">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className={`h-9 rounded ${styles.inputBg} animate-pulse`} />
            ))}
          </div>
        ) : error ? (
          <div className={`flex flex-col items-center py-10 text-[11px] ${styles.dangerText}`}>
            <ShieldAlert size={20} className="mb-2" />
            <span className="font-mono text-center max-w-lg break-all">{error}</span>
          </div>
        ) : rows.length === 0 ? (
          <div className={`flex flex-col items-center py-12 text-[11px] ${styles.muted}`}>
            <Activity size={22} className="mb-2" />
            <span className="font-semibold">{t("ow.fnAudit.empty.title")}</span>
            <span className="mt-1 max-w-md text-center">{t("ow.fnAudit.empty.desc")}</span>
          </div>
        ) : (
          <table className="w-full text-[10px] border-collapse">
            <thead className={`sticky top-0 ${styles.cardBg}`}>
              <tr className={`text-left ${styles.cardTextMuted} border-b ${styles.appBorder}`}>
                <th className="px-2 py-1.5 font-semibold">{t("ow.fnAudit.col.function")}</th>
                <th className="px-2 py-1.5 font-semibold">{t("ow.fnAudit.col.caller")}</th>
                <th className="px-2 py-1.5 font-semibold">{t("ow.fnAudit.col.status")}</th>
                <th className="px-2 py-1.5 font-semibold min-w-[10rem]">{t("ow.fnAudit.col.expression")}</th>
                <th className="px-2 py-1.5 font-semibold text-right">{t("ow.fnAudit.col.duration")}</th>
                <th className="px-2 py-1.5 font-semibold text-center">{t("ow.fnAudit.col.cache")}</th>
                <th className="px-2 py-1.5 font-semibold">{t("ow.fnAudit.col.trace")}</th>
                <th className="px-2 py-1.5 font-semibold">{t("ow.fnAudit.col.time")}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(r => {
                const id = String(r.id);
                const status = String(r.status || "UNKNOWN");
                const expr = String(r.expression || "");
                const expanded = expandedIds.has(id);
                const caller = String(r.caller_id || "—");
                return (
                  <tr key={id} className={`border-b ${styles.appBorder} ${styles.cardText}`}>
                    <td className="px-2 py-1.5 font-mono">{r.function_name || r.functionName || "—"}</td>
                    <td className="px-2 py-1.5 font-mono">{caller}</td>
                    <td className="px-2 py-1.5">
                      <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-semibold ${statusTone(status, styles)}`}>
                        {status === "SUCCESS" && <CheckCircle2 size={9} />}
                        {status === "FORBIDDEN" && <ShieldAlert size={9} />}
                        {status}
                      </span>
                    </td>
                    <td className="px-2 py-1.5">
                      <button
                        onClick={() => hasExpr(r) && toggleExpanded(id)}
                        className={`font-mono text-left max-w-[20rem] ${hasExpr(r) ? "cursor-pointer hover:underline" : "cursor-default"} truncate inline-block align-middle`}
                        title={expr}
                        style={expanded ? { whiteSpace: "normal", wordBreak: "break-all" } : { whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}
                      >
                        {expr || "—"}
                      </button>
                    </td>
                    <td className="px-2 py-1.5 text-right font-mono">
                      {String(r.duration_ms ?? r.durationMs ?? "—")}
                    </td>
                    <td className="px-2 py-1.5 text-center">
                      {Number(r.is_cache_hit ?? (typeof r.cacheHit === "boolean" ? (r.cacheHit ? 1 : 0) : 0)) === 1
                        ? <span className={`${styles.successText}`}>HIT</span>
                        : <span className={styles.muted}>MISS</span>}
                    </td>
                    <td className="px-2 py-1.5">
                      <span className="inline-flex items-center gap-1">
                        <span className="font-mono max-w-[9rem] truncate inline-block align-middle">{r.trace_id || "—"}</span>
                        {r.trace_id && (
                          <button onClick={() => copyTrace(r.trace_id)} className={`${styles.muted} hover:opacity-70`} title={t("ow.fnAudit.copyTrace")}>
                            <Copy size={10} />
                          </button>
                        )}
                      </span>
                    </td>
                    <td className="px-2 py-1.5 font-mono whitespace-nowrap">{fmtDate(r.create_time || r.created_at)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* 分页 */}
      {!loading && rows.length > 0 && total !== null && total > PAGE_SIZE && (
        <div className={`flex items-center justify-between mt-2 text-[10px] ${styles.muted}`}>
          <span>{t("ow.fnAudit.page")}: {page}</span>
          <div className="flex items-center gap-1">
            <button
              disabled={page <= 1}
              onClick={() => setPage(p => p - 1)}
              className={`px-2 py-0.5 rounded ${styles.inputBg} ${styles.inputText} ${styles.sidebarHoverBg} disabled:opacity-40`}
            >
              {t("ow.fnAudit.prev")}
            </button>
            <button
              disabled={page * PAGE_SIZE >= total}
              onClick={() => setPage(p => p + 1)}
              className={`px-2 py-0.5 rounded ${styles.inputBg} ${styles.inputText} ${styles.sidebarHoverBg} disabled:opacity-40`}
            >
              {t("ow.fnAudit.next")}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
