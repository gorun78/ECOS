/**
 * Scenario cognition loop prototype embedded in the Business Decision area.
 * Uses ECOS theme/i18n tokens. Sample scenario is explicitly marked as demo data.
 * This page is not connected to write APIs; all prototype actions are local only.
 */
import React, { useState } from "react";
import { Link } from "react-router-dom";
import { Activity, AlertTriangle, ArrowRight, Brain, CheckCircle2, ChevronDown, ChevronUp, Compass, FileCheck2, GitBranch, Lightbulb, RefreshCw, ShieldCheck, Workflow } from "lucide-react";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";

type Section = "overview" | "scenario" | "mind" | "analysis" | "actions" | "learning";
const SECTIONS: { id: Section; key: string; icon: React.ComponentType<{size?: number}> }[] = [
  { id: "overview", key: "workspace.cognition.overview", icon: Compass },
  { id: "scenario", key: "workspace.cognition.scenario", icon: GitBranch },
  { id: "mind", key: "workspace.cognition.mind", icon: Brain },
  { id: "analysis", key: "workspace.cognition.analysis", icon: Lightbulb },
  { id: "actions", key: "workspace.cognition.actions", icon: Activity },
  { id: "learning", key: "workspace.cognition.learning", icon: RefreshCw },
];
const MIND_SECTIONS = [
  ["workspace.cognition.mind_context", "workspace.cognition.context_text"],
  ["workspace.cognition.mind_beliefs", "workspace.cognition.belief_text"],
  ["workspace.cognition.mind_hypotheses", "workspace.cognition.hypotheses_text"],
  ["workspace.cognition.mind_evidence", "workspace.cognition.evidence_text"],
  ["workspace.cognition.mind_explanation", "workspace.cognition.explanation_text"],
  ["workspace.cognition.mind_evolution", "workspace.cognition.evolution_text"],
] as const;
const RUN_STEPS = [
  "workspace.cognition.step_context",
  "workspace.cognition.step_evidence",
  "workspace.cognition.step_model",
  "workspace.cognition.step_validate",
  "workspace.cognition.step_deliver",
] as const;

