/**
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React from 'react';
import LucideIcon from '../../components/LucideIcon';
import type { ThemeStyles } from '../../components/ThemeContext';
import { AVAILABLE_DATASETS,AVAILABLE_OBJECTS,AVAILABLE_KNOWLEDGE,AVAILABLE_AGENTS,AVAILABLE_INTERFACES,AVAILABLE_SECURITY } from '../project-workbench/data';

export interface WizardState { showWizardModal:boolean; wizardScenarioId:string|null; wizardStep:number; wName:string; wGoal:string; wDesc:string; wDept:string; wPriority:'CRITICAL'|'HIGH'|'MEDIUM'|'LOW'; wBudget:string; wStatus:'ACTIVE'|'DRAFT'|'COMPLETED'|'SUSPENDED'; wSafetyIndex:string; wDatasets:string[]; wObjectTypes:string[]; wKnowledgeBases:string[]; wAiAgents:string[]; wInterfaces:string[]; wSecurityPolicies:string[]; }

export interface ScenarioEditorProps {
  wizard:WizardState; onClose:()=>void; onStepChange:(s:number)=>void; onSave:()=>void;
  setWName:(v:string)=>void; setWGoal:(v:string)=>void; setWDesc:(v:string)=>void; setWDept:(v:string)=>void;
  setWPriority:(v:'CRITICAL'|'HIGH'|'MEDIUM'|'LOW')=>void; setWBudget:(v:string)=>void;
  setWStatus:(v:'ACTIVE'|'DRAFT'|'COMPLETED'|'SUSPENDED')=>void; setWSafetyIndex:(v:string)=>void;
  setWDatasets:(v:string[])=>void; setWObjectTypes:(v:string[])=>void; setWKnowledgeBases:(v:string[])=>void;
  setWAiAgents:(v:string[])=>void; setWInterfaces:(v:string[])=>void; setWSecurityPolicies:(v:string[])=>void;
  styles:ThemeStyles; toast:(t:string,m:string)=>void;
}

const STEPS=[{s:1,l:'1. 场景要素',i:'Briefcase'},{s:2,l:'2. 物理数据',i:'Database'},{s:3,l:'3. 联邦本体',i:'Network'},{s:4,l:'4. 合规知识',i:'BookOpen'},{s:5,l:'5. AI 智能体',i:'Cpu'},{s:6,l:'6. 应用工作台',i:'Layout'},{s:7,l:'7. 安全阻断',i:'ShieldCheck'}]as const;

function CbList({items,selected,onChange,styles}:{items:{id:string;label:string;desc?:string}[];selected:string[];onChange:(v:string[])=>void;styles:ThemeStyles}){
  return <div className={`grid grid-cols-1 gap-2 max-h-[220px] overflow-y-auto p-2 ${styles.inputBg} border ${styles.inputBorder} rounded`}>
    {items.map(item=>{const ck=selected.includes(item.id);return <label key={item.id} className={`flex items-start gap-2.5 p-2.5 rounded cursor-pointer transition-colors border ${ck?'bg-indigo-950/30 border-indigo-500/40 text-indigo-200':`${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-slate-850/40`}`}><input type="checkbox" checked={ck} onChange={()=>onChange(ck?selected.filter(id=>id!==item.id):[...selected,item.id])} className="mt-0.5 cursor-pointer accent-indigo-500"/><div className="space-y-0.5"><span className="font-bold text-[11px] block text-slate-200">{item.label}</span>{item.desc&&<span className={`text-[10px] ${styles.cardTextMuted} block leading-tight`}>{item.desc}</span>}</div></label>;})}
  </div>;
}

export default function ScenarioEditor({wizard,onClose,onStepChange,onSave,setWName,setWGoal,setWDesc,setWDept,setWPriority,setWBudget,setWStatus,setWSafetyIndex,setWDatasets,setWObjectTypes,setWKnowledgeBases,setWAiAgents,setWInterfaces,setWSecurityPolicies,styles,toast}:ScenarioEditorProps){
  const {showWizardModal,wizardScenarioId,wizardStep,wName,wGoal,wDesc,wDept,wPriority,wBudget,wStatus,wSafetyIndex,wDatasets,wObjectTypes,wKnowledgeBases,wAiAgents,wInterfaces,wSecurityPolicies}=wizard;
  if(!showWizardModal)return null;

  const next=()=>{if(wizardStep===1&&(!wName.trim()||!wGoal.trim())){toast('error','请先填写必填的场景名称与核心目标！');return;}onStepChange(wizardStep+1);};
  const prev=()=>{if(wizardStep>1)onStepChange(wizardStep-1);};
  const Ic=(n:string,s:number)=>React.createElement(LucideIcon,{name:n,size:s});

  return <div className="fixed inset-0 bg-black/80 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-fadeIn overflow-y-auto">
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl max-w-2xl w-full overflow-hidden shadow-2xl my-8`}>
      <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} flex items-center justify-between`}>
        <span className="text-sm font-bold text-white flex items-center gap-1.5">{Ic('Briefcase',14)}<span className="text-indigo-400"/>{wizardScenarioId?'修改场景要素对接 (ECOS 全生命周期向导)':'创建全新 ECOS 业务融合场景 (全要素向导)'}</span>
        <button onClick={onClose} className={`${styles.cardTextMuted} hover:text-white cursor-pointer`}>{Ic('X',16)}</button>
      </div>

      <div className={`${styles.cardBg} px-4 py-3 border-b ${styles.cardBorder} overflow-x-auto shrink-0`}>
        <div className="flex items-center justify-between min-w-[760px] px-2 py-1">
          {STEPS.map((it,idx,arr)=><React.Fragment key={it.s}>
            <div className="flex items-center gap-1.5">
              <div className={`w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold transition-all ${wizardStep===it.s?'bg-indigo-600 text-white ring-4 ring-indigo-950 shadow-md':wizardStep>it.s?'bg-emerald-600 text-white font-bold':`bg-slate-800 ${styles.cardTextMuted} border border-slate-700`}`}>{wizardStep>it.s?'✓':it.s}</div>
              <span className={`text-[11px] font-bold whitespace-nowrap transition-colors ${wizardStep===it.s?'text-indigo-400 font-extrabold':wizardStep>it.s?'text-emerald-500':styles.cardTextMuted}`}>{it.l}</span>
            </div>
            {idx<arr.length-1&&<div className={`flex-1 h-[2px] mx-2 min-w-[12px] transition-all duration-300 ${wizardStep>it.s?'bg-emerald-600/60':'bg-slate-800'}`}/>}
          </React.Fragment>)}
        </div>
      </div>

      <div className="p-5 max-h-[55vh] overflow-y-auto space-y-4 text-xs">
        {wizardStep===1&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">{Ic('Info',12)}ECOS 顶层设计 - 场景要素与项目战术目标定位</span><p className="text-[10px] text-[var(--card-text-muted)] leading-relaxed">从场景的基本物理特征、主责主控部门、安全目标与保障预算出发，定义该业务场景在数字太空中的核心价值边界与战术攻坚指向。</p></div>
          <div className="space-y-1.5"><label className="text-[var(--card-text-muted)] font-bold block">1. 场景/项目名称 <span className="text-red-400">*</span></label><input type="text" placeholder="例如：2026 跨境执勤时数综合对账合规中心" value={wName} onChange={e=>setWName(e.target.value)} className={`w-full p-2.5 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}/></div>
          <div className="space-y-1.5"><label className="text-[var(--card-text-muted)] font-bold block">2. 核心战术目标 <span className="text-red-400">*</span></label><input type="text" placeholder="例如：通过大模型对账推理，将飞行执勤对账时效降低90%" value={wGoal} onChange={e=>setWGoal(e.target.value)} className={`w-full p-2.5 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}/></div>
          <div className="space-y-1.5"><label className="text-[var(--card-text-muted)] font-bold block">3. 场景描述与背景</label><textarea placeholder="详细描述该业务场景涉及的关联部门、合规背景以及具体想要预防的零信任数据安全隐患。" value={wDesc} onChange={e=>setWDesc(e.target.value)} rows={3} className={`w-full p-2.5 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none font-sans focus:border-indigo-500 transition-colors`}/></div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5"><label className="text-[var(--card-text-muted)] font-bold block">4. 主责主导部门</label><select value={wDept} onChange={e=>setWDept(e.target.value)} className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}><option>民航 AOC 运行指挥部</option><option>人资财务审计处</option><option>新航线前沿探索战略部</option><option>企业零信任安全审计处</option></select></div>
            <div className="space-y-1.5"><label className="text-[var(--card-text-muted)] font-bold block">5. 安全优先级</label><select value={wPriority} onChange={e=>setWPriority(e.target.value as any)} className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded text-indigo-400 font-bold outline-none focus:border-indigo-500 transition-colors`}><option value="CRITICAL">🔥 CRITICAL</option><option value="HIGH">⚡ HIGH</option><option value="MEDIUM">📋 MEDIUM</option><option value="LOW">🛡️ LOW</option></select></div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5"><label className="text-[var(--card-text-muted)] font-bold block">6. 运行状态</label><select value={wStatus} onChange={e=>setWStatus(e.target.value as any)} className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none`}><option value="DRAFT">📋 DRAFT</option><option value="ACTIVE">⚡ ACTIVE</option><option value="COMPLETED">✅ COMPLETED</option><option value="SUSPENDED">⚠️ SUSPENDED</option></select></div>
            <div className="space-y-1.5"><label className="text-[var(--card-text-muted)] font-bold block">7. 场景保障预算</label><input type="text" value={wBudget} onChange={e=>setWBudget(e.target.value)} className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none`}/></div>
            <div className="space-y-1.5"><label className="text-[var(--card-text-muted)] font-bold block">8. 目标安全性指数</label><input type="text" value={wSafetyIndex} onChange={e=>setWSafetyIndex(e.target.value)} className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none`}/></div>
          </div>
        </div>}

        {wizardStep===2&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">{Ic('Database',12)}ECOS 物理数据层</span><p className="text-[10px] text-[var(--card-text-muted)] leading-relaxed">联接底层原始物理数据表与主库变动流。这些物理数据是支撑整个融合场景本体演化与实时分析的绝对基石。</p></div>
          <div className="space-y-2"><div className="flex items-center justify-between"><label className="text-[var(--card-text)] font-bold block">选择集成的物理表与数据源</label><span className="text-[10px] text-indigo-400 font-mono">已选择 {wDatasets.length} 个</span></div><CbList items={AVAILABLE_DATASETS} selected={wDatasets} onChange={setWDatasets} styles={styles}/></div>
        </div>}

        {wizardStep===3&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">{Ic('Network',12)}ECOS 联邦逻辑本体层</span><p className="text-[10px] text-[var(--card-text-muted)] leading-relaxed">将原始物理表在虚拟对齐空间内抽象为业务对象。本体屏蔽了底层关系型表的物理存储细节，使得智能体能直接进行常识推理。</p></div>
          <div className="space-y-2"><div className="flex items-center justify-between"><label className="text-[var(--card-text)] font-bold block">勾选需要对接的联邦逻辑本体实体</label><span className="text-[10px] text-indigo-400 font-mono">已选择 {wObjectTypes.length} 个</span></div><CbList items={AVAILABLE_OBJECTS} selected={wObjectTypes} onChange={setWObjectTypes} styles={styles}/></div>
        </div>}

        {wizardStep===4&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">{Ic('BookOpen',12)}ECOS 规则与先验知识库层</span><p className="text-[10px] text-[var(--card-text-muted)] leading-relaxed">导入符合行业标准的合规审定标准、管理条例或应急预案。这构成了智能体进行逻辑推理、审查与核准的核心规则约束红线。</p></div>
          <div className="space-y-2"><div className="flex items-center justify-between"><label className="text-[var(--card-text)] font-bold block">选择注入此场景的合规知识库</label><span className="text-[10px] text-indigo-400 font-mono">已选择 {wKnowledgeBases.length} 个</span></div><CbList items={AVAILABLE_KNOWLEDGE} selected={wKnowledgeBases} onChange={setWKnowledgeBases} styles={styles}/></div>
        </div>}

        {wizardStep===5&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">{Ic('Cpu',12)}ECOS 协同认知智能体</span><p className="text-[10px] text-[var(--card-text-muted)] leading-relaxed">将不同的后台推理大模型或自动化分析智能体指派给本场景。它们将协同处理自动排班、财务差异账单审查或敏感越权告警。</p></div>
          <div className="space-y-2"><div className="flex items-center justify-between"><label className="text-[var(--card-text)] font-bold block">指派协同大语言模型与决策智能体</label><span className="text-[10px] text-indigo-400 font-mono">已选择 {wAiAgents.length} 个</span></div><CbList items={AVAILABLE_AGENTS} selected={wAiAgents} onChange={setWAiAgents} styles={styles}/></div>
        </div>}

        {wizardStep===6&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">{Ic('Layout',12)}ECOS 应用工作台与可视化看板</span><p className="text-[10px] text-[var(--card-text-muted)] leading-relaxed">联接面向用户的终端作业界面、低代码大盘以及业务决策流。这是最终保障运营总监、DPO 或调度员人机共协的窗口。</p></div>
          <div className="space-y-2"><div className="flex items-center justify-between"><label className="text-[var(--card-text)] font-bold block">绑定前端业务展示系统与可视化大盘</label><span className="text-[10px] text-indigo-400 font-mono">已选择 {wInterfaces.length} 个</span></div><CbList items={AVAILABLE_INTERFACES} selected={wInterfaces} onChange={setWInterfaces} styles={styles}/></div>
        </div>}

        {wizardStep===7&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">{Ic('ShieldCheck',12)}ECOS 零信任级联安全隔离域</span><p className="text-[10px] text-[var(--card-text-muted)] leading-relaxed">挂载强约束型的细粒度安全规则。对敏感 PII（如机长社保、薪酬）强制在拦截挂钩中遮蔽，非白名单 IP 自动沙阻断，强制总监多级签批。</p></div>
          <div className="space-y-2"><div className="flex items-center justify-between"><label className="text-[var(--card-text)] font-bold block">勾选级联安全隔离域规则与拦截网</label><span className="text-[10px] text-indigo-400 font-mono">已选择 {wSecurityPolicies.length} 个</span></div><CbList items={AVAILABLE_SECURITY} selected={wSecurityPolicies} onChange={setWSecurityPolicies} styles={styles}/></div>
        </div>}
      </div>

      <div className={`p-4 ${styles.cardBg} border-t ${styles.cardBorder} flex justify-between items-center shrink-0`}>
        <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>步进 {wizardStep} / 7 | ECOS Engine Active</span>
        <div className="flex gap-2">
          {wizardStep>1&&<button onClick={prev} className={`px-3.5 py-1.5 ${styles.inputBg} ${styles.cardTextMuted} text-xs font-bold rounded cursor-pointer transition-all flex items-center gap-1`}>{Ic('ChevronLeft',12)}上一步</button>}
          {wizardStep<7?<button onClick={next} className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold rounded cursor-pointer transition-all flex items-center gap-1">下一步{Ic('ChevronRight',12)}</button>:<button onClick={onSave} className="px-5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded cursor-pointer transition-all flex items-center gap-1 shadow-lg shadow-emerald-900/20">{Ic('Check',12)}{wizardScenarioId?'保存全部要素绑定':'完成并初始化融合场景'}</button>}
        </div>
      </div>
    </div>
  </div>;
}
