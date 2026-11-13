/**
 * Scenario cognition loop: aggregation surface for existing ECOS scenario and Mind APIs.
 * It does not create parallel scenario/Mind state or local-only governance actions.
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { AlertTriangle, ArrowRight, Brain, CircleHelp, GitBranch, RefreshCw, ShieldCheck, type LucideIcon } from "lucide-react";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";
import { apiFetchData } from "../api";
import { WORKSPACE_SCENARIOS_BASE } from "../services/scenarioActionClient";
import { getCompleteness, type CompletenessReportVO } from "../services/scenarioCompletenessClient";
import { fetchMindList, type MindRow } from "../services/cognitive/mindApi";
import logger from "../services/logger";

interface ScenarioSummary { id: string; name: string; businessGoal?: string; description?: string; status?: string; }
type LoadState = { loading: boolean; error: string | null };
type Section = "overview" | "scenario" | "mind";
const SECTIONS: { id: Section; key: string; icon: LucideIcon }[] = [
  { id: "overview", key: "workspace.cognition.overview", icon: GitBranch },
  { id: "scenario", key: "workspace.cognition.scenario", icon: ShieldCheck },
  { id: "mind", key: "workspace.cognition.mind", icon: Brain },
];
function errorText(error: unknown): string { return error instanceof Error ? error.message : String(error); }
function axisLabel(t: (key: string) => string, value: string | null | undefined): string {
  if (!value) return "—";
  const key = `platform.mind.status.${value}`;
  const translated = t(key);
  return translated === key ? `${t("platform.mind.unmappedPrefix")}${value}` : translated;
}
function translatedOr(t: (key: string) => string, key: string, fallback: string): string {
  const translated = t(key);
  return translated === key ? fallback : translated;
}

export default function CognitiveWorkbench() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [section, setSection] = useState<Section>("overview");
  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [scenarioState, setScenarioState] = useState<LoadState>({ loading: true, error: null });
  const [selectedScenarioId, setSelectedScenarioId] = useState("");
  const [report, setReport] = useState<CompletenessReportVO | null>(null);
  const [reportState, setReportState] = useState<LoadState>({ loading: false, error: null });
  const [minds, setMinds] = useState<MindRow[]>([]);
  const [mindColumnsProvisioned, setMindColumnsProvisioned] = useState(false);
  const [mindState, setMindState] = useState<LoadState>({ loading: true, error: null });
  const card = `rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`;
  const heading = `flex items-center gap-2 text-sm font-semibold ${styles.cardText}`;
  const button = `inline-flex min-h-10 items-center justify-center gap-1.5 rounded-md border px-3 py-2 text-xs font-semibold transition hover:opacity-80 ${styles.cardBorder} ${styles.cardText}`;

  const loadScenarios = useCallback(async () => {
    setScenarioState({ loading: true, error: null });
    try {
      const data = await apiFetchData<ScenarioSummary[]>(WORKSPACE_SCENARIOS_BASE);
      const items = Array.isArray(data) ? data : [];
      setScenarios(items);
      setSelectedScenarioId(current => current && items.some(item => item.id === current) ? current : (items[0]?.id ?? ""));
      setScenarioState({ loading: false, error: null });
    } catch (error) {
      logger.error("scenario-cognition-scenarios", error);
      setScenarioState({ loading: false, error: errorText(error) });
      setScenarios([]);
      setSelectedScenarioId("");
    }
  }, []);

  const loadMinds = useCallback(async () => {
    setMindState({ loading: true, error: null });
    try {
      const response = await fetchMindList(50);
      setMinds(response.items);
      setMindColumnsProvisioned(response.r4ColumnsProvisioned);
      setMindState({ loading: false, error: null });
    } catch (error) {
      logger.error("scenario-cognition-minds", error);
      setMindState({ loading: false, error: errorText(error) });
      setMinds([]);
    }
  }, []);

  const loadCompleteness = useCallback(async (scenarioId: string) => {
    if (!scenarioId) { setReport(null); setReportState({ loading: false, error: null }); return; }
    setReportState({ loading: true, error: null });
    setReport(null);
    try {
      setReport(await getCompleteness(scenarioId));
      setReportState({ loading: false, error: null });
    } catch (error) {
      logger.error("scenario-cognition-completeness", error);
      setReportState({ loading: false, error: errorText(error) });
    }
  }, []);

  useEffect(() => { void loadScenarios(); void loadMinds(); }, [loadScenarios, loadMinds]);
  useEffect(() => { void loadCompleteness(selectedScenarioId); }, [selectedScenarioId, loadCompleteness]);
  const selectedScenario = useMemo(() => scenarios.find(item => item.id === selectedScenarioId), [scenarios, selectedScenarioId]);
  const refresh = (): void => { void loadScenarios(); void loadMinds(); if (selectedScenarioId) void loadCompleteness(selectedScenarioId); };
  const coverage = report ? `${report.presentEdges?.length ?? 0} / ${report.requiredEdges?.length ?? 0}` : "—";
  const verdict = report ? translatedOr(t, `workspace.cognition.verdict.${report.verdict}`, report.verdict) : "—";

  return <main className={`flex-1 min-w-0 overflow-y-auto ${styles.appBg} ${styles.appText}`}>
    <div className="mx-auto max-w-7xl space-y-4 p-4 md:p-6">
      <header className="flex flex-col justify-between gap-3 md:flex-row md:items-start">
        <div className="min-w-0">
          <div className={`mb-2 flex items-center gap-2 text-[10px] ${styles.cardTextMuted}`}><ShieldCheck size={13} aria-hidden="true"/>{t("workspace.cognition.source_of_truth")}</div>
          <h1 className={`text-xl font-bold tracking-tight ${styles.cardText}`}>{t("workspace.cognition.title")}</h1>
          <p className={`mt-1 max-w-3xl text-xs leading-relaxed ${styles.cardTextMuted}`}>{t("workspace.cognition.subtitle")}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link to="/workbench" className={button}>{t("workspace.cognition.open_scenarios")} <ArrowRight size={13}/></Link>
          <Link to="/decision/mind" className={button}><Brain size={13}/>{t("workspace.cognition.open_minds")}</Link>
          <button type="button" onClick={refresh} className={button}><RefreshCw size={13}/>{t("workspace.cognition.refresh")}</button>
        </div>
      </header>
      <nav aria-label={t("workspace.cognition.title")} className={`flex gap-1 overflow-x-auto border-b ${styles.cardBorder}`}>
        {SECTIONS.map(({ id, key, icon: Icon }) => <button key={id} type="button" onClick={() => setSection(id)} aria-current={section === id ? "page" : undefined}
          className={`inline-flex min-h-11 shrink-0 items-center gap-2 border-b-2 px-3 text-xs font-semibold transition ${section === id ? `${styles.accentText} ${styles.accentBorder}` : `border-transparent ${styles.cardTextMuted}`}`}>
          <Icon size={14} aria-hidden="true"/>{t(key)}
        </button>)}
      </nav>

      {section === "overview" && <div className="grid grid-cols-1 gap-4 xl:grid-cols-5">
        <section className={`space-y-3 xl:col-span-3 ${card}`}>
          <div className="flex flex-wrap items-center justify-between gap-2"><h2 className={heading}><GitBranch size={15}/>{t("workspace.cognition.selected_scenario")}</h2><Link to="/workbench" className={`text-xs underline ${styles.accentText}`}>{t("workspace.cognition.open_scenarios")}</Link></div>
          {scenarioState.loading ? <Loading text={t("workspace.cognition.loading_scenarios")} styles={styles}/> :
            scenarioState.error ? <ErrorState detail={scenarioState.error} onRetry={() => void loadScenarios()} title={t("workspace.cognition.scenarios_error")} retryLabel={t("workspace.cognition.retry")} styles={styles}/> :
            scenarios.length === 0 ? <EmptyState text={t("workspace.cognition.no_scenarios")} styles={styles}/> :
            <>
              <label className={`block text-xs ${styles.cardTextMuted}`} htmlFor="cognition-scenario-select">{t("workspace.cognition.choose_scenario")}</label>
              <select id="cognition-scenario-select" value={selectedScenarioId} onChange={event => setSelectedScenarioId(event.target.value)} className={`min-h-10 w-full rounded-md border px-3 py-2 text-sm ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
                {scenarios.map(scenario => <option key={scenario.id} value={scenario.id}>{scenario.name}</option>)}
              </select>
              {selectedScenario && <div className={`rounded-md border p-3 ${styles.cardBorder}`}>
                <h3 className={`text-sm font-semibold ${styles.cardText}`}>{selectedScenario.name}</h3>
                <p className={`mt-1 text-xs leading-relaxed ${styles.cardTextMuted}`}>{selectedScenario.businessGoal || selectedScenario.description || t("workspace.cognition.no_description")}</p>
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  {selectedScenario.status && <span className={`rounded border px-2 py-1 text-[10px] ${styles.cardBorder} ${styles.cardTextMuted}`}>{selectedScenario.status}</span>}
                  <Link to={`/workbench/${encodeURIComponent(selectedScenario.id)}`} className={`inline-flex items-center gap-1 text-xs font-semibold ${styles.accentText}`}>{t("workspace.cognition.open_scenario")} <ArrowRight size={12}/></Link>
                </div>
              </div>}
            </>}
        </section>
        <section className={`space-y-3 xl:col-span-2 ${card}`}>
          <div className="flex items-center justify-between gap-2"><h2 className={heading}><ShieldCheck size={15}/>{t("workspace.cognition.readiness")}</h2><span className={`text-sm font-semibold tabular-nums ${report?.blocking ? styles.dangerText : styles.cardText}`}>{coverage}</span></div>
          {reportState.loading ? <Loading text={t("workspace.cognition.loading_completeness")} styles={styles}/> :
            reportState.error ? <ErrorState detail={reportState.error} onRetry={() => void loadCompleteness(selectedScenarioId)} title={t("workspace.cognition.completeness_error")} retryLabel={t("workspace.cognition.retry")} styles={styles}/> :
            !report ? <EmptyState text={t("workspace.cognition.completeness_unavailable")} styles={styles}/> :
            <>
              <div className="flex flex-wrap items-center gap-2">
                <span className={`rounded border px-2 py-1 text-[10px] ${report.blocking ? `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}` : `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`}`}>{verdict}</span>
                {report.blocking && <span className={`inline-flex items-center gap-1 text-[10px] ${styles.dangerText}`}><AlertTriangle size={12}/>{t("workspace.cognition.blocked")}</span>}
              </div>
              <p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>{t("workspace.cognition.completeness_backend_note")}</p>
              <Link to={`/workbench/${encodeURIComponent(selectedScenarioId)}`} className={`inline-flex items-center gap-1 text-xs font-semibold ${styles.accentText}`}>{t("workspace.cognition.inspect_completeness")} <ArrowRight size={12}/></Link>
            </>}
        </section>
        <section className={`space-y-3 xl:col-span-5 ${card}`}>
          <div className="flex flex-wrap items-center justify-between gap-2"><h2 className={heading}><Brain size={15}/>{t("workspace.cognition.mind_title")}</h2><Link to="/decision/mind" className={`text-xs underline ${styles.accentText}`}>{t("workspace.cognition.open_minds")}</Link></div>
          <MindContent minds={minds} state={mindState} provisioned={mindColumnsProvisioned} t={t} styles={styles} onRetry={() => void loadMinds()} axisLabel={value => axisLabel(t, value)}/>
        </section>
      </div>}

      {section === "scenario" && <section className={`space-y-4 ${card}`}>
        <div className="flex flex-wrap items-start justify-between gap-2"><div><h2 className={heading}><ShieldCheck size={15}/>{t("workspace.cognition.scenario")}</h2><p className={`mt-1 text-xs ${styles.cardTextMuted}`}>{t("workspace.cognition.completeness_backend_note")}</p></div>
          {selectedScenario && <Link to={`/workbench/${encodeURIComponent(selectedScenario.id)}`} className={button}>{t("workspace.cognition.open_scenario")} <ArrowRight size={12}/></Link>}</div>
        {scenarioState.loading ? <Loading text={t("workspace.cognition.loading_scenarios")} styles={styles}/> :
          scenarioState.error ? <ErrorState detail={scenarioState.error} onRetry={() => void loadScenarios()} title={t("workspace.cognition.scenarios_error")} retryLabel={t("workspace.cognition.retry")} styles={styles}/> :
          scenarios.length === 0 ? <EmptyState text={t("workspace.cognition.no_scenarios")} styles={styles}/> :
          <div className="space-y-3">
            <label className={`block text-xs ${styles.cardTextMuted}`} htmlFor="cognition-scenario-select-detail">{t("workspace.cognition.choose_scenario")}</label>
            <select id="cognition-scenario-select-detail" value={selectedScenarioId} onChange={event => setSelectedScenarioId(event.target.value)} className={`min-h-10 w-full rounded-md border px-3 py-2 text-sm ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
              {scenarios.map(scenario => <option key={scenario.id} value={scenario.id}>{scenario.name}</option>)}
            </select>
            {reportState.loading ? <Loading text={t("workspace.cognition.loading_completeness")} styles={styles}/> :
              reportState.error ? <ErrorState detail={reportState.error} onRetry={() => void loadCompleteness(selectedScenarioId)} title={t("workspace.cognition.completeness_error")} retryLabel={t("workspace.cognition.retry")} styles={styles}/> :
              report ? <CompletenessDetails report={report} t={t} styles={styles}/> : <EmptyState text={t("workspace.cognition.completeness_unavailable")} styles={styles}/>}
          </div>}
      </section>}

      {section === "mind" && <section className={`space-y-3 ${card}`}>
        <div className="flex flex-wrap items-center justify-between gap-2"><div><h2 className={heading}><Brain size={15}/>{t("workspace.cognition.mind_title")}</h2><p className={`mt-1 text-xs ${styles.cardTextMuted}`}>{t("workspace.cognition.mind_source_note")}</p></div>
          <Link to="/decision/mind" className={button}>{t("workspace.cognition.open_minds")} <ArrowRight size={12}/></Link></div>
        <MindContent minds={minds} state={mindState} provisioned={mindColumnsProvisioned} t={t} styles={styles} onRetry={() => void loadMinds()} axisLabel={value => axisLabel(t, value)}/>
      </section>}

      <section className={`space-y-3 ${card}`}>
        <h2 className={heading}><CircleHelp size={15}/>{t("workspace.cognition.existing_workflows")}</h2>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
          <WorkflowLink to={selectedScenario ? `/workbench/${encodeURIComponent(selectedScenario.id)}` : "/workbench"} title={t("workspace.cognition.scenario_detail_link")} description={t("workspace.cognition.scenario_detail_desc")} styles={styles}/>
          <WorkflowLink to="/decision/mind" title={t("workspace.cognition.mind_governance_link")} description={t("workspace.cognition.mind_governance_desc")} styles={styles}/>
          <WorkflowLink to="/resource-center" title={t("workspace.cognition.asset_center_link")} description={t("workspace.cognition.asset_center_desc")} styles={styles}/>
        </div>
      </section>
      <footer className={`flex flex-wrap items-center justify-between gap-2 border-t pt-3 text-[10px] ${styles.cardBorder} ${styles.cardTextMuted}`}>
        <span className="flex items-center gap-1.5"><ShieldCheck size={12}/>{t("workspace.cognition.footer")}</span><span className="font-mono">ECOS / DECISION / LIVE API</span>
      </footer>
    </div>
  </main>;
}

function MindContent({ minds, state, provisioned, t, styles, onRetry, axisLabel: labelAxis }: {
  minds: MindRow[]; state: LoadState; provisioned: boolean; t: (key: string) => string;
  styles: ReturnType<typeof useTheme>["styles"]; onRetry: () => void; axisLabel: (value: string | null | undefined) => string;
}) {
  if (state.loading) return <Loading text={t("workspace.cognition.loading_minds")} styles={styles}/>;
  if (state.error) return <ErrorState detail={state.error} onRetry={onRetry} title={t("workspace.cognition.minds_error")} retryLabel={t("workspace.cognition.retry")} styles={styles}/>;
  if (!minds.length) return <EmptyState text={t("workspace.cognition.no_minds")} styles={styles}/>;
  return <div className="space-y-3">
    {!provisioned && <div className={`rounded-md border px-3 py-2 text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>{t("workspace.cognition.mind_axes_not_migrated")}</div>}
    <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">{minds.slice(0, 6).map(mind => <article key={mind.mindId} className={`rounded-md border p-3 ${styles.cardBorder}`}>
      <div className="flex items-start justify-between gap-2"><h3 className={`text-xs font-semibold ${styles.cardText}`}>{mind.name || mind.mindKey || mind.mindId}</h3><Brain size={14} className={styles.cardTextMuted}/></div>
      {mind.mindKey && <p className={`mt-1 font-mono text-[10px] ${styles.cardTextMuted}`}>{mind.mindKey}</p>}
      <p className={`mt-2 line-clamp-3 text-xs leading-relaxed ${styles.cardTextMuted}`}>{mind.description || t("workspace.cognition.no_description")}</p>
      <dl className="mt-3 space-y-1.5">
        <AxisRow label={t("platform.mind.axis.validity")} value={labelAxis(mind.validityStatus)} styles={styles}/>
        <AxisRow label={t("platform.mind.axis.review")} value={labelAxis(mind.reviewStatus)} styles={styles}/>
        <AxisRow label={t("platform.mind.axis.adoption")} value={labelAxis(mind.adoptionStatus)} styles={styles}/>
      </dl>
      <Link to={`/decision/mind/${encodeURIComponent(mind.mindId)}`} className={`mt-3 inline-flex items-center gap-1 text-xs font-semibold ${styles.accentText}`}>{t("workspace.cognition.review_mind")} <ArrowRight size={12}/></Link>
    </article>)}</div>
  </div>;
}
function AxisRow({ label, value, styles }: { label: string; value: string; styles: ReturnType<typeof useTheme>["styles"] }) {
  return <div className="flex items-start justify-between gap-2 text-[10px]"><dt className={styles.cardTextMuted}>{label}</dt><dd className={`text-right ${styles.cardText}`}>{value}</dd></div>;
}
function CompletenessDetails({ report, t, styles }: { report: CompletenessReportVO; t: (key: string) => string; styles: ReturnType<typeof useTheme>["styles"] }) {
  const present = report.presentEdges ?? [], missing = report.missingEdges ?? [], required = report.requiredEdges ?? [];
  return <div className="space-y-4">
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
      <Metric label={t("workspace.cognition.required_edges")} value={String(required.length)} styles={styles}/>
      <Metric label={t("workspace.cognition.present_edges")} value={String(present.length)} styles={styles}/>
      <Metric label={t("workspace.cognition.missing_edges")} value={String(missing.length)} styles={styles}/>
    </div>
    {present.length > 0 && <div className="space-y-2"><h3 className={`text-xs font-semibold ${styles.cardText}`}>{t("workspace.cognition.connected_edges")}</h3>{present.map((edge, i) => <div key={`${edge.type}-${edge.sourceId}-${i}`} className={`flex flex-col gap-1 rounded-md border p-3 text-xs sm:flex-row sm:items-center sm:justify-between ${styles.cardBorder}`}><span className={styles.cardText}>{edge.type}</span><span className={styles.cardTextMuted}>{edge.sourceId} → {edge.targetId}</span><span className={styles.cardTextMuted}>{t("workspace.cognition.contract_count")}: {edge.contractCount}</span></div>)}</div>}
    {missing.length > 0 && <div className="space-y-2"><h3 className={`text-xs font-semibold ${styles.cardText}`}>{t("workspace.cognition.missing_edges")}</h3>{missing.map((edge, i) => <div key={`${edge.type}-${i}`} className={`rounded-md border p-3 ${styles.cardBorder}`}><div className="flex flex-wrap items-center gap-2"><AlertTriangle size={13} className={styles.dangerText}/><span className={`text-xs font-semibold ${styles.cardText}`}>{translatedOr(t, edge.labelKey, edge.type)}</span>{edge.severity && <span className={`rounded border px-2 py-0.5 text-[10px] ${styles.cardBorder} ${styles.cardTextMuted}`}>{edge.severity}</span>}</div><p className={`mt-1 text-xs leading-relaxed ${styles.cardTextMuted}`}>{edge.reason}</p></div>)}</div>}
    {(report.islands?.length ?? 0) > 0 && <div className="space-y-2"><h3 className={`text-xs font-semibold ${styles.cardText}`}>{t("workspace.cognition.isolated_bindings")}</h3>{report.islands.map(island => <div key={island.bindingId} className={`rounded-md border p-3 ${styles.cardBorder}`}><div className={`text-xs font-semibold ${styles.cardText}`}>{island.name}</div><p className={`mt-1 text-xs ${styles.cardTextMuted}`}>{island.reason}</p></div>)}</div>}
    {(report.probeErrors?.length ?? 0) > 0 && <div className={`rounded-md border p-3 ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}><h3 className="text-xs font-semibold">{t("workspace.cognition.probe_errors")}</h3><ul className="mt-2 list-disc space-y-1 pl-4 text-xs">{report.probeErrors.map((error, i) => <li key={i}>{error}</li>)}</ul></div>}
    {required.length === 0 && missing.length === 0 && present.length === 0 && <EmptyState text={t("workspace.cognition.no_edge_data")} styles={styles}/>}
  </div>;
}
function Metric({ label, value, styles }: { label: string; value: string; styles: ReturnType<typeof useTheme>["styles"] }) {
  return <div className={`rounded-md border p-3 ${styles.cardBorder}`}><div className={`text-[10px] ${styles.cardTextMuted}`}>{label}</div><div className={`mt-1 text-lg font-semibold tabular-nums ${styles.cardText}`}>{value}</div></div>;
}
function WorkflowLink({ to, title, description, styles }: { to: string; title: string; description: string; styles: ReturnType<typeof useTheme>["styles"] }) {
  return <Link to={to} className={`block rounded-md border p-3 transition hover:opacity-80 ${styles.cardBorder}`}><div className={`flex items-center justify-between gap-2 text-xs font-semibold ${styles.cardText}`}>{title}<ArrowRight size={12}/></div><p className={`mt-2 text-xs leading-relaxed ${styles.cardTextMuted}`}>{description}</p></Link>;
}
function Loading({ text, styles }: { text: string; styles: ReturnType<typeof useTheme>["styles"] }) {
  return <div role="status" className={`py-6 text-center text-xs ${styles.cardTextMuted}`}>{text}</div>;
}
function EmptyState({ text, styles }: { text: string; styles: ReturnType<typeof useTheme>["styles"] }) {
  return <div className={`rounded-md border p-5 text-center text-xs ${styles.cardTextMuted} ${styles.cardBorder}`}>{text}</div>;
}
function ErrorState({ detail, title, retryLabel, onRetry, styles }: { detail: string; title: string; retryLabel: string; onRetry: () => void; styles: ReturnType<typeof useTheme>["styles"] }) {
  return <div role="alert" className={`rounded-md border p-3 ${styles.dangerText} ${styles.cardBorder}`}><div className="text-xs font-semibold">{title}</div><p className="mt-1 break-words text-xs">{detail}</p><button type="button" onClick={onRetry} className={`mt-3 rounded-md border px-3 py-2 text-xs ${styles.cardBorder}`}>{retryLabel}</button></div>;
}
