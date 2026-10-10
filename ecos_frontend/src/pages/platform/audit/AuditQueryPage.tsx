/**
 * AuditQueryPage — 审计查询页 (#/platform/security/audit)
 * 设计：docs/30-设计/详细设计-01-安全域-security与审计底座-2026-09-28.md §三 B 章 3.2。
 *
 * 顶部过滤条（时间窗默认 7d/最大 90d、结果 success|denied|all、userId/action/traceId/分页）
 * + 列表（时间/用户/动作/资源/结果/IP/链验证状态）+ 右抽屉详情（detail_json 折叠 + 链哈希）。
 * 「验证链完整性」→ POST /api/v1/security/audit/verify-chain，结果写 aria-live。
 * 数据只经 GET /api/v1/security/audit/logs（禁前端拼引擎端点）。
 * 边界：结果 >10k → 收窄提示；503 离线穿透（错误条保留上次数据）。
 */
import React, { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  Search,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  Inbox,
  AlertTriangle,
  Link2,
  CheckCircle2,
  XCircle,
} from "lucide-react";
import { useLanguage } from "../../../components/LanguageContext";
import { useTheme } from "../../../components/ThemeContext";
import { useToast } from "../../../components/common/Toast";
import {
  listAuditLogs,
  getAuditLog,
  verifyChain,
  type AuditLog,
  type ChainVerifyResult,
} from "../../../services/security";
import AuditRow from "./components/AuditRow";
import AuditDetailDrawer from "./components/AuditDetailDrawer";

const PAGE_SIZE = 20;
const MAX_WINDOW_DAYS = 90;
const MAX_RESULTS = 10_000;

type Result = "all" | "success" | "denied";

/** 时间窗：默认近 7 天，钳制到 90 天上限。返回 { from, to } ISO 或 undefined。 */
function windowBounds(days: number): { from?: string; to?: string } {
  const d = Math.min(Math.max(1, days || 7), MAX_WINDOW_DAYS);
  const to = new Date();
  const from = new Date(to.getTime() - d * 86_400_000);
  return { from: from.toISOString(), to: to.toISOString() };
}

