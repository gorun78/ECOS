/**
 * caliber/index.tsx — 口径与指标治理页（F03-16 / B.1）
 *
 * 布局：左 CaliberList（列表）→ 中 CaliberVersionTimeline（版本状态）→ 右 FormulaView + BoundMetricTable。
 * 语义：
 *   - 空态：无口径 → 引导"新建口径"；
 *   - 加载：骨架行 8；
 *   - 错误：ECOS-ONTO-040/042 直显后端 message；
 *   - 不可变：APPROVED 版本公式只读（升版而非改历史）；
 *   - 权限：`callerId` 主体标识仅管理员可见（本页展示 ownerRole 即可，
 *     因 B.1 未要求展示 callerId/主体列表 — 主体缺失视图由前端不做）。
 * 数据源：services/ontologyClient.ts（唯一事实源）。
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Calculator,
  CheckCircle2,
  Clock,
  GitBranch,
  History,
  Info,
  Lock,
  Loader2,
  Plus,
  RefreshCw,
  ShieldAlert,
  Table2,
  User,
  Wand2,
  XCircle,
} from 'lucide-react';
import { useLanguage } from "../../../components/LanguageContext";
import { useTheme } from "../../../components/ThemeContext";
import {
  bindMetricCaliber,
  createCaliber,
  fetchCaliberDetail,
  fetchCalibers,
  type Caliber,
  type CaliberVersion,
} from "../../../services/ontologyClient";

interface CaliberPageProps {
  onToast: (type: "success" | "info" | "error", message: string) => void;
}

/** 版本状态 → 主题色令牌（语义色，禁硬编码 Tailwind）。 */
function versionToneStyle(
  status: string,
  styles: ReturnType<typeof useTheme>["styles"]
): { bg: string; text: string; icon: React.ElementType } {
  if (status === "APPROVED") return { bg: styles.successBg, text: styles.successText, icon: CheckCircle2 };
  if (status === "RETIRED") return { bg: styles.infoBg, text: styles.infoText, icon: Lock };
  if (status === "REVIEWING") return { bg: styles.warningBg, text: styles.cardText, icon: Clock };
  return { bg: styles.infoBg, text: styles.infoText, icon: Clock };
}

