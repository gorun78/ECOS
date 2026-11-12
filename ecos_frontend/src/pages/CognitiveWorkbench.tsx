/**
 * ECOS 场景与认知闭环工作台。
 * 可交互产品原型：当前使用演示数据，不写入后端，也不执行真实业务动作。
 */
import React, { useMemo, useState } from 'react';
import { Activity, AlertTriangle, ArrowRight, Brain, Check, CheckCircle2, ChevronRight, Clock3, FileCheck2, GitBranch, Lightbulb, Play, Plus, RefreshCw, ShieldCheck, Sparkles, Workflow } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';

type ViewKey = 'overview' | 'scenario' | 'mind' | 'models' | 'agent' | 'review';
const MODELS = [
  { id: 'system', name: '系统思维', en: 'Systems Thinking', type: 'mental', use: '识别反馈回路、延迟与系统边界', enUse: 'Identify feedback loops, delays and system boundaries', status: '已发布', enStatus: 'Published' },
  { id: 'causal', name: '因果诊断模型', en: 'Causal Diagnosis', type: 'cognitive', use: '比较竞争性假设并识别可干预因素', enUse: 'Compare hypotheses and identify intervention points', status: '已验证', enStatus: 'Validated' },
  { id: 'incentive', name: '激励机制', en: 'Incentive Design', type: 'mental', use: '分析执行中的行为响应与逆向激励', enUse: 'Analyze behavior and unintended incentives', status: '已发布', enStatus: 'Published' },
  { id: 'risk', name: '风险评估模型', en: 'Risk Assessment', type: 'cognitive', use: '评估影响、可能性、可逆性与控制措施', enUse: 'Assess impact, likelihood, reversibility and controls', status: '需复核', enStatus: 'Review due' },
] as const;
const STEPS = [
  { id: 'context', zh: '加载场景与 Mind', en: 'Load scenario & Mind', detail: '读取目标、边界、当前信念与证据引用', enDetail: 'Read goals, boundaries, beliefs and evidence references' },
  { id: 'evidence', zh: '补齐证据', en: 'Gather evidence', detail: '查询业务对象、数据资源、知识库与规则', enDetail: 'Query business objects, data, knowledge and rules' },
  { id: 'reason', zh: '运行认知模型', en: 'Run cognitive models', detail: '执行系统分析、因果诊断与风险评估', enDetail: 'Apply systems analysis, causal diagnosis and risk assessment' },
  { id: 'validate', zh: '校验与整合', en: 'Validate & synthesize', detail: '检查矛盾、证据缺口、适用边界和行动风险', enDetail: 'Check conflicts, evidence gaps, model limits and action risks' },
  { id: 'deliver', zh: '交付判断提案', en: 'Deliver recommendation', detail: '输出依据、备选方案、置信程度与待审批行动', enDetail: 'Present rationale, options, confidence and actions for approval' },
] as const;

