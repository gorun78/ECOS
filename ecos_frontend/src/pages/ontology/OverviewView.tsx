/**
 * OverviewView — 本体工作台全景总览
 *
 * 展示关系拓扑图谱、业务域管理、快速分级导航和审计日志。
 * 子组件：DomainManager（域CRUD）、helpers（颜色映射/日志数据）。
 *
 * @license Apache-2.0
 */
import React from 'react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import { Box, ChevronRight, Compass, GitMerge, History, Layers, Network, ShieldCheck, Tag, Workflow } from 'lucide-react';
import DynamicIcon from '../../components/ontology/DynamicIcon';
import { getDomainColorClasses, auditLogs } from './helpers';
import DomainManager from './DomainManager';
import OntologyGraph from '../../components/OntologyGraph';
import type { ObjectType, LinkType, ActionType, InterfaceType, SharedProperty, Dataset, OntologyDomain } from '../../types/ontology';

interface OverviewViewProps {
  objectTypes: ObjectType[];
  linkTypes: LinkType[];
  actionTypes: ActionType[];
  interfaces: InterfaceType[];
  sharedProperties: SharedProperty[];
  datasets: Dataset[];
  domains: OntologyDomain[];
  selectedDomainFilter: string | null;
  onSelectDomainFilter: (id: string | null) => void;
  onSelectNode: (nodeId: string) => void;
  onSelectEdge: (edgeId: string) => void;
  onQuickNavigate: (category: any, id: string) => void;
  onViewModeChange?: (mode: string) => void;
  onUpdateDomains: (domains: OntologyDomain[]) => void;
  onUpdateObjectTypes: (objectTypes: ObjectType[]) => void;
}

