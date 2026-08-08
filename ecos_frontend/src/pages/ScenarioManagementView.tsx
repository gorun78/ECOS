/**
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useState, useEffect } from 'react';
import LucideIcon from '../components/LucideIcon';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { CopilotPanel } from '../components/CopilotPanel';
import type { BusinessScenario, ScenarioManagementViewProps } from './project-workbench/types';
import { calcMetrics, hasBindingChanged } from './project-workbench/helpers';
import { initialScenarios, threatRadarData, efficiencyData } from './project-workbench/data';
import FusionMatrixTab from './project-workbench/tabs/FusionMatrixTab';
import DecisionDeskTab from './project-workbench/tabs/DecisionDeskTab';
import MetricsTab from './project-workbench/tabs/MetricsTab';
import GitVersionTab from './project-workbench/tabs/GitVersionTab';
import ScenarioList from './scenario/ScenarioList';
import ScenarioEditor, { type WizardState } from './scenario/ScenarioEditor';
import SimulationResultPanel from './scenario/SimulationResultPanel';

const DEF_COMMITS = [
  { id:'c1', hash:'b712fa4', author:'AOC_Admin', date:'2026-07-06 14:32:10', message:'feat: 初始化 ECOS 2026 暑运配置架构并挂载基础宽表', bindings:{ datasets:['ds_flight_schedules'], objectTypes:['AviationFlight'], knowledgeBases:['CAAC 121部运行合格审定规则'], aiAgents:['AOC签派大脑智能体 (王凯副本)'], securityPolicies:['proj_aviation_core'], interfaces:['航空运行指挥与航班调度系统'] } },
  { id:'c2', hash:'e42e519', author:'SecOps_Auditor', date:'2026-07-07 09:15:43', message:'sec: 级联安全底线重构，绑定 gr-pii 拦截网保护飞行员隐私', bindings:{ datasets:['ds_flight_schedules','ds_fleet_costs'], objectTypes:['AviationFlight','AviationPilot'], knowledgeBases:['CAAC 121部运行合格审定规则','AOC 雷雨天气签派应急改派规范'], aiAgents:['AOC签派大脑智能体 (王凯副本)','PII数据物理遮蔽卫士'], securityPolicies:['purpose_fleet_opt_2026','proj_aviation_core','gr-pii'], interfaces:['航空运行指挥与航班调度系统'] } },
];

const BRANCH_DEFAULTS = { scen_summer_rush:'main', scen_pilot_audit:'main', scen_evtol_sandbox:'main' };

export default function ScenarioManagementView({ showToast }: ScenarioManagementViewProps) {
  const toast = showToast || ((t:string,m:string)=>console.log(`[PMO] ${t}:`,m));
  const { locale } = useLanguage(); const { styles } = useTheme();
  const tl = (zh:string,en:string)=>locale==='zh'?zh:en;

  const [scenarios,setScenarios]=useState<BusinessScenario[]>(()=>{ try{ const c=localStorage.getItem('ecos_cached_scenarios'); return c?JSON.parse(c):initialScenarios; }catch{ return initialScenarios; }});
  const [selectedScenarioId,setSelectedScenarioId]=useState('scen_summer_rush');
  const [activeTab,setActiveTab]=useState<'fusion'|'decision'|'metrics'|'git'>('fusion');
  const [showCopilot,setShowCopilot]=useState(false);
  const [showWizardModal,setShowWizardModal]=useState(false);
  const [wizardScenarioId,setWizardScenarioId]=useState<string|null>(null);
  const [wizardStep,setWizardStep]=useState(1);
  const [wName,setWName]=useState('');const[wGoal,setWGoal]=useState('');
  const [wDesc,setWDesc]=useState('');const[wDept,setWDept]=useState('民航 AOC 运行指挥部');
  const [wPriority,setWPriority]=useState<'CRITICAL'|'HIGH'|'MEDIUM'|'LOW'>('HIGH');
  const [wBudget,setWBudget]=useState('¥800,000');
  const [wStatus,setWStatus]=useState<'ACTIVE'|'DRAFT'|'COMPLETED'|'SUSPENDED'>('DRAFT');
  const [wSafetyIndex,setWSafetyIndex]=useState('99.90%');
  const [wDatasets,setWDatasets]=useState<string[]>([]);const[wObjectTypes,setWObjectTypes]=useState<string[]>([]);
  const [wKnowledgeBases,setWKnowledgeBases]=useState<string[]>([]);const[wAiAgents,setWAiAgents]=useState<string[]>([]);
  const [wInterfaces,setWInterfaces]=useState<string[]>([]);const[wSecurityPolicies,setWSecurityPolicies]=useState<string[]>([]);
  const [gitCommits,setGitCommits]=useState<{[scenarioId:string]:any[]}>(()=>JSON.parse(localStorage.getItem('ecos_cached_git_commits')||'{}')||{scen_summer_rush:DEF_COMMITS});
  const [gitBranches,setGitBranches]=useState<{[scenarioId:string]:string}>(()=>JSON.parse(localStorage.getItem('ecos_cached_git_branches')||'{}')||BRANCH_DEFAULTS);
  const [selectedCommitId,setSelectedCommitId]=useState<string|null>(null);
  const [gitCommitMsg,setGitCommitMsg]=useState('');const[gitTerminalLogs,setGitTerminalLogs]=useState<string[]>([]);
  const [isGitPushing,setIsGitPushing]=useState(false);const[gitViewMode,setGitViewMode]=useState<'visual'|'json'>('visual');
  const [proposals,setProposals]=useState<any[]>([]);const[isLoadingProposals,setIsLoadingProposals]=useState(false);
  const [resolvingProposalId,setResolvingProposalId]=useState<string|null>(null);
  const [simQuery,setSimQuery]=useState('查询 UA102 机长张建国资质与保底工资');
  const [simRole,setSimRole]=useState<'AOC_DIRECTOR'|'EXTERNAL_CONTRACTOR'>('AOC_DIRECTOR');
  const [simResult,setSimResult]=useState<any>(null);const[isSimulating,setIsSimulating]=useState(false);

  const activeScenario = scenarios.find(s=>s.id===selectedScenarioId)||scenarios[0];
  useEffect(()=>{localStorage.setItem('ecos_cached_scenarios',JSON.stringify(scenarios));},[scenarios]);
  useEffect(()=>{localStorage.setItem('ecos_cached_git_commits',JSON.stringify(gitCommits));},[gitCommits]);
  useEffect(()=>{localStorage.setItem('ecos_cached_git_branches',JSON.stringify(gitBranches));},[gitBranches]);

  // ── Proposals ──
  const fetchProposalsList=async()=>{setIsLoadingProposals(true);try{const r=await fetch('/api/v1/ontology/proposals');if(r.ok){const d=await r.json();setProposals(Array.isArray(d)?d:(d?.data||[]));}}catch(e){console.error('Failed to fetch proposals',e);}finally{setIsLoadingProposals(false);}};
  useEffect(()=>{fetchProposalsList();const t=setInterval(fetchProposalsList,10000);return()=>clearInterval(t);},[]);
  const handleApproveProposal=async(id:string,actionId:string)=>{setResolvingProposalId(id);try{const r=await fetch(`/api/v1/ontology/proposals/${id}/approve`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({userRole:'签派总监',userName:'王凯'})});const d=await r.json();if(r.ok&&d.success){toast('success',`✅ 已完成 ${actionId} 对账写入！`);fetchProposalsList();setScenarios(prev=>prev.map(s=>s.id===selectedScenarioId?{...s,actualSafetyIndex:'100.00%',metrics:{...s.metrics,integrityScore:Math.min(100,s.metrics.integrityScore+1)}}:s));}else toast('error',d.message||'审批写入失败！');}catch(e:any){toast('error',`网络请求失败: ${e.message}`);}finally{setResolvingProposalId(null);}};
  const handleRejectProposal=async(id:string,actionId:string)=>{setResolvingProposalId(id);const reason=prompt('请输入拒绝理由:','物理主键冲突或越权写回');if(reason===null){setResolvingProposalId(null);return;}try{const r=await fetch(`/api/v1/ontology/proposals/${id}/reject`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({userName:'王凯',reason})});if(r.ok){toast('info',`❌ 已拒绝对账事务 ${actionId}`);fetchProposalsList();}else toast('error','拒绝失败！');}catch(e:any){toast('error',`网络请求失败: ${e.message}`);}finally{setResolvingProposalId(null);}};

  // ── Simulation ──
  const handleRunSandbox=async()=>{setIsSimulating(true);try{const params={query:simQuery,userId:simRole==='AOC_DIRECTOR'?'analyst_li':'contractor_xiao',orgId:simRole==='AOC_DIRECTOR'?'org_aviation_hq':'org_contractor',clientIp:simRole==='AOC_DIRECTOR'?'10.120.5.23':'222.22.22.22',projectId:activeScenario.bindings.securityPolicies[1]||'proj_aviation_core',datasetId:activeScenario.bindings.datasets[0]||'ds_flight_schedules',purposeId:activeScenario.bindings.securityPolicies[0]||'purpose_fleet_opt_2026',distanceMetric:'cosine'};const r=await fetch('/api/v1/knowledge/query',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(params)});if(r.ok){const d=await r.json();setSimResult(d);toast(d.verdict==='DENIED'?'error':'success',d.verdict==='DENIED'?'🛡️ 沙箱安全拦截！':'🧬 沙箱校验通过！');}else setSimResult({verdict:simRole==='AOC_DIRECTOR'?'GRANTED':'DENIED',answer:simRole==='AOC_DIRECTOR'?'[模拟] 已获取机长张建国信息。SSN/薪资已通过 PII 遮蔽。':'⚠️ 外部承包商无权访问此人资物理表。',groundedDocs:[{title:'AOC民航适航规范',score:0.94}]});}catch(e:any){setSimResult({verdict:simRole==='AOC_DIRECTOR'?'GRANTED':'DENIED',answer:simRole==='AOC_DIRECTOR'?`[本地沙箱] 已在「${activeScenario.name}」场景下执行。`:'⚠️ 外部承包商访问限制级 PII 被阻断。',groundedDocs:[{title:'CAAC 121部',score:0.92}]});}finally{setIsSimulating(false);}};

  // ── Wizard ──
  const openCreateWizard=()=>{setWizardScenarioId(null);setWizardStep(1);setWName('');setWGoal('');setWDesc('');setWDept('民航 AOC 运行指挥部');setWPriority('HIGH');setWBudget('¥800,000');setWStatus('DRAFT');setWSafetyIndex('99.90%');setWDatasets(['ds_flight_schedules']);setWObjectTypes(['AviationFlight']);setWKnowledgeBases(['CAAC 121部运行合格审定规则']);setWAiAgents(['默认决策智能体助理']);setWInterfaces(['低代码决策可视化系统']);setWSecurityPolicies(['proj_aviation_core']);setShowWizardModal(true);};
  const openEditWizard=(scen:BusinessScenario)=>{setWizardScenarioId(scen.id);setWizardStep(1);setWName(scen.name);setWGoal(scen.businessGoal);setWDesc(scen.description);setWDept(scen.department);setWPriority(scen.priority);setWBudget(scen.budget);setWStatus(scen.status);setWSafetyIndex(scen.safetyIndexTarget);setWDatasets(scen.bindings.datasets||[]);setWObjectTypes(scen.bindings.objectTypes||[]);setWKnowledgeBases(scen.bindings.knowledgeBases||[]);setWAiAgents(scen.bindings.aiAgents||[]);setWInterfaces(scen.bindings.interfaces||[]);setWSecurityPolicies(scen.bindings.securityPolicies||[]);setShowWizardModal(true);};
  const handleSaveWizard=()=>{if(!wName.trim()){toast('error','请填写场景名称！');setWizardStep(1);return;}if(!wGoal.trim()){toast('error','请填写业务目标！');setWizardStep(1);return;}const m=calcMetrics(wDatasets,wObjectTypes,wKnowledgeBases,wAiAgents,wInterfaces,wSecurityPolicies);if(wizardScenarioId){const updated=scenarios.map(s=>s.id===wizardScenarioId?{...s,name:wName,description:wDesc||'暂无描述。',businessGoal:wGoal,department:wDept,priority:wPriority,status:wStatus,budget:wBudget,safetyIndexTarget:wSafetyIndex,actualSafetyIndex:wStatus==='ACTIVE'?'99.50%':'0.00%',bindings:{datasets:wDatasets,objectTypes:wObjectTypes,knowledgeBases:wKnowledgeBases,aiAgents:wAiAgents,interfaces:wInterfaces,securityPolicies:wSecurityPolicies},metrics:{integrityScore:m.integrityScore,mappingCompleteness:m.mappingCompleteness,threatBlockRate:m.threatBlockRate,slaScore:m.slaScore}}:s);setScenarios(updated);localStorage.setItem('ecos_cached_scenarios',JSON.stringify(updated));if(hasBindingChanged((gitCommits[wizardScenarioId]||[]).slice(-1)[0],{datasets:wDatasets,objectTypes:wObjectTypes,knowledgeBases:wKnowledgeBases,aiAgents:wAiAgents,interfaces:wInterfaces,securityPolicies:wSecurityPolicies}))setGitCommits(prev=>({...prev,[wizardScenarioId]:[...(prev[wizardScenarioId]||[]),{id:`c_${Date.now()}`,hash:Math.random().toString(16).substring(2,9),author:'Wizard_Auto',date:new Date().toISOString().replace('T',' ').substring(0,19),message:`refactor(config): 更新「${wName}」要素`,bindings:{datasets:wDatasets,objectTypes:wObjectTypes,knowledgeBases:wKnowledgeBases,aiAgents:wAiAgents,interfaces:wInterfaces,securityPolicies:wSecurityPolicies}}]}));toast('success',`✅ 已更新场景「${wName}」！`);}else{const newId=`scen_${Date.now()}`;const newScen:BusinessScenario={id:newId,name:wName,description:wDesc||'暂无描述。',businessGoal:wGoal,department:wDept,priority:wPriority,status:wStatus,budget:wBudget,safetyIndexTarget:wSafetyIndex,actualSafetyIndex:wStatus==='ACTIVE'?'99.50%':'0.00%',createdAt:new Date().toISOString().split('T')[0],bindings:{datasets:wDatasets,objectTypes:wObjectTypes,knowledgeBases:wKnowledgeBases,aiAgents:wAiAgents,interfaces:wInterfaces,securityPolicies:wSecurityPolicies},metrics:{integrityScore:m.integrityScore,mappingCompleteness:m.mappingCompleteness,threatBlockRate:m.threatBlockRate,slaScore:m.slaScore}};setGitCommits(prev=>({...prev,[newId]:[{id:`c_${Date.now()}`,hash:Math.random().toString(16).substring(2,9),author:'Wizard_Init',date:new Date().toISOString().replace('T',' ').substring(0,19),message:`feat: 初始化场景「${wName}」`,bindings:{...newScen.bindings}}]}));setGitBranches(prev=>({...prev,[newId]:'main'}));setScenarios([...scenarios,newScen]);setSelectedScenarioId(newId);localStorage.setItem('ecos_cached_scenarios',JSON.stringify([...scenarios,newScen]));toast('success',`🎉 已创建场景「${wName}」！`);}setShowWizardModal(false);};

  // ── Git ──
  const handleGitCommitManual=(msg:string)=>{if(!msg.trim()){toast('error','请输入提交说明！');return;}const nc={id:`c_${Date.now()}`,hash:Math.random().toString(16).substring(2,9),author:'AOC_Admin',date:new Date().toISOString().replace('T',' ').substring(0,19),message:msg.trim(),bindings:{...activeScenario.bindings}};setGitCommits(prev=>({...prev,[selectedScenarioId]:[...(prev[selectedScenarioId]||[]),nc]}));setGitCommitMsg('');toast('success',`📦 Git 提交成功！${nc.hash}`);};
  const handleGitCheckoutCommit=(commit:any)=>{if(!commit)return;const wB=commit.bindings;const m=calcMetrics(wB.datasets||[],wB.objectTypes||[],wB.knowledgeBases||[],wB.aiAgents||[],wB.interfaces||[],wB.securityPolicies||[]);const updated=scenarios.map(s=>s.id===selectedScenarioId?{...s,bindings:{datasets:wB.datasets||[],objectTypes:wB.objectTypes||[],knowledgeBases:wB.knowledgeBases||[],aiAgents:wB.aiAgents||[],interfaces:wB.interfaces||[],securityPolicies:wB.securityPolicies||[]},metrics:{integrityScore:m.integrityScore,mappingCompleteness:m.mappingCompleteness,threatBlockRate:m.threatBlockRate,slaScore:m.slaScore}}:s);setScenarios(updated);localStorage.setItem('ecos_cached_scenarios',JSON.stringify(updated));toast('success',`🔮 已回滚至 ${commit.hash}`);};
  const handleSwitchGitBranch=(branchName:string)=>{setGitBranches(prev=>({...prev,[selectedScenarioId]:branchName}));toast('info',`🔀 已切换至分支 [${branchName}]`);};
  const handleGitPushRemote=()=>{if(isGitPushing)return;setIsGitPushing(true);setGitTerminalLogs(['$ git remote -v','origin  gitlab.ecos.internal:aviation-dispatch/scenarios.git (fetch)','origin  gitlab.ecos.internal:aviation-dispatch/scenarios.git (push)','$ git status',`On branch ${gitBranches[selectedScenarioId]||'main'}`,`Your branch is ahead by ${gitCommits[selectedScenarioId]?.length||1} commits.`,`$ git push origin ${gitBranches[selectedScenarioId]||'main'}`,'🔐 [MFA Signature Verified] AOC_DIRECTOR certificate approved.']);setTimeout(()=>setGitTerminalLogs(prev=>[...prev,'Enumerating objects: 7, done.','Counting objects: 100% (7/7), done.','Compressing objects: 100% (4/4), done.']),1000);setTimeout(()=>{setGitTerminalLogs(prev=>[...prev,'Writing objects: 100% (4/4), done.','To gitlab.ecos.internal:aviation-dispatch/scenarios.git','🟢 ECOS 云底座要素库配置冻结成功！']);setIsGitPushing(false);toast('success','🚀 ECOS 云端同步完成！');},2500);};

  const wizard: WizardState = { showWizardModal,wizardScenarioId,wizardStep,wName,wGoal,wDesc,wDept,wPriority,wBudget,wStatus,wSafetyIndex,wDatasets,wObjectTypes,wKnowledgeBases,wAiAgents,wInterfaces,wSecurityPolicies };

  // ══════════ RENDER ══════════
  return (
    <div className={`flex-1 flex flex-col ${styles.appBg} ${styles.appText} overflow-hidden font-sans relative`}>
      <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0 flex flex-col md:flex-row md:items-center justify-between gap-4`}>
        <div>
          <div className="flex items-center gap-2"><span className="p-1.5 rounded-md bg-indigo-600 text-white flex items-center justify-center"><LucideIcon name="Briefcase" size={16}/></span><h1 className="text-lg font-bold tracking-tight text-white flex items-center gap-2">场景与项目综合调度中心 <span className="text-[10px] bg-indigo-900/60 border border-indigo-700/50 px-2 py-0.5 rounded text-indigo-300 font-bold tracking-widest uppercase">Executive Cockpit</span></h1></div>
          <p className={`text-xs ${styles.cardTextMuted} mt-1`}>从企业高层管理者的视角，将孤立的「物理数据、语义本体、知识 RAG、AI 智能体及安全围栏」融合成具体的高价值业务场景。</p>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center max-w-2xl w-full md:w-auto">
          <KpiBox styles={styles} label="运行场景总数" value={`${scenarios.length}`} suffix="个" color="text-blue-400"/>
          <KpiBox styles={styles} label="融合预算估算" value="¥2.85M" color="text-indigo-400"/>
          <KpiBox styles={styles} label="威胁防御阻断率" value="100%" color="text-emerald-400"/>
          <KpiBox styles={styles} label="平均对账时效" value="2.8s" suffix={<span className="text-xs font-normal text-emerald-400">(-93.7%)</span>} color="text-amber-400"/>
          <div className="col-span-2 sm:col-span-4 flex justify-end mt-1">
            <button onClick={()=>setShowCopilot(!showCopilot)} className={`flex items-center gap-1.5 px-3 py-1.5 rounded border transition-colors cursor-pointer text-xs font-bold ${showCopilot?'bg-blue-600 text-white border-blue-500':`${styles.cardBg} ${styles.cardTextMuted} border-[var(--border)] hover:bg-slate-800/50`}`}><LucideIcon name="MessageSquare" size={12}/>{showCopilot?tl('关闭助手','Close Copilot'):tl('智能助手','Copilot')}</button>
          </div>
        </div>
      </div>

      <div className="flex-1 flex overflow-hidden">
        <ScenarioList scenarios={scenarios} selectedScenarioId={selectedScenarioId} onSelect={setSelectedScenarioId} onCreateNew={openCreateWizard} styles={styles} locale={locale} tl={tl}/>

        <div className={`flex-1 flex flex-col overflow-hidden ${styles.appBg}`}>
          <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0`}>
            <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
              <div><div className="flex items-center gap-2"><span className="text-[10px] bg-emerald-950 border border-emerald-900 px-2 py-0.5 rounded text-emerald-400 font-mono font-bold">ACTIVE PROJECT</span><span className={`text-xs font-semibold ${styles.cardTextMuted} font-mono`}>ID: {activeScenario.id}</span></div><h2 className="text-base font-extrabold text-white mt-1">{activeScenario.name}</h2><p className={`text-xs ${styles.cardTextMuted} mt-1 max-w-4xl leading-relaxed`}>{activeScenario.description}</p></div>
              <div className="flex flex-col gap-2 items-end self-end sm:self-start shrink-0"><span className={`text-xs ${styles.cardTextMuted}`}>部门责任人： <span className="text-xs bg-slate-800 border border-slate-700 px-2.5 py-1 rounded font-bold text-slate-200">{activeScenario.department}</span></span><button onClick={()=>openEditWizard(activeScenario)} className="px-3 py-1 bg-indigo-600/20 hover:bg-indigo-600 border border-indigo-500/30 text-indigo-300 hover:text-white text-[11px] font-bold rounded flex items-center gap-1 transition-all cursor-pointer shadow-xs"><LucideIcon name="Settings" size={11} className="text-indigo-400"/>修改场景要素对接 (向导)</button></div>
            </div>
            <div className={`grid grid-cols-2 sm:grid-cols-4 gap-4 mt-4 pt-3 border-t ${styles.cardBorder} text-xs font-mono`}>
              <div><span className={`${styles.cardTextMuted} text-[10px] block`}>项目预设总预算</span><span className="text-sm font-bold text-indigo-400">{activeScenario.budget}</span></div>
              <div><span className={`${styles.cardTextMuted} text-[10px] block`}>安全适航合规率</span><span className="text-sm font-bold text-teal-400">{activeScenario.safetyIndexTarget}</span></div>
              <div><span className={`${styles.cardTextMuted} text-[10px] block`}>当前实际运行安全率</span><span className="text-sm font-bold text-emerald-400">{activeScenario.actualSafetyIndex}</span></div>
              <div><span className={`${styles.cardTextMuted} text-[10px] block`}>项目创建日期</span><span className={`text-sm font-bold ${styles.cardText}`}>{activeScenario.createdAt}</span></div>
            </div>
          </div>

          <div className={`h-10 ${styles.cardBg} px-4 border-b ${styles.cardBorder} shrink-0 flex items-center justify-between`}>
            <div className="flex gap-2">
              {(['fusion','decision','metrics','git']as const).map(tab=>(
                <button key={tab} onClick={()=>setActiveTab(tab)} className={`px-4 h-10 border-b-2 text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer ${activeTab===tab?'border-indigo-500 text-indigo-400':`border-transparent ${styles.cardTextMuted} hover:text-slate-200`}`}>
                  <LucideIcon name={tab==='fusion'?'Network':tab==='decision'?'ShieldCheck':tab==='metrics'?'TrendingUp':'GitBranch'} size={13}/>
                  <span>{tl(tab==='fusion'?'认知能力融合矩阵':tab==='decision'?'决策与对账沙箱':tab==='metrics'?'业务指标分析':'Git 版本控制',tab==='fusion'?'Fusion Matrix':tab==='decision'?'Decision & Sandbox':tab==='metrics'?'Metrics & Analytics':'Git Version Control')}</span>
                  {tab==='decision'&&proposals.filter((p:any)=>p.status==='pending').length>0&&<span className="bg-rose-500 text-white text-[9px] px-1.5 py-0.2 rounded-full animate-bounce">{proposals.filter((p:any)=>p.status==='pending').length} {tl('待对账','pending')}</span>}
                </button>
              ))}
            </div>
            <div className="text-[10px] text-indigo-400 font-mono font-bold bg-indigo-950/60 border border-indigo-900/60 px-2 py-0.5 rounded">{tl(activeTab==='fusion'?'资源绑定与集成':activeTab==='decision'?'审批与威胁日志':activeTab==='metrics'?'数据分析看板':'Git版本与架构','')}</div>
          </div>

          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {activeTab==='fusion'&&<FusionMatrixTab activeScenario={activeScenario}/>}
            {activeTab==='decision'&&<><SimulationResultPanel simQuery={simQuery} setSimQuery={setSimQuery} simRole={simRole} setSimRole={setSimRole} simResult={simResult} isSimulating={isSimulating} onRun={handleRunSandbox} styles={styles} locale={locale} tl={tl} safetyIndexActual={activeScenario.actualSafetyIndex}/><DecisionDeskTab activeScenario={activeScenario} proposals={proposals} isLoadingProposals={isLoadingProposals} fetchProposalsList={fetchProposalsList} handleApproveProposal={handleApproveProposal} handleRejectProposal={handleRejectProposal} resolvingProposalId={resolvingProposalId} simQuery={simQuery} setSimQuery={setSimQuery} simRole={simRole} setSimRole={setSimRole} simResult={simResult} isSimulating={isSimulating} handleRunSandbox={handleRunSandbox}/></>}
            {activeTab==='metrics'&&<MetricsTab threatRadarData={threatRadarData} efficiencyData={efficiencyData}/>}
            {activeTab==='git'&&<GitVersionTab gitCommits={gitCommits} gitBranches={gitBranches} selectedScenarioId={selectedScenarioId} gitCommitMsg={gitCommitMsg} setGitCommitMsg={setGitCommitMsg} gitTerminalLogs={gitTerminalLogs} isGitPushing={isGitPushing} gitViewMode={gitViewMode} setGitViewMode={setGitViewMode} selectedCommitId={selectedCommitId} setSelectedCommitId={setSelectedCommitId} handleGitCommitManual={handleGitCommitManual} handleGitCheckoutCommit={handleGitCheckoutCommit} handleSwitchGitBranch={handleSwitchGitBranch} handleGitPushRemote={handleGitPushRemote}/>}
          </div>
        </div>
      </div>

      <ScenarioEditor wizard={wizard} onClose={()=>setShowWizardModal(false)} onStepChange={setWizardStep} onSave={handleSaveWizard} setWName={setWName} setWGoal={setWGoal} setWDesc={setWDesc} setWDept={setWDept} setWPriority={setWPriority} setWBudget={setWBudget} setWStatus={setWStatus} setWSafetyIndex={setWSafetyIndex} setWDatasets={setWDatasets} setWObjectTypes={setWObjectTypes} setWKnowledgeBases={setWKnowledgeBases} setWAiAgents={setWAiAgents} setWInterfaces={setWInterfaces} setWSecurityPolicies={setWSecurityPolicies} styles={styles} toast={toast}/>

      {showCopilot&&<div className="absolute top-0 right-0 bottom-0 w-80 border-l border-[var(--border)] bg-[var(--card)] shadow-2xl z-40 flex flex-col overflow-hidden"><CopilotPanel agentType="scenario"/></div>}
    </div>
  );
}

function KpiBox({styles,label,value,suffix,color}:{styles:any;label:string;value:string;suffix?:React.ReactNode;color:string}){
  return <div className={`${styles.cardBg} border ${styles.cardBorder} p-2 rounded-lg`}><span className={`block text-[10px] ${styles.cardTextMuted} font-bold uppercase`}>{label}</span><span className={`text-lg font-extrabold ${color}`}>{value} {suffix}</span></div>;
}