export default function CaliberPage({ onToast }: CaliberPageProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // 列表 + 详情态
  const [calibers, setCalibers] = useState<Caliber[]>([]);
  const [loadingList, setLoadingList] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [selectedCode, setSelectedCode] = useState<string | null>(null);
  const [detail, setDetail] = useState<Caliber | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  // 新建/绑定会话态（服务端确认后本地登记）
  const [newCaliberOpen, setNewCaliberOpen] = useState(false);
  const [newCode, setNewCode] = useState("");
  const [newName, setNewName] = useState("");
  const [creating, setCreating] = useState(false);

  const [bindMetricCode, setBindMetricCode] = useState("");
  const [bindVersion, setBindVersion] = useState("");
  const [bindings, setBindings] = useState<Array<{ metricCode: string; formulaVersion: string; frozenAt: string }>>([]);
  const [binding, setBinding] = useState(false);

  const loadList = useCallback(async () => {
    setLoadingList(true);
    setListError(null);
    try {
      const list = await fetchCalibers({ limit: 50 });
      setCalibers(list || []);
      if (!selectedCode && (list || []).length > 0) {
        setSelectedCode(list[0].code);
      }
    } catch (e: any) {
      setListError(e?.message || String(e));
    } finally {
      setLoadingList(false);
    }
  }, [selectedCode]);

  const loadDetail = useCallback(async (code: string) => {
    setLoadingDetail(true);
    setDetailError(null);
    try {
      const vo = await fetchCaliberDetail(code);
      setDetail(vo);
    } catch (e: any) {
      setDetailError(e?.message || String(e));
      setDetail(null);
    } finally {
      setLoadingDetail(false);
    }
  }, []);

  useEffect(() => { loadList(); /* eslint-disable-next-line */ }, []);
  useEffect(() => {
    if (selectedCode) loadDetail(selectedCode);
    else setDetail(null);
  }, [selectedCode, loadDetail]);

  const handleCreateCaliber = async () => {
    if (!newCode.trim() || !newName.trim()) {
      onToast("error", t("ow.caliber.err.required"));
      return;
    }
    setCreating(true);
    try {
      await createCaliber({ code: newCode.trim(), name: newName.trim() });
      onToast("success", t("ow.caliber.msg.created"));
      setNewCaliberOpen(false);
      setNewCode("");
      setNewName("");
      await loadList();
    } catch (e: any) {
      onToast("error", e?.message || String(e));
    } finally {
      setCreating(false);
    }
  };

  const handleBindMetric = async () => {
    if (!detail) return;
    if (!bindMetricCode.trim()) {
      onToast("info", t("ow.caliber.bind.needMetric"));
      return;
    }
    const version = bindVersion || (detail.versions || []).find(v => v.status === "APPROVED")?.version_no || "";
    if (!version) {
      onToast("error", t("ow.caliber.bind.noApprovedVersion"));
      return;
    }
    setBinding(true);
    try {
      await bindMetricCaliber(bindMetricCode.trim(), { caliberCode: detail.code, formulaVersion: version });
      onToast("success", t("ow.caliber.msg.boundOk"));
      setBindings(prev => [
        ...prev,
        { metricCode: bindMetricCode.trim(), formulaVersion: version, frozenAt: new Date().toISOString() },
      ]);
      setBindMetricCode("");
    } catch (e: any) {
      // 直显后端 message（含 ECOS-ONTO-040/041/042）
      onToast("error", e?.message || String(e));
    } finally {
      setBinding(false);
    }
  };

  const versions = useMemo<CaliberVersion[]>(() => detail?.versions || [], [detail]);

  // ── 渲染 ────────────────────────────────────────────
  return (
    <div className="p-4 h-full overflow-hidden text-xs">
      <div className="grid grid-cols-12 gap-3 h-full min-h-0">

        {/* ── 左：口径列表 ─────────────────────────────── */}
        <section className={`col-span-3 rounded-lg border ${styles.cardBorder} ${styles.cardBg} flex flex-col h-full min-h-0`}>
          <header className={`flex items-center justify-between px-3 py-2 border-b ${styles.appBorder} ${styles.cardTextMuted}`}>
            <div className="flex items-center gap-1.5 font-bold">
              <Calculator size={13} />
              <span>{t("ow.caliber.list.title")}</span>
              <span className={`ml-1 px-1.5 rounded text-[9px] ${styles.infoBg} ${styles.infoText}`}>{calibers.length}</span>
            </div>
            <div className="flex items-center gap-1">
              <button
                onClick={loadList}
                className={`p-1 rounded ${styles.sidebarHoverBg} ${styles.muted}`}
                title={t("ow.caliber.actions.refresh")}
              >
                <RefreshCw size={11} className={loadingList ? "animate-spin" : ""} />
              </button>
              <button
                onClick={() => setNewCaliberOpen(v => !v)}
                className={`p-1 rounded ${styles.sidebarHoverBg} ${styles.accentText}`}
                title={t("ow.caliber.actions.new")}
              >
                <Plus size={12} />
              </button>
            </div>
          </header>

          {/* 无假真理·limit50 截断提示：后端无分页端点，第 51 条起不可见必须显式声明 */}
          {calibers.length >= 50 && !loadingList && (
            <div className={`px-3 py-1.5 border-b ${styles.appBorder} text-[10px] ${styles.warningText} ${styles.warningBg}`}>
              {t("ow.caliber.list.truncated")}
            </div>
          )}
          {newCaliberOpen && (

            <div className={`px-3 py-2 border-b ${styles.appBorder} ${styles.cardBg} space-y-1.5`}>
              <input
                value={newCode}
                onChange={e => setNewCode(e.target.value)}
                placeholder={t("ow.caliber.form.code")}
                className={`w-full px-2 py-1 rounded border ${styles.appBorder} ${styles.inputBg} ${styles.inputText} text-[10px]`}
              />
              <input
                value={newName}
                onChange={e => setNewName(e.target.value)}
                placeholder={t("ow.caliber.form.name")}
                className={`w-full px-2 py-1 rounded border ${styles.appBorder} ${styles.inputBg} ${styles.inputText} text-[10px]`}
              />
              <div className="flex items-center gap-1 pt-1">
                <button
                  onClick={() => setNewCaliberOpen(false)}
                  className={`flex-1 py-1 rounded text-[10px] font-semibold ${styles.inputBg} ${styles.inputText} ${styles.sidebarHoverBg}`}
                >
                  {t("ow.btn.cancel")}
                </button>
                <button
                  onClick={handleCreateCaliber}
                  disabled={creating}
                  className={`flex-1 py-1 rounded text-[10px] font-semibold ${styles.accentBg} text-white ${styles.accentHover} disabled:opacity-50`}
                >
                  {creating ? <Loader2 size={10} className="inline animate-spin" /> : t("ow.btn.create")}
                </button>
              </div>
            </div>
          )}

          <div className="flex-1 min-h-0 overflow-y-auto p-2 space-y-1">
            {loadingList && calibers.length === 0 && (
              <div className="space-y-1.5">
                {Array.from({ length: 8 }).map((_, i) => (
                  <div key={i} className={`h-8 rounded ${styles.inputBg} animate-pulse`} />
                ))}
              </div>
            )}
            {!loadingList && listError && (
              <div className={`flex flex-col items-center py-4 text-[10px] ${styles.dangerText}`}>
                <ShieldAlert size={16} className="mb-1" />
                <span className={`font-mono ${styles.dangerText}`}>{listError}</span>
              </div>
            )}
            {!loadingList && !listError && calibers.length === 0 && (
              <div className={`flex flex-col items-center py-6 text-[10px] ${styles.muted}`}>
                <Calculator size={20} className="mb-1" />
                <span>{t("ow.caliber.empty.title")}</span>
                <button
                  onClick={() => setNewCaliberOpen(true)}
                  className={`mt-2 px-2 py-0.5 rounded ${styles.accentBg} text-white`}
                >
                  {t("ow.caliber.actions.new")}
                </button>
              </div>
            )}
            {calibers.map(c => {
              const active = selectedCode === c.code;
              return (
                <button
                  key={c.id}
                  onClick={() => setSelectedCode(c.code)}
                  className={`w-full text-left px-2.5 py-1.5 rounded-md flex items-center justify-between gap-2 transition-colors ${
                    active
                      ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText} font-semibold border-l-2 ${styles.accentBorder}`
                      : `${styles.sidebarText} ${styles.sidebarHoverBg}`
                  }`}
                >
                  <div className="min-w-0">
                    <div className="truncate font-semibold">{c.name}</div>
                    <div className={`font-mono text-[9px] truncate ${styles.muted}`}>{c.code}</div>
                  </div>
                  {c.status && (
                    <span className={`shrink-0 px-1.5 py-0.5 rounded text-[9px] font-semibold ${styles.infoBg} ${styles.infoText}`}>
                      {c.status}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </section>

        {/* ── 中：版本时间线 ────────────────────────────── */}
        <section className={`col-span-5 rounded-lg border ${styles.cardBorder} ${styles.cardBg} flex flex-col h-full min-h-0`}>
          <header className={`flex items-center justify-between px-3 py-2 border-b ${styles.appBorder} ${styles.cardTextMuted}`}>
            <div className="flex items-center gap-1.5 font-bold">
              <History size={13} />
              <span>{t("ow.caliber.timeline.title")}</span>
              <span className={`ml-1 font-mono ${styles.muted}`}>{detail?.code || "-"}</span>
            </div>
          </header>
          <div className="flex-1 min-h-0 overflow-y-auto p-3">
            {loadingDetail && (
              <div className="space-y-2">
                {Array.from({ length: 5 }).map((_, i) => (
                  <div key={i} className={`h-14 rounded ${styles.inputBg} animate-pulse`} />
                ))}
              </div>
            )}
            {!loadingDetail && detailError && (
              <div className={`flex flex-col items-center py-6 text-[10px] ${styles.dangerText}`}>
                <ShieldAlert size={18} className="mb-1" />
                <span className="font-mono text-center max-w-md break-all">{detailError}</span>
              </div>
            )}
            {!loadingDetail && !detailError && !detail && (
              <div className={`flex flex-col items-center py-6 text-[10px] ${styles.muted}`}>
                <History size={20} className="mb-1" />
                <span>{t("ow.caliber.timeline.empty")}</span>
              </div>
            )}
            {!loadingDetail && !detailError && detail && (
              <>
                <div className={`mb-3 rounded border ${styles.appBorder} ${styles.cardBg} p-2.5 space-y-1`}>
                  <div className={`flex items-center gap-2 ${styles.cardText}`}>
                    <Calculator size={13} className={styles.accentText} />
                    <span className="font-semibold">{detail.name}</span>
                    <span className={`font-mono ${styles.muted}`}>{detail.code}</span>
                  </div>
                  <div className={`flex flex-wrap items-center gap-2 text-[10px] ${styles.muted}`}>
                    {detail.ownerRole && (
                      <span className="inline-flex items-center gap-1">
                        <User size={10} /> {detail.ownerRole}
                      </span>
                    )}
                    {detail.unit && <span>{t("ow.caliber.field.unit")}: {detail.unit}</span>}
                    {detail.currency && <span>{t("ow.caliber.field.currency")}: {detail.currency}</span>}
                    {detail.domain && <span className={`font-mono px-1 rounded ${styles.inputBg}`}>{detail.domain}</span>}
                  </div>
                </div>
                <div className="space-y-2">
                  {versions.length === 0 && (
                    <div className={`flex flex-col items-center py-4 text-[10px] ${styles.muted}`}>
                      <History size={16} className="mb-1" />
                      <span>{t("ow.caliber.timeline.noVersions")}</span>
                    </div>
                  )}
                  {versions.map(v => {
                    const key = String(v.version_no || v.versionNo || "-");
                    const status = String(v.status || "DRAFT");
                    const tone = versionToneStyle(status, styles);
                    const readOnly = status === "APPROVED" || status === "RETIRED";
                    return (
                      <div
                        key={`${detail.code}-${key}`}
                        className={`rounded border ${styles.appBorder} ${styles.cardBg} p-2.5 flex items-start gap-2`}
                      >
                        <div className={`shrink-0 inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-semibold ${tone.bg} ${tone.text}`}>
                          <tone.icon size={10} /> {status}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="font-mono font-semibold">{key}</span>
                            {v.git_ref || v.gitRef ? (
                              <span className={`inline-flex items-center gap-1 text-[9px] font-mono ${styles.muted}`}>
                                <GitBranch size={9} />
                                {String(v.git_ref || v.gitRef)}
                              </span>
                            ) : null}
                            <span className={`text-[9px] ${styles.muted}`}>
                              {(v.published_at || v.publishedAt || "").toString().slice(0, 19).replace("T", " ")}
                            </span>
                          </div>
                          <div className="mt-1.5">
                            <FormulaView formula={String(v.formula || "")} readOnly={readOnly} styles={styles} t={t} />
                          </div>
                          <div className={`mt-1 flex flex-wrap items-center gap-2 text-[9px] ${styles.muted}`}>
                            {typeof v.additive === "boolean" && (
                              <span>{t("ow.caliber.field.additive")}: {v.additive ? "Y" : "N"}</span>
                            )}
                            {(v.period_granularity || v.periodGranularity) && (
                              <span>{t("ow.caliber.field.period")}: {String(v.period_granularity || v.periodGranularity)}</span>
                            )}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </>
            )}
          </div>
        </section>

        {/* ── 右：绑定指标 ────────────────────────────── */}
        <section className={`col-span-4 rounded-lg border ${styles.cardBorder} ${styles.cardBg} flex flex-col h-full min-h-0`}>
          <header className={`flex items-center justify-between px-3 py-2 border-b ${styles.appBorder} ${styles.cardTextMuted}`}>
            <div className="flex items-center gap-1.5 font-bold">
              <Table2 size={13} />
              <span>{t("ow.caliber.bound.title")}</span>
              <span title={t("ow.caliber.bound.session_only")} className={`ml-1 px-1.5 rounded text-[9px] ${styles.infoBg} ${styles.infoText}`}>{bindings.length}</span>
            </div>
            <Wand2 size={13} className={styles.accentText} />
          </header>
          <div className="flex-1 min-h-0 overflow-y-auto p-3 space-y-3">
            <div className={`rounded border ${styles.appBorder} ${styles.inputBg} p-2.5 space-y-1.5`}>
              <div className={`text-[10px] font-semibold ${styles.muted}`}>{t("ow.caliber.bind.hint")}</div>
              <input
                value={bindMetricCode}
                onChange={e => setBindMetricCode(e.target.value)}
                placeholder={t("ow.caliber.bind.placeholder.metric")}
                disabled={!detail}
                className={`w-full px-2 py-1 rounded border ${styles.appBorder} ${styles.inputBg} ${styles.inputText} text-[10px] disabled:opacity-50`}
              />
              <input
                value={bindVersion}
                onChange={e => setBindVersion(e.target.value)}
                placeholder={t("ow.caliber.bind.placeholder.version")}
                disabled={!detail}
                className={`w-full px-2 py-1 rounded border ${styles.appBorder} ${styles.inputBg} ${styles.inputText} text-[10px] font-mono disabled:opacity-50`}
              />
              <button
                onClick={handleBindMetric}
                disabled={!detail || binding}
                className={`w-full py-1.5 rounded text-[10px] font-semibold ${styles.accentBg} text-white ${styles.accentHover} disabled:opacity-40`}
              >
                {binding ? <Loader2 size={10} className="inline animate-spin" /> : t("ow.caliber.bind.submit")}
              </button>
              <div className={`flex items-start gap-1 text-[9px] ${styles.muted}`}>
                <ShieldAlert size={10} className="mt-0.5 shrink-0" />
                <span>{t("ow.caliber.bind.policy")}</span>
              </div>
              {/* 无假真理：后端仅有 POST binding（提交即落库），无 GET 回读端点 ⇒
                  本列表只可能是「本会话成功提交」的集合，已落库历史绑定不可见，
                  计数徽章同此口径，必须显式声明（此前静默显示 0 条 = 假空态误导）。 */}
              <div className={`flex items-start gap-1 text-[10px] ${styles.warningText}`}>
                <Info size={10} className="mt-0.5 shrink-0" />
                <span>{t("ow.caliber.bound.session_only")}</span>
              </div>
            </div>

            {bindings.length === 0 ? (
              <div className={`flex flex-col items-center py-4 text-[10px] ${styles.muted}`}>
                <XCircle size={16} className="mb-1" />
                <span>{t("ow.caliber.bound.empty")}</span>
              </div>
            ) : (
              <div className="space-y-1.5">
                {bindings.map((b, i) => (
                  <div key={`${b.metricCode}-${i}`} className={`rounded border ${styles.appBorder} ${styles.cardBg} p-2`}>
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono font-semibold">{b.metricCode}</span>
                      <span className={`font-mono text-[9px] ${styles.muted}`}>v{b.formulaVersion}</span>
                    </div>
                    <div className={`mt-1 text-[9px] ${styles.muted}`}>
                      {t("ow.caliber.bound.frozenAt")}: {b.frozenAt.slice(0, 19).replace("T", " ")}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

// ── 副组件：公式片段（只读/可编辑两态） ─────────────────────
function FormulaView({
  formula,
  readOnly,
  styles,
  t,
}: {
  formula: string;
  readOnly: boolean;
  styles: ReturnType<typeof useTheme>["styles"];
  t: (k: string, p?: string) => string;
}) {
  if (!formula) {
    return <div className={`text-[10px] ${styles.muted}`}>{t("ow.caliber.formula.empty")}</div>;
  }
  return (
    <div
      className={`rounded border ${styles.appBorder} ${styles.inputBg} px-2 py-1.5 font-mono text-[10px] ${styles.inputText} whitespace-pre-wrap break-words`}
      title={readOnly ? t("ow.caliber.formula.readonly") : undefined}
    >
      {formula}
      {readOnly && (
        <span className={`inline-block ml-2 ${styles.muted}`}>
          <Lock size={9} className="inline" />
        </span>
      )}
    </div>
  );
}
