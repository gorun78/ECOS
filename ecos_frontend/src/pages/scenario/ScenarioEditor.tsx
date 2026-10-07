/**
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React from 'react';
import LucideIcon from '../../components/LucideIcon';
import type { ThemeStyles } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
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

const STEPS=[{s:1,ak:'scenario.step.1',i:'Briefcase'},{s:2,ak:'scenario.step.2',i:'Database'},{s:3,ak:'scenario.step.3',i:'Network'},{s:4,ak:'scenario.step.4',i:'BookOpen'},{s:5,ak:'scenario.step.5',i:'Cpu'},{s:6,ak:'scenario.step.6',i:'Layout'},{s:7,ak:'scenario.step.7',i:'ShieldCheck'}]as const;

function CbList({items,selected,onChange,styles}:{items:{id:string;label:string;desc?:string}[];selected:string[];onChange:(v:string[])=>void;styles:ThemeStyles}){
  return <div className={`grid grid-cols-1 gap-2 max-h-[220px] overflow-y-auto p-2 ${styles.inputBg} border ${styles.inputBorder} rounded`}>
    {items.map(item=>{const ck=selected.includes(item.id);return <label key={item.id} className={`flex items-start gap-2.5 p-2.5 rounded cursor-pointer transition-colors border ${ck?'bg-indigo-950/30 border-indigo-500/40 text-indigo-200':`${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-90`}`}><input type="checkbox" checked={ck} onChange={()=>onChange(ck?selected.filter(id=>id!==item.id):[...selected,item.id])} className="mt-0.5 cursor-pointer accent-indigo-500"/><div className="space-y-0.5"><span className={`font-bold text-[11px] block ${styles.cardText}`}>{item.label}</span>{item.desc&&<span className={`text-[10px] ${styles.cardTextMuted} block leading-tight`}>{item.desc}</span>}</div></label>;})}
  </div>;
}

export default function ScenarioEditor({wizard,onClose,onStepChange,onSave,setWName,setWGoal,setWDesc,setWDept,setWPriority,setWBudget,setWStatus,setWSafetyIndex,setWDatasets,setWObjectTypes,setWKnowledgeBases,setWAiAgents,setWInterfaces,setWSecurityPolicies,styles,toast}:ScenarioEditorProps){
  const {t}=useLanguage();
  const {showWizardModal,wizardScenarioId,wizardStep,wName,wGoal,wDesc,wDept,wPriority,wBudget,wStatus,wSafetyIndex,wDatasets,wObjectTypes,wKnowledgeBases,wAiAgents,wInterfaces,wSecurityPolicies}=wizard;
  if(!showWizardModal)return null;

  const next=()=>{if(wizardStep===1&&(!wName.trim()||!wGoal.trim())){toast('error',t('scenario.validator.requiredFields'));return;}onStepChange(wizardStep+1);};
  const prev=()=>{if(wizardStep>1)onStepChange(wizardStep-1);};
  const Ic=(n:string,s:number)=>React.createElement(LucideIcon,{name:n,size:s});

  return <div className="fixed inset-0 bg-black/80 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-fadeIn overflow-y-auto">
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl max-w-2xl w-full overflow-hidden shadow-2xl my-8`}>
      <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} flex items-center justify-between`}>
        <span className={`text-sm font-bold text-white flex items-center gap-1.5`}>{Ic('Briefcase',14)}<span className={styles.accentText}/>{wizardScenarioId?t('scenario.title.edit'):t('scenario.title.create')}</span>
        <button onClick={onClose} className={`${styles.cardTextMuted} hover:text-white cursor-pointer`}>{Ic('X',16)}</button>
      </div>

      <div className={`${styles.cardBg} px-4 py-3 border-b ${styles.cardBorder} overflow-x-auto shrink-0`}>
        <div className="flex items-center justify-between min-w-[760px] px-2 py-1">
          {STEPS.map((it,idx,arr)=><React.Fragment key={it.s}>
            <div className="flex items-center gap-1.5">
              <div className={`w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold transition-all ${wizardStep===it.s?'bg-indigo-600 text-white ring-4 ring-indigo-950 shadow-md':wizardStep>it.s?'bg-emerald-600 text-white font-bold':`bg-[var(--card,#1E293B)] ${styles.cardTextMuted} border border-[var(--card,#334155)]`}`}>{wizardStep>it.s?'✓':it.s}</div>
              <span className={`text-[11px] font-bold whitespace-nowrap transition-colors ${wizardStep===it.s?'text-indigo-400 font-extrabold':wizardStep>it.s?'text-emerald-500':styles.cardTextMuted}`}>{t(it.ak)}</span>
            </div>
            {idx<arr.length-1&&<div className={`flex-1 h-[2px] mx-2 min-w-[12px] transition-all duration-300 ${wizardStep>it.s?'bg-emerald-600/60':'bg-[var(--card,#334155)]'}`}/>}
          </React.Fragment>)}
        </div>
      </div>

      <div className="p-5 max-h-[55vh] overflow-y-auto space-y-4 text-xs">
        {wizardStep===1&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className={`text-[11px] font-bold ${styles.accentText} flex items-center gap-1`}>{Ic('Info',12)}{t('scenario.wizard.s1.header')}</span><p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>{t('scenario.wizard.s1.desc')}</p></div>
          <div className="space-y-1.5"><label style={{color:'var(--card-text-muted)'}} className="font-bold block">{t('scenario.wizard.s1.nameLabel')} <span className="text-red-400">*</span></label><input type="text" placeholder={t('scenario.wizard.s1.namePlaceholder')} value={wName} onChange={e=>setWName(e.target.value)} className={`w-full p-2.5 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}/></div>
          <div className="space-y-1.5"><label style={{color:'var(--card-text-muted)'}} className="font-bold block">{t('scenario.wizard.s1.goalLabel')} <span className="text-red-400">*</span></label><input type="text" placeholder={t('scenario.wizard.s1.goalPlaceholder')} value={wGoal} onChange={e=>setWGoal(e.target.value)} className={`w-full p-2.5 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}/></div>
          <div className="space-y-1.5"><label style={{color:'var(--card-text-muted)'}} className="font-bold block">{t('scenario.wizard.s1.descLabel')}</label><textarea placeholder={t('scenario.wizard.s1.descPlaceholder')} value={wDesc} onChange={e=>setWDesc(e.target.value)} rows={3} className={`w-full p-2.5 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none font-sans focus:border-indigo-500 transition-colors`}/></div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5"><label style={{color:'var(--card-text-muted)'}} className="font-bold block">{t('scenario.wizard.s1.deptLabel')}</label><select value={wDept} onChange={e=>setWDept(e.target.value)} className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}><option>{t('scenario.wizard.s1.dept.d1')}</option><option>{t('scenario.wizard.s1.dept.d2')}</option><option>{t('scenario.wizard.s1.dept.d3')}</option><option>{t('scenario.wizard.s1.dept.d4')}</option></select></div>
            <div className="space-y-1.5"><label style={{color:'var(--card-text-muted)'}} className="font-bold block">{t('scenario.wizard.s1.priorityLabel')}</label><select value={wPriority} onChange={e=>setWPriority(e.target.value as any)} className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.accentText} font-bold outline-none focus:border-indigo-500 transition-colors`}><option value="CRITICAL">{t('scenario.wizard.pr.critical')}</option><option value="HIGH">{t('scenario.wizard.pr.high')}</option><option value="MEDIUM">{t('scenario.wizard.pr.medium')}</option><option value="LOW">{t('scenario.wizard.pr.low')}</option></select></div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5"><label style={{color:'var(--card-text-muted)'}} className="font-bold block">{t('scenario.wizard.s1.statusLabel')}</label><select value={wStatus} onChange={e=>setWStatus(e.target.value as any)} className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none`}><option value="DRAFT">{t('scenario.wizard.st.draft')}</option><option value="ACTIVE">{t('scenario.wizard.st.active')}</option><option value="COMPLETED">{t('scenario.wizard.st.completed')}</option><option value="SUSPENDED">{t('scenario.wizard.st.suspended')}</option></select></div>
            <div className="space-y-1.5"><label style={{color:'var(--card-text-muted)'}} className="font-bold block">{t('scenario.wizard.s1.budgetLabel')}</label><input type="text" value={wBudget} onChange={e=>setWBudget(e.target.value)} className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none`}/></div>
            <div className="space-y-1.5"><label style={{color:'var(--card-text-muted)'}} className="font-bold block">{t('scenario.wizard.s1.safetyLabel')}</label><input type="text" value={wSafetyIndex} onChange={e=>setWSafetyIndex(e.target.value)} className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none`}/></div>
          </div>
        </div>}

        {wizardStep===2&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className={`text-[11px] font-bold ${styles.accentText} flex items-center gap-1`}>{Ic('Database',12)}{t('scenario.wizard.s2.header')}</span><p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>{t('scenario.wizard.s2.desc')}</p></div>
          <div className="space-y-2"><div className="flex items-center justify-between"><label className={`${styles.cardText} font-bold block`}>{t('scenario.wizard.s2.label')}</label><span className={`text-[10px] ${styles.accentText} font-mono`}>{t('scenario.wizard.selected',{n:String(wDatasets.length)})}</span></div><CbList items={AVAILABLE_DATASETS} selected={wDatasets} onChange={setWDatasets} styles={styles}/></div>
        </div>}

        {wizardStep===3&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className={`text-[11px] font-bold ${styles.accentText} flex items-center gap-1`}>{Ic('Network',12)}{t('scenario.wizard.s3.header')}</span><p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>{t('scenario.wizard.s3.desc')}</p></div>
          <div className="space-y-2"><div className="flex items-center justify-between"><label className={`${styles.cardText} font-bold block`}>{t('scenario.wizard.s3.label')}</label><span className={`text-[10px] ${styles.accentText} font-mono`}>{t('scenario.wizard.selected',{n:String(wObjectTypes.length)})}</span></div><CbList items={AVAILABLE_OBJECTS} selected={wObjectTypes} onChange={setWObjectTypes} styles={styles}/></div>
        </div>}

        {wizardStep===4&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className={`text-[11px] font-bold ${styles.accentText} flex items-center gap-1`}>{Ic('BookOpen',12)}{t('scenario.wizard.s4.header')}</span><p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>{t('scenario.wizard.s4.desc')}</p></div>
          <div className="space-y-2"><div className="flex items-center justify-between"><label className={`${styles.cardText} font-bold block`}>{t('scenario.wizard.s4.label')}</label><span className={`text-[10px] ${styles.accentText} font-mono`}>{t('scenario.wizard.selected',{n:String(wKnowledgeBases.length)})}</span></div><CbList items={AVAILABLE_KNOWLEDGE} selected={wKnowledgeBases} onChange={setWKnowledgeBases} styles={styles}/></div>
        </div>}

        {wizardStep===5&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className={`text-[11px] font-bold ${styles.accentText} flex items-center gap-1`}>{Ic('Cpu',12)}{t('scenario.wizard.s5.header')}</span><p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>{t('scenario.wizard.s5.desc')}</p></div>
          <div className="space-y-2"><div className="flex items-center justify-between"><label className={`${styles.cardText} font-bold block`}>{t('scenario.wizard.s5.label')}</label><span className={`text-[10px] ${styles.accentText} font-mono`}>{t('scenario.wizard.selected',{n:String(wAiAgents.length)})}</span></div><CbList items={AVAILABLE_AGENTS} selected={wAiAgents} onChange={setWAiAgents} styles={styles}/></div>
        </div>}

        {wizardStep===6&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className={`text-[11px] font-bold ${styles.accentText} flex items-center gap-1`}>{Ic('Layout',12)}{t('scenario.wizard.s6.header')}</span><p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>{t('scenario.wizard.s6.desc')}</p></div>
          <div className="space-y-2"><div className="flex items-center justify-between"><label className={`${styles.cardText} font-bold block`}>{t('scenario.wizard.s6.label')}</label><span className={`text-[10px] ${styles.accentText} font-mono`}>{t('scenario.wizard.selected',{n:String(wInterfaces.length)})}</span></div><CbList items={AVAILABLE_INTERFACES} selected={wInterfaces} onChange={setWInterfaces} styles={styles}/></div>
        </div>}

        {wizardStep===7&&<div className="space-y-4">
          <div className="p-3 bg-[var(--card)] border border-[var(--border)] rounded-lg space-y-1"><span className={`text-[11px] font-bold ${styles.accentText} flex items-center gap-1`}>{Ic('ShieldCheck',12)}{t('scenario.wizard.s7.header')}</span><p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>{t('scenario.wizard.s7.desc')}</p></div>
          <div className="space-y-2"><div className="flex items-center justify-between"><label className={`${styles.cardText} font-bold block`}>{t('scenario.wizard.s7.label')}</label><span className={`text-[10px] ${styles.accentText} font-mono`}>{t('scenario.wizard.selected',{n:String(wSecurityPolicies.length)})}</span></div><CbList items={AVAILABLE_SECURITY} selected={wSecurityPolicies} onChange={setWSecurityPolicies} styles={styles}/></div>
        </div>}
      </div>

      <div className={`p-4 ${styles.cardBg} border-t ${styles.cardBorder} flex justify-between items-center shrink-0`}>
        <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>{t('scenario.wizard.progress',{step:String(wizardStep)})}</span>
        <div className="flex gap-2">
          {wizardStep>1&&<button type="button" onClick={prev} className={`px-3.5 py-1.5 ${styles.inputBg} ${styles.cardTextMuted} text-xs font-bold rounded cursor-pointer transition-all flex items-center gap-1`}>{Ic('ChevronLeft',12)}{t('scenario.nav.prev')}</button>}
          {wizardStep<7?<button type="button" onClick={next} className={`px-4 py-1.5 text-white text-xs font-bold rounded cursor-pointer transition-all flex items-center gap-1 ${styles.accentBg}`}>{t('scenario.nav.next')}{Ic('ChevronRight',12)}</button>:<button type="button" onClick={onSave} className={`px-5 py-1.5 text-white text-xs font-bold rounded cursor-pointer transition-all flex items-center gap-1 shadow-lg ${styles.successBg}`}>{Ic('Check',12)}{wizardScenarioId?t('scenario.save.allBind'):t('scenario.save.init')}</button>}
        </div>
      </div>
    </div>
  </div>;
}