export default function OverviewView({
  objectTypes, linkTypes, actionTypes, interfaces, sharedProperties, datasets,
  domains, selectedDomainFilter, onSelectDomainFilter,
  onSelectNode, onSelectEdge, onQuickNavigate, onUpdateDomains, onUpdateObjectTypes,
}: OverviewViewProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const handleDeleteDomain = (domainId: string) => {
    if (!window.confirm(t('ontology.confirm_delete_domain').replace('{name}', domains.find(d => d.id === domainId)?.displayName || ''))) return;
    onUpdateDomains(domains.filter(d => d.id !== domainId));
    onUpdateObjectTypes(objectTypes.map(ot => ot.domainId === domainId ? { ...ot, domainId: undefined } : ot));
    if (selectedDomainFilter === domainId) onSelectDomainFilter(null);
  };

  const displayedObjects = !selectedDomainFilter ? objectTypes
    : selectedDomainFilter === 'unassigned' ? objectTypes.filter(ot => !ot.domainId)
    : objectTypes.filter(ot => ot.domainId === selectedDomainFilter);
  const displayedLinks = linkTypes.filter(lt =>
    displayedObjects.some(o => o.id === lt.sourceObjectType) && displayedObjects.some(o => o.id === lt.targetObjectType)
  );

  return (
    <div className={`flex flex-col h-full ${styles.appBg} overflow-y-auto p-6 space-y-6 select-none`}>
      {/* Title Banner */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs flex items-center justify-between`}>
        <div className="space-y-1 flex-1">
          <h2 className="text-sm font-semibold text-slate-900 flex items-center gap-1.5"><Workflow className="text-blue-600" size={16} />{t('ow.section.ontologyTitle')}</h2>
          <p className="text-xs text-slate-500 max-w-3xl leading-relaxed">{t('ow.section.ontologyDesc')}</p>
        </div>
        <div className="flex items-center gap-2 bg-blue-50 border border-blue-100 text-blue-700 px-3 py-1.5 rounded-lg text-xs font-medium shrink-0"><ShieldCheck size={13} /><span>{t('ow.label.domainsReady').replace('{n}', String(domains.length))}</span></div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-4 gap-4">
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow`}><div className="space-y-1"><span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">{t('ow.card.objectEntities')}</span><div className={`text-xl font-bold ${styles.cardText} font-mono`}>{objectTypes.length}</div><div className="text-[10px] text-slate-500 flex items-center gap-1"><span>{objectTypes.filter(ot => ot.domainId).length} {t('ow.label.domainAssigned')}</span><span className="text-slate-300">|</span><span className="text-amber-600 font-medium">{objectTypes.filter(ot => !ot.domainId).length} {t('ow.label.unassigned')}</span></div></div><span className="p-2.5 rounded-xl bg-blue-50 text-blue-600 border border-blue-100"><Box size={18} /></span></div>
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow`}><div className="space-y-1"><span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">{t('ow.card.linkCount')}</span><div className={`text-xl font-bold ${styles.cardText} font-mono`}>{linkTypes.length}</div><div className="text-[10px] text-slate-500">{t('ow.card.linkCountDesc')}</div></div><span className="p-2.5 rounded-xl bg-emerald-50 text-emerald-600 border border-emerald-100"><GitMerge size={18} /></span></div>
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow`}><div className="space-y-1"><span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">{t('ow.card.systemDomains')}</span><div className={`text-xl font-bold ${styles.cardText} font-mono`}>{domains.length}</div><div className="text-[10px] text-slate-500">{t('ow.card.systemDomainsDesc')}</div></div><span className="p-2.5 rounded-xl bg-purple-50 text-purple-600 border border-purple-100"><Layers size={18} /></span></div>
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow`}><div className="space-y-1"><span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">{t('ow.card.interfaceAndProperties')}</span><div className={`text-xl font-bold ${styles.cardText} font-mono`}>{interfaces.length + sharedProperties.length}</div><div className="text-[10px] text-slate-500">{interfaces.length} {t('ow.label.contracts')} · {sharedProperties.length} {t('ow.label.sharedProps')}</div></div><span className="p-2.5 rounded-xl bg-amber-50 text-amber-600 border border-amber-100"><Tag size={18} /></span></div>
      </div>

      {/* Graph Panel */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs space-y-4`}>
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 border-b border-slate-100 pb-3">
          <div className="space-y-0.5"><h3 className="text-xs font-semibold text-slate-900 flex items-center gap-1.5"><Network size={15} className="text-blue-600" />{t('ow.section.graphTitle')}</h3><p className="text-[11px] text-slate-500">{t('ow.section.graphDesc')}</p></div>
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="text-slate-400 font-bold uppercase text-[9px] tracking-wider">{t('ow.label.filterByDomain')}</span>
            <button onClick={() => onSelectDomainFilter(null)} className={`px-2.5 py-1 rounded-full transition-all border font-medium cursor-pointer text-[11px] ${!selectedDomainFilter ? 'bg-slate-900 text-white border-slate-900 shadow-xs' : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'}`}>{t('ow.btn.globalPanorama')} ({objectTypes.length})</button>
            {domains.map(d => { const isSelected = selectedDomainFilter === d.id; const count = objectTypes.filter(ot => ot.domainId === d.id).length; const classes = getDomainColorClasses(d.color); return (<button key={d.id} onClick={() => onSelectDomainFilter(d.id)} className={`px-2.5 py-1 rounded-full transition-all border font-medium cursor-pointer flex items-center gap-1.5 text-[11px] ${isSelected ? `${classes.activeBg} text-white border-transparent shadow-xs` : `bg-white text-slate-700 border-slate-200 ${classes.hoverBg}`}`}><span className={`w-1.5 h-1.5 rounded-full ${isSelected ? 'bg-white' : classes.dot}`} /><span>{d.displayName.split(' (')[0]}</span><span className={`text-[9px] px-1 py-0.2 rounded-full font-mono ${isSelected ? 'bg-white/20' : 'bg-slate-100 text-slate-500'}`}>{count}</span></button>); })}
            {objectTypes.some(ot => !ot.domainId) && (<button onClick={() => onSelectDomainFilter('unassigned')} className={`px-2.5 py-1 rounded-full transition-all border font-medium cursor-pointer text-[11px] ${selectedDomainFilter === 'unassigned' ? 'bg-slate-500 text-white border-slate-500 shadow-xs' : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'}`}>{t('ow.btn.unassigned')} ({objectTypes.filter(ot => !ot.domainId).length})</button>)}
          </div>
        </div>
        <div className={`relative border ${styles.cardBorder} rounded-lg overflow-hidden`}><OntologyGraph objectTypes={displayedObjects} linkTypes={displayedLinks} onSelectNode={onSelectNode} onSelectEdge={onSelectEdge} /></div>
      </div>

      {/* Domain Manager */}
      <DomainManager
        domains={domains} objectTypes={objectTypes}
        selectedDomainFilter={selectedDomainFilter} onSelectDomainFilter={onSelectDomainFilter}
        onUpdateDomains={onUpdateDomains} onUpdateObjectTypes={onUpdateObjectTypes}
        onQuickNavigate={onQuickNavigate} onDeleteDomain={handleDeleteDomain}
      />

      {/* Bottom: Quick Nav + Audit Logs */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs space-y-3`}>
          <h3 className="text-xs font-semibold text-slate-900 flex items-center gap-1.5"><Compass size={14} className="text-slate-500" />{t('ow.section.quickNavigation')}</h3>
          <div className="space-y-4 max-h-[260px] overflow-y-auto pr-1">
            {domains.map(d => { const domObjs = objectTypes.filter(ot => ot.domainId === d.id); if (domObjs.length === 0) return null; const classes = getDomainColorClasses(d.color); return (<div key={d.id} className="space-y-1.5"><div className="flex items-center gap-1.5 text-[10px] font-extrabold text-slate-500 tracking-wider uppercase border-b border-slate-100 pb-1"><span className={`w-2 h-2 rounded-full ${classes.dot}`} /><span>{d.displayName}</span></div><div className="space-y-1 pl-1">{domObjs.map(ot => (<div key={ot.id} onClick={() => onQuickNavigate('object', ot.id)} className="flex items-center justify-between p-1.5 rounded-lg border border-slate-100 hover:border-blue-300 hover:bg-blue-50/20 cursor-pointer transition-all group"><div className="flex items-center gap-2 truncate"><span className={`p-0.5 rounded border ${ot.color}`}><DynamicIcon name={ot.icon} size={11} /></span><span className="text-xs font-medium text-slate-700">{ot.displayName}</span></div><ChevronRight size={11} className="text-slate-300 group-hover:translate-x-0.5" /></div>))}</div></div>); })}
            {objectTypes.some(ot => !ot.domainId) && (<div className="space-y-1.5"><div className="flex items-center gap-1.5 text-[10px] font-extrabold text-slate-400 tracking-wider uppercase border-b border-slate-100 pb-1"><span className="w-2 h-2 rounded-full bg-slate-300" /><span>{t('ow.section.unassignedPool')}</span></div><div className="space-y-1 pl-1">{objectTypes.filter(ot => !ot.domainId).map(ot => (<div key={ot.id} onClick={() => onQuickNavigate('object', ot.id)} className="flex items-center justify-between p-1.5 rounded-lg border border-slate-100 hover:border-blue-300 hover:bg-blue-50/20 cursor-pointer transition-all group"><div className="flex items-center gap-2 truncate"><span className={`p-0.5 rounded border ${ot.color}`}><DynamicIcon name={ot.icon} size={11} /></span><span className="text-xs font-medium text-slate-700">{ot.displayName}</span></div><ChevronRight size={11} className="text-slate-300 group-hover:translate-x-0.5" /></div>))}</div></div>)}
          </div>
        </div>
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs lg:col-span-2 space-y-3`}>
          <h3 className="text-xs font-semibold text-slate-900 flex items-center gap-1.5"><History size={14} className="text-slate-500" />{t('ow.section.auditLog')}</h3>
          <div className="divide-y divide-slate-100 max-h-[260px] overflow-y-auto pr-1">
            {auditLogs.map(log => (<div key={log.id} className="py-2.5 first:pt-0 last:pb-0 text-[11px] flex items-start gap-3"><div className="mt-0.5"><span className={`w-2 h-2 rounded-full inline-block ${log.type === 'publish' ? 'bg-blue-500' : log.type === 'create' ? 'bg-emerald-500' : 'bg-slate-400'}`} /></div><div className="flex-1 space-y-0.5"><div className="flex justify-between items-center text-xs"><span className="font-semibold text-slate-800">{log.action}</span><span className="text-[10px] text-slate-400 font-mono">{log.time}</span></div><p className="text-slate-500 text-[11px] leading-relaxed">{log.detail}</p><div className="text-[10px] text-slate-400 font-mono">{t('ow.label.operator')} {log.user}</div></div></div>))}
          </div>
        </div>
      </div>
    </div>
  );
}