export default function CognitiveWorkbench() {
  const { styles } = useTheme();
  const { locale } = useLanguage();
  const zh = locale === 'zh' || locale === 'zh-CN';
  const [view, setView] = useState<ViewKey>('overview');
  const [filter, setFilter] = useState<'all' | 'mental' | 'cognitive'>('all');
  const [ran, setRan] = useState(false);
  const [candidate, setCandidate] = useState<'pending' | 'submitted' | 'returned'>('pending');
  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const [draftName, setDraftName] = useState('');
  const t = useMemo(() => ({
    title: zh ? '场景与认知闭环' : 'Scenario & Cognition Loop',
    subtitle: zh ? '从问题定义到证据、判断、行动与学习的统一工作空间' : 'A unified workspace from problem framing to evidence, decisions, actions and learning',
    prototype: zh ? '交互原型 · 演示数据' : 'Interactive prototype · Demo data',
    overview: zh ? '闭环总览' : 'Overview', scenario: zh ? '场景定义' : 'Scenario',
    mind: zh ? 'Mind 心智' : 'Mind state', models: zh ? '认知模型' : 'Cognitive models',
    agent: zh ? 'W Agent 编排' : 'W Agent orchestration', review: zh ? '结果与学习' : 'Review & learning',
    new: zh ? '新建场景' : 'New scenario', open: zh ? '打开场景工作台' : 'Open scenario workbench',
    current: zh ? '当前场景' : 'Current scenario',
    scenarioName: zh ? '惠企政策申报量上升，但企业获得感未提升' : 'Policy applications rise, but perceived business value does not',
    scenarioDesc: zh ? '分析政策触达、申报转化、兑现时效与企业实际受益之间的断点。' : 'Find gaps between policy reach, application conversion, fulfillment time and realized benefits.',
    active: zh ? '进行中' : 'In progress', target: zh ? '目标与成功标准' : 'Goal & success criteria',
    targetText: zh ? '提升符合条件企业的实际受益率，并缩短政策兑现周期。' : 'Increase the share of eligible businesses receiving benefits and shorten fulfillment time.',
    boundary: zh ? '分析边界' : 'Scope boundary',
    boundaryText: zh ? '近 12 个月申报、审核、兑现及企业反馈；按政策类型与企业规模分层。' : 'Applications, reviews, fulfillment and feedback over 12 months, segmented by policy and business size.',
    constraints: zh ? '关键约束' : 'Key constraints',
    constraintsText: zh ? '数据口径一致、保护企业敏感信息、行动必须经过授权。' : 'Consistent metric definitions, protected business data and authorized actions only.',
    belief: zh ? '当前信念' : 'Current belief',
    beliefText: zh ? '申报增长尚未转化为企业实际收益，瓶颈可能出现在审核与兑现环节。' : 'Application growth has not translated into realized benefits; bottlenecks may sit in review and fulfillment.',
    hypotheses: zh ? '待验证假设' : 'Hypotheses to validate',
    hypothesesText: zh ? 'H1：企业资格理解存在偏差。H2：审核或兑现周期过长。H3：政策设计与企业实际需求错配。' : 'H1: eligibility criteria are poorly understood. H2: review or fulfillment is slow. H3: policy design is misaligned with business needs.',
    evidence: zh ? '关联证据' : 'Linked evidence',
    evidenceText: zh ? '已关联 12 项证据 · 3 项待补齐 · 1 组口径冲突' : '12 linked items · 3 gaps · 1 metric-definition conflict',
    quality: zh ? '认知质量' : 'Cognitive quality', completeness: zh ? '场景完整度' : 'Scenario completeness',
    approvals: zh ? '待审批行动' : 'Actions awaiting approval', update: zh ? '认知更新候选' : 'Cognition update candidate',
    pending: zh ? '待复核' : 'Pending review', submitted: zh ? '已提交复核' : 'Submitted for review', returned: zh ? '已退回补充证据' : 'Returned for more evidence',
    submit: zh ? '提交复核' : 'Submit for review', returnEvidence: zh ? '退回补证' : 'Return for evidence',
    run: zh ? '运行演示流程' : 'Run demo workflow', rerun: zh ? '重新运行' : 'Run again',
    noRealAction: zh ? '此操作仅更新本页面的演示状态，不调用后端、不执行真实业务动作。' : 'This updates local demo state only; no backend or business action is executed.',
    modelType: zh ? '模型类型' : 'Model type', all: zh ? '全部' : 'All', mental: zh ? '心智模型' : 'Mental model', cognitive: zh ? '认知模型' : 'Cognitive model',
    applicability: zh ? '适用问题' : 'Use case', created: zh ? '已创建本地演示草稿' : 'Local demo draft created',
    createTitle: zh ? '新建场景草稿' : 'Create scenario draft', scenarioNameLabel: zh ? '场景名称' : 'Scenario name',
    placeholder: zh ? '输入场景名称' : 'Enter a scenario name', cancel: zh ? '取消' : 'Cancel', create: zh ? '创建草稿' : 'Create draft',
    expected: zh ? '预期结果' : 'Expected outcome', actual: zh ? '实际结果' : 'Actual outcome', variance: zh ? '偏差与解释' : 'Variance & explanation',
    expectedText: zh ? '目标：缩短 20% 兑现周期，提升 8 个百分点实际受益率。' : 'Target: reduce fulfillment time by 20% and raise realized benefit rate by 8 percentage points.',
    actualText: zh ? '演示数据尚未接入真实结果，需在行动执行后回填。' : 'Demo data is not connected to real outcomes; actual results must be recorded after execution.',
    varianceText: zh ? '结果回填后，对比预期与实际，复核假设并评估模型适用性。' : 'Compare actuals to expectations, review hypotheses and assess model fit after outcomes are recorded.',
  }), [zh]);
  const nav: { id: ViewKey; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
    { id: 'overview', label: t.overview, icon: Activity }, { id: 'scenario', label: t.scenario, icon: GitBranch },
    { id: 'mind', label: 'Mind', icon: Brain }, { id: 'models', label: t.models, icon: Lightbulb },
    { id: 'agent', label: 'W Agent', icon: Workflow }, { id: 'review', label: t.review, icon: RefreshCw },
  ];
  const filteredModels = MODELS.filter(m => filter === 'all' || m.type === filter);
  const card = `rounded-lg border p-4 ${styles.cardBorder} ${styles.cardBg}`;
  const titleClass = `flex items-center gap-2 text-sm font-semibold ${styles.cardText}`;
  const btn = 'inline-flex items-center justify-center gap-1.5 rounded-md px-3 py-2 text-xs font-semibold transition disabled:cursor-not-allowed disabled:opacity-50';

  return <div className={`flex-1 overflow-y-auto ${styles.appBg} ${styles.appText}`}>
    <div className="mx-auto max-w-7xl space-y-5 p-4 md:p-6">
      <header className="flex flex-col justify-between gap-4 md:flex-row md:items-start">
        <div className="min-w-0">
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <span className={`inline-flex items-center gap-1 rounded border px-2 py-1 text-[10px] font-mono uppercase tracking-wider ${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`}><Sparkles size={12} /> {t.prototype}</span>
            <span className={`inline-flex items-center gap-1 rounded border px-2 py-1 text-[10px] ${styles.cardBorder} ${styles.cardTextMuted}`}><ShieldCheck size={12} /> {zh ? '受控认知更新' : 'Governed cognition updates'}</span>
          </div>
          <h1 className={`text-xl font-bold tracking-tight ${styles.cardText}`}>{t.title}</h1>
          <p className={`mt-1 max-w-3xl text-xs leading-relaxed ${styles.cardTextMuted}`}>{t.subtitle}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={() => setCreateOpen(true)} className={`${btn} ${styles.accentBg} ${styles.accentSolidText}`}><Plus size={14} /> {t.new}</button>
          <Link to="/workbench" className={`${btn} border ${styles.cardBorder} ${styles.cardText} hover:opacity-80`}>{t.open} <ArrowRight size={14} /></Link>
        </div>
      </header>
      {draftName && <div role="status" className={`flex flex-wrap items-center justify-between gap-2 rounded-lg border px-3 py-2 text-xs ${styles.successBg} ${styles.successText} ${styles.successBorder}`}><span className="flex items-center gap-2"><CheckCircle2 size={14} /> {t.created}: <strong>{draftName}</strong></span><span>{t.noRealAction}</span></div>}
      <nav aria-label={t.title} className={`flex gap-1 overflow-x-auto border-b ${styles.cardBorder}`}>
        {nav.map(item => { const Icon = item.icon; const active = view === item.id; return <button key={item.id} type="button" onClick={() => setView(item.id)} aria-current={active ? 'page' : undefined} className={`inline-flex shrink-0 items-center gap-2 border-b-2 px-3 py-3 text-xs font-semibold transition ${active ? `${styles.accentText} ${styles.accentBorder}` : `border-transparent ${styles.cardTextMuted} hover:opacity-90`}`}><Icon size={14} /> {item.label}</button>; })}
      </nav>

      {view === 'overview' && <div className="space-y-4">
        <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {[{label:t.quality,value:'78/100',detail:zh?'2 项关键证据待补齐':'2 key evidence gaps',icon:Brain,tone:styles.infoText},{label:t.completeness,value:'86%',detail:zh?'目标、边界已定义':'Goal and scope defined',icon:CheckCircle2,tone:styles.successText},{label:t.approvals,value:'2',detail:zh?'需业务负责人审批':'Business owner approval',icon:ShieldCheck,tone:styles.warningText},{label:t.update,value:candidate==='pending'?'1':'0',detail:candidate==='pending'?t.pending:candidate==='submitted'?t.submitted:t.returned,icon:FileCheck2,tone:styles.infoText}].map(m=>{const Icon=m.icon;return <article key={m.label} className={card}><div className="flex items-center justify-between"><span className={`text-xs ${styles.cardTextMuted}`}>{m.label}</span><Icon size={16} className={m.tone}/></div><div className={`mt-3 text-2xl font-semibold tracking-tight ${styles.cardText}`}>{m.value}</div><div className={`mt-1 text-[11px] ${styles.cardTextMuted}`}>{m.detail}</div></article>;})}
        </section>
        <section className={`${card} space-y-4`}>
          <div className="flex flex-wrap items-start justify-between gap-3"><div><div className={`mb-1 flex items-center gap-2 text-[10px] font-mono uppercase tracking-wider ${styles.cardTextMuted}`}>SCENARIO / SC-024 <span className={`rounded border px-1.5 py-0.5 font-sans normal-case ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>{t.active}</span></div><h2 className={`text-base font-semibold ${styles.cardText}`}>{t.scenarioName}</h2><p className={`mt-1 max-w-3xl text-xs leading-relaxed ${styles.cardTextMuted}`}>{t.scenarioDesc}</p></div><button type="button" onClick={() => setView('scenario')} className={`inline-flex items-center gap-1 text-xs font-semibold ${styles.accentText}`}>{t.scenario} <ChevronRight size={14}/></button></div>
          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">{[{name:t.scenario,desc:zh?'目标、边界、约束与成功标准':'Goals, boundaries, constraints and success criteria',icon:GitBranch},{name:'Mind',desc:zh?'信念、假设、证据与解释结构':'Beliefs, hypotheses, evidence and explanations',icon:Brain},{name:t.models,desc:zh?'适用性、输入输出契约与验证方式':'Applicability, I/O contract and validation',icon:Lightbulb}].map(m=>{const Icon=m.icon;return <div key={m.name} className={`rounded-md border p-3 ${styles.cardBorder}`}><div className="flex items-center gap-2"><Icon size={15} className={styles.accentText}/><span className={`text-xs font-semibold ${styles.cardText}`}>{m.name}</span><Check size={13} className={`ml-auto ${styles.successText}`}/></div><p className={`mt-2 text-[11px] ${styles.cardTextMuted}`}>{m.desc}</p></div>;})}</div>
        </section>
        <section className={`${card} space-y-3`}><h2 className={titleClass}><Workflow size={16}/>{zh?'认知闭环路径':'Cognitive loop'}</h2><div className="grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-5">{[{id:'scenario' as ViewKey,title:t.scenario,desc:zh?'定义问题':'Frame the problem',icon:GitBranch},{id:'mind' as ViewKey,title:'Mind',desc:zh?'维护当前认知':'Maintain understanding',icon:Brain},{id:'models' as ViewKey,title:t.models,desc:zh?'提供解释框架':'Provide models',icon:Lightbulb},{id:'agent' as ViewKey,title:'W Agent',desc:zh?'组织推理与行动':'Orchestrate work',icon:Workflow},{id:'review' as ViewKey,title:t.review,desc:zh?'验证结果并学习':'Validate and learn',icon:RefreshCw}].map((m,i)=>{const Icon=m.icon;return <button key={m.id} type="button" onClick={()=>setView(m.id)} className={`flex items-center gap-3 rounded-md border p-3 text-left transition hover:opacity-80 ${styles.cardBorder} ${styles.cardBg}`}><span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md ${styles.infoBg} ${styles.infoText}`}><Icon size={15}/></span><span className="min-w-0"><span className={`block text-xs font-semibold ${styles.cardText}`}>{m.title}</span><span className={`mt-1 block text-[10px] ${styles.cardTextMuted}`}>{m.desc}</span></span>{i<4&&<ChevronRight size={13} className={`ml-auto shrink-0 ${styles.cardTextMuted}`}/>}</button>;})}</div></section>
      </div>}

      {view === 'scenario' && <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1.5fr)_minmax(260px,0.8fr)]">
        <section className={`${card} space-y-4`}><h2 className={titleClass}><GitBranch size={16}/>{t.current}</h2><div><h3 className={`text-sm font-semibold ${styles.cardText}`}>{t.scenarioName}</h3><p className={`mt-1 text-xs leading-relaxed ${styles.cardTextMuted}`}>{t.scenarioDesc}</p></div>{[{title:t.target,body:t.targetText},{title:t.boundary,body:t.boundaryText},{title:t.constraints,body:t.constraintsText}].map(x=><div key={x.title} className={`border-t pt-3 ${styles.cardBorder}`}><h3 className={`text-xs font-semibold ${styles.cardText}`}>{x.title}</h3><p className={`mt-1 text-xs leading-relaxed ${styles.cardTextMuted}`}>{x.body}</p></div>)}<div className={`rounded-md border p-3 ${styles.infoBg} ${styles.infoBorder}`}><div className={`text-xs font-semibold ${styles.infoText}`}>{zh?'场景完整度门禁':'Scenario completeness gate'}</div><p className={`mt-1 text-[11px] leading-relaxed ${styles.cardTextMuted}`}>{zh?'运行前校验主体、目标、范围、指标口径、证据要求、权限与风险约束。缺失项应澄清或显式标记。':'Validate actors, goals, scope, metrics, evidence requirements, permissions and risk constraints before execution.'}</p></div></section>
        <aside className={`${card} space-y-3`}><h3 className={titleClass}><ShieldCheck size={16}/>{zh?'场景治理':'Scenario governance'}</h3>{[zh?'目标与成功标准':'Goals & success criteria',zh?'业务对象与时间范围':'Business objects & time range',zh?'证据要求':'Evidence requirements',zh?'权限与行动审批':'Permissions & action approval'].map((x,i)=><div key={x} className="flex items-center gap-2 text-xs"><span className={i===3?styles.warningText:styles.successText}>{i===3?<AlertTriangle size={14}/>:<CheckCircle2 size={14}/>}</span><span className={styles.cardText}>{x}</span><span className={`ml-auto text-[10px] ${styles.cardTextMuted}`}>{i===3?(zh?'待确认':'Confirm'):(zh?'已定义':'Defined')}</span></div>)}</aside>
      </div>}

      {view === 'mind' && <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.35fr)_minmax(280px,0.8fr)]">
        <section className={`${card} space-y-4`}><div className="flex flex-wrap items-start justify-between gap-2"><div><h2 className={titleClass}><Brain size={16}/>{zh?'当前认知状态':'Current Mind state'}</h2><p className={`mt-1 text-[11px] ${styles.cardTextMuted}`}>Mind v1.8 · {zh?'最近复核':'Last reviewed'} 2026-10-28</p></div><span className={`rounded border px-2 py-1 text-[10px] ${styles.successBg} ${styles.successText} ${styles.successBorder}`}>{zh?'有效认知状态':'Current state'}</span></div><div className={`rounded-md border p-3 ${styles.cardBorder}`}><h3 className={`text-xs font-semibold ${styles.cardText}`}>{t.belief}</h3><p className={`mt-2 text-xs leading-relaxed ${styles.cardTextMuted}`}>{t.beliefText}</p><div className="mt-3 flex items-center gap-2 text-[10px]"><span className={`rounded px-1.5 py-0.5 ${styles.infoBg} ${styles.infoText}`}>{zh?'置信度 0.68':'Confidence 0.68'}</span><span className={styles.cardTextMuted}>{zh?'来源：证据组 EV-012':'Source: evidence set EV-012'}</span></div></div><div><h3 className={`mb-2 text-xs font-semibold ${styles.cardText}`}>{t.hypotheses}</h3><p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>{t.hypothesesText}</p></div><div><h3 className={`mb-2 text-xs font-semibold ${styles.cardText}`}>{t.evidence}</h3><div className={`rounded-md border p-3 text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}><AlertTriangle size={14} className="mr-1 inline"/>{t.evidenceText}</div></div></section>
        <aside className={`${card} space-y-3`}><h3 className={titleClass}><GitBranch size={16}/>{zh?'认知更新治理':'Cognition update governance'}</h3><div className={`rounded-md border p-3 ${styles.cardBorder}`}><div className={`text-xs font-semibold ${styles.cardText}`}>{zh?'候选更新 · MUP-018':'Update candidate · MUP-018'}</div><p className={`mt-2 text-[11px] leading-relaxed ${styles.cardTextMuted}`}>{zh?'新证据提示：兑现时效可能比政策触达更能解释企业获得感。':'New evidence suggests fulfillment time may explain perceived value better than policy reach.'}</p><span className={`mt-3 inline-flex rounded border px-2 py-1 text-[10px] ${candidate==='pending'?`${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`:candidate==='submitted'?`${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`:`${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}`}>{candidate==='pending'?t.pending:candidate==='submitted'?t.submitted:t.returned}</span></div><div className="flex flex-wrap gap-2"><button type="button" onClick={()=>setCandidate('submitted')} className={`${btn} ${styles.accentBg} ${styles.accentSolidText}`}><FileCheck2 size={13}/>{t.submit}</button><button type="button" onClick={()=>setCandidate('returned')} className={`${btn} border ${styles.cardBorder} ${styles.cardText}`}><RefreshCw size={13}/>{t.returnEvidence}</button></div><p className={`text-[10px] leading-relaxed ${styles.cardTextMuted}`}>{t.noRealAction}</p></aside>
      </div>}

      {view === 'models' && <section className={`${card} space-y-4`}><div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center"><div><h2 className={titleClass}><Lightbulb size={16}/>{zh?'认知模型体系':'Cognitive model system'}</h2><p className={`mt-1 text-[11px] ${styles.cardTextMuted}`}>{zh?'明确模型类型、适用条件、输入输出契约与验证方式。':'Make model type, applicability, I/O contract and validation explicit.'}</p></div><div className="flex items-center gap-2"><span className={`text-[11px] ${styles.cardTextMuted}`}>{t.modelType}</span><select value={filter} onChange={e=>setFilter(e.target.value as typeof filter)} className={`rounded-md border px-2 py-2 text-xs ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}><option value="all">{t.all}</option><option value="mental">{t.mental}</option><option value="cognitive">{t.cognitive}</option></select></div></div><div className="grid grid-cols-1 gap-3 lg:grid-cols-2">{filteredModels.map(m=><article key={m.id} className={`rounded-md border p-4 ${styles.cardBorder}`}><div className="flex items-start justify-between gap-3"><div><span className={`mb-2 inline-flex rounded border px-1.5 py-0.5 text-[10px] ${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`}>{m.type==='mental'?t.mental:t.cognitive}</span><h3 className={`text-sm font-semibold ${styles.cardText}`}>{zh?m.name:m.en}</h3></div><span className={`shrink-0 rounded border px-1.5 py-0.5 text-[10px] ${m.status==='需复核'?`${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`:`${styles.successBg} ${styles.successText} ${styles.successBorder}`}`}>{zh?m.status:m.enStatus}</span></div><p className={`mt-2 text-xs leading-relaxed ${styles.cardTextMuted}`}>{zh?m.use:m.enUse}</p><div className={`mt-3 border-t pt-3 text-[10px] ${styles.cardBorder} ${styles.cardTextMuted}`}>{t.applicability}: {zh?'政策成效分析、资源配置与执行改进':'Policy impact, resource allocation and execution improvement'}</div></article>)}</div></section>}

      {view === 'agent' && <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.3fr)_minmax(280px,0.8fr)]"><section className={`${card} space-y-4`}><div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className={titleClass}><Workflow size={16}/> W Agent</h2><p className={`mt-1 text-[11px] ${styles.cardTextMuted}`}>{zh?'根据场景与 Mind 编排数据、知识、模型、验证和交付。':'Orchestrate data, knowledge, models, validation and delivery from scenario context.'}</p></div><button type="button" onClick={()=>setRan(true)} className={`${btn} ${styles.accentBg} ${styles.accentSolidText}`}><Play size={13}/>{ran?t.rerun:t.run}</button></div><div className="space-y-2">{STEPS.map((s,i)=><div key={s.id} className={`flex items-start gap-3 rounded-md border p-3 ${styles.cardBorder}`}><span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-[11px] ${ran?`${styles.successBg} ${styles.successText} ${styles.successBorder}`:`${styles.cardBorder} ${styles.cardTextMuted}`}`}>{ran?<Check size={14}/>:i+1}</span><div className="min-w-0 flex-1"><div className={`text-xs font-semibold ${styles.cardText}`}>{zh?s.zh:s.en}</div><p className={`mt-1 text-[11px] leading-relaxed ${styles.cardTextMuted}`}>{zh?s.detail:s.enDetail}</p></div><span className={`text-[10px] ${ran?styles.successText:styles.cardTextMuted}`}>{ran?(zh?'完成':'Done'):(zh?'待执行':'Queued')}</span></div>)}</div>{ran&&<div role="status" className={`rounded-md border p-3 text-xs ${styles.successBg} ${styles.successText} ${styles.successBorder}`}><CheckCircle2 size={14} className="mr-1 inline"/>{zh?'演示流程已完成':'Demo workflow completed'}</div>}<p className={`text-[10px] leading-relaxed ${styles.cardTextMuted}`}>{t.noRealAction}</p></section><aside className={`${card} space-y-3`}><h3 className={titleClass}><ShieldCheck size={16}/>{zh?'执行治理':'Execution governance'}</h3>{[{title:zh?'权限与租户边界':'Permissions & tenant boundary',body:zh?'工具调用前校验主体、资源和授权范围。':'Validate actor, resource and authorization before tool calls.'},{title:zh?'证据与模型约束':'Evidence & model constraints',body:zh?'记录来源、版本、适用条件与校验结果。':'Record source, version, applicability and validation.'},{title:zh?'行动审批':'Action approval',body:zh?'区分建议、授权、执行和效果，禁止越权直写。':'Separate recommendation, authorization, execution and outcomes.'}].map(x=><div key={x.title} className={`border-t pt-3 ${styles.cardBorder}`}><div className={`text-xs font-semibold ${styles.cardText}`}>{x.title}</div><p className={`mt-1 text-[11px] leading-relaxed ${styles.cardTextMuted}`}>{x.body}</p></div>)}</aside></div>}

      {view === 'review' && <div className="space-y-4"><section className={`${card} space-y-3`}><h2 className={titleClass}><RefreshCw size={16}/>{t.review}</h2><div className="grid grid-cols-1 gap-3 md:grid-cols-3">{[{title:t.expected,body:t.expectedText,icon:Clock3},{title:t.actual,body:t.actualText,icon:Activity},{title:t.variance,body:t.varianceText,icon:AlertTriangle}].map(x=>{const Icon=x.icon;return <article key={x.title} className={`rounded-md border p-3 ${styles.cardBorder}`}><div className={`flex items-center gap-2 text-xs font-semibold ${styles.cardText}`}><Icon size={14}/>{x.title}</div><p className={`mt-2 text-xs leading-relaxed ${styles.cardTextMuted}`}>{x.body}</p></article>;})}</div></section><section className={`${card} space-y-3`}><h3 className={titleClass}><GitBranch size={16}/>{zh?'学习与治理规则':'Learning and governance rules'}</h3><ul className={`space-y-2 text-xs leading-relaxed ${styles.cardTextMuted}`}><li>• {zh?'比较预期与实际结果，记录偏差与证据来源。':'Compare expected and actual outcomes, recording variance and evidence sources.'}</li><li>• {zh?'复核信念与假设；证据不足时保留不确定性。':'Review beliefs and hypotheses; preserve uncertainty when evidence is insufficient.'}</li><li>• {zh?'模型更新先形成候选，经测试、复核与批准后才能发布。':'Model updates remain candidates until tested, reviewed and approved.'}</li></ul></section></div>}

      <footer className={`flex flex-wrap items-center justify-between gap-2 border-t pt-3 text-[10px] ${styles.cardBorder} ${styles.cardTextMuted}`}><span className="flex items-center gap-1.5"><ShieldCheck size={12}/>{zh?'证据可追溯 · 判断可复核 · 行动受治理 · 学习可回滚':'Evidence traceable · Decisions reviewable · Actions governed · Learning reversible'}</span><span className="font-mono">ECOS / COGNITION / PROTOTYPE</span></footer>
    </div>
    {createOpen&&<div role="presentation" className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onMouseDown={e=>{if(e.target===e.currentTarget)setCreateOpen(false);}}><section role="dialog" aria-modal="true" aria-labelledby="cognitive-create-title" className={`w-full max-w-md rounded-xl border p-5 shadow-xl ${styles.cardBg} ${styles.cardBorder}`}><div className="mb-4 flex items-center justify-between gap-3"><h2 id="cognitive-create-title" className={`text-sm font-semibold ${styles.cardText}`}>{t.createTitle}</h2><button type="button" aria-label={t.cancel} onClick={()=>setCreateOpen(false)} className={`text-xs ${styles.cardTextMuted}`}>✕</button></div><label htmlFor="cognitive-scenario-name" className={`mb-1.5 block text-xs font-medium ${styles.cardText}`}>{t.scenarioNameLabel}</label><input id="cognitive-scenario-name" autoFocus value={name} onChange={e=>setName(e.target.value)} placeholder={t.placeholder} className={`w-full rounded-md border px-3 py-2 text-xs outline-none focus:ring-1 ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}/><p className={`mt-2 text-[10px] leading-relaxed ${styles.cardTextMuted}`}>{t.noRealAction}</p><div className="mt-5 flex justify-end gap-2"><button type="button" onClick={()=>setCreateOpen(false)} className={`${btn} border ${styles.cardBorder} ${styles.cardText}`}>{t.cancel}</button><button type="button" disabled={!name.trim()} onClick={()=>{setDraftName(name.trim());setName('');setCreateOpen(false);}} className={`${btn} ${styles.accentBg} ${styles.accentSolidText}`}>{t.create}</button></div></section></div>}
  </div>;
}