export default function CognitiveWorkbench() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [section, setSection] = useState<Section>("overview");
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [candidateState, setCandidateState] = useState<"pending" | "submitted" | "returned">("pending");
  const card = `rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`;
  const heading = `flex items-center gap-2 text-sm font-semibold ${styles.cardText}`;
  const button = `inline-flex min-h-10 items-center justify-center gap-1.5 rounded-md border px-3 py-2 text-xs font-semibold transition hover:opacity-80 ${styles.cardBorder} ${styles.cardText}`;
  const warningTag = `inline-flex items-center gap-1 rounded border px-2 py-1 text-[10px] ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`;
  const candidateLabel = candidateState === "pending" ? t("workspace.cognition.status_pending") : candidateState === "submitted" ? t("workspace.cognition.submit_review") : t("workspace.cognition.return_evidence");

  return <main className={`flex-1 min-w-0 overflow-y-auto ${styles.appBg} ${styles.appText}`}>
    <div className="mx-auto max-w-7xl space-y-4 p-4 md:p-6">
      <header className="flex flex-col justify-between gap-3 md:flex-row md:items-start">
        <div className="min-w-0">
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <span className={warningTag}><AlertTriangle size={12} aria-hidden="true"/>{t("workspace.cognition.demo")}</span>
            <span className={`inline-flex items-center gap-1 rounded border px-2 py-1 text-[10px] ${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`}><ShieldCheck size={12} aria-hidden="true"/>{t("workspace.cognition.status_demo")}</span>
          </div>
          <h1 className={`text-xl font-bold tracking-tight ${styles.cardText}`}>{t("workspace.cognition.title")}</h1>
          <p className={`mt-1 max-w-3xl text-xs leading-relaxed ${styles.cardTextMuted}`}>{t("workspace.cognition.subtitle")}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link to="/workbench" className={button}>{t("workspace.cognition.open_scenarios")} <ArrowRight size={13} aria-hidden="true"/></Link>
          <Link to="/decision/mind" className={button}><Brain size={13} aria-hidden="true"/>{t("workspace.cognition.open_minds")}</Link>
        </div>
      </header>
      <nav aria-label={t("workspace.cognition.title")} className={`flex gap-1 overflow-x-auto border-b ${styles.cardBorder}`}>
        {SECTIONS.map(({id,key,icon:Icon}) => <button key={id} type="button" onClick={()=>setSection(id)} aria-current={section===id?"page":undefined} className={`inline-flex min-h-11 shrink-0 items-center gap-2 border-b-2 px-3 text-xs font-semibold transition ${section===id?`${styles.accentText} ${styles.accentBorder}`:`border-transparent ${styles.cardTextMuted}`}`}><Icon size={14} aria-hidden="true"/>{t(key)}</button>)}
      </nav>

      {section === "overview" && <div className="space-y-4">
        <section className={`${card} space-y-4`}>
          <div className="flex flex-col justify-between gap-3 md:flex-row md:items-start">
            <div className="min-w-0"><div className={`mb-1 text-[10px] font-mono uppercase tracking-wider ${styles.cardTextMuted}`}>{t("workspace.cognition.sample_label")}</div><h2 className={`text-base font-semibold ${styles.cardText}`}>{t("workspace.cognition.sample_name")}</h2><p className={`mt-1 max-w-3xl text-xs leading-relaxed ${styles.cardTextMuted}`}>{t("workspace.cognition.sample_desc")}</p></div>
            <span className={warningTag}><AlertTriangle size={12} aria-hidden="true"/>{t("workspace.cognition.not_activatable")}</span>
          </div>
          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">{[["workspace.cognition.goal","workspace.cognition.goal_text"],["workspace.cognition.scope","workspace.cognition.scope_text"],["workspace.cognition.constraint","workspace.cognition.constraint_text"]].map(([title,body])=><article key={title} className={`rounded-md border p-3 ${styles.cardBorder}`}><h3 className={`text-xs font-semibold ${styles.cardText}`}>{t(title)}</h3><p className={`mt-2 text-xs leading-relaxed ${styles.cardTextMuted}`}>{t(body)}</p></article>)}</div>
        </section>
        <section className={`${card} space-y-3`}>
          <div className="flex flex-wrap items-center justify-between gap-2"><h2 className={heading}><GitBranch size={15} aria-hidden="true"/>{t("workspace.cognition.completeness")}</h2><span className={`text-sm font-semibold tabular-nums ${styles.dangerText}`}>2 / 3 · 67%</span></div>
          <p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>{t("workspace.cognition.coverage_note")}</p>
          <div className={`h-2 overflow-hidden rounded-full ${styles.inputBg}`} role="progressbar" aria-label={t("workspace.cognition.completeness")} aria-valuemin={0} aria-valuemax={3} aria-valuenow={2}><div className={`h-full w-2/3 rounded-full ${styles.dangerBg}`}/></div>
          <div className="grid grid-cols-1 gap-2 md:grid-cols-3">{[["workspace.cognition.mapping",true],["workspace.cognition.extraction",true],["workspace.cognition.cognition_edge",false]].map(([key,present])=><div key={String(key)} className={`flex items-center gap-2 rounded-md border p-3 ${styles.cardBorder}`}><span className={present?styles.successText:styles.dangerText}>{present?<CheckCircle2 size={15}/>:<AlertTriangle size={15}/>}</span><span className={`text-xs ${styles.cardText}`}>{t(String(key))}</span><span className={`ml-auto text-[10px] ${present?styles.successText:styles.dangerText}`}>{t(present?"workspace.cognition.present":"workspace.cognition.missing")}</span></div>)}</div>
          <p className={`text-xs ${styles.dangerText}`}>{t("workspace.cognition.not_activatable")}</p>
        </section>
        <section className={`${card} space-y-3`}><h2 className={heading}><Brain size={15} aria-hidden="true"/>{t("workspace.cognition.mind_title")}</h2><div className="grid grid-cols-1 gap-2 md:grid-cols-2 xl:grid-cols-3">{MIND_SECTIONS.map(([title,body])=><article key={title} className={`rounded-md border p-3 ${styles.cardBorder}`}><h3 className={`text-xs font-semibold ${styles.cardText}`}>{t(title)}</h3><p className={`mt-2 text-xs leading-relaxed ${styles.cardTextMuted}`}>{t(body)}</p></article>)}</div><Link to="/decision/mind" className={button}>{t("workspace.cognition.open_minds")} <ArrowRight size={13}/></Link></section>
      </div>}

      {section === "scenario" && <section className={`${card} space-y-4`}><h2 className={heading}><GitBranch size={15}/>{t("workspace.cognition.scenario")}</h2><p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>{t("workspace.cognition.coverage_note")}</p><div className="grid grid-cols-1 gap-3 md:grid-cols-3">{[["workspace.cognition.mapping",true],["workspace.cognition.extraction",true],["workspace.cognition.cognition_edge",false]].map(([key,present])=><article key={String(key)} className={`rounded-md border p-3 ${styles.cardBorder}`}><div className="flex items-center justify-between gap-2"><span className={`text-xs font-semibold ${styles.cardText}`}>{t(String(key))}</span><span className={`text-[10px] ${present?styles.successText:styles.dangerText}`}>{t(present?"workspace.cognition.present":"workspace.cognition.missing")}</span></div><p className={`mt-2 text-[11px] ${styles.cardTextMuted}`}>{t(present?"workspace.cognition.status_ready":"workspace.cognition.status_missing")}</p></article>)}</div><div className={`rounded-md border p-3 ${styles.warningBg} ${styles.warningBorder} ${styles.warningText}`}><AlertTriangle size={14} className="mr-1 inline" aria-hidden="true"/>{t("workspace.cognition.not_activatable")}</div><Link to="/workbench" className={button}>{t("workspace.cognition.open_scenarios")} <ArrowRight size={13}/></Link></section>}

      {section === "mind" && <div className="space-y-4">
        <section className={`${card} space-y-3`}><div className="flex flex-wrap items-center justify-between gap-2"><h2 className={heading}><Brain size={15}/>{t("workspace.cognition.mind_title")}</h2><span className={warningTag}>{t("workspace.cognition.status_demo")}</span></div><div className="grid grid-cols-1 gap-3 md:grid-cols-3">{[["workspace.cognition.validity","workspace.cognition.validity_value",styles.warningBg,styles.warningText],["workspace.cognition.review","workspace.cognition.review_value",styles.infoBg,styles.infoText],["workspace.cognition.adoption","workspace.cognition.adoption_value",styles.cardBg,styles.cardTextMuted]].map(([label,value,bg,fg])=><article key={String(label)} className={`rounded-md border p-3 ${styles.cardBorder} ${String(bg)}`}><div className={`text-[11px] ${styles.cardTextMuted}`}>{t(String(label))}</div><div className={`mt-2 text-sm font-semibold ${String(fg)}`}>{t(String(value))}</div></article>)}</div><p className={`text-[11px] ${styles.cardTextMuted}`}>{t("workspace.cognition.axis_note")}</p></section>
        <section className={`${card} space-y-3`}><div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">{MIND_SECTIONS.map(([title,body],i)=><article key={title} className={`rounded-md border p-3 ${styles.cardBorder}`}><div className="flex items-start gap-2"><span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded border text-[10px] ${styles.cardBorder} ${styles.cardTextMuted}`}>{i+1}</span><h3 className={`text-xs font-semibold ${styles.cardText}`}>{t(title)}</h3></div><p className={`mt-2 text-xs leading-relaxed ${styles.cardTextMuted}`}>{t(body)}</p></article>)}</div><Link to="/decision/mind" className={button}>{t("workspace.cognition.open_minds")} <ArrowRight size={13}/></Link></section>
        <section className={`${card} space-y-3`}><h3 className={heading}><FileCheck2 size={15}/>{t("workspace.cognition.candidate")}</h3><p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>{t("workspace.cognition.candidate_text")}</p><span className={warningTag}>{candidateLabel}</span><div className="flex flex-wrap gap-2"><button type="button" onClick={()=>setCandidateState("submitted")} className={`${button} ${styles.accentBg} ${styles.accentSolidText}`}>{t("workspace.cognition.submit_review")}</button><button type="button" onClick={()=>setCandidateState("returned")} className={button}>{t("workspace.cognition.return_evidence")}</button></div><p className={`text-[10px] ${styles.cardTextMuted}`}>{t("workspace.cognition.demo_action_note")}</p></section>
      </div>}

      {section === "analysis" && <section className={`${card} space-y-4`}><h2 className={heading}><Lightbulb size={15}/>{t("workspace.cognition.analysis_title")}</h2><p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>{t("workspace.cognition.analysis_desc")}</p><ol className="space-y-2">{[1,2,3,4].map(n=>{const key=`workspace.cognition.analysis_step${n}`;return <li key={key} className={`flex items-start gap-3 rounded-md border p-3 ${styles.cardBorder}`}><span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-[10px] ${styles.cardBorder} ${styles.cardTextMuted}`}>{n}</span><span className={`text-xs leading-relaxed ${styles.cardText}`}>{t(key)}</span></li>})}</ol><Link to="/resource-center" className={button}>{t("workspace.cognition.open_assets")} <ArrowRight size={13}/></Link></section>}

      {section === "actions" && <section className={`${card} space-y-4`}><h2 className={heading}><Activity size={15}/>{t("workspace.cognition.actions_title")}</h2><article className={`rounded-md border p-4 ${styles.cardBorder}`}><h3 className={`text-xs font-semibold ${styles.cardText}`}>{t("workspace.cognition.action_proposed")}</h3><p className={`mt-2 text-xs leading-relaxed ${styles.cardTextMuted}`}>{t("workspace.cognition.action_proposed_text")}</p><div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3">{[["workspace.cognition.action_owner","workspace.cognition.action_owner_value"],["workspace.cognition.action_approval","workspace.cognition.action_approval_value"],["workspace.cognition.action_effect","workspace.cognition.action_effect_value"]].map(([k,v])=><div key={k} className={`rounded-md border p-3 ${styles.cardBorder}`}><div className={`text-[10px] ${styles.cardTextMuted}`}>{t(k)}</div><p className={`mt-1 text-xs ${styles.cardText}`}>{t(v)}</p></div>)}</div></article><Link to="/workbench" className={button}>{t("workspace.cognition.open_scenario")} <ArrowRight size={13}/></Link></section>}

      {section === "learning" && <div className="space-y-4"><section className={`${card} space-y-3`}><h2 className={heading}><RefreshCw size={15}/>{t("workspace.cognition.learning_title")}</h2><div className="grid grid-cols-1 gap-3 md:grid-cols-3">{[["workspace.cognition.expected","workspace.cognition.expected_text"],["workspace.cognition.actual","workspace.cognition.actual_text"],["workspace.cognition.variance","workspace.cognition.variance_text"]].map(([k,v])=><article key={k} className={`rounded-md border p-3 ${styles.cardBorder}`}><h3 className={`text-xs font-semibold ${styles.cardText}`}>{t(k)}</h3><p className={`mt-2 text-xs leading-relaxed ${styles.cardTextMuted}`}>{t(v)}</p></article>)}</div></section><section className={`${card} space-y-2`}><h3 className={heading}><ShieldCheck size={15}/>{t("workspace.cognition.learning_rule")}</h3><p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>{t("workspace.cognition.learning_rule_text")}</p></section></div>}

      <section className={`${card} space-y-3`}>
        <button type="button" onClick={()=>setAdvancedOpen(v=>!v)} aria-expanded={advancedOpen} className="flex min-h-10 w-full items-center justify-between gap-2 text-left"><span className={heading}><Workflow size={15}/>{t("workspace.cognition.advanced")}</span>{advancedOpen?<ChevronUp size={15}/>:<ChevronDown size={15}/>}</button>
        {advancedOpen&&<div className="space-y-3"><p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>{t("workspace.cognition.advanced_note")}</p><h3 className={`text-xs font-semibold ${styles.cardText}`}>{t("workspace.cognition.workflow")}</h3><ol className="grid grid-cols-1 gap-2 md:grid-cols-2 xl:grid-cols-5">{RUN_STEPS.map((key,i)=><li key={key} className={`rounded-md border p-3 ${styles.cardBorder}`}><span className={`mb-2 flex h-6 w-6 items-center justify-center rounded-full border text-[10px] ${styles.cardBorder} ${styles.cardTextMuted}`}>{i+1}</span><p className={`text-[11px] leading-relaxed ${styles.cardTextMuted}`}>{t(key)}</p></li>)}</ol></div>}
      </section>
      <footer className={`flex flex-wrap items-center justify-between gap-2 border-t pt-3 text-[10px] ${styles.cardBorder} ${styles.cardTextMuted}`}><span className="flex items-center gap-1.5"><ShieldCheck size={12} aria-hidden="true"/>{t("workspace.cognition.footer")}</span><span className="font-mono">ECOS / DECISION / PROTOTYPE</span></footer>
    </div>
  </main>;
}
