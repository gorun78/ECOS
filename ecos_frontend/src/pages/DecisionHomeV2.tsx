import React, { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ArrowRight, BarChart3, ShieldAlert, GitCompare, ListChecks,
  Search, Database, ShieldCheck, Workflow, ChevronDown, Sparkles
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

type TaskKey = "forecast" | "risk" | "compare" | "actions";

export default function DecisionHomeV2() {
  const navigate = useNavigate();
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const zh = locale === "zh";
  const [question, setQuestion] = useState("");
  const [selectedTask, setSelectedTask] = useState<TaskKey | null>(null);
  const [showDetails, setShowDetails] = useState(false);

  const copy = useMemo(() => ({
    eyebrow: zh ? "经营决策工作空间" : "DECISION WORKSPACE",
    title: zh ? "今天想解决什么问题？" : "What would you like to solve today?",
    subtitle: zh ? "从业务目标开始。ECOS 帮你组织分析、检查条件，并把结果连接到可追溯的行动。" : "Start with a business goal. ECOS organizes analysis, checks prerequisites, and connects results to traceable actions.",
    promptLabel: zh ? "直接描述你的业务问题" : "Describe your business question",
    placeholder: zh ? "例如：为什么今年利润可能低于目标？" : "e.g. Why might profit fall below target this year?",
    start: zh ? "开始分析" : "Start analysis",
    taskHeading: zh ? "从常见任务开始" : "Start with a common task",
    taskHint: zh ? "选择目标，无需先配置场景" : "Choose an outcome—no need to configure a scenario first",
    tasks: [
      { key: "forecast" as const, title: zh ? "预测今年利润" : "Forecast annual profit", desc: zh ? "查看预测值、目标差距和主要影响因素" : "Review forecast, target gap, and key drivers", icon: BarChart3, action: zh ? "开始预测" : "Start forecast" },
      { key: "risk" as const, title: zh ? "找出经营风险" : "Identify business risks", desc: zh ? "识别异常、影响范围和需要关注的事项" : "Identify anomalies, impact, and items needing attention", icon: ShieldAlert, action: zh ? "分析风险" : "Analyze risks" },
      { key: "compare" as const, title: zh ? "比较经营方案" : "Compare business options", desc: zh ? "对比指标、关键假设和敏感性" : "Compare metrics, assumptions, and sensitivities", icon: GitCompare, action: zh ? "比较方案" : "Compare options" },
      { key: "actions" as const, title: zh ? "跟踪决策执行" : "Track decision execution", desc: zh ? "查看行动、责任人、期限和实际结果" : "Track actions, owners, due dates, and outcomes", icon: ListChecks, action: zh ? "查看行动" : "View actions" }
    ],
    prep: zh ? "开始任务后，ECOS 会自动检查" : "When a task starts, ECOS checks",
    checks: zh ? [
      ["场景与业务口径", "匹配已有场景和已发布指标定义"],
      ["数据与运行条件", "调用数据引擎执行质量门禁与权限检查"],
      ["认知依据与治理", "检查相关判断的有效性、审查和采纳状态"]
    ] : [
      ["Scenario and business definitions", "Match existing scenarios and published metric definitions"],
      ["Data and execution readiness", "Use the data engine for quality gates and access checks"],
      ["Cognitive basis and governance", "Check validity, review, and adoption states"]
    ],
    detailLabel: zh ? "任务预览" : "Task preview",
    next: zh ? "下一步" : "Next step",
    openWorkbench: zh ? "进入场景工作台" : "Open scenario workbench",
    details: zh ? "了解系统会检查什么" : "What the system checks",
    hide: zh ? "收起" : "Hide",
    specialist: zh ? "专业工作空间" : "Specialist workspaces",
    specialistHint: zh ? "复杂能力仍然可用，但不再是开始业务任务的前置步骤。" : "Advanced capabilities remain available, but are no longer prerequisites for starting a business task.",
    ontology: zh ? "本体与业务定义" : "Ontology & business definitions",
    data: zh ? "数据工作台" : "Data workbench",
    ai: zh ? "AI 工作台" : "AI workbench",
    foot: zh ? "业务语言优先 · 证据可追溯 · 权限与治理门禁不变" : "Business-first · Traceable evidence · Governance and access controls preserved"
  }), [zh]);

  const selected = copy.tasks.find(t => t.key === selectedTask);
  const taskForQuestion = (q: string): TaskKey => {
    if (/风险|异常|隐患|risk|anomal/i.test(q)) return "risk";
    if (/比较|方案|对比|compare|option/i.test(q)) return "compare";
    if (/行动|执行|跟踪|action|track/i.test(q)) return "actions";
    return "forecast";
  };
  const startFromQuestion = () => {
    const q = question.trim();
    if (!q) return;
    setSelectedTask(taskForQuestion(q));
    setShowDetails(true);
  };

  const openWorkbench = () => navigate("/project_workbench");

  return (
    <div className={`flex-grow overflow-y-auto p-5 md:p-8 ${styles.appBg} ${styles.appText}`}>
      <div className="max-w-6xl mx-auto space-y-7">
        <header className="space-y-2">
          <div className="text-xs font-bold tracking-[0.16em] text-indigo-600 dark:text-indigo-400">{copy.eyebrow}</div>
          <h1 className="text-2xl md:text-3xl font-bold tracking-tight">{copy.title}</h1>
          <p className={`text-sm max-w-3xl ${styles.cardTextMuted}`}>{copy.subtitle}</p>
        </header>

        <section className={`rounded-2xl border p-4 md:p-5 shadow-sm ${styles.cardBg} ${styles.cardBorder}`}>
          <label htmlFor="decision-question" className="block text-xs font-semibold mb-2">{copy.promptLabel}</label>
          <div className="flex flex-col sm:flex-row gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input id="decision-question" value={question} onChange={e => setQuestion(e.target.value)}
                onKeyDown={e => { if (e.key === "Enter") startFromQuestion(); }}
                placeholder={copy.placeholder}
                className="w-full h-12 pl-10 pr-3 rounded-xl border border-slate-200 dark:border-slate-700 bg-transparent text-sm outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-500" />
            </div>
            <button onClick={startFromQuestion} disabled={!question.trim()}
              className="h-12 px-5 rounded-xl bg-indigo-600 hover:bg-indigo-700 disabled:opacity-40 text-white text-sm font-semibold flex items-center justify-center gap-2 transition">
              {copy.start}<ArrowRight className="w-4 h-4" />
            </button>
          </div>
          <div className="flex flex-wrap gap-2 mt-3">
            {copy.tasks.slice(0, 3).map(t => (
              <button key={t.key} onClick={() => { setQuestion(t.title); setSelectedTask(t.key); setShowDetails(true); }}
                className="px-3 py-1.5 rounded-full border text-xs border-slate-200 dark:border-slate-700 hover:border-indigo-400 hover:text-indigo-600 transition">
                {t.title}
              </button>
            ))}
          </div>
        </section>

        <section>
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-1 mb-3">
            <h2 className="font-bold text-base">{copy.taskHeading}</h2>
            <span className={`text-xs ${styles.cardTextMuted}`}>{copy.taskHint}</span>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
            {copy.tasks.map(t => {
              const Icon = t.icon;
              const active = selectedTask === t.key && showDetails;
              return <button key={t.key} onClick={() => { setSelectedTask(t.key); setShowDetails(true); }}
                className={`text-left rounded-xl border p-4 transition hover:-translate-y-0.5 hover:shadow-sm ${styles.cardBg} ${active ? "border-indigo-500 ring-1 ring-indigo-500/20" : styles.cardBorder}`}>
                <span className="w-10 h-10 rounded-xl bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 flex items-center justify-center mb-3"><Icon className="w-5 h-5" /></span>
                <strong className="block text-sm">{t.title}</strong>
                <span className={`block text-xs leading-relaxed mt-1.5 min-h-[36px] ${styles.cardTextMuted}`}>{t.desc}</span>
                <span className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 dark:text-indigo-400">{t.action}<ArrowRight className="w-3.5 h-3.5" /></span>
              </button>;
            })}
          </div>
        </section>

        {showDetails && selected && (
          <section className={`rounded-2xl border p-5 md:p-6 ${styles.cardBg} ${styles.cardBorder}`} aria-live="polite">
            <div className="flex items-start justify-between gap-4">
              <div><div className="text-xs font-semibold text-indigo-600 dark:text-indigo-400">{copy.detailLabel}</div>
                <h2 className="text-lg font-bold mt-1">{selected.title}</h2>
                <p className={`text-sm mt-1 ${styles.cardTextMuted}`}>{selected.desc}</p></div>
              <button onClick={() => setShowDetails(false)} className="text-xs border rounded-lg px-3 py-2 border-slate-200 dark:border-slate-700">{copy.hide}</button>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mt-5">
              {copy.checks.map(([title, desc], i) => {
                const Icon = [Workflow, Database, ShieldCheck][i];
                return <div key={title} className="rounded-xl border p-3 border-slate-200 dark:border-slate-700">
                  <Icon className="w-4 h-4 text-indigo-500 mb-2" /><strong className="block text-sm">{title}</strong>
                  <p className={`text-xs leading-relaxed mt-1 ${styles.cardTextMuted}`}>{desc}</p>
                </div>;
              })}
            </div>
            <div className="mt-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl bg-indigo-500/5 p-3">
              <div className="flex gap-2 items-start"><Sparkles className="w-4 h-4 mt-0.5 text-indigo-500 shrink-0" /><p className="text-xs leading-relaxed">{zh ? "任务预览不会伪造运行状态或结果。进入工作台后，系统应以服务端预检结果决定是否可运行。" : "The preview does not fabricate execution status or results. The workbench must use server-side preflight to determine readiness."}</p></div>
              <button onClick={openWorkbench} className="shrink-0 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold px-4 py-2.5 inline-flex items-center gap-2">{copy.openWorkbench}<ArrowRight className="w-3.5 h-3.5" /></button>
            </div>
          </section>
        )}

        <section className={`rounded-xl border p-4 md:p-5 ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="flex items-start gap-3">
            <div className="w-9 h-9 rounded-xl bg-emerald-500/10 text-emerald-600 flex items-center justify-center shrink-0"><ShieldCheck className="w-5 h-5" /></div>
            <div className="flex-1">
              <h2 className="text-sm font-bold">{copy.prep}</h2>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-3">
                {copy.checks.map(([title, desc]) => <div key={title}><strong className="block text-xs">{title}</strong><p className={`text-xs leading-relaxed mt-1 ${styles.cardTextMuted}`}>{desc}</p></div>)}
              </div>
            </div>
          </div>
        </section>

        <section>
          <div className="mb-3"><h2 className="font-bold text-base">{copy.specialist}</h2><p className={`text-xs mt-1 ${styles.cardTextMuted}`}>{copy.specialistHint}</p></div>
          <div className="flex flex-wrap gap-2">
            <button onClick={() => navigate("/ontology_workbench")} className="border rounded-lg px-3 py-2 text-xs flex items-center gap-2 hover:border-indigo-400"><Workflow className="w-4 h-4" />{copy.ontology}<ArrowRight className="w-3 h-3" /></button>
            <button onClick={() => navigate("/data-workbench")} className="border rounded-lg px-3 py-2 text-xs flex items-center gap-2 hover:border-indigo-400"><Database className="w-4 h-4" />{copy.data}<ArrowRight className="w-3 h-3" /></button>
            <button onClick={() => navigate("/ai-workbench")} className="border rounded-lg px-3 py-2 text-xs flex items-center gap-2 hover:border-indigo-400"><Sparkles className="w-4 h-4" />{copy.ai}<ArrowRight className="w-3 h-3" /></button>
          </div>
        </section>
        <footer className={`text-center text-[11px] py-2 ${styles.cardTextMuted}`}>{copy.foot}</footer>
      </div>
    </div>
  );
}