export default function AuditQueryPage() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const { showToast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const days = Math.min(Math.max(1, parseInt(searchParams.get("days") ?? "7", 10) || 7), MAX_WINDOW_DAYS);
  const result = (searchParams.get("result") ?? "all") as Result;
  const userId = searchParams.get("userId") ?? "";
  const action = searchParams.get("action") ?? "";
  const traceId = searchParams.get("traceId") ?? "";
  const page = Math.max(1, parseInt(searchParams.get("page") ?? "1", 10) || 1);
  const auditId = searchParams.get("auditId");

  const { from, to } = windowBounds(days);

  const [rows, setRows] = useState<AuditLog[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | undefined>(undefined);
  const [traceIdErr, setTraceIdErr] = useState<string | undefined>(undefined);

  const [verify, setVerify] = useState<ChainVerifyResult | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [liveText, setLiveText] = useState("");

  const [detail, setDetail] = useState<AuditLog | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const inFlightRef = useRef(false);

  const setQuery = useCallback(
    (patch: Record<string, string | number>) => {
      setSearchParams((prev) => {
        const sp = new URLSearchParams(prev);
        for (const [k, v] of Object.entries(patch)) sp.set(k, String(v));
        return sp;
      });
    },
    [setSearchParams]
  );

  const fetchOnce = useCallback(async () => {
    if (inFlightRef.current) return;
    inFlightRef.current = true;
    setLoading(true);
    setError(undefined);
    setTraceIdErr(undefined);
    try {
      const res = await listAuditLogs({ from, to, userId, action, traceId, result, page, size: PAGE_SIZE });
      setRows(res.items);
      setTotal(res.total);
      if (res.total > MAX_RESULTS) {
        setTraceIdErr(t("platform.audit.state.windowTooWide"));
      }
    } catch (e) {
      // 离线/503 显式穿透：保留上次 rows
      setError(String((e as Error)?.message ?? ""));
      const m = String((e as Error)?.message ?? "").match(/traceId[:"\s]+([0-9a-fA-F-]{8,})/i);
      if (m) setTraceIdErr(m[1]);
    } finally {
      inFlightRef.current = false;
      setLoading(false);
    }
  }, [from, to, userId, action, traceId, result, page, t]);

  useEffect(() => {
    void fetchOnce();
  }, [fetchOnce]);

  const openAudit = useCallback(
    (a: AuditLog) => {
      setDetail({ ...a });
      setDetailLoading(true);
      setSearchParams((prev) => {
        const sp = new URLSearchParams(prev);
        sp.set("auditId", a.id);
        return sp;
      });
      getAuditLog(a.id)
        .then((fresh) => setDetail(fresh))
        .catch(() => {/* 保留列表数据 */})
        .finally(() => setDetailLoading(false));
    },
    [setSearchParams]
  );

  const closeDrawer = useCallback(() => {
    setDetail(null);
    setSearchParams((prev) => {
      const sp = new URLSearchParams(prev);
      sp.delete("auditId");
      return sp;
    });
  }, [setSearchParams]);

  const doVerifyChain = useCallback(async () => {
    setVerifying(true);
    try {
      const r = await verifyChain();
      setVerify(r);
      setLiveText(
        r.valid
          ? t("platform.audit.chain.verifyOk")
          : t("platform.audit.chain.verifyBroken")
      );
      if (r.valid) showToast("info", t("platform.audit.chain.verifyOk"));
      else showToast("error", t("platform.audit.chain.verifyBroken"));
    } catch (e) {
      setVerify({ valid: false, brokenAt: [], totalChecked: 0 });
      setLiveText(t("platform.audit.chain.verifyBroken"));
      showToast("error", String((e as Error)?.message ?? ""));
    } finally {
      setVerifying(false);
    }
  }, [showToast, t]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className={`h-full flex flex-col ${styles.appBg} ${styles.appText}`}>
      {/* 顶栏 */}
      <div className={`flex items-center justify-between px-4 pt-4 pb-3 border-b ${styles.appBorder}`}>
        <div className="flex items-center gap-2">
          <Search className={`w-4 h-4 ${styles.accentText}`} />
          <h1 className={`text-base font-semibold ${styles.cardText}`} data-testid="audit-page-title">
            {t("platform.audit.title")}
          </h1>
        </div>
        <button
          type="button"
          data-testid="audit-verify-chain"
          onClick={() => void doVerifyChain()}
          disabled={verifying}
          aria-busy={verifying}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium cursor-pointer border ${styles.accentBorder} ${styles.accentText} hover:opacity-80 disabled:opacity-50`}
        >
          <Link2 className={`w-3.5 h-3.5 ${verifying ? "animate-spin" : ""}`} />
          {t("platform.audit.action.verifyChain")}
        </button>
      </div>

      {/* 链验证结果（aria-live） */}
      {verify && (
        <div
          className={`px-4 py-2 border-b ${styles.appBorder} text-xs ${verify.valid ? styles.successText : styles.dangerText}`}
          aria-live="polite"
          data-testid="audit-chain-result"
        >
          <span className="inline-flex items-center gap-2">
            {verify.valid ? <CheckCircle2 className="w-4 h-4" /> : <XCircle className="w-4 h-4" />}
            {verify.valid ? t("platform.audit.chain.verifyOk") : t("platform.audit.chain.verifyBroken")}
            {typeof verify.totalChecked === "number" && (
              <span className={styles.muted}>{t("platform.audit.field.chainChecked")} {verify.totalChecked}</span>
            )}
            {!verify.valid && (verify.brokenAt ?? []).length > 0 && (
              <span className={`font-mono ${styles.dangerText}`}>
                {(verify.brokenAt ?? []).join(", ")}
              </span>
            )}
          </span>
        </div>
      )}

      {/* 过滤条 */}
      <div className={`flex items-center gap-2 px-4 py-2 border-b ${styles.appBorder} flex-wrap`}>
        <label className={`flex items-center gap-1.5 text-[10px] font-mono ${styles.muted}`}>
          {t("platform.audit.filter.timeWindow")}
          <select
            data-testid="audit-time-window"
            value={days}
            onChange={(e) => setQuery({ days: e.target.value, page: 1 })}
            className={`px-2 py-1 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} text-xs cursor-pointer`}
            aria-label={t("platform.audit.filter.timeWindow")}
          >
            {[7, 30, 90].map((d) => (
              <option key={d} value={d}>{d}d</option>
            ))}
          </select>
        </label>

        <label className={`flex items-center gap-1.5 text-[10px] font-mono ${styles.muted}`}>
          {t("platform.audit.filter.result")}
          <select
            data-testid="audit-result-filter"
            value={result}
            onChange={(e) => { setQuery({ result: e.target.value, page: 1 }); }}
            className={`px-2 py-1 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} text-xs cursor-pointer`}
            aria-label={t("platform.audit.filter.result")}
          >
            <option value="all">{t("platform.audit.result.all")}</option>
            <option value="success">{t("platform.audit.result.success")}</option>
            <option value="denied">{t("platform.audit.result.denied")}</option>
          </select>
        </label>

        <input
          data-testid="audit-filter-user"
          value={userId}
          onChange={(e) => setQuery({ userId: e.target.value, page: 1 })}
          placeholder={t("platform.audit.field.user")}
          aria-label={t("platform.audit.field.user")}
          className={`px-2 py-1 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} text-xs w-32`}
        />
        <input
          data-testid="audit-filter-action"
          value={action}
          onChange={(e) => setQuery({ action: e.target.value, page: 1 })}
          placeholder={t("platform.audit.field.action")}
          aria-label={t("platform.audit.field.action")}
          className={`px-2 py-1 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} text-xs w-32`}
        />
        <input
          data-testid="audit-filter-trace"
          value={traceId}
          onChange={(e) => setQuery({ traceId: e.target.value, page: 1 })}
          placeholder="traceId"
          aria-label={t("platform.audit.field.traceId")}
          className={`px-2 py-1 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} text-xs font-mono w-40`}
        />

        <div className="flex-1" />
        <button
          type="button"
          onClick={() => void fetchOnce()}
          aria-busy={loading}
          data-testid="audit-refresh"
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium cursor-pointer border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} hover:opacity-80 disabled:opacity-50`}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {t("platform.audit.action.search")}
        </button>

        {totalPages > 1 && (
          <nav aria-label="pagination" className={`flex items-center gap-1 text-[10px] font-mono ${styles.muted}`}>
            <button
              type="button"
              data-testid="audit-prev"
              disabled={page <= 1}
              onClick={() => setQuery({ page: page - 1 })}
              className={`px-2 py-0.5 border rounded ${styles.inputBorder} ${styles.inputText} disabled:opacity-40 cursor-pointer inline-flex items-center`}
            >
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>
            <span className="px-2 tabular-nums">{page}/{totalPages}</span>
            <button
              type="button"
              data-testid="audit-next"
              disabled={page >= totalPages}
              onClick={() => setQuery({ page: page + 1 })}
              className={`px-2 py-0.5 border rounded ${styles.inputBorder} ${styles.inputText} disabled:opacity-40 cursor-pointer inline-flex items-center`}
            >
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </nav>
        )}
      </div>

      {/* 收窄提示（>10k） */}
      {traceIdErr && !error && (
        <div role="alert" data-testid="audit-narrow" className={`px-4 py-2 border-b ${styles.warningBorder} ${styles.dangerBg} text-xs ${styles.dangerText} flex items-center gap-2`}>
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span className="flex-1">{traceIdErr}</span>
          {traceIdErr !== t("platform.audit.state.windowTooWide") && <code className="font-mono text-[10px]">{traceIdErr}</code>}
        </div>
      )}
      {error && (
        <div role="alert" data-testid="audit-error" className={`px-4 py-2 border-b ${styles.dangerBorder} ${styles.dangerBg} text-xs ${styles.dangerText} flex items-center gap-2`}>
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span className="flex-1">{error}</span>
          {traceIdErr && <code className="font-mono text-[10px]">{traceIdErr}</code>}
        </div>
      )}

      {/* 列表 */}
      <div className="flex-1 overflow-y-auto p-4 space-y-2 scrollbar-thin" data-testid="audit-list">
        {loading && rows.length === 0 ? (
          <div className="space-y-2" aria-busy="true">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className={`h-14 rounded-md border ${styles.appBorder} animate-pulse`} />
            ))}
          </div>
        ) : rows.length === 0 ? (
          <div data-testid="audit-empty" className={`${styles.cardBg} border ${styles.cardBorder} rounded-md p-8 flex flex-col items-center gap-3`}>
            <Inbox className={`w-8 h-8 ${styles.muted}`} />
            <div className={`text-sm font-semibold ${styles.cardText}`}>{t("platform.audit.state.empty")}</div>
          </div>
        ) : (
          rows.map((a) => <AuditRow key={a.id} audit={a} onOpen={openAudit} />)
        )}
      </div>

      {/* aria-live 状态变化区 */}
      <div aria-live="polite" aria-atomic="true" data-testid="audit-live" className="sr-only">
        {liveText}
      </div>

      {/* 详情抽屉 */}
      <AuditDetailDrawer audit={detail} loading={detailLoading} onClose={closeDrawer} />
    </div>
  );
}
